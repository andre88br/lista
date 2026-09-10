package br.com.andre88.lista.data.push

import android.content.Context
import android.util.Log
import br.com.andre88.lista.BuildConfig
import br.com.andre88.lista.data.sync.SyncRepositorio
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Notificacao push (FCM) para avisar o outro aparelho mesmo com o app fechado.
 * Configurada so por BuildConfig, sem o plugin google-services nem o arquivo
 * google-services.json — os 4 valores que ele traria nao sao segredo (so
 * identificam o app no Firebase, como o Client ID do login), e cabem em
 * gradle.properties do mesmo jeito. Sem eles, o recurso fica desligado e o
 * app continua funcionando pelo SSE (app aberto) e pelo ciclo periodico.
 */
class FirebasePush(private val contexto: Context) {

    val configurado: Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    // Sem o plugin google-services nao existe app "[DEFAULT]" pronto; e preciso
    // inicializar na mao, uma unica vez, antes de qualquer chamada ao FCM.
    private val app: FirebaseApp? by lazy {
        if (!configurado) return@lazy null
        FirebaseApp.getApps(contexto).firstOrNull()
            ?: FirebaseApp.initializeApp(
                contexto,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
    }

    /** Garante que o Firebase esta inicializado, para o servico de mensagens funcionar. */
    fun inicializar() {
        app
    }

    /** Busca o token atual do aparelho e manda para o servidor, se houver sessao. */
    fun registrarToken(sync: SyncRepositorio, escopo: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        if (app == null) return
        escopo.launch {
            runCatching {
                // Tasks.await() e bloqueante e proibe a thread principal por conta
                // disso; escopo pode vir do viewModelScope (thread principal), daqui
                // a garantia de rodar em segundo plano.
                val token = withContext(Dispatchers.IO) { Tasks.await(FirebaseMessaging.getInstance().token) }
                sync.registrarTokenFcm(token)
            }.onFailure { Log.d(TAG, "nao deu para registrar o token de push: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "FirebasePush"
    }
}
