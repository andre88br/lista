package br.com.andre88.lista.ui.listas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.andre88.lista.domain.Campo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarrinhoScreen(
    viewModel: ListasViewModel,
    aoEscanear: () -> Unit,
) {
    val itens by viewModel.carrinho.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbar.showSnackbar(it)
            viewModel.mensagemMostrada()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comprados") },
                actions = {
                    if (itens.isNotEmpty()) {
                        TextButton(onClick = { viewModel.guardarTudoNoEstoque() }) {
                            Text("Guardar tudo")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = aoEscanear,
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("Escanear ao guardar") },
            )
        },
    ) { padding ->
        if (itens.isEmpty()) {
            ListaVazia(
                titulo = "Nada comprado ainda",
                descricao = "No mercado, escaneie no modo \"Estou no mercado\" e os produtos aparecem aqui.",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(itens, key = { it.codigoBarras }) { item ->
                    LinhaItem(
                        item = item,
                        quantidade = item.qtdCarrinho,
                        aoSomar = { viewModel.ajustar(item, Campo.CARRINHO, 1) },
                        aoSubtrair = { viewModel.ajustar(item, Campo.CARRINHO, -1) },
                        acao = {
                            IconButton(onClick = { viewModel.guardar(item) }) {
                                Icon(Icons.Filled.Inventory2, contentDescription = "Guardar no estoque")
                            }
                        },
                    )
                }
            }
        }
    }
}
