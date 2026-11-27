import SwiftUI

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

struct QuotaChipView: View {
    let usedToday: Int
    let dailyLimit: Int
    let isPro: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 11, weight: .semibold))
                Text(isPro ? "PRO · UNLIMITED" : "\(usedToday)/\(dailyLimit) today")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
            }
            .foregroundStyle(DocScanColors.scanGlow)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(DocScanColors.scanGlow.opacity(isPro ? 0.2 : 0.12))
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(isPro ? DocScanColors.tertiary : DocScanColors.scanGlow.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

struct LocalFirstTrustBanner: View {
    var text: String = "100% local-first · Documents never leave your phone"

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

    var body: some View {
        HStack(spacing: 4) {
            ForEach(EnhanceFilterMode.allCases, id: \.self) { mode in
                let isSelected = selected == mode
                Button {
                    selected = mode
                } label: {
                    Text(mode.label)
                        .font(.caption.weight(isSelected ? .semibold : .medium))
                        .foregroundStyle(isSelected ? DocScanColors.scanGlow : DocScanColors.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(isSelected ? DocScanColors.surfaceOverlay : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
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

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("OCR Extracted Text")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(DocScanColors.textPrimary)
                Spacer()
                Button {
                    UIPasteboard.general.string = text
                    copied = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: copied ? "checkmark" : "doc.on.doc")
                            .font(.system(size: 12))
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
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(DocScanColors.textSecondary)
                .textSelection(.enabled)

            Text(languageLabel)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundStyle(DocScanColors.tertiary)
        }
        .padding(16)
        .background(DocScanColors.surfaceRaised)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(DocScanColors.borderSubtle, lineWidth: 1)
        )
    }
}

struct InteractiveQuadOverlay: View {
    @Binding var corners: [CGPoint] // normalized 0...1
    @State private var activeIndex: Int? = nil

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
                            .position(p)
                            .gesture(
                                DragGesture(minimumDistance: 0)
                                    .onChanged { value in
                                        activeIndex = idx
                                        let nx = min(max(value.location.x / w, 0.03), 0.97)
                                        let ny = min(max(value.location.y / h, 0.03), 0.97)
                                        corners[idx] = CGPoint(x: nx, y: ny)
                                    }
                                    .onEnded { _ in
                                        activeIndex = nil
                                    }
                            )
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
                    }
                }
            }
        }
    }
}
