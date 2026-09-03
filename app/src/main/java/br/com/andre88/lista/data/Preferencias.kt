package br.com.andre88.lista.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes simples do app, guardados em SharedPreferences. */
class Preferencias(context: Context) {

    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _consultarOpenFoodFacts = MutableStateFlow(prefs.getBoolean(CHAVE_OFF, true))
    val consultarOpenFoodFacts: StateFlow<Boolean> = _consultarOpenFoodFacts

    private val _cooldownMs = MutableStateFlow(prefs.getLong(CHAVE_COOLDOWN, COOLDOWN_PADRAO))
    val cooldownMs: StateFlow<Long> = _cooldownMs

    private val _somAoLer = MutableStateFlow(prefs.getBoolean(CHAVE_SOM, true))
    val somAoLer: StateFlow<Boolean> = _somAoLer

    fun definirConsultarOpenFoodFacts(valor: Boolean) {
        prefs.edit().putBoolean(CHAVE_OFF, valor).apply()
        _consultarOpenFoodFacts.value = valor
    }

    fun definirCooldown(ms: Long) {
        prefs.edit().putLong(CHAVE_COOLDOWN, ms).apply()
        _cooldownMs.value = ms
    }

    fun definirSomAoLer(valor: Boolean) {
        prefs.edit().putBoolean(CHAVE_SOM, valor).apply()
        _somAoLer.value = valor
    }

    companion object {
        const val COOLDOWN_PADRAO = 2500L
        private const val CHAVE_OFF = "consultar_off"
        private const val CHAVE_COOLDOWN = "cooldown_ms"
        private const val CHAVE_SOM = "som_ao_ler"
    }
}
