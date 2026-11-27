import Foundation
import SwiftUI

enum DocScanRoute: Equatable {
    case onboarding
    case cameraPermission
    case photoPermission
    case homeLibrary
    case capture
    case librarySuggest
    case reviewEdit
    case docDetail
    case searchFilter
    case exportShare
    case backup
    case paywall
    case settings
}

enum MainTab: String, CaseIterable {
    case library = "Library"
    case scan = "Scan"
    case `import` = "Import"
}

enum EnhanceFilterMode: String, CaseIterable {
    case original, color, gray, docClean

    var label: String {
        switch self {
        case .original: return "Original"
        case .color: return "Color"
        case .gray: return "Gray"
        case .docClean: return "Doc Clean"
        }
    }
}

enum ExportFormatOption: String, CaseIterable, Identifiable {
    case pdf, docx, images
    var id: String { rawValue }

    var title: String {
        switch self {
        case .pdf: return "PDF Document (Searchable)"
        case .docx: return "Microsoft Word (DOCX)"
        case .images: return "Images (ZIP / JPEG)"
        }
    }

    var subtitle: String {
        switch self {
        case .pdf: return "Includes invisible OCR text layer for searching and copying"
        case .docx: return "Editable text layout with embedded scanned images"
        case .images: return "High-resolution color-corrected JPEG scans"
        }
    }
}

enum ExportQualityOption: String, CaseIterable, Identifiable {
    case compact = "Compact"
    case standard = "Standard"
    case maximum = "Maximum"
    var id: String { rawValue }

    var estSize: String {
        switch self {
        case .compact: return "450 KB"
        case .standard: return "1.1 MB"
        case .maximum: return "2.4 MB"
        }
    }
}

enum ProPlanOption: String, CaseIterable, Identifiable {
    case yearly = "pro_yearly"
    case quarterly = "pro_quarterly"
    case monthly = "pro_monthly"
    var id: String { rawValue }

    var title: String {
        switch self {
        case .yearly: return "Yearly Access"
        case .quarterly: return "3 Months"
        case .monthly: return "1 Month"
        }
    }

    var price: String {
        switch self {
        case .yearly: return "$6 / year"
        case .quarterly: return "$2 / 3 mos"
        case .monthly: return "$1 / month"
        }
    }

    var perMonth: String {
        switch self {
        case .yearly: return "$0.50 / month · Billed $6 annually"
        case .quarterly: return "$0.67 / month · Billed $2 every 3 months"
        case .monthly: return "Flexible monthly billing, cancel anytime"
        }
    }

    var badge: String? {
        switch self {
        case .yearly: return "BEST VALUE · SAVE 50%"
        case .quarterly: return "SAVE 33%"
        case .monthly: return nil
        }
    }
}

struct DraftPageItem: Identifiable {
    let id: String
    var title: String
    var imagePath: String = ""
    var source: String = "camera"
    var enhanceMode: EnhanceFilterMode
    var rotationDegrees: Int
    var corners: [CGPoint]
    var ocrText: String

    static func defaultQuad() -> [CGPoint] {
        [
            CGPoint(x: 0.10, y: 0.12),
            CGPoint(x: 0.90, y: 0.12),
            CGPoint(x: 0.90, y: 0.88),
            CGPoint(x: 0.10, y: 0.88),
        ]
    }
}

struct DocumentItem: Identifiable {
    let id: String
    var name: String
    var docType: String
    var imagePath: String = ""
    var source: String = "camera"
    var createdAt: Date
    var pageCount: Int
    var ocrText: String
    var ocrLang: String
    var ocrConfidence: Double
    var summary: String
    var tags: [String]
}

struct SuggestCandidateItem: Identifiable {
    let id: String
    var category: String
    var title: String
    var subtitle: String
    var confidencePercent: Int
    var isDocument: Bool
    var selected: Bool
    var mediaId: String? = nil
}

@MainActor
final class DocScanAppState: ObservableObject {
    static let freeDailyQuota = 5

    @Published var route: DocScanRoute = .onboarding
    @Published var activeTab: MainTab = .library

    @Published var isPro: Bool = false
    @Published var selectedPlan: ProPlanOption = .yearly
    @Published var quotaUsedToday: Int = 0

    @Published var cameraPermissionGranted: Bool = false
    @Published var photoPermissionGranted: Bool = false

