import UIKit

final class DefaultDocScanEngine: DocScanEngineProtocol {
    private var ready = false
    private(set) var workQueue: WorkQueue?

    func initialize() async throws {
        ready = true
        if workQueue == nil {
            workQueue = WorkQueue { [weak self] job in
                await self?.handle(job: job)
            }
        }
    }

    func detectDocument(image: UIImage) async -> DocQuad? {
        await VisionOcrEngine.detectQuad(image: image)
    }

    func recognize(image: UIImage, quad: DocQuad?, langHint: String?) async throws -> OcrPage {
        var page = try await VisionOcrEngine.recognize(image: image, langHint: langHint)
        let lang = LangDetectEngine.detect(ocrText: page.text, hint: langHint ?? page.lang)
        page.lang = lang.lang
        page.langConfidence = lang.confidence
        return page
    }

    func detectLanguage(ocrText: String, hint: String?) async -> LangResult {
        LangDetectEngine.detect(ocrText: ocrText, hint: hint)
    }

    func classifyDocument(ocr: OcrPage) async -> DocTypeResult {
        DocClassifyEngine.classify(ocr: ocr)
    }

    func tag(ocr: OcrPage) async -> TagResult {
        await classifyDocument(ocr: ocr).toTagResult()
    }

    func summarize(ocrText: String, mode: SummaryMode) async -> SummaryResult {
        let effective: SummaryMode = (mode == .auto && !DeviceCapability.allowGenerative()) ? .extractive : mode
        return SummaryEngine.summarize(ocrText: ocrText, mode: effective)
    }

    func enhance(image: UIImage, preset: EnhancePreset) -> UIImage {
        ImageEnhance.enhance(image, preset: preset)
    }

    func writeImageMetadata(imagePath: String, meta: PageNlpMetadata) -> Bool {
        ImageMetadataWriter.write(imagePath: imagePath, meta: meta)
    }

    /// Enqueue FreshScan NLP step chain for a new page (OCR→lang→classify→summary→metadata).
    @discardableResult
    func enqueueFreshScanChain(pageId: String) async -> [String] {
        let groupId = UUID().uuidString
        let jobs: [WorkJob] = [
            .ocr(pageId: pageId, groupId: groupId),
            .lang(pageId: pageId, groupId: groupId),
            .classify(pageId: pageId, groupId: groupId),
            .summarize(pageId: pageId, groupId: groupId),
            .metadata(pageId: pageId, groupId: groupId),
        ]
        guard let q = workQueue else { return [] }
        return await q.enqueueChain(jobs)
    }

    var isReady: Bool { ready }

    private func handle(job: WorkJob) async {
        // Full PageWorkStore wiring lands with Capture UI; steps are scheduled & timed here.
        switch job {
        case .suggestScanChunk, .suggestScoreItem:
            guard DeviceCapability.systemOk() else { return }
        default:
            break
        }
    }
}
