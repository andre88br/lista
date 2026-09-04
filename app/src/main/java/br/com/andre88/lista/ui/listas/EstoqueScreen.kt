package br.com.andre88.lista.ui.listas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
fun EstoqueScreen(
    viewModel: ListasViewModel,
    aoEscanear: () -> Unit,
) {
    val itens by viewModel.estoque.collectAsStateWithLifecycle()
    val busca by viewModel.busca.collectAsStateWithLifecycle()
    val mensagem by viewModel.mensagem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbar.showSnackbar(it)
            viewModel.mensagemMostrada()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Estoque de casa") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = aoEscanear,
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("Escanear o que acabou") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = busca,
                onValueChange = viewModel::buscar,
                label = { Text("Buscar no estoque") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (itens.isEmpty()) {
                ListaVazia(
                    titulo = if (busca.isBlank()) "Estoque vazio" else "Nada encontrado",
                    descricao = if (busca.isBlank()) {
                        "Ao guardar as compras, escaneie no modo \"Guardando as compras\" para montar o estoque."
                    } else {
                        "Nenhum produto do estoque combina com \"$busca\"."
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(itens, key = { it.codigoBarras }) { item ->
                        LinhaItem(
                            item = item,
                            quantidade = item.qtdEstoque,
                            aoSomar = { viewModel.ajustar(item, Campo.ESTOQUE, 1) },
                            aoSubtrair = { viewModel.ajustar(item, Campo.ESTOQUE, -1) },
                            acao = {
                                IconButton(onClick = { viewModel.marcarAcabou(item) }) {
                                    Icon(
                                        Icons.Filled.RemoveShoppingCart,
                                        contentDescription = "Acabou, colocar na lista",
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
