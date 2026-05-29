package roy.ij.obscure.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AnalyticsTracker {
    private const val PARAM_FEATURE = "feature"
    private const val PARAM_REASON = "reason"

    private var analytics: FirebaseAnalytics? = null
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }
    private var enabled: Boolean = AnalyticsConsent.defaultEnabled

    fun initialize(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
        setConsent(AnalyticsConsent.isEnabled(context))
    }

    fun setConsent(enabled: Boolean) {
        this.enabled = enabled
        analytics?.setAnalyticsCollectionEnabled(enabled)
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    fun screen(name: String) {
        if (!enabled) return

        val screenName = AnalyticsEventSanitizer.eventName(name)
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
            }
        )
        setCrashContext(screenName)
    }

    fun action(name: String, params: Map<String, String> = emptyMap()) {
        if (!enabled) return
        analytics?.logEvent(AnalyticsEventSanitizer.eventName(name), params.toBundle())
    }

    fun failure(name: String, reason: String) {
        if (!enabled) return
        action(
            "${name}_failure",
            mapOf(PARAM_REASON to reasonCategory(reason))
        )
        crashlytics.log("${AnalyticsEventSanitizer.eventName(name)} failed")
    }

    fun setCrashContext(screen: String) {
        if (!enabled) return
        crashlytics.setCustomKey("current_screen", AnalyticsEventSanitizer.eventName(screen))
    }

    fun setFeatureArea(feature: String) {
        if (!enabled) return
        crashlytics.setCustomKey(PARAM_FEATURE, AnalyticsEventSanitizer.eventName(feature))
    }

    fun setAppForeground(isForeground: Boolean) {
        if (!enabled) return
        crashlytics.setCustomKey("app_foreground", isForeground)
    }

    fun recordHandledException(throwable: Throwable, feature: String) {
        if (!enabled) return
        val safeFeature = AnalyticsEventSanitizer.eventName(feature)
        val safeType = AnalyticsEventSanitizer.eventName(
            throwable::class.java.simpleName.ifBlank { "throwable" }
        )
        setFeatureArea(safeFeature)
        crashlytics.setCustomKey("exception_type", safeType)
        crashlytics.recordException(RuntimeException("${safeFeature}_handled_${safeType}"))
    }

    private fun Map<String, String>.toBundle(): Bundle =
        Bundle().apply {
            AnalyticsEventSanitizer.sanitize(this@toBundle).forEach { (key, value) ->
                putString(key, value)
            }
        }

    private fun reasonCategory(reason: String): String {
        val normalized = reason.lowercase()
        return when {
            "network" in normalized || "timeout" in normalized -> "network"
            "server" in normalized || "5" in normalized -> "server"
            "invalid" in normalized || "credential" in normalized -> "invalid"
            "permission" in normalized || "unavailable" in normalized -> "unavailable"
            "upload" in normalized || "download" in normalized -> "transfer"
            "missing" in normalized || "required" in normalized -> "validation"
            else -> "unknown"
        }
    }
}
