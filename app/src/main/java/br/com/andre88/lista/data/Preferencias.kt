package br.com.andre88.lista.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dados da casa compartilhada, quando a sincronizacao esta ligada. */
data class Casa(
    val id: String,
    val nome: String,
    val codigo: String,
    val membros: Int = 1,
)

/** O que a tela de ajustes precisa saber sobre a sincronizacao. */
data class EstadoSincronizacao(
    val servidorUrl: String? = null,
    val registrado: Boolean = false,
    val casa: Casa? = null,
    val ultimaSync: Long = 0,
    val ultimoErro: String? = null,
) {
    val ativa: Boolean get() = servidorUrl != null && registrado && casa != null
}

/** Ajustes do app e configuracao da sincronizacao, guardados em SharedPreferences. */
class Preferencias(context: Context) {

    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ do app

    private val _consultarOpenFoodFacts = MutableStateFlow(prefs.getBoolean(CHAVE_OFF, true))
    val consultarOpenFoodFacts: StateFlow<Boolean> = _consultarOpenFoodFacts.asStateFlow()

    private val _cooldownMs = MutableStateFlow(prefs.getLong(CHAVE_COOLDOWN, COOLDOWN_PADRAO))
    val cooldownMs: StateFlow<Long> = _cooldownMs.asStateFlow()

    private val _somAoLer = MutableStateFlow(prefs.getBoolean(CHAVE_SOM, true))
    val somAoLer: StateFlow<Boolean> = _somAoLer.asStateFlow()

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

    // -------------------------------------------------------- da sincronizacao

    private val _sincronizacao = MutableStateFlow(lerEstadoSincronizacao())
    val sincronizacao: StateFlow<EstadoSincronizacao> = _sincronizacao.asStateFlow()

    val servidorUrl: String? get() = prefs.getString(CHAVE_SERVIDOR, null)
    val dispositivoId: String? get() = prefs.getString(CHAVE_DISPOSITIVO, null)
    val token: String? get() = prefs.getString(CHAVE_TOKEN, null)
    val casaId: String? get() = prefs.getString(CHAVE_CASA_ID, null)

    /** Ate onde ja lemos o historico do servidor. */
    var cursorSeq: Long
        get() = prefs.getLong(CHAVE_CURSOR_SEQ, 0)
        set(valor) = prefs.edit().putLong(CHAVE_CURSOR_SEQ, valor).apply()

    /** Data do produto mais recente que ja baixamos. */
    var cursorProdutos: Long
        get() = prefs.getLong(CHAVE_CURSOR_PRODUTOS, 0)
        set(valor) = prefs.edit().putLong(CHAVE_CURSOR_PRODUTOS, valor).apply()

    /** Data do produto mais recente que ja enviamos. */
    var cursorProdutosEnviados: Long
        get() = prefs.getLong(CHAVE_CURSOR_ENVIO, 0)
        set(valor) = prefs.edit().putLong(CHAVE_CURSOR_ENVIO, valor).apply()

    fun definirServidor(url: String?) {
        prefs.edit().putString(CHAVE_SERVIDOR, url?.trimEnd('/')).apply()
        atualizarEstado()
    }

    fun definirDispositivo(id: String, token: String) {
        prefs.edit().putString(CHAVE_DISPOSITIVO, id).putString(CHAVE_TOKEN, token).apply()
        atualizarEstado()
    }

    fun definirCasa(casa: Casa?) {
        prefs.edit()
            .putString(CHAVE_CASA_ID, casa?.id)
            .putString(CHAVE_CASA_NOME, casa?.nome)
            .putString(CHAVE_CASA_CODIGO, casa?.codigo)
            .putInt(CHAVE_CASA_MEMBROS, casa?.membros ?: 0)
            .apply()
        if (casa == null) {
            cursorSeq = 0
            cursorProdutos = 0
            cursorProdutosEnviados = 0
        }
        atualizarEstado()
    }

    fun registrarSincronizacao(quando: Long, erro: String?) {
        prefs.edit().putLong(CHAVE_ULTIMA_SYNC, quando).putString(CHAVE_ULTIMO_ERRO, erro).apply()
        atualizarEstado()
    }

    /** Desliga a sincronizacao neste aparelho, sem apagar as listas. */
    fun esquecerSincronizacao() {
        prefs.edit()
            .remove(CHAVE_CASA_ID).remove(CHAVE_CASA_NOME).remove(CHAVE_CASA_CODIGO)
            .remove(CHAVE_CASA_MEMBROS).remove(CHAVE_CURSOR_SEQ).remove(CHAVE_CURSOR_PRODUTOS)
            .remove(CHAVE_CURSOR_ENVIO).remove(CHAVE_ULTIMA_SYNC).remove(CHAVE_ULTIMO_ERRO)
            .apply()
        atualizarEstado()
    }

    private fun atualizarEstado() {
        _sincronizacao.value = lerEstadoSincronizacao()
    }

    private fun lerEstadoSincronizacao(): EstadoSincronizacao {
        val casaId = prefs.getString(CHAVE_CASA_ID, null)
        return EstadoSincronizacao(
            servidorUrl = prefs.getString(CHAVE_SERVIDOR, null),
            registrado = prefs.getString(CHAVE_TOKEN, null) != null,
            casa = casaId?.let {
                Casa(
                    id = it,
                    nome = prefs.getString(CHAVE_CASA_NOME, "Minha casa").orEmpty(),
                    codigo = prefs.getString(CHAVE_CASA_CODIGO, "").orEmpty(),
                    membros = prefs.getInt(CHAVE_CASA_MEMBROS, 1),
                )
            },
            ultimaSync = prefs.getLong(CHAVE_ULTIMA_SYNC, 0),
            ultimoErro = prefs.getString(CHAVE_ULTIMO_ERRO, null),
        )
    }

    companion object {
        const val COOLDOWN_PADRAO = 2500L

        private const val CHAVE_OFF = "consultar_off"
        private const val CHAVE_COOLDOWN = "cooldown_ms"
        private const val CHAVE_SOM = "som_ao_ler"
        private const val CHAVE_SERVIDOR = "servidor_url"
        private const val CHAVE_DISPOSITIVO = "dispositivo_id"
        private const val CHAVE_TOKEN = "dispositivo_token"
        private const val CHAVE_CASA_ID = "casa_id"
        private const val CHAVE_CASA_NOME = "casa_nome"
        private const val CHAVE_CASA_CODIGO = "casa_codigo"
        private const val CHAVE_CASA_MEMBROS = "casa_membros"
        private const val CHAVE_CURSOR_SEQ = "cursor_seq"
        private const val CHAVE_CURSOR_PRODUTOS = "cursor_produtos"
        private const val CHAVE_CURSOR_ENVIO = "cursor_envio"
        private const val CHAVE_ULTIMA_SYNC = "ultima_sync"
        private const val CHAVE_ULTIMO_ERRO = "ultimo_erro"
    }
}
