package cam.docscan.engine.summary

import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.api.SummaryResult
import cam.docscan.engine.runtime.DeviceCapability
import java.io.File

/**
 * Extractive always. Generative only when weights exist and [DeviceCapability] allows.
 */
class SummaryEngine(
    private val modelsDir: File? = null,
    private val capability: DeviceCapability = DeviceCapability(),
) {
    private val generativeAvailable: Boolean
        get() = modelsDir?.resolve("summary")?.takeIf { it.isDirectory }?.list()?.isNotEmpty() == true

    suspend fun summarize(
        ocrText: String,
        mode: SummaryMode = SummaryMode.AUTO,
        maxChars: Int = DeviceCapability.PAGE_SUMMARY_MAX_CHARS,
        inputCap: Int = DeviceCapability.BATCH_SUMMARY_MAX_CHARS,
    ): SummaryResult {
        val cleaned = ocrText.trim().take(inputCap)
        if (cleaned.isEmpty()) {
            return SummaryResult("", SummaryResult.SOURCE_EXTRACTIVE)
        }
        val wantGen = mode == SummaryMode.AUTO && generativeAvailable && capability.allowGenerative()
        if (wantGen) {
            // Placeholder for ORT/ExecuTorch weights; fall through until wired.
            val gen = runGenerativeStub(cleaned, maxChars)
            if (gen != null) return gen
        }
        return SummaryResult(extractive(cleaned, maxChars), SummaryResult.SOURCE_EXTRACTIVE)
    }

    /** Legacy String API for callers not yet on SummaryResult. */
    suspend fun summarizeText(ocrText: String): String = summarize(ocrText).text

    private fun runGenerativeStub(text: String, maxChars: Int): SummaryResult? {
        // Weights directory present but runtime not wired → treat as unavailable.
        if (!generativeAvailable) return null
        return null
    }

    private fun extractive(text: String, maxChars: Int): String {
        val sentences = text
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 20 }
        if (sentences.isEmpty()) {
            return text.take(maxChars.coerceAtMost(280)).trim()
        }
        val scored = sentences.map { s ->
            val score = s.length +
                (if (KEYWORD.containsMatchIn(s)) 40 else 0)
            s to score
        }.sortedByDescending { it.second }
        return scored.take(3).joinToString(" ") { it.first }.take(maxChars)
    }

    companion object {
        private val KEYWORD = Regex(
            "invoice|total|amount|agreement|date|receipt|contract|passport",
            RegexOption.IGNORE_CASE,
        )
    }
}
