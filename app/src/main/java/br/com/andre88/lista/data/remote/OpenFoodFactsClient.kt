package br.com.andre88.lista.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** O que conseguimos descobrir sobre um codigo de barras na base publica. */
data class SugestaoProduto(
    val nome: String,
    val marca: String?,
    val imagemUrl: String?,
    val categoria: String?,
)

/**
 * Consulta o Open Food Facts (base publica, sem chave de API) para preencher o nome do
 * produto na primeira leitura. Falha em silencio: sem internet, o app simplesmente pede
 * o nome na mao e continua funcionando.
 */
class OpenFoodFactsClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun buscar(codigoBarras: String): SugestaoProduto? = withContext(Dispatchers.IO) {
        if (!codigoBarras.all { it.isDigit() }) return@withContext null
        val url = "https://world.openfoodfacts.org/api/v2/product/$codigoBarras.json" +
            "?fields=product_name,product_name_pt,brands,image_front_small_url,image_small_url,categories"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val corpo = resp.body?.string().orEmpty()
                if (corpo.isBlank()) return@use null
                val raiz = json.parseToJsonElement(corpo).jsonObject
                val produto = raiz["product"]?.jsonObject ?: return@use null
                fun campo(nome: String): String? = textoDe(produto[nome])

                val nome = campo("product_name_pt") ?: campo("product_name") ?: return@use null
                SugestaoProduto(
                    nome = nome.trim(),
                    marca = campo("brands")?.split(",")?.firstOrNull()?.trim(),
                    imagemUrl = campo("image_front_small_url") ?: campo("image_small_url"),
                    categoria = campo("categories")?.split(",")?.firstOrNull()?.trim(),
                )
            }
        }.onFailure { Log.d(TAG, "Consulta ao Open Food Facts falhou: ${it.message}") }
            .getOrNull()
    }

    /** Le um campo string do JSON, tratando ausente, nulo e vazio como "nao tem". */
    private fun textoDe(elemento: JsonElement?): String? {
        val primitivo = elemento as? JsonPrimitive ?: return null
        if (primitivo is JsonNull || !primitivo.isString) return null
        return primitivo.content.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "OpenFoodFacts"
        const val USER_AGENT = "ListaDeCompras/1.0 (Android; app pessoal)"
    }
}
