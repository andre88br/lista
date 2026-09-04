package br.com.andre88.lista.ui.casa

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.andre88.lista.AppContainer
import br.com.andre88.lista.ui.fabricaCasa
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasaScreen(
    container: AppContainer,
    aoVoltar: () -> Unit,
) {
    val viewModel: CasaViewModel = viewModel(factory = fabricaCasa(container))
    val estado by viewModel.sincronizacao.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pendentes by viewModel.pendentes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val contexto = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(ui.mensagem, ui.erro) {
        (ui.erro ?: ui.mensagem)?.let {
            snackbar.showSnackbar(it)
            viewModel.mensagemMostrada()
        }
    }
    LaunchedEffect(Unit) { if (estado.ativa) viewModel.atualizarCasa() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compartilhar a lista") },
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
            if (ui.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                estado.servidorUrl == null -> SemServidorNoApk()

                estado.casa == null ->
                    PassoCasa(ocupado = ui.ocupado, aoCriar = viewModel::criarCasa, aoEntrar = viewModel::entrar)

                else -> CasaAtiva(
                    nome = estado.casa!!.nome,
                    codigo = estado.casa!!.codigo,
                    membros = estado.casa!!.membros,
                    ultimaSync = estado.ultimaSync,
                    ultimoErro = estado.ultimoErro,
                    pendentes = pendentes,
                    ocupado = ui.ocupado,
                    aoCompartilhar = {
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Instale o app da lista de compras e use este código para entrar na nossa casa: " +
                                    estado.casa!!.codigo,
                            )
                        }
                        contexto.startActivity(Intent.createChooser(envio, "Enviar o código"))
                    },
                    aoSincronizar = viewModel::sincronizarAgora,
                    aoNovoCodigo = viewModel::gerarNovoCodigo,
                    aoSair = viewModel::sair,
                )
            }

            if (estado.servidorUrl != null) {
                ServidorAvancado(
                    url = estado.servidorUrl!!,
                    ocupado = ui.ocupado,
                    aoTrocar = viewModel::trocarServidor,
                )
            }
        }
    }
}

/** APK gerado sem endereco de servidor: so acontece em build feito na mao. */
@Composable
private fun SemServidorNoApk() {
    Text("Sincronização indisponível", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Este APK foi gerado sem endereço de servidor. Baixe a versão publicada " +
            "pelo repositório, ou informe um endereço em \"Servidor\", abaixo.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * O endereco do servidor ja vem no APK, entao fica recolhido: quem usa o app
 * nao precisa saber que ele existe. Serve para quem hospeda em outro lugar ou
 * para o dia em que o endereco mudar.
 */
@Composable
private fun ServidorAvancado(url: String, ocupado: Boolean, aoTrocar: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    var novoEndereco by remember(url) { mutableStateOf(url) }

    HorizontalDivider()
    TextButton(onClick = { aberto = !aberto }, modifier = Modifier.fillMaxWidth()) {
        Text(if (aberto) "Ocultar servidor" else "Servidor (avançado)")
    }
    if (aberto) {
        Text(
            text = "Em uso: $url",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = novoEndereco,
            onValueChange = { novoEndereco = it.trim() },
            label = { Text("Endereço do servidor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { aoTrocar(novoEndereco) },
            enabled = !ocupado && novoEndereco != url && novoEndereco.length > 10,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Usar este servidor") }
    }
}

@Composable
private fun PassoCasa(
    ocupado: Boolean,
    aoCriar: (String) -> Unit,
    aoEntrar: (String, Boolean) -> Unit,
) {
    var nome by remember { mutableStateOf("Minha casa") }
    var codigo by remember { mutableStateOf("") }
    var perguntandoJuntar by remember { mutableStateOf(false) }

    Text("Criar uma casa", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Gera um código para a outra pessoa entrar e passar a dividir a mesma " +
            "lista, o mesmo estoque e os mesmos comprados. O que já está neste celular vai junto.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = nome,
        onValueChange = { nome = it },
        label = { Text("Nome da casa") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { aoCriar(nome) }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
        Text("Criar casa")
    }

    HorizontalDivider()

    Text("Ou entrar com um código", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = codigo,
        onValueChange = { codigo = it.uppercase() },
        label = { Text("Código (ex.: 4KJ2-9WPX)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { perguntandoJuntar = true },
        enabled = !ocupado && codigo.filter { it.isLetterOrDigit() }.length == 8,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Entrar na casa") }

    if (perguntandoJuntar) {
        AlertDialog(
            onDismissRequest = { perguntandoJuntar = false },
            title = { Text("E o que já está neste celular?") },
            text = {
                Text(
                    "Você pode somar seus itens aos da casa, ou descartar os daqui e usar só " +
                        "o que já existe na casa.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    perguntandoJuntar = false
                    aoEntrar(codigo, true)
                }) { Text("Juntar os meus") }
            },
            dismissButton = {
                TextButton(onClick = {
                    perguntandoJuntar = false
                    aoEntrar(codigo, false)
                }) { Text("Usar só os da casa") }
            },
        )
    }
}

@Composable
private fun CasaAtiva(
    nome: String,
    codigo: String,
    membros: Int,
    ultimaSync: Long,
    ultimoErro: String?,
    pendentes: Int,
    ocupado: Boolean,
    aoCompartilhar: () -> Unit,
    aoSincronizar: () -> Unit,
    aoNovoCodigo: () -> Unit,
    aoSair: () -> Unit,
) {
    var confirmandoSaida by remember { mutableStateOf(false) }

    Text(nome, style = MaterialTheme.typography.titleLarge)
    Text(
        text = if (membros > 1) "$membros aparelhos nesta casa" else "Só este aparelho por enquanto",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Código para a outra pessoa entrar", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = codigo,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = aoCompartilhar) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enviar o código")
            }
        }
    }

    HorizontalDivider()

    Text("Sincronização", style = MaterialTheme.typography.titleMedium)
    Text(
        text = when {
            pendentes > 0 -> "$pendentes leitura(s) esperando conexão"
            ultimaSync > 0 -> "Tudo enviado. Última vez: ${dataLegivel(ultimaSync)}"
            else -> "Ainda não sincronizou"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ultimoErro?.let {
        Text("Último erro: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = aoSincronizar, enabled = !ocupado) { Text("Sincronizar agora") }
        Spacer(Modifier.width(12.dp))
        if (ocupado) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }

    HorizontalDivider()

    OutlinedButton(onClick = aoNovoCodigo, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
        Text("Gerar um código novo")
    }
    Text(
        text = "Use se o código antigo foi parar em quem não devia. Quem já está na casa continua dentro.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    TextButton(onClick = { confirmandoSaida = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Sair da casa", color = MaterialTheme.colorScheme.error)
    }

    if (confirmandoSaida) {
        AlertDialog(
            onDismissRequest = { confirmandoSaida = false },
            title = { Text("Sair da casa?") },
            text = {
                Text(
                    "Este celular para de sincronizar, mas as listas continuam aqui e a outra " +
                        "pessoa não perde nada.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoSaida = false
                    aoSair()
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { confirmandoSaida = false }) { Text("Cancelar") } },
        )
    }
}

private fun dataLegivel(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
