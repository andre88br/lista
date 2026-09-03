package br.com.andre88.lista.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
) {
    companion object {
        const val ORIGEM_OFF = "OFF"
        const val ORIGEM_MANUAL = "MANUAL"
    }
}

/** Quantidades do produto em cada lista. Um produto pode estar em mais de uma ao mesmo tempo. */
@Entity(tableName = "item")
data class ItemEntity(
    @PrimaryKey val codigoBarras: String,
    @ColumnInfo(defaultValue = "0") val qtdEstoque: Int = 0,
    @ColumnInfo(defaultValue = "0") val qtdLista: Int = 0,
    @ColumnInfo(defaultValue = "0") val qtdCarrinho: Int = 0,
    val atualizadoEm: Long = System.currentTimeMillis(),
) {
    fun paraQtds(): ItemQtds = ItemQtds(estoque = qtdEstoque, lista = qtdLista, carrinho = qtdCarrinho)

    companion object {
        fun de(codigoBarras: String, q: ItemQtds, agora: Long = System.currentTimeMillis()) = ItemEntity(
            codigoBarras = codigoBarras,
            qtdEstoque = q.estoque,
            qtdLista = q.lista,
            qtdCarrinho = q.carrinho,
            atualizadoEm = agora,
        )
    }
}

/** Historico de leituras: e o que torna o "Desfazer" possivel. */
@Entity(
    tableName = "scan_evento",
    indices = [Index("timestamp"), Index("codigoBarras")],
)
data class ScanEventoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigoBarras: String,
    val modo: String,
    val deltaEstoque: Int,
    val deltaLista: Int,
    val deltaCarrinho: Int,
    val timestamp: Long = System.currentTimeMillis(),
    /** Marcado quando o evento ja foi desfeito, para nao desfazer duas vezes. */
    @ColumnInfo(defaultValue = "0") val desfeito: Boolean = false,
)

/** Linha exibida nas listas: quantidades + dados do produto. */
data class ItemComProduto(
    val codigoBarras: String,
    val nome: String,
    val marca: String?,
    val imagemUrl: String?,
    val qtdEstoque: Int,
    val qtdLista: Int,
    val qtdCarrinho: Int,
    val atualizadoEm: Long,
)
