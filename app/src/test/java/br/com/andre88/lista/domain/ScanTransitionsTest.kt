package br.com.andre88.lista.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTransitionsTest {

    @Test
    fun `no mercado move da lista para o carrinho`() {
        val r = ScanTransitions.aplicar(ItemQtds(estoque = 0, lista = 2, carrinho = 0), Modo.MERCADO)
        assertEquals(ItemQtds(estoque = 0, lista = 1, carrinho = 1), r.depois)
        assertEquals(1, r.deltaCarrinho)
        assertEquals(-1, r.deltaLista)
    }

    @Test
    fun `no mercado sem estar na lista ainda vai para o carrinho`() {
        val r = ScanTransitions.aplicar(ItemQtds(), Modo.MERCADO)
        assertEquals(ItemQtds(carrinho = 1), r.depois)
        assertEquals(0, r.deltaLista)
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
        assertEquals(0, r.deltaCarrinho)
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
        assertEquals(0, r.deltaEstoque)
    }

    @Test
    fun `ciclo completo volta ao ponto de partida`() {
        var q = ItemQtds(estoque = 1)
        q = ScanTransitions.aplicar(q, Modo.ACABOU).depois
        assertEquals(ItemQtds(estoque = 0, lista = 1), q)
        q = ScanTransitions.aplicar(q, Modo.MERCADO).depois
        assertEquals(ItemQtds(carrinho = 1), q)
        q = ScanTransitions.aplicar(q, Modo.GUARDAR).depois
        assertEquals(ItemQtds(estoque = 1), q)
    }

    @Test
    fun `desfazer devolve o estado anterior mesmo com contador zerado`() {
        val antes = ItemQtds(estoque = 0, lista = 0, carrinho = 0)
        val r = ScanTransitions.aplicar(antes, Modo.ACABOU)
        val revertido = ScanTransitions.reverter(r.depois, r.deltaEstoque, r.deltaLista, r.deltaCarrinho)
        assertEquals(antes, revertido)
    }

    @Test
    fun `desfazer de varias leituras seguidas devolve o estado inicial`() {
        val inicial = ItemQtds(estoque = 2, lista = 1, carrinho = 0)
        val r1 = ScanTransitions.aplicar(inicial, Modo.ACABOU)
        val r2 = ScanTransitions.aplicar(r1.depois, Modo.MERCADO)
        var q = ScanTransitions.reverter(r2.depois, r2.deltaEstoque, r2.deltaLista, r2.deltaCarrinho)
        q = ScanTransitions.reverter(q, r1.deltaEstoque, r1.deltaLista, r1.deltaCarrinho)
        assertEquals(inicial, q)
    }

    @Test
    fun `nenhum contador fica negativo em nenhum modo`() {
        for (modo in Modo.entries) {
            val d = ScanTransitions.aplicar(ItemQtds(), modo).depois
            assertTrue("$modo gerou negativo: $d", d.estoque >= 0 && d.lista >= 0 && d.carrinho >= 0)
        }
    }

    @Test
    fun `item vazio e detectado para poder sumir das listas`() {
        assertTrue(ItemQtds().vazio)
        assertTrue(!ItemQtds(lista = 1).vazio)
    }
}
