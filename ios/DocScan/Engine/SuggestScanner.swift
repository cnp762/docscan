import Foundation
import UIKit

struct SuggestMediaItem {
    var mediaId: String
    var takenAtMs: Int64
}

struct SuggestCacheEntry {
    var mediaId: String
    var takenAtMs: Int64
    var score: Float
    var dismissedAt: Date?
    var imported: Bool
}

final class SuggestCache {
    private var map: [String: SuggestCacheEntry] = [:]

    func upsert(mediaId: String, takenAtMs: Int64, score: Float) {
        let prev = map[mediaId]
        map[mediaId] = SuggestCacheEntry(
            mediaId: mediaId,
            takenAtMs: takenAtMs,
            score: score,
            dismissedAt: prev?.dismissedAt,
            imported: prev?.imported ?? false
        )
    }

    func isDismissed(_ mediaId: String) -> Bool { map[mediaId]?.dismissedAt != nil }
    func alreadyImported(_ mediaId: String) -> Bool { map[mediaId]?.imported == true }

    func topCandidates(minScore: Float = 0.45) -> [SuggestCacheEntry] {
        map.values
            .filter { $0.dismissedAt == nil && !$0.imported && $0.score >= minScore }
            .sorted { $0.score > $1.score }
    }
}

enum SuggestScorer {
    static func score(image: UIImage) -> Float {
        let w = max(image.size.width, 1)
        let h = max(image.size.height, 1)
        let aspect = w / h
        let aspectScore: Float
        if (0.55...0.85).contains(aspect) || (1.2...1.8).contains(aspect) {
            aspectScore = 0.55
        } else if (0.9...1.1).contains(aspect) {
            aspectScore = 0.25
        } else {
            aspectScore = 0.35
        }
        let mp = Float((w * h) / 1_000_000)
        let resScore: Float = mp >= 2 ? 0.35 : (mp >= 0.5 ? 0.25 : 0.1)
        return min(1, aspectScore + resScore)
    }
}

/// Enqueues 7-day suggest work on the shared WorkQueue (Photos provider later).
actor SuggestScanner {
    private let queue: WorkQueue
    private let cache: SuggestCache
    private let chunkSize: Int

    init(queue: WorkQueue, cache: SuggestCache = SuggestCache(), chunkSize: Int = 20) {
        self.queue = queue
        self.cache = cache
        self.chunkSize = chunkSize
    }

    @discardableResult
    func enqueueScan(cursor: String? = nil) async -> String {
        await queue.enqueue(WorkJob.suggestChunk(cursor: cursor, chunkSize: chunkSize))
    }

    func cancel() async {
        await queue.cancelSuggest()
    }

    func topCandidates(minScore: Float = 0.45) -> [SuggestCacheEntry] {
        cache.topCandidates(minScore: minScore)
    }
}
