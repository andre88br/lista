package br.com.andre88.lista.data

import br.com.andre88.lista.data.db.ItemEntity
import br.com.andre88.lista.data.db.ProdutoEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProdutoBackup(
    val codigoBarras: String,
    val nome: String,
    val marca: String? = null,
    val imagemUrl: String? = null,
    val categoria: String? = null,
    val origemNome: String = ProdutoEntity.ORIGEM_MANUAL,
    val criadoEm: Long = 0L,
) {
    fun paraEntidade() = ProdutoEntity(
        codigoBarras = codigoBarras,
        nome = nome,
        marca = marca,
        imagemUrl = imagemUrl,
        categoria = categoria,
        origemNome = origemNome,
        criadoEm = if (criadoEm > 0) criadoEm else System.currentTimeMillis(),
    )

    companion object {
        fun de(p: ProdutoEntity) = ProdutoBackup(
            codigoBarras = p.codigoBarras,
            nome = p.nome,
            marca = p.marca,
            imagemUrl = p.imagemUrl,
            categoria = p.categoria,
            origemNome = p.origemNome,
            criadoEm = p.criadoEm,
        )
    }
}

@Serializable
data class ItemBackup(
    val codigoBarras: String,
    val qtdEstoque: Int = 0,
    val qtdLista: Int = 0,
    val qtdCarrinho: Int = 0,
    val atualizadoEm: Long = 0L,
) {
    fun paraEntidade() = ItemEntity(
        codigoBarras = codigoBarras,
        qtdEstoque = qtdEstoque,
        qtdLista = qtdLista,
        qtdCarrinho = qtdCarrinho,
        atualizadoEm = if (atualizadoEm > 0) atualizadoEm else System.currentTimeMillis(),
    )

    companion object {
        fun de(i: ItemEntity) = ItemBackup(
            codigoBarras = i.codigoBarras,
            qtdEstoque = i.qtdEstoque,
            qtdLista = i.qtdLista,
            qtdCarrinho = i.qtdCarrinho,
            atualizadoEm = i.atualizadoEm,
        )
    }
}

/** Arquivo de backup: produtos cadastrados e suas quantidades. */
@Serializable
data class BackupDados(
    val versao: Int = 1,
    val geradoEm: Long = System.currentTimeMillis(),
    val produtos: List<ProdutoBackup> = emptyList(),
    val itens: List<ItemBackup> = emptyList(),
) {
    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        fun paraTexto(dados: BackupDados): String = json.encodeToString(serializer(), dados)

        fun deTexto(texto: String): BackupDados = json.decodeFromString(serializer(), texto)
    }
}
