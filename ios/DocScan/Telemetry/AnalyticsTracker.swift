import Foundation
import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics

/// Privacy-safe telemetry wrapper enforcing docs/ads-analytics.md.
enum AnalyticsTracker {
    private static let allowedParams: Set<String> = [
        "source",
        "n_pages",
        "is_pro",
        "quota_remaining",
        "trigger",
        "format",
        "product_id",
        "screen_name",
    ]

    static func configureIfPresent() {
        guard FirebaseApp.app() == nil else { return }
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    static func sanitize(_ params: [String: Any]) -> [String: Any] {
        params.filter { allowedParams.contains($0.key) }
    }

    static func logEvent(_ name: String, params: [String: Any] = [:]) {
        guard FirebaseApp.app() != nil else { return }
        Analytics.logEvent(name, parameters: sanitize(params))
    }

    static func logScreenView(_ screenId: String) {
        logEvent(AnalyticsEventScreenView, params: ["screen_name": screenId])
    }

    static func recordNonFatal(_ error: Error) {
        guard FirebaseApp.app() != nil else { return }
        Crashlytics.crashlytics().record(error: error)
    }
}
