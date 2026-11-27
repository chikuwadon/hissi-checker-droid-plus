package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.view.WindowCompat

/** Persistent cross-activity preferences, independent of the hosting task. */
object AppSettings {
    private const val FILE = "checker_settings"
    const val THEME = "theme"
    const val LINK_MODE = "external_link_mode"
    const val TAB_ANIMATIONS = "tab_switch_animations"
    const val ARCHIVE_SEARCH_BOTTOM = "archive_search_bottom"
    const val ARCHIVE_FONT_PERCENT = "archive_font_percent"
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
    fun configureBars(activity: Activity) {
        val light = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        // Use the AndroidX controller without overwriting unrelated window flags.
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        configureBarBackgrounds(activity)
    }

    // Keep the platform's existing bar background behavior for legacy devices and
    // three-button navigation. Edge-to-edge platforms may ignore these colors.
    @Suppress("DEPRECATION")
    private fun configureBarBackgrounds(activity: Activity) {
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

    /** Persisted across both 5ch and Edge result pages; enabled by default. */
    @JvmStatic
    fun tabAnimations(context: Context): Boolean =
        prefs(context).getBoolean(TAB_ANIMATIONS, true)

    @JvmStatic
    fun setTabAnimations(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(TAB_ANIMATIONS, enabled).apply()
    }

    @JvmStatic
    fun archiveSearchAtBottom(context: Context): Boolean =
        prefs(context).getBoolean(ARCHIVE_SEARCH_BOTTOM, false)

    @JvmStatic
    fun setArchiveSearchAtBottom(context: Context, bottom: Boolean) {
        prefs(context).edit().putBoolean(ARCHIVE_SEARCH_BOTTOM, bottom).apply()
    }

    @JvmStatic
    fun archiveFontPercent(context: Context): Int =
        ArchiveFontScale.normalize(prefs(context).getInt(ARCHIVE_FONT_PERCENT, ArchiveFontScale.DEFAULT))

    @JvmStatic
    fun setArchiveFontPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(ARCHIVE_FONT_PERCENT, ArchiveFontScale.normalize(percent)).apply()
    }

    @JvmStatic
    fun standalone(context: Context): Boolean =
        prefs(context).getString(LINK_MODE, MODE_CONNECTED) == MODE_STANDALONE
}
