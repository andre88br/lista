package br.com.andre88.lista.domain

/**
 * O que sabemos sobre o ritmo de recompra de um produto, a partir de quando ele
 * foi comprado (leituras no modo mercado, sem contar as desfeitas).
 */
data class RitmoDeRecompra(
    val vezesComprado: Int,
    val intervaloMedioDias: Double,
    val diasDesdeUltimaCompra: Double,
) {
    /** So da para falar em ritmo depois de pelo menos duas compras (um intervalo). */
    val temRitmo: Boolean get() = vezesComprado >= 2

    /** Ja passou do tempo que esse produto costuma levar para acabar de novo. */
    val horaDeComprar: Boolean get() = temRitmo && diasDesdeUltimaCompra >= intervaloMedioDias
}

/**
 * Calcula o ritmo de recompra a partir do historico de datas de compra de um
 * produto. Quanto mais compras no historico, mais confiavel a media — com so uma,
 * ainda nao da para falar em ritmo, so em ha quanto tempo foi a ultima.
 */
object CalculadoraDeRitmo {

    private const val UM_DIA_MS = 24 * 60 * 60 * 1000.0

    fun calcular(timestampsDeCompra: List<Long>, agora: Long = System.currentTimeMillis()): RitmoDeRecompra? {
        if (timestampsDeCompra.isEmpty()) return null
        val ordenados = timestampsDeCompra.sorted()
        val diasDesdeUltima = (agora - ordenados.last()) / UM_DIA_MS

        if (ordenados.size < 2) {
            return RitmoDeRecompra(vezesComprado = 1, intervaloMedioDias = 0.0, diasDesdeUltimaCompra = diasDesdeUltima)
        }

        val intervaloMedioMs = ordenados.zipWithNext { anterior, proxima -> (proxima - anterior).toDouble() }.average()
        return RitmoDeRecompra(
            vezesComprado = ordenados.size,
            intervaloMedioDias = intervaloMedioMs / UM_DIA_MS,
            diasDesdeUltimaCompra = diasDesdeUltima,
        )
    }
}
