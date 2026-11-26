package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared lightweight shell for the new independent top-level screens. */
abstract class MainSectionActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    override fun onResume() {
        super.onResume()
        MainNavigation.refresh(this)
    }

    protected fun dp(n: Int) = MainNavigation.dp(this, n)

    protected fun screen(title: String, destination: MainNavigation.Destination): LinearLayout {
        AppSettings.configureBars(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(18))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
        })
        MainNavigation.attach(this, root, destination)
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
            root.requestApplyInsets()
        }
        return body
    }

    protected fun row(body: LinearLayout, label: String, action: () -> Unit): TextView {
        val item = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            minHeight = dp(52)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            TouchFeedback.foreground(this)
            setOnClickListener { action() }
        }
        body.addView(item, LinearLayout.LayoutParams(-1, -2))
        body.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        return item
    }

    protected fun caption(body: LinearLayout, label: String) {
        body.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
    }
}
