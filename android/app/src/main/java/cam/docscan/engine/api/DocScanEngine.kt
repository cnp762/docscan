package cam.docscan.engine.api

import android.graphics.Bitmap
import java.io.File

interface DocScanEngine {
    suspend fun initialize(modelsDir: File? = null)

    suspend fun detectDocument(image: Bitmap): Quad?

    suspend fun recognize(
        image: Bitmap,
        quad: Quad? = null,
        langHint: String? = null,
    ): OcrPage

    suspend fun detectLanguage(ocrText: String, hint: String? = null): LangResult

    suspend fun classifyDocument(ocr: OcrPage, image: Bitmap? = null): DocTypeResult

    suspend fun tag(ocr: OcrPage, image: Bitmap? = null): TagResult

    suspend fun summarize(ocrText: String, mode: SummaryMode = SummaryMode.AUTO): SummaryResult

    suspend fun enhance(image: Bitmap, preset: EnhancePreset): Bitmap

    fun writeImageMetadata(imagePath: String, meta: PageNlpMetadata): Boolean

    fun close()
}
