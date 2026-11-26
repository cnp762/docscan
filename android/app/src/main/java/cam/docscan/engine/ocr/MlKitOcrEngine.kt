package cam.docscan.engine.ocr

import android.graphics.Bitmap
import cam.docscan.engine.api.OcrLine
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.PointF
import cam.docscan.engine.api.Quad
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR rewrite path (CS mobile_ocr.data encrypted → REWRITE).
 * ML Kit Latin recognizer; langHint reserved for future multi-script packs.
 */
class MlKitOcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(
        image: Bitmap,
        quad: Quad? = null,
        langHint: String? = null,
    ): OcrPage {
        val cropped = quad?.let { cropToQuadBounds(image, it) } ?: image
        val input = InputImage.fromBitmap(cropped, 0)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

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
                lines += OcrLine(text = line.text, confidence = null, box = box)
            }
        }
        val full = text.text.trim()
        val title = lines.firstOrNull { it.text.length >= 4 }?.text
        // Lang finalized by LangDetectEngine in DefaultDocScanEngine / pipeline.
        return OcrPage(
            text = full,
            lang = langHint?.takeIf { it.isNotBlank() },
            langConfidence = if (!langHint.isNullOrBlank()) 0.9f else null,
            lines = lines,
            title = title,
        )
    }

    fun close() {
        recognizer.close()
    }

    private fun cropToQuadBounds(image: Bitmap, quad: Quad): Bitmap {
        val xs = listOf(quad.topLeft.x, quad.topRight.x, quad.bottomRight.x, quad.bottomLeft.x)
        val ys = listOf(quad.topLeft.y, quad.topRight.y, quad.bottomRight.y, quad.bottomLeft.y)
        val left = xs.min().toInt().coerceIn(0, image.width - 1)
        val top = ys.min().toInt().coerceIn(0, image.height - 1)
        val right = xs.max().toInt().coerceIn(left + 1, image.width)
        val bottom = ys.max().toInt().coerceIn(top + 1, image.height)
        return Bitmap.createBitmap(image, left, top, right - left, bottom - top)
    }

}
