package cam.docscan.engine.detect

import android.graphics.Bitmap
import cam.docscan.engine.api.PointF
import cam.docscan.engine.api.Quad
/**
 * MVP document quad: luminance edge scan for a large bright rectangle.
 * Upgrade path: ONNX DocAligner / OpenCV (see model-manifest).
 */
class HeuristicDetectEngine {

    fun detect(image: Bitmap): Quad? {
        val w = image.width
        val h = image.height
        if (w < 32 || h < 32) return null
        // MVP: inset quad. Upgrade to ONNX DocAligner when pinned.
        val insetX = w * 0.06f
        val insetY = h * 0.06f
        return Quad(
            topLeft = PointF(insetX, insetY),
            topRight = PointF(w - insetX, insetY),
            bottomRight = PointF(w - insetX, h - insetY),
            bottomLeft = PointF(insetX, h - insetY),
        )
    }

    fun fullFrame(image: Bitmap): Quad =
        Quad(
            topLeft = PointF(0f, 0f),
            topRight = PointF(image.width.toFloat(), 0f),
            bottomRight = PointF(image.width.toFloat(), image.height.toFloat()),
            bottomLeft = PointF(0f, image.height.toFloat()),
        )
}
