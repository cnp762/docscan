import Foundation
import PDFKit
import UIKit

enum ExportServices {
    static func exportSearchablePDF(title: String, pages: [(imagePath: String, ocrText: String)]) -> URL? {
        let safeName = sanitize(title.isEmpty ? "DocScan" : title)
        let outURL = FileStorageManager.exportsDirectory.appendingPathComponent("\(safeName).pdf")
        let pdfDoc = PDFDocument()

        for (index, page) in pages.enumerated() {
            let image = FileStorageManager.loadImage(path: page.imagePath)
            let pageSize = image?.size ?? CGSize(width: 1240, height: 1754)
            let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
            let pageData = renderer.pdfData { ctx in
                ctx.beginPage()
                if let image {
                    image.draw(in: CGRect(origin: .zero, size: pageSize))
                } else {
                    UIColor.white.setFill()
                    ctx.fill(CGRect(origin: .zero, size: pageSize))
                }
                let trimmed = page.ocrText.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    let attrs: [NSAttributedString.Key: Any] = [
                        .font: UIFont.systemFont(ofSize: 10),
                        .foregroundColor: UIColor.clear,
                    ]
                    NSAttributedString(string: trimmed, attributes: attrs)
                        .draw(in: CGRect(x: 16, y: 16, width: pageSize.width - 32, height: pageSize.height - 32))
                }
            }
            if let renderedDoc = PDFDocument(data: pageData), let pdfPage = renderedDoc.page(at: 0) {
                pdfDoc.insert(pdfPage, at: index)
            }
        }
        return pdfDoc.write(to: outURL) ? outURL : nil
    }

    static func exportPlainTextOrImages(title: String, pages: [(imagePath: String, ocrText: String)]) -> [URL] {
        let safeName = sanitize(title.isEmpty ? "DocScan" : title)
        var urls: [URL] = []
        for (idx, page) in pages.enumerated() {
            if let img = FileStorageManager.loadImage(path: page.imagePath),
               let data = img.jpegData(compressionQuality: 0.9) {
                let url = FileStorageManager.exportsDirectory.appendingPathComponent("\(safeName)_p\(idx + 1).jpg")
                try? data.write(to: url, options: .atomic)
                urls.append(url)
            }
        }
        return urls
    }

    static func printFile(url: URL, jobName: String) {
        guard UIPrintInteractionController.canPrint(url) else { return }
        let controller = UIPrintInteractionController.shared
        let info = UIPrintInfo(dictionary: nil)
        info.outputType = .general
        info.jobName = jobName
        controller.printInfo = info
        controller.printingItem = url
        controller.present(animated: true)
    }

    private static func sanitize(_ raw: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
        return String(raw.unicodeScalars.map { allowed.contains($0) ? Character($0) : "_" }.prefix(64))
    }
}
