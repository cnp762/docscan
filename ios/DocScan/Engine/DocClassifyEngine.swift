import Foundation

enum DocClassifyEngine {
    static func classify(ocr: OcrPage) -> DocTypeResult {
        let tagged = HeuristicTagEngine.tag(ocr: ocr)
        let ordered: [String]
        if tagged.scores.isEmpty {
            ordered = tagged.tags
        } else {
            ordered = tagged.scores.sorted { $0.value > $1.value }.map(\.key)
        }
        let primary = ordered.first ?? "document"
        let alts = Array(ordered.dropFirst().prefix(3))
        let primaryScore = tagged.scores[primary] ?? (primary == "document" ? 0.35 : 1)
        let maxScore = max(tagged.scores.values.max() ?? 1, 1)
        let confidence: Float
        if primary == "document" && tagged.scores.isEmpty {
            confidence = 0.35
        } else {
            confidence = min(0.95, max(0.35, 0.45 + 0.5 * (primaryScore / maxScore)))
        }
        return DocTypeResult(
            docType: primary,
            confidence: confidence,
            alternatives: alts,
            tags: tagged.tags,
            scores: tagged.scores,
            isTable: tagged.isTable,
            isHandwritten: tagged.isHandwritten
        )
    }
}
