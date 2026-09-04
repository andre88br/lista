package br.com.andre88.lista.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RespostaDispositivo(
    @SerialName("dispositivoId") val dispositivoId: String,
    val token: String,
)

@Serializable
data class RespostaCasa(
    val casaId: String,
    val nome: String,
    val codigo: String,
    val membros: Int = 1,
)

@Serializable
data class EventoApi(
    val id: String,
    val codigoBarras: String,
    val modo: String,
    val deltaEstoque: Int = 0,
    val deltaLista: Int = 0,
    val deltaCarrinho: Int = 0,
    val autorId: String? = null,
    val criadoEm: String? = null,
    val seq: Long = 0,
)

@Serializable
data class ProdutoApi(
    val codigoBarras: String,
    val nome: String,
    val marca: String? = null,
    val imagemUrl: String? = null,
    val categoria: String? = null,
    val atualizadoEm: String? = null,
)

@Serializable
data class ItemApi(
    val codigoBarras: String,
    val estoque: Int = 0,
    val lista: Int = 0,
    val carrinho: Int = 0,
)

@Serializable
data class EnvioSync(
    val eventos: List<EventoApi> = emptyList(),
    val produtos: List<ProdutoApi> = emptyList(),
)

@Serializable
data class RespostaEnvio(
    val eventosAceitos: Int = 0,
    val produtosRecebidos: Int = 0,
    val seq: Long = 0,
)

@Serializable
data class RespostaLeitura(
    val seq: Long = 0,
    val agora: String? = null,
    val temMais: Boolean = false,
    val eventos: List<EventoApi> = emptyList(),
    val produtos: List<ProdutoApi> = emptyList(),
)

@Serializable
data class RespostaInstantaneo(
    val seq: Long = 0,
    val agora: String? = null,
    val produtos: List<ProdutoApi> = emptyList(),
    val itens: List<ItemApi> = emptyList(),
)

@Serializable
data class RespostaErro(val erro: String? = null)

/** Erro vindo do servidor, com a mensagem que da para mostrar na tela. */
class ErroDaApi(val status: Int, mensagem: String) : Exception(mensagem)
