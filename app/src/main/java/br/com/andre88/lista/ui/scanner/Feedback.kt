package br.com.andre88.lista.ui.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/** Bip curto + vibracao a cada leitura aceita: no mercado voce nao fica olhando a tela. */
class Feedback(context: Context) {

    private val appContext = context.applicationContext

    private val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService<Vibrator>()
    }

    private val tom: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    }.getOrNull()

    fun sucesso(comSom: Boolean) {
        if (comSom) tom?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrar(60)
    }

    fun erro() = vibrar(200)

    private fun vibrar(ms: Long) {
        val v = vibrador ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    fun liberar() = tom?.release()
}
