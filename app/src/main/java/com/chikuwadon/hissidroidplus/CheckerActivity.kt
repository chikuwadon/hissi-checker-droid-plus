package com.chikuwadon.hissidroidplus

import android.os.Build
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Shared AndroidX foundation. Existing native Views and task routing are retained. */
abstract class CheckerActivity : FragmentActivity() {
    protected fun registerBackAction(action: () -> Unit) {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = action()
        })
    }

    protected fun applyContentInsets(root: View) {
        // Preserve platform fitting on API 26–29 in this migration.
        if (Build.VERSION.SDK_INT < 30) return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }
}
