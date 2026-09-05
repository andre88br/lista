package br.com.andre88.lista.ui.casa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.andre88.lista.data.ListaRepository
import br.com.andre88.lista.data.auth.LoginGoogle
import br.com.andre88.lista.data.sync.ResultadoSync
import br.com.andre88.lista.data.sync.SyncRepositorio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CasaUiState(
    val ocupado: Boolean = false,
    val mensagem: String? = null,
    val erro: String? = null,
)

class CasaViewModel(
    private val sync: SyncRepositorio,
    private val login: LoginGoogle,
    repositorio: ListaRepository,
) : ViewModel() {

    val sincronizacao = sync.estado

    val pendentes: StateFlow<Int> = repositorio.eventosPendentes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _ui = MutableStateFlow(CasaUiState())
    val ui: StateFlow<CasaUiState> = _ui.asStateFlow()

    fun trocarServidor(url: String) = executar("Servidor alterado; entre de novo") {
        sync.configurarServidor(url)
    }

    fun criarCasa(nome: String) = executar("Casa criada") {
        sync.criarCasa(nome.ifBlank { "Minha casa" }).map { }
    }

    fun entrar(codigo: String, juntarMeusItens: Boolean) = executar("Pronto, vocês estão na mesma casa") {
        sync.entrarNaCasa(codigo, juntarMeusItens).map { }
    }

    /** Encerra a sessao neste aparelho; o app volta para a tela de login. */
    fun sairDaConta() = viewModelScope.launch {
        login.esquecerConta()
        sync.sair()
    }

    fun gerarNovoCodigo() = executar("Código novo gerado") { sync.gerarNovoCodigo().map { } }

    fun sair() = executar("Você saiu da casa") { sync.sairDaCasa() }

    fun atualizarCasa() = viewModelScope.launch { sync.atualizarDadosDaCasa() }

    fun sincronizarAgora() = viewModelScope.launch {
        _ui.value = _ui.value.copy(ocupado = true, mensagem = null, erro = null)
        val resultado = sync.sincronizar()
        sync.atualizarDadosDaCasa()
        _ui.value = when (resultado) {
            is ResultadoSync.Ok -> CasaUiState(
                mensagem = "Enviado: ${resultado.enviados} · recebido: ${resultado.recebidos}",
            )
            is ResultadoSync.Falhou -> CasaUiState(erro = resultado.mensagem)
            ResultadoSync.Desligada -> CasaUiState(erro = "A sincronização ainda não está configurada")
        }
    }

    fun mensagemMostrada() {
        _ui.value = _ui.value.copy(mensagem = null, erro = null)
    }

    private fun executar(sucesso: String, acao: suspend () -> Result<Unit>) = viewModelScope.launch {
        _ui.value = CasaUiState(ocupado = true)
        _ui.value = acao()
            .fold(
                onSuccess = { CasaUiState(mensagem = sucesso) },
                onFailure = { CasaUiState(erro = it.message ?: "não deu certo") },
            )
    }
}
