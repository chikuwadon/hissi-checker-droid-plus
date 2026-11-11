package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Discover non-ChMate readers through their URL filters, without an allowlist. */
object ExternalThreadApps {
    private const val MATE = "jp.co.airfront.android.a2chMate"
    private const val HAIAGARU = "jp.co.airfront.android.a2chMate.haiagaru"

    class Handler(
        @JvmField val packageName: String,
        @JvmField val activityName: String,
        @JvmField val url: String
    )

    private fun view(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    @Suppress("DEPRECATION")
    private fun query(pm: PackageManager, url: String): List<ResolveInfo> =
        try {
            pm.queryIntentActivities(view(url), PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: RuntimeException) {
            emptyList()
        }

    @JvmStatic
    fun urlVariants(canonical: String): List<String> =
        ArrayList(LinkedHashSet<String>().apply {
            add(canonical)
            addAll(ThreadOpener.chMateUrls(canonical))
        })

    @JvmStatic
    fun find(pm: PackageManager, canonical: String, ownPackage: String): List<Handler> {
        val genericWeb = HashSet<String>()
        for (url in arrayOf("https://www.example.com/", "http://www.example.com/")) {
            for (info in query(pm, url)) {
                info.activityInfo?.packageName?.let(genericWeb::add)
            }
        }
        val readers = LinkedHashMap<String, Handler>()
        for (url in urlVariants(canonical)) {
            for (info in query(pm, url)) {
                val activity = info.activityInfo ?: continue
                if (!activity.exported) continue
                val pkg = activity.packageName ?: continue
                val name = activity.name ?: continue
                if (pkg == ownPackage || pkg == MATE || pkg == HAIAGARU ||
                    pkg == "android" || pkg == "com.android.internal.app" ||
                    pkg in genericWeb || BrowserLauncher.isKnownBrowserPackage(pkg)
                ) continue
                readers.putIfAbsent(pkg, Handler(pkg, name, url))
            }
        }
        return ArrayList(readers.values)
    }

    private fun explicit(handler: Handler): Intent =
        view(handler.url).setComponent(ComponentName(handler.packageName, handler.activityName))

    private fun launch(activity: Activity, intent: Intent): Boolean =
        try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    @JvmStatic
    fun open(activity: Activity, canonical: String) {
        val readers = find(activity.packageManager, canonical, activity.packageName)
        if (readers.size == 1) {
            if (launch(activity, explicit(readers[0]))) return
        } else if (readers.size > 1) {
            val extras = Array(readers.size - 1) { index -> explicit(readers[index + 1]) }
            val chooser = Intent.createChooser(explicit(readers[0]), "スレッドを開くアプリ")
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras)
            if (launch(activity, chooser)) return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            for (url in urlVariants(canonical)) {
                val nonBrowser = view(url).addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
                if (launch(activity, nonBrowser)) return
            }
        }
        BrowserLauncher.open(activity, canonical)
    }
}
