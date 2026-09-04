package br.com.andre88.lista.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.andre88.lista.data.ListaRepository
import br.com.andre88.lista.data.Preferencias
import br.com.andre88.lista.data.ResultadoLeitura
import br.com.andre88.lista.data.db.ProdutoEntity
import br.com.andre88.lista.data.sync.SyncRepositorio
import br.com.andre88.lista.domain.Modo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que a tela mostra sobre a ultima leitura aceita. */
data class UltimaLeitura(
    val codigoBarras: String,
    val nome: String,
    val eventoId: String,
    val descricao: String,
    val quantidadeNaLista: Int,
)

/** Cadastro rapido aberto quando o codigo lido ainda nao existe. */
data class CadastroPendente(
    val codigoBarras: String,
    val carregandoSugestao: Boolean = true,
    val nomeSugerido: String = "",
    val marcaSugerida: String? = null,
    val imagemUrl: String? = null,
    val categoria: String? = null,
    val veioDaInternet: Boolean = false,
)

data class ScannerUiState(
    val modo: Modo = Modo.MERCADO,
    val ultima: UltimaLeitura? = null,
    val lidosNaSessao: Int = 0,
    val cadastro: CadastroPendente? = null,
    val aviso: String? = null,
    val lanterna: Boolean = false,
)

class ScannerViewModel(
    private val repositorio: ListaRepository,
    private val preferencias: Preferencias,
    private val sync: SyncRepositorio,
    modo: Modo,
) : ViewModel() {

    private val _estado = MutableStateFlow(ScannerUiState(modo = modo))
    val estado: StateFlow<ScannerUiState> = _estado.asStateFlow()

    /** Ultima vez que cada codigo foi aceito, para nao contar a mesma embalagem duas vezes. */
    private val ultimaLeituraPorCodigo = mutableMapOf<String, Long>()

    fun aoLerCodigo(codigoBruto: String, agora: Long = System.currentTimeMillis()) {
        val codigo = codigoBruto.trim()
        if (codigo.isEmpty()) return
        // Enquanto o cadastro rapido esta aberto, a camera nao interfere.
        if (_estado.value.cadastro != null) return

        val ultimoMs = ultimaLeituraPorCodigo[codigo]
        if (ultimoMs != null && agora - ultimoMs < preferencias.cooldownMs.value) return
        ultimaLeituraPorCodigo[codigo] = agora

        viewModelScope.launch {
            when (val resultado = repositorio.registrarLeitura(codigo, _estado.value.modo)) {
                is ResultadoLeitura.Registrada -> {
                    mostrarRegistrada(resultado)
                    sync.solicitarEnvio()
                }
                is ResultadoLeitura.ProdutoDesconhecido -> abrirCadastro(resultado.codigoBarras)
            }
        }
    }

    /** Digitacao manual do codigo, para etiquetas danificadas. */
    fun aoDigitarCodigo(codigo: String) {
        ultimaLeituraPorCodigo.remove(codigo.trim())
        aoLerCodigo(codigo)
    }

    private suspend fun abrirCadastro(codigoBarras: String) {
        _estado.update { it.copy(cadastro = CadastroPendente(codigoBarras = codigoBarras)) }
        val sugestao = repositorio.sugerirProduto(codigoBarras)
        _estado.update { atual ->
            val pendente = atual.cadastro ?: return@update atual
            if (pendente.codigoBarras != codigoBarras) return@update atual
            atual.copy(
                cadastro = pendente.copy(
                    carregandoSugestao = false,
                    nomeSugerido = sugestao?.nome.orEmpty(),
                    marcaSugerida = sugestao?.marca,
                    imagemUrl = sugestao?.imagemUrl,
                    categoria = sugestao?.categoria,
                    veioDaInternet = sugestao != null,
                ),
            )
        }
    }

    fun confirmarCadastro(nome: String, marca: String?) {
        val pendente = _estado.value.cadastro ?: return
        val nomeLimpo = nome.trim().ifEmpty { pendente.codigoBarras }
        viewModelScope.launch {
            val produto = ProdutoEntity(
                codigoBarras = pendente.codigoBarras,
                nome = nomeLimpo,
                marca = marca?.trim()?.takeIf { it.isNotEmpty() },
                imagemUrl = pendente.imagemUrl,
                categoria = pendente.categoria,
                origemNome = if (pendente.veioDaInternet && nomeLimpo == pendente.nomeSugerido) {
                    ProdutoEntity.ORIGEM_OFF
                } else {
                    ProdutoEntity.ORIGEM_MANUAL
                },
            )
            val resultado = repositorio.cadastrarEAplicar(produto, _estado.value.modo)
            _estado.update { it.copy(cadastro = null) }
            mostrarRegistrada(resultado)
            sync.solicitarEnvio()
        }
    }

    fun cancelarCadastro() {
        val pendente = _estado.value.cadastro
        if (pendente != null) ultimaLeituraPorCodigo.remove(pendente.codigoBarras)
        _estado.update { it.copy(cadastro = null) }
    }

    fun desfazerUltima() {
        val ultima = _estado.value.ultima ?: return
        viewModelScope.launch {
            val ok = repositorio.desfazer(ultima.eventoId)
            ultimaLeituraPorCodigo.remove(ultima.codigoBarras)
            if (ok) sync.solicitarEnvio()
            _estado.update {
                it.copy(
                    ultima = null,
                    lidosNaSessao = (it.lidosNaSessao - 1).coerceAtLeast(0),
                    aviso = if (ok) "Desfeito: ${ultima.nome}" else "Nada para desfazer",
                )
            }
        }
    }

    fun alternarLanterna() = _estado.update { it.copy(lanterna = !it.lanterna) }

    fun avisoMostrado() = _estado.update { it.copy(aviso = null) }

    private fun mostrarRegistrada(resultado: ResultadoLeitura.Registrada) {
        val q = resultado.resultado.depois
        val descricao = when (resultado.modo) {
            Modo.MERCADO -> "no carrinho"
            Modo.GUARDAR -> "no estoque"
            Modo.ACABOU -> "na lista de compras"
        }
        val visivel = q.exibir()
        val quantidade = when (resultado.modo) {
            Modo.MERCADO -> visivel.carrinho
            Modo.GUARDAR -> visivel.estoque
            Modo.ACABOU -> visivel.lista
        }
        _estado.update {
            it.copy(
                ultima = UltimaLeitura(
                    codigoBarras = resultado.produto.codigoBarras,
                    nome = resultado.produto.nome,
                    eventoId = resultado.eventoId,
                    descricao = descricao,
                    quantidadeNaLista = quantidade,
                ),
                lidosNaSessao = it.lidosNaSessao + 1,
            )
        }
    }
}
