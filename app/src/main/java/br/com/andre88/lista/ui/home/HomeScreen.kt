package br.com.andre88.lista.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.ui.listas.ListasViewModel
import br.com.andre88.lista.ui.tema.CoresModo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ListasViewModel,
    aoEscolherModo: (Modo) -> Unit,
    aoAbrirAjustes: () -> Unit,
) {
    val totais by viewModel.totais.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista & Estoque") },
                actions = {
                    IconButton(onClick = aoAbrirAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "O que você está fazendo agora?",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Escolha o momento e escaneie os produtos. Cada leitura vale uma unidade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(4.dp))

            CartaoModo(
                titulo = "Estou no mercado",
                descricao = "Cada leitura marca o produto como comprado e o tira da lista.",
                rodape = if (totais.lista > 0) "${totais.lista} item(ns) para comprar" else "Lista de compras vazia",
                icone = Icons.Filled.ShoppingCart,
                cor = CoresModo.mercado,
                aoClicar = { aoEscolherModo(Modo.MERCADO) },
            )

            CartaoModo(
                titulo = "Guardando as compras",
                descricao = "Cada leitura tira do carrinho e coloca no estoque de casa.",
                rodape = if (totais.carrinho > 0) "${totais.carrinho} item(ns) para guardar" else "Nada comprado no momento",
                icone = Icons.Filled.Inventory2,
                cor = CoresModo.guardar,
                aoClicar = { aoEscolherModo(Modo.GUARDAR) },
            )

            CartaoModo(
                titulo = "Acabou um produto",
                descricao = "Cada leitura tira do estoque e joga na lista de compras.",
                rodape = "${totais.estoque} item(ns) no estoque",
                icone = Icons.Filled.RemoveShoppingCart,
                cor = CoresModo.acabou,
                aoClicar = { aoEscolherModo(Modo.ACABOU) },
            )
        }
    }
}

@Composable
private fun CartaoModo(
    titulo: String,
    descricao: String,
    rodape: String,
    icone: ImageVector,
    cor: Color,
    aoClicar: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar),
        colors = CardDefaults.cardColors(containerColor = cor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = cor,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, CircleShape)
                    .padding(10.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = rodape,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
