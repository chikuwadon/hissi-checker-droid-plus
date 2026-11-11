package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import java.net.URI
import java.util.LinkedHashSet
import java.util.Locale
import java.util.regex.Pattern

/** ChMate/Haiagaru thread handoff with validated URLs and persistent app selection. */
object ThreadOpener {
    private const val MATE = "jp.co.airfront.android.a2chMate"
    private const val HAIAGARU = "jp.co.airfront.android.a2chMate.haiagaru"
    private const val RES_LIST = "jp.syoboi.a2chMate.activity.ResListActivity"
    private const val OPEN_URL = "app.morphe.extension.chmate.OpenUrlActivity"
    private val THREAD = Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]{1,60})/(\\d{10,20})/(\\d{1,6})?$"
    )
    private val FIVECH_THREAD = Pattern.compile(
        "^/test/read\\.cgi/[A-Za-z0-9_]{1,60}/\\d{10,20}(?:/[A-Za-z0-9_,+\\-]{1,50})?/?$"
    )

    @JvmStatic
    fun chMateUrls(source: String): List<String> {
        val urls = LinkedHashSet<String>()
        try {
            val uri = URI.create(source)
            if (!uri.scheme.equals("https", true) ||
                !uri.host.equals("bbs.eddibb.cc", true) ||
                uri.rawUserInfo != null || uri.port != -1
            ) return emptyList()
            val matched = THREAD.matcher(uri.rawPath ?: return emptyList())
            if (!matched.matches()) return emptyList()
            val suffix = matched.group(3)?.let { "/" + it } ?: ""
            val shortPath = "/" + matched.group(1) + "/" + matched.group(2) + suffix
            val canonical = "/test/read.cgi/" + matched.group(1) + "/" +
                matched.group(2) + "/" + (matched.group(3) ?: "")
            urls.add("http://bbs.eddibb.cc" + shortPath)
            urls.add("https://bbs.eddibb.cc" + shortPath)
            urls.add("http://bbs.eddibb.cc" + canonical)
            urls.add("https://bbs.eddibb.cc" + canonical)
        } catch (_: Exception) {
            // Return no candidates for invalid source URLs.
        }
        return ArrayList(urls)
    }

    @Suppress("DEPRECATION")
    private fun isReaderInstalled(pm: PackageManager, pkg: String): Boolean =
        try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    @JvmStatic
    fun isMateInstalled(pm: PackageManager): Boolean = isReaderInstalled(pm, MATE)

    @JvmStatic
    fun readerPackages(mateInstalled: Boolean, haiagaruInstalled: Boolean): List<String> =
        ArrayList<String>().apply {
            if (mateInstalled) add(MATE)
            if (haiagaruInstalled) add(HAIAGARU)
        }

    private fun readerLabel(pkg: String): String = if (pkg == HAIAGARU) "Haiagaru" else "ChMate"

    @JvmStatic
    fun viewIntent(url: String, pkg: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).also {
            if (pkg != null) it.setPackage(pkg) else it.addCategory(Intent.CATEGORY_BROWSABLE)
        }

    @JvmStatic
    fun validatedThreadUrl(raw: String): String {
        val canonical = EdgeThreadLink.original(raw)
        return if (chMateUrls(canonical).isEmpty()) "" else canonical
    }

    @JvmStatic
    fun validated5chThreadUrl(raw: String?): String {
        if (raw == null) return ""
        return try {
            val uri = URI.create(raw)
            val scheme = uri.scheme
            val host = uri.host
            if (!scheme.equals("http", true) && !scheme.equals("https", true)) return ""
            if (host == null || uri.rawUserInfo != null || uri.port != -1 ||
                uri.rawQuery != null || uri.rawFragment != null
            ) return ""
            val lower = host.lowercase(Locale.ROOT)
            val is5ch = lower == "5ch.net" || lower.endsWith(".5ch.net") ||
                lower == "5ch.io" || lower.endsWith(".5ch.io")
            if (!is5ch || !FIVECH_THREAD.matcher(uri.rawPath ?: "").matches()) "" else raw
        } catch (_: IllegalArgumentException) {
            ""
        }
    }

    private fun directThreadIntent(url: String, pkg: String, activityClass: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClassName(pkg, activityClass)

    private fun launch(activity: Activity, intent: Intent): Boolean =
        try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    private fun copyAndLaunchReader(activity: Activity, url: String, pkg: String): Boolean =
        try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                as? ClipboardManager ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText("スレッドURL", url))
            activity.startActivity(launchIntent)
            Toast.makeText(activity,
                "URLをコピーしました。" + readerLabel(pkg) +
                    "の「URLを指定して開く」に貼り付けてください",
                Toast.LENGTH_LONG).show()
            true
        } catch (_: RuntimeException) {
            false
        }

    private fun openInReader(activity: Activity, url: String, pkg: String, edge: Boolean) {
        if (!edge && launch(activity, viewIntent(url, pkg))) return
        if (launch(activity, directThreadIntent(url, pkg, RES_LIST))) return
        if (launch(activity, directThreadIntent(url, pkg, OPEN_URL))) return
        val label = readerLabel(pkg)
        ThemedMenu.choose(activity, "スレッドを開く",
            arrayOf("URLをコピーして" + label + "を開く", "他のアプリで開く", "外部ブラウザで開く"),
            -1
        ) { which ->
            if (which == 0 && !copyAndLaunchReader(activity, url, pkg)) {
                Toast.makeText(activity, label + "を起動できませんでした", Toast.LENGTH_LONG).show()
            } else if (which == 1) {
                ExternalThreadApps.open(activity, url)
            } else if (which == 2) {
                BrowserLauncher.open(activity, url)
            }
        }
    }

    private fun modeForPackage(pkg: String): String =
        if (pkg == HAIAGARU) AppSettings.THREAD_HAIAGARU else AppSettings.THREAD_CHMATE

    private fun chooseReader(activity: Activity, url: String, edge: Boolean, readers: List<String>) {
        if (readers.isEmpty()) {
            ExternalThreadApps.open(activity, url)
            return
        }
        val labels = Array(readers.size + 2) { index ->
            if (index < readers.size) readerLabel(readers[index]) + "で開く"
            else if (index == readers.size) "他のアプリで開く"
            else "外部ブラウザで開く"
        }
        ThemedMenu.chooseWithAlways(activity, "スレッドを開く", labels) { which, always ->
            val selection = when {
                which < readers.size -> modeForPackage(readers[which])
                which == readers.size -> AppSettings.THREAD_OTHER
                else -> AppSettings.THREAD_BROWSER
            }
            AppSettings.setThreadOpenMode(
                activity, if (always) selection else AppSettings.THREAD_ASK
            )
            if (which < readers.size) openInReader(activity, url, readers[which], edge)
            else if (which == readers.size) ExternalThreadApps.open(activity, url)
            else BrowserLauncher.open(activity, url)
        }
    }

    @JvmStatic
    fun open(activity: Activity, raw: String?, edge: Boolean) {
        if (raw.isNullOrEmpty()) return
        val normalized = if (edge) EdgeThreadLink.original(raw) else raw
        val accepted = if (edge) validatedThreadUrl(raw) else validated5chThreadUrl(raw)
        if (accepted.isEmpty()) {
            if (!edge && launch(activity, viewIntent(normalized, null))) return
            BrowserLauncher.open(activity, normalized)
            return
        }
        val pm = activity.packageManager
        val readers = readerPackages(isMateInstalled(pm), isReaderInstalled(pm, HAIAGARU))
        val mode = AppSettings.threadOpenMode(activity)
        when {
            mode == AppSettings.THREAD_BROWSER -> BrowserLauncher.open(activity, accepted)
            mode == AppSettings.THREAD_OTHER -> ExternalThreadApps.open(activity, accepted)
            mode == AppSettings.THREAD_CHMATE && MATE in readers ->
                openInReader(activity, accepted, MATE, edge)
            mode == AppSettings.THREAD_HAIAGARU && HAIAGARU in readers ->
                openInReader(activity, accepted, HAIAGARU, edge)
            readers.isEmpty() -> ExternalThreadApps.open(activity, accepted)
            readers.size == 1 && mode == AppSettings.THREAD_ASK ->
                openInReader(activity, accepted, readers[0], edge)
            else -> chooseReader(activity, accepted, edge, readers)
        }
    }
}
