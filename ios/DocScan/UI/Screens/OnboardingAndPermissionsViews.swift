import SwiftUI

struct OnboardingView: View {
    let onGetStarted: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(DocScanColors.scanGlow.opacity(0.16))
                            .frame(width: 36, height: 36)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(DocScanColors.scanGlow.opacity(0.4), lineWidth: 1)
                            )
                            .overlay(
                                Image(systemName: "doc.viewfinder")
                                    .foregroundStyle(DocScanColors.scanGlow)
                            )
                        Text("DOCSCAN")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(DocScanColors.textPrimary)
                    }
                    Spacer()
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 12))
                        Text("100% Local-First Engine")
                            .font(.caption.weight(.medium))
                    }
                    .foregroundStyle(DocScanColors.scanGlow)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(DocScanColors.scanGlow.opacity(0.12))
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(DocScanColors.scanGlow.opacity(0.3), lineWidth: 1))
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Scan documents on your phone.\nPrivate by default.")
                        .font(.title.weight(.bold))
                        .foregroundStyle(DocScanColors.textPrimary)
                    Text("Fast document flattening and OCR without sending a single byte to an external server.")
                        .font(.subheadline)
                        .foregroundStyle(DocScanColors.textSecondary)
                }

                OnboardingFeatureCard(icon: "crop", badge: "Fast", title: "Auto-capture", subtitle: "Real-time boundary tracking, perspective correction & shadow removal in milliseconds.")
                OnboardingFeatureCard(icon: "doc.text.viewfinder", badge: "Offline", title: "OCR & on-device search", subtitle: "Instant multi-language text extraction. Search within receipts, contracts, and notes.")
                OnboardingFeatureCard(icon: "doc.richtext", badge: "Vectorized", title: "Export PDF & DOC", subtitle: "Generate lightweight, searchable PDF, DOCX, and JPEG archives ready to share.")
                OnboardingFeatureCard(icon: "checkmark.seal", badge: "No watermark", title: "Free Forever", subtitle: "Zero forced watermarks on exported PDFs or docs. Core offline scanning remains free.")

                HStack(spacing: 8) {
                    InfoPill(icon: "bolt.fill", label: "5 free scans / day")
                    InfoPill(icon: "icloud.slash", label: "Optional cloud sync")
                }

                Button(action: onGetStarted) {
                    HStack {
                        Text("Get started")
                            .font(.headline)
                        Image(systemName: "arrow.right")
                    }
                    .foregroundStyle(DocScanColors.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .background(DocScanColors.scanGlow)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)

                Text("No account needed. Camera permission is requested only when you start scanning.")
                    .font(.caption)
                    .foregroundStyle(DocScanColors.textMuted)
            }
            .padding(20)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

private struct OnboardingFeatureCard: View {
    let icon: String
    let badge: String
    let title: String
    let subtitle: String

    var body: some View {
        DocCardSurface {
            HStack(alignment: .top, spacing: 12) {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(DocScanColors.scanGlow.opacity(0.14))
                    .frame(width: 40, height: 40)
                    .overlay(Image(systemName: icon).foregroundStyle(DocScanColors.scanGlow))
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(DocScanColors.textPrimary)
                        Spacer()
                        Text(badge)
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundStyle(DocScanColors.tertiary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(DocScanColors.surfaceOverlay)
                            .clipShape(Capsule())
                    }
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(DocScanColors.textSecondary)
                }
            }
        }
    }
}

private struct InfoPill: View {
    let icon: String
    let label: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(DocScanColors.scanGlow)
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundStyle(DocScanColors.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .background(DocScanColors.surfaceRaised)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(DocScanColors.borderSubtle, lineWidth: 1))
    }
}

