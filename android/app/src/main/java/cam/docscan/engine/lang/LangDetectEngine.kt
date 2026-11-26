package cam.docscan.engine.lang

import cam.docscan.engine.api.LangResult

/**
 * Text-first language detection from OCR string (CPU, no vision model).
 * Unicode script histogram + compact keyword / diacritic cues.
 */
class LangDetectEngine {

    fun detect(ocrText: String, hint: String? = null): LangResult {
        if (!hint.isNullOrBlank()) {
            return LangResult(lang = normalize(hint), confidence = 0.9f)
        }
        val sample = ocrText.trim().take(SAMPLE_CHARS)
        if (sample.isEmpty()) return LangResult(lang = "und", confidence = 0.0f)

        val counts = ScriptCounts()
        for (ch in sample) {
            when {
                ch.code in 0x0400..0x04FF -> counts.cyrillic++
                ch.code in 0x4E00..0x9FFF -> counts.han++
                ch.code in 0x3040..0x30FF -> counts.kana++
                ch.code in 0xAC00..0xD7AF -> counts.hangul++
                ch.code in 0x0E00..0x0E7F -> counts.thai++
                ch.code in 0x0600..0x06FF -> counts.arabic++
                ch.code in 0x0900..0x097F -> counts.devanagari++
                isLatinLetter(ch) -> counts.latin++
                ch.isDigit() -> counts.digit++
            }
        }

        val letters = counts.letterTotal().coerceAtLeast(1)
        // Dominant non-Latin scripts
        when {
            counts.hangul > letters * 0.15 ->
                return LangResult("ko", conf(counts.hangul, letters))
            counts.kana > letters * 0.1 ->
                return LangResult("ja", conf(counts.kana + counts.han, letters))
            counts.han > letters * 0.2 ->
                return LangResult("zh", conf(counts.han, letters))
            counts.cyrillic > letters * 0.2 ->
                return LangResult("ru", conf(counts.cyrillic, letters))
            counts.thai > letters * 0.15 ->
                return LangResult("th", conf(counts.thai, letters))
            counts.arabic > letters * 0.15 ->
                return LangResult("ar", conf(counts.arabic, letters))
            counts.devanagari > letters * 0.15 ->
                return LangResult("hi", conf(counts.devanagari, letters))
        }

        if (counts.latin == 0) {
            return LangResult("und", confidence = 0.2f)
        }

        // Latin family: Vietnamese diacritics, then EN default
        val viMarks = sample.count { it in VI_DIACRITICS }
        if (viMarks >= 2 || VI_WORDS.containsMatchIn(sample)) {
            val c = (0.55f + (viMarks.coerceAtMost(20) / 40f)).coerceAtMost(0.92f)
            return LangResult("vi", c)
        }
        if (FR_MARKS_OR_WORDS.containsMatchIn(sample)) {
            return LangResult("fr", 0.65f)
        }
        if (DE_MARKS_OR_WORDS.containsMatchIn(sample)) {
            return LangResult("de", 0.65f)
        }
        if (ES_MARKS_OR_WORDS.containsMatchIn(sample)) {
            return LangResult("es", 0.62f)
        }

        val latinRatio = counts.latin.toFloat() / letters
        return LangResult("en", (0.5f + latinRatio * 0.35f).coerceAtMost(0.85f))
    }

    private fun conf(hit: Int, letters: Int): Float =
        (0.55f + hit.toFloat() / letters * 0.4f).coerceIn(0.4f, 0.95f)

    private fun isLatinLetter(ch: Char): Boolean =
        ch in 'A'..'Z' || ch in 'a'..'z' || ch.code in 0x00C0..0x024F

    private fun normalize(code: String): String = code.trim().lowercase().take(8)

    private data class ScriptCounts(
        var latin: Int = 0,
        var digit: Int = 0,
        var cyrillic: Int = 0,
        var han: Int = 0,
        var kana: Int = 0,
        var hangul: Int = 0,
        var thai: Int = 0,
        var arabic: Int = 0,
        var devanagari: Int = 0,
    ) {
        fun letterTotal(): Int =
            latin + cyrillic + han + kana + hangul + thai + arabic + devanagari
    }

    companion object {
        private const val SAMPLE_CHARS = 800

        private val VI_DIACRITICS = (
            "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩị" +
                "òóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
                "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊ" +
                "ÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"
            ).toSet()

        private val VI_WORDS = Regex(
            "\\b(và|của|không|được|với|này|cho|trong|hóa|đơn)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val FR_MARKS_OR_WORDS = Regex(
            "[àâçéèêëîïôùûüÿœæ]|\\b(le|la|les|des|une|pour)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val DE_MARKS_OR_WORDS = Regex(
            "[äöüß]|\\b(und|der|die|das|für)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val ES_MARKS_OR_WORDS = Regex(
            "[ñ¿¡]|\\b(el|los|las|para|una)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}