    @Published var isBatchMode: Bool = true
    @Published var autoCaptureEnabled: Bool = true
    @Published var defaultEnhance: EnhanceFilterMode = .docClean
    @Published var autoRenameEnabled: Bool = true
    @Published var backgroundProcessingEnabled: Bool = true
    @Published var notificationsEnabled: Bool = false

    @Published var draftPages: [DraftPageItem] = []
    @Published var activeDraftIndex: Int = 0
    @Published var editingDocumentId: String? = nil

    @Published var documents: [DocumentItem] = []
    @Published var selectedDocumentId: String? = nil
    @Published var selectedCategory: String = "All"
    @Published var searchQuery: String = ""
    @Published var searchOnlyTagged: Bool = false

    @Published var suggestCandidates: [SuggestCandidateItem] = []
    @Published var isScanningSuggestions: Bool = false

    // Backup (iOS: Google Drive + iCloud Drive + Google Photos; images only)
    @Published var googleDriveConnected: Bool = false
    @Published var iCloudDriveConnected: Bool = ICloudDriveBackupService.isAvailable
    @Published var googlePhotosConnected: Bool = false
    @Published var backupWifiOnly: Bool = true
    @Published var backupEntireLibrary: Bool = true
    @Published var lastSyncMessage: String = "Not backed up yet"

    // Export
    @Published var exportFormat: ExportFormatOption = .pdf
    @Published var exportQuality: ExportQualityOption = .standard
    @Published var exportEncryptPassword: Bool = false
    @Published var exportPassword: String = ""

    @Published var statusBanner: String? = nil

    private let engine = DefaultDocScanEngine()
    private lazy var pipeline = DocumentPipeline(engine: engine)
    let apiClient = DocScanAPIClient.shared
    let storeKit = StoreKitManager()
    private let localStore = DocScanStore.shared

    init() {
        self.isPro = storeKit.isPro
        let persisted = localStore.fetchAll()
        self.documents = persisted.map { rec in
            DocumentItem(
                id: rec.id,
                name: rec.name,
                docType: rec.docType ?? "Scanned",
                imagePath: rec.imagePath,
                source: rec.source,
                createdAt: rec.createdAt,
                pageCount: 1,
                ocrText: rec.ocrText,
                ocrLang: rec.ocrLang ?? "en",
                ocrConfidence: 0.98,
                summary: rec.summary ?? "",
                tags: rec.tags
            )
        }
        Task {
            try? await engine.initialize()
            await pipeline.setOnPageUpdated { [weak self] pageId, st in
                guard let self else { return }
                self.applyPipelineUpdate(pageId: pageId, state: st)
            }
            try? await apiClient.ensureRegistered()
            if let status = try? await apiClient.getQuotaToday() {
                self.quotaUsedToday = status.used
                self.isPro = status.isPro || storeKit.isPro
            }
            await storeKit.loadProducts()
        }
    }

    var remainingFreeQuota: Int {
        max(Self.freeDailyQuota - quotaUsedToday, 0)
    }

    var selectedDocument: DocumentItem? {
        documents.first { $0.id == selectedDocumentId } ?? documents.first
    }

    func openDocument(_ id: String) {
        selectedDocumentId = id
        route = .docDetail
    }

    func startNewCaptureSession() {
        editingDocumentId = nil
        draftPages = []
        activeDraftIndex = 0
        route = cameraPermissionGranted ? .capture : .cameraPermission
    }

    func addCapturedImage(_ image: UIImage, corners: [CGPoint] = DraftPageItem.defaultQuad(), title: String? = nil) {
        let pageId = UUID().uuidString
        let savedPath = FileStorageManager.saveImage(image, pageId: pageId)?.path ?? ""
        let idx = draftPages.count + 1
        draftPages.append(
            DraftPageItem(
                id: pageId,
                title: title ?? "Scan \(idx)",
                imagePath: savedPath,
                source: "camera",
                enhanceMode: defaultEnhance,
                rotationDegrees: 0,
                corners: corners,
                ocrText: ""
            )
        )
        activeDraftIndex = max(draftPages.count - 1, 0)
    }

