import Foundation

enum SummaryEngine {
    static func summarize(
        ocrText: String,
        mode: SummaryMode = .auto,
        maxChars: Int = DeviceCapability.pageSummaryMaxChars,
        inputCap: Int = DeviceCapability.batchSummaryMaxChars
    ) -> SummaryResult {
        let cleaned = String(ocrText.trimmingCharacters(in: .whitespacesAndNewlines).prefix(inputCap))
        if cleaned.isEmpty {
            return SummaryResult(text: "", source: SummaryResult.sourceExtractive)
        }
        let wantGen = mode == .auto && generativeAvailable() && DeviceCapability.allowGenerative()
        if wantGen {
            // Weights hook — extractive until ORT wired.
        }
        return SummaryResult(text: extractive(cleaned, maxChars: maxChars), source: SummaryResult.sourceExtractive)
    }

    private static func generativeAvailable() -> Bool {
        guard let url = Bundle.main.resourceURL?.appendingPathComponent("Models/summary") else {
            return false
        }
        let items = try? FileManager.default.contentsOfDirectory(atPath: url.path)
        return (items?.isEmpty == false)
    }

    private static func extractive(_ text: String, maxChars: Int) -> String {
        let parts = text
            .components(separatedBy: CharacterSet(charactersIn: ".\n!?"))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.count >= 20 }
        if parts.isEmpty {
            return String(text.prefix(min(maxChars, 280)))
        }
        let joined = parts.prefix(3).joined(separator: ". ")
        return String(joined.prefix(maxChars))
    }
}
