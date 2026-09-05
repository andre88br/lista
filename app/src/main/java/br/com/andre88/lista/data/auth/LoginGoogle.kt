package br.com.andre88.lista.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import br.com.andre88.lista.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Erro de login com uma mensagem que faz sentido para quem esta olhando a tela. */
class ErroDeLogin(mensagem: String, val cancelado: Boolean = false) : Exception(mensagem)

/**
 * Login com a conta Google do aparelho, pelo Credential Manager.
 *
 * O que sai daqui e o "ID token": um cracha assinado pelo Google que o servidor
 * confere. O app nunca ve senha nenhuma, e o token so vale para este aplicativo.
 */
class LoginGoogle(private val contextoDoApp: Context) {

    private val gerenciador by lazy { CredentialManager.create(contextoDoApp) }

    val configurado: Boolean get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    /**
     * @param contextoDaTela precisa ser o da Activity: o seletor de contas e uma
     *        tela, e com o contexto da aplicacao ele nao tem onde aparecer.
     */
    suspend fun pedirIdToken(contextoDaTela: Context): String {
        if (!configurado) {
            throw ErroDeLogin("Este APK foi gerado sem a configuração do Google.")
        }

        // GetSignInWithGoogleOption e a opcao feita para um botao "Entrar com o
        // Google": abre a escolha de conta sempre. A GetGoogleIdOption e para o
        // login automatico, e quando nao ha conta previamente autorizada ela
        // pode simplesmente nao mostrar nada - que era o que acontecia aqui.
        val opcao = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID).build()
        val pedido = GetCredentialRequest.Builder().addCredentialOption(opcao).build()
        val resposta = try {
            // Rede de seguranca: se o Google nunca responder, a tela nao pode
            // ficar presa no "Entrando..." para sempre.
            withTimeout(TEMPO_LIMITE_MS) {
                gerenciador.getCredential(contextoDaTela, pedido)
            }
        } catch (erro: TimeoutCancellationException) {
            throw ErroDeLogin("O Google demorou demais para responder. Tente de novo.")
        } catch (erro: GetCredentialCancellationException) {
            throw ErroDeLogin("Login cancelado.", cancelado = true)
        } catch (erro: NoCredentialException) {
            throw ErroDeLogin(
                "Nenhuma conta Google encontrada neste aparelho. Adicione uma conta nas " +
                    "configurações do Android e tente de novo.",
            )
        } catch (erro: GetCredentialException) {
            Log.w(TAG, "getCredential falhou: ${erro.type}", erro)
            throw ErroDeLogin(mensagemPara(erro))
        } catch (erro: Exception) {
            // Sem este ramo, um erro inesperado deixava a tela girando sem fim.
            Log.w(TAG, "falha inesperada no login", erro)
            throw ErroDeLogin("Falha inesperada no login: ${erro.javaClass.simpleName}")
        }

        val credencial = resposta.credential
        if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credencial.data).idToken
        }
        throw ErroDeLogin("O Google devolveu uma credencial inesperada.")
    }

    /**
     * Traduz os erros do Credential Manager para o que costuma ser a causa real,
     * porque as mensagens originais nao ajudam quem esta olhando a tela.
     */
    private fun mensagemPara(erro: GetCredentialException): String = when {
        erro.type.contains("INTERRUPTED") ->
            "O Google foi interrompido. Tente de novo."
        erro.type.contains("PROVIDER_CONFIGURATION") ->
            "Os serviços do Google Play deste aparelho não estão prontos para o login. " +
                "Verifique se estão atualizados."
        erro.message?.contains("10:", ignoreCase = true) == true || erro.type.contains("UNKNOWN") ->
            "O Google não reconheceu este aplicativo. Isso costuma ser o SHA-1 ou o nome do " +
                "pacote diferentes do que está registrado no Google Cloud, ou a conta fora da " +
                "lista de usuários de teste."
        else -> "Não consegui falar com o Google: ${erro.message ?: erro.type}"
    }

    /** Esquece a conta escolhida, para o próximo login voltar a perguntar. */
    suspend fun esquecerConta() {
        runCatching {
            gerenciador.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }
    }

    private companion object {
        const val TAG = "LoginGoogle"
        // Tempo suficiente para escolher a conta, mas curto o bastante para a
        // falha aparecer rapido quando a tela do Google nao chega a abrir.
        const val TEMPO_LIMITE_MS = 90 * 1000L
    }
}
