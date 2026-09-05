package br.com.andre88.lista.data

import androidx.room.withTransaction
import br.com.andre88.lista.data.db.AppDatabase
import br.com.andre88.lista.data.db.ItemComProduto
import br.com.andre88.lista.data.db.ItemEntity
import br.com.andre88.lista.data.db.ProdutoEntity
import br.com.andre88.lista.data.db.ScanEventoEntity
import br.com.andre88.lista.data.remote.OpenFoodFactsClient
import br.com.andre88.lista.data.remote.SugestaoProduto
import br.com.andre88.lista.domain.Campo
import br.com.andre88.lista.domain.Deltas
import br.com.andre88.lista.domain.ItemQtds
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.domain.ScanResultado
import br.com.andre88.lista.domain.ScanTransitions
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Resposta de uma leitura de codigo de barras. */
sealed interface ResultadoLeitura {

    /** A leitura foi aplicada. `eventoId` permite desfazer. */
    data class Registrada(
        val produto: ProdutoEntity,
        val resultado: ScanResultado,
        val modo: Modo,
        val eventoId: String,
    ) : ResultadoLeitura

    /** Codigo nunca visto: a UI precisa pedir (ou sugerir) o nome antes de aplicar. */
    data class ProdutoDesconhecido(val codigoBarras: String) : ResultadoLeitura
}

