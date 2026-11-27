import SwiftUI

struct DocumentDetailView: View {
    @ObservedObject var state: DocScanAppState
    let onBack: () -> Void

    @State private var showAddTag = false
    @State private var newTagText = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(DocScanColors.textPrimary)
                        .frame(width: 36, height: 36)
                        .background(DocScanColors.surfaceCard)
                        .clipShape(Circle())
                }
                if let doc = state.selectedDocument {
                    VStack(alignment: .leading) {
                        Text(doc.name).font(.headline).foregroundStyle(DocScanColors.textPrimary).lineLimit(1)
                        Text("\(doc.docType) · \(doc.pageCount) page(s)").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                    }
                }
                Spacer()
            }
            .padding(16)

            if let doc = state.selectedDocument {
                ScrollView {
                    VStack(spacing: 14) {
                        DocCardSurface(backgroundColor: DocScanColors.surfaceRaised) {
                            VStack(spacing: 6) {
                                Image(systemName: "doc.text.fill")
                                    .font(.largeTitle)
                                    .foregroundStyle(DocScanColors.scanGlow)
                                Text(doc.name)
                                    .font(.headline)
                                    .foregroundStyle(DocScanColors.textPrimary)
                                Text("Ready for export · Private on this phone")
                                    .font(.caption)
                                    .foregroundStyle(DocScanColors.textSecondary)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 150)
                            .background(DocScanColors.surfaceCard)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }

                        DocCardSurface {
                            Label("Document Summary", systemImage: "sparkles")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(DocScanColors.textPrimary)
                            Text(doc.summary)
                                .font(.subheadline)
                                .foregroundStyle(DocScanColors.textSecondary)
                        }

                        OcrInspectionCard(
                            text: doc.ocrText,
                            languageLabel: "Language: \(doc.ocrLang.uppercased()) · Confidence \(Int(doc.ocrConfidence * 100))%"
                        )

                        DocCardSurface {
                            HStack {
                                Text("Categorization & Tags")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(DocScanColors.textPrimary)
                                Spacer()
                                Button {
                                    showAddTag = true
                                } label: {
                                    Label("Add tag", systemImage: "plus")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(DocScanColors.scanGlow)
                                }
                            }
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(doc.tags, id: \.self) { tag in
                                        Button {
                                            state.removeTagFromSelected(tag)
                                        } label: {
                                            HStack(spacing: 4) {
                                                Text("#\(tag)")
                                                Image(systemName: "xmark").font(.caption2)
                                            }
                                            .font(.caption.weight(.medium))
                                            .foregroundStyle(DocScanColors.tertiary)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(DocScanColors.surfaceOverlay)
                                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }

                        HStack(spacing: 8) {
                            ActionGridCard(icon: "slider.horizontal.3", title: "Edit Scan", subtitle: "No quota") {
                                state.editExistingDocument(doc)
                            }
                            ActionGridCard(icon: "square.and.arrow.up", title: "Export", subtitle: "PDF · DOCX") {
                                state.route = .exportShare
                            }
                        }
                        HStack(spacing: 8) {
                            ActionGridCard(icon: "icloud.and.arrow.up", title: "Backup", subtitle: "Drive, iCloud & Photos") {
                                state.route = .backup
                            }
                            ShareLink(item: doc.ocrText) {
                                DocCardSurface {
                                    HStack(spacing: 10) {
                                        Image(systemName: "paperplane.fill").foregroundStyle(DocScanColors.scanGlow)
                                        VStack(alignment: .leading) {
                                            Text("Share").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                            Text("System sheet").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                                        }
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
            }

            FreeAdBannerSlot(visible: !state.isPro)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
        .alert("Add Tag", isPresented: $showAddTag) {
            TextField("e.g. tax 2025", text: $newTagText)
            Button("Add") {
                state.addTagToSelected(newTagText)
                newTagText = ""
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

private struct ActionGridCard: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            DocCardSurface {
                HStack(spacing: 10) {
                    Image(systemName: icon).foregroundStyle(DocScanColors.scanGlow)
                    VStack(alignment: .leading) {
                        Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                        Text(subtitle).font(.caption).foregroundStyle(DocScanColors.textSecondary)
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }
}

struct SearchFilterView: View {
    @ObservedObject var state: DocScanAppState
    let onBack: () -> Void

    private var results: [DocumentItem] {
        let q = state.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return state.documents.filter { doc in
            let tagMatch = !state.searchOnlyTagged || !doc.tags.isEmpty
            let queryMatch = q.isEmpty ||
                doc.name.lowercased().contains(q) ||
                doc.ocrText.lowercased().contains(q) ||
                doc.tags.contains { $0.lowercased().contains(q) }
            return tagMatch && queryMatch
        }
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(DocScanColors.textPrimary)
                        .frame(width: 36, height: 36)
                        .background(DocScanColors.surfaceCard)
                        .clipShape(Circle())
                }
                Text("Search").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(DocScanColors.scanGlow)
                TextField("Search OCR text, invoices, tags...", text: $state.searchQuery)
                    .foregroundStyle(DocScanColors.textPrimary)
                if !state.searchQuery.isEmpty {
                    Button { state.searchQuery = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(DocScanColors.textSecondary)
                    }
                }
            }
            .padding(12)
            .background(DocScanColors.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(DocScanColors.borderSubtle, lineWidth: 1))
            .padding(.horizontal, 16)

            HStack {
                Button {
                    state.searchOnlyTagged.toggle()
                } label: {
                    Text("Has Tag")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(state.searchOnlyTagged ? DocScanColors.scanGlow : DocScanColors.textSecondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(state.searchOnlyTagged ? DocScanColors.scanGlow.opacity(0.18) : DocScanColors.surfaceCard)
                        .clipShape(Capsule())
                }
                Spacer()
                Text("\(results.count) result(s)")
                    .font(.caption)
                    .foregroundStyle(DocScanColors.textSecondary)
            }
            .padding(.horizontal, 16)

            ScrollView {
                VStack(spacing: 10) {
                    ForEach(results) { doc in
                        Button {
                            state.openDocument(doc.id)
                        } label: {
                            DocCardSurface {
                                Text(doc.name).font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                Text(doc.ocrText.replacingOccurrences(of: "\n", with: " · "))
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundStyle(DocScanColors.tertiary)
                                    .lineLimit(2)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }

            FreeAdBannerSlot(visible: !state.isPro)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

struct ExportShareView: View {
    @ObservedObject var state: DocScanAppState
    let onBack: () -> Void
    @State private var pageSelection = "All pages"

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(DocScanColors.textPrimary)
                        .frame(width: 36, height: 36)
                        .background(DocScanColors.surfaceCard)
                        .clipShape(Circle())
                }
                VStack(alignment: .leading) {
                    Text("Export & Share").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                    Text(state.selectedDocument?.name ?? "Document").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                }
                Spacer()
            }
            .padding(16)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Export Format").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                    ForEach(ExportFormatOption.allCases) { fmt in
                        let selected = state.exportFormat == fmt
                        Button {
                            state.exportFormat = fmt
                        } label: {
                            DocCardSurface(borderColor: selected ? DocScanColors.scanGlow : DocScanColors.borderSubtle) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(fmt.title).font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                        Text(fmt.subtitle).font(.caption).foregroundStyle(DocScanColors.textSecondary)
                                    }
                                    Spacer()
                                    if selected {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(DocScanColors.scanGlow)
                                    }
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }

                    Text("Output Quality").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                    HStack(spacing: 8) {
                        ForEach(ExportQualityOption.allCases) { q in
                            let selected = state.exportQuality == q
                            Button {
                                state.exportQuality = q
                            } label: {
                                DocCardSurface(borderColor: selected ? DocScanColors.scanGlow : DocScanColors.borderSubtle) {
                                    Text(q.rawValue).font(.caption.weight(.semibold)).foregroundStyle(selected ? DocScanColors.scanGlow : DocScanColors.textPrimary)
                                    Text(q.estSize).font(.system(size: 11, design: .monospaced)).foregroundStyle(DocScanColors.textSecondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    DocCardSurface(borderColor: state.exportEncryptPassword ? DocScanColors.scanGlow.opacity(0.5) : DocScanColors.borderSubtle) {
                        Toggle(isOn: $state.exportEncryptPassword) {
                            VStack(alignment: .leading) {
                                Text("Encrypt with Password").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                Text("Optional password · Stored only on this device").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                            }
                        }
                        .tint(DocScanColors.scanGlow)

                        if state.exportEncryptPassword {
                            SecureField("Document password", text: $state.exportPassword)
                                .textFieldStyle(.roundedBorder)
                        }
                    }
                }
                .padding(16)
            }

            VStack(spacing: 10) {
                ShareLink(item: state.selectedDocument?.ocrText ?? "DocScan Export") {
                    Label("Share & Export \(state.exportFormat.rawValue.uppercased())", systemImage: "square.and.arrow.up")
                        .font(.headline)
                        .foregroundStyle(DocScanColors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(DocScanColors.scanGlow)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                Button {
                    state.statusBanner = "Print job queued on-device"
                } label: {
                    Label("Print Document", systemImage: "printer")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(DocScanColors.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(DocScanColors.borderMedium, lineWidth: 1))
                }
            }
            .padding(16)
            .background(DocScanColors.surfaceRaised)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

struct BackupView: View {
    @ObservedObject var state: DocScanAppState
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(DocScanColors.textPrimary)
                        .frame(width: 36, height: 36)
                        .background(DocScanColors.surfaceCard)
                        .clipShape(Circle())
                }
                VStack(alignment: .leading) {
                    Text("Backup & Sync").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                    Text(state.lastSyncMessage).font(.caption).foregroundStyle(DocScanColors.textSecondary)
                }
                Spacer()
                Button("Sync All") { state.lastSyncMessage = "Images synced just now" }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(DocScanColors.scanGlow)
            }
            .padding(16)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    LocalFirstTrustBanner(text: "Backup sends page images only to accounts you connect. We never send your scans to DocScan.")

                    Text("Connected Clouds").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)

                    DocCardSurface(borderColor: state.googleDriveConnected ? DocScanColors.scanGlow.opacity(0.45) : DocScanColors.borderSubtle) {
                        Toggle(isOn: $state.googleDriveConnected) {
                            VStack(alignment: .leading) {
                                Text("Google Drive").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                Text(state.googleDriveConnected ? "Connected · /DocScan/Images/" : "Not connected · Images only")
                                    .font(.caption).foregroundStyle(DocScanColors.textSecondary)
                            }
                        }
                        .tint(DocScanColors.scanGlow)
                    }

                    DocCardSurface(borderColor: state.iCloudDriveConnected ? DocScanColors.scanGlow.opacity(0.45) : DocScanColors.borderSubtle) {
                        Toggle(isOn: $state.iCloudDriveConnected) {
                            VStack(alignment: .leading) {
                                Text("iCloud Drive").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                Text(state.iCloudDriveConnected ? "Connected · Files.app/DocScan (Images)" : "Off · Images only")
                                    .font(.caption).foregroundStyle(DocScanColors.textSecondary)
                            }
                        }
                        .tint(DocScanColors.scanGlow)
                    }

                    DocCardSurface(borderColor: state.googlePhotosConnected ? DocScanColors.scanGlow.opacity(0.45) : DocScanColors.borderSubtle) {
                        Toggle(isOn: $state.googlePhotosConnected) {
                            VStack(alignment: .leading) {
                                Text("Google Photos").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                                Text("Dedicated ‘DocScan’ album (JPEGs only)")
                                    .font(.caption).foregroundStyle(DocScanColors.textSecondary)
                            }
                        }
                        .tint(DocScanColors.scanGlow)
                    }

                    DocCardSurface {
                        Toggle("Sync over Wi-Fi only", isOn: $state.backupWifiOnly)
                            .foregroundStyle(DocScanColors.textPrimary)
                            .tint(DocScanColors.scanGlow)
                    }
                }
                .padding(16)
            }
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

/// Go Pro & Quota Paywall: Content of Stitch v2 + Style of Stitch v1
struct PaywallView: View {
    @ObservedObject var state: DocScanAppState
    let onClose: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .foregroundStyle(DocScanColors.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(DocScanColors.surfaceCard)
                            .clipShape(Circle())
                    }
                    Text("Go Pro").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                    Spacer()
                    Text("DOCSCAN PRO")
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundStyle(DocScanColors.scanGlow)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(DocScanColors.scanGlow.opacity(0.16))
                        .clipShape(Capsule())
                }

                Text("Unlimited scans")
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(DocScanColors.textPrimary)
                Text("You've used \(state.quotaUsedToday)/\(DocScanAppState.freeDailyQuota) free scans today. Go Pro for unlimited page saves and ad-free scanning.")
                    .font(.subheadline)
                    .foregroundStyle(DocScanColors.textSecondary)

                DocCardSurface(borderColor: DocScanColors.scanGlow.opacity(0.3)) {
                    Text("WHAT'S INCLUDED")
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundStyle(DocScanColors.scanGlow)
                    PaywallBenefit(icon: "infinity", title: "Unlimited scans", subtitle: "No 5/day limit while Pro is active")
                    PaywallBenefit(icon: "doc.viewfinder", title: "Continuous capture", subtitle: "Keep scanning when free daily quota would block you")
                    PaywallBenefit(icon: "bolt.fill", title: "Faster background processing", subtitle: "OCR and page naming finish in the queue")
                    PaywallBenefit(icon: "heart.fill", title: "Support independent development", subtitle: "Keeps DocScan private, ad-free, and tracker-free")
                }

                ForEach(ProPlanOption.allCases) { plan in
                    let selected = state.selectedPlan == plan
                    Button {
                        state.selectedPlan = plan
                    } label: {
                        DocCardSurface(
                            borderColor: selected ? DocScanColors.scanGlow : DocScanColors.borderSubtle,
                            backgroundColor: selected ? DocScanColors.surfaceOverlay : DocScanColors.surfaceCard
                        ) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    if let badge = plan.badge {
                                        Text(badge)
                                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                                            .foregroundStyle(DocScanColors.scanGlow)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 2)
                                            .background(DocScanColors.scanGlow.opacity(0.16))
                                            .clipShape(Capsule())
                                    }
                                    Text(plan.title).font(.headline).foregroundStyle(DocScanColors.textPrimary)
                                    Text(plan.perMonth).font(.caption).foregroundStyle(DocScanColors.textSecondary)
                                }
                                Spacer()
                                Text(plan.price).font(.headline).foregroundStyle(DocScanColors.scanGlow)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }

                Button {
                    state.activatePro(state.selectedPlan)
                } label: {
                    Label("Continue with \(state.selectedPlan.title)", systemImage: "lock.open.fill")
                        .font(.headline)
                        .foregroundStyle(DocScanColors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(DocScanColors.scanGlow)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)

                Button("Restore Purchases") {
                    state.statusBanner = "Checked App Store purchases"
                }
                .font(.caption)
                .foregroundStyle(DocScanColors.textSecondary)
                .frame(maxWidth: .infinity)

                LocalFirstTrustBanner(text: "100% private: We never send your documents or their contents to DocScan. Backup only if you connect an account.")
            }
            .padding(16)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

private struct PaywallBenefit: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(DocScanColors.scanGlow).frame(width: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                Text(subtitle).font(.caption).foregroundStyle(DocScanColors.textSecondary)
            }
        }
    }
}

/// Settings & Preferences: Content of Stitch v2 + Style of Stitch v1, version 1.0.0
struct SettingsView: View {
    @ObservedObject var state: DocScanAppState
    let onBack: () -> Void

    @State private var showClearAlert = false
    @State private var showDeleteAlert = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .foregroundStyle(DocScanColors.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(DocScanColors.surfaceCard)
                            .clipShape(Circle())
                    }
                    Text("Settings").font(.headline).foregroundStyle(DocScanColors.textPrimary)
                    Spacer()
                }

                DocCardSurface(borderColor: DocScanColors.scanGlow.opacity(0.35)) {
                    Text(state.isPro ? "DocScan Pro" : "DocScan Free")
                        .font(.headline)
                        .foregroundStyle(DocScanColors.textPrimary)
                    Text("Documents never leave your phone")
                        .font(.caption)
                        .foregroundStyle(DocScanColors.textSecondary)
                    Text(state.isPro ? "Daily scans · Unlimited" : "Daily scans · \(state.quotaUsedToday) / \(DocScanAppState.freeDailyQuota) today")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(DocScanColors.scanGlow)
                    if !state.isPro {
                        Text("Resets at midnight UTC · \(state.remainingFreeQuota) pages remaining")
                            .font(.caption)
                            .foregroundStyle(DocScanColors.textSecondary)
                        Button {
                            state.route = .paywall
                        } label: {
                            Label("Upgrade to Pro", systemImage: "star.fill")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(DocScanColors.onPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .background(DocScanColors.scanGlow)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    Button("Restore purchases") { state.statusBanner = "Restored App Store purchases" }
                        .font(.subheadline)
                        .foregroundStyle(DocScanColors.textPrimary)
                }

                Text("PREFERENCES")
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundStyle(DocScanColors.textMuted)
                DocCardSurface {
                    Toggle("Auto-capture", isOn: $state.autoCaptureEnabled).tint(DocScanColors.scanGlow)
                    Toggle("Auto-rename from suggestion", isOn: $state.autoRenameEnabled).tint(DocScanColors.scanGlow)
                    Toggle("Background processing", isOn: $state.backgroundProcessingEnabled).tint(DocScanColors.scanGlow)
                    Toggle("Notifications", isOn: $state.notificationsEnabled).tint(DocScanColors.scanGlow)
                }
                .foregroundStyle(DocScanColors.textPrimary)

                Text("BACKUP")
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundStyle(DocScanColors.textMuted)
                Button { state.route = .backup } label: {
                    DocCardSurface {
                        HStack {
                            Text("Google Drive · iCloud Drive · Google Photos (Images)")
                                .font(.subheadline)
                                .foregroundStyle(DocScanColors.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(DocScanColors.textSecondary)
                        }
                    }
                }
                .buttonStyle(.plain)

                LocalFirstTrustBanner(text: "100% private: We never send your documents or their contents to DocScan. Backup only runs if you connect an account.")

                Text("DATA")
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundStyle(DocScanColors.textMuted)
                DocCardSurface {
                    Button("Clear local library") { showClearAlert = true }
                        .foregroundStyle(DocScanColors.textPrimary)
                    Divider().background(DocScanColors.borderSubtle)
                    Button("Delete account & data") { showDeleteAlert = true }
                        .foregroundStyle(DocScanColors.dangerCrimson)
                }

                Text("DocScan 1.0.0 · cam.docscan · We never upload your scans")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(DocScanColors.textMuted)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding(16)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
        .alert("Clear local library?", isPresented: $showClearAlert) {
            Button("Clear library", role: .destructive) { state.clearLocalLibrary() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently deletes documents and OCR text stored on this phone. Files already backed up to Drive, Photos, or iCloud are not removed.")
        }
        .alert("Delete account & data?", isPresented: $showDeleteAlert) {
            Button("Delete everything", role: .destructive) { state.deleteAccountAndData() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes documents on this phone and DocScan server data tied to this device (quota and device ID).")
        }
    }
}
