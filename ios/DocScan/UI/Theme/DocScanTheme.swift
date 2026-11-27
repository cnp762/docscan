import ImageIO
import SwiftUI
import UIKit

/// Cybernetic Scanner Minimal design tokens for iOS (matching Stitch design system).
enum DocScanColors {
    static let surfaceBase = Color(hex: 0x0B132B)
    static let background = Color(hex: 0x0A122A)
    static let surfaceRaised = Color(hex: 0x0F172A)
    static let surfaceCard = Color(hex: 0x162032)
    static let surfaceOverlay = Color(hex: 0x1E293B)

    static let primary = Color(hex: 0x44E5C2)
    static let onPrimary = Color(hex: 0x00382D)
    static let scanGlow = Color(hex: 0x00C9A7)
    static let secondary = Color(hex: 0x4FDBC8)
    static let tertiary = Color(hex: 0x38BDF8)

    static let textPrimary = Color(hex: 0xF8FAFC)
    static let textSecondary = Color(hex: 0x94A3B8)
    static let textMuted = Color(hex: 0x64748B)

    static let borderSubtle = Color.white.opacity(0.08)
    static let borderMedium = Color.white.opacity(0.16)

    static let warningAmber = Color(hex: 0xF59E0B)
    static let dangerCrimson = Color(hex: 0xEF4444)
}

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

struct DocCardSurface<Content: View>: View {
    var borderColor: Color = DocScanColors.borderSubtle
    var backgroundColor: Color = DocScanColors.surfaceCard
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(borderColor, lineWidth: 1)
        )
    }
}

struct DocCircleIconButton: View {
    let systemImage: String
    var accessibilityLabel: String = ""
    var visualSize: CGFloat = 36
    var foreground: Color = DocScanColors.textPrimary
    var background: Color = DocScanColors.surfaceCard
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .foregroundStyle(foreground)
                .frame(width: visualSize, height: visualSize)
                .background(background)
                .clipShape(Circle())
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel.isEmpty ? systemImage : accessibilityLabel)
    }
}

struct ScalePressButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var scale: CGFloat = 0.92

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? scale : 1.0)
            .animation(reduceMotion ? nil : .spring(response: 0.22, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

struct QuotaChipView: View {
    let usedToday: Int
    let dailyLimit: Int
    let isPro: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: "bolt.fill")
                    .font(.caption2.weight(.semibold))
                Text(isPro ? "PRO · UNLIMITED" : "\(usedToday)/\(dailyLimit) today")
                    .font(.caption2.weight(.medium).monospaced())
            }
            .foregroundStyle(DocScanColors.scanGlow)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(DocScanColors.scanGlow.opacity(isPro ? 0.2 : 0.12))
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(isPro ? DocScanColors.tertiary : DocScanColors.scanGlow.opacity(0.35), lineWidth: 1)
            )
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct FreeAdBannerSlot: View {
    var isPro: Bool

    var body: some View {
        AdMobBannerView(isPro: isPro)
            .padding(.vertical, 4)
    }
}

struct LocalFirstTrustBanner: View {
    var text: String = "Documents never leave your phone. We never send your scans to DocScan."

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.shield.fill")
                .foregroundStyle(DocScanColors.scanGlow)
                .font(.system(size: 14))
            Text(text)
                .font(.caption)
                .foregroundStyle(DocScanColors.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(DocScanColors.surfaceRaised)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(DocScanColors.borderSubtle, lineWidth: 1)
        )
    }
}

