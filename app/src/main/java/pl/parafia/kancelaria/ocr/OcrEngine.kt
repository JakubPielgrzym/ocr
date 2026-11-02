package pl.parafia.kancelaria.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import pl.parafia.kancelaria.util.cleanWord
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(val text: String, val uncertainWords: Set<String>)

/** Rozpoznawanie tekstu na urządzeniu (ML Kit, model dołączony do aplikacji - bez internetu). */
class OcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Słowa z pewnością poniżej [threshold] trafiają na listę "niepewnych" (podświetlane na żółto). */
    suspend fun recognize(uris: List<Uri>, threshold: Float = 0.75f): OcrResult {
        val sb = StringBuilder()
        val uncertain = linkedSetOf<String>()
        for ((i, uri) in uris.withIndex()) {
            val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
            val result = recognizer.process(image).await()
            if (i > 0) sb.append("\n\n")
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    sb.append(line.text).append('\n')
                    for (el in line.elements) {
                        if (el.confidence < threshold) {
                            val w = cleanWord(el.text)
                            if (w.length >= 3) uncertain += w
                        }
                    }
                }
            }
        }
        return OcrResult(sb.toString().trim(), uncertain)
    }

    fun close() = recognizer.close()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