    func importImages(_ images: [UIImage]) {
        guard !images.isEmpty else { return }
        editingDocumentId = nil
        draftPages = []
        Task {
            var newDrafts: [DraftPageItem] = []
            for (idx, image) in images.enumerated() {
                let pageId = UUID().uuidString
                let savedPath = FileStorageManager.saveImage(image, pageId: pageId)?.path ?? ""
                let quad = await engine.detectDocument(image: image)
                let corners = normalizedCorners(from: quad, imageSize: image.size)
                newDrafts.append(
                    DraftPageItem(
                        id: pageId,
                        title: "Imported Page \(idx + 1)",
                        imagePath: savedPath,
                        source: "import",
                        enhanceMode: defaultEnhance,
                        rotationDegrees: 0,
                        corners: corners,
                        ocrText: ""
                    )
                )
            }
            self.draftPages = newDrafts
            self.activeDraftIndex = 0
            self.route = .reviewEdit
        }
    }

    func refreshLibrarySuggestions() {
        guard photoPermissionGranted, !isScanningSuggestions else { return }
        isScanningSuggestions = true
        Task {
            let provider = PhotoKitSuggestProvider()
            let items = await provider.loadChunk(cursor: nil, limit: 60)
            var scored: [SuggestCandidateItem] = []
            for item in items {
                guard let thumb = await provider.loadThumb(mediaId: item.mediaId) else { continue }
                let score = SuggestScorer.score(image: thumb)
                let pct = min(max(Int(score * 100), 0), 100)
                let isDoc = score >= 0.55
                let takenDate = Date(timeIntervalSince1970: Double(item.takenAtMs) / 1000.0)
                let days = max(0, Int(Date().timeIntervalSince(takenDate) / 86_400))
                let ageLabel = days == 0 ? "Today" : (days == 1 ? "Yesterday" : "\(days) days ago")
                scored.append(
                    SuggestCandidateItem(
                        id: item.mediaId,
                        category: isDoc ? "Document" : "Photo",
                        title: isDoc ? "Document candidate" : "Recent photo",
                        subtitle: "\(ageLabel) · \(item.width)×\(item.height)",
                        confidencePercent: pct,
                        isDocument: isDoc,
                        selected: isDoc,
                        mediaId: item.mediaId
                    )
                )
            }
            self.suggestCandidates = scored.sorted { $0.confidencePercent > $1.confidencePercent }
            self.isScanningSuggestions = false
        }
    }

    func importSelectedSuggestions() {
        let chosen = suggestCandidates.filter { $0.selected }
        guard !chosen.isEmpty else { return }
        Task {
            let provider = PhotoKitSuggestProvider()
            var images: [UIImage] = []
            for item in chosen {
                if let mediaId = item.mediaId, let img = await provider.loadFullImage(mediaId: mediaId) {
                    images.append(img)
                }
            }
            if !images.isEmpty {
                self.importImages(images)
            }
        }
    }

    func editExistingDocument(_ doc: DocumentItem) {
        editingDocumentId = doc.id
        draftPages = [
            DraftPageItem(
                id: doc.id,
                title: doc.name,
                imagePath: doc.imagePath,
                source: doc.source,
                enhanceMode: .original,
                rotationDegrees: 0,
                corners: [
                    CGPoint(x: 0.03, y: 0.03),
                    CGPoint(x: 0.97, y: 0.03),
                    CGPoint(x: 0.97, y: 0.97),
                    CGPoint(x: 0.03, y: 0.97),
                ],
                ocrText: doc.ocrText
            )
        ]
        activeDraftIndex = 0
        route = .reviewEdit
    }

