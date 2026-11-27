import SwiftUI

@main
struct DocScanApp: App {
    init() {
        AnalyticsTracker.configureIfPresent()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
