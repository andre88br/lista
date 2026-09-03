package br.com.andre88.lista.domain

/**
 * Nucleo de regra do app, sem nenhuma dependencia de Android: dado o estado atual de
 * um produto e o modo ativo, diz qual e o novo estado. Toda leitura vale uma unidade.
 *
 * MERCADO  -> carrinho +1, lista   -1 (nunca abaixo de zero)
 * GUARDAR  -> estoque  +1, carrinho -1 (nunca abaixo de zero)
 * ACABOU   -> lista    +1, estoque  -1 (nunca abaixo de zero)
 */
object ScanTransitions {

    fun aplicar(atual: ItemQtds, modo: Modo): ScanResultado {
        val depois = when (modo) {
            Modo.MERCADO -> atual.copy(
                carrinho = atual.carrinho + 1,
                lista = (atual.lista - 1).coerceAtLeast(0),
            )

            Modo.GUARDAR -> atual.copy(
                estoque = atual.estoque + 1,
                carrinho = (atual.carrinho - 1).coerceAtLeast(0),
            )

            Modo.ACABOU -> atual.copy(
                lista = atual.lista + 1,
                estoque = (atual.estoque - 1).coerceAtLeast(0),
            )
        }
        return ScanResultado(antes = atual, depois = depois)
    }

    /**
     * Desfaz uma leitura subtraindo os deltas que foram efetivamente aplicados.
     * Usa os deltas guardados no historico, e nao o inverso do modo, porque uma
     * leitura pode ter sido parcialmente absorvida por um contador em zero.
     */
    fun reverter(atual: ItemQtds, deltaEstoque: Int, deltaLista: Int, deltaCarrinho: Int): ItemQtds =
        ItemQtds(
            estoque = (atual.estoque - deltaEstoque).coerceAtLeast(0),
            lista = (atual.lista - deltaLista).coerceAtLeast(0),
            carrinho = (atual.carrinho - deltaCarrinho).coerceAtLeast(0),
        )
}
