package br.com.andre88.lista.ui.listas

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.andre88.lista.data.Campo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaComprasScreen(
    viewModel: ListasViewModel,
    aoEscanear: () -> Unit,
) {
    val itens by viewModel.listaDeCompras.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var adicionando by remember { mutableStateOf(false) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbar.showSnackbar(it)
            viewModel.mensagemMostrada()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista de compras") },
                actions = {
                    IconButton(onClick = { adicionando = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar item sem código")
                    }
                    IconButton(
                        onClick = {
                            val envio = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.textoDaLista())
                            }
                            contexto.startActivity(Intent.createChooser(envio, "Compartilhar lista"))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = aoEscanear,
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("Escanear no mercado") },
            )
        },
    ) { padding ->
        if (itens.isEmpty()) {
            ListaVazia(
                titulo = "Nada para comprar",
                descricao = "Quando um produto acabar, escaneie no modo \"Acabou\" que ele aparece aqui.",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(itens, key = { it.codigoBarras }) { item ->
                    LinhaItem(
                        item = item,
                        quantidade = item.qtdLista,
                        aoSomar = { viewModel.ajustar(item, Campo.LISTA, 1) },
                        aoSubtrair = { viewModel.ajustar(item, Campo.LISTA, -1) },
                        acao = {
                            IconButton(onClick = { viewModel.marcarComprado(item) }) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Marcar como comprado")
                            }
                        },
                    )
                }
            }
        }
    }

    if (adicionando) {
        var texto by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adicionando = false },
            title = { Text("Item sem código de barras") },
            text = {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Ex.: banana, pão francês") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.adicionarItemManual(texto)
                        adicionando = false
                    },
                    enabled = texto.isNotBlank(),
                ) { Text("Adicionar") }
            },
            dismissButton = { TextButton(onClick = { adicionando = false }) { Text("Cancelar") } },
        )
    }
}
