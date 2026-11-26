import UIKit
import Vision

enum VisionOcrEngine {
    static func recognize(image: UIImage, langHint: String?) async throws -> OcrPage {
        guard let cg = image.cgImage else {
            return OcrPage(text: "", lang: langHint, langConfidence: nil, lines: [], title: nil)
        }
        return try await withCheckedThrowingContinuation { cont in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    cont.resume(throwing: error)
                    return
                }
                let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
                var lines: [OcrLine] = []
                var parts: [String] = []
                for obs in observations {
                    guard let best = obs.topCandidates(1).first else { continue }
                    lines.append(OcrLine(text: best.string, confidence: best.confidence))
                    parts.append(best.string)
                }
                let text = parts.joined(separator: "\n")
                cont.resume(returning: OcrPage(
                    text: text,
                    lang: langHint,
                    langConfidence: langHint != nil ? 0.9 : nil,
                    lines: lines,
                    title: lines.first(where: { $0.text.count >= 4 })?.text
                ))
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            let handler = VNImageRequestHandler(cgImage: cg, options: [:])
            do {
                try handler.perform([request])
            } catch {
                cont.resume(throwing: error)
            }
        }
    }

    static func detectQuad(image: UIImage) async -> DocQuad? {
        guard let cg = image.cgImage else { return nil }
        return await withCheckedContinuation { cont in
            let request = VNDetectDocumentSegmentationRequest { request, _ in
                guard let obs = (request.results as? [VNRectangleObservation])?.first else {
                    cont.resume(returning: insetQuad(width: cg.width, height: cg.height))
                    return
                }
                let w = CGFloat(cg.width)
                let h = CGFloat(cg.height)
                cont.resume(returning: DocQuad(
                    topLeft: DocPoint(x: obs.topLeft.x * w, y: (1 - obs.topLeft.y) * h),
                    topRight: DocPoint(x: obs.topRight.x * w, y: (1 - obs.topRight.y) * h),
                    bottomRight: DocPoint(x: obs.bottomRight.x * w, y: (1 - obs.bottomRight.y) * h),
                    bottomLeft: DocPoint(x: obs.bottomLeft.x * w, y: (1 - obs.bottomLeft.y) * h)
                ))
            }
            let handler = VNImageRequestHandler(cgImage: cg, options: [:])
            try? handler.perform([request])
        }
    }

    private static func insetQuad(width: Int, height: Int) -> DocQuad {
        let ix = CGFloat(width) * 0.06
        let iy = CGFloat(height) * 0.06
        let w = CGFloat(width)
        let h = CGFloat(height)
        return DocQuad(
            topLeft: DocPoint(x: ix, y: iy),
            topRight: DocPoint(x: w - ix, y: iy),
            bottomRight: DocPoint(x: w - ix, y: h - iy),
            bottomLeft: DocPoint(x: ix, y: h - iy)
        )
    }
}
