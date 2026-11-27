import AppTrackingTransparency
import GoogleMobileAds
import SwiftUI

struct AdMobBannerView: View {
    let isPro: Bool
    @State private var loaded = false

    var body: some View {
        if !isPro {
            BannerRepresentable(loaded: $loaded)
                .frame(height: loaded ? 50 : 0)
                .clipped()
                .task {
                    if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                        _ = await ATTrackingManager.requestTrackingAuthorization()
                    }
                    GADMobileAds.sharedInstance().start(completionHandler: nil)
                }
        }
    }
}

private struct BannerRepresentable: UIViewRepresentable {
    @Binding var loaded: Bool

    func makeCoordinator() -> Coordinator { Coordinator(loaded: $loaded) }

    func makeUIView(context: Context) -> GADBannerView {
        let banner = GADBannerView(adSize: GADAdSizeBanner)
        banner.adUnitID = AppConfig.adMobBannerUnitID
        banner.delegate = context.coordinator
        banner.rootViewController = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first
        banner.load(GADRequest())
        return banner
    }

    func updateUIView(_ uiView: GADBannerView, context: Context) {}

    final class Coordinator: NSObject, GADBannerViewDelegate {
        @Binding var loaded: Bool

        init(loaded: Binding<Bool>) {
            _loaded = loaded
        }

        func bannerViewDidReceiveAd(_ bannerView: GADBannerView) {
            loaded = true
            AnalyticsTracker.logEvent("ad_impression")
        }

        func bannerView(_ bannerView: GADBannerView, didFailToReceiveAdWithError error: Error) {
            loaded = false
        }

        func bannerViewDidRecordClick(_ bannerView: GADBannerView) {
            AnalyticsTracker.logEvent("ad_click")
        }
    }
}
