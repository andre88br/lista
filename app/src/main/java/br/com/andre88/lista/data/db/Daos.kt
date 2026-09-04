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

    /** Produtos alterados depois do ultimo envio, para subir na proxima sincronizacao. */
    @Query("SELECT * FROM produto WHERE atualizadoEm > :desde ORDER BY atualizadoEm LIMIT :limite")
    suspend fun alteradosDepoisDe(desde: Long, limite: Int = 400): List<ProdutoEntity>

    @Query("UPDATE produto SET nome = :nome, marca = :marca, atualizadoEm = :agora WHERE codigoBarras = :codigoBarras")
    suspend fun renomear(codigoBarras: String, nome: String, marca: String?, agora: Long)

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

    /**
     * Limpa apenas linhas exatamente zeradas. Linhas com valor negativo ficam:
     * elas guardam a "divida" que mantem os dois celulares somando igual.
     */
    @Query("DELETE FROM item WHERE qtdEstoque = 0 AND qtdLista = 0 AND qtdCarrinho = 0")
    suspend fun limparZerados()

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

    @Query("SELECT COALESCE(SUM(MAX(qtdLista, 0)), 0) FROM item")
    fun totalLista(): Flow<Int>

    @Query("SELECT COALESCE(SUM(MAX(qtdEstoque, 0)), 0) FROM item")
    fun totalEstoque(): Flow<Int>

    @Query("SELECT COALESCE(SUM(MAX(qtdCarrinho, 0)), 0) FROM item")
    fun totalCarrinho(): Flow<Int>

    @Query("SELECT * FROM item WHERE qtdCarrinho > 0")
    suspend fun itensNoCarrinho(): List<ItemEntity>

    @Query("SELECT * FROM item WHERE qtdEstoque != 0 OR qtdLista != 0 OR qtdCarrinho != 0")
    suspend fun itensComQuantidade(): List<ItemEntity>

    companion object {
        // MAX(coluna, 0): a soma crua pode ser negativa, mas na tela nunca aparece negativo.
        const val SELECT_BASE = """
            SELECT i.codigoBarras AS codigoBarras, p.nome AS nome, p.marca AS marca,
                   p.imagemUrl AS imagemUrl,
                   MAX(i.qtdEstoque, 0) AS qtdEstoque,
                   MAX(i.qtdLista, 0) AS qtdLista,
                   MAX(i.qtdCarrinho, 0) AS qtdCarrinho,
                   i.atualizadoEm AS atualizadoEm
            FROM item i INNER JOIN produto p ON p.codigoBarras = i.codigoBarras
        """
    }
}

@Dao
interface ScanEventoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirSeNovo(evento: ScanEventoEntity): Long

    @Query("SELECT * FROM scan_evento WHERE desfeito = 0 ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun ultimoNaoDesfeito(): ScanEventoEntity?

    @Query("SELECT * FROM scan_evento WHERE id = :id")
    suspend fun porId(id: String): ScanEventoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM scan_evento WHERE id = :id)")
    suspend fun existe(id: String): Boolean

    @Query("UPDATE scan_evento SET desfeito = 1 WHERE id = :id")
    suspend fun marcarDesfeito(id: String)

    @Query("SELECT * FROM scan_evento WHERE sincronizado = 0 ORDER BY timestamp LIMIT :limite")
    suspend fun pendentes(limite: Int = 400): List<ScanEventoEntity>

    @Query("SELECT COUNT(*) FROM scan_evento WHERE sincronizado = 0")
    fun quantidadePendente(): Flow<Int>

    @Query("UPDATE scan_evento SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun marcarSincronizados(ids: List<String>)

    @Query("UPDATE scan_evento SET sincronizado = 1")
    suspend fun marcarTudoSincronizado()

    @Query("DELETE FROM scan_evento")
    suspend fun apagarTudo()
}
