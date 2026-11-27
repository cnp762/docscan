package cam.docscan.engine.summary

import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.api.SummaryResult
import cam.docscan.engine.runtime.DeviceCapability
import java.io.File

/**
 * Fast on-device extractive summary prioritizing searchable facts
 * (titles, dates, amounts, IDs, and multilingual document keywords).
 */
class SummaryEngine(
    @Suppress("UNUSED_PARAMETER") modelsDir: File? = null,
    @Suppress("UNUSED_PARAMETER") capability: DeviceCapability = DeviceCapability(),
) {
    suspend fun summarize(
        ocrText: String,
        @Suppress("UNUSED_PARAMETER") mode: SummaryMode = SummaryMode.EXTRACTIVE,
        maxChars: Int = DeviceCapability.PAGE_SUMMARY_MAX_CHARS,
        inputCap: Int = DeviceCapability.BATCH_SUMMARY_MAX_CHARS,
    ): SummaryResult {
        val cleaned = ocrText.trim().take(inputCap)
        if (cleaned.isEmpty()) {
            return SummaryResult("", SummaryResult.SOURCE_EXTRACTIVE)
        }
        return SummaryResult(extractive(cleaned, maxChars), SummaryResult.SOURCE_EXTRACTIVE)
    }

    /** Legacy String API for callers not yet on SummaryResult. */
    suspend fun summarizeText(ocrText: String): String = summarize(ocrText).text

    private fun extractive(text: String, maxChars: Int): String {
        val lines = text
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 8 }
            .distinct()
        if (lines.isEmpty()) {
            return text.take(maxChars.coerceAtMost(280)).trim()
        }
        val scored = lines.mapIndexed { idx, s ->
            var score = s.length.coerceAtMost(120)
            if (idx == 0) score += 25 // Header / title line
            if (KEYWORD.containsMatchIn(s)) score += 45
            if (ENTITY_SIGNAL.containsMatchIn(s)) score += 35
            s to score
        }.sortedByDescending { it.second }
        return scored.take(4).joinToString(" · ") { it.first }.take(maxChars)
    }

    companion object {
        private val KEYWORD = Regex(
            "invoice|total|subtotal|amount|due|tax|vat|agreement|date|receipt|contract|passport|id|account|" +
                "hóa đơn|tổng|thành tiền|thuế|ngày|hợp đồng|biên lai|số|mã|căn cước|" +
                "rechnung|betrag|datum|facture|montant|total|fecha|importe|合計|請求|領収|합계|영수증|发票|总额",
            RegexOption.IGNORE_CASE,
        )
        private val ENTITY_SIGNAL = Regex(
            "(\\$|€|£|¥|₫|USD|VND|EUR)\\s?\\d|\\d{1,4}[/-]\\d{1,2}[/-]\\d{2,4}|#[A-Z0-9-]{3,}",
            RegexOption.IGNORE_CASE,
        )
    }
}