    func commitDraftPages() -> Bool {
        let count = draftPages.count
        guard count > 0 else { return false }

        let isReEdit = editingDocumentId != nil
        if !isReEdit && !isPro && quotaUsedToday + count > Self.freeDailyQuota {
            AnalyticsTracker.logEvent("quota_hit", params: ["quota_remaining": remainingFreeQuota])
            route = .paywall
            return false
        }

        let snapshot = draftPages
        let editId = editingDocumentId
        Task {
            if !isReEdit && !isPro {
                let res = await apiClient.consumeQuota(n: count)
                switch res {
                case .allowed(let status):
                    self.quotaUsedToday = status.used
                    self.isPro = status.isPro
                case .quotaExceeded:
                    AnalyticsTracker.logEvent("quota_hit", params: ["quota_remaining": 0])
                    self.route = .paywall
                    return
                case .networkError:
                    if self.quotaUsedToday + count > Self.freeDailyQuota {
                        self.route = .paywall
                        return
                    }
                    self.quotaUsedToday += count
                }
            }

            if let editId, let first = snapshot.first,
               let idx = self.documents.firstIndex(where: { $0.id == editId }) {
                let existing = self.documents[idx]
                let geomChanged = first.rotationDegrees != 0 || first.corners != DraftPageItem.defaultQuad()
                let pathChanged = !first.imagePath.isEmpty && first.imagePath != existing.imagePath
                if (pathChanged || first.enhanceMode != defaultEnhance || geomChanged),
                   let raw = FileStorageManager.loadImage(path: first.imagePath.isEmpty ? existing.imagePath : first.imagePath) {
                    let processed = applyDraftEdits(raw: raw, draft: first)
                    let savedPath = FileStorageManager.saveImage(processed, pageId: existing.id)?.path ?? existing.imagePath
                    self.documents[idx].imagePath = savedPath
                    self.documents[idx].name = first.title
                    await pipeline.enqueuePage(
                        pageId: existing.id,
                        name: first.title,
                        source: existing.source,
                        imagePath: savedPath,
                        image: processed,
                        quad: nil,
                        preset: .original,
                        sortIndex: 0,
                        freshScan: false
                    )
                } else {
                    self.documents[idx].name = first.title
                    self.localStore.upsert(
                        PersistedPageRecord(
                            id: existing.id,
                            name: first.title,
                            source: existing.source,
                            imagePath: existing.imagePath,
                            createdAt: existing.createdAt,
                            ocrText: existing.ocrText,
                            ocrLang: existing.ocrLang,
                            docType: existing.docType,
                            tags: existing.tags,
                            summary: existing.summary
                        )
                    )
                }
            } else {
                let batchId = snapshot.count > 1 ? UUID().uuidString : nil
                var firstId: String?
                for (idx, draft) in snapshot.enumerated() {
                    guard let raw = FileStorageManager.loadImage(path: draft.imagePath) else { continue }
                    let processed = applyDraftEdits(raw: raw, draft: draft)
                    let savedPath = FileStorageManager.saveImage(processed, pageId: draft.id)?.path ?? draft.imagePath
                    let newDoc = DocumentItem(
                        id: draft.id,
                        name: draft.title,
                        docType: "Scanned",
                        imagePath: savedPath,
                        source: draft.source,
                        createdAt: Date(),
                        pageCount: 1,
                        ocrText: "",
                        ocrLang: "en",
                        ocrConfidence: 0.0,
                        summary: "",
                        tags: []
                    )
                    self.localStore.upsert(
                        PersistedPageRecord(
                            id: newDoc.id,
                            batchId: batchId,
                            name: newDoc.name,
                            source: newDoc.source,
                            imagePath: savedPath,
                            createdAt: newDoc.createdAt,
                            sortIndex: idx
                        )
                    )
                    self.documents.insert(newDoc, at: 0)
                    if firstId == nil { firstId = newDoc.id }
                    await pipeline.enqueuePage(
                        pageId: draft.id,
                        batchId: batchId,
                        name: draft.title,
                        source: draft.source,
                        imagePath: savedPath,
                        image: processed,
                        quad: nil,
                        preset: .original,
                        sortIndex: idx,
                        freshScan: true
                    )
                }
                if let firstId { self.selectedDocumentId = firstId }
                let source = snapshot.first?.source ?? "camera"
                AnalyticsTracker.logEvent("scan_save", params: ["n_pages": count, "source": source, "is_pro": self.isPro])
            }

            self.draftPages = []
            self.editingDocumentId = nil
            self.statusBanner = "Saved \(count) page(s) — processing on device"
            self.route = .homeLibrary
            self.activeTab = .library
        }
        return true
    }

    private func applyPipelineUpdate(pageId: String, state st: DocumentPipeline.PageState) {
        if let idx = documents.firstIndex(where: { $0.id == pageId }) {
            documents[idx].name = st.name
            if let text = st.ocr?.text { documents[idx].ocrText = text }
            if let lang = st.lang?.lang ?? st.ocr?.lang { documents[idx].ocrLang = lang }
            if let conf = st.lang?.confidence ?? st.ocr?.langConfidence { documents[idx].ocrConfidence = Double(conf) }
            if let dt = st.docType?.docType { documents[idx].docType = dt }
            if !st.tags.isEmpty { documents[idx].tags = st.tags }
            if let sum = st.summary?.text { documents[idx].summary = sum }
        }
        let existing = localStore.fetchById(pageId)
        localStore.upsert(
            PersistedPageRecord(
                id: pageId,
                batchId: st.batchId ?? existing?.batchId,
                name: st.name,
                source: st.source,
                imagePath: st.imagePath,
                createdAt: existing?.createdAt ?? Date(),
                ocrText: st.ocr?.text ?? existing?.ocrText ?? "",
                ocrLang: st.lang?.lang ?? st.ocr?.lang ?? existing?.ocrLang,
                docType: st.docType?.docType ?? existing?.docType,
                tags: st.tags.isEmpty ? (existing?.tags ?? []) : st.tags,
                summary: st.summary?.text ?? existing?.summary,
                sortIndex: st.sortIndex
            )
        )
    }

