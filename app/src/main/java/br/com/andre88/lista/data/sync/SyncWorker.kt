package br.com.andre88.lista.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.andre88.lista.ListaApp
import java.util.concurrent.TimeUnit

/**
 * Sincroniza em segundo plano. E o que faz as leituras feitas no mercado sem
 * sinal subirem sozinhas quando a conexao volta.
 */
class SyncWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? ListaApp)?.container ?: return Result.success()
        return when (container.sync.sincronizar()) {
            is ResultadoSync.Ok -> Result.success()
            ResultadoSync.Desligada -> Result.success()
            // Sem rede ou servidor fora do ar: o WorkManager tenta de novo sozinho.
            is ResultadoSync.Falhou -> if (runAttemptCount < 5) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODICO = "sync-periodico"
        private const val AGORA = "sync-agora"

        private val exigeRede = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Rede de seguranca: mesmo com o app fechado, sobe o que ficou pendente. */
        fun agendarPeriodico(contexto: Context) {
            val pedido = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(exigeRede)
                .build()
            WorkManager.getInstance(contexto)
                .enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        /** Chamado logo depois de uma leitura, para o outro celular ver quase na hora. */
        fun agora(contexto: Context) {
            val pedido = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(exigeRede)
                .build()
            WorkManager.getInstance(contexto)
                .enqueueUniqueWork(AGORA, ExistingWorkPolicy.REPLACE, pedido)
        }

        fun cancelarTudo(contexto: Context) {
            WorkManager.getInstance(contexto).cancelUniqueWork(PERIODICO)
        }
    }
}
