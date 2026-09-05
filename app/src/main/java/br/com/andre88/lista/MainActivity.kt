package br.com.andre88.lista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.andre88.lista.ui.AppLista
import br.com.andre88.lista.ui.login.LoginScreen
import br.com.andre88.lista.ui.tema.ListaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ListaApp).container
        setContent {
            ListaTheme {
                val sincronizacao by container.preferencias.sincronizacao.collectAsStateWithLifecycle()
                // O login acontece uma vez; depois a sessao fica guardada e o
                // app abre direto, mesmo sem internet.
                if (sincronizacao.registrado) {
                    AppLista(container = container)
                } else {
                    LoginScreen(container = container)
                }
            }
        }
    }
}
