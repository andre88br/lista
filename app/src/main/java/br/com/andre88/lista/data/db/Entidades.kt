package br.com.andre88.lista.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.andre88.lista.domain.Deltas
import br.com.andre88.lista.domain.ItemQtds

/** Cadastro do produto. Um codigo de barras so precisa ser identificado uma vez. */
@Entity(tableName = "produto")
data class ProdutoEntity(
    @PrimaryKey val codigoBarras: String,
    val nome: String,
    val marca: String? = null,
    val imagemUrl: String? = null,
    val categoria: String? = null,
    /** OFF (veio do Open Food Facts) ou MANUAL (digitado por voce). */
    val origemNome: String = ORIGEM_MANUAL,
    val criadoEm: Long = System.currentTimeMillis(),
    /** Nome de produto nao e contador: na sincronizacao, vence a escrita mais recente. */
    @ColumnInfo(defaultValue = "0") val atualizadoEm: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ORIGEM_OFF = "OFF"
        const val ORIGEM_MANUAL = "MANUAL"
    }
}

/**
 * Quantidades do produto em cada lista, guardadas como **soma crua** dos deltas.
 * Podem ficar negativas; as consultas e a tela e que limitam em zero. Ver
 * [br.com.andre88.lista.domain.ItemQtds].
 */
@Entity(tableName = "item")
data class ItemEntity(
    @PrimaryKey val codigoBarras: String,
    @ColumnInfo(defaultValue = "0") val qtdEstoque: Int = 0,
    @ColumnInfo(defaultValue = "0") val qtdLista: Int = 0,
    @ColumnInfo(defaultValue = "0") val qtdCarrinho: Int = 0,
    val atualizadoEm: Long = System.currentTimeMillis(),
    /** Quem mexeu por ultimo neste produto, para as listas mostrarem de quem foi. */
    val ultimoAutorNome: String? = null,
) {
    fun paraQtds(): ItemQtds = ItemQtds(estoque = qtdEstoque, lista = qtdLista, carrinho = qtdCarrinho)

    companion object {
        fun de(
            codigoBarras: String,
            q: ItemQtds,
            agora: Long = System.currentTimeMillis(),
            autor: String? = null,
        ) = ItemEntity(
            codigoBarras = codigoBarras,
            qtdEstoque = q.estoque,
            qtdLista = q.lista,
            qtdCarrinho = q.carrinho,
            atualizadoEm = agora,
            ultimoAutorNome = autor,
        )
    }
}

/**
 * Historico de leituras. E a unidade de sincronizacao: cada linha e um delta com
 * id proprio, e a quantidade de um produto e a soma dos deltas. O id vem do
 * celular, entao reenviar o mesmo evento nunca conta duas vezes.
 */
@Entity(
    tableName = "scan_evento",
    indices = [Index("timestamp"), Index("codigoBarras"), Index("sincronizado")],
)
data class ScanEventoEntity(
    @PrimaryKey val id: String,
    val codigoBarras: String,
    val modo: String,
    val deltaEstoque: Int,
    val deltaLista: Int,
    val deltaCarrinho: Int,
    val timestamp: Long = System.currentTimeMillis(),
    /** Marcado quando o evento ja foi desfeito, para nao desfazer duas vezes. */
    @ColumnInfo(defaultValue = "0") val desfeito: Boolean = false,
    /** 0 enquanto o evento ainda nao subiu para o servidor. */
    @ColumnInfo(defaultValue = "0") val sincronizado: Boolean = false,
    /** Qual aparelho gerou a leitura (nulo quando o app roda sem sincronizacao). */
    val autorId: String? = null,
    /** Nome de quem escaneou, para mostrar nas listas sem consultar o servidor. */
    val autorNome: String? = null,
) {
    fun paraDeltas(): Deltas = Deltas(estoque = deltaEstoque, lista = deltaLista, carrinho = deltaCarrinho)

    companion object {
        const val MODO_AJUSTE = "AJUSTE"
        const val MODO_DESFAZER = "DESFAZER"
    }
}

/** Linha exibida nas listas. As quantidades ja vem limitadas em zero pela consulta. */
data class ItemComProduto(
    val codigoBarras: String,
    val nome: String,
    val marca: String?,
    val imagemUrl: String?,
    val qtdEstoque: Int,
    val qtdLista: Int,
    val qtdCarrinho: Int,
    val atualizadoEm: Long,
    val ultimoAutorNome: String?,
)
