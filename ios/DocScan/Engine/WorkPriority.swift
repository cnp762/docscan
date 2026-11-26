import Foundation

enum WorkPriority: Int, Comparable {
    case deferred = 10
    case suggest = 40
    case userSave = 80
    case freshScan = 100

    var band: Int { rawValue }

    static func < (lhs: WorkPriority, rhs: WorkPriority) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

enum WorkTimeouts {
    static let suggestMs: UInt64 = 5_000
    static let enhanceDetectMs: UInt64 = 8_000
    static let ocrMs: UInt64 = 15_000
    static let langMs: UInt64 = 2_000
    static let classifyMs: UInt64 = 2_000
    static let summaryExtractiveMs: UInt64 = 2_000
    static let summaryGenerativeMs: UInt64 = 8_000
    static let metadataMs: UInt64 = 3_000
}

enum WorkJob {
    case enhancePage(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case detectPage(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case ocrPage(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case langPage(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case classifyPage(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case summarizePage(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case writeMetadata(id: String, priority: WorkPriority, pageId: String, groupId: String?, timeoutMs: UInt64)
    case summarizeBatch(id: String, priority: WorkPriority, batchId: String, pageIds: [String], groupId: String?, timeoutMs: UInt64)
    case suggestScanChunk(id: String, priority: WorkPriority, cursor: String?, chunkSize: Int, timeoutMs: UInt64)
    case suggestScoreItem(id: String, priority: WorkPriority, mediaId: String, takenAtMs: Int64, timeoutMs: UInt64)

    var id: String {
        switch self {
        case .enhancePage(let id, _, _, _, _),
             .detectPage(let id, _, _, _, _),
             .ocrPage(let id, _, _, _, _),
             .langPage(let id, _, _, _, _),
             .classifyPage(let id, _, _, _, _),
             .summarizePage(let id, _, _, _, _),
             .writeMetadata(let id, _, _, _, _),
             .summarizeBatch(let id, _, _, _, _, _),
             .suggestScanChunk(let id, _, _, _, _),
             .suggestScoreItem(let id, _, _, _, _):
            return id
        }
    }

    var priority: WorkPriority {
        switch self {
        case .enhancePage(_, let p, _, _, _),
             .detectPage(_, let p, _, _, _),
             .ocrPage(_, let p, _, _, _),
             .langPage(_, let p, _, _, _),
             .classifyPage(_, let p, _, _, _),
             .summarizePage(_, let p, _, _, _),
             .writeMetadata(_, let p, _, _, _),
             .summarizeBatch(_, let p, _, _, _, _),
             .suggestScanChunk(_, let p, _, _, _),
             .suggestScoreItem(_, let p, _, _, _):
            return p
        }
    }

    var pageId: String? {
        switch self {
        case .enhancePage(_, _, let pageId, _, _),
             .detectPage(_, _, let pageId, _, _),
             .ocrPage(_, _, let pageId, _, _),
             .langPage(_, _, let pageId, _, _),
             .classifyPage(_, _, let pageId, _, _),
             .summarizePage(_, _, let pageId, _, _),
             .writeMetadata(_, _, let pageId, _, _):
            return pageId
        default:
            return nil
        }
    }

    var groupId: String? {
        switch self {
        case .enhancePage(_, _, _, let g, _),
             .detectPage(_, _, _, let g, _),
             .ocrPage(_, _, _, let g, _),
             .langPage(_, _, _, let g, _),
             .classifyPage(_, _, _, let g, _),
             .summarizePage(_, _, _, let g, _),
             .writeMetadata(_, _, _, let g, _),
             .summarizeBatch(_, _, _, _, let g, _):
            return g
        default:
            return nil
        }
    }

    var timeoutMs: UInt64 {
        switch self {
        case .enhancePage(_, _, _, _, let t),
             .detectPage(_, _, _, _, let t),
             .ocrPage(_, _, _, _, let t),
             .langPage(_, _, _, _, let t),
             .classifyPage(_, _, _, _, let t),
             .summarizePage(_, _, _, _, let t),
             .writeMetadata(_, _, _, _, let t),
             .summarizeBatch(_, _, _, _, _, let t),
             .suggestScanChunk(_, _, _, _, let t),
             .suggestScoreItem(_, _, _, _, let t):
            return t
        }
    }

    static func ocr(pageId: String, groupId: String?, priority: WorkPriority = .freshScan) -> WorkJob {
        .ocrPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.ocrMs)
    }

    static func lang(pageId: String, groupId: String?, priority: WorkPriority = .freshScan) -> WorkJob {
        .langPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.langMs)
    }

    static func classify(pageId: String, groupId: String?, priority: WorkPriority = .freshScan) -> WorkJob {
        .classifyPage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.classifyMs)
    }

    static func summarize(pageId: String, groupId: String?, priority: WorkPriority = .freshScan) -> WorkJob {
        .summarizePage(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.summaryGenerativeMs)
    }

    static func metadata(pageId: String, groupId: String?, priority: WorkPriority = .freshScan) -> WorkJob {
        .writeMetadata(id: UUID().uuidString, priority: priority, pageId: pageId, groupId: groupId, timeoutMs: WorkTimeouts.metadataMs)
    }

    static func suggestChunk(cursor: String? = nil, chunkSize: Int = 20) -> WorkJob {
        .suggestScanChunk(
            id: UUID().uuidString,
            priority: .suggest,
            cursor: cursor,
            chunkSize: chunkSize,
            timeoutMs: WorkTimeouts.suggestMs
        )
    }

    static func suggestScore(mediaId: String, takenAtMs: Int64) -> WorkJob {
        .suggestScoreItem(
            id: UUID().uuidString,
            priority: .suggest,
            mediaId: mediaId,
            takenAtMs: takenAtMs,
            timeoutMs: WorkTimeouts.suggestMs
        )
    }
}
