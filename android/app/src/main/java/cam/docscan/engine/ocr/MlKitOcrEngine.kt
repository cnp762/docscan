package cam.docscan.engine.ocr

import android.graphics.Bitmap
import cam.docscan.engine.api.OcrLine
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.PointF
import cam.docscan.engine.api.Quad
import cam.docscan.engine.enhance.BitmapEnhanceEngine
import cam.docscan.engine.runtime.DeviceCapability
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Multi-script ML Kit OCR with automatic CJK/Devanagari fallback
 * and automatic upright orientation correction.
 */
class MlKitOcrEngine(
    private val enhanceEngine: BitmapEnhanceEngine = BitmapEnhanceEngine(),
) {
    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val chineseRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val japaneseRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    private val koreanRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    private val devanagariRecognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    suspend fun recognize(
        image: Bitmap,
        quad: Quad? = null,
        langHint: String? = null,
    ): OcrPage {
        val warped = quad?.let { enhanceEngine.perspectiveWarp(image, it) } ?: image
        val prepared = enhanceEngine.downscale(warped, DeviceCapability.NLP_MAX_LONG_EDGE)

        val primaryScript = scriptForHint(langHint)
        var result = runWithFallback(prepared, primaryScript)

        // Orientation check -> auto-apply upright rotation and re-OCR once if rotated.
        val rotation = detectRotationDegrees(result)
        if (rotation != 0) {
            val upright = enhanceEngine.rotate(prepared, rotation)
            result = runWithFallback(upright, primaryScript)
        }
        return toOcrPage(result, langHint)
    }

    private suspend fun runWithFallback(bitmap: Bitmap, primary: Script): Text {
        val primaryText = process(recognizerFor(primary), bitmap)
        if (scoreTextRichness(primaryText.text) >= MIN_PRIMARY_CHARS) {
            return primaryText
        }
        var best = primaryText
        var bestScore = scoreTextRichness(primaryText.text)
        for (script in Script.entries) {
            if (script == primary) continue
            val candidate = runCatching { process(recognizerFor(script), bitmap) }.getOrNull() ?: continue
            val score = scoreTextRichness(candidate.text)
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
            if (bestScore >= MIN_PRIMARY_CHARS * 2) break
        }
        return best
    }

    private suspend fun process(recognizer: TextRecognizer, bitmap: Bitmap): Text {
        val input = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    private fun toOcrPage(text: Text, langHint: String?): OcrPage {
        val lines = mutableListOf<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox?.let { r ->
                    listOf(
                        PointF(r.left.toFloat(), r.top.toFloat()),
                        PointF(r.right.toFloat(), r.top.toFloat()),
                        PointF(r.right.toFloat(), r.bottom.toFloat()),
                        PointF(r.left.toFloat(), r.bottom.toFloat()),
                    )
                }
                lines += OcrLine(text = line.text, confidence = line.confidence, box = box)
            }
        }
        val full = text.text.trim()
        val title = lines.firstOrNull { it.text.length >= 4 }?.text
        return OcrPage(
            text = full,
            lang = langHint?.takeIf { it.isNotBlank() },
            langConfidence = if (!langHint.isNullOrBlank()) 0.9f else null,
            lines = lines,
            title = title,
        )
    }

    private fun detectRotationDegrees(text: Text): Int {
        val angles = text.textBlocks.flatMap { it.lines }.map { it.angle }
        if (angles.isEmpty()) return 0
        val sorted = angles.sorted()
        val median = sorted[sorted.size / 2]
        // ML Kit line.angle is clockwise tilt; rotate by -median to make upright.
        return snapRotationDegrees(-median)
    }

    private fun recognizerFor(script: Script): TextRecognizer = when (script) {
        Script.LATIN -> latinRecognizer
        Script.CHINESE -> chineseRecognizer
        Script.JAPANESE -> japaneseRecognizer
        Script.KOREAN -> koreanRecognizer
        Script.DEVANAGARI -> devanagariRecognizer
    }

    private fun scriptForHint(langHint: String?): Script {
        val h = langHint?.trim()?.lowercase().orEmpty()
        return when {
            h.startsWith("zh") -> Script.CHINESE
            h.startsWith("ja") -> Script.JAPANESE
            h.startsWith("ko") -> Script.KOREAN
            h.startsWith("hi") || h.startsWith("mr") || h.startsWith("ne") || h.startsWith("sa") -> Script.DEVANAGARI
            else -> Script.LATIN
        }
    }

    private fun scoreTextRichness(text: String): Int =
        text.count { it.isLetterOrDigit() }

    fun close() {
        runCatching { latinRecognizer.close() }
        runCatching { chineseRecognizer.close() }
        runCatching { japaneseRecognizer.close() }
        runCatching { koreanRecognizer.close() }
        runCatching { devanagariRecognizer.close() }
    }

    private enum class Script {
        LATIN,
        CHINESE,
        JAPANESE,
        KOREAN,
        DEVANAGARI,
    }

    companion object {
        private const val MIN_PRIMARY_CHARS = 8

        fun snapRotationDegrees(angle: Float): Int {
            val norm = ((angle % 360f) + 360f) % 360f
            return when {
                abs(norm - 90f) <= 25f -> 90
                abs(norm - 180f) <= 25f -> 180
                abs(norm - 270f) <= 25f -> 270
                else -> 0
            }
        }
    }
}
