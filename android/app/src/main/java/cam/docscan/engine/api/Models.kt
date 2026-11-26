package cam.docscan.engine.api

data class PointF(val x: Float, val y: Float)

/** Document corners in image pixel space: TL, TR, BR, BL. */
data class Quad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF,
)

data class OcrLine(
    val text: String,
    val confidence: Float? = null,
    val box: List<PointF>? = null,
)

data class OcrPage(
    val text: String,
    val lang: String? = null,
    val langConfidence: Float? = null,
    val lines: List<OcrLine> = emptyList(),
    val title: String? = null,
)

data class TagResult(
    val tags: List<String>,
    val scores: Map<String, Float> = emptyMap(),
    val isTable: Boolean = false,
    val isHandwritten: Boolean = false,
)

data class LangResult(
    val lang: String,
    val confidence: Float,
)

data class DocTypeResult(
    val docType: String,
    val confidence: Float,
    val alternatives: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val scores: Map<String, Float> = emptyMap(),
    val isTable: Boolean = false,
    val isHandwritten: Boolean = false,
) {
    fun toTagResult(): TagResult = TagResult(
        tags = tags.ifEmpty { listOf(docType) },
        scores = scores,
        isTable = isTable,
        isHandwritten = isHandwritten,
    )
}

enum class SummaryMode {
    EXTRACTIVE,
    AUTO,
}

data class SummaryResult(
    val text: String,
    val source: String,
) {
    companion object {
        const val SOURCE_EXTRACTIVE = "extractive"
        const val SOURCE_GENERATIVE = "generative"
    }
}

data class PageNlpMetadata(
    val pageId: String,
    val lang: String? = null,
    val langConfidence: Float? = null,
    val docType: String? = null,
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val summarySource: String? = null,
    val nlpVersion: String = NLP_VERSION,
) {
    companion object {
        const val NLP_VERSION = "1.0.0"
    }
}

enum class EnhancePreset {
    ORIGINAL,
    COLOR,
    GRAY,
    BW,
    DOCUMENT,
    DESHADOW,
}