class ListaRepository(
    private val db: AppDatabase,
    private val openFoodFacts: OpenFoodFactsClient,
    private val preferencias: Preferencias,
) {

    private val produtoDao = db.produtoDao()
    private val itemDao = db.itemDao()
    private val eventoDao = db.scanEventoDao()

    // ----------------------------------------------------------------- leitura

    val listaDeCompras: Flow<List<ItemComProduto>> = itemDao.listaDeCompras()
    val estoque: Flow<List<ItemComProduto>> = itemDao.estoque()
    val carrinho: Flow<List<ItemComProduto>> = itemDao.carrinho()
    val totalLista: Flow<Int> = itemDao.totalLista()
    val totalEstoque: Flow<Int> = itemDao.totalEstoque()
    val totalCarrinho: Flow<Int> = itemDao.totalCarrinho()
    val eventosPendentes: Flow<Int> = eventoDao.quantidadePendente()

    suspend fun produto(codigoBarras: String): ProdutoEntity? = produtoDao.porCodigo(codigoBarras)

    suspend fun itemComProduto(codigoBarras: String): ItemComProduto? = itemDao.itemComProduto(codigoBarras)

    // ------------------------------------------------------------------ escrita

    /**
     * Aplica uma leitura. Se o codigo ainda nao tem cadastro, nada e alterado e a UI
     * recebe [ResultadoLeitura.ProdutoDesconhecido] para abrir o cadastro rapido.
     */
    suspend fun registrarLeitura(codigoBarras: String, modo: Modo): ResultadoLeitura {
        val produto = produtoDao.porCodigo(codigoBarras)
            ?: return ResultadoLeitura.ProdutoDesconhecido(codigoBarras)
        return aplicar(produto, modo)
    }

    /** Cadastra o produto novo e ja aplica a leitura que o descobriu, numa tacada so. */
    suspend fun cadastrarEAplicar(produto: ProdutoEntity, modo: Modo): ResultadoLeitura.Registrada {
        produtoDao.salvar(produto)
        return aplicar(produto, modo)
    }

    private suspend fun aplicar(produto: ProdutoEntity, modo: Modo): ResultadoLeitura.Registrada =
        db.withTransaction {
            val atual = qtdsDe(produto.codigoBarras)
            val resultado = ScanTransitions.aplicar(atual, modo)
            val evento = gravar(produto.codigoBarras, resultado.deltas, modo.name, resultado.depois)
            ResultadoLeitura.Registrada(produto, resultado, modo, evento.id)
        }

    /**
     * Desfaz uma leitura gravando um evento com os deltas invertidos, em vez de
     * apagar o original. E assim que o desfazer chega no outro celular.
     */
    suspend fun desfazer(eventoId: String): Boolean = db.withTransaction {
        val evento = eventoDao.porId(eventoId) ?: return@withTransaction false
        if (evento.desfeito) return@withTransaction false

        val deltas = -evento.paraDeltas()
        val novo = ScanTransitions.aplicarDeltas(qtdsDe(evento.codigoBarras), deltas)
        gravar(evento.codigoBarras, deltas, ScanEventoEntity.MODO_DESFAZER, novo)
        eventoDao.marcarDesfeito(evento.id)
        true
    }

    suspend fun desfazerUltima(): Boolean {
        val ultimo = eventoDao.ultimoNaoDesfeito() ?: return false
        return desfazer(ultimo.id)
    }

    /** Ajuste manual de quantidade (botoes + e - nas listas). */
    suspend fun ajustar(codigoBarras: String, campo: Campo, passo: Int) = db.withTransaction {
        val atual = qtdsDe(codigoBarras)
        val deltas = ScanTransitions.deltasDeAjuste(atual, campo, passo)
        if (deltas.nulo) return@withTransaction
        gravar(codigoBarras, deltas, ScanEventoEntity.MODO_AJUSTE, atual + deltas)
    }

    /** Zera um contador especifico (remover da lista, tirar do estoque). */
    suspend fun zerar(codigoBarras: String, campo: Campo) = db.withTransaction {
        val atual = qtdsDe(codigoBarras)
        val deltas = ScanTransitions.deltasParaZerar(atual, campo)
        if (deltas.nulo) return@withTransaction
        gravar(codigoBarras, deltas, ScanEventoEntity.MODO_AJUSTE, atual + deltas)
    }

    /** Atalho de quem nao quer reescanear em casa: tudo que foi comprado vai para o estoque. */
    suspend fun guardarTudoNoEstoque(): Int = db.withTransaction {
        val noCarrinho = itemDao.itensNoCarrinho()
        var total = 0
        noCarrinho.forEach { item ->
            val atual = item.paraQtds()
            val quantidade = atual.exibir().carrinho
            if (quantidade <= 0) return@forEach
            // Some no estoque quitando a divida dele, e zera o carrinho.
            val deltas = Deltas(
                estoque = quantidade + (if (atual.estoque < 0) -atual.estoque else 0),
                carrinho = -quantidade,
            )
            gravar(item.codigoBarras, deltas, Modo.GUARDAR.name, atual + deltas)
            total += quantidade
        }
        total
    }

    /** Cadastro/edicao de produto sem passar por uma leitura. */
    suspend fun salvarProduto(produto: ProdutoEntity) =
        produtoDao.salvar(produto.copy(atualizadoEm = System.currentTimeMillis()))

    suspend fun renomearProduto(codigoBarras: String, nome: String, marca: String?) =
        produtoDao.renomear(codigoBarras, nome, marca, System.currentTimeMillis())

    /** Item sem codigo de barras (frutas, granel): entra com um codigo interno. */
    suspend fun adicionarItemManual(nome: String, modo: Modo = Modo.ACABOU): ResultadoLeitura.Registrada {
        val produto = ProdutoEntity(
            codigoBarras = "manual:${UUID.randomUUID()}",
            nome = nome.trim(),
            origemNome = ProdutoEntity.ORIGEM_MANUAL,
        )
        return cadastrarEAplicar(produto, modo)
    }

    // ------------------------------------------------------------ Open Food Facts

    /** Sugestao de nome para um codigo desconhecido. Devolve null se estiver offline ou desligado. */
    suspend fun sugerirProduto(codigoBarras: String): SugestaoProduto? {
        if (!preferencias.consultarOpenFoodFacts.value) return null
        return openFoodFacts.buscar(codigoBarras)
    }

    // ------------------------------------------------------------- sincronizacao

    suspend fun eventosParaEnviar(limite: Int = 400): List<ScanEventoEntity> = eventoDao.pendentes(limite)

    suspend fun produtosParaEnviar(desde: Long, limite: Int = 400): List<ProdutoEntity> =
        produtoDao.alteradosDepoisDe(desde, limite)

    suspend fun marcarEventosSincronizados(ids: List<String>) {
        if (ids.isNotEmpty()) eventoDao.marcarSincronizados(ids)
    }

    /**
     * Aplica um evento que veio do servidor. O id e a garantia de que aplicar duas
     * vezes nao conta duas vezes.
     */
    suspend fun aplicarEventoRemoto(evento: ScanEventoEntity): Boolean = db.withTransaction {
        if (eventoDao.existe(evento.id)) return@withTransaction false
        eventoDao.inserirSeNovo(evento.copy(sincronizado = true))
        val novo = ScanTransitions.aplicarDeltas(qtdsDe(evento.codigoBarras), evento.paraDeltas())
        itemDao.salvar(ItemEntity.de(evento.codigoBarras, novo, autor = evento.autorNome))
        itemDao.limparZerados()
        true
    }

    /** Nome de produto nao e contador: vence quem escreveu por ultimo. */
    suspend fun aplicarProdutoRemoto(produto: ProdutoEntity) = db.withTransaction {
        val local = produtoDao.porCodigo(produto.codigoBarras)
        if (local == null || produto.atualizadoEm > local.atualizadoEm) {
            produtoDao.salvar(produto)
        }
    }

    /**
     * Ao criar uma casa (ou ao juntar seus itens a uma casa existente), o que ja
     * existe no celular sobe como um evento por produto — o estado atual, e nao a
     * vida inteira do historico.
     */
    suspend fun prepararEnvioDoEstadoAtual(autorId: String?) = db.withTransaction {
        eventoDao.marcarTudoSincronizado()
        val agora = System.currentTimeMillis()
        itemDao.itensComQuantidade().forEach { item ->
            val q = item.paraQtds()
            eventoDao.inserirSeNovo(
                ScanEventoEntity(
                    id = UUID.randomUUID().toString(),
                    codigoBarras = item.codigoBarras,
                    modo = ScanEventoEntity.MODO_AJUSTE,
                    deltaEstoque = q.estoque,
                    deltaLista = q.lista,
                    deltaCarrinho = q.carrinho,
                    timestamp = agora,
                    sincronizado = false,
                    autorId = autorId,
                ),
            )
        }
        produtoDao.todos().forEach { produto ->
            if (produto.atualizadoEm <= 0) produtoDao.salvar(produto.copy(atualizadoEm = agora))
        }
    }

    /** Descarta o conteudo local para adotar o da casa (quem escolhe "usar so os itens da casa"). */
    suspend fun limparParaAdotarCasa() = db.withTransaction {
        eventoDao.apagarTudo()
        itemDao.apagarTudo()
        produtoDao.apagarTudo()
    }

    /** Aplica o estado completo da casa, para quem acabou de entrar pelo codigo. */
    suspend fun aplicarInstantaneo(produtos: List<ProdutoEntity>, itens: List<ItemEntity>) = db.withTransaction {
        produtos.forEach { aplicarProdutoRemoto(it) }
        itens.forEach { itemDao.salvar(it) }
        itemDao.limparZerados()
    }

    // -------------------------------------------------------------------- backup

    suspend fun exportar(): BackupDados = db.withTransaction {
        BackupDados(
            produtos = produtoDao.todos().map(ProdutoBackup::de),
            itens = itemDao.todos().map(ItemBackup::de),
        )
    }

    /** Importa um backup substituindo tudo que existe hoje. */
    suspend fun importar(dados: BackupDados) = db.withTransaction {
        eventoDao.apagarTudo()
        itemDao.apagarTudo()
        produtoDao.apagarTudo()
        dados.produtos.forEach { produtoDao.salvar(it.paraEntidade()) }
        dados.itens.forEach { itemDao.salvar(it.paraEntidade()) }
        itemDao.limparZerados()
    }

    // ------------------------------------------------------------------ internos

    private suspend fun qtdsDe(codigoBarras: String): ItemQtds =
        itemDao.porCodigo(codigoBarras)?.paraQtds() ?: ItemQtds()

    /** Grava a mudanca e o evento correspondente. Toda alteracao passa por aqui. */
    private suspend fun gravar(
        codigoBarras: String,
        deltas: Deltas,
        modo: String,
        novoEstado: ItemQtds,
    ): ScanEventoEntity {
        val eu = preferencias.sincronizacao.value.usuario?.nome
        itemDao.salvar(ItemEntity.de(codigoBarras, novoEstado, autor = eu))
        itemDao.limparZerados()
        val evento = ScanEventoEntity(
            id = UUID.randomUUID().toString(),
            codigoBarras = codigoBarras,
            modo = modo,
            deltaEstoque = deltas.estoque,
            deltaLista = deltas.lista,
            deltaCarrinho = deltas.carrinho,
            autorId = preferencias.dispositivoId,
            autorNome = eu,
            sincronizado = false,
        )
        eventoDao.inserirSeNovo(evento)
        return evento
    }
}
