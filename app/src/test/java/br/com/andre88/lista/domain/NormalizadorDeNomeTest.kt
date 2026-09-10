package br.com.andre88.lista.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizadorDeNomeTest {

    @Test
    fun `cafe moido reconhece a caracteristica`() {
        val r = NormalizadorDeNome.normalizar("Café Pilão Torrado e Moído Tradicional 500g", "Pilão")
        assertEquals("Café moído", r)
    }

    @Test
    fun `cafe soluvel reconhece mesmo com o tipo embutido na marca`() {
        val r = NormalizadorDeNome.normalizar("Nescafé Original Solúvel 200g", "Nescafé")
        assertEquals("Café solúvel", r)
    }

    @Test
    fun `cafe em capsulas`() {
        val r = NormalizadorDeNome.normalizar("Café em Cápsulas Compatível Nespresso 10un", null)
        assertEquals("Café em cápsulas", r)
    }

    @Test
    fun `cafe sem caracteristica reconhecida vira so a categoria`() {
        val r = NormalizadorDeNome.normalizar("Café Pilão Tradicional 500g", "Pilão")
        assertEquals("Café", r)
    }

    @Test
    fun `nome ja normalizado fica igual, sem duplicar nada`() {
        assertEquals("Café", NormalizadorDeNome.normalizar("Café"))
        assertEquals("Café moído", NormalizadorDeNome.normalizar("Café moído"))
    }

    @Test
    fun `leite integral`() {
        val r = NormalizadorDeNome.normalizar("Leite Integral Itambé 1L", "Itambé")
        assertEquals("Leite integral", r)
    }

    @Test
    fun `leite em po combina com a caracteristica de gordura quando as duas aparecem`() {
        val r = NormalizadorDeNome.normalizar("Leite em Pó Integral Ninho 400g", "Ninho")
        assertEquals("Leite em pó integral", r)
    }

    @Test
    fun `leite condensado`() {
        val r = NormalizadorDeNome.normalizar("Leite Condensado Moça 395g", "Moça")
        assertEquals("Leite condensado", r)
    }

    @Test
    fun `arroz integral`() {
        val r = NormalizadorDeNome.normalizar("Arroz Integral Tio João 1kg", "Tio João")
        assertEquals("Arroz integral", r)
    }

    @Test
    fun `feijao carioca`() {
        val r = NormalizadorDeNome.normalizar("Feijão Carioca Camil Tipo 1 1kg", "Camil")
        assertEquals("Feijão carioca", r)
    }

    @Test
    fun `azeite extra virgem`() {
        val r = NormalizadorDeNome.normalizar("Azeite de Oliva Extra Virgem Gallo 500ml", "Gallo")
        assertEquals("Azeite extra virgem", r)
    }

    @Test
    fun `papel higienico folha dupla`() {
        val r = NormalizadorDeNome.normalizar("Papel Higiênico Folha Dupla Neve 30m", "Neve")
        assertEquals("Papel higiênico folha dupla", r)
    }

    @Test
    fun `produto fora do dicionario so perde marca e peso`() {
        val r = NormalizadorDeNome.normalizar("Salgadinho de Milho Sabor Churrasco 150g", "Elma Chips")
        assertEquals("Salgadinho de Milho Sabor Churrasco", r)
    }

    @Test
    fun `produto fora do dicionario sem marca informada mantem o nome, so tira o peso`() {
        val r = NormalizadorDeNome.normalizar("Chocolate Amargo 70% Cacau 90g", null)
        assertEquals("Chocolate Amargo 70% Cacau", r)
    }

    @Test
    fun `palavra da categoria dentro de outro produto ainda reconhece a categoria`() {
        // Limitacao conhecida: e uma busca por palavra, nao um classificador de produto.
        // "Chocolate ao Leite" tem a palavra "leite" e cai na categoria Leite.
        val r = NormalizadorDeNome.normalizar("Chocolate ao Leite 90g", "Lacta")
        assertEquals("Leite", r)
    }

    @Test
    fun `nome vazio nao quebra`() {
        assertEquals("", NormalizadorDeNome.normalizar("", "Marca"))
        assertEquals("   ".trim(), NormalizadorDeNome.normalizar("   ", null))
    }

    // ----------------------------------------------------------------- corredorDe

    @Test
    fun `corredor de laticinios`() {
        assertEquals("Laticínios", NormalizadorDeNome.corredorDe("Leite integral"))
        assertEquals("Laticínios", NormalizadorDeNome.corredorDe("Queijo mussarela"))
        assertEquals("Laticínios", NormalizadorDeNome.corredorDe("Iogurte natural"))
    }

    @Test
    fun `corredor de mercearia`() {
        assertEquals("Mercearia", NormalizadorDeNome.corredorDe("Café moído"))
        assertEquals("Mercearia", NormalizadorDeNome.corredorDe("Arroz integral"))
        assertEquals("Mercearia", NormalizadorDeNome.corredorDe("Feijão carioca"))
    }

    @Test
    fun `corredor de padaria e bebidas`() {
        assertEquals("Padaria", NormalizadorDeNome.corredorDe("Pão de forma"))
        assertEquals("Bebidas", NormalizadorDeNome.corredorDe("Água com gás"))
        assertEquals("Bebidas", NormalizadorDeNome.corredorDe("Refrigerante zero"))
    }

    @Test
    fun `corredor desconhecido cai em Outros`() {
        assertEquals("Outros", NormalizadorDeNome.corredorDe("Salgadinho de Milho Sabor Churrasco"))
        assertEquals("Outros", NormalizadorDeNome.corredorDe("banana"))
    }
}
