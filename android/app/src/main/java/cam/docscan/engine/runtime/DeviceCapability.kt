package cam.docscan.engine.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * System health for heavy NLP. No min-RAM / free-RAM gates.
 * [systemOk] = !OS lowMemory && !thermal throttled.
 */
class DeviceCapability(
    private val context: Context? = null,
) {
    data class Snapshot(
        val osLowMemory: Boolean,
        val thermalThrottled: Boolean,
        val systemOk: Boolean,
    )

    fun snapshot(): Snapshot {
        val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val osLow = am != null && memInfo.lowMemory
        val thermal = isThermallyThrottled()
        return Snapshot(
            osLowMemory = osLow,
            thermalThrottled = thermal,
            systemOk = !osLow && !thermal,
        )
    }

    fun systemOk(): Boolean = snapshot().systemOk

    /** Generative only when system is healthy (weights checked separately). */
    fun allowGenerative(): Boolean = systemOk()

    private fun isThermallyThrottled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return try {
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        const val NLP_MAX_LONG_EDGE = 1600
        const val BATCH_SUMMARY_MAX_CHARS = 8_000
        const val PAGE_SUMMARY_MAX_CHARS = 500

        // Legacy aliases used by older call sites — map to WorkTimeouts.
        const val TIMEOUT_LANG_CLASSIFY_MS = WorkTimeouts.LANG_MS
        const val TIMEOUT_EXTRACTIVE_MS = WorkTimeouts.SUMMARY_EXTRACTIVE_MS
        const val TIMEOUT_GENERATIVE_MS = WorkTimeouts.SUMMARY_GENERATIVE_MS
    }
}
