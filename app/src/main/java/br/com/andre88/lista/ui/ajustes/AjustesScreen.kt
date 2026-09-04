package br.com.andre88.lista.ui.ajustes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.andre88.lista.AppContainer
import br.com.andre88.lista.data.BackupDados
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    container: AppContainer,
    aoVoltar: () -> Unit,
    aoAbrirCasa: () -> Unit,
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val preferencias = container.preferencias

    val sincronizacao by preferencias.sincronizacao.collectAsStateWithLifecycle()
    val consultarOff by preferencias.consultarOpenFoodFacts.collectAsStateWithLifecycle()
    val somAoLer by preferencias.somAoLer.collectAsStateWithLifecycle()
    val cooldown by preferencias.cooldownMs.collectAsStateWithLifecycle()

    val exportar = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destino: Uri? ->
        if (destino == null) return@rememberLauncherForActivityResult
        escopo.launch {
            val resultado = runCatching {
                val dados = container.repositorio.exportar()
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openOutputStream(destino)?.use { saida ->
                        saida.write(BackupDados.paraTexto(dados).toByteArray())
                    } ?: error("Não foi possível escrever no arquivo")
                }
                dados
            }
            snackbar.showSnackbar(
                resultado.fold(
                    onSuccess = { "Backup salvo: ${it.produtos.size} produto(s)" },
                    onFailure = { "Falha ao salvar o backup: ${it.message}" },
                ),
            )
        }
    }

    val importar = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { origem: Uri? ->
        if (origem == null) return@rememberLauncherForActivityResult
        escopo.launch {
            val resultado = runCatching {
                val texto = withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(origem)?.use { it.readBytes().decodeToString() }
                        ?: error("Não foi possível ler o arquivo")
                }
                val dados = BackupDados.deTexto(texto)
                container.repositorio.importar(dados)
                dados
            }
            snackbar.showSnackbar(
                resultado.fold(
                    onSuccess = { "Backup restaurado: ${it.produtos.size} produto(s)" },
                    onFailure = { "Arquivo inválido: ${it.message}" },
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = aoAbrirCasa,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Compartilhar com outra pessoa", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            sincronizacao.casa != null ->
                                "Casa \"${sincronizacao.casa!!.nome}\" · código ${sincronizacao.casa!!.codigo}"
                            sincronizacao.registrado -> "Servidor conectado. Falta criar ou entrar numa casa."
                            else -> "Use o mesmo estoque e a mesma lista em dois celulares."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            LinhaAjuste(
                titulo = "Buscar nome na internet",
                descricao = "Ao ler um código novo, procura o produto no Open Food Facts. " +
                    "Desligado, você digita o nome na mão.",
            ) {
                Switch(checked = consultarOff, onCheckedChange = preferencias::definirConsultarOpenFoodFacts)
            }

            HorizontalDivider()

            LinhaAjuste(
                titulo = "Bipar a cada leitura",
                descricao = "Som curto de confirmação. A vibração continua ligada de qualquer forma.",
            ) {
                Switch(checked = somAoLer, onCheckedChange = preferencias::definirSomAoLer)
            }

            HorizontalDivider()

            Column {
                Text("Intervalo entre leituras iguais", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Evita contar a mesma embalagem duas vezes: ${"%.1f".format(cooldown / 1000f)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = cooldown.toFloat(),
                    onValueChange = { preferencias.definirCooldown(it.toLong()) },
                    valueRange = 500f..6000f,
                    steps = 10,
                )
            }

            HorizontalDivider()

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Os dados ficam só neste celular. Exporte um arquivo JSON antes de trocar de aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { exportar.launch("lista-backup.json") }) { Text("Exportar") }
                OutlinedButton(onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("Importar")
                }
            }
            Text(
                text = "Importar substitui todos os dados atuais pelos do arquivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LinhaAjuste(
    titulo: String,
    descricao: String,
    controle: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                text = descricao,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        controle()
    }
}
