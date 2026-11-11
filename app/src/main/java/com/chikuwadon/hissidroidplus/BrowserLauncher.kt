package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.util.LinkedHashSet

/** Open real browser Activities without recursively returning to our own checker link handler. */
object BrowserLauncher {
    private val COMMON_BROWSERS = arrayOf(
        "com.android.chrome", "com.sec.android.app.sbrowser",
        "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "com.brave.browser", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.opera.browser",
        "com.opera.mini.native", "com.vivaldi.browser",
        "com.kiwibrowser.browser", "org.chromium.chrome",
        "com.google.android.apps.chrome"
    )

    @JvmStatic
    fun isKnownBrowserPackage(pkg: String?): Boolean =
        pkg != null && COMMON_BROWSERS.contains(pkg)

    @JvmStatic
    fun open(activity: Activity, url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val target = Uri.parse(url)
        if (!target.scheme.equals("https", true) && !target.scheme.equals("http", true)) {
            return false
        }
        val base = Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = activity.packageManager

        // Determine the preferred generic browser, not the handler for our deep links.
        val generic = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val packages = LinkedHashSet<String>()
        try {
            val preferred = pm.resolveActivity(generic, PackageManager.MATCH_DEFAULT_ONLY)
            val name = preferred?.activityInfo?.packageName
            if (!name.isNullOrEmpty()) packages.add(name)
        } catch (_: Exception) {}
        packages.addAll(COMMON_BROWSERS)
        try {
            for (item in pm.queryIntentActivities(generic, 0)) {
                val name = item.activityInfo?.packageName
                if (!name.isNullOrEmpty()) packages.add(name)
            }
        } catch (_: Exception) {}
        for (pkg in packages) {
            if (pkg.isEmpty() || pkg == activity.packageName ||
                pkg == "android" || pkg == "com.android.internal.app"
            ) continue
            try {
                activity.startActivity(Intent(base).setPackage(pkg))
                return true
            } catch (_: Exception) {
                // Try another installed browser.
            }
        }
        try {
            val chooser = Intent.createChooser(base, "ブラウザで開く")
            if (Build.VERSION.SDK_INT >= 29) {
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(
                    ComponentName(activity, ResultActivity::class.java),
                    ComponentName(activity.packageName,
                        activity.packageName + ".LegacyEdgeLinkActivity"),
                    ComponentName(activity.packageName,
                        activity.packageName + ".FilteredEdgeLinkActivity")
                ))
            }
            activity.startActivity(chooser)
            return true
        } catch (_: Exception) {}
        Toast.makeText(activity, "ブラウザで開けませんでした", Toast.LENGTH_SHORT).show()
        return false
    }
}
