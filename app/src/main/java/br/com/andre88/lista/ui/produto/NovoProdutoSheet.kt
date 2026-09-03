package br.com.andre88.lista.ui.produto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.ui.scanner.CadastroPendente
import coil.compose.AsyncImage

/**
 * Cadastro rapido de um codigo desconhecido. Acontece uma unica vez por produto:
 * a partir dai o codigo ja e reconhecido, mesmo sem internet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovoProdutoSheet(
    pendente: CadastroPendente,
    modo: Modo,
    aoConfirmar: (nome: String, marca: String?) -> Unit,
    aoCancelar: () -> Unit,
) {
    val estadoSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nome by remember(pendente.codigoBarras) { mutableStateOf(pendente.nomeSugerido) }
    var marca by remember(pendente.codigoBarras) { mutableStateOf(pendente.marcaSugerida.orEmpty()) }

    // Quando a sugestao chega da internet, preenche o campo que ainda esta vazio.
    LaunchedEffect(pendente.nomeSugerido) {
        if (nome.isBlank() && pendente.nomeSugerido.isNotBlank()) nome = pendente.nomeSugerido
        if (marca.isBlank() && !pendente.marcaSugerida.isNullOrBlank()) marca = pendente.marcaSugerida
    }

    ModalBottomSheet(onDismissRequest = aoCancelar, sheetState = estadoSheet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Produto novo", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Código ${pendente.codigoBarras}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                pendente.carregandoSugestao -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Procurando o nome na internet…", style = MaterialTheme.typography.bodyMedium)
                }

                pendente.veioDaInternet -> Text(
                    text = "Nome encontrado no Open Food Facts. Confira e ajuste se quiser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                else -> Text(
                    text = "Não achamos este código na base pública. Dê um nome para reconhecê-lo depois.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                pendente.imagemUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = { Text("Nome do produto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = marca,
                        onValueChange = { marca = it },
                        label = { Text("Marca (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text(
                text = when (modo) {
                    Modo.MERCADO -> "Ao salvar, vai direto para os comprados."
                    Modo.GUARDAR -> "Ao salvar, entra no estoque."
                    Modo.ACABOU -> "Ao salvar, entra na lista de compras."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = aoCancelar) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { aoConfirmar(nome, marca.takeIf { it.isNotBlank() }) },
                    enabled = nome.isNotBlank(),
                ) { Text("Salvar e registrar") }
            }
        }
    }
}
