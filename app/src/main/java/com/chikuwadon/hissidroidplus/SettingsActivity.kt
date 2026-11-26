package com.chikuwadon.hissidroidplus

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Settings previously accessible only through the home overflow menu. */
class SettingsActivity : MainSectionActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = screen("設定", MainNavigation.Destination.SETTINGS)
        caption(body, "外観")
        row(body, "テーマ") {
            val values = arrayOf(AppSettings.THEME_DARK, AppSettings.THEME_LIGHT, AppSettings.THEME_SYSTEM)
            val labels = arrayOf("ダーク", "ライト", "端末の設定に合わせる")
            val current = AppSettings.prefs(this).getString(AppSettings.THEME, AppSettings.THEME_DARK)
            ThemedMenu.choose(this, "テーマ", labels, values.indexOf(current)) {
                AppSettings.prefs(this).edit().putString(AppSettings.THEME, values[it]).apply()
                recreate()
            }
        }
        caption(body, "操作")
        val animationLabel = "タブ切替アニメーション"
        val animationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            setPadding(dp(12), 0, dp(4), 0)
            isFocusable = true
            contentDescription = animationLabel
            TouchFeedback.foreground(this)
        }
        val label = TextView(this).apply {
            text = animationLabel
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val checkbox = CheckBox(this).apply {
            buttonTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
            isChecked = AppSettings.tabAnimations(this@SettingsActivity)
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnCheckedChangeListener { _, enabled ->
                AppSettings.setTabAnimations(this@SettingsActivity, enabled)
            }
        }
        animationRow.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        animationRow.addView(checkbox, LinearLayout.LayoutParams(dp(40), dp(48)))
        animationRow.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
        animationRow.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = CheckBox::class.java.name
                info.isCheckable = true
                info.isChecked = checkbox.isChecked
            }
        }
        body.addView(animationRow, LinearLayout.LayoutParams(-1, -2))
        body.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        caption(body, "外部アプリ連携")
        row(body, "スレの開き方") {
            val labels = arrayOf("毎回選択", "ChMateで開く", "Haiagaruで開く",
                "他のアプリで開く", "外部ブラウザで開く")
            ThemedMenu.choose(this, "スレの開き方", labels,
                AppSettings.THREAD_MODES.indexOf(AppSettings.threadOpenMode(this))) {
                AppSettings.setThreadOpenMode(this, AppSettings.THREAD_MODES[it])
            }
        }
        row(body, "リンクの開き方") {
            val labels = arrayOf("ChMate連携（元の画面へ戻る）", "単独アプリ（別タスクで開く）")
            ThemedMenu.choose(this, "リンクの開き方", labels, if (AppSettings.standalone(this)) 1 else 0) {
                AppSettings.prefs(this).edit().putString(AppSettings.LINK_MODE,
                    if (it == 1) AppSettings.MODE_STANDALONE else AppSettings.MODE_CONNECTED).apply()
            }
        }
        caption(body, "アプリ情報")
        row(body, "このアプリについて") { ThemedMenu.showAbout(this) }
    }
}