    private func applyDraftEdits(raw: UIImage, draft: DraftPageItem) -> UIImage {
        var current = raw
        let isFullBleed = draft.corners.count == 4 &&
            draft.corners[0].x <= 0.04 && draft.corners[0].y <= 0.04 &&
            draft.corners[2].x >= 0.96 && draft.corners[2].y >= 0.96
        if !isFullBleed && draft.corners.count == 4, let cg = current.cgImage {
            let w = CGFloat(cg.width)
            let h = CGFloat(cg.height)
            let quad = DocQuad(
                topLeft: DocPoint(x: draft.corners[0].x * w, y: draft.corners[0].y * h),
                topRight: DocPoint(x: draft.corners[1].x * w, y: draft.corners[1].y * h),
                bottomRight: DocPoint(x: draft.corners[2].x * w, y: draft.corners[2].y * h),
                bottomLeft: DocPoint(x: draft.corners[3].x * w, y: draft.corners[3].y * h)
            )
            current = ImageEnhance.perspectiveWarp(current, quad: quad)
        }
        if draft.rotationDegrees % 360 != 0 {
            current = ImageEnhance.rotate(current, degrees: draft.rotationDegrees)
        }
        let preset = toEnginePreset(draft.enhanceMode)
        if preset != .original {
            current = engine.enhance(image: current, preset: preset)
        }
        return current
    }

    private func toEnginePreset(_ mode: EnhanceFilterMode) -> EnhancePreset {
        switch mode {
        case .original: return .original
        case .color: return .color
        case .gray: return .gray
        case .bw: return .bw
        case .docClean: return .document
        case .deshadow: return .deshadow
        }
    }

    private func normalizedCorners(from quad: DocQuad?, imageSize: CGSize) -> [CGPoint] {
        guard let quad, imageSize.width > 0, imageSize.height > 0 else {
            return DraftPageItem.defaultQuad()
        }
        func pt(_ p: DocPoint) -> CGPoint {
            CGPoint(
                x: min(max(p.x / imageSize.width, 0.02), 0.98),
                y: min(max(p.y / imageSize.height, 0.02), 0.98)
            )
        }
        return [pt(quad.topLeft), pt(quad.topRight), pt(quad.bottomRight), pt(quad.bottomLeft)]
    }

