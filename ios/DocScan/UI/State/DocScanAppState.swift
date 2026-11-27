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
}

@MainActor
final class DocScanAppState: ObservableObject {
    static let freeDailyQuota = 5

    @Published var route: DocScanRoute = .onboarding
    @Published var activeTab: MainTab = .library

    @Published var isPro: Bool = false
    @Published var selectedPlan: ProPlanOption = .yearly
    @Published var quotaUsedToday: Int = 3

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

    @Published var suggestCandidates: [SuggestCandidateItem] = [
        SuggestCandidateItem(id: "sug-1", category: "Receipt", title: "Blue Bottle Coffee", subtitle: "Yesterday · $14.20", confidencePercent: 98, isDocument: true, selected: true),
        SuggestCandidateItem(id: "sug-2", category: "Contract", title: "NDA Addendum P.1", subtitle: "2 days ago · Legal", confidencePercent: 94, isDocument: true, selected: true),
        SuggestCandidateItem(id: "sug-3", category: "Invoice", title: "Studio Rig Invoice", subtitle: "4 days ago · $480.00", confidencePercent: 91, isDocument: true, selected: true),
        SuggestCandidateItem(id: "sug-4", category: "Portrait", title: "Portrait photo", subtitle: "3 days ago", confidencePercent: 12, isDocument: false, selected: false),
        SuggestCandidateItem(id: "sug-5", category: "Scene", title: "Coastline sunset", subtitle: "5 days ago", confidencePercent: 5, isDocument: false, selected: false),
    ]

    // Backup (iOS: Google Drive + iCloud Drive + Google Photos; images only)
    @Published var googleDriveConnected: Bool = true
    @Published var iCloudDriveConnected: Bool = true
    @Published var googlePhotosConnected: Bool = false
    @Published var backupWifiOnly: Bool = true
    @Published var backupEntireLibrary: Bool = true
    @Published var lastSyncMessage: String = "Last mirror verified 2m ago"

    // Export
    @Published var exportFormat: ExportFormatOption = .pdf
    @Published var exportQuality: ExportQualityOption = .standard
    @Published var exportEncryptPassword: Bool = false
    @Published var exportPassword: String = ""

    @Published var statusBanner: String? = nil

    private let engine = DefaultDocScanEngine()

