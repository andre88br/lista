package br.com.andre88.lista.ui.login

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.andre88.lista.data.auth.ErroDeLogin
import br.com.andre88.lista.data.auth.LoginGoogle
import br.com.andre88.lista.data.sync.SyncRepositorio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val entrando: Boolean = false,
    val erro: String? = null,
)

class LoginViewModel(
    private val login: LoginGoogle,
    private val sync: SyncRepositorio,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    val estado = sync.estado

    private val nomeDoAparelho = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun entrar() = viewModelScope.launch {
        _ui.value = LoginUiState(entrando = true)
        try {
            val idToken = login.pedirIdToken()
            sync.entrarComGoogle(idToken, nomeDoAparelho)
                .onSuccess { _ui.value = LoginUiState() }
                .onFailure { _ui.value = LoginUiState(erro = mensagemDe(it)) }
        } catch (erro: ErroDeLogin) {
            // Cancelar nao e erro: a pessoa so mudou de ideia.
            _ui.value = LoginUiState(erro = if (erro.cancelado) null else erro.message)
        }
    }

    fun erroMostrado() {
        _ui.value = _ui.value.copy(erro = null)
    }

    private fun mensagemDe(erro: Throwable): String = when {
        erro.message?.contains("nao configurado", ignoreCase = true) == true ->
            "O servidor ainda não está configurado para login com Google."
        erro.message?.contains("invalido", ignoreCase = true) == true ->
            "O Google recusou este login. Tente de novo."
        else -> "Não consegui falar com o servidor: ${erro.message ?: "erro desconhecido"}"
    }
}
