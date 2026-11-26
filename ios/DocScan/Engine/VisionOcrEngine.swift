import UIKit
import Vision

enum VisionOcrEngine {
    static func recognize(image: UIImage, langHint: String?) async throws -> OcrPage {
        let scaled = ImageEnhance.downscale(image, maxLongEdge: DeviceCapability.nlpMaxLongEdge)
        guard let cg = scaled.cgImage else {
            return OcrPage(text: "", lang: langHint, langConfidence: nil, lines: [], title: nil)
        }
        let (page, rotationDegrees) = try await performRecognize(cgImage: cg, langHint: langHint)
        if rotationDegrees != 0 {
            let upright = ImageEnhance.rotate(scaled, degrees: rotationDegrees)
            if let uprightCg = upright.cgImage {
                let (uprightPage, _) = try await performRecognize(cgImage: uprightCg, langHint: langHint)
                return uprightPage
            }
        }
        return page
    }

    private static func performRecognize(cgImage: CGImage, langHint: String?) async throws -> (OcrPage, Int) {
        return try await withCheckedThrowingContinuation { cont in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    cont.resume(throwing: error)
                    return
                }
                let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
                var lines: [OcrLine] = []
                var parts: [String] = []
                var angles: [Double] = []
                for obs in observations {
                    guard let best = obs.topCandidates(1).first else { continue }
                    lines.append(OcrLine(text: best.string, confidence: best.confidence))
                    parts.append(best.string)
                    let dx = Double(obs.topRight.x - obs.topLeft.x)
                    let dy = Double(obs.topRight.y - obs.topLeft.y)
                    if hypot(dx, dy) > 0.01 {
                        angles.append(atan2(dy, dx) * 180.0 / .pi)
                    }
                }
                let rotation = detectRotationDegrees(angles: angles)
                let text = parts.joined(separator: "\n")
                let page = OcrPage(
                    text: text,
                    lang: langHint,
                    langConfidence: langHint != nil ? 0.9 : nil,
                    lines: lines,
                    title: lines.first(where: { $0.text.count >= 4 })?.text
                )
                cont.resume(returning: (page, rotation))
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.automaticallyDetectsLanguage = true
            if let hint = langHint?.trimmingCharacters(in: .whitespacesAndNewlines), !hint.isEmpty {
                request.recognitionLanguages = [hint]
            }
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                cont.resume(throwing: error)
            }
        }
    }

    private static func detectRotationDegrees(angles: [Double]) -> Int {
        guard !angles.isEmpty else { return 0 }
        let sorted = angles.sorted()
        let median = sorted[sorted.count / 2]
        let norm = ((median.truncatingRemainder(dividingBy: 360)) + 360).truncatingRemainder(dividingBy: 360)
        if abs(norm - 90) <= 25 { return 90 }
        if abs(norm - 180) <= 25 { return 180 }
        if abs(norm - 270) <= 25 { return 270 }
        return 0
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
