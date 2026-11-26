import Foundation
import NaturalLanguage

enum LangDetectEngine {
    private static let sampleChars = 800
    private static let nlMinConfidence: Double = 0.45
    private static let viDiacritics = CharacterSet(charactersIn:
        "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩị" +
        "òóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
        "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊ" +
        "ÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"
    )

    static func detect(ocrText: String, hint: String? = nil) -> LangResult {
        if let hint, !hint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return LangResult(lang: hint.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), confidence: 0.9)
        }
        let sample = String(ocrText.trimmingCharacters(in: .whitespacesAndNewlines).prefix(sampleChars))
        if sample.isEmpty { return LangResult(lang: "und", confidence: 0) }

        if let nlResult = detectWithNaturalLanguage(sample) {
            return nlResult
        }
        return detectWithScriptHeuristic(sample)
    }

    private static func detectWithNaturalLanguage(_ sample: String) -> LangResult? {
        let recognizer = NLLanguageRecognizer()
        recognizer.processString(sample)
        guard let dominant = recognizer.dominantLanguage, dominant != .undetermined else {
            return nil
        }
        let hypotheses = recognizer.languageHypotheses(withMaximum: 1)
        let prob = hypotheses[dominant] ?? 0
        guard prob >= nlMinConfidence else { return nil }
        let code = normalizeBcp47(dominant.rawValue)
        return LangResult(lang: code, confidence: Float(min(0.99, max(0.45, prob))))
    }

    private static func normalizeBcp47(_ raw: String) -> String {
        switch raw {
        case "zh-Hans", "zh-Hant": return "zh"
        default: return raw
        }
    }

    private static func detectWithScriptHeuristic(_ sample: String) -> LangResult {
        var latin = 0, cyrillic = 0, han = 0, kana = 0, hangul = 0
        var thai = 0, arabic = 0, devanagari = 0
        for scalar in sample.unicodeScalars {
            switch scalar.value {
            case 0x0400...0x04FF: cyrillic += 1
            case 0x4E00...0x9FFF: han += 1
            case 0x3040...0x30FF: kana += 1
            case 0xAC00...0xD7AF: hangul += 1
            case 0x0E00...0x0E7F: thai += 1
            case 0x0600...0x06FF: arabic += 1
            case 0x0900...0x097F: devanagari += 1
            case 0x0041...0x005A, 0x0061...0x007A, 0x00C0...0x024F: latin += 1
            default: break
            }
        }
        let letters = max(latin + cyrillic + han + kana + hangul + thai + arabic + devanagari, 1)
        if hangul > letters / 6 { return LangResult(lang: "ko", confidence: conf(hangul, letters)) }
        if kana > letters / 10 { return LangResult(lang: "ja", confidence: conf(kana + han, letters)) }
        if han > letters / 5 { return LangResult(lang: "zh", confidence: conf(han, letters)) }
        if cyrillic > letters / 5 { return LangResult(lang: "ru", confidence: conf(cyrillic, letters)) }
        if thai > letters / 6 { return LangResult(lang: "th", confidence: conf(thai, letters)) }
        if arabic > letters / 6 { return LangResult(lang: "ar", confidence: conf(arabic, letters)) }
        if devanagari > letters / 6 { return LangResult(lang: "hi", confidence: conf(devanagari, letters)) }

        let viMarks = sample.unicodeScalars.filter { viDiacritics.contains($0) }.count
        if viMarks >= 2 {
            return LangResult(lang: "vi", confidence: min(0.92, 0.55 + Float(min(viMarks, 20)) / 40))
        }
        if latin == 0 { return LangResult(lang: "und", confidence: 0.2) }
        let ratio = Float(latin) / Float(letters)
        return LangResult(lang: "en", confidence: min(0.85, 0.5 + ratio * 0.35))
    }

    private static func conf(_ hit: Int, _ letters: Int) -> Float {
        min(0.95, max(0.4, 0.55 + Float(hit) / Float(letters) * 0.4))
    }
}
