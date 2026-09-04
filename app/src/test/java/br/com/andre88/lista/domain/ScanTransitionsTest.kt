package br.com.andre88.lista.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTransitionsTest {

    private fun ItemQtds.visivel() = exibir()

    @Test
    fun `no mercado move da lista para o carrinho`() {
        val r = ScanTransitions.aplicar(ItemQtds(lista = 2), Modo.MERCADO)
        assertEquals(ItemQtds(lista = 1, carrinho = 1), r.depois)
    }

    @Test
    fun `no mercado sem estar na lista ainda vai para o carrinho`() {
        val r = ScanTransitions.aplicar(ItemQtds(), Modo.MERCADO)
        assertEquals(ItemQtds(carrinho = 1), r.depois)
        assertEquals(0, r.deltas.lista)
    }

    @Test
    fun `guardando move do carrinho para o estoque`() {
        val r = ScanTransitions.aplicar(ItemQtds(estoque = 1, carrinho = 2), Modo.GUARDAR)
        assertEquals(ItemQtds(estoque = 2, carrinho = 1), r.depois)
    }

    @Test
    fun `guardando sem carrinho apenas soma no estoque`() {
        val r = ScanTransitions.aplicar(ItemQtds(estoque = 3), Modo.GUARDAR)
        assertEquals(ItemQtds(estoque = 4), r.depois)
        assertEquals(0, r.deltas.carrinho)
    }

    @Test
    fun `acabou move do estoque para a lista`() {
        val r = ScanTransitions.aplicar(ItemQtds(estoque = 2, lista = 1), Modo.ACABOU)
        assertEquals(ItemQtds(estoque = 1, lista = 2), r.depois)
    }

    @Test
    fun `acabou sem estoque ainda entra na lista`() {
        val r = ScanTransitions.aplicar(ItemQtds(), Modo.ACABOU)
        assertEquals(ItemQtds(lista = 1), r.depois)
        assertEquals(0, r.deltas.estoque)
    }

    @Test
    fun `ciclo completo volta ao ponto de partida`() {
        var q = ItemQtds(estoque = 1)
        q = ScanTransitions.aplicar(q, Modo.ACABOU).depois
        assertEquals(ItemQtds(lista = 1), q)
        q = ScanTransitions.aplicar(q, Modo.MERCADO).depois
        assertEquals(ItemQtds(carrinho = 1), q)
        q = ScanTransitions.aplicar(q, Modo.GUARDAR).depois
        assertEquals(ItemQtds(estoque = 1), q)
    }

    @Test
    fun `nada aparece negativo na tela`() {
        for (modo in Modo.entries) {
            val d = ScanTransitions.aplicar(ItemQtds(), modo).depois.visivel()
            assertTrue("$modo mostrou negativo: $d", d.estoque >= 0 && d.lista >= 0 && d.carrinho >= 0)
        }
    }

    @Test
    fun `guardar um produto com estoque negativo mostra 1 na tela`() {
        // Os dois marcaram "acabou" no ultimo pacote: a soma crua ficou em -1.
        // Ao guardar uma unidade, a pessoa precisa VER 1 no estoque, e nao 0.
        val comDivida = ItemQtds(estoque = -1)
        val r = ScanTransitions.aplicar(comDivida, Modo.GUARDAR)
        assertEquals(2, r.deltas.estoque)
        assertEquals(1, r.depois.visivel().estoque)
    }

    @Test
    fun `ajuste manual para cima quita a divida`() {
        val d = ScanTransitions.deltasDeAjuste(ItemQtds(lista = -2), Campo.LISTA, 1)
        assertEquals(3, d.lista)
        assertEquals(1, ItemQtds(lista = -2).plus(d).visivel().lista)
    }

    @Test
    fun `ajuste manual para baixo nao inventa divida`() {
        val d = ScanTransitions.deltasDeAjuste(ItemQtds(lista = 0), Campo.LISTA, -1)
        assertEquals(0, d.lista, "tirar de um contador vazio nao pode gerar delta")
    }

    @Test
    fun `zerar tira exatamente o que esta visivel`() {
        val d = ScanTransitions.deltasParaZerar(ItemQtds(estoque = 5), Campo.ESTOQUE)
        assertEquals(-5, d.estoque)
        assertEquals(0, ItemQtds(estoque = 5).plus(d).estoque)
    }

    @Test
    fun `zerar um contador ja negativo nao mexe em nada`() {
        val d = ScanTransitions.deltasParaZerar(ItemQtds(estoque = -3), Campo.ESTOQUE)
        assertTrue(d.nulo)
    }

    @Test
    fun `item vazio some das listas mesmo com divida`() {
        assertTrue(ItemQtds().vazio)
        assertTrue(ItemQtds(estoque = -2).vazio)
        assertTrue(!ItemQtds(lista = 1).vazio)
    }
}

private fun assertEquals(esperado: Int, obtido: Int, mensagem: String) =
    org.junit.Assert.assertEquals(mensagem, esperado.toLong(), obtido.toLong())
