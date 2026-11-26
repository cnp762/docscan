import Foundation

enum DeviceCapability {
    static let nlpMaxLongEdge = 1600
    static let batchSummaryMaxChars = 8000
    static let pageSummaryMaxChars = 500

    /// Soft session pause after memory warning (set by app delegate / scene).
    static var memoryWarningSoftPause = false

    struct Snapshot {
        let thermalThrottled: Bool
        let systemOk: Bool
    }

    static func snapshot() -> Snapshot {
        let thermal = isThermallyThrottled()
        let ok = !thermal && !memoryWarningSoftPause
        return Snapshot(thermalThrottled: thermal, systemOk: ok)
    }

    static func systemOk() -> Bool { snapshot().systemOk }

    static func allowGenerative() -> Bool { systemOk() }

    private static func isThermallyThrottled() -> Bool {
        switch ProcessInfo.processInfo.thermalState {
        case .serious, .critical:
            return true
        default:
            return false
        }
    }
}
