package br.com.andre88.lista.domain

/**
 * Os tres momentos do ciclo de um produto na casa. O usuario escolhe o modo antes
 * de escanear, e cada leitura aplica a transicao correspondente.
 */
enum class Modo {
    /** No mercado: o produto foi para o carrinho (sai da lista de compras). */
    MERCADO,

    /** Guardando as compras em casa: o produto entra no estoque (sai do carrinho). */
    GUARDAR,

    /** O produto acabou: sai do estoque e entra na lista de compras. */
    ACABOU;

    companion object {
        fun deNome(nome: String?): Modo = entries.firstOrNull { it.name == nome } ?: MERCADO
    }
}
