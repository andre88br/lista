package br.com.andre88.lista.ui.listas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.andre88.lista.domain.Campo
import br.com.andre88.lista.data.ListaRepository
import br.com.andre88.lista.data.sync.ResultadoSync
import br.com.andre88.lista.data.sync.SyncRepositorio
import br.com.andre88.lista.data.db.ItemComProduto
import br.com.andre88.lista.domain.Modo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Contadores mostrados na tela inicial e na barra de navegacao. */
data class Totais(val lista: Int = 0, val estoque: Int = 0, val carrinho: Int = 0)

class ListasViewModel(
    private val repositorio: ListaRepository,
    private val sync: SyncRepositorio,
) : ViewModel() {

    /** Quantas leituras ainda nao subiram: vira o aviso de "nao sincronizado". */
    val pendentes: StateFlow<Int> = repositorio.eventosPendentes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sincronizacao = sync.estado

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    val listaDeCompras: StateFlow<List<ItemComProduto>> = repositorio.listaDeCompras
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val carrinho: StateFlow<List<ItemComProduto>> = repositorio.carrinho
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estoque ja filtrado pelo texto de busca. */
    val estoque: StateFlow<List<ItemComProduto>> = combine(repositorio.estoque, _busca) { itens, busca ->
        if (busca.isBlank()) {
            itens
        } else {
            itens.filter { it.nome.contains(busca, ignoreCase = true) || it.marca?.contains(busca, true) == true }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totais: StateFlow<Totais> = combine(
        repositorio.totalLista,
        repositorio.totalEstoque,
        repositorio.totalCarrinho,
    ) { lista, estoque, carrinho -> Totais(lista, estoque, carrinho) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Totais())

    fun buscar(texto: String) { _busca.value = texto }

    fun mensagemMostrada() { _mensagem.value = null }

    fun ajustar(item: ItemComProduto, campo: Campo, delta: Int) = viewModelScope.launch {
        repositorio.ajustar(item.codigoBarras, campo, delta)
        sync.solicitarEnvio()
    }

    fun zerar(item: ItemComProduto, campo: Campo) = viewModelScope.launch {
        repositorio.zerar(item.codigoBarras, campo)
        sync.solicitarEnvio()
    }

    /** Marcar como comprado direto na lista, sem escanear (mesmo efeito do modo "No mercado"). */
    fun marcarComprado(item: ItemComProduto) = viewModelScope.launch {
        repositorio.registrarLeitura(item.codigoBarras, Modo.MERCADO)
        sync.solicitarEnvio()
        _mensagem.value = "${item.nome} foi para os comprados"
    }

    /** "Acabou" direto no estoque, sem escanear. */
    fun marcarAcabou(item: ItemComProduto) = viewModelScope.launch {
        repositorio.registrarLeitura(item.codigoBarras, Modo.ACABOU)
        sync.solicitarEnvio()
        _mensagem.value = "${item.nome} entrou na lista de compras"
    }

    /** Guardar um item do carrinho no estoque, sem escanear. */
    fun guardar(item: ItemComProduto) = viewModelScope.launch {
        repositorio.registrarLeitura(item.codigoBarras, Modo.GUARDAR)
        sync.solicitarEnvio()
        _mensagem.value = "${item.nome} foi para o estoque"
    }

    fun guardarTudoNoEstoque() = viewModelScope.launch {
        val quantos = repositorio.guardarTudoNoEstoque()
        sync.solicitarEnvio()
        _mensagem.value = if (quantos > 0) "$quantos item(ns) guardados no estoque" else "O carrinho ja esta vazio"
    }

    fun adicionarItemManual(nome: String) = viewModelScope.launch {
        if (nome.isBlank()) return@launch
        repositorio.adicionarItemManual(nome)
        sync.solicitarEnvio()
        _mensagem.value = "$nome entrou na lista de compras"
    }

    fun renomear(item: ItemComProduto, nome: String, marca: String?) = viewModelScope.launch {
        if (nome.isBlank()) return@launch
        repositorio.renomearProduto(item.codigoBarras, nome.trim(), marca)
        sync.solicitarEnvio()
    }

    /** Sincronizacao pedida na mao (puxar para atualizar). */
    fun sincronizarAgora() = viewModelScope.launch {
        when (val resultado = sync.sincronizar()) {
            is ResultadoSync.Ok -> {
                _mensagem.value = if (resultado.recebidos > 0) {
                    "${resultado.recebidos} novidade(s) da casa"
                } else {
                    "Tudo em dia"
                }
            }
            is ResultadoSync.Falhou -> _mensagem.value = "Não sincronizou: ${resultado.mensagem}"
            ResultadoSync.Desligada -> Unit
        }
    }

    /** Texto da lista para compartilhar no WhatsApp. */
    fun textoDaLista(): String {
        val itens = listaDeCompras.value
        if (itens.isEmpty()) return "Lista de compras vazia"
        return buildString {
            appendLine("Lista de compras:")
            itens.forEach { item ->
                append("- ")
                append(item.nome)
                if (item.qtdLista > 1) append(" (${item.qtdLista})")
                appendLine()
            }
        }.trim()
    }
}
