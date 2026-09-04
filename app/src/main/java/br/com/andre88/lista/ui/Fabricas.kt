package br.com.andre88.lista.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.andre88.lista.AppContainer
import br.com.andre88.lista.domain.Modo
import br.com.andre88.lista.ui.casa.CasaViewModel
import br.com.andre88.lista.ui.listas.ListasViewModel
import br.com.andre88.lista.ui.scanner.ScannerViewModel

fun fabricaListas(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { ListasViewModel(container.repositorio, container.sync) }
}

fun fabricaScanner(container: AppContainer, modo: Modo): ViewModelProvider.Factory = viewModelFactory {
    initializer { ScannerViewModel(container.repositorio, container.preferencias, container.sync, modo) }
}

fun fabricaCasa(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { CasaViewModel(container.sync, container.repositorio) }
}
