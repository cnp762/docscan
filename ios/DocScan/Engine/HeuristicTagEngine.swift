import Foundation

enum HeuristicTagEngine {
    private static let rules: [(NSRegularExpression, String)] = {
        let patterns: [(String, String)] = [
            ("invoice|bill to|amount due|tax invoice", "invoice"),
            ("receipt|cash|change due|merchant", "receipt"),
            ("contract|agreement|hereinafter|party of", "contract"),
            ("passport|nationality|date of birth", "passport"),
            ("driver.?license|driving licence|identity|id card|cmnd|cccd", "id_card"),
            ("whiteboard|agenda|brainstorm", "whiteboard"),
            ("chapter|isbn|contents", "book"),
            ("bank|account number|iban|swift", "bank"),
            ("formula", "formula"),
        ]
        return patterns.compactMap { pair in
            guard let re = try? NSRegularExpression(pattern: pair.0, options: [.caseInsensitive]) else {
                return nil
            }
            return (re, pair.1)
        }
    }()

    static func tag(ocr: OcrPage) -> TagResult {
        let text = ((ocr.title ?? "") + "\n" + ocr.text) as NSString
        var scores: [String: Float] = [:]
        let range = NSRange(location: 0, length: text.length)
        for (re, label) in rules {
            if re.firstMatch(in: text as String, options: [], range: range) != nil {
                scores[label, default: 0] += 1
            }
        }
        let isTable = text.components(separatedBy: "|").count > 3
        if isTable { scores["table"] = 1 }
        var tags = Array(scores.keys)
        if tags.isEmpty { tags = ["document"] }
        return TagResult(tags: tags, scores: scores, isTable: isTable, isHandwritten: false)
    }

    static func autoname(ocr: OcrPage, tags: [String]) -> String {
        let base = ocr.title.map { String($0.prefix(48)) }
            ?? ocr.lines.first.map { String($0.text.prefix(48)) }
            ?? tags.first?.replacingOccurrences(of: "_", with: " ")
            ?? "Scan"
        let cleaned = base.replacingOccurrences(
            of: "[\\\\/:*?\"<>|]",
            with: "",
            options: .regularExpression
        )
        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Scan" : cleaned
    }
}
