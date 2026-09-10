package br.com.andre88.lista.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraDeRitmoTest {

    private val dia = 24 * 60 * 60 * 1000L

    @Test
    fun `sem nenhuma compra nao ha ritmo`() {
        assertNull(CalculadoraDeRitmo.calcular(emptyList()))
    }

    @Test
    fun `com uma compra so sabe ha quanto tempo foi, sem ritmo`() {
        val agora = 100 * dia
        val r = CalculadoraDeRitmo.calcular(listOf(90 * dia), agora)!!
        assertEquals(1, r.vezesComprado)
        assertFalse(r.temRitmo)
        assertFalse(r.horaDeComprar)
        assertEquals(10.0, r.diasDesdeUltimaCompra, 0.001)
    }

    @Test
    fun `duas compras de 12 em 12 dias, ainda nao chegou a hora`() {
        val agora = 20 * dia
        // ultima compra ha 8 dias, media de intervalo 12 dias -> ainda nao
        val r = CalculadoraDeRitmo.calcular(listOf(0L, 12 * dia), agora)!!
        assertEquals(2, r.vezesComprado)
        assertTrue(r.temRitmo)
        assertEquals(12.0, r.intervaloMedioDias, 0.001)
        assertEquals(8.0, r.diasDesdeUltimaCompra, 0.001)
        assertFalse(r.horaDeComprar)
    }

    @Test
    fun `passou do intervalo medio, hora de comprar`() {
        val agora = 25 * dia
        val r = CalculadoraDeRitmo.calcular(listOf(0L, 12 * dia), agora)!!
        assertEquals(13.0, r.diasDesdeUltimaCompra, 0.001)
        assertTrue(r.horaDeComprar)
    }

    @Test
    fun `media de varias compras, ordem de entrada nao importa`() {
        // compras em 0, 10, 20, 30 dias -> intervalo medio 10 dias
        val agora = 40 * dia
        val ordenado = CalculadoraDeRitmo.calcular(listOf(0L, 10 * dia, 20 * dia, 30 * dia), agora)!!
        val foraDeOrdem = CalculadoraDeRitmo.calcular(listOf(30 * dia, 0L, 20 * dia, 10 * dia), agora)!!
        assertEquals(ordenado, foraDeOrdem)
        assertEquals(4, ordenado.vezesComprado)
        assertEquals(10.0, ordenado.intervaloMedioDias, 0.001)
        assertEquals(10.0, ordenado.diasDesdeUltimaCompra, 0.001)
        assertTrue(ordenado.horaDeComprar)
    }

    @Test
    fun `exatamente no dia do intervalo medio ja conta como hora de comprar`() {
        val agora = 24 * dia
        val r = CalculadoraDeRitmo.calcular(listOf(0L, 12 * dia), agora)!!
        assertEquals(12.0, r.diasDesdeUltimaCompra, 0.001)
        assertTrue(r.horaDeComprar)
    }
}
