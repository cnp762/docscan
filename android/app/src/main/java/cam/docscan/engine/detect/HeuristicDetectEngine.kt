package cam.docscan.engine.detect

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import cam.docscan.engine.api.PointF
import cam.docscan.engine.api.Quad
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Unified document quad detector:
 * 1) DocAligner ONNX model when `modelsDir/detect/docaligner.onnx` is present
 * 2) Luminance-gradient border scan heuristic fallback
 * 3) Inset quad safety fallback
 */
class HeuristicDetectEngine {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    fun initialize(modelsDir: File?) {
        val modelFile = modelsDir?.resolve("detect/docaligner.onnx")?.takeIf { it.isFile } ?: return
        runCatching {
            val env = OrtEnvironment.getEnvironment()
            ortEnv = env
            ortSession = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        }
    }

    fun detect(image: Bitmap): Quad? {
        val w = image.width
        val h = image.height
        if (w < 32 || h < 32) return null

        detectWithDocAligner(image)?.let { return it }
        detectWithLuminanceBounds(image)?.let { return it }

        val insetX = w * 0.06f
        val insetY = h * 0.06f
        return Quad(
            topLeft = PointF(insetX, insetY),
            topRight = PointF(w - insetX, insetY),
            bottomRight = PointF(w - insetX, h - insetY),
            bottomLeft = PointF(insetX, h - insetY),
        )
    }

    private fun detectWithDocAligner(image: Bitmap): Quad? {
        val env = ortEnv ?: return null
        val session = ortSession ?: return null
        return runCatching {
            val size = DOCALIGNER_INPUT_SIZE
            val resized = Bitmap.createScaledBitmap(image, size, size, true)
            val pixels = IntArray(size * size)
            resized.getPixels(pixels, 0, size, 0, 0, size, size)
            if (resized !== image) resized.recycle()

            val chw = FloatBuffer.allocate(3 * size * size)
            val plane = size * size
            val arr = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val p = pixels[i]
                arr[i] = ((p shr 16) and 0xff) / 255f
                arr[plane + i] = ((p shr 8) and 0xff) / 255f
                arr[2 * plane + i] = (p and 0xff) / 255f
            }
            chw.put(arr)
            chw.rewind()

            val inputName = session.inputNames.first()
            OnnxTensor.createTensor(env, chw, longArrayOf(1, 3, size.toLong(), size.toLong())).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { result ->
                    val raw = result[0].value
                    decodeCorners(raw, image.width.toFloat(), image.height.toFloat())
                }
            }
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeCorners(raw: Any?, w: Float, h: Float): Quad? {
        val flat: FloatArray = when (raw) {
            is Array<*> -> (raw.firstOrNull() as? FloatArray) ?: return null
            is FloatArray -> raw
            else -> return null
        }
        if (flat.size < 8) return null
        return Quad(
            topLeft = PointF(flat[0].coerceIn(0f, 1f) * w, flat[1].coerceIn(0f, 1f) * h),
            topRight = PointF(flat[2].coerceIn(0f, 1f) * w, flat[3].coerceIn(0f, 1f) * h),
            bottomRight = PointF(flat[4].coerceIn(0f, 1f) * w, flat[5].coerceIn(0f, 1f) * h),
            bottomLeft = PointF(flat[6].coerceIn(0f, 1f) * w, flat[7].coerceIn(0f, 1f) * h),
        )
    }

    /** Downscaled luminance-edge scan for a bright/contrasting page region. */
    private fun detectWithLuminanceBounds(image: Bitmap): Quad? {
        val maxDim = 240
        val scale = maxDim.toFloat() / max(image.width, image.height)
        val sw = (image.width * scale).roundToInt().coerceAtLeast(32)
        val sh = (image.height * scale).roundToInt().coerceAtLeast(32)
        val small = Bitmap.createScaledBitmap(image, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (small !== image) small.recycle()

        val luma = IntArray(sw * sh) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xff) * 30 + (p shr 8 and 0xff) * 59 + (p and 0xff) * 11) / 100
        }
        val rowEnergy = IntArray(sh)
        val colEnergy = IntArray(sw)
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val gx = abs(luma[y * sw + x + 1] - luma[y * sw + x - 1])
                val gy = abs(luma[(y + 1) * sw + x] - luma[(y - 1) * sw + x])
                val g = gx + gy
                if (g > 36) {
                    rowEnergy[y]++
                    colEnergy[x]++
                }
            }
        }
        val minRowHit = sw / 8
        val minColHit = sh / 8
        val top = (0 until sh / 2).firstOrNull { rowEnergy[it] >= minRowHit } ?: return null
        val bottom = (sh - 1 downTo sh / 2).firstOrNull { rowEnergy[it] >= minRowHit } ?: return null
        val left = (0 until sw / 2).firstOrNull { colEnergy[it] >= minColHit } ?: return null
        val right = (sw - 1 downTo sw / 2).firstOrNull { colEnergy[it] >= minColHit } ?: return null
        if (right - left < sw / 3 || bottom - top < sh / 3) return null

        val invX = image.width.toFloat() / sw
        val invY = image.height.toFloat() / sh
        return Quad(
            topLeft = PointF(left * invX, top * invY),
            topRight = PointF(right * invX, top * invY),
            bottomRight = PointF(right * invX, bottom * invY),
            bottomLeft = PointF(left * invX, bottom * invY),
        )
    }

    fun fullFrame(image: Bitmap): Quad =
        Quad(
            topLeft = PointF(0f, 0f),
            topRight = PointF(image.width.toFloat(), 0f),
            bottomRight = PointF(image.width.toFloat(), image.height.toFloat()),
            bottomLeft = PointF(0f, image.height.toFloat()),
        )

    fun close() {
        runCatching { ortSession?.close() }
        ortSession = null
    }

    companion object {
        private const val DOCALIGNER_INPUT_SIZE = 256
    }
}
