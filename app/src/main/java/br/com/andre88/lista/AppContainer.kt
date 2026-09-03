package br.com.andre88.lista

import android.content.Context
import br.com.andre88.lista.data.ListaRepository
import br.com.andre88.lista.data.Preferencias
import br.com.andre88.lista.data.db.AppDatabase
import br.com.andre88.lista.data.remote.OpenFoodFactsClient

/** Injecao de dependencia na mao: o app e pequeno o bastante para nao precisar de framework. */
class AppContainer(context: Context) {

    val preferencias: Preferencias by lazy { Preferencias(context) }

    private val database: AppDatabase by lazy { AppDatabase.criar(context) }

    private val openFoodFacts: OpenFoodFactsClient by lazy { OpenFoodFactsClient() }

    val repositorio: ListaRepository by lazy {
        ListaRepository(db = database, openFoodFacts = openFoodFacts, preferencias = preferencias)
    }
}
