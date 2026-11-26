package cam.docscan.engine.tag

import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.TagResult

/**
 * Tag rewrite (CS tagengine .data encrypted). Taxonomy inspired by CS tag IDs,
 * English labels only.
 */
class HeuristicTagEngine {

    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("invoice|bill to|amount due|tax invoice", RegexOption.IGNORE_CASE) to "invoice",
        Regex("receipt|cash|change due|merchant", RegexOption.IGNORE_CASE) to "receipt",
        Regex("contract|agreement|hereinafter|party of", RegexOption.IGNORE_CASE) to "contract",
        Regex("passport|nationality|date of birth|P<", RegexOption.IGNORE_CASE) to "passport",
        Regex("driver.?license|driving licence|DL No", RegexOption.IGNORE_CASE) to "id_card",
        Regex("identity|ID card|CMND|CCCD|national id", RegexOption.IGNORE_CASE) to "id_card",
        Regex("whiteboard|agenda|brainstorm", RegexOption.IGNORE_CASE) to "whiteboard",
        Regex("chapter|isbn|contents", RegexOption.IGNORE_CASE) to "book",
        Regex("bank|account number|iban|swift", RegexOption.IGNORE_CASE) to "bank",
        Regex("formula|∫|∑|√", RegexOption.IGNORE_CASE) to "formula",
    )

    fun tag(ocr: OcrPage): TagResult {
        val text = buildString {
            append(ocr.title.orEmpty())
            append('\n')
            append(ocr.text)
        }
        val hits = linkedMapOf<String, Float>()
        for ((regex, label) in rules) {
            if (regex.containsMatchIn(text)) {
                hits[label] = (hits[label] ?: 0f) + 1f
            }
        }
        val isTable = Regex("\\|").findAll(text).count() >= 3 ||
            text.lines().count { it.contains('\t') } >= 3
        if (isTable) hits["table"] = 1f

        val tags = if (hits.isEmpty()) listOf("document") else hits.keys.toList()
        return TagResult(
            tags = tags,
            scores = hits,
            isTable = isTable,
            isHandwritten = false,
        )
    }

    fun autoname(ocr: OcrPage, tags: List<String>): String {
        val base = ocr.title?.take(48)?.replace(Regex("\\s+"), " ")?.trim()
            ?: ocr.lines.firstOrNull()?.text?.take(48)?.trim()
            ?: tags.firstOrNull()?.replace('_', ' ')
            ?: "Scan"
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "").ifBlank { "Scan" }
        return safe
    }
}
