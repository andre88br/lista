package br.com.andre88.lista.data

import androidx.room.withTransaction
import br.com.andre88.lista.data.db.AppDatabase
import br.com.andre88.lista.data.db.ItemComProduto
import br.com.andre88.lista.data.db.ItemEntity
import br.com.andre88.lista.data.db.ProdutoEntity
import br.com.andre88.lista.data.db.ScanEventoEntity
import br.com.andre88.lista.data.remote.OpenFoodFactsClient
import br.com.andre88.lista.data.remote.SugestaoProduto
import br.com.andre88.lista.domain.ItemQtds
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.domain.ScanResultado
import br.com.andre88.lista.domain.ScanTransitions
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Qual contador ajustar quando o usuario mexe na quantidade na mao. */
enum class Campo { ESTOQUE, LISTA, CARRINHO }

/** Resposta de uma leitura de codigo de barras. */
sealed interface ResultadoLeitura {

    /** A leitura foi aplicada. `eventoId` permite desfazer. */
    data class Registrada(
        val produto: ProdutoEntity,
        val resultado: ScanResultado,
        val modo: Modo,
        val eventoId: Long,
    ) : ResultadoLeitura

    /** Codigo nunca visto: a UI precisa pedir (ou sugerir) o nome antes de aplicar. */
    data class ProdutoDesconhecido(val codigoBarras: String) : ResultadoLeitura
}

