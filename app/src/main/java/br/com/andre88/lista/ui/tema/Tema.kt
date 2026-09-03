package br.com.andre88.lista.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import br.com.andre88.lista.domain.Modo

private val VerdeMercado = Color(0xFF0F6B3F)
private val VerdeClaro = Color(0xFF7CD8A8)

private val EsquemaClaro = lightColorScheme(
    primary = VerdeMercado,
    onPrimary = Color.White,
    secondary = Color(0xFF3A6EA5),
    tertiary = Color(0xFFB35A00),
)

private val EsquemaEscuro = darkColorScheme(
    primary = VerdeClaro,
    onPrimary = Color(0xFF00391F),
    secondary = Color(0xFF9CC6FF),
    tertiary = Color(0xFFFFB77C),
)

/** Cada modo tem a sua cor, para nunca haver duvida do que a proxima leitura vai fazer. */
object CoresModo {
    val mercado = Color(0xFF3A6EA5)
    val guardar = Color(0xFF0F6B3F)
    val acabou = Color(0xFFB35A00)

    fun de(modo: Modo): Color = when (modo) {
        Modo.MERCADO -> mercado
        Modo.GUARDAR -> guardar
        Modo.ACABOU -> acabou
    }
}

@Composable
fun ListaTheme(escuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (escuro) EsquemaEscuro else EsquemaClaro,
        content = content,
    )
}
