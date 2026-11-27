package com.chikuwadon.hissidroidplus

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** Centralizes typed package-manager flags on Android 13+ without changing
 * package visibility, intent filters, match flags or error handling. */
internal object PackageQueries {
    @Suppress("DEPRECATION")
    fun packageInfo(pm: PackageManager, name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageInfo(name, 0)
        }

    @Suppress("DEPRECATION")
    fun resolve(pm: PackageManager, intent: Intent, flags: Int): ResolveInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            pm.resolveActivity(intent, flags)
        }

    @Suppress("DEPRECATION")
    fun query(pm: PackageManager, intent: Intent, flags: Int): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            pm.queryIntentActivities(intent, flags)
        }
}
