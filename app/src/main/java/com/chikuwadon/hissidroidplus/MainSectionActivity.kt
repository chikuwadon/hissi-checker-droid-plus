package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared lightweight shell for the new independent top-level screens. */
abstract class MainSectionActivity : CheckerActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    override fun onResume() {
        super.onResume()
        MainNavigation.refresh(this)
    }

    protected fun dp(n: Int) = MainNavigation.dp(this, n)

    protected fun screen(title: String?, destination: MainNavigation.Destination): LinearLayout {
        AppSettings.configureBars(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        val content = SectionContent(this)
        val body = content.build(root, title)
        MainNavigation.attach(this, root, destination)
        setContentView(root)
        applyContentInsets(root)
        return body
    }

}

/** Shared native Views for the standalone compatibility activities and hosted Fragments. */
internal open class SectionContent(protected val host: Activity) {
    protected fun dp(n: Int) = MainNavigation.dp(host, n)

    fun build(root: LinearLayout, title: String?): LinearLayout {
        val scroll = ScrollView(host).apply { isFillViewport = true }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(18))
        }
        if (title != null) root.addView(AppBar.title(host, title), LinearLayout.LayoutParams(-1, dp(56)))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }

    fun row(body: LinearLayout, label: String, action: () -> Unit): TextView {
        val item = TextView(host).apply {
            text = label
            textSize = 15f
            setTextColor(host.getColor(R.color.text_primary))
            minHeight = dp(52)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            TouchFeedback.foreground(this)
            setOnClickListener { action() }
        }
        body.addView(item, LinearLayout.LayoutParams(-1, -2))
        body.addView(View(host).apply { setBackgroundColor(host.getColor(R.color.divider)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        return item
    }

    fun caption(body: LinearLayout, label: String) {
        body.addView(TextView(host).apply {
            text = label
            textSize = 13f
            setTextColor(host.getColor(R.color.text_secondary))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
    }
}
