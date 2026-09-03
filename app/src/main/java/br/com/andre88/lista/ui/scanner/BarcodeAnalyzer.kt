package br.com.andre88.lista.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Le os formatos usados em embalagens de supermercado. O modelo do ML Kit vai embutido
 * no APK, entao a leitura funciona sem internet dentro do mercado.
 */
class BarcodeAnalyzer(private val aoLer: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val leitor: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
            )
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imagem: ImageProxy) {
        val frame = imagem.image
        if (frame == null) {
            imagem.close()
            return
        }
        val entrada = InputImage.fromMediaImage(frame, imagem.imageInfo.rotationDegrees)
        leitor.process(entrada)
            .addOnSuccessListener { codigos ->
                codigos.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }?.let(aoLer)
            }
            .addOnCompleteListener { imagem.close() }
    }

    fun fechar() = leitor.close()
}
