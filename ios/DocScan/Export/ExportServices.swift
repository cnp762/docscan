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

    static func exportDOCX(title: String, pages: [(imagePath: String, ocrText: String)]) -> URL? {
        let safeName = sanitize(title.isEmpty ? "DocScan" : title)
        let outURL = FileStorageManager.exportsDirectory.appendingPathComponent("\(safeName).docx")

        let contentTypes = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
        """
        let rels = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
        """
        var bodyXml = "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>\(escapeXml(title))</w:t></w:r></w:p>"
        for (idx, page) in pages.enumerated() {
            bodyXml += "<w:p><w:r><w:t>Page \(idx + 1)</w:t></w:r></w:p>"
            let lines = page.ocrText.components(separatedBy: .newlines)
            for line in lines where !line.trimmingCharacters(in: .whitespaces).isEmpty {
                bodyXml += "<w:p><w:r><w:t xml:space=\"preserve\">\(escapeXml(line))</w:t></w:r></w:p>"
            }
        }
        let documentXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>\(bodyXml)<w:sectPr/></w:body>
        </w:document>
        """

        let entries: [(String, Data)] = [
            ("[Content_Types].xml", Data(contentTypes.utf8)),
            ("_rels/.rels", Data(rels.utf8)),
            ("word/document.xml", Data(documentXml.utf8)),
        ]
        let zipData = buildUncompressedZip(entries: entries)
        do {
            try zipData.write(to: outURL, options: .atomic)
            return outURL
        } catch {
            return nil
        }
    }

    private static func escapeXml(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }

    private static func buildUncompressedZip(entries: [(String, Data)]) -> Data {
        var archive = Data()
        var centralDir = Data()
        for (name, data) in entries {
            let nameData = Data(name.utf8)
            let crc = crc32(data)
            let offset = UInt32(archive.count)
            let size = UInt32(data.count)

            archive.appendLE(UInt32(0x04034B50))
            archive.appendLE(UInt16(20))
            archive.appendLE(UInt16(0))
            archive.appendLE(UInt16(0))
            archive.appendLE(UInt16(0))
            archive.appendLE(UInt16(0))
            archive.appendLE(crc)
            archive.appendLE(size)
            archive.appendLE(size)
            archive.appendLE(UInt16(nameData.count))
            archive.appendLE(UInt16(0))
            archive.append(nameData)
            archive.append(data)

            centralDir.appendLE(UInt32(0x02014B50))
            centralDir.appendLE(UInt16(20))
            centralDir.appendLE(UInt16(20))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(crc)
            centralDir.appendLE(size)
            centralDir.appendLE(size)
            centralDir.appendLE(UInt16(nameData.count))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt16(0))
            centralDir.appendLE(UInt32(0))
            centralDir.appendLE(offset)
            centralDir.append(nameData)
        }
        let cdOffset = UInt32(archive.count)
        let cdSize = UInt32(centralDir.count)
        archive.append(centralDir)
        archive.appendLE(UInt32(0x06054B50))
        archive.appendLE(UInt16(0))
        archive.appendLE(UInt16(0))
        archive.appendLE(UInt16(entries.count))
        archive.appendLE(UInt16(entries.count))
        archive.appendLE(cdSize)
        archive.appendLE(cdOffset)
        archive.appendLE(UInt16(0))
        return archive
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320 : (crc >> 1)
            }
        }
        return ~crc
    }

    static func exportRtfDocument(title: String, pages: [(imagePath: String, ocrText: String)]) -> URL? {
        let safeName = sanitize(title.isEmpty ? "DocScan" : title)
        let outURL = FileStorageManager.exportsDirectory.appendingPathComponent("\(safeName).rtf")
        let body = pages.enumerated().map { idx, page in
            "Page \(idx + 1)\n\(page.ocrText.trimmingCharacters(in: .whitespacesAndNewlines))"
        }.joined(separator: "\n\n---\n\n")
        let attr = NSAttributedString(string: "\(title)\n\n\(body)")
        guard let data = try? attr.data(
            from: NSRange(location: 0, length: attr.length),
            documentAttributes: [.documentType: NSAttributedString.DocumentType.rtf]
        ) else {
            return nil
        }
        do {
            try data.write(to: outURL, options: .atomic)
            return outURL
        } catch {
            return nil
        }
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

private extension Data {
    mutating func appendLE(_ value: UInt16) {
        var v = value.littleEndian
        Swift.withUnsafeBytes(of: &v) { append(contentsOf: $0) }
    }

    mutating func appendLE(_ value: UInt32) {
        var v = value.littleEndian
        Swift.withUnsafeBytes(of: &v) { append(contentsOf: $0) }
    }
}
