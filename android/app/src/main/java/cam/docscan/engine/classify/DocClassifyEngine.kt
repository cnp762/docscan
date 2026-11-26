package cam.docscan.engine.classify

import cam.docscan.engine.api.DocTypeResult
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.tag.HeuristicTagEngine

/**
 * Heuristic document taxonomy v1 — promotes tag engine to primary doc_type + alts.
 */
class DocClassifyEngine(
    private val tagEngine: HeuristicTagEngine = HeuristicTagEngine(),
) {

    fun classify(ocr: OcrPage): DocTypeResult {
        val tagged = tagEngine.tag(ocr)
        val scored = tagged.scores
        val ordered = if (scored.isNotEmpty()) {
            scored.entries.sortedByDescending { it.value }.map { it.key }
        } else {
            tagged.tags
        }
        val primary = ordered.firstOrNull() ?: "document"
        val alts = ordered.drop(1).take(3)
        val primaryScore = scored[primary] ?: if (primary == "document") 0.35f else 1f
        val maxScore = scored.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val confidence = when {
            primary == "document" && scored.isEmpty() -> 0.35f
            else -> (0.45f + 0.5f * (primaryScore / maxScore)).coerceIn(0.35f, 0.95f)
        }
        return DocTypeResult(
            docType = primary,
            confidence = confidence,
            alternatives = alts,
            tags = tagged.tags,
            scores = scored,
            isTable = tagged.isTable,
            isHandwritten = tagged.isHandwritten,
        )
    }

    fun autoname(ocr: OcrPage, tags: List<String>): String = tagEngine.autoname(ocr, tags)
}
