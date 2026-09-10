package br.com.andre88.lista.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.andre88.lista.ListaApp
import br.com.andre88.lista.MainActivity
import br.com.andre88.lista.R
import br.com.andre88.lista.data.sync.ResultadoSync
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/**
 * Recebe o "tem novidade" do servidor e sincroniza na hora, mesmo com o app
 * fechado — o SSE so alcanca quem esta com o app aberto. So mostra uma
 * notificacao quando a sincronizacao traz algo de fato novo, para nao avisar
 * a pessoa do que ela mesma acabou de escanear.
 *
 * O Firebase chama estes metodos numa thread de segundo plano (nao a
 * principal), entao bloquear aqui com runBlocking e seguro — e o jeito certo
 * de nao deixar o processo morrer no meio da sincronizacao antes de terminar.
 */
class ListaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val sync = (application as ListaApp).container.sync
        runBlocking {
            runCatching { sync.registrarTokenFcm(token) }
                .onFailure { Log.d(TAG, "nao deu para atualizar o token de push: ${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val sync = (application as ListaApp).container.sync
        runBlocking {
            try {
                when (val resultado = sync.sincronizar()) {
                    is ResultadoSync.Ok -> if (resultado.recebidos > 0) notificar(resultado.recebidos)
                    else -> Unit
                }
            } catch (erro: Exception) {
                Log.d(TAG, "sincronizacao pelo push falhou: ${erro.message}")
            }
        }
    }

    private fun notificar(quantos: Int) {
        criarCanalSeNecessario()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val abrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val texto = if (quantos == 1) "1 novidade na sua casa" else "$quantos novidades na sua casa"
        val notificacao = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("Lista & Estoque")
            .setContentText(texto)
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICACAO_ID, notificacao)
    }

    private fun criarCanalSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerenciador = getSystemService(NotificationManager::class.java) ?: return
        if (gerenciador.getNotificationChannel(CANAL_ID) != null) return
        gerenciador.createNotificationChannel(
            NotificationChannel(CANAL_ID, "Novidades da casa", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private companion object {
        const val TAG = "ListaMessagingService"
        const val CANAL_ID = "novidades_da_casa"
        const val NOTIFICACAO_ID = 1001
    }
}
