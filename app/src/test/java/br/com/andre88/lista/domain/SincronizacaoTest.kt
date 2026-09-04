package br.com.andre88.lista.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O que garante que os dois celulares mostram a mesma coisa. Um evento e so um
 * delta com id; aplicar os mesmos eventos, em qualquer ordem, tem de dar no
 * mesmo resultado, e aplicar duas vezes nao pode contar duas vezes.
 */
class SincronizacaoTest {

    private data class Evento(val id: String, val deltas: Deltas)

    /**
     * Simula um celular: guarda o que ja aplicou e soma os deltas novos.
     * O [nome] entra no id do evento porque, no app, cada leitura gera um UUID
     * proprio - dois aparelhos nunca produzem o mesmo id.
     */
    private class Celular(private val nome: String = "celular") {
        var estado = ItemQtds()
            private set
        private val aplicados = mutableSetOf<String>()

        fun aplicar(evento: Evento) {
            if (!aplicados.add(evento.id)) return
            estado = ScanTransitions.aplicarDeltas(estado, evento.deltas)
        }

        fun escanear(modo: Modo): Evento {
            val resultado = ScanTransitions.aplicar(estado, modo)
            estado = resultado.depois
            val evento = Evento(id = "$nome-${aplicados.size}-${modo.name}", deltas = resultado.deltas)
            aplicados.add(evento.id)
            return evento
        }
    }

    @Test
    fun `a ordem de chegada nao muda o resultado`() {
        val eventos = listOf(
            Evento("1", Deltas(lista = 1, estoque = -1)),
            Evento("2", Deltas(carrinho = 1, lista = -1)),
            Evento("3", Deltas(estoque = 1, carrinho = -1)),
        )

        val umaOrdem = Celular().apply { eventos.forEach(::aplicar) }.estado
        val outraOrdem = Celular().apply { eventos.reversed().forEach(::aplicar) }.estado
        val terceiraOrdem = Celular().apply { listOf(eventos[1], eventos[2], eventos[0]).forEach(::aplicar) }.estado

        assertEquals(umaOrdem, outraOrdem)
        assertEquals(umaOrdem, terceiraOrdem)
    }

    @Test
    fun `o mesmo evento aplicado duas vezes conta uma vez so`() {
        val celular = Celular()
        val evento = Evento("abc", Deltas(lista = 1))
        celular.aplicar(evento)
        celular.aplicar(evento)
        celular.aplicar(evento)
        assertEquals(ItemQtds(lista = 1), celular.estado)
    }

    @Test
    fun `duas pessoas offline no mesmo produto convergem`() {
        // Os dois comecam com 1 no estoque e marcam "acabou" sem sinal.
        val meu = Celular("meu").apply { aplicar(Evento("inicial", Deltas(estoque = 1))) }
        val dela = Celular("dela").apply { aplicar(Evento("inicial", Deltas(estoque = 1))) }

        val meuEvento = meu.escanear(Modo.ACABOU)
        val eventoDela = dela.escanear(Modo.ACABOU)

        // Voltou o sinal: cada um recebe o evento do outro.
        meu.aplicar(eventoDela)
        dela.aplicar(meuEvento)

        assertEquals(meu.estado, dela.estado)
        assertEquals(0, meu.estado.exibir().estoque)
        assertEquals(2, meu.estado.exibir().lista, "as duas leituras contam: aparece 2 na lista")
    }

    @Test
    fun `desfazer viaja como evento compensatorio`() {
        val meu = Celular("meu")
        val dela = Celular("dela")

        val leitura = meu.escanear(Modo.ACABOU)
        dela.aplicar(leitura)

        // Desfazer nao apaga o evento: cria outro com os deltas invertidos.
        val desfazer = Evento("${leitura.id}-desfeito", -leitura.deltas)
        meu.aplicar(desfazer)
        dela.aplicar(desfazer)

        assertEquals(meu.estado, dela.estado)
        assertEquals(ItemQtds(), meu.estado)
    }

    @Test
    fun `celular que entrou depois chega no mesmo estado pela soma dos eventos`() {
        val antigo = Celular("antigo")
        val eventos = buildList {
            add(antigo.escanear(Modo.ACABOU))
            add(antigo.escanear(Modo.MERCADO))
            add(antigo.escanear(Modo.GUARDAR))
        }

        val novo = Celular("novo")
        // Chega tudo de uma vez, em ordem embaralhada, como num instantaneo.
        eventos.shuffled().forEach(novo::aplicar)

        assertEquals(antigo.estado, novo.estado)
    }
}

private fun assertEquals(esperado: Int, obtido: Int, mensagem: String) =
    org.junit.Assert.assertEquals(mensagem, esperado.toLong(), obtido.toLong())
