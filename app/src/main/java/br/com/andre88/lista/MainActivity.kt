package br.com.andre88.lista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.andre88.lista.ui.AppLista
import br.com.andre88.lista.ui.tema.ListaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ListaApp).container
        setContent {
            ListaTheme {
                AppLista(container = container)
            }
        }
    }
}
