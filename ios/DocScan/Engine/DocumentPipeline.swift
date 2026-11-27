import Foundation
import UIKit

/// Save-path orchestration on iOS via shared `WorkQueue` stepped jobs.
/// Mirrors Android `DocumentPipeline.kt` + `WorkJobHandler.kt`:
/// FreshScan (100) for new capture/import, UserSave (80) for re-edit.
actor DocumentPipeline {
    struct PageState {
        var pageId: String
        var batchId: String?
        var name: String
        var source: String
        var imagePath: String
        var workingImage: UIImage
        var quad: DocQuad?
        var preset: EnhancePreset
        var ocr: OcrPage?
        var lang: LangResult?
        var docType: DocTypeResult?
        var tags: [String]
        var summary: SummaryResult?
        var sortIndex: Int
    }

    private let engine: DefaultDocScanEngine
    private var states: [String: PageState] = [:]
    private var queue: WorkQueue?
    private var onPageUpdated: (@MainActor @Sendable (String, PageState) -> Void)?

    init(engine: DefaultDocScanEngine) {
        self.engine = engine
    }

    func setOnPageUpdated(_ callback: @escaping @MainActor @Sendable (String, PageState) -> Void) {
        self.onPageUpdated = callback
    }

    func ensureInitialized() async {
        if !engine.isReady {
            try? await engine.initialize()
        }
        if queue == nil {
            queue = WorkQueue { [weak self] job in
                await self?.handle(job: job)
            }
        }
    }

    @discardableResult
    func enqueuePage(
        pageId: String,
        batchId: String? = nil,
        name: String,
        source: String,
        imagePath: String,
        image: UIImage,
        quad: DocQuad? = nil,
        preset: EnhancePreset = .document,
        sortIndex: Int = 0,
        freshScan: Bool = true,
        needDetect: Bool = false,
        needEnhance: Bool = false
    ) async -> [String] {
        await ensureInitialized()
        let priority: WorkPriority = freshScan ? .freshScan : .userSave
        let groupId = UUID().uuidString
        states[pageId] = PageState(
            pageId: pageId,
            batchId: batchId,
            name: name,
            source: source,
            imagePath: imagePath,
            workingImage: image,
            quad: quad,
            preset: preset,
            ocr: nil,
            lang: nil,
            docType: nil,
            tags: [],
            summary: nil,
            sortIndex: sortIndex
        )

        var jobs: [WorkJob] = []
        if needEnhance {
            jobs.append(.enhancePage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.enhanceDetectMs))
        }
        if needDetect && quad == nil {
            jobs.append(.detectPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.enhanceDetectMs))
        }
        jobs.append(.ocrPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.ocrMs))
        jobs.append(.langPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.langMs))
        jobs.append(.classifyPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.classifyMs))
        jobs.append(.summarizePage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.summaryExtractiveMs))
        jobs.append(.writeMetadata(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.metadataMs))

        guard let q = queue else { return [] }
        return await q.enqueueChain(jobs)
    }

    func cancelPage(_ pageId: String) async {
        states.removeValue(forKey: pageId)
        await queue?.cancelPage(pageId)
    }

    private func handle(job: WorkJob) async {
        switch job {
        case .enhancePage(_, _, let pageId, _, _):
            guard var st = states[pageId] else { return }
            st.workingImage = engine.enhance(image: st.workingImage, preset: st.preset)
            states[pageId] = st

        case .detectPage(_, _, let pageId, _, _):
            guard var st = states[pageId], st.quad == nil else { return }
            st.quad = await engine.detectDocument(image: st.workingImage)
            states[pageId] = st

        case .ocrPage(_, _, let pageId, _, _):
            guard var st = states[pageId] else { return }
            if let page = try? await engine.recognize(image: st.workingImage, quad: st.quad, langHint: nil) {
                st.ocr = page
                if let title = page.title, !title.isEmpty, st.name.hasPrefix("Scan") || st.name.hasPrefix("Imported") {
                    st.name = String(title.prefix(48))
                }
                states[pageId] = st
                await notify(pageId: pageId, state: st)
            }

        case .langPage(_, _, let pageId, _, _):
            guard var st = states[pageId] else { return }
            let text = st.ocr?.text ?? ""
            guard !text.isEmpty else { return }
            let res = await engine.detectLanguage(ocrText: text, hint: st.ocr?.lang)
            st.lang = res
            st.ocr?.lang = res.lang
            st.ocr?.langConfidence = res.confidence
            states[pageId] = st
            await notify(pageId: pageId, state: st)

        case .classifyPage(_, _, let pageId, _, _):
            guard var st = states[pageId], let ocr = st.ocr else { return }
            let res = await engine.classifyDocument(ocr: ocr)
            st.docType = res
            st.tags = res.toTagResult().tags
            states[pageId] = st
            await notify(pageId: pageId, state: st)

        case .summarizePage(_, _, let pageId, _, _):
            guard var st = states[pageId] else { return }
            let text = st.ocr?.text ?? ""
            let mode: SummaryMode = DeviceCapability.allowGenerative() ? .auto : .extractive
            let res = await engine.summarize(ocrText: text, mode: mode)
            st.summary = res
            states[pageId] = st
            await notify(pageId: pageId, state: st)

        case .writeMetadata(_, _, let pageId, _, _):
            guard let st = states[pageId] else { return }
            if !st.imagePath.isEmpty {
                let meta = PageNlpMetadata(
                    pageId: pageId,
                    lang: st.lang?.lang ?? st.ocr?.lang,
                    langConfidence: st.lang?.confidence ?? st.ocr?.langConfidence,
                    docType: st.docType?.docType,
                    tags: st.tags,
                    summary: st.summary?.text,
                    summarySource: st.summary?.source
                )
                _ = engine.writeImageMetadata(imagePath: st.imagePath, meta: meta)
            }
            states.removeValue(forKey: pageId)

        case .summarizeBatch, .suggestScanChunk, .suggestScoreItem:
            break
        }
    }

    private func notify(pageId: String, state: PageState) async {
        guard let cb = onPageUpdated else { return }
        await MainActor.run {
            cb(pageId, state)
        }
    }
}
