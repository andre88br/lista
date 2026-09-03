package br.com.andre88.lista.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao {

    @Upsert
    suspend fun salvar(produto: ProdutoEntity)

    @Query("SELECT * FROM produto WHERE codigoBarras = :codigoBarras")
    suspend fun porCodigo(codigoBarras: String): ProdutoEntity?

    @Query("SELECT * FROM produto ORDER BY nome COLLATE NOCASE")
    suspend fun todos(): List<ProdutoEntity>

    @Query("UPDATE produto SET nome = :nome, marca = :marca WHERE codigoBarras = :codigoBarras")
    suspend fun renomear(codigoBarras: String, nome: String, marca: String?)

    @Query("DELETE FROM produto WHERE codigoBarras = :codigoBarras")
    suspend fun apagar(codigoBarras: String)

    @Query("DELETE FROM produto")
    suspend fun apagarTudo()
}

@Dao
interface ItemDao {

    @Upsert
    suspend fun salvar(item: ItemEntity)

    @Query("SELECT * FROM item WHERE codigoBarras = :codigoBarras")
    suspend fun porCodigo(codigoBarras: String): ItemEntity?

    @Query("SELECT * FROM item")
    suspend fun todos(): List<ItemEntity>

    @Query("DELETE FROM item WHERE qtdEstoque = 0 AND qtdLista = 0 AND qtdCarrinho = 0")
    suspend fun limparVazios()

    @Query("DELETE FROM item")
    suspend fun apagarTudo()

    @Query(SELECT_BASE + " WHERE i.qtdLista > 0 ORDER BY p.nome COLLATE NOCASE")
    fun listaDeCompras(): Flow<List<ItemComProduto>>

    @Query(SELECT_BASE + " WHERE i.qtdEstoque > 0 ORDER BY p.nome COLLATE NOCASE")
    fun estoque(): Flow<List<ItemComProduto>>

    @Query(SELECT_BASE + " WHERE i.qtdCarrinho > 0 ORDER BY i.atualizadoEm DESC")
    fun carrinho(): Flow<List<ItemComProduto>>

    @Query(SELECT_BASE + " WHERE i.codigoBarras = :codigoBarras")
    suspend fun itemComProduto(codigoBarras: String): ItemComProduto?

    @Query("SELECT COALESCE(SUM(qtdLista), 0) FROM item")
    fun totalLista(): Flow<Int>

    @Query("SELECT COALESCE(SUM(qtdEstoque), 0) FROM item")
    fun totalEstoque(): Flow<Int>

    @Query("SELECT COALESCE(SUM(qtdCarrinho), 0) FROM item")
    fun totalCarrinho(): Flow<Int>

    @Query("SELECT * FROM item WHERE qtdCarrinho > 0")
    suspend fun itensNoCarrinho(): List<ItemEntity>

    companion object {
        const val SELECT_BASE = """
            SELECT i.codigoBarras AS codigoBarras, p.nome AS nome, p.marca AS marca,
                   p.imagemUrl AS imagemUrl, i.qtdEstoque AS qtdEstoque, i.qtdLista AS qtdLista,
                   i.qtdCarrinho AS qtdCarrinho, i.atualizadoEm AS atualizadoEm
            FROM item i INNER JOIN produto p ON p.codigoBarras = i.codigoBarras
        """
    }
}

@Dao
interface ScanEventoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(evento: ScanEventoEntity): Long

    @Query("SELECT * FROM scan_evento WHERE desfeito = 0 ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun ultimoNaoDesfeito(): ScanEventoEntity?

    @Query("SELECT * FROM scan_evento WHERE id = :id")
    suspend fun porId(id: Long): ScanEventoEntity?

    @Query("UPDATE scan_evento SET desfeito = 1 WHERE id = :id")
    suspend fun marcarDesfeito(id: Long)

    @Query("DELETE FROM scan_evento")
    suspend fun apagarTudo()
}
