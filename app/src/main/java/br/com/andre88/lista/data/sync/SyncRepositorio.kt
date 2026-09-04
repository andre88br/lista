package br.com.andre88.lista.data.sync

import android.content.Context
import android.util.Log
import br.com.andre88.lista.data.Casa
import br.com.andre88.lista.data.ListaRepository
import br.com.andre88.lista.data.Preferencias
import br.com.andre88.lista.data.db.ItemEntity
import br.com.andre88.lista.data.db.ProdutoEntity
import br.com.andre88.lista.data.db.ScanEventoEntity
import br.com.andre88.lista.domain.ItemQtds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface ResultadoSync {
    data class Ok(val enviados: Int, val recebidos: Int) : ResultadoSync
    data object Desligada : ResultadoSync
    data class Falhou(val mensagem: String) : ResultadoSync
}

/**
 * Motor da sincronizacao. O celular continua sendo a fonte de verdade do que
 * acontece nele: primeiro empurra o que e local, depois puxa o que e dos outros.
 * Como cada evento tem id proprio, repetir uma sincronizacao nao muda nada.
 */
class SyncRepositorio(
    private val contexto: Context,
    private val api: ApiCliente,
    private val repositorio: ListaRepository,
    private val preferencias: Preferencias,
) {

    /**
     * Pede um envio imediato depois de uma leitura. Vai pelo WorkManager para
     * sobreviver ao app sendo fechado no bolso, no meio do mercado.
     */
    fun solicitarEnvio() {
        if (estado.value.ativa) SyncWorker.agora(contexto)
    }

    // Uma sincronizacao por vez: o botao manual, o worker e o retorno do app podem coincidir.
    private val trava = Mutex()

    val estado = preferencias.sincronizacao

    suspend fun sincronizar(): ResultadoSync = trava.withLock {
        val servidor = preferencias.servidorUrl
        val token = preferencias.token
        if (servidor == null || token == null || preferencias.casaId == null) return ResultadoSync.Desligada

        try {
            var enviados = 0
            var recebidos = 0
            // Paginacao: em casos normais uma volta basta, mas depois de muito tempo
            // offline pode haver mais do que cabe numa resposta.
            var volta = 0
            while (volta < MAXIMO_DE_VOLTAS) {
                enviados += empurrar(servidor, token)
                val (quantos, temMais) = puxar(servidor, token)
                recebidos += quantos
                if (!temMais) break
                volta++
            }
            preferencias.registrarSincronizacao(System.currentTimeMillis(), null)
            ResultadoSync.Ok(enviados, recebidos)
        } catch (erro: Exception) {
            val mensagem = mensagemDe(erro)
            Log.w(TAG, "sincronizacao falhou: $mensagem")
            preferencias.registrarSincronizacao(preferencias.sincronizacao.value.ultimaSync, mensagem)
            ResultadoSync.Falhou(mensagem)
        }
    }

    // ------------------------------------------------------------------ empurrar

    private suspend fun empurrar(servidor: String, token: String): Int {
        val eventos = repositorio.eventosParaEnviar()
        val produtos = repositorio.produtosParaEnviar(preferencias.cursorProdutosEnviados)
        if (eventos.isEmpty() && produtos.isEmpty()) return 0

        api.enviar(
            servidor, token,
            EnvioSync(
                eventos = eventos.map { evento ->
                    EventoApi(
                        id = evento.id,
                        codigoBarras = evento.codigoBarras,
                        modo = modoParaApi(evento.modo),
                        deltaEstoque = evento.deltaEstoque,
                        deltaLista = evento.deltaLista,
                        deltaCarrinho = evento.deltaCarrinho,
                        criadoEm = paraIso(evento.timestamp),
                    )
                },
                produtos = produtos.map { produto ->
                    ProdutoApi(
                        codigoBarras = produto.codigoBarras,
                        nome = produto.nome,
                        marca = produto.marca,
                        imagemUrl = produto.imagemUrl,
                        categoria = produto.categoria,
                        atualizadoEm = paraIso(produto.atualizadoEm),
                    )
                },
            ),
        )

        repositorio.marcarEventosSincronizados(eventos.map { it.id })
        produtos.maxOfOrNull { it.atualizadoEm }?.let { preferencias.cursorProdutosEnviados = it }
        return eventos.size
    }

    // --------------------------------------------------------------------- puxar

    private suspend fun puxar(servidor: String, token: String): Pair<Int, Boolean> {
        val resposta = api.buscar(
            servidor, token,
            desde = preferencias.cursorSeq,
            produtosDesde = preferencias.cursorProdutos.takeIf { it > 0 }?.let(::paraIso),
        )

        // Produtos primeiro: assim um evento nunca chega antes do nome do produto.
        resposta.produtos.forEach { produto ->
            repositorio.aplicarProdutoRemoto(
                ProdutoEntity(
                    codigoBarras = produto.codigoBarras,
                    nome = produto.nome,
                    marca = produto.marca,
                    imagemUrl = produto.imagemUrl,
                    categoria = produto.categoria,
                    atualizadoEm = deIso(produto.atualizadoEm),
                ),
            )
        }

        var aplicados = 0
        resposta.eventos.forEach { evento ->
            // Eventos que este proprio celular gerou ja estao aplicados aqui.
            val novo = repositorio.aplicarEventoRemoto(
                ScanEventoEntity(
                    id = evento.id,
                    codigoBarras = evento.codigoBarras,
                    modo = evento.modo,
                    deltaEstoque = evento.deltaEstoque,
                    deltaLista = evento.deltaLista,
                    deltaCarrinho = evento.deltaCarrinho,
                    timestamp = deIso(evento.criadoEm),
                    sincronizado = true,
                    autorId = evento.autorId,
                ),
            )
            if (novo) aplicados++
        }

        if (resposta.seq > preferencias.cursorSeq) preferencias.cursorSeq = resposta.seq
        resposta.produtos.mapNotNull { deIsoOuNulo(it.atualizadoEm) }.maxOrNull()?.let {
            if (it > preferencias.cursorProdutos) preferencias.cursorProdutos = it
        }
        return aplicados to resposta.temMais
    }

    // ------------------------------------------------------------ entrar e sair

    /** Troca o endereco do servidor. So usado por quem hospeda em outro lugar. */
    suspend fun configurarServidor(url: String, nomeDoAparelho: String): Result<Unit> = runCatching {
        val limpo = url.trim().trimEnd('/')
        require(limpo.startsWith("https://") || limpo.startsWith("http://")) {
            "o endereco precisa comecar com https://"
        }
        if (!api.servidorResponde(limpo)) error("nao consegui falar com $limpo")

        preferencias.definirServidor(limpo)
        garantirRegistro(nomeDoAparelho)
    }

    /**
     * Registra o aparelho no servidor, se ainda nao estiver. Acontece sozinho na
     * primeira vez que a pessoa cria ou entra numa casa: o endereco ja vem no
     * APK, entao nao ha nada para ela digitar ou entender.
     */
    suspend fun garantirRegistro(nomeDoAparelho: String) {
        if (preferencias.token != null) return
        val servidor = preferencias.servidorUrl
            ?: error("este APK foi gerado sem endereco de servidor")
        val resposta = api.registrarDispositivo(servidor, nomeDoAparelho)
        preferencias.definirDispositivo(resposta.dispositivoId, resposta.token)
    }

    /** Cria a casa e sobe o que ja existe neste celular. */
    suspend fun criarCasa(nome: String): Result<Casa> = runCatching {
        val (servidor, token) = exigirConfigurado()
        val resposta = api.criarCasa(servidor, token, nome)
        preferencias.definirCasa(resposta.paraCasa())
        SyncWorker.agendarPeriodico(contexto)
        repositorio.prepararEnvioDoEstadoAtual(preferencias.dispositivoId)
        sincronizar()
        resposta.paraCasa()
    }

    /**
     * Entra numa casa existente. [juntarMeusItens] decide o que fazer com o que ja
     * esta neste celular: somar ao da casa ou descartar e adotar o da casa.
     */
    suspend fun entrarNaCasa(codigo: String, juntarMeusItens: Boolean): Result<Casa> = runCatching {
        val (servidor, token) = exigirConfigurado()
        val resposta = api.entrarNaCasa(servidor, token, codigo)
        preferencias.definirCasa(resposta.paraCasa())
        SyncWorker.agendarPeriodico(contexto)

        if (juntarMeusItens) {
            repositorio.prepararEnvioDoEstadoAtual(preferencias.dispositivoId)
        } else {
            repositorio.limparParaAdotarCasa()
        }

        // O instantaneo evita reproduzir o historico inteiro da casa.
        val instantaneo = api.instantaneo(servidor, token)
        repositorio.aplicarInstantaneo(
            produtos = instantaneo.produtos.map {
                ProdutoEntity(
                    codigoBarras = it.codigoBarras,
                    nome = it.nome,
                    marca = it.marca,
                    imagemUrl = it.imagemUrl,
                    categoria = it.categoria,
                    atualizadoEm = deIso(it.atualizadoEm),
                )
            },
            itens = instantaneo.itens.map {
                ItemEntity.de(it.codigoBarras, ItemQtds(it.estoque, it.lista, it.carrinho))
            },
        )
        preferencias.cursorSeq = instantaneo.seq
        preferencias.cursorProdutos = System.currentTimeMillis()

        sincronizar()
        resposta.paraCasa()
    }

    suspend fun gerarNovoCodigo(): Result<Casa> = runCatching {
        val (servidor, token) = exigirConfigurado()
        val resposta = api.novoCodigo(servidor, token)
        preferencias.definirCasa(resposta.paraCasa())
        resposta.paraCasa()
    }

    suspend fun atualizarDadosDaCasa(): Result<Casa> = runCatching {
        val (servidor, token) = exigirConfigurado()
        val resposta = api.casaAtual(servidor, token)
        preferencias.definirCasa(resposta.paraCasa())
        resposta.paraCasa()
    }

    /** Sai da casa neste aparelho. As listas locais continuam como estao. */
    suspend fun sairDaCasa(): Result<Unit> = runCatching {
        val (servidor, token) = exigirConfigurado()
        runCatching { api.sairDaCasa(servidor, token) }
        preferencias.esquecerSincronizacao()
        SyncWorker.cancelarTudo(contexto)
    }

    // ------------------------------------------------------------------ internos

    private fun exigirConfigurado(): Pair<String, String> {
        val servidor = preferencias.servidorUrl ?: error("configure o endereco do servidor primeiro")
        val token = preferencias.token ?: error("este aparelho ainda nao foi registrado no servidor")
        return servidor to token
    }

    private fun RespostaCasa.paraCasa() = Casa(id = casaId, nome = nome, codigo = codigo, membros = membros)

    /** O servidor so conhece os modos do ciclo; ajuste e desfazer viram AJUSTE/DESFAZER. */
    private fun modoParaApi(modo: String): String = when (modo) {
        "MERCADO", "GUARDAR", "ACABOU", "DESFAZER" -> modo
        else -> "AJUSTE"
    }

    // java.time exigiria minSdk 26 ou desugaring; o formato do servidor e fixo,
    // entao SimpleDateFormat em UTC resolve sem dependencia nova.
    private val formatoIso = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }

    private fun paraIso(millis: Long): String = formatoIso.get()!!.format(Date(millis))

    private fun deIso(texto: String?): Long = deIsoOuNulo(texto) ?: System.currentTimeMillis()

    private fun deIsoOuNulo(texto: String?): Long? {
        if (texto.isNullOrBlank()) return null
        return try {
            formatoIso.get()!!.parse(texto)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private fun mensagemDe(erro: Exception): String = when {
        erro is ErroDaApi && erro.status == 401 -> "o servidor nao reconheceu este aparelho"
        erro is ErroDaApi && erro.status == 409 -> "este aparelho nao esta em nenhuma casa"
        erro is ErroDaApi -> erro.message ?: "erro no servidor"
        else -> erro.message ?: erro.javaClass.simpleName
    }

    private companion object {
        const val TAG = "Sync"
        const val MAXIMO_DE_VOLTAS = 10
    }
}
