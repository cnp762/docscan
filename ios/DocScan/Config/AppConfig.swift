import Foundation

enum AppConfig {
    static var apiBaseURL: URL {
        let raw = string("API_BASE_URL") ?? "https://api.docscan.cam/v1"
        return URL(string: raw.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
            ?? URL(string: "https://api.docscan.cam/v1")!
    }

    static var publicSiteURL: URL {
        let raw = string("PUBLIC_SITE_URL") ?? "https://docscan.cam"
        return URL(string: raw) ?? URL(string: "https://docscan.cam")!
    }

    static var adMobBannerUnitID: String {
        string("ADMOB_BANNER_UNIT_ID") ?? "ca-app-pub-3940256099942544/2934735716"
    }

    static var googleOAuthClientID: String? {
        string("GOOGLE_OAUTH_CLIENT_ID")
    }

    static var productProMonthly: String { string("PRODUCT_PRO_MONTHLY") ?? "pro_monthly" }
    static var productProQuarterly: String { string("PRODUCT_PRO_QUARTERLY") ?? "pro_quarterly" }
    static var productProYearly: String { string("PRODUCT_PRO_YEARLY") ?? "pro_yearly" }

    static var productIds: Set<String> {
        [productProMonthly, productProQuarterly, productProYearly]
    }

    private static func string(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.hasPrefix("$(") else { return nil }
        return trimmed
    }
}
