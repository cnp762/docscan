import AVFoundation
import SwiftUI

struct HomeLibraryView: View {
    @ObservedObject var state: DocScanAppState
    let onOpenImport: () -> Void

    private let categories = ["All", "Receipts", "Contracts", "Invoices", "ID & Legal"]

    private var filteredDocuments: [DocumentItem] {
        if state.selectedCategory == "All" { return state.documents }
        return state.documents.filter {
            $0.docType.localizedCaseInsensitiveContains(state.selectedCategory) ||
            $0.tags.contains(where: { $0.localizedCaseInsensitiveContains(state.selectedCategory) })
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // Header
                HStack {
                    Text("DocScan")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(DocScanColors.textPrimary)
                    Spacer()
                    QuotaChipView(
                        usedToday: state.quotaUsedToday,
                        dailyLimit: DocScanAppState.freeDailyQuota,
                        isPro: state.isPro
                    ) {
                        state.route = .paywall
                    }
                    DocCircleIconButton(
                        systemImage: "gearshape.fill",
                        accessibilityLabel: "Settings"
                    ) {
                        state.route = .settings
                    }
                }

                    if let banner = state.statusBanner {
                        Button {
                            state.statusBanner = nil
                        } label: {
                            HStack {
                                Text(banner)
                                    .font(.caption)
                                    .foregroundStyle(DocScanColors.scanGlow)
                                Spacer()
                                Image(systemName: "xmark")
                                    .font(.caption)
                                    .foregroundStyle(DocScanColors.scanGlow)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(DocScanColors.scanGlow.opacity(0.14))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(DocScanColors.scanGlow.opacity(0.4), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }

                    // Search trigger
                    Button {
                        state.route = .searchFilter
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "magnifyingglass")
                                .foregroundStyle(DocScanColors.textSecondary)
                            Text("Search OCR text, titles, or tags...")
                                .font(.subheadline)
                                .foregroundStyle(DocScanColors.textMuted)
                            Spacer()
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(DocScanColors.surfaceCard)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(DocScanColors.borderSubtle, lineWidth: 1))
                    }
                    .buttonStyle(.plain)

                    // Category chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(categories, id: \.self) { cat in
                                let selected = state.selectedCategory == cat
                                Button {
                                    state.selectedCategory = cat
                                } label: {
                                    Text(cat == "All" ? "All (\(state.documents.count))" : cat)
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(selected ? DocScanColors.scanGlow : DocScanColors.textSecondary)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(selected ? DocScanColors.scanGlow.opacity(0.18) : DocScanColors.surfaceCard)
                                        .clipShape(Capsule())
                                        .overlay(Capsule().stroke(selected ? DocScanColors.scanGlow.opacity(0.5) : DocScanColors.borderSubtle, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    // 7-day Suggested card
                    Button {
                        state.route = .librarySuggest
                    } label: {
                        DocCardSurface(borderColor: DocScanColors.scanGlow.opacity(0.28)) {
                            HStack {
                                Label("Suggested · 7 days", systemImage: "sparkles")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(DocScanColors.textPrimary)
                                Spacer()
                                HStack(spacing: 2) {
                                    Text("See all")
                                    Image(systemName: "chevron.right")
                                }
                                .font(.caption.weight(.medium))
                                .foregroundStyle(DocScanColors.scanGlow)
                            }
                            Text("Detected \(state.suggestCandidates.filter(\.isDocument).count) unfiled paper items from your camera roll")
                                .font(.caption)
                                .foregroundStyle(DocScanColors.textSecondary)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(state.suggestCandidates.filter(\.isDocument)) { item in
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(item.category)
                                                .font(.caption2.weight(.medium).monospaced())
                                                .foregroundStyle(DocScanColors.tertiary)
                                            Text(item.title)
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(DocScanColors.textPrimary)
                                                .lineLimit(1)
                                            Text(item.subtitle)
                                                .font(.caption)
                                                .foregroundStyle(DocScanColors.textSecondary)
                                                .lineLimit(1)
                                        }
                                        .frame(width: 155, alignment: .leading)
                                        .padding(10)
                                        .background(DocScanColors.surfaceRaised)
                                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(DocScanColors.borderSubtle, lineWidth: 1))
                                    }
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)

                    // Recent Documents
                    HStack {
                        Text("Recent Documents (\(filteredDocuments.count))")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(DocScanColors.textPrimary)
                        Spacer()
                        Text("Newest first")
                            .font(.caption)
                            .foregroundStyle(DocScanColors.textSecondary)
                    }

                    if filteredDocuments.isEmpty {
                        DocCardSurface {
                            Text("No documents yet")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(DocScanColors.textPrimary)
                            Text("Tap Scan to capture a document or Import to pick existing photos.")
                                .font(.caption)
                                .foregroundStyle(DocScanColors.textSecondary)
                        }
                    } else {
                        ForEach(filteredDocuments) { doc in
                            Button {
                                state.openDocument(doc.id)
                            } label: {
                                DocCardSurface {
                                    HStack(spacing: 12) {
                                        DocPageThumbnailView(imagePath: doc.imagePath, iconFont: .title3, maxDimension: 256)
                                            .frame(width: 48, height: 64)
                                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                                            .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous).stroke(DocScanColors.borderSubtle, lineWidth: 1))
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(doc.name)
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(DocScanColors.textPrimary)
                                                .lineLimit(1)
                                            Text("\(doc.docType) · \(doc.pageCount) pg · OCR ready")
                                                .font(.caption)
                                                .foregroundStyle(DocScanColors.textSecondary)
                                            if !doc.tags.isEmpty {
                                                HStack(spacing: 6) {
                                                    ForEach(doc.tags.prefix(3), id: \.self) { tag in
                                                        Text("#\(tag)")
                                                            .font(.caption2.monospaced())
                                                            .foregroundStyle(DocScanColors.tertiary)
                                                            .padding(.horizontal, 6)
                                                            .padding(.vertical, 2)
                                                            .background(DocScanColors.surfaceOverlay)
                                                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                                                    }
                                                }
                                            }
                                        }
                                        Spacer()
                                        Image(systemName: "ellipsis")
                                            .foregroundStyle(DocScanColors.textSecondary)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 12)
            }
            .safeAreaInset(edge: .bottom) {
                // Free AdMob banner + 3-tab bottom bar (Search lives in Library header)
                VStack(spacing: 0) {
                    FreeAdBannerSlot(isPro: state.isPro)
                    HStack {
                        FloatingTabButton(icon: "folder.fill", label: "Library", selected: state.activeTab == .library) {
                            state.activeTab = .library
                            state.route = .homeLibrary
                        }
                        FloatingTabButton(icon: "doc.viewfinder", label: "Scan", selected: state.activeTab == .scan, highlight: true) {
                            state.activeTab = .scan
                            state.startNewCaptureSession()
                        }
                        FloatingTabButton(icon: "photo.on.rectangle", label: "Import", selected: state.activeTab == .import) {
                            state.activeTab = .import
                            onOpenImport()
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(.ultraThinMaterial)
                    .background(DocScanColors.surfaceOverlay.opacity(0.85))
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(DocScanColors.borderMedium, lineWidth: 1))
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                }
            }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

private struct FloatingTabButton: View {
    let icon: String
    let label: String
    let selected: Bool
    var highlight: Bool = false
    let action: () -> Void

    var body: some View {
        let tint: Color = highlight ? DocScanColors.scanGlow : (selected ? DocScanColors.primary : DocScanColors.textSecondary)
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 18))
                Text(label).font(.caption2.weight(.medium))
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

struct LibrarySuggestView: View {
    @ObservedObject var state: DocScanAppState
    let onBack: () -> Void

    private var selectedCount: Int {
        state.suggestCandidates.filter(\.selected).count
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                DocCircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back", action: onBack)
                VStack(alignment: .leading) {
                    Text("Library Suggest").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                    Text("Last 7 days · On-device").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                }
                Spacer()
            }
            .padding(16)

            ScrollView {
                VStack(spacing: 12) {
                    LocalFirstTrustBanner(text: "Photos stay on your phone. Select the ones that look like documents.")

                    HStack {
                        Button {
                            for i in state.suggestCandidates.indices {
                                state.suggestCandidates[i].selected = state.suggestCandidates[i].isDocument
                            }
                        } label: {
                            Label("Select all docs (\(state.suggestCandidates.filter(\.isDocument).count))", systemImage: "checkmark")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(DocScanColors.scanGlow)
                        }
                        Spacer()
                        Text("3 suggested")
                            .font(.caption)
                            .foregroundStyle(DocScanColors.textSecondary)
                    }

                    ForEach($state.suggestCandidates) { $item in
                        Button {
                            item.selected.toggle()
                        } label: {
                            DocCardSurface(borderColor: item.selected ? DocScanColors.scanGlow.opacity(0.5) : DocScanColors.borderSubtle) {
                                HStack(spacing: 12) {
                                    Image(systemName: item.isDocument ? "doc.text.fill" : "photo")
                                        .foregroundStyle(item.isDocument ? DocScanColors.scanGlow : DocScanColors.textMuted)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(item.category) · \(item.confidencePercent)%")
                                            .font(.caption2.monospaced())
                                            .foregroundStyle(DocScanColors.tertiary)
                                        Text(item.title)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(DocScanColors.textPrimary)
                                        Text(item.subtitle)
                                            .font(.caption)
                                            .foregroundStyle(DocScanColors.textSecondary)
                                    }
                                    Spacer()
                                    Image(systemName: item.selected ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(item.selected ? DocScanColors.scanGlow : DocScanColors.textMuted)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }

            VStack(spacing: 10) {
                HStack {
                    Text("\(selectedCount) document(s) selected")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(DocScanColors.textPrimary)
                    Spacer()
                    Text(state.isPro ? "Pro · Unlimited" : "Uses \(selectedCount)/\(DocScanAppState.freeDailyQuota) daily quota")
                        .font(.caption2.monospaced())
                        .foregroundStyle(DocScanColors.scanGlow)
                }
                Button {
                    state.importSelectedSuggestions()
                } label: {
                    Text("Import & Scan (\(selectedCount))")
                        .font(.headline)
                        .foregroundStyle(DocScanColors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(selectedCount > 0 ? DocScanColors.scanGlow : DocScanColors.surfaceOverlay)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .disabled(selectedCount == 0)
            }
            .padding(16)
            .background(DocScanColors.surfaceRaised)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
        .onAppear {
            if state.photoPermissionGranted && state.suggestCandidates.isEmpty {
                state.refreshLibrarySuggestions()
            }
        }
    }
}

private struct CameraPreviewRepresentable: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {
        uiView.previewLayer.session = session
    }

    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

struct CameraCaptureView: View {
    @ObservedObject var state: DocScanAppState
    let onOpenGallery: () -> Void
    let onBack: () -> Void

    @StateObject private var captureController = CameraCaptureController()
    @State private var corners: [CGPoint] = DraftPageItem.defaultQuad()
    @State private var flashOn = false
    @State private var isCapturing = false
    @State private var lastAutoCaptureAt: TimeInterval = 0

    private func triggerCapture() {
        guard state.cameraPermissionGranted, !isCapturing else { return }
        isCapturing = true
        Task {
            let image = await captureController.capturePhoto()
            isCapturing = false
            if let image {
                state.addCapturedImage(image, corners: corners)
                if !state.isBatchMode {
                    state.route = .reviewEdit
                }
            }
        }
    }

    var body: some View {
        ZStack {
            if state.cameraPermissionGranted {
                CameraPreviewRepresentable(session: captureController.session)
                    .ignoresSafeArea()
                    .onAppear {
                        try? captureController.configureAndStart()
                    }
                    .onDisappear {
                        captureController.stop()
                    }
                    .onChange(of: captureController.detectedQuad) { _, detected in
                        if let detected, detected.count == 4 {
                            corners = detected
                        }
                        let now = CACurrentMediaTime()
                        if state.autoCapture,
                           captureController.isStable,
                           !captureController.isLowLight,
                           !isCapturing,
                           now - lastAutoCaptureAt >= 2.0 {
                            lastAutoCaptureAt = now
                            triggerCapture()
                        }
                    }
                    .onChange(of: flashOn) { _, newVal in
                        captureController.setFlashEnabled(newVal)
                    }
            } else {
                DocScanColors.surfaceBase.ignoresSafeArea()
                Text("Camera preview ready after permission")
                    .font(.subheadline)
                    .foregroundStyle(DocScanColors.textSecondary)
            }

            DocScanColors.surfaceBase.opacity(0.28).ignoresSafeArea()

            InteractiveQuadOverlay(corners: $corners)
                .padding(.top, 80)
                .padding(.bottom, 180)

            VStack {
                HStack {
                    DocCircleIconButton(
                        systemImage: "chevron.left",
                        accessibilityLabel: "Back",
                        visualSize: 40,
                        background: DocScanColors.surfaceRaised.opacity(0.85),
                        action: onBack
                    )
                    Spacer()
                    QuotaChipView(usedToday: state.quotaUsedToday, dailyLimit: DocScanAppState.freeDailyQuota, isPro: state.isPro) {
                        state.route = .paywall
                    }
                    Spacer()
                    DocCircleIconButton(
                        systemImage: flashOn ? "bolt.fill" : "bolt.slash",
                        accessibilityLabel: "Flash",
                        visualSize: 40,
                        foreground: flashOn ? DocScanColors.warningAmber : DocScanColors.textPrimary,
                        background: DocScanColors.surfaceRaised.opacity(0.85)
                    ) {
                        flashOn.toggle()
                    }
                }
                .padding(.horizontal, 16)

                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(captureController.isLowLight ? DocScanColors.warningAmber : DocScanColors.scanGlow)
                    Text(captureController.guidanceMessage)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(DocScanColors.textPrimary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(DocScanColors.surfaceRaised.opacity(0.88))
                .clipShape(Capsule())
                .overlay(Capsule().stroke(DocScanColors.scanGlow.opacity(0.45), lineWidth: 1))

                Spacer()

                VStack(spacing: 12) {
                    HStack(spacing: 8) {
                        Button("Single") { state.isBatchMode = false }
                            .font(.caption.weight(.medium))
                            .foregroundStyle(!state.isBatchMode ? DocScanColors.scanGlow : DocScanColors.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(!state.isBatchMode ? DocScanColors.scanGlow.opacity(0.2) : Color.clear)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(!state.isBatchMode ? DocScanColors.scanGlow : DocScanColors.borderSubtle, lineWidth: 1))

                        Button("Batch") { state.isBatchMode = true }
                            .font(.caption.weight(.medium))
                            .foregroundStyle(state.isBatchMode ? DocScanColors.scanGlow : DocScanColors.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(state.isBatchMode ? DocScanColors.scanGlow.opacity(0.2) : Color.clear)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(state.isBatchMode ? DocScanColors.scanGlow : DocScanColors.borderSubtle, lineWidth: 1))
                    }

                    HStack {
                        Button(action: onOpenGallery) {
                            Image(systemName: "photo.on.rectangle")
                                .foregroundStyle(DocScanColors.textPrimary)
                                .frame(width: 48, height: 48)
                                .background(DocScanColors.surfaceOverlay)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        Spacer()
                        Button {
                            triggerCapture()
                        } label: {
                            Circle()
                                .stroke(DocScanColors.scanGlow, lineWidth: 2)
                                .frame(width: 74, height: 74)
                                .overlay(Circle().fill(isCapturing ? DocScanColors.textMuted : Color.white).frame(width: 56, height: 56))
                        }
                        .disabled(isCapturing)
                        .buttonStyle(ScalePressButtonStyle())
                        Spacer()
                        Button {
                            if !state.draftPages.isEmpty {
                                state.route = .reviewEdit
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Text("\(state.draftPages.count) pgs")
                                Image(systemName: "arrow.right")
                            }
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(state.draftPages.isEmpty ? DocScanColors.textSecondary : DocScanColors.onPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(state.draftPages.isEmpty ? DocScanColors.surfaceOverlay : DocScanColors.scanGlow)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                    }
                }
                .padding(20)
                .background(DocScanColors.surfaceRaised.opacity(0.92))
            }
        }
    }
}

struct ReviewEditView: View {
    @ObservedObject var state: DocScanAppState
    let onAddMore: () -> Void
    let onDiscard: () -> Void
    @State private var isDraggingQuad = false

    private var activeIndex: Int {
        min(max(state.activeDraftIndex, 0), max(state.draftPages.count - 1, 0))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                DocCircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back", action: onDiscard)
                VStack(alignment: .leading) {
                    Text("Perspective Crop").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                    if !state.draftPages.isEmpty {
                        Text("Page \(activeIndex + 1) of \(state.draftPages.count) · Quad Active")
                            .font(.caption)
                            .foregroundStyle(DocScanColors.textSecondary)
                    }
                }
                Spacer()
                QuotaChipView(usedToday: state.quotaUsedToday, dailyLimit: DocScanAppState.freeDailyQuota, isPro: state.isPro) {
                    state.route = .paywall
                }
            }
            .padding(16)

            ScrollView {
                VStack(spacing: 14) {
                    // Filmstrip
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(state.draftPages.enumerated()), id: \.element.id) { idx, page in
                                let selected = idx == activeIndex
                                Button {
                                    state.activeDraftIndex = idx
                                } label: {
                                    ZStack {
                                        DocPageThumbnailView(imagePath: page.imagePath, iconFont: .caption, maxDimension: 200)
                                        DocScanColors.surfaceBase.opacity(0.35)
                                        VStack {
                                            Text("p\(idx + 1)")
                                                .font(.caption2.monospaced())
                                                .foregroundStyle(selected ? DocScanColors.scanGlow : DocScanColors.textPrimary)
                                            Spacer()
                                            Text(selected ? "Edit" : "Ready")
                                                .font(.caption2.monospaced())
                                                .foregroundStyle(DocScanColors.textSecondary)
                                        }
                                        .padding(6)
                                    }
                                    .frame(width: 56, height: 72)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .stroke(selected ? DocScanColors.scanGlow : DocScanColors.borderSubtle, lineWidth: selected ? 2 : 1)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                            Button(action: onAddMore) {
                                VStack(spacing: 4) {
                                    Image(systemName: "camera.fill").foregroundStyle(DocScanColors.scanGlow)
                                    Text("+ Add").font(.caption2).foregroundStyle(DocScanColors.scanGlow)
                                }
                                .frame(width: 56, height: 72)
                                .background(DocScanColors.surfaceRaised)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(DocScanColors.borderMedium, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if activeIndex < state.draftPages.count {
                        ZStack {
                            DocPageThumbnailView(
                                imagePath: state.draftPages[activeIndex].imagePath,
                                fallbackIcon: "doc.viewfinder",
                                iconFont: .largeTitle,
                                maxDimension: 1024,
                                enhancePreset: {
                                    switch state.draftPages[activeIndex].enhanceMode {
                                    case .original: return .original
                                    case .color: return .color
                                    case .gray: return .gray
                                    case .bw: return .bw
                                    case .docClean: return .document
                                    case .deshadow: return .deshadow
                                    }
                                }(),
                                rotationDegrees: state.draftPages[activeIndex].rotationDegrees
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(DocScanColors.borderSubtle, lineWidth: 1))

                            if state.draftPages[activeIndex].imagePath.isEmpty {
                                VStack(spacing: 6) {
                                    Spacer()
                                    Text(state.draftPages[activeIndex].title)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(DocScanColors.textPrimary)
                                    Text("Drag corners for 4× loupe · Rotated \(state.draftPages[activeIndex].rotationDegrees)°")
                                        .font(.caption)
                                        .foregroundStyle(DocScanColors.textSecondary)
                                }
                                .padding(14)
                            }

                            InteractiveQuadOverlay(
                                corners: $state.draftPages[activeIndex].corners,
                                onDragStateChange: { isDraggingQuad = $0 }
                            )
                        }
                        .frame(height: 320)

                        HStack(spacing: 8) {
                            Button("Rotate 90°") {
                                state.draftPages[activeIndex].rotationDegrees = (state.draftPages[activeIndex].rotationDegrees + 90) % 360
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(DocScanColors.borderMedium, lineWidth: 1))

                            Button("Reset Quad") {
                                state.draftPages[activeIndex].corners = DraftPageItem.defaultQuad()
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(DocScanColors.borderMedium, lineWidth: 1))

                            Button("Full Bleed") {
                                state.draftPages[activeIndex].corners = [
                                    CGPoint(x: 0.03, y: 0.03),
                                    CGPoint(x: 0.97, y: 0.03),
                                    CGPoint(x: 0.97, y: 0.97),
                                    CGPoint(x: 0.03, y: 0.97),
                                ]
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(DocScanColors.borderMedium, lineWidth: 1))
                        }
                        .font(.caption.weight(.medium))
                        .foregroundStyle(DocScanColors.textPrimary)
                    }
                }
                .padding(16)
            }
            .scrollDisabled(isDraggingQuad)

            VStack(spacing: 12) {
                if activeIndex < state.draftPages.count {
                    FilterMatrixBar(selected: $state.draftPages[activeIndex].enhanceMode)
                }

                HStack(spacing: 10) {
                    Button(action: onDiscard) {
                        Text("Discard")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(DocScanColors.dangerCrimson)
                            .padding(.horizontal, 18)
                            .frame(height: 50)
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(DocScanColors.dangerCrimson.opacity(0.5), lineWidth: 1))
                    }
                    Button {
                        _ = state.commitDraftPages()
                    } label: {
                        Text(state.editingDocumentId != nil ? "Save Changes (Free)" : "Save \(state.draftPages.count) Page(s)")
                            .font(.headline)
                            .foregroundStyle(DocScanColors.onPrimary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(DocScanColors.scanGlow)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .disabled(state.draftPages.isEmpty)
                }

                Text("No cloud upload. Digitized entirely on device.")
                    .font(.caption2)
                    .foregroundStyle(DocScanColors.textMuted)
            }
            .padding(16)
            .background(DocScanColors.surfaceRaised)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}
