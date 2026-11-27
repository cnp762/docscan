import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @StateObject private var state = DocScanAppState()
    @State private var showPhotoPicker = false
    @State private var showFileImporter = false
    @State private var pickedItems: [PhotosPickerItem] = []
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var canSwipeBack: Bool {
        switch state.route {
        case .onboarding, .homeLibrary, .capture:
            return false
        default:
            return true
        }
    }

    private func handleBackGesture() {
        switch state.route {
        case .exportShare:
            state.route = .docDetail
        case .reviewEdit:
            if state.editingDocumentId != nil {
                state.draftPages = []
                state.editingDocumentId = nil
                state.route = .docDetail
            } else {
                state.draftPages = []
                state.route = .homeLibrary
                state.activeTab = .library
            }
        default:
            state.route = .homeLibrary
            state.activeTab = .library
        }
    }

    var body: some View {
        Group {
            switch state.route {
            case .onboarding:
                OnboardingView {
                    AnalyticsTracker.logEvent("onboarding_complete")
                    state.route = .homeLibrary
                    state.activeTab = .library
                }
            case .cameraPermission:
                CameraPermissionView(
                    onAllowCamera: {
                        Task {
                            let granted = await CameraCaptureController().requestAccess()
                            state.cameraPermissionGranted = granted
                            state.route = granted ? .capture : .homeLibrary
                        }
                    },
                    onNotNow: {
                        state.route = .homeLibrary
                        state.activeTab = .library
                    }
                )
            case .photoPermission:
                PhotoPermissionView(
                    onAllowAccess: {
                        Task {
                            let granted = await PhotoKitSuggestProvider.requestAuthorization()
                            state.photoPermissionGranted = granted
                            if granted {
                                state.route = .librarySuggest
                                state.refreshLibrarySuggestions()
                            } else {
                                state.route = .homeLibrary
                                state.activeTab = .library
                            }
                        }
                    },
                    onSelectManually: { showFileImporter = true },
                    onNotNow: {
                        state.route = .homeLibrary
                        state.activeTab = .library
                    }
                )
            case .homeLibrary:
                HomeLibraryView(state: state) {
                    if state.photoPermissionGranted {
                        showPhotoPicker = true
                    } else {
                        state.route = .photoPermission
                    }
                }
            case .librarySuggest:
                LibrarySuggestView(state: state) {
                    state.route = .homeLibrary
                    state.activeTab = .library
                }
            case .capture:
                CameraCaptureView(
                    state: state,
                    onOpenGallery: { showPhotoPicker = true },
                    onBack: {
                        state.route = .homeLibrary
                        state.activeTab = .library
                    }
                )
            case .reviewEdit:
                ReviewEditView(
                    state: state,
                    onAddMore: { state.route = .capture },
                    onDiscard: {
                        state.draftPages = []
                        state.editingDocumentId = nil
                        state.route = .homeLibrary
                        state.activeTab = .library
                    }
                )
            case .docDetail:
                DocumentDetailView(state: state) {
                    state.route = .homeLibrary
                    state.activeTab = .library
                }
            case .searchFilter:
                SearchFilterView(state: state) {
                    state.route = .homeLibrary
                    state.activeTab = .library
                }
            case .exportShare:
                ExportShareView(state: state) {
                    state.route = .docDetail
                }
            case .backup:
                BackupView(state: state) {
                    state.route = .homeLibrary
                }
            case .paywall:
                PaywallView(state: state) {
                    state.route = .homeLibrary
                }
            case .settings:
                SettingsView(state: state) {
                    state.route = .homeLibrary
                    state.activeTab = .library
                }
            }
        }
        .animation(reduceMotion ? .easeOut(duration: 0.14) : .snappy(duration: 0.25), value: state.route)
        .simultaneousGesture(
            DragGesture(minimumDistance: 24)
                .onEnded { value in
                    guard canSwipeBack,
                          value.startLocation.x < 28,
                          value.translation.width > 72,
                          abs(value.translation.height) < 60 else { return }
                    handleBackGesture()
                }
        )
        .onChange(of: state.route) { _, newRoute in
            AnalyticsTracker.logScreenView("\(newRoute)")
            if newRoute == .paywall {
                AnalyticsTracker.logEvent("paywall_view", params: ["is_pro": state.isPro])
            }
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $pickedItems, maxSelectionCount: 10, matching: .images)
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.image],
            allowsMultipleSelection: true
        ) { result in
            guard case .success(let urls) = result, !urls.isEmpty else { return }
            var images: [UIImage] = []
            for url in urls {
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url), let img = UIImage(data: data) {
                    images.append(img)
                }
            }
            if !images.isEmpty {
                state.importImages(images)
            }
        }
        .onChange(of: pickedItems) { _, newItems in
            guard !newItems.isEmpty else { return }
            let items = newItems
            pickedItems = []
            Task {
                var images: [UIImage] = []
                for item in items {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let img = UIImage(data: data) {
                        images.append(img)
                    }
                }
                if !images.isEmpty {
                    state.importImages(images)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

#Preview {
    ContentView()
}
