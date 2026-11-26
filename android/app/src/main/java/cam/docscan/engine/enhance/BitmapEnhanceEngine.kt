package cam.docscan.engine.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import cam.docscan.engine.api.EnhancePreset
import cam.docscan.engine.api.Quad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-device image enhancement: perspective warp, rotation, illumination
 * normalization (background division deshadow), and integral-image adaptive B&W.
 */
class BitmapEnhanceEngine {

    fun enhance(image: Bitmap, preset: EnhancePreset): Bitmap {
        if (preset == EnhancePreset.ORIGINAL) return image
        val src = if (image.config == Bitmap.Config.ARGB_8888) image else image.copy(Bitmap.Config.ARGB_8888, false)
        return when (preset) {
            EnhancePreset.DESHADOW -> deshadow(src)
            EnhancePreset.BW -> adaptiveBw(src)
            else -> applyMatrix(src, matrixFor(preset))
        }
    }

    fun perspectiveWarp(image: Bitmap, quad: Quad): Bitmap {
        val (dstW, dstH) = computeQuadTargetSize(quad)
        val srcPts = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y,
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            dstW.toFloat(), 0f,
            dstW.toFloat(), dstH.toFloat(),
            0f, dstH.toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            return image
        }
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(image, matrix, paint)
        return out
    }

    fun rotate(image: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return image
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
    }

    fun downscale(image: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = max(image.width, image.height)
        if (longEdge <= maxLongEdge || maxLongEdge <= 0) return image
        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val w = (image.width * scale).roundToInt().coerceAtLeast(1)
        val h = (image.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(image, w, h, true)
    }

    private fun matrixFor(preset: EnhancePreset): ColorMatrix = when (preset) {
        EnhancePreset.COLOR -> ColorMatrix().apply { setSaturation(1.15f) }
        EnhancePreset.GRAY -> ColorMatrix().apply { setSaturation(0f) }
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

    /**
     * Illumination normalization via background division:
     * downscales to estimate local background illumination via separable box blur,
     * then divides source pixels by the blurred background to remove local shadows.
     */
    private fun deshadow(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val bgW = (w / 8).coerceIn(16, 180)
        val bgH = (h / 8).coerceIn(16, 180)
        val small = Bitmap.createScaledBitmap(src, bgW, bgH, true)
        val smallPix = IntArray(bgW * bgH)
        small.getPixels(smallPix, 0, bgW, 0, 0, bgW, bgH)
        val bgLuma = boxBlurLuma(smallPix, bgW, bgH, radius = max(3, minOf(bgW, bgH) / 14))
        small.recycle()

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPix = IntArray(pixels.size)
        for (y in 0 until h) {
            val by = (y * bgH / h).coerceIn(0, bgH - 1)
            val rowOff = y * w
            val bgRowOff = by * bgW
            for (x in 0 until w) {
                val bx = (x * bgW / w).coerceIn(0, bgW - 1)
                val bg = max(bgLuma[bgRowOff + bx], 16)
                val p = pixels[rowOff + x]
                val r = (((p shr 16) and 0xff) * 240 / bg).coerceIn(0, 255)
                val g = (((p shr 8) and 0xff) * 240 / bg).coerceIn(0, 255)
                val b = ((p and 0xff) * 240 / bg).coerceIn(0, 255)
                outPix[rowOff + x] = (p and 0xff000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPix, 0, w, 0, 0, w, h)
        return out
    }

    /** Adaptive B&W using deshadow + O(W*H) integral-image local mean thresholding. */
    private fun adaptiveBw(src: Bitmap): Bitmap {
        val clean = deshadow(src)
        val w = clean.width
        val h = clean.height
        val pixels = IntArray(w * h)
        clean.getPixels(pixels, 0, w, 0, 0, w, h)
        if (clean !== src) clean.recycle()

        val luma = IntArray(w * h)
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = y * w
            val intRow = (y + 1) * (w + 1)
            val prevIntRow = y * (w + 1)
            for (x in 0 until w) {
                val p = pixels[rowOff + x]
                val yVal = ((p shr 16 and 0xff) * 30 + (p shr 8 and 0xff) * 59 + (p and 0xff) * 11) / 100
                luma[rowOff + x] = yVal
                rowSum += yVal
                integral[intRow + x + 1] = integral[prevIntRow + x + 1] + rowSum
            }
        }

        val halfWin = max(8, minOf(w, h) / 32)
        val outPix = IntArray(w * h)
        for (y in 0 until h) {
            val y1 = (y - halfWin).coerceAtLeast(0)
            val y2 = (y + halfWin).coerceAtMost(h - 1)
            val rowOff = y * w
            for (x in 0 until w) {
                val x1 = (x - halfWin).coerceAtLeast(0)
                val x2 = (x + halfWin).coerceAtMost(w - 1)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                    integral[y1 * (w + 1) + (x2 + 1)] -
                    integral[(y2 + 1) * (w + 1) + x1] +
                    integral[y1 * (w + 1) + x1]
                val localMean = (sum / count).toInt()
                val v = if (luma[rowOff + x] < (localMean * 0.88f).toInt()) 0 else 255
                outPix[rowOff + x] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPix, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlurLuma(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val luma = IntArray(w * h) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xff) * 30 + (p shr 8 and 0xff) * 59 + (p and 0xff) * 11) / 100
        }
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0
                var cnt = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    sum += luma[row + nx]
                    cnt++
                }
                tmp[row + x] = sum / cnt
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var cnt = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    sum += tmp[ny * w + x]
                    cnt++
                }
                out[y * w + x] = sum / cnt
            }
        }
        return out
    }

    companion object {
        fun computeQuadTargetSize(quad: Quad): Pair<Int, Int> {
            val topW = hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y)
            val botW = hypot(quad.bottomRight.x - quad.bottomLeft.x, quad.bottomRight.y - quad.bottomLeft.y)
            val leftH = hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y)
            val rightH = hypot(quad.bottomRight.x - quad.topRight.x, quad.bottomRight.y - quad.topRight.y)
            val w = max(topW, botW).roundToInt().coerceAtLeast(32)
            val h = max(leftH, rightH).roundToInt().coerceAtLeast(32)
            return w to h
        }
    }
}
