import Foundation

enum SummaryEngine {
    static func summarize(
        ocrText: String,
        mode: SummaryMode = .extractive,
        maxChars: Int = DeviceCapability.pageSummaryMaxChars,
        inputCap: Int = DeviceCapability.batchSummaryMaxChars
    ) -> SummaryResult {
        let cleaned = String(ocrText.trimmingCharacters(in: .whitespacesAndNewlines).prefix(inputCap))
        if cleaned.isEmpty {
            return SummaryResult(text: "", source: SummaryResult.sourceExtractive)
        }
        return SummaryResult(text: extractive(cleaned, maxChars: maxChars), source: SummaryResult.sourceExtractive)
    }

    private static let keywordRegex = try? NSRegularExpression(
        pattern: "invoice|total|subtotal|amount|due|tax|vat|agreement|date|receipt|contract|passport|id|account|hóa đơn|tổng|thành tiền|thuế|ngày|hợp đồng|biên lai|số|mã|căn cước|rechnung|betrag|datum|facture|montant|fecha|importe|合計|請求|領収|합계|영수증|发票|总额",
        options: [.caseInsensitive]
    )

    private static let entityRegex = try? NSRegularExpression(
        pattern: "(\\$|€|£|¥|₫|USD|VND|EUR)\\s?\\d|\\d{1,4}[/-]\\d{1,2}[/-]\\d{2,4}|#[A-Z0-9-]{3,}",
        options: [.caseInsensitive]
    )

    private static func extractive(_ text: String, maxChars: Int) -> String {
        var seen = Set<String>()
        let parts = text
            .components(separatedBy: CharacterSet(charactersIn: ".\n!?"))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.count >= 8 && seen.insert($0).inserted }
        if parts.isEmpty {
            return String(text.prefix(min(maxChars, 280)))
        }
        let scored = parts.enumerated().map { idx, line -> (String, Int) in
            var score = min(line.count, 120)
            if idx == 0 { score += 25 }
            let range = NSRange(line.startIndex..., in: line)
            if keywordRegex?.firstMatch(in: line, range: range) != nil { score += 45 }
            if entityRegex?.firstMatch(in: line, range: range) != nil { score += 35 }
            return (line, score)
        }.sorted { $0.1 > $1.1 }
        let joined = scored.prefix(4).map(\.0).joined(separator: " · ")
        return String(joined.prefix(maxChars))
    }
}
