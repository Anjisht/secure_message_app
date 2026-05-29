package roy.ij.obscure.analytics

import android.content.Context

object AnalyticsConsent {
    const val defaultEnabled: Boolean = false

    private const val PREF_NAME = "analytics_consent"
    private const val KEY_CHOICE_MADE = "choice_made"
    private const val KEY_ENABLED = "enabled"

    fun hasChoice(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHOICE_MADE, false)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, defaultEnabled)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CHOICE_MADE, true)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
