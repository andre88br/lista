package br.com.andre88.lista.domain

/**
 * Nucleo de regra do app, sem nenhuma dependencia de Android: dado o estado atual
 * de um produto e o modo ativo, diz o quanto cada contador muda. Toda leitura
 * vale uma unidade.
 *
 * MERCADO  -> carrinho +1, lista   -1 (so se houver algo na lista)
 * GUARDAR  -> estoque  +1, carrinho -1 (so se houver algo no carrinho)
 * ACABOU   -> lista    +1, estoque  -1 (so se houver algo no estoque)
 *
 * Os deltas sao calculados sobre o valor **exibido**, e uma leitura que aumenta
 * um contador tambem quita a "divida" dele (o quanto ele esta abaixo de zero).
 * Sem isso, guardar um produto cujo estoque ficou em -1 nao mostraria nada na
 * tela, e a pessoa acharia que a leitura falhou.
 */
object ScanTransitions {

    fun deltasDe(atual: ItemQtds, modo: Modo): Deltas {
        val visivel = atual.exibir()
        return when (modo) {
            Modo.MERCADO -> Deltas(
                carrinho = 1 + divida(atual.carrinho),
                lista = if (visivel.lista > 0) -1 else 0,
            )

            Modo.GUARDAR -> Deltas(
                estoque = 1 + divida(atual.estoque),
                carrinho = if (visivel.carrinho > 0) -1 else 0,
            )

            Modo.ACABOU -> Deltas(
                lista = 1 + divida(atual.lista),
                estoque = if (visivel.estoque > 0) -1 else 0,
            )
        }
    }

    fun aplicar(atual: ItemQtds, modo: Modo): ScanResultado {
        val deltas = deltasDe(atual, modo)
        return ScanResultado(antes = atual, depois = atual + deltas, deltas = deltas)
    }

    /** Aplica um delta que veio do outro celular (ou do historico). */
    fun aplicarDeltas(atual: ItemQtds, deltas: Deltas): ItemQtds = atual + deltas

    /**
     * Deltas de um ajuste manual (os botoes + e - nas listas). Somar leva em conta
     * a divida, e subtrair so mexe se houver algo visivel para tirar.
     */
    fun deltasDeAjuste(atual: ItemQtds, campo: Campo, passo: Int): Deltas {
        val visivel = atual.exibir()
        val (valorCru, valorVisivel) = when (campo) {
            Campo.ESTOQUE -> atual.estoque to visivel.estoque
            Campo.LISTA -> atual.lista to visivel.lista
            Campo.CARRINHO -> atual.carrinho to visivel.carrinho
        }
        val variacao = when {
            passo > 0 -> passo + divida(valorCru)
            passo < 0 -> -minOf(-passo, valorVisivel)
            else -> 0
        }
        return deltaNoCampo(campo, variacao)
    }

    /** Deltas para zerar um contador (remover da lista, tirar do estoque). */
    fun deltasParaZerar(atual: ItemQtds, campo: Campo): Deltas {
        val visivel = atual.exibir()
        val valor = when (campo) {
            Campo.ESTOQUE -> visivel.estoque
            Campo.LISTA -> visivel.lista
            Campo.CARRINHO -> visivel.carrinho
        }
        return deltaNoCampo(campo, -valor)
    }

    private fun deltaNoCampo(campo: Campo, variacao: Int): Deltas = when (campo) {
        Campo.ESTOQUE -> Deltas(estoque = variacao)
        Campo.LISTA -> Deltas(lista = variacao)
        Campo.CARRINHO -> Deltas(carrinho = variacao)
    }

    /** O quanto um contador esta abaixo de zero. */
    private fun divida(valorCru: Int): Int = if (valorCru < 0) -valorCru else 0
}
