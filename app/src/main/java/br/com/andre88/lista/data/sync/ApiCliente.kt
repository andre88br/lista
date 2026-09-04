package br.com.andre88.lista.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente do servidor de sincronizacao. Deliberadamente simples: OkHttp e JSON,
 * o mesmo par que o app ja usa para consultar o Open Food Facts.
 */
class ApiCliente(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tipoJson = "application/json; charset=utf-8".toMediaType()

    suspend fun registrarDispositivo(servidor: String, nome: String): RespostaDispositivo {
        val corpo = buildJsonObject { put("nome", nome) }.toString()
        return json.decodeFromString(
            RespostaDispositivo.serializer(),
            executar(servidor, "/v1/dispositivos", token = null, metodo = "POST", corpoJson = corpo),
        )
    }

    suspend fun criarCasa(servidor: String, token: String, nome: String): RespostaCasa {
        val corpo = buildJsonObject { put("nome", nome) }.toString()
        return json.decodeFromString(
            RespostaCasa.serializer(),
            executar(servidor, "/v1/casas", token, "POST", corpo),
        )
    }

    suspend fun entrarNaCasa(servidor: String, token: String, codigo: String): RespostaCasa {
        val corpo = buildJsonObject { put("codigo", codigo) }.toString()
        return json.decodeFromString(
            RespostaCasa.serializer(),
            executar(servidor, "/v1/casas/entrar", token, "POST", corpo),
        )
    }

    suspend fun casaAtual(servidor: String, token: String): RespostaCasa =
        json.decodeFromString(
            RespostaCasa.serializer(),
            executar(servidor, "/v1/casas/atual", token, "GET", null),
        )

    suspend fun novoCodigo(servidor: String, token: String): RespostaCasa =
        json.decodeFromString(
            RespostaCasa.serializer(),
            executar(servidor, "/v1/casas/codigo", token, "POST", "{}"),
        )

    suspend fun sairDaCasa(servidor: String, token: String) {
        executar(servidor, "/v1/casas/atual", token, "DELETE", null)
    }

    suspend fun enviar(servidor: String, token: String, envio: EnvioSync): RespostaEnvio {
        val corpo = json.encodeToString(EnvioSync.serializer(), envio)
        return json.decodeFromString(
            RespostaEnvio.serializer(),
            executar(servidor, "/v1/sync", token, "POST", corpo),
        )
    }

    suspend fun buscar(servidor: String, token: String, desde: Long, produtosDesde: String?): RespostaLeitura {
        val caminho = buildString {
            append("/v1/sync?desde=").append(desde)
            if (!produtosDesde.isNullOrBlank()) {
                append("&produtosDesde=").append(java.net.URLEncoder.encode(produtosDesde, "UTF-8"))
            }
        }
        return json.decodeFromString(
            RespostaLeitura.serializer(),
            executar(servidor, caminho, token, "GET", null),
        )
    }

    suspend fun instantaneo(servidor: String, token: String): RespostaInstantaneo =
        json.decodeFromString(
            RespostaInstantaneo.serializer(),
            executar(servidor, "/v1/sync/instantaneo", token, "GET", null),
        )

    /** Usado pela tela de ajustes para dizer se o endereco digitado responde. */
    suspend fun servidorResponde(servidor: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("$servidor/saude").build()).execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun executar(
        servidor: String,
        caminho: String,
        token: String?,
        metodo: String,
        corpoJson: String?,
    ): String = withContext(Dispatchers.IO) {
        val construtor = Request.Builder().url("$servidor$caminho")
        token?.let { construtor.header("Authorization", "Bearer $it") }
        when (metodo) {
            "GET" -> construtor.get()
            "DELETE" -> construtor.delete()
            else -> construtor.post((corpoJson ?: "{}").toRequestBody(tipoJson))
        }

        http.newCall(construtor.build()).execute().use { resposta ->
            val texto = resposta.body?.string().orEmpty()
            if (!resposta.isSuccessful) {
                val mensagem = runCatching {
                    json.decodeFromString(RespostaErro.serializer(), texto).erro
                }.getOrNull() ?: "o servidor respondeu ${resposta.code}"
                throw ErroDaApi(resposta.code, mensagem)
            }
            if (metodo == "DELETE" && texto.isBlank()) return@use "{}"
            if (texto.isBlank()) throw IOException("resposta vazia do servidor")
            texto
        }
    }
}
