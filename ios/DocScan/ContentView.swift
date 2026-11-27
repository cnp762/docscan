import PhotosUI
import SwiftUI

struct ContentView: View {
    @StateObject private var state = DocScanAppState()
    @State private var showPhotoPicker = false
    @State private var pickedItems: [PhotosPickerItem] = []

    var body: some View {
        Group {
            switch state.route {
            case .onboarding:
                OnboardingView {
                    state.route = .homeLibrary
                    state.activeTab = .library
                }
            case .cameraPermission:
                CameraPermissionView(
                    onAllowCamera: {
                        Task {
                            let granted = await CaptureSessionStub().requestAccess()
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
                        state.photoPermissionGranted = true
                        state.route = .librarySuggest
                    },
                    onSelectManually: { showPhotoPicker = true },
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
        .photosPicker(isPresented: $showPhotoPicker, selection: $pickedItems, maxSelectionCount: 10, matching: .images)
        .onChange(of: pickedItems) { _, newItems in
            guard !newItems.isEmpty else { return }
            state.importPhotos(count: newItems.count)
            pickedItems = []
        }
        .preferredColorScheme(.dark)
    }
}

#Preview {
    ContentView()
}