    func addTagToSelected(_ raw: String) {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "#", with: "")
        guard !clean.isEmpty, let id = selectedDocument?.id,
              let idx = documents.firstIndex(where: { $0.id == id }) else { return }
        if !documents[idx].tags.contains(clean) {
            documents[idx].tags.append(clean)
        }
    }

    func removeTagFromSelected(_ tag: String) {
        guard let id = selectedDocument?.id,
              let idx = documents.firstIndex(where: { $0.id == id }) else { return }
        documents[idx].tags.removeAll { $0 == tag }
    }

    func clearLocalLibrary() {
        documents = []
        selectedDocumentId = nil
        localStore.clearAll()
        statusBanner = "Local library cleared"
    }

    func deleteSelectedDocument() {
        guard let doc = selectedDocument else { return }
        documents.removeAll { $0.id == doc.id }
        selectedDocumentId = nil
        localStore.delete(id: doc.id)
        statusBanner = "Document deleted"
        route = .homeLibrary
    }

    func renameSelectedDocument(_ newName: String) {
        let clean = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, let doc = selectedDocument,
              let idx = documents.firstIndex(where: { $0.id == doc.id }) else { return }
        documents[idx].name = clean
        if let rec = localStore.fetchById(doc.id) {
            rec.name = clean
            localStore.upsert(rec)
        }
        statusBanner = "Renamed to \(clean)"
    }

    func exportSelectedDocumentURL() -> URL? {
        guard let doc = selectedDocument else { return nil }
        let pages = [(imagePath: doc.imagePath, ocrText: doc.ocrText)]
        AnalyticsTracker.logEvent("export_share", params: ["format": exportFormat.rawValue])
        switch exportFormat {
        case .pdf:
            return ExportServices.exportSearchablePDF(title: doc.name, pages: pages)
        case .doc:
            return ExportServices.exportDOCX(title: doc.name, pages: pages)
        case .images:
            return ExportServices.exportPlainTextOrImages(title: doc.name, pages: pages).first
        }
    }

    func connectGoogleBackup(includePhotos: Bool) {
        if GoogleOAuthBackupService.shared.isSignedIn { return }
        Task {
            do {
                _ = try await GoogleOAuthBackupService.shared.signIn(includePhotos: includePhotos)
                self.statusBanner = "Google account connected for backup"
            } catch {
                self.googleDriveConnected = false
                self.googlePhotosConnected = false
                self.statusBanner = "Google sign-in unavailable (check GOOGLE_OAUTH_CLIENT_ID)"
            }
        }
    }

    func syncBackupNow() {
        guard iCloudDriveConnected || googleDriveConnected || googlePhotosConnected else {
            lastSyncMessage = "Connect iCloud, Drive, or Photos first"
            return
        }
        let targets = backupEntireLibrary ? documents : (selectedDocument.map { [$0] } ?? [])
        guard !targets.isEmpty else {
            lastSyncMessage = "No pages to sync"
            return
        }
        lastSyncMessage = "Syncing \(targets.count) page(s)..."
        Task {
            var uploaded = 0
            var failed = 0
            for doc in targets {
                guard !doc.imagePath.isEmpty else { continue }
                let fileURL = URL(fileURLWithPath: doc.imagePath)
                let fileName = "\(doc.name.isEmpty ? "Scan" : doc.name)_\(doc.id.prefix(6)).jpg"
                var ok = true
                if iCloudDriveConnected {
                    do {
                        try await ICloudDriveBackupService.uploadFile(localURL: fileURL, fileName: fileName)
                    } catch {
                        ok = false
                    }
                }
                if googleDriveConnected {
                    do {
                        try await GoogleOAuthBackupService.shared.uploadImageToDrive(localURL: fileURL, fileName: fileName)
                    } catch {
                        ok = false
                    }
                }
                if googlePhotosConnected {
                    do {
                        try await GoogleOAuthBackupService.shared.uploadImageToPhotos(localURL: fileURL, fileName: fileName)
                    } catch {
                        ok = false
                    }
                }
                if ok { uploaded += 1 } else { failed += 1 }
            }
            self.lastSyncMessage = failed == 0
                ? "Synced \(uploaded) image(s) just now"
                : "Synced \(uploaded), failed \(failed) (check cloud sign-in)"
            AnalyticsTracker.logEvent("backup_sync", params: ["uploaded": uploaded, "failed": failed])
        }
    }

    func deleteAccountAndData() {
        clearLocalLibrary()
        isPro = false
        quotaUsedToday = 0
        googleDriveConnected = false
        iCloudDriveConnected = false
        googlePhotosConnected = false
        GoogleOAuthBackupService.shared.revoke()
        storeKit.clearLocalEntitlement()
        Task {
            try? await apiClient.deleteDeviceMe()
        }
        statusBanner = "Device account & local data deleted"
    }

    func purchasePlan(_ plan: ProPlanOption) {
        selectedPlan = plan
        Task {
            do {
                let ok = try await storeKit.purchase(productId: plan.rawValue)
                if ok {
                    self.isPro = true
                    AnalyticsTracker.logEvent("purchase_success", params: ["product_id": plan.rawValue])
                    self.statusBanner = "DocScan Pro active (\(plan.title))"
                    self.route = .homeLibrary
                }
            } catch {
                AnalyticsTracker.logEvent("purchase_fail", params: ["product_id": plan.rawValue])
                self.statusBanner = "Purchase failed: \(error.localizedDescription)"
            }
        }
    }

    func restorePurchases() {
        Task {
            let restored = await storeKit.restorePurchases()
            self.isPro = restored
            if restored {
                AnalyticsTracker.logEvent("restore_success")
                self.statusBanner = "DocScan Pro restored"
                self.route = .homeLibrary
            } else {
                self.statusBanner = "No active Pro subscription found"
            }
        }
    }

    func activatePro(_ plan: ProPlanOption) {
        selectedPlan = plan
        isPro = true
        statusBanner = "DocScan Pro active (\(plan.title))"
        route = .homeLibrary
    }

}
