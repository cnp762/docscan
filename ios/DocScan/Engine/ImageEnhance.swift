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
            ci = ci.applyingFilter("CIPhotoEffectMono")
                .applyingFilter("CIColorControls", parameters: [
                    kCIInputContrastKey: 1.8,
                    kCIInputBrightnessKey: 0.05,
                ])
        case .color:
            ci = ci.applyingFilter("CIColorControls", parameters: [
                kCIInputSaturationKey: 1.15,
            ])
        case .document, .deshadow:
            ci = ci.applyingFilter("CIColorControls", parameters: [
                kCIInputContrastKey: 1.25,
                kCIInputSaturationKey: 0.85,
                kCIInputBrightnessKey: 0.05,
            ])
        case .original:
            break
        }
        guard let out = context.createCGImage(ci, from: ci.extent) else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: image.imageOrientation)
    }
}
