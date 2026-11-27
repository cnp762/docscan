package cam.docscan.telemetry

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import cam.docscan.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds

object AdMobManager {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                MobileAds.initialize(context.applicationContext) {}
                initialized = true
            }
        }
    }
}

/**
 * AdMob banner strip placed strictly above the bottom nav on Library / Search / Doc Detail for Free users.
 * Fail-soft: collapses to 0dp on load error and never renders when isPro == true.
 */
@Composable
fun AdMobBanner(
    isPro: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isPro) return

    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        AdMobManager.ensureInitialized(context)
        onDispose {}
    }

    val adView = remember(context) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
            visibility = View.GONE
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    visibility = View.VISIBLE
                    loaded = true
                    AnalyticsTracker.logEvent(context, "ad_impression")
                }

                override fun onAdClicked() {
                    AnalyticsTracker.logEvent(context, "ad_click")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    visibility = View.GONE
                    loaded = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    if (loaded) {
        AndroidView(
            factory = { adView },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        // Keep AdView attached at 0dp while loading so onAdLoaded can fire
        AndroidView(
            factory = { adView },
            modifier = Modifier,
        )
    }
}
