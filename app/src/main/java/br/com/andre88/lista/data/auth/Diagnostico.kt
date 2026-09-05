package br.com.andre88.lista.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import br.com.andre88.lista.BuildConfig
import java.security.MessageDigest

/**
 * Dados que explicam a maioria das falhas de login. Ficam visiveis no proprio
 * celular porque a causa quase sempre e uma diferenca entre o que esta no
 * aparelho e o que foi registrado no Google Cloud - e sem ver os dois lados
 * nao da para saber qual.
 */
data class DiagnosticoLogin(
    val pacote: String,
    val sha1: String?,
    val clientIdResumido: String?,
    val temPlayServices: Boolean,
)

fun diagnosticarLogin(contexto: Context): DiagnosticoLogin = DiagnosticoLogin(
    pacote = contexto.packageName,
    sha1 = sha1DoAplicativo(contexto),
    clientIdResumido = BuildConfig.GOOGLE_CLIENT_ID
        .takeIf { it.isNotBlank() }
        ?.substringBefore('-')
        ?.let { "$it-…" },
    temPlayServices = pacoteInstalado(contexto, "com.google.android.gms"),
)

/** A mesma impressao digital que o Google Cloud pede no cliente Android. */
private fun sha1DoAplicativo(contexto: Context): String? = runCatching {
    val gerenciador = contexto.packageManager
    val assinaturas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        gerenciador.getPackageInfo(contexto.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        gerenciador.getPackageInfo(contexto.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    val primeira = assinaturas?.firstOrNull() ?: return@runCatching null
    MessageDigest.getInstance("SHA-1")
        .digest(primeira.toByteArray())
        .joinToString(":") { "%02X".format(it) }
}.getOrNull()

private fun pacoteInstalado(contexto: Context, pacote: String): Boolean = runCatching {
    @Suppress("DEPRECATION")
    contexto.packageManager.getPackageInfo(pacote, 0)
    true
}.getOrDefault(false)
