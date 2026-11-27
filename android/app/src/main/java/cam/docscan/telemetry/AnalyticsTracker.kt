package cam.docscan.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Privacy-safe telemetry wrapper.
 * Enforces docs/ads-analytics.md: NEVER send document images, OCR text, summaries, or PII filenames.
 */
object AnalyticsTracker {
    private val ALLOWED_PARAMS = setOf(
        "source",
        "n_pages",
        "is_pro",
        "quota_remaining",
        "trigger",
        "format",
        "product_id",
        "screen_name",
    )

    fun sanitizeParams(raw: Map<String, Any>): Map<String, Any> =
        raw.filterKeys { it in ALLOWED_PARAMS }

    fun logEvent(context: Context, name: String, params: Map<String, Any> = emptyMap()) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val safe = sanitizeParams(params)
        val bundle = Bundle().apply {
            safe.forEach { (k, v) ->
                when (v) {
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Double -> putDouble(k, v)
                    is Boolean -> putBoolean(k, v)
                    else -> putString(k, v.toString())
                }
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }

    fun logScreenView(context: Context, screenId: String) {
        logEvent(context, FirebaseAnalytics.Event.SCREEN_VIEW, mapOf("screen_name" to screenId))
    }

    fun recordNonFatal(context: Context, throwable: Throwable) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }
}
