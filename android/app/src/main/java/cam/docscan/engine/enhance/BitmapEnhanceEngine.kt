package cam.docscan.engine.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import cam.docscan.engine.api.EnhancePreset
import kotlin.math.max
import kotlin.math.min

/**
 * CS magic-enhancer rewrite (no .so): ColorMatrix presets approximating
 * color / gray / B&W / document contrast / deshadow proxy.
 */
class BitmapEnhanceEngine {

    fun enhance(image: Bitmap, preset: EnhancePreset): Bitmap {
        if (preset == EnhancePreset.ORIGINAL) return image
        val src = if (image.config == Bitmap.Config.ARGB_8888) image else image.copy(Bitmap.Config.ARGB_8888, false)
        return when (preset) {
            EnhancePreset.DESHADOW -> deshadowProxy(src)
            else -> applyMatrix(src, matrixFor(preset))
        }
    }

    private fun matrixFor(preset: EnhancePreset): ColorMatrix = when (preset) {
        EnhancePreset.COLOR -> ColorMatrix().apply { setSaturation(1.15f) }
        EnhancePreset.GRAY -> ColorMatrix().apply { setSaturation(0f) }
        EnhancePreset.BW -> ColorMatrix(
            floatArrayOf(
                85f, 85f, 85f, 0f, -128f * 85f,
                85f, 85f, 85f, 0f, -128f * 85f,
                85f, 85f, 85f, 0f, -128f * 85f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ).also {
            // High-contrast B&W via scale after grayscale
            val g = ColorMatrix().apply { setSaturation(0f) }
            val c = ColorMatrix(
                floatArrayOf(
                    1.8f, 0f, 0f, 0f, -80f,
                    0f, 1.8f, 0f, 0f, -80f,
                    0f, 0f, 1.8f, 0f, -80f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            g.postConcat(c)
            it.set(g)
        }
        EnhancePreset.DOCUMENT -> ColorMatrix(
            floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.2f, 0f, 0f, 10f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ).also { cm ->
            val sat = ColorMatrix().apply { setSaturation(0.85f) }
            sat.postConcat(cm)
            cm.set(sat)
        }
        else -> ColorMatrix()
    }

    private fun applyMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Local illumination normalize proxy (not CS deshadow). */
    private fun deshadowProxy(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var sum = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            sum += (r * 30 + g * 59 + b * 11) / 100
        }
        val mean = (sum / pixels.size).toInt().coerceIn(1, 255)
        val target = 180
        val outPix = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = (p shr 16) and 0xff
            var g = (p shr 8) and 0xff
            var b = p and 0xff
            val y = (r * 30 + g * 59 + b * 11) / 100
            val scale = target.toFloat() / max(y, 1)
            val s = min(scale, 2.2f)
            r = min(255, (r * s).toInt())
            g = min(255, (g * s).toInt())
            b = min(255, (b * s).toInt())
            // Pull toward mean-corrected document look
            val mix = 0.35f
            r = ((r * (1 - mix)) + (r * target / mean) * mix).toInt().coerceIn(0, 255)
            g = ((g * (1 - mix)) + (g * target / mean) * mix).toInt().coerceIn(0, 255)
            b = ((b * (1 - mix)) + (b * target / mean) * mix).toInt().coerceIn(0, 255)
            outPix[i] = (p and 0xff000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPix, 0, w, 0, 0, w, h)
        return applyMatrix(out, matrixFor(EnhancePreset.DOCUMENT))
    }
}
