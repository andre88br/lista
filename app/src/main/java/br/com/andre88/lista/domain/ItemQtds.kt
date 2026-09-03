package br.com.andre88.lista.domain

/** Quantidades de um mesmo produto em cada uma das tres listas. */
data class ItemQtds(
    val estoque: Int = 0,
    val lista: Int = 0,
    val carrinho: Int = 0,
) {
    val vazio: Boolean get() = estoque == 0 && lista == 0 && carrinho == 0
}

/**
 * Resultado de uma leitura: o novo estado e os deltas que foram *de fato* aplicados.
 * Os deltas sao guardados no historico para que o "Desfazer" reverta exatamente o
 * que aconteceu, mesmo quando algum contador ja estava em zero.
 */
data class ScanResultado(
    val antes: ItemQtds,
    val depois: ItemQtds,
) {
    val deltaEstoque: Int get() = depois.estoque - antes.estoque
    val deltaLista: Int get() = depois.lista - antes.lista
    val deltaCarrinho: Int get() = depois.carrinho - antes.carrinho

    /** Uma leitura que nao mudou nada (ex.: "acabou" com estoque zerado e item ja na lista). */
    val semEfeito: Boolean get() = antes == depois
}
