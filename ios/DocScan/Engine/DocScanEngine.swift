import UIKit

struct DocPoint {
    var x: CGFloat
    var y: CGFloat
}

struct DocQuad {
    var topLeft: DocPoint
    var topRight: DocPoint
    var bottomRight: DocPoint
    var bottomLeft: DocPoint
}

struct OcrLine {
    var text: String
    var confidence: Float?
}

struct OcrPage {
    var text: String
    var lang: String?
    var langConfidence: Float?
    var lines: [OcrLine]
    var title: String?
}

struct TagResult {
    var tags: [String]
    var scores: [String: Float]
    var isTable: Bool
    var isHandwritten: Bool
}

struct LangResult {
    var lang: String
    var confidence: Float
}

struct DocTypeResult {
    var docType: String
    var confidence: Float
    var alternatives: [String]
    var tags: [String]
    var scores: [String: Float]
    var isTable: Bool
    var isHandwritten: Bool

    func toTagResult() -> TagResult {
        TagResult(
            tags: tags.isEmpty ? [docType] : tags,
            scores: scores,
            isTable: isTable,
            isHandwritten: isHandwritten
        )
    }
}

enum SummaryMode {
    case extractive
    case auto
}

struct SummaryResult {
    var text: String
    var source: String
    static let sourceExtractive = "extractive"
    static let sourceGenerative = "generative"
}

struct PageNlpMetadata {
    var pageId: String
    var lang: String?
    var langConfidence: Float?
    var docType: String?
    var tags: [String]
    var summary: String?
    var summarySource: String?
    var nlpVersion: String

    static let version = "1.0.0"

    init(
        pageId: String,
        lang: String? = nil,
        langConfidence: Float? = nil,
        docType: String? = nil,
        tags: [String] = [],
        summary: String? = nil,
        summarySource: String? = nil,
        nlpVersion: String = PageNlpMetadata.version
    ) {
        self.pageId = pageId
        self.lang = lang
        self.langConfidence = langConfidence
        self.docType = docType
        self.tags = tags
        self.summary = summary
        self.summarySource = summarySource
        self.nlpVersion = nlpVersion
    }
}

enum EnhancePreset: String {
    case original, color, gray, bw, document, deshadow
}

protocol DocScanEngineProtocol {
    func initialize() async throws
    func detectDocument(image: UIImage) async -> DocQuad?
    func recognize(image: UIImage, quad: DocQuad?, langHint: String?) async throws -> OcrPage
    func detectLanguage(ocrText: String, hint: String?) async -> LangResult
    func classifyDocument(ocr: OcrPage) async -> DocTypeResult
    func tag(ocr: OcrPage) async -> TagResult
    func summarize(ocrText: String, mode: SummaryMode) async -> SummaryResult
    func enhance(image: UIImage, preset: EnhancePreset) -> UIImage
    func writeImageMetadata(imagePath: String, meta: PageNlpMetadata) -> Bool
}
