import Foundation
import UIKit

enum FileStorageManager {
    static var pagesDirectory: URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = appSupport.appendingPathComponent("docscan/pages", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static var exportsDirectory: URL {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let dir = caches.appendingPathComponent("docscan/exports", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func saveImage(_ image: UIImage, pageId: String, quality: CGFloat = 0.92) -> URL? {
        guard let data = image.jpegData(compressionQuality: quality) else { return nil }
        let url = pagesDirectory.appendingPathComponent("\(pageId).jpg")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    static func loadImage(path: String) -> UIImage? {
        guard !path.isEmpty else { return nil }
        return UIImage(contentsOfFile: path)
    }

    static func deleteFile(at path: String) {
        guard !path.isEmpty else { return }
        try? FileManager.default.removeItem(atPath: path)
    }

    static func clearAllFiles() {
        try? FileManager.default.removeItem(at: pagesDirectory)
        try? FileManager.default.removeItem(at: exportsDirectory)
    }
}
