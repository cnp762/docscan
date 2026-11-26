import Foundation

enum LangDetectEngine {
    private static let sampleChars = 800
    private static let viDiacritics = CharacterSet(charactersIn:
        "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩị" +
        "òóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
        "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊ" +
        "ÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"
    )

    static func detect(ocrText: String, hint: String? = nil) -> LangResult {
        if let hint, !hint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return LangResult(lang: hint.lowercased(), confidence: 0.9)
        }
        let sample = String(ocrText.trimmingCharacters(in: .whitespacesAndNewlines).prefix(sampleChars))
        if sample.isEmpty { return LangResult(lang: "und", confidence: 0) }

        var latin = 0, cyrillic = 0, han = 0, kana = 0, hangul = 0
        for scalar in sample.unicodeScalars {
            let v = scalar.value
            switch v {
            case 0x0400...0x04FF: cyrillic += 1
            case 0x4E00...0x9FFF: han += 1
            case 0x3040...0x30FF: kana += 1
            case 0xAC00...0xD7AF: hangul += 1
            case 0x0041...0x005A, 0x0061...0x007A, 0x00C0...0x024F: latin += 1
            default: break
            }
        }
        let letters = max(latin + cyrillic + han + kana + hangul, 1)
        if hangul > letters / 6 { return LangResult(lang: "ko", confidence: conf(hangul, letters)) }
        if kana > letters / 10 { return LangResult(lang: "ja", confidence: conf(kana + han, letters)) }
        if han > letters / 5 { return LangResult(lang: "zh", confidence: conf(han, letters)) }
        if cyrillic > letters / 5 { return LangResult(lang: "ru", confidence: conf(cyrillic, letters)) }

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
