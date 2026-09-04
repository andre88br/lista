package br.com.andre88.lista.domain

/**
 * Quantidades de um produto em cada lista.
 *
 * Os valores guardados sao a **soma crua** de todos os deltas ja aplicados e
 * podem ficar negativos: se duas pessoas marcarem "acabou" no ultimo pacote,
 * o estoque fica em -1. Guardar a soma crua e o que faz os dois celulares
 * chegarem ao mesmo numero, porque soma nao depende de ordem. Quem limita em
 * zero e a exibicao ([exibir]), nunca a gravacao.
 */
data class ItemQtds(
    val estoque: Int = 0,
    val lista: Int = 0,
    val carrinho: Int = 0,
) {
    /** O que aparece na tela: nenhum contador negativo. */
    fun exibir(): ItemQtds = ItemQtds(
        estoque = estoque.coerceAtLeast(0),
        lista = lista.coerceAtLeast(0),
        carrinho = carrinho.coerceAtLeast(0),
    )

    val vazio: Boolean get() = estoque <= 0 && lista <= 0 && carrinho <= 0

    operator fun plus(deltas: Deltas): ItemQtds = ItemQtds(
        estoque = estoque + deltas.estoque,
        lista = lista + deltas.lista,
        carrinho = carrinho + deltas.carrinho,
    )
}

/** O quanto uma acao mexeu em cada contador. E o que viaja entre os celulares. */
data class Deltas(
    val estoque: Int = 0,
    val lista: Int = 0,
    val carrinho: Int = 0,
) {
    val nulo: Boolean get() = estoque == 0 && lista == 0 && carrinho == 0

    operator fun unaryMinus(): Deltas = Deltas(-estoque, -lista, -carrinho)
}

/** Resultado de uma leitura: o estado antes, o depois e o delta que os liga. */
data class ScanResultado(
    val antes: ItemQtds,
    val depois: ItemQtds,
    val deltas: Deltas,
) {
    val semEfeito: Boolean get() = deltas.nulo
}
