import Foundation
import ImageIO

enum ImageMetadataWriter {
    @discardableResult
    static func write(imagePath: String, meta: PageNlpMetadata) -> Bool {
        let url = URL(fileURLWithPath: imagePath)
        guard FileManager.default.fileExists(atPath: imagePath) else { return false }

        var sidecarOk = false
        do {
            try writeSidecar(beside: url, meta: meta)
            sidecarOk = true
        } catch {
            // ignore
        }

        writeExifBestEffort(url: url, meta: meta)
        return sidecarOk
    }

    static func sidecarURL(for imageURL: URL) -> URL {
        let stem = imageURL.deletingPathExtension().lastPathComponent
        return imageURL.deletingLastPathComponent().appendingPathComponent("\(stem).docscan.json")
    }

    static func readSidecar(imagePath: String) -> PageNlpMetadata? {
        let url = sidecarURL(for: URL(fileURLWithPath: imagePath))
        guard let data = try? Data(contentsOf: url),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return PageNlpMetadata(
            pageId: obj["page_id"] as? String ?? "",
            lang: obj["lang"] as? String,
            langConfidence: (obj["lang_confidence"] as? NSNumber)?.floatValue,
            docType: obj["doc_type"] as? String,
            tags: obj["tags"] as? [String] ?? [],
            summary: obj["summary"] as? String,
            summarySource: obj["summary_source"] as? String,
            nlpVersion: obj["nlp_version"] as? String ?? PageNlpMetadata.version
        )
    }

    private static func writeSidecar(beside imageURL: URL, meta: PageNlpMetadata) throws {
        var dict: [String: Any] = [
            "docscan": 1,
            "page_id": meta.pageId,
            "tags": meta.tags,
            "nlp_version": meta.nlpVersion,
        ]
        if let lang = meta.lang { dict["lang"] = lang }
        if let c = meta.langConfidence { dict["lang_confidence"] = c }
        if let t = meta.docType { dict["doc_type"] = t }
        if let s = meta.summary { dict["summary"] = s }
        if let src = meta.summarySource { dict["summary_source"] = src }
        let data = try JSONSerialization.data(withJSONObject: dict, options: [.sortedKeys])
        try data.write(to: sidecarURL(for: imageURL), options: .atomic)
    }

    private static func writeExifBestEffort(url: URL, meta: PageNlpMetadata) {
        var parts: [String] = []
        if let t = meta.docType { parts.append("doc_type=\(t)") }
        if let l = meta.lang { parts.append("lang=\(l)") }
        if let s = meta.summary { parts.append(String(s.prefix(200))) }
        let desc = parts.joined(separator: "; ")
        guard !desc.isEmpty,
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let type = CGImageSourceGetType(source),
              let destData = CFDataCreateMutable(nil, 0),
              let destination = CGImageDestinationCreateWithData(destData, type, 1, nil) else {
            return
        }
        let props: [CFString: Any] = [
            kCGImagePropertyExifDictionary: [
                kCGImagePropertyExifUserComment: desc,
            ] as CFDictionary,
        ]
        CGImageDestinationAddImageFromSource(destination, source, 0, props as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return }
        try? (destData as Data).write(to: url, options: .atomic)
    }
}
