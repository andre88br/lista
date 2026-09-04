package br.com.andre88.lista

import android.app.Application
import br.com.andre88.lista.data.sync.SyncWorker

class ListaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // So agenda se a sincronizacao estiver configurada; sem casa, o app e 100% local.
        if (container.preferencias.sincronizacao.value.ativa) {
            SyncWorker.agendarPeriodico(this)
        }
    }
}
