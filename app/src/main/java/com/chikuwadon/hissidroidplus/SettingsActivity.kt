package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import android.widget.Toast

/** Settings previously accessible only through the home overflow menu. */
class SettingsActivity : MainSectionActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = screen(null, MainNavigation.Destination.SETTINGS)
        SettingsContent(this).populate(body)
    }
}

/** One implementation for the hosted SettingsFragment and compatibility activity. */
internal class SettingsContent(host: Activity) : SectionContent(host) {
    fun populate(body: LinearLayout) = with(host) {
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
            minimumHeight = dp(52)
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
            isChecked = AppSettings.tabAnimations(host)
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnCheckedChangeListener { _, enabled ->
                AppSettings.setTabAnimations(host, enabled)
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
        caption(body, "過去ログ検索")
        val placementLabels = arrayOf("画面上部", "画面下部")
        fun placementText(): String = "検索欄の位置：" + placementLabels[if (AppSettings.archiveSearchAtBottom(host)) 1 else 0]
        lateinit var placementRow: TextView
        placementRow = row(body, placementText()) {
            ThemedMenu.choose(this, "検索欄の位置", placementLabels,
                if (AppSettings.archiveSearchAtBottom(host)) 1 else 0) { index ->
                AppSettings.setArchiveSearchAtBottom(host, index == 1)
                placementRow.text = placementText()
            }
        }
        caption(body, "ナビゲーションバー")
        for (destination in MainNavigation.configurable) {
            val navRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(12), 0, dp(4), 0)
                isFocusable = true
                contentDescription = destination.label
                TouchFeedback.foreground(this)
            }
            val navLabel = TextView(this).apply {
                text = destination.label
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val navCheck = CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
                isChecked = MainNavigation.isVisible(host, destination)
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnCheckedChangeListener { _, enabled ->
                    MainNavigation.setVisible(host, destination, enabled)
                    MainNavigation.refresh(host)
                }
            }
            navRow.addView(navLabel, LinearLayout.LayoutParams(0, -2, 1f))
            navRow.addView(navCheck, LinearLayout.LayoutParams(dp(40), dp(48)))
            navRow.setOnClickListener { navCheck.isChecked = !navCheck.isChecked }
            navRow.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CheckBox::class.java.name
                    info.isCheckable = true
                    info.isChecked = navCheck.isChecked
                }
            }
            body.addView(navRow, LinearLayout.LayoutParams(-1, -2))
            body.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) },
                LinearLayout.LayoutParams(-1, dp(1)))
        }
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
        caption(body, "履歴管理")
        var currentLimit = SearchHistory.limit(this)
        var pendingLimit = currentLimit
        var saving = false
        val limitLabel = TextView(this).apply {
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(12), dp(8), dp(12), 0)
        }
        val limitRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), dp(12), 0)
        }
        limitLabel.setPadding(dp(12), 0, 0, 0)
        limitLabel.textSize = 14f
        limitRow.addView(limitLabel, LinearLayout.LayoutParams(0, -2, 1f))
        val currentLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
        }
        limitRow.addView(currentLabel, LinearLayout.LayoutParams(-2, -2))
        body.addView(limitRow, LinearLayout.LayoutParams(-1, -2))
        val slider = SeekBar(this).apply {
            max = HistoryRetention.LAST_PROGRESS
            progress = HistoryRetention.toProgress(currentLimit)
            progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
            thumbTintList = progressTintList
            contentDescription = "履歴の保存件数"
        }
        body.addView(slider, LinearLayout.LayoutParams(-1, dp(48)))
        val applyLimit = ThemedMenu.formButton(this, "保存件数を適用").apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(getColor(R.color.accent))
            }
            val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            setTextColor(if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                getColor(R.color.surface) else android.graphics.Color.WHITE)
        }
        body.addView(applyLimit, LinearLayout.LayoutParams(-1, dp(44)).apply {
            setMargins(dp(12), dp(6), dp(12), dp(12))
        })
        fun updateLimitUi() {
            currentLabel.text = "現在：${HistoryRetention.label(currentLimit)}"
            limitLabel.text = "履歴の保存件数：${HistoryRetention.label(pendingLimit)}"
            applyLimit.isEnabled = !saving && pendingLimit != currentLimit
            applyLimit.alpha = if (applyLimit.isEnabled) 1f else 0.4f
            slider.isEnabled = !saving
        }
        applyLimit.setOnClickListener {
            if (saving || pendingLimit == currentLimit) return@setOnClickListener
            val requested = pendingLimit
            val apply = Runnable {
                saving = true
                updateLimitUi()
                SearchHistory.updateLimit(this, requested) { success ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        currentLimit = SearchHistory.limit(this)
                        pendingLimit = currentLimit
                        saving = false
                        slider.progress = HistoryRetention.toProgress(currentLimit)
                        updateLimitUi()
                        Toast.makeText(this, if (success) "保存件数を更新しました" else "保存件数の更新に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (HistoryRetention.reduced(currentLimit, requested)) {
                ThemedMenu.confirm(this, "履歴の保存件数",
                    "上限を超える古い履歴は削除されます。お気に入りは削除されません。", "適用", apply)
            } else apply.run()
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                pendingLimit = HistoryRetention.fromProgress(progress)
                updateLimitUi()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        updateLimitUi()
        caption(body, "アプリ情報")
        row(body, "このアプリについて") { ThemedMenu.showAbout(this) }
    }
}
