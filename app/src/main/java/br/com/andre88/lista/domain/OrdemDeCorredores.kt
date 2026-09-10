package br.com.andre88.lista.domain

/**
 * Agrupa itens pelo corredor do mercado a que pertencem, na ordem de um trajeto
 * comum: primeiro o que fica perto da entrada, "Outros" sempre por ultimo.
 */
object OrdemDeCorredores {

    private val ORDEM = listOf("Padaria", "Laticínios", "Mercearia", "Bebidas", "Higiene e limpeza", "Outros")

    fun <T> agrupar(itens: List<T>, corredorDe: (T) -> String): List<Pair<String, List<T>>> =
        itens.groupBy(corredorDe)
            .toList()
            .sortedBy { (corredor, _) -> ORDEM.indexOf(corredor).let { if (it < 0) ORDEM.size else it } }
}