class ListaRepository(
    private val db: AppDatabase,
    private val openFoodFacts: OpenFoodFactsClient,
    private val preferencias: Preferencias,
) {

    private val produtoDao = db.produtoDao()
    private val itemDao = db.itemDao()
    private val eventoDao = db.scanEventoDao()

    // ----------------------------------------------------------------- leitura

    val listaDeCompras: Flow<List<ItemComProduto>> = itemDao.listaDeCompras()
    val estoque: Flow<List<ItemComProduto>> = itemDao.estoque()
    val carrinho: Flow<List<ItemComProduto>> = itemDao.carrinho()
    val totalLista: Flow<Int> = itemDao.totalLista()
    val totalEstoque: Flow<Int> = itemDao.totalEstoque()
    val totalCarrinho: Flow<Int> = itemDao.totalCarrinho()

    suspend fun produto(codigoBarras: String): ProdutoEntity? = produtoDao.porCodigo(codigoBarras)

    suspend fun itemComProduto(codigoBarras: String): ItemComProduto? = itemDao.itemComProduto(codigoBarras)

    // ------------------------------------------------------------------ escrita

    /**
     * Aplica uma leitura. Se o codigo ainda nao tem cadastro, nada e alterado e a UI
     * recebe [ResultadoLeitura.ProdutoDesconhecido] para abrir o cadastro rapido.
     */
    suspend fun registrarLeitura(codigoBarras: String, modo: Modo): ResultadoLeitura {
        val produto = produtoDao.porCodigo(codigoBarras)
            ?: return ResultadoLeitura.ProdutoDesconhecido(codigoBarras)
        return aplicar(produto, modo)
    }

    /** Cadastra o produto novo e ja aplica a leitura que o descobriu, numa tacada so. */
    suspend fun cadastrarEAplicar(produto: ProdutoEntity, modo: Modo): ResultadoLeitura.Registrada {
        produtoDao.salvar(produto)
        return aplicar(produto, modo)
    }

    private suspend fun aplicar(produto: ProdutoEntity, modo: Modo): ResultadoLeitura.Registrada =
        db.withTransaction {
            val atual = itemDao.porCodigo(produto.codigoBarras)?.paraQtds() ?: ItemQtds()
            val resultado = ScanTransitions.aplicar(atual, modo)
            itemDao.salvar(ItemEntity.de(produto.codigoBarras, resultado.depois))
            val eventoId = eventoDao.inserir(
                ScanEventoEntity(
                    codigoBarras = produto.codigoBarras,
                    modo = modo.name,
                    deltaEstoque = resultado.deltaEstoque,
                    deltaLista = resultado.deltaLista,
                    deltaCarrinho = resultado.deltaCarrinho,
                ),
            )
            ResultadoLeitura.Registrada(produto, resultado, modo, eventoId)
        }

    /** Desfaz uma leitura especifica, subtraindo exatamente os deltas que ela aplicou. */
    suspend fun desfazer(eventoId: Long): Boolean = db.withTransaction {
        val evento = eventoDao.porId(eventoId) ?: return@withTransaction false
        if (evento.desfeito) return@withTransaction false
        val atual = itemDao.porCodigo(evento.codigoBarras)?.paraQtds() ?: ItemQtds()
        val revertido = ScanTransitions.reverter(
            atual = atual,
            deltaEstoque = evento.deltaEstoque,
            deltaLista = evento.deltaLista,
            deltaCarrinho = evento.deltaCarrinho,
        )
        itemDao.salvar(ItemEntity.de(evento.codigoBarras, revertido))
        eventoDao.marcarDesfeito(evento.id)
        itemDao.limparVazios()
        true
    }

    suspend fun desfazerUltima(): Boolean {
        val ultimo = eventoDao.ultimoNaoDesfeito() ?: return false
        return desfazer(ultimo.id)
    }

    /** Ajuste manual de quantidade (botoes + e - nas listas). */
    suspend fun ajustar(codigoBarras: String, campo: Campo, delta: Int) = db.withTransaction {
        val atual = itemDao.porCodigo(codigoBarras)?.paraQtds() ?: ItemQtds()
        val novo = when (campo) {
            Campo.ESTOQUE -> atual.copy(estoque = (atual.estoque + delta).coerceAtLeast(0))
            Campo.LISTA -> atual.copy(lista = (atual.lista + delta).coerceAtLeast(0))
            Campo.CARRINHO -> atual.copy(carrinho = (atual.carrinho + delta).coerceAtLeast(0))
        }
        itemDao.salvar(ItemEntity.de(codigoBarras, novo))
        itemDao.limparVazios()
    }

    /** Zera um contador especifico (remover da lista, tirar do estoque). */
    suspend fun zerar(codigoBarras: String, campo: Campo) = db.withTransaction {
        val atual = itemDao.porCodigo(codigoBarras)?.paraQtds() ?: return@withTransaction
        val novo = when (campo) {
            Campo.ESTOQUE -> atual.copy(estoque = 0)
            Campo.LISTA -> atual.copy(lista = 0)
            Campo.CARRINHO -> atual.copy(carrinho = 0)
        }
        itemDao.salvar(ItemEntity.de(codigoBarras, novo))
        itemDao.limparVazios()
    }

    /** Atalho de quem nao quer reescanear em casa: tudo que foi comprado vai para o estoque. */
    suspend fun guardarTudoNoEstoque(): Int = db.withTransaction {
        val noCarrinho = itemDao.itensNoCarrinho()
        noCarrinho.forEach { item ->
            val qtd = item.qtdCarrinho
            itemDao.salvar(
                ItemEntity.de(
                    item.codigoBarras,
                    item.paraQtds().copy(estoque = item.qtdEstoque + qtd, carrinho = 0),
                ),
            )
            eventoDao.inserir(
                ScanEventoEntity(
                    codigoBarras = item.codigoBarras,
                    modo = Modo.GUARDAR.name,
                    deltaEstoque = qtd,
                    deltaLista = 0,
                    deltaCarrinho = -qtd,
                ),
            )
        }
        itemDao.limparVazios()
        noCarrinho.sumOf { it.qtdCarrinho }
    }

    /** Cadastro/edicao de produto sem passar por uma leitura. */
    suspend fun salvarProduto(produto: ProdutoEntity) = produtoDao.salvar(produto)

    suspend fun renomearProduto(codigoBarras: String, nome: String, marca: String?) =
        produtoDao.renomear(codigoBarras, nome, marca)

    /** Item sem codigo de barras (frutas, granel): entra com um codigo interno. */
    suspend fun adicionarItemManual(nome: String, modo: Modo = Modo.ACABOU): ResultadoLeitura.Registrada {
        val produto = ProdutoEntity(
            codigoBarras = "manual:${UUID.randomUUID()}",
            nome = nome.trim(),
            origemNome = ProdutoEntity.ORIGEM_MANUAL,
        )
        return cadastrarEAplicar(produto, modo)
    }

    // ------------------------------------------------------------ Open Food Facts

    /** Sugestao de nome para um codigo desconhecido. Devolve null se estiver offline ou desligado. */
    suspend fun sugerirProduto(codigoBarras: String): SugestaoProduto? {
        if (!preferencias.consultarOpenFoodFacts.value) return null
        return openFoodFacts.buscar(codigoBarras)
    }

    // -------------------------------------------------------------------- backup

    suspend fun exportar(): BackupDados = db.withTransaction {
        BackupDados(
            produtos = produtoDao.todos().map(ProdutoBackup::de),
            itens = itemDao.todos().map(ItemBackup::de),
        )
    }

    /** Importa um backup substituindo tudo que existe hoje. */
    suspend fun importar(dados: BackupDados) = db.withTransaction {
        eventoDao.apagarTudo()
        itemDao.apagarTudo()
        produtoDao.apagarTudo()
        dados.produtos.forEach { produtoDao.salvar(it.paraEntidade()) }
        dados.itens.forEach { itemDao.salvar(it.paraEntidade()) }
        itemDao.limparVazios()
    }
}
