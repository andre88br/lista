package br.com.andre88.lista.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.andre88.lista.AppContainer
import br.com.andre88.lista.data.auth.diagnosticarLogin
import br.com.andre88.lista.ui.fabricaLogin

/**
 * Porta de entrada do app. O login acontece **uma vez**: depois disso a sessao
 * fica guardada e o app funciona offline, que e o que importa dentro do mercado.
 */
@Composable
fun LoginScreen(container: AppContainer) {
    val viewModel: LoginViewModel = viewModel(factory = fabricaLogin(container))
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // O contexto daqui e o da Activity, que e o que o seletor de contas exige.
    val contexto = LocalContext.current

    LaunchedEffect(ui.erro) {
        ui.erro?.let {
            snackbar.showSnackbar(it)
            viewModel.erroMostrado()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(20.dp),
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Lista & Estoque",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Entre com sua conta Google para a lista e o estoque acompanharem você, " +
                    "mesmo se trocar de celular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { viewModel.entrar(contexto) },
                enabled = !ui.entrando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.entrando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Entrando…")
                } else {
                    Text("Entrar com o Google")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "É uma vez só. Depois o app funciona sem internet — inclusive no mercado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
            DetalhesTecnicos(contexto)
        }
    }
}

/**
 * Quando o login falha, a causa quase sempre e uma diferenca entre o que esta
 * neste aparelho e o que foi registrado no Google Cloud. Mostrar os dois dados
 * aqui evita ter que descobrir isso por tentativa e erro.
 */
@Composable
private fun DetalhesTecnicos(contexto: android.content.Context) {
    var aberto by remember { mutableStateOf(false) }
    val diagnostico = remember { diagnosticarLogin(contexto) }

    TextButton(onClick = { aberto = !aberto }) {
        Text(if (aberto) "Ocultar detalhes técnicos" else "Detalhes técnicos")
    }

    if (aberto) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinhaTecnica("Pacote", diagnostico.pacote)
            LinhaTecnica("SHA-1 deste APK", diagnostico.sha1 ?: "não consegui ler")
            LinhaTecnica("Client ID", diagnostico.clientIdResumido ?: "não configurado")
            LinhaTecnica(
                "Google Play Services",
                if (diagnostico.temPlayServices) "instalado" else "AUSENTE",
            )
            Text(
                text = "O pacote e o SHA-1 acima precisam ser exatamente os registrados no " +
                    "cliente Android do Google Cloud. Se um deles diferir, o login falha antes " +
                    "de mostrar a escolha de conta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinhaTecnica(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelSmall)
        Text(
            text = valor,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
