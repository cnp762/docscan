package cam.docscan.capture

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import cam.docscan.engine.api.Quad
import cam.docscan.engine.detect.HeuristicDetectEngine
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

data class CaptureFrameGuidance(
    val normalizedCorners: List<Offset>? = null,
    val lumaAvg: Double = 128.0,
    val isLowLight: Boolean = false,
    val isBlurry: Boolean = false,
    val isStable: Boolean = false,
    val message: String = "Align document in frame",
)

/**
 * CameraX controller binding Preview, ImageAnalysis (live quad/blur/light guidance), and ImageCapture.
 */
class CameraXCaptureController(
    private val context: Context,
    private val detectEngine: HeuristicDetectEngine = HeuristicDetectEngine(),
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastAnalyzedMs = 0L
    private var prevCorners: List<Offset>? = null
    private var stableStreak = 0

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        flashEnabled: Boolean = false,
        onGuidance: (CaptureFrameGuidance) -> Unit = {},
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()
            imageCapture = capture

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor) { proxy ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAnalyzedMs >= ANALYSIS_INTERVAL_MS) {
                            lastAnalyzedMs = now
                            val guidance = analyzeProxy(proxy)
                            ContextCompat.getMainExecutor(context).execute {
                                onGuidance(guidance)
                            }
                        }
                        proxy.close()
                    }
                }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setFlashEnabled(enabled: Boolean) {
        imageCapture?.flashMode = if (enabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    fun takePhoto(outputFile: File, onResult: (Result<File>) -> Unit) {
        val capture = imageCapture ?: run {
            onResult(Result.failure(IllegalStateException("Camera not bound")))
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onResult(Result.success(outputFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            },
        )
    }

    fun release() {
        runCatching { cameraProvider?.unbindAll() }
        analysisExecutor.shutdown()
    }

    private fun analyzeProxy(proxy: ImageProxy): CaptureFrameGuidance {
        val plane = proxy.planes.firstOrNull() ?: return CaptureFrameGuidance()
        val buffer = plane.buffer
        val w = proxy.width
        val h = proxy.height
        if (w < 32 || h < 32) return CaptureFrameGuidance()

        val rowStride = plane.rowStride
        val targetW = 160
        val targetH = max(32, (h * targetW) / w)
        val pixels = IntArray(targetW * targetH)

        var lumaSum = 0L
        var edgeEnergy = 0L
        var samples = 0

        for (y in 0 until targetH) {
            val srcY = (y * h) / targetH
            val rowOffset = srcY * rowStride
            var prevLuma = -1
            for (x in 0 until targetW) {
                val srcX = (x * w) / targetW
                val idx = rowOffset + srcX
                val luma = if (idx < buffer.limit()) (buffer.get(idx).toInt() and 0xFF) else 128
                pixels[y * targetW + x] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
                lumaSum += luma
                if (prevLuma >= 0) {
                    edgeEnergy += abs(luma - prevLuma)
                }
                prevLuma = luma
                samples++
            }
        }

        val lumaAvg = if (samples > 0) lumaSum.toDouble() / samples else 128.0
        val meanEdge = if (samples > 0) edgeEnergy.toDouble() / samples else 0.0
        val isLowLight = lumaAvg < LOW_LIGHT_THRESHOLD
        val isBlurry = meanEdge < BLUR_EDGE_THRESHOLD

        val bmp = Bitmap.createBitmap(pixels, targetW, targetH, Bitmap.Config.ARGB_8888)
        val quad = detectEngine.detect(bmp)
        bmp.recycle()

        val normalized = quad?.toNormalizedOffsets(targetW.toFloat(), targetH.toFloat())
        val isStable = updateStability(normalized)

        val message = when {
            isLowLight -> "Low light · Try enabling flash"
            isBlurry -> "Hold steady · Image slightly blurry"
            normalized == null -> "Align document inside frame"
            isStable -> "Hold steady · All corners locked"
            else -> "Corners detected · Hold steady"
        }

        return CaptureFrameGuidance(
            normalizedCorners = normalized,
            lumaAvg = lumaAvg,
            isLowLight = isLowLight,
            isBlurry = isBlurry,
            isStable = isStable,
            message = message,
        )
    }

    private fun updateStability(current: List<Offset>?): Boolean {
        if (current == null || current.size != 4) {
            stableStreak = 0
            prevCorners = null
            return false
        }
        val prev = prevCorners
        prevCorners = current
        if (prev == null || prev.size != 4) {
            stableStreak = 0
            return false
        }
        val maxDelta = (0..3).maxOf { i ->
            max(abs(current[i].x - prev[i].x), abs(current[i].y - prev[i].y))
        }
        if (maxDelta <= STABILITY_DELTA) {
            stableStreak++
        } else {
            stableStreak = 0
        }
        return stableStreak >= REQUIRED_STABLE_FRAMES
    }

    private fun Quad.toNormalizedOffsets(w: Float, h: Float): List<Offset> = listOf(
        Offset((topLeft.x / w).coerceIn(0.02f, 0.98f), (topLeft.y / h).coerceIn(0.02f, 0.98f)),
        Offset((topRight.x / w).coerceIn(0.02f, 0.98f), (topRight.y / h).coerceIn(0.02f, 0.98f)),
        Offset((bottomRight.x / w).coerceIn(0.02f, 0.98f), (bottomRight.y / h).coerceIn(0.02f, 0.98f)),
        Offset((bottomLeft.x / w).coerceIn(0.02f, 0.98f), (bottomLeft.y / h).coerceIn(0.02f, 0.98f)),
    )

    companion object {
        private const val ANALYSIS_INTERVAL_MS = 280L
        private const val LOW_LIGHT_THRESHOLD = 48.0
        private const val BLUR_EDGE_THRESHOLD = 4.2
        private const val STABILITY_DELTA = 0.035f
        private const val REQUIRED_STABLE_FRAMES = 2
    }
}