struct FilterMatrixBar: View {
    @Binding var selected: EnhanceFilterMode
    @Namespace private var filterNS
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 4) {
            ForEach(EnhanceFilterMode.allCases, id: \.self) { mode in
                let isSelected = selected == mode
                Button {
                    if reduceMotion {
                        selected = mode
                    } else {
                        withAnimation(.spring(response: 0.26, dampingFraction: 0.8)) {
                            selected = mode
                        }
                    }
                } label: {
                    Text(mode.label)
                        .font(.caption.weight(isSelected ? .semibold : .medium))
                        .foregroundStyle(isSelected ? DocScanColors.scanGlow : DocScanColors.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background {
                            if isSelected {
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(DocScanColors.surfaceOverlay)
                                    .matchedGeometryEffect(id: "filterPill", in: filterNS)
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(DocScanColors.surfaceRaised)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(DocScanColors.borderSubtle, lineWidth: 1)
        )
    }
}

struct OcrInspectionCard: View {
    let text: String
    let languageLabel: String
    @State private var copied = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("OCR Extracted Text")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(DocScanColors.textPrimary)
                Spacer()
                Button {
                    UIPasteboard.general.string = text
                    if reduceMotion {
                        copied = true
                    } else {
                        withAnimation(.easeOut(duration: 0.16)) {
                            copied = true
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: copied ? "checkmark" : "doc.on.doc")
                            .font(.caption.weight(.medium))
                            .contentTransition(.symbolEffect(.replace))
                        Text(copied ? "Copied" : "Copy text")
                            .font(.caption.weight(.medium))
                    }
                    .foregroundStyle(copied ? DocScanColors.scanGlow : DocScanColors.textPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(copied ? DocScanColors.scanGlow.opacity(0.16) : DocScanColors.surfaceOverlay)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(copied ? DocScanColors.scanGlow : DocScanColors.borderSubtle, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }

            Text(text.isEmpty ? "No OCR text extracted yet." : text)
                .font(.caption.monospaced())
                .foregroundStyle(DocScanColors.textSecondary)
                .textSelection(.enabled)

            Text(languageLabel)
                .font(.caption2.weight(.medium).monospaced())
                .foregroundStyle(DocScanColors.tertiary)
        }
        .padding(16)
        .background(DocScanColors.surfaceRaised)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(DocScanColors.borderSubtle, lineWidth: 1)
        )
        .task(id: copied) {
            guard copied else { return }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if reduceMotion {
                copied = false
            } else {
                withAnimation(.easeOut(duration: 0.15)) {
                    copied = false
                }
            }
        }
    }
}

struct InteractiveQuadOverlay: View {
    @Binding var corners: [CGPoint] // normalized 0...1
    var onDragStateChange: ((Bool) -> Void)? = nil
    @State private var activeIndex: Int? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let w = max(geo.size.width, 1)
            let h = max(geo.size.height, 1)
            let pts = corners.map { CGPoint(x: $0.x * w, y: $0.y * h) }

            ZStack {
                if pts.count == 4 {
                    Path { path in
                        path.move(to: pts[0])
                        path.addLine(to: pts[1])
                        path.addLine(to: pts[2])
                        path.addLine(to: pts[3])
                        path.closeSubpath()
                    }
                    .stroke(DocScanColors.scanGlow.opacity(0.35), lineWidth: 8)

                    Path { path in
                        path.move(to: pts[0])
                        path.addLine(to: pts[1])
                        path.addLine(to: pts[2])
                        path.addLine(to: pts[3])
                        path.closeSubpath()
                    }
                    .stroke(DocScanColors.scanGlow, lineWidth: 2)

                    ForEach(0..<4, id: \.self) { idx in
                        let p = pts[idx]
                        let isDragging = activeIndex == idx
                        Circle()
                            .fill(DocScanColors.scanGlow.opacity(isDragging ? 0.45 : 0.25))
                            .frame(width: isDragging ? 38 : 28, height: isDragging ? 38 : 28)
                            .overlay(
                                Circle()
                                    .fill(DocScanColors.scanGlow)
                                    .frame(width: 18, height: 18)
                                    .overlay(Circle().fill(Color.white).frame(width: 10, height: 10))
                            )
                            .animation(reduceMotion ? nil : .spring(response: 0.22, dampingFraction: 0.75), value: isDragging)
                            .frame(width: 44, height: 44)
                            .position(p)
                    }

                    if let active = activeIndex, active < pts.count {
                        let target = pts[active]
                        let lx = min(max(target.x, 48), w - 48)
                        let ly = max(target.y - 76, 48)
                        Circle()
                            .fill(DocScanColors.surfaceOverlay.opacity(0.95))
                            .frame(width: 64, height: 64)
                            .overlay(Circle().stroke(DocScanColors.scanGlow, lineWidth: 2))
                            .overlay(
                                ZStack {
                                    Rectangle().fill(DocScanColors.scanGlow.opacity(0.7)).frame(width: 18, height: 1.5)
                                    Rectangle().fill(DocScanColors.scanGlow.opacity(0.7)).frame(width: 1.5, height: 18)
                                }
                            )
                            .position(x: lx, y: ly)
                            .transition(reduceMotion ? .opacity : .scale(scale: 0.75).combined(with: .opacity))
                    }
                }
            }
            .contentShape(Rectangle())
            .highPriorityGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if activeIndex == nil {
                            var bestIdx: Int? = nil
                            var bestDist: CGFloat = .greatestFiniteMagnitude
                            for (idx, p) in pts.enumerated() {
                                let d = hypot(p.x - value.startLocation.x, p.y - value.startLocation.y)
                                if d < bestDist && d <= 44 {
                                    bestDist = d
                                    bestIdx = idx
                                }
                            }
                            activeIndex = bestIdx
                            onDragStateChange?(bestIdx != nil)
                        }
                        guard let idx = activeIndex, idx < corners.count else { return }
                        let nx = min(max(value.location.x / w, 0.03), 0.97)
                        let ny = min(max(value.location.y / h, 0.03), 0.97)
                        corners[idx] = CGPoint(x: nx, y: ny)
                    }
                    .onEnded { _ in
                        activeIndex = nil
                        onDragStateChange?(false)
                    }
            )
        }
    }
}

struct DocPageThumbnailView: View {
    let imagePath: String
    var fallbackIcon: String = "doc.text.fill"
    var iconFont: Font = .title3
    var maxDimension: CGFloat = 512
    var enhancePreset: EnhancePreset? = nil
    var rotationDegrees: Int = 0

    @State private var loadedImage: UIImage? = nil

    private var taskKey: String {
        "\(imagePath)|\(String(describing: enhancePreset))|\(rotationDegrees)"
    }

    var body: some View {
        ZStack {
            DocScanColors.surfaceRaised
            if let img = loadedImage {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: fallbackIcon)
                    .font(iconFont)
                    .foregroundStyle(DocScanColors.scanGlow)
            }
        }
        .clipped()
        .task(id: taskKey) {
            guard !imagePath.isEmpty else {
                loadedImage = nil
                return
            }
            let preset = enhancePreset
            let rot = rotationDegrees
            loadedImage = await Task.detached(priority: .utility) {
                guard var img = Self.loadDownsampled(path: imagePath, maxPixelSize: maxDimension) else {
                    return nil
                }
                if rot % 360 != 0 {
                    img = ImageEnhance.rotate(img, degrees: rot)
                }
                if let preset, preset != .original {
                    img = ImageEnhance.enhance(img, preset: preset)
                }
                return img
            }.value
        }
    }

    private static func loadDownsampled(path: String, maxPixelSize: CGFloat) -> UIImage? {
        let url = URL(fileURLWithPath: path)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let sourceOpts = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithURL(url as CFURL, sourceOpts) else {
            return UIImage(contentsOfFile: path)
        }
        let thumbOpts = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ] as CFDictionary
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbOpts) else {
            return UIImage(contentsOfFile: path)
        }
        return UIImage(cgImage: cgImage)
    }
}
