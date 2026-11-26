import Foundation

/// Deprecated NLP-only executor — use [WorkQueue] for Save + suggest.
@available(*, deprecated, message: "Use WorkQueue")
actor EngineExecutor {
    func submitTimed<T: Sendable>(
        timeoutMs: UInt64,
        _ work: @Sendable () throws -> T
    ) async -> T? {
        await withTaskGroup(of: Optional<T>.self) { group in
            group.addTask {
                do { return try work() } catch { return nil }
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: timeoutMs * 1_000_000)
                return nil
            }
            defer { group.cancelAll() }
            for await value in group {
                if let value { return value }
            }
            return nil
        }
    }

    func cancelPending() {}
}
