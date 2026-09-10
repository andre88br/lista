package br.com.andre88.lista

import android.app.Application
import br.com.andre88.lista.data.sync.SyncWorker

class ListaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Inicializa sempre (nao so quando ha sessao): quando o sistema acorda o
        // processo so para entregar uma notificacao push, e o onCreate que roda
        // primeiro, antes do FirebaseMessagingService precisar do Firebase pronto.
        container.firebasePush.inicializar()

        // So agenda/registra se a sincronizacao estiver configurada; sem casa, o app e 100% local.
        if (container.preferencias.sincronizacao.value.ativa) {
            SyncWorker.agendarPeriodico(this)
            container.firebasePush.registrarToken(container.sync)
        }
    }
}
