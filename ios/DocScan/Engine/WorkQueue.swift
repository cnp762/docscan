import Foundation

/// Shared serial priority queue — FreshScan > UserSave > Suggest > Deferred;
/// newer groupEnqueuedAt first; stepIndex ascending within a group.
actor WorkQueue {
    struct Progress: Sendable {
        var pending: Int = 0
        var runningId: String?
        var completed: Int = 0
        var failed: Int = 0
        var lastError: String?
    }

    private struct Entry {
        let job: WorkJob
        let groupEnqueuedAt: UInt64
        let stepIndex: Int
    }

    private var heap: [Entry] = []
    private var running = false
    private var cancelledJobIds = Set<String>()
    private var cancelledPageIds = Set<String>()
    private var cancelledGroupIds = Set<String>()
    private(set) var progress = Progress()
    private let handler: @Sendable (WorkJob) async -> Void

    init(handler: @escaping @Sendable (WorkJob) async -> Void) {
        self.handler = handler
    }

    @discardableResult
    func enqueue(_ job: WorkJob, groupEnqueuedAt: UInt64 = DispatchTime.now().uptimeNanoseconds, stepIndex: Int = 0) -> String {
        heap.append(Entry(job: job, groupEnqueuedAt: groupEnqueuedAt, stepIndex: stepIndex))
        sortHeap()
        progress.pending = heap.count
        Task { await self.pump() }
        return job.id
    }

    @discardableResult
    func enqueueChain(_ jobs: [WorkJob], groupEnqueuedAt: UInt64 = DispatchTime.now().uptimeNanoseconds) -> [String] {
        jobs.enumerated().map { idx, job in
            enqueue(job, groupEnqueuedAt: groupEnqueuedAt, stepIndex: idx)
        }
    }

    func cancel(jobId: String) {
        cancelledJobIds.insert(jobId)
        heap.removeAll { $0.job.id == jobId }
        progress.pending = heap.count
    }

    func cancelPage(_ pageId: String) {
        cancelledPageIds.insert(pageId)
        heap.removeAll { $0.job.pageId == pageId }
        progress.pending = heap.count
    }

    func cancelSuggest() {
        heap.removeAll { $0.job.priority == .suggest }
        progress.pending = heap.count
    }

    func pendingSnapshot() -> [WorkJob] {
        sortHeap()
        return heap.map(\.job)
    }

    private func pump() async {
        if running { return }
        running = true
        defer { running = false }
        while let entry = popBest() {
            let job = entry.job
            if isCancelled(job) {
                progress.pending = heap.count
                continue
            }
            progress.runningId = job.id
            progress.pending = heap.count
            let ok = await runTimed(job)
            if ok {
                progress.completed += 1
                progress.lastError = nil
            } else {
                progress.failed += 1
                progress.lastError = "timeout_or_fail:\(job.id)"
            }
            progress.runningId = nil
            progress.pending = heap.count
        }
    }

    private func runTimed(_ job: WorkJob) async -> Bool {
        await withTaskGroup(of: Bool.self) { group in
            group.addTask {
                await self.handler(job)
                return true
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: job.timeoutMs * 1_000_000)
                return false
            }
            let first = await group.next() ?? false
            group.cancelAll()
            return first
        }
    }

    private func popBest() -> Entry? {
        sortHeap()
        guard !heap.isEmpty else { return nil }
        return heap.removeFirst()
    }

    private func sortHeap() {
        heap.sort { a, b in
            if a.job.priority.band != b.job.priority.band {
                return a.job.priority.band > b.job.priority.band
            }
            if a.groupEnqueuedAt != b.groupEnqueuedAt {
                return a.groupEnqueuedAt > b.groupEnqueuedAt
            }
            return a.stepIndex < b.stepIndex
        }
    }

    private func isCancelled(_ job: WorkJob) -> Bool {
        if cancelledJobIds.contains(job.id) { return true }
        if let pageId = job.pageId, cancelledPageIds.contains(pageId) { return true }
        if let groupId = job.groupId, cancelledGroupIds.contains(groupId) { return true }
        return false
    }
}
