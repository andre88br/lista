package br.com.andre88.lista.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import br.com.andre88.lista.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Erro de login com uma mensagem que faz sentido para quem esta olhando a tela. */
class ErroDeLogin(mensagem: String, val cancelado: Boolean = false) : Exception(mensagem)

/**
 * Login com a conta Google do aparelho, pelo Credential Manager.
 *
 * O que sai daqui e o "ID token": um cracha assinado pelo Google que o servidor
 * confere. O app nunca ve senha nenhuma, e o token so vale para este aplicativo.
 */
class LoginGoogle(private val contexto: Context) {

    private val gerenciador by lazy { CredentialManager.create(contexto) }

    val configurado: Boolean get() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    suspend fun pedirIdToken(): String {
        if (!configurado) {
            throw ErroDeLogin("Este APK foi gerado sem a configuração do Google.")
        }

        val opcao = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            // false = mostra todas as contas do aparelho, inclusive na primeira vez.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val resposta = try {
            gerenciador.getCredential(contexto, GetCredentialRequest.Builder().addCredentialOption(opcao).build())
        } catch (erro: GetCredentialCancellationException) {
            throw ErroDeLogin("Login cancelado.", cancelado = true)
        } catch (erro: NoCredentialException) {
            throw ErroDeLogin(
                "Nenhuma conta Google encontrada neste aparelho. Adicione uma conta nas " +
                    "configurações do Android e tente de novo.",
            )
        } catch (erro: GetCredentialException) {
            throw ErroDeLogin("Não consegui falar com o Google: ${erro.message ?: erro.type}")
        }

        val credencial = resposta.credential
        if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credencial.data).idToken
        }
        throw ErroDeLogin("O Google devolveu uma credencial inesperada.")
    }

    /** Esquece a conta escolhida, para o próximo login voltar a perguntar. */
    suspend fun esquecerConta() {
        runCatching {
            gerenciador.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }
    }
}
