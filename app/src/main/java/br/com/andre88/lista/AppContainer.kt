package br.com.andre88.lista

import android.content.Context
import br.com.andre88.lista.data.ListaRepository
import br.com.andre88.lista.data.Preferencias
import br.com.andre88.lista.data.db.AppDatabase
import br.com.andre88.lista.data.remote.OpenFoodFactsClient
import br.com.andre88.lista.data.sync.ApiCliente
import br.com.andre88.lista.data.sync.SyncRepositorio

/** Injecao de dependencia na mao: o app e pequeno o bastante para nao precisar de framework. */
class AppContainer(private val context: Context) {

    val preferencias: Preferencias by lazy { Preferencias(context) }

    private val database: AppDatabase by lazy { AppDatabase.criar(context) }

    private val openFoodFacts: OpenFoodFactsClient by lazy { OpenFoodFactsClient() }

    private val api: ApiCliente by lazy { ApiCliente() }

    val repositorio: ListaRepository by lazy {
        ListaRepository(db = database, openFoodFacts = openFoodFacts, preferencias = preferencias)
    }

    val sync: SyncRepositorio by lazy {
        SyncRepositorio(contexto = context.applicationContext, api = api, repositorio = repositorio, preferencias = preferencias)
    }
}
