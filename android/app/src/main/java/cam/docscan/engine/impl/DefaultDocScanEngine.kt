package cam.docscan.engine.impl

import android.content.Context
import android.graphics.Bitmap
import cam.docscan.engine.api.DocScanEngine
import cam.docscan.engine.api.DocTypeResult
import cam.docscan.engine.api.EnhancePreset
import cam.docscan.engine.api.LangResult
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.PageNlpMetadata
import cam.docscan.engine.api.Quad
import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.api.SummaryResult
import cam.docscan.engine.api.TagResult
import cam.docscan.engine.classify.DocClassifyEngine
import cam.docscan.engine.detect.HeuristicDetectEngine
import cam.docscan.engine.enhance.BitmapEnhanceEngine
import cam.docscan.engine.lang.LangDetectEngine
import cam.docscan.engine.metadata.ImageMetadataWriter
import cam.docscan.engine.ocr.MlKitOcrEngine
import cam.docscan.engine.runtime.DeviceCapability
import cam.docscan.engine.summary.SummaryEngine
import java.io.File

/**
 * Engine primitives. Scheduling / timeouts live on [cam.docscan.engine.runtime.WorkQueue].
 */
class DefaultDocScanEngine(
    context: Context? = null,
) : DocScanEngine {

    private val capability = DeviceCapability(context?.applicationContext)
    private val detect = HeuristicDetectEngine()
    private val enhanceEngine = BitmapEnhanceEngine()
    private val ocr = MlKitOcrEngine()
    private val langDetect = LangDetectEngine()
    private val classifyEngine = DocClassifyEngine()
    private val metadataWriter = ImageMetadataWriter()
    private var summary = SummaryEngine(capability = capability)
    private var ready = false

    fun capability(): DeviceCapability = capability

    override suspend fun initialize(modelsDir: File?) {
        summary = SummaryEngine(modelsDir, capability)
        ready = true
    }

    override suspend fun detectDocument(image: Bitmap): Quad? = detect.detect(image)

    override suspend fun recognize(image: Bitmap, quad: Quad?, langHint: String?): OcrPage {
        // Lang finalized by LangPage step on WorkQueue.
        return ocr.recognize(image, quad, langHint)
    }

    override suspend fun detectLanguage(ocrText: String, hint: String?): LangResult =
        langDetect.detect(ocrText, hint)

    override suspend fun classifyDocument(ocrPage: OcrPage, image: Bitmap?): DocTypeResult =
        classifyEngine.classify(ocrPage)

    override suspend fun tag(ocrPage: OcrPage, image: Bitmap?): TagResult =
        classifyDocument(ocrPage, image).toTagResult()

    override suspend fun summarize(ocrText: String, mode: SummaryMode): SummaryResult {
        val effective = if (mode == SummaryMode.AUTO && !capability.allowGenerative()) {
            SummaryMode.EXTRACTIVE
        } else {
            mode
        }
        return summary.summarize(ocrText, effective)
    }

    override suspend fun enhance(image: Bitmap, preset: EnhancePreset): Bitmap =
        enhanceEngine.enhance(image, preset)

    override fun writeImageMetadata(imagePath: String, meta: PageNlpMetadata): Boolean =
        metadataWriter.write(imagePath, meta) is ImageMetadataWriter.Result.Ok

    fun autoname(ocrPage: OcrPage, tags: List<String>): String =
        classifyEngine.autoname(ocrPage, tags)

    override fun close() {
        ocr.close()
        ready = false
    }

    fun isReady(): Boolean = ready
}
