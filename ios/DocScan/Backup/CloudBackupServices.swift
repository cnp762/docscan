import Foundation

enum CloudBackupError: Error {
    case iCloudUnavailable
    case notAuthenticated
    case uploadFailed(Int)
}

enum ICloudDriveBackupService {
    static var isAvailable: Bool {
        FileManager.default.ubiquityIdentityToken != nil
    }

    static func uploadFile(localURL: URL, fileName: String) async throws {
        guard let containerURL = FileManager.default.url(forUbiquityContainerIdentifier: nil) else {
            throw CloudBackupError.iCloudUnavailable
        }
        let docsDir = containerURL
            .appendingPathComponent("Documents", isDirectory: true)
            .appendingPathComponent("DocScan", isDirectory: true)
        try FileManager.default.createDirectory(at: docsDir, withIntermediateDirectories: true)
        let destURL = docsDir.appendingPathComponent(fileName)
        if FileManager.default.fileExists(atPath: destURL.path) {
            try FileManager.default.removeItem(at: destURL)
        }
        try FileManager.default.copyItem(at: localURL, to: destURL)
    }
}
