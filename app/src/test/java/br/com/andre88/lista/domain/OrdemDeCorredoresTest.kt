package br.com.andre88.lista.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OrdemDeCorredoresTest {

    @Test
    fun `agrupa e ordena seguindo o trajeto do mercado, fora de ordem na entrada`() {
        val itens = listOf("refrigerante", "pao", "queijo", "arroz", "papel")
        val corredorDe = { item: String ->
            when (item) {
                "pao" -> "Padaria"
                "queijo" -> "Laticínios"
                "arroz" -> "Mercearia"
                "refrigerante" -> "Bebidas"
                "papel" -> "Higiene e limpeza"
                else -> "Outros"
            }
        }
        val grupos = OrdemDeCorredores.agrupar(itens, corredorDe)
        assertEquals(
            listOf("Padaria", "Laticínios", "Mercearia", "Bebidas", "Higiene e limpeza"),
            grupos.map { it.first },
        )
    }

    @Test
    fun `outros sempre fica por ultimo`() {
        val itens = listOf("banana", "arroz")
        val corredorDe = { item: String -> if (item == "arroz") "Mercearia" else "Outros" }
        val grupos = OrdemDeCorredores.agrupar(itens, corredorDe)
        assertEquals(listOf("Mercearia", "Outros"), grupos.map { it.first })
    }

    @Test
    fun `corredor desconhecido nao quebra e vai para o fim`() {
        val itens = listOf("x", "arroz")
        val corredorDe = { item: String -> if (item == "arroz") "Mercearia" else "Corredor Inventado" }
        val grupos = OrdemDeCorredores.agrupar(itens, corredorDe)
        assertEquals(listOf("Mercearia", "Corredor Inventado"), grupos.map { it.first })
    }

    @Test
    fun `itens do mesmo corredor ficam juntos`() {
        val itens = listOf("leite", "queijo", "iogurte")
        val corredorDe = { _: String -> "Laticínios" }
        val grupos = OrdemDeCorredores.agrupar(itens, corredorDe)
        assertEquals(1, grupos.size)
        assertEquals(itens, grupos.single().second)
    }

    @Test
    fun `lista vazia devolve nenhum grupo`() {
        assertEquals(emptyList<Pair<String, List<String>>>(), OrdemDeCorredores.agrupar(emptyList<String>()) { "Outros" })
    }
}
