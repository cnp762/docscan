import SwiftUI

/// Shell home. Full flows follow docs/screens/* after mock review.
struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("DocScan")
                    .font(.largeTitle.weight(.semibold))
                Text("cam.docscan")
                    .foregroundStyle(.secondary)
                Text("Shell build. Implement Capture / Import / Suggest / Edit from docs/screens.")
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
                Button("Scan") { /* TODO: AVFoundation capture */ }
                    .buttonStyle(.borderedProminent)
                Button("Import") { /* TODO: PHPicker */ }
                Button("Init engine") {
                    Task {
                        let eng = DefaultDocScanEngine()
                        try? await eng.initialize()
                    }
                }
                Button("Go Pro") { /* TODO: StoreKit 2 */ }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(red: 0.05, green: 0.07, blue: 0.06))
            .foregroundStyle(Color(red: 0.91, green: 0.94, blue: 0.92))
            .navigationTitle("Library")
        }
    }
}

#Preview {
    ContentView()
}