struct CameraPermissionView: View {
    let onAllowCamera: () -> Void
    let onNotNow: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Permissions Flow")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(DocScanColors.textSecondary)
                Spacer()
                Button(action: onNotNow) {
                    Image(systemName: "xmark")
                        .foregroundStyle(DocScanColors.textSecondary)
                }
            }

            Spacer()

            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(DocScanColors.scanGlow.opacity(0.16))
                .frame(width: 64, height: 64)
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(DocScanColors.scanGlow.opacity(0.4), lineWidth: 1))
                .overlay(Image(systemName: "camera.fill").font(.title2).foregroundStyle(DocScanColors.scanGlow))

            Text("Allow camera access?")
                .font(.title.weight(.bold))
                .foregroundStyle(DocScanColors.textPrimary)
            Text("DocScan needs camera access to detect document edges and scan pages in real-time. Scans are processed entirely on your device.")
                .font(.subheadline)
                .foregroundStyle(DocScanColors.textSecondary)

            LocalFirstTrustBanner(text: "100% on-device processing · No photos sent to remote servers")

            HStack(spacing: 10) {
                DocCardSurface {
                    Image(systemName: "crop").foregroundStyle(DocScanColors.scanGlow)
                    Text("Auto-Edge Quad").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                    Text("Millimeter snap").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                }
                DocCardSurface {
                    Image(systemName: "doc.text.viewfinder").foregroundStyle(DocScanColors.tertiary)
                    Text("Instant OCR").font(.subheadline.weight(.semibold)).foregroundStyle(DocScanColors.textPrimary)
                    Text("Offline text extract").font(.caption).foregroundStyle(DocScanColors.textSecondary)
                }
            }

            Spacer()

            Button(action: onAllowCamera) {
                Label("Continue", systemImage: "camera.fill")
                    .font(.headline)
                    .foregroundStyle(DocScanColors.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(DocScanColors.scanGlow)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)

            Button("Not now", action: onNotNow)
                .font(.subheadline)
                .foregroundStyle(DocScanColors.textSecondary)
                .frame(maxWidth: .infinity)
        }
        .padding(20)
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}

struct PhotoPermissionView: View {
    let onAllowAccess: () -> Void
    let onSelectManually: () -> Void
    let onNotNow: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Permissions Flow")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(DocScanColors.textSecondary)
                    Spacer()
                    Button(action: onNotNow) {
                        Image(systemName: "xmark")
                            .foregroundStyle(DocScanColors.textSecondary)
                    }
                }

                Text("Allow photo & file access?")
                    .font(.title.weight(.bold))
                    .foregroundStyle(DocScanColors.textPrimary)
                Text("DocScan securely imports existing receipts, documents, and past 7-day images directly from your storage. Nothing is ever sent out.")
                    .font(.subheadline)
                    .foregroundStyle(DocScanColors.textSecondary)

                LocalFirstTrustBanner(text: "All file parsing, perspective correction, and OCR run strictly on-device.")

                OnboardingFeatureCard(icon: "folder.fill", badge: "Direct", title: "Direct Photo & File Import", subtitle: "Select forms, receipts, invoices, and high-res images directly from your device.")
                OnboardingFeatureCard(icon: "sparkles", badge: "7 Days", title: "Smart 7-Day Auto Discovery", subtitle: "Library Suggest finds document-like snapshots taken recently, skipping selfies and scenery.")
                OnboardingFeatureCard(icon: "photo.on.rectangle.angled", badge: "Scoped", title: "Scoped & Private Access", subtitle: "Fully compatible with iOS Limited Library. You control which photos DocScan can see.")

                Button(action: onAllowAccess) {
                    Label("Allow Access", systemImage: "folder.fill")
                        .font(.headline)
                        .foregroundStyle(DocScanColors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(DocScanColors.scanGlow)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)

                Button(action: onSelectManually) {
                    Text("Select Manually")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(DocScanColors.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(DocScanColors.borderMedium, lineWidth: 1))
                }
                .buttonStyle(.plain)

                Button("Not now", action: onNotNow)
                    .font(.subheadline)
                    .foregroundStyle(DocScanColors.textSecondary)
                    .frame(maxWidth: .infinity)
            }
            .padding(20)
        }
        .background(DocScanColors.surfaceBase.ignoresSafeArea())
    }
}
