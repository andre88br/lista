package br.com.andre88.lista.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.andre88.lista.AppContainer
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.ui.ajustes.AjustesScreen
import br.com.andre88.lista.ui.home.HomeScreen
import br.com.andre88.lista.ui.listas.CarrinhoScreen
import br.com.andre88.lista.ui.listas.EstoqueScreen
import br.com.andre88.lista.ui.listas.ListaComprasScreen
import br.com.andre88.lista.ui.listas.ListasViewModel
import br.com.andre88.lista.ui.scanner.ScannerScreen

object Rotas {
    const val HOME = "home"
    const val LISTA = "lista"
    const val ESTOQUE = "estoque"
    const val CARRINHO = "carrinho"
    const val AJUSTES = "ajustes"
    const val SCANNER = "scanner/{modo}"

    fun scanner(modo: Modo) = "scanner/${modo.name}"
}

private data class AbaNav(
    val rota: String,
    val titulo: String,
    val icone: ImageVector,
)

private val abas = listOf(
    AbaNav(Rotas.HOME, "Início", Icons.Filled.Home),
    AbaNav(Rotas.LISTA, "Comprar", Icons.Filled.ShoppingBasket),
    AbaNav(Rotas.CARRINHO, "Comprados", Icons.Filled.ShoppingCart),
    AbaNav(Rotas.ESTOQUE, "Estoque", Icons.Filled.Inventory2),
)

@Composable
fun AppLista(container: AppContainer) {
    val navController = rememberNavController()
    val listasViewModel: ListasViewModel = viewModel(factory = fabricaListas(container))
    val totais by listasViewModel.totais.collectAsStateWithLifecycle()

    val entradaAtual by navController.currentBackStackEntryAsState()
    val rotaAtual = entradaAtual?.destination?.route
    val mostrarBarra = abas.any { it.rota == rotaAtual }

    Scaffold(
        bottomBar = {
            if (mostrarBarra) {
                NavigationBar {
                    abas.forEach { aba ->
                        val selecionada = entradaAtual?.destination?.hierarchy?.any { it.route == aba.rota } == true
                        val contador = when (aba.rota) {
                            Rotas.LISTA -> totais.lista
                            Rotas.CARRINHO -> totais.carrinho
                            Rotas.ESTOQUE -> totais.estoque
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = selecionada,
                            onClick = {
                                navController.navigate(aba.rota) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(badge = {
                                    if (contador > 0) Badge { Text(contador.toString()) }
                                }) {
                                    Icon(aba.icone, contentDescription = aba.titulo)
                                }
                            },
                            label = { Text(aba.titulo) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (mostrarBarra) Modifier.padding(padding) else Modifier),
        ) {
            NavHost(navController = navController, startDestination = Rotas.HOME) {
                composable(Rotas.HOME) {
                    HomeScreen(
                        viewModel = listasViewModel,
                        aoEscolherModo = { modo -> navController.navigate(Rotas.scanner(modo)) },
                        aoAbrirAjustes = { navController.navigate(Rotas.AJUSTES) },
                    )
                }
                composable(Rotas.LISTA) {
                    ListaComprasScreen(
                        viewModel = listasViewModel,
                        aoEscanear = { navController.navigate(Rotas.scanner(Modo.MERCADO)) },
                    )
                }
                composable(Rotas.CARRINHO) {
                    CarrinhoScreen(
                        viewModel = listasViewModel,
                        aoEscanear = { navController.navigate(Rotas.scanner(Modo.GUARDAR)) },
                    )
                }
                composable(Rotas.ESTOQUE) {
                    EstoqueScreen(
                        viewModel = listasViewModel,
                        aoEscanear = { navController.navigate(Rotas.scanner(Modo.ACABOU)) },
                    )
                }
                composable(Rotas.AJUSTES) {
                    AjustesScreen(
                        container = container,
                        aoVoltar = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Rotas.SCANNER,
                    arguments = listOf(navArgument("modo") { type = NavType.StringType }),
                ) { entrada ->
                    val modo = Modo.deNome(entrada.arguments?.getString("modo"))
                    ScannerScreen(
                        modo = modo,
                        container = container,
                        aoVoltar = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
