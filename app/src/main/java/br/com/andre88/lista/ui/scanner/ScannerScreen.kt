package br.com.andre88.lista.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.andre88.lista.AppContainer
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.ui.fabricaScanner
import br.com.andre88.lista.ui.produto.NovoProdutoSheet
import br.com.andre88.lista.ui.tema.CoresModo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private fun tituloDoModo(modo: Modo) = when (modo) {
    Modo.MERCADO -> "No mercado"
    Modo.GUARDAR -> "Guardando as compras"
    Modo.ACABOU -> "Acabou"
}

private fun explicacaoDoModo(modo: Modo) = when (modo) {
    Modo.MERCADO -> "Cada leitura marca como comprado"
    Modo.GUARDAR -> "Cada leitura guarda no estoque"
    Modo.ACABOU -> "Cada leitura entra na lista de compras"
}

@Composable
fun ScannerScreen(
    modo: Modo,
    container: AppContainer,
    aoVoltar: () -> Unit,
) {
    val contexto = LocalContext.current
    val viewModel: ScannerViewModel = viewModel(
        key = "scanner_${modo.name}",
        factory = fabricaScanner(container, modo),
    )
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val somLigado by container.preferencias.somAoLer.collectAsStateWithLifecycle()

    var temPermissao by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        temPermissao = it
    }
    LaunchedEffect(Unit) {
        if (!temPermissao) pedirPermissao.launch(Manifest.permission.CAMERA)
    }

    val feedback = remember { Feedback(contexto) }
    DisposableEffect(Unit) { onDispose { feedback.liberar() } }
    LaunchedEffect(estado.ultima?.eventoId) {
        if (estado.ultima != null) feedback.sucesso(somLigado)
    }

    var digitando by remember { mutableStateOf(false) }
    val corDoModo = CoresModo.de(modo)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (temPermissao) {
            CameraPreview(
                lanternaLigada = estado.lanterna,
                aoLerCodigo = viewModel::aoLerCodigo,
            )
        } else {
            SemPermissao(aoPedir = { pedirPermissao.launch(Manifest.permission.CAMERA) })
        }

        // Cabecalho com o modo ativo, sempre visivel.
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(corDoModo)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = aoVoltar) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tituloDoModo(modo),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = explicacaoDoModo(modo),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { digitando = true }) {
                    Icon(Icons.Filled.Keyboard, contentDescription = "Digitar código", tint = Color.White)
                }
                IconButton(onClick = viewModel::alternarLanterna) {
                    Icon(
                        imageVector = if (estado.lanterna) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Lanterna",
                        tint = Color.White,
                    )
                }
            }
            if (estado.lidosNaSessao > 0) {
                Text(
                    text = "${estado.lidosNaSessao} leitura(s) nesta sessão",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        // Ultima leitura + desfazer.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            estado.aviso?.let { aviso ->
                Snackbar { Text(aviso) }
                LaunchedEffect(aviso) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.avisoMostrado()
                }
            }
            estado.ultima?.let { ultima ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = ultima.nome,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            )
                            Text(
                                text = "${ultima.quantidadeNaLista}x ${ultima.descricao}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = corDoModo,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = viewModel::desfazerUltima) {
                            Icon(Icons.Filled.Undo, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Desfazer")
                        }
                    }
                }
            }
            Text(
                text = "Aponte a câmera para o código de barras",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    estado.cadastro?.let { pendente ->
        NovoProdutoSheet(
            pendente = pendente,
            modo = modo,
            aoConfirmar = viewModel::confirmarCadastro,
            aoCancelar = viewModel::cancelarCadastro,
        )
    }

    if (digitando) {
        DialogoCodigoManual(
            aoConfirmar = { codigo ->
                digitando = false
                viewModel.aoDigitarCodigo(codigo)
            },
            aoCancelar = { digitando = false },
        )
    }
}

@Composable
private fun CameraPreview(
    lanternaLigada: Boolean,
    aoLerCodigo: (String) -> Unit,
) {
    val contexto = LocalContext.current
    val donoDoCiclo = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analisador = remember { BarcodeAnalyzer(aoLerCodigo) }
    val previewView = remember {
        PreviewView(contexto).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analisador.fechar()
            executor.shutdown()
        }
    }

    LaunchedEffect(previewView) {
        val provider = contexto.obterCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analise = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, analisador) }

        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                donoDoCiclo,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analise,
            )
        }
    }

    LaunchedEffect(lanternaLigada, camera) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(lanternaLigada)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun SemPermissao(aoPedir: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "O app precisa da câmera para ler os códigos de barras.",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = aoPedir) { Text("Permitir câmera") }
    }
}

@Composable
private fun DialogoCodigoManual(
    aoConfirmar: (String) -> Unit,
    aoCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text("Digitar código de barras") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.filter(Char::isDigit) },
                label = { Text("Código") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { aoConfirmar(texto) },
                enabled = texto.isNotBlank(),
            ) { Text("Usar") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

/** Espera o CameraX ficar pronto sem bloquear a thread principal. */
private suspend fun Context.obterCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuacao ->
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener(
            { continuacao.resume(futuro.get()) },
            ContextCompat.getMainExecutor(this),
        )
    }
