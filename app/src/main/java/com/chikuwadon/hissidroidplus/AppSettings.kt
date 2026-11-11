package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.View

/** Persistent cross-activity preferences, independent of the hosting task. */
object AppSettings {
    private const val FILE = "checker_settings"
    const val THEME = "theme"
    const val LINK_MODE = "external_link_mode"
    const val THREAD_OPEN_MODE = "thread_open_mode"
    const val THREAD_ASK = "ask"
    const val THREAD_CHMATE = "chmate"
    const val THREAD_HAIAGARU = "haiagaru"
    const val THREAD_OTHER = "other"
    const val THREAD_BROWSER = "browser"
    @JvmField val THREAD_MODES = arrayOf(
        THREAD_ASK, THREAD_CHMATE, THREAD_HAIAGARU, THREAD_OTHER, THREAD_BROWSER
    )
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM = "system"
    const val MODE_CONNECTED = "connected"
    const val MODE_STANDALONE = "standalone"

    @JvmStatic
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @JvmStatic
    fun themeContext(base: Context): Context {
        val theme = prefs(base).getString(THEME, THEME_DARK)
        if (theme == THEME_SYSTEM) return base
        val override = Configuration(base.resources.configuration)
        val desired = if (theme == THEME_LIGHT)
            Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        override.uiMode = (override.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or desired
        return base.createConfigurationContext(override)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun configureBars(activity: Activity) {
        val light = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        val flags = if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        activity.window.decorView.systemUiVisibility = flags
        activity.window.statusBarColor = activity.getColor(R.color.surface)
        activity.window.navigationBarColor = activity.getColor(R.color.surface)
    }

    @JvmStatic
    fun threadOpenMode(context: Context): String {
        val stored = prefs(context).getString(THREAD_OPEN_MODE, THREAD_ASK)
        return THREAD_MODES.firstOrNull { it == stored } ?: THREAD_ASK
    }

    @JvmStatic
    fun setThreadOpenMode(context: Context, mode: String?) {
        val validated = THREAD_MODES.firstOrNull { it == mode } ?: THREAD_ASK
        prefs(context).edit().putString(THREAD_OPEN_MODE, validated).apply()
    }

    @JvmStatic
    fun standalone(context: Context): Boolean =
        prefs(context).getString(LINK_MODE, MODE_CONNECTED) == MODE_STANDALONE
}
