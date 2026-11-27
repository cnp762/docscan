import Foundation
import SwiftData

@Model
final class PersistedPageRecord {
    @Attribute(.unique) var id: String
    var batchId: String?
    var name: String
    var source: String
    var imagePath: String
    var createdAt: Date
    var ocrText: String
    var ocrLang: String?
    var docType: String?
    var tagsRaw: String
    var summary: String?
    var sortIndex: Int

    init(
        id: String,
        batchId: String? = nil,
        name: String,
        source: String,
        imagePath: String,
        createdAt: Date = Date(),
        ocrText: String = "",
        ocrLang: String? = nil,
        docType: String? = nil,
        tags: [String] = [],
        summary: String? = nil,
        sortIndex: Int = 0
    ) {
        self.id = id
        self.batchId = batchId
        self.name = name
        self.source = source
        self.imagePath = imagePath
        self.createdAt = createdAt
        self.ocrText = ocrText
        self.ocrLang = ocrLang
        self.docType = docType
        self.tagsRaw = (try? String(data: JSONEncoder().encode(tags), encoding: .utf8)) ?? "[]"
        self.summary = summary
        self.sortIndex = sortIndex
    }

    var tags: [String] {
        guard let data = tagsRaw.data(using: .utf8),
              let decoded = try? JSONDecoder().decode([String].self, from: data) else {
            return []
        }
        return decoded
    }
}

@MainActor
final class DocScanStore {
    static let shared = DocScanStore()

    let container: ModelContainer?

    init() {
        let schema = Schema([PersistedPageRecord.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        self.container = try? ModelContainer(for: schema, configurations: [config])
    }

    func fetchAll() -> [PersistedPageRecord] {
        guard let context = container?.mainContext else { return [] }
        let descriptor = FetchDescriptor<PersistedPageRecord>(
            sortBy: [SortDescriptor(\.createdAt, order: .reverse), SortDescriptor(\.sortIndex, order: .forward)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    func upsert(_ record: PersistedPageRecord) {
        guard let context = container?.mainContext else { return }
        let targetId = record.id
        let descriptor = FetchDescriptor<PersistedPageRecord>(predicate: #Predicate { $0.id == targetId })
        if let existing = (try? context.fetch(descriptor))?.first {
            existing.batchId = record.batchId
            existing.name = record.name
            existing.source = record.source
            existing.imagePath = record.imagePath
            existing.ocrText = record.ocrText
            existing.ocrLang = record.ocrLang
            existing.docType = record.docType
            existing.tagsRaw = record.tagsRaw
            existing.summary = record.summary
            existing.sortIndex = record.sortIndex
        } else {
            context.insert(record)
        }
        try? context.save()
    }

    func fetchById(_ id: String) -> PersistedPageRecord? {
        guard let context = container?.mainContext else { return nil }
        let descriptor = FetchDescriptor<PersistedPageRecord>(predicate: #Predicate { $0.id == id })
        return (try? context.fetch(descriptor))?.first
    }

    func delete(id: String) {
        guard let context = container?.mainContext else { return }
        let descriptor = FetchDescriptor<PersistedPageRecord>(predicate: #Predicate { $0.id == id })
        if let matches = try? context.fetch(descriptor) {
            for item in matches {
                FileStorageManager.deleteFile(at: item.imagePath)
                context.delete(item)
            }
            try? context.save()
        }
    }

    func clearAll() {
        guard let context = container?.mainContext else { return }
        try? context.delete(model: PersistedPageRecord.self)
        try? context.save()
        FileStorageManager.clearAllFiles()
    }
}
