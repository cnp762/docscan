package cam.docscan.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors

/**
 * CameraX controller binding Preview, ImageAnalysis (for live quad/blur guidance), and ImageCapture.
 */
class CameraXCaptureController(private val context: Context) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrameAnalyzed: (lumaAvg: Double) -> Unit = {},
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
                .build()
            imageCapture = capture

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor) { proxy ->
                        val buffer = proxy.planes.firstOrNull()?.buffer
                        var sum = 0L
                        var count = 0
                        if (buffer != null) {
                            val step = 16
                            var idx = 0
                            while (idx < buffer.remaining()) {
                                sum += (buffer.get(idx).toInt() and 0xFF)
                                count++
                                idx += step
                            }
                        }
                        val avg = if (count > 0) sum.toDouble() / count else 128.0
                        onFrameAnalyzed(avg)
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
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }
}
