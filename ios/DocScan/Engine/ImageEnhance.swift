import UIKit
import CoreImage

enum ImageEnhance {
    private static let context = CIContext(options: nil)

    static func enhance(_ image: UIImage, preset: EnhancePreset) -> UIImage {
        guard preset != .original, let cg = image.cgImage else { return image }
        var ci = CIImage(cgImage: cg)
        switch preset {
        case .gray:
            ci = ci.applyingFilter("CIPhotoEffectMono")
        case .bw:
            ci = applyAdaptiveBw(ci)
        case .color:
            ci = ci.applyingFilter("CIColorControls", parameters: [
                kCIInputSaturationKey: 1.15,
            ])
        case .document:
            ci = ci.applyingFilter("CIColorControls", parameters: [
                kCIInputContrastKey: 1.25,
                kCIInputSaturationKey: 0.85,
                kCIInputBrightnessKey: 0.05,
            ])
        case .deshadow:
            ci = applyDeshadow(ci)
        case .original:
            break
        }
        guard let out = context.createCGImage(ci, from: ci.extent) else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: image.imageOrientation)
    }

    /// Flattens a skewed document quad using CoreImage `CIPerspectiveCorrection`.
    static func perspectiveWarp(_ image: UIImage, quad: DocQuad) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let ci = CIImage(cgImage: cg)
        let h = CGFloat(cg.height)
        let warped = ci.applyingFilter("CIPerspectiveCorrection", parameters: [
            "inputTopLeft": CIVector(x: quad.topLeft.x, y: h - quad.topLeft.y),
            "inputTopRight": CIVector(x: quad.topRight.x, y: h - quad.topRight.y),
            "inputBottomRight": CIVector(x: quad.bottomRight.x, y: h - quad.bottomRight.y),
            "inputBottomLeft": CIVector(x: quad.bottomLeft.x, y: h - quad.bottomLeft.y),
        ])
        guard let out = context.createCGImage(warped, from: warped.extent) else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: .up)
    }

    /// Downscales image so its longest edge is at most `maxLongEdge` pixels.
    static func downscale(_ image: UIImage, maxLongEdge: Int) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let w = cg.width
        let h = cg.height
        let longEdge = max(w, h)
        guard longEdge > maxLongEdge, maxLongEdge > 0 else { return image }
        let scale = CGFloat(maxLongEdge) / CGFloat(longEdge)
        let targetW = max(1, Int((CGFloat(w) * scale).rounded()))
        let targetH = max(1, Int((CGFloat(h) * scale).rounded()))
        let ci = CIImage(cgImage: cg).applyingFilter("CILanczosScaleTransform", parameters: [
            kCIInputScaleKey: scale,
            kCIInputAspectRatioKey: 1.0,
        ])
        let rect = CGRect(x: 0, y: 0, width: targetW, height: targetH)
        guard let out = context.createCGImage(ci, from: rect) else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: image.imageOrientation)
    }

    /// Rotates image clockwise by `degrees` (90, 180, 270) to upright orientation.
    static func rotate(_ image: UIImage, degrees: Int) -> UIImage {
        let norm = ((degrees % 360) + 360) % 360
        guard norm != 0, let cg = image.cgImage else { return image }
        let orientation: CGImagePropertyOrientation
        switch norm {
        case 90: orientation = .right
        case 180: orientation = .down
        case 270: orientation = .left
        default: return image
        }
        let ci = CIImage(cgImage: cg).oriented(orientation)
        guard let out = context.createCGImage(ci, from: ci.extent) else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: .up)
    }

    /// Illumination normalization via background division (`src / blurred_bg`) to remove local shadows.
    private static func applyDeshadow(_ ci: CIImage) -> CIImage {
        let extent = ci.extent
        let radius = max(18.0, min(extent.width, extent.height) * 0.035)
        let bg = ci.clampedToExtent()
            .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: radius])
            .cropped(to: extent)
        return ci.applyingFilter("CIDivideBlendMode", parameters: [
            kCIInputBackgroundImageKey: bg,
        ])
        .applyingFilter("CIColorControls", parameters: [
            kCIInputContrastKey: 1.18,
            kCIInputSaturationKey: 0.9,
        ])
    }

    /// High-contrast B&W after local illumination normalization to keep text crisp in shadowed areas.
    private static func applyAdaptiveBw(_ ci: CIImage) -> CIImage {
        return applyDeshadow(ci)
            .applyingFilter("CIPhotoEffectMono")
            .applyingFilter("CIUnsharpMask", parameters: [
                kCIInputRadiusKey: 1.5,
                kCIInputIntensityKey: 0.6,
            ])
            .applyingFilter("CIColorControls", parameters: [
                kCIInputContrastKey: 2.1,
                kCIInputBrightnessKey: 0.08,
            ])
    }
}