    init() {
        seedDocuments()
        Task { try? await engine.initialize() }
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

    func addCapturedPage(title: String? = nil) {
        let idx = draftPages.count + 1
        draftPages.append(
            DraftPageItem(
                id: UUID().uuidString,
                title: title ?? "Scan \(idx)",
                enhanceMode: defaultEnhance,
                rotationDegrees: 0,
                corners: DraftPageItem.defaultQuad(),
                ocrText: "DOCSCAN CAPTURE #\(idx)\nProcessed locally on-device."
            )
        )
        activeDraftIndex = max(draftPages.count - 1, 0)
    }

    func importPhotos(count: Int) {
        guard count > 0 else { return }
        editingDocumentId = nil
        draftPages = (0..<count).map { idx in
            DraftPageItem(
                id: UUID().uuidString,
                title: "Imported Page \(idx + 1)",
                enhanceMode: defaultEnhance,
                rotationDegrees: 0,
                corners: DraftPageItem.defaultQuad(),
                ocrText: "Imported photo \(idx + 1)\nReady for perspective crop & OCR."
            )
        }
        activeDraftIndex = 0
        route = .reviewEdit
    }

    func importSelectedSuggestions() {
        let chosen = suggestCandidates.filter { $0.selected }
        guard !chosen.isEmpty else { return }
        editingDocumentId = nil
        draftPages = chosen.map { item in
            DraftPageItem(
                id: UUID().uuidString,
                title: item.title,
                enhanceMode: defaultEnhance,
                rotationDegrees: 0,
                corners: DraftPageItem.defaultQuad(),
                ocrText: "\(item.category): \(item.title)\n\(item.subtitle)"
            )
        }
        activeDraftIndex = 0
        route = .reviewEdit
    }

    func editExistingDocument(_ doc: DocumentItem) {
        editingDocumentId = doc.id
        draftPages = [
            DraftPageItem(
                id: doc.id,
                title: doc.name,
                enhanceMode: defaultEnhance,
                rotationDegrees: 0,
                corners: DraftPageItem.defaultQuad(),
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
            route = .paywall
            return false
        }

        if let editId = editingDocumentId, let first = draftPages.first,
           let idx = documents.firstIndex(where: { $0.id == editId }) {
            documents[idx].name = first.title
            documents[idx].ocrText = first.ocrText
        } else {
            let newDoc = DocumentItem(
                id: UUID().uuidString,
                name: draftPages.first?.title ?? "Scanned Document",
                docType: "Invoices",
                createdAt: Date(),
                pageCount: count,
                ocrText: draftPages.map { $0.ocrText }.joined(separator: "\n\n"),
                ocrLang: "en",
                ocrConfidence: 0.99,
                summary: "Digitized locally on-device (\(count) page\(count > 1 ? "s" : "")).",
                tags: ["scanned"]
            )
            documents.insert(newDoc, at: 0)
            selectedDocumentId = newDoc.id
            if !isPro {
                quotaUsedToday += count
            }
        }

        draftPages = []
        editingDocumentId = nil
        statusBanner = "Saved \(count) page(s) locally"
        route = .homeLibrary
        activeTab = .library
        return true
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
        statusBanner = "Local library cleared"
    }

    func deleteAccountAndData() {
        clearLocalLibrary()
        isPro = false
        quotaUsedToday = 0
        googleDriveConnected = false
        iCloudDriveConnected = false
        googlePhotosConnected = false
        statusBanner = "Device account & local data deleted"
    }

    func activatePro(_ plan: ProPlanOption) {
        selectedPlan = plan
        isPro = true
        statusBanner = "DocScan Pro active (\(plan.title))"
        route = .homeLibrary
    }

    private func seedDocuments() {
        let now = Date()
        documents = [
            DocumentItem(
                id: "doc-1",
                name: "Invoice Mar 12",
                docType: "Invoices",
                createdAt: now,
                pageCount: 4,
                ocrText: "INVOICE #1042-88\nBilled to: Acme Corp\nDate: March 12, 2025\nAmount: $1,240.00 USD\nTax ID: US-99214482",
                ocrLang: "en",
                ocrConfidence: 0.994,
                summary: "Vendor invoice for cloud infrastructure services ($1,240.00). Due March 31, 2025.",
                tags: ["finance", "tax 2025", "vendor"]
            ),
            DocumentItem(
                id: "doc-2",
                name: "Receipt_0912",
                docType: "Receipts",
                createdAt: now.addingTimeInterval(-86_400),
                pageCount: 1,
                ocrText: "Blue Bottle Coffee\nTotal: $14.20\nTax 2024 · Paid via Card",
                ocrLang: "en",
                ocrConfidence: 0.98,
                summary: "Coffee receipt for $14.20 from Blue Bottle Coffee.",
                tags: ["tax 2024", "coffee"]
            ),
            DocumentItem(
                id: "doc-3",
                name: "Contract Lease Agreement",
                docType: "Contracts",
                createdAt: now.addingTimeInterval(-3 * 86_400),
                pageCount: 3,
                ocrText: "RESIDENTIAL LEASE AGREEMENT\nSigned between Landlord & Tenant for 12 months.",
                ocrLang: "en",
                ocrConfidence: 0.97,
                summary: "3-page signed lease agreement for 12-month term.",
                tags: ["legal", "signed"]
            ),
            DocumentItem(
                id: "doc-4",
                name: "Health Insurance Form",
                docType: "ID & Legal",
                createdAt: now.addingTimeInterval(-7 * 86_400),
                pageCount: 2,
                ocrText: "HEALTH INSURANCE POLICY #HI-4410\nMember ID: 8821-09\nCoverage: Comprehensive",
                ocrLang: "en",
                ocrConfidence: 0.96,
                summary: "2-page health insurance policy confirmation.",
                tags: ["medical"]
            ),
        ]
    }
}
