package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Theme-aware shared app menus, anchored overflow and centered confirmation panels. */
object ThemedMenu {
    fun interface Selection { fun onSelected(index: Int) }
    fun interface RememberSelection { fun onSelected(index: Int, always: Boolean) }

    private fun dp(a: Activity, amount: Int): Int =
        (a.resources.displayMetrics.density * amount).roundToInt()

    private fun panelBackground(a: Activity, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(a.getColor(R.color.panel))
            cornerRadius = dp(a, radius).toFloat()
            setStroke(dp(a, 1), a.getColor(R.color.border))
        }

    private fun text(a: Activity, value: String, size: Int, color: Int): TextView =
        TextView(a).apply {
            this.text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            fontFeatureSettings = "kern"
        }

    private fun applyTouch(a: Activity, view: View) {
        // The same touch-origin ripple as the category picker button.
        TouchFeedback.foreground(view)
    }

    private fun applyMenuTouch(a: Activity, view: View) {
        // No persistent fill or border tint for the ON state: only the checkmark
        // identifies the saved setting. The ripple expands while pressing.
        TouchFeedback.foreground(view)
    }

    private fun panelWidth(a: Activity, preferred: Int): Int =
        min(a.resources.displayMetrics.widthPixels - dp(a, 32), dp(a, preferred))

    private fun column(a: Activity, padding: Int): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, padding), dp(a, padding), dp(a, padding), dp(a, padding))
            background = panelBackground(a, 15)
        }

    /** Compact popup attached to toolbar action, not a modal dialog. */
    @JvmStatic
    fun showOverflow(a: Activity, anchor: View, items: Array<String>, selected: Selection) {
        if (a.isFinishing || a.isDestroyed) return
        val panel = column(a, 5)
        val measure = text(a, "", 14, a.getColor(R.color.text_primary))
        val labelWidth = items.maxOfOrNull { measure.paint.measureText(it) } ?: 0f
        val desired = ceil(labelWidth.toDouble()).toInt() + dp(a, 48)
        val width = min(panelWidth(a, 304), max(dp(a, 156), desired))
        val popup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dp(a, 10).toFloat()
        var actionPending = false
        items.forEachIndexed { index, value ->
            val row = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                minimumHeight = dp(a, 48)
                setPadding(dp(a, 15), dp(a, 10), dp(a, 12), dp(a, 10))
                isSingleLine = false
            }
            applyMenuTouch(a, row)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2))
            row.setOnClickListener {
                if (actionPending) return@setOnClickListener
                actionPending = true
                row.postOnAnimation {
                    if (popup.isShowing && !a.isFinishing && !a.isDestroyed) {
                        popup.dismiss()
                        selected.onSelected(index)
                    }
                }
            }
        }
        popup.showAsDropDown(anchor, 0, -dp(a, 2), Gravity.END)
    }

    /**
     * A checkable overflow item that does not dismiss its popup when toggled.
     * All ordinary actions continue to close the popup, as before.
     */
    @JvmStatic
    fun showOverflowToggle(
        a: Activity, anchor: View, items: Array<String>, toggleIndex: Int,
        checked: Boolean, onToggle: (Boolean) -> Unit, selected: Selection
    ) {
        if (a.isFinishing || a.isDestroyed) return
        val panel = column(a, 5)
        val measure = text(a, "", 14, a.getColor(R.color.text_primary))
        // Size exactly for the widest row. The checkmark sits next to its
        // label; no weighted spacer or hard-coded 280dp minimum is necessary.
        val ordinaryTextWidth = items.filterIndexed { i, _ -> i != toggleIndex }
            .maxOfOrNull { measure.paint.measureText(it) } ?: 0f
        val toggleTextWidth = items.getOrNull(toggleIndex)?.let {
            measure.paint.measureText(it)
        } ?: 0f
        val ordinaryWidth = ceil(ordinaryTextWidth.toDouble()).toInt() + dp(a, 37)
        // 10dp panel + 12dp left + 4dp gap + 38dp checkbox + 6dp right.
        val checkableWidth = ceil(toggleTextWidth.toDouble()).toInt() + dp(a, 70)
        val availableWidth = a.resources.displayMetrics.widthPixels - dp(a, 24)
        val width = min(availableWidth, max(dp(a, 170), max(ordinaryWidth, checkableWidth)))
        val popup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dp(a, 10).toFloat()
        var actionPending = false
        items.forEachIndexed { index, value ->
            if (index == toggleIndex) {
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(a, 48)
                    setPadding(dp(a, 12), 0, dp(a, 6), 0)
                    contentDescription = value + if (checked) "、オン" else "、オフ"
                }
                applyMenuTouch(a, row)
                val title = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxWidth = (width - dp(a, 70)).coerceAtLeast(dp(a, 76))
                }
                row.addView(title, LinearLayout.LayoutParams(-2, -2))
                row.addView(View(a), LinearLayout.LayoutParams(dp(a, 4), 1))
                val checkbox = CheckBox(a).apply {
                    isChecked = checked
                    buttonTintList = ColorStateList.valueOf(a.getColor(R.color.accent))
                    minimumWidth = 0
                    minimumHeight = 0
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnCheckedChangeListener { _, enabled ->
                        row.contentDescription = value + if (enabled) "、オン" else "、オフ"
                        onToggle(enabled)
                    }
                }
                row.addView(checkbox, LinearLayout.LayoutParams(dp(a, 38), dp(a, 48)))
                row.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
                // The checkbox reflects the saved value without changing the row frame.
                panel.addView(row, LinearLayout.LayoutParams(-1, -2))
            } else {
                val row = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                    minimumHeight = dp(a, 48)
                    setPadding(dp(a, 15), dp(a, 10), dp(a, 12), dp(a, 10))
                    isSingleLine = false
                }
                applyMenuTouch(a, row)
                panel.addView(row, LinearLayout.LayoutParams(-1, -2))
                row.setOnClickListener {
                    if (actionPending) return@setOnClickListener
                    actionPending = true
                    // Let the native ripple start on touch, then act on the next frame.
                    row.postOnAnimation {
                        if (popup.isShowing && !a.isFinishing && !a.isDestroyed) {
                            popup.dismiss()
                            selected.onSelected(index)
                        }
                    }
                }
            }
        }
        popup.showAsDropDown(anchor, 0, -dp(a, 2), Gravity.END)
    }

    private fun dialog(a: Activity, panel: LinearLayout): AlertDialog =
        AlertDialog.Builder(a).create().apply {
            setView(panel)
            create()
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.CENTER)
                setLayout(panelWidth(a, 420), WindowManager.LayoutParams.WRAP_CONTENT)
            }
            show()
        }

    internal fun formPanel(a: Activity): LinearLayout = column(a, 12)

    internal fun formTitle(a: Activity, label: String): TextView = title(a, label)

    internal fun formLabel(a: Activity, label: String): TextView =
        text(a, label, 12, a.getColor(R.color.text_secondary)).apply {
            setPadding(dp(a, 2), dp(a, 8), dp(a, 2), dp(a, 5))
        }

    internal fun formInput(a: Activity, value: String): android.widget.EditText =
        android.widget.EditText(a).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(a.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.input_background)
            setPadding(dp(a, 12), 0, dp(a, 12), 0)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setText(value)
        }

    internal fun formButton(a: Activity, label: String, outlined: Boolean = true): TextView =
        text(a, label, 14, a.getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(a, 8), 0, dp(a, 8), 0)
            background = if (outlined) panelBackground(a, 9).apply { setColor(a.getColor(R.color.button_surface)) }
            else GradientDrawable().apply {
                setColor(a.getColor(R.color.button_surface))
                cornerRadius = dp(a, 9).toFloat()
            }
            applyTouch(a, this)
        }

    internal fun formAction(a: Activity, label: String): TextView =
        text(a, label, 14, a.getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(a, 9), 0, dp(a, 9), 0)
            applyTouch(a, this)
        }

    internal fun showForm(a: Activity, panel: LinearLayout): AlertDialog = dialog(a, panel)

    private fun title(a: Activity, name: String): TextView =
        text(a, name, 17, a.getColor(R.color.text_primary)).apply {
            setPadding(dp(a, 8), dp(a, 4), dp(a, 8), dp(a, 11))
        }

    private fun addClose(a: Activity, panel: LinearLayout, holder: Array<AlertDialog?>) {
        // The close button is right-aligned, but its clickable/ripple area is
        // only the button itself, not the full width of the dialog.
        val actions = LinearLayout(a).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val close = text(a, "閉じる", 14, a.getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(a, 16), dp(a, 3), dp(a, 16), dp(a, 3))
            minimumWidth = dp(a, 80)
            minimumHeight = dp(a, 46)
        }
        applyTouch(a, close)
        actions.addView(close, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 46)
        ))
        panel.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        var closing = false
        close.setOnClickListener {
            if (closing) return@setOnClickListener
            closing = true
            close.postOnAnimation { holder[0]?.takeIf { it.isShowing }?.dismiss() }
        }
    }

    @JvmStatic
    fun choose(
        a: Activity, heading: String, labels: Array<String>,
        selectedIndex: Int, onChoice: Selection
    ) {
        if (a.isFinishing || a.isDestroyed) return
        val panel = column(a, 10)
        panel.addView(title(a, heading))
        val holder = arrayOfNulls<AlertDialog>(1)
        var actionPending = false
        labels.forEachIndexed { index, value ->
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(a, 48)
            }
            applyMenuTouch(a, row)
            val option = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                setPadding(dp(a, 10), dp(a, 8), 0, dp(a, 8))
            }
            row.addView(option, LinearLayout.LayoutParams(0, -2, 1f))
            val check = text(a, if (index == selectedIndex) "✓" else "", 19,
                a.getColor(R.color.accent)).apply { gravity = Gravity.CENTER }
            row.addView(check, LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)))
            panel.addView(row)
            row.setOnClickListener {
                if (actionPending) return@setOnClickListener
                actionPending = true
                check.text = "✓"
                row.postOnAnimation {
                    if (holder[0]?.isShowing == true && !a.isFinishing && !a.isDestroyed) {
                        holder[0]?.dismiss()
                        onChoice.onSelected(index)
                    }
                }
            }
        }
        addClose(a, panel, holder)
        holder[0] = dialog(a, panel)
    }

    @JvmStatic
    fun chooseWithAlways(
        a: Activity, heading: String, labels: Array<String>, onChoice: RememberSelection
    ) {
        if (a.isFinishing || a.isDestroyed) return
        val panel = column(a, 10)
        panel.addView(title(a, heading))
        val holder = arrayOfNulls<AlertDialog>(1)
        var actionPending = false
        val always = CheckBox(a).apply {
            text = "次回から確認しない"
            textSize = 14f
            setTextColor(a.getColor(R.color.text_primary))
            buttonTintList = ColorStateList.valueOf(a.getColor(R.color.accent))
            setPadding(dp(a, 10), 0, dp(a, 8), 0)
            minimumHeight = dp(a, 48)
        }
        labels.forEachIndexed { index, value ->
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(a, 48)
            }
            applyMenuTouch(a, row)
            val option = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8))
            }
            row.addView(option, LinearLayout.LayoutParams(-1, -2))
            panel.addView(row)
            row.setOnClickListener {
                if (actionPending) return@setOnClickListener
                actionPending = true
                val checked = always.isChecked
                row.postOnAnimation {
                    if (holder[0]?.isShowing == true && !a.isFinishing && !a.isDestroyed) {
                        holder[0]?.dismiss()
                        onChoice.onSelected(index, checked)
                    }
                }
            }
        }
        panel.addView(always)
        addClose(a, panel, holder)
        holder[0] = dialog(a, panel)
    }

    @JvmStatic
    fun confirm(
        a: Activity, heading: String, message: String, affirmative: String,
        onConfirm: Runnable
    ) = confirm(a, heading, message, affirmative, onConfirm, null)

    @JvmStatic
    fun confirm(
        a: Activity, heading: String, message: String, affirmative: String,
        onConfirm: Runnable, onDismiss: Runnable?
    ) {
        if (a.isFinishing || a.isDestroyed) return
        val panel = column(a, 14)
        panel.addView(title(a, heading))
        val details = text(a, message, 14, a.getColor(R.color.text_primary)).apply {
            setPadding(dp(a, 8), dp(a, 2), dp(a, 8), dp(a, 16))
        }
        panel.addView(details)
        val buttons = LinearLayout(a).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        panel.addView(buttons)
        val holder = arrayOfNulls<AlertDialog>(1)
        val cancel = text(a, "キャンセル", 14, a.getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(a, 48)
            setPadding(dp(a, 14), 0, dp(a, 14), 0)
        }
        applyTouch(a, cancel)
        buttons.addView(cancel)
        cancel.setOnClickListener { holder[0]?.dismiss() }
        val apply = text(a, affirmative, 14, a.getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            minimumHeight = dp(a, 48)
            setPadding(dp(a, 14), 0, dp(a, 14), 0)
        }
        applyTouch(a, apply)
        buttons.addView(apply)
        apply.setOnClickListener {
            holder[0]?.dismiss()
            onConfirm.run()
        }
        holder[0] = dialog(a, panel)
        if (onDismiss != null) holder[0]?.setOnDismissListener { onDismiss.run() }
    }

    private fun aboutLine(a: Activity, panel: LinearLayout, line: String, color: Int) {
        panel.addView(text(a, line, 13, color).apply {
            setPadding(dp(a, 8), dp(a, 2), dp(a, 8), dp(a, 5))
        })
    }

    private fun aboutHeading(a: Activity, panel: LinearLayout, heading: String) {
        panel.addView(text(a, heading, 13, a.getColor(R.color.text_primary)).apply {
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(a, 8), dp(a, 10), dp(a, 8), dp(a, 4))
        })
    }

    private fun aboutLink(a: Activity, panel: LinearLayout, label: String, uri: String) {
        val link = text(a, label + "  ↗", 13, a.getColor(R.color.accent)).apply {
            setPadding(dp(a, 8), dp(a, 7), dp(a, 8), dp(a, 7))
            minimumHeight = dp(a, 42)
        }
        applyTouch(a, link)
        panel.addView(link)
        link.setOnClickListener {
            try {
                a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: Exception) {
                // No browser installed, no action.
            }
        }
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun showAbout(a: Activity) {
        if (a.isFinishing || a.isDestroyed) return
        var version = "不明"
        try {
            version = PackageQueries.packageInfo(a.packageManager, a.packageName).versionName ?: "不明"
        } catch (_: Exception) { }
        val panel = column(a, 12)
        panel.addView(title(a, "必死チェッカーdroid +"))
        panel.addView(text(a, "Hissi Checker Droid Plus  /  v" + version,
            13, a.getColor(R.color.text_secondary)).apply {
            setPadding(dp(a, 8), 0, dp(a, 8), dp(a, 9))
        })
        val scroll = ScrollView(a).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
        }
        val bodyHeight = min(dp(a, 420),
            (a.resources.displayMetrics.heightPixels * 0.57f).toInt())
        panel.addView(scroll, LinearLayout.LayoutParams(-1, bodyHeight))
        val content = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        val secondary = a.getColor(R.color.text_secondary)
        aboutHeading(a, content, "アプリについて")
        aboutLine(a, content,
            "5chとエッヂのID別投稿を、レス・スレ・名前ごとに確認できる非公式のAndroidアプリです。独立して開発しています。", secondary)
        aboutHeading(a, content, "主な機能")
        aboutLine(a, content,
            "・5ch・エッヂのID別投稿検索（日付指定あり／なし）\n" +
                "・レス・スレ・名前の一覧、投稿のコピー、時間帯別投稿グラフ、エッヂの書き込み分析\n" +
                "・板名・板コード・カテゴリ検索、板のお気に入り、オフライン板一覧\n" +
                "・履歴・お気に入りの絞り込み、並べ替え、複数選択削除\n" +
                "・ChMate・Haiagaru・対応する専ブラ・ブラウザとの連携", secondary)
        aboutHeading(a, content, "過去ログ検索")
        aboutLine(a, content,
            "スレタイから過去ログを検索し、結果をアプリ内で閲覧できます。\n" +
                ArchiveProvider.entries.joinToString("\n") { "・" + it.label } +
                "\nサービス別の詳細条件、ページ指定、検索履歴、板のお気に入り、外部ブラウザに対応しています。", secondary)
        aboutHeading(a, content, "表示・操作設定")
        aboutLine(a, content,
            "ダーク／ライト／端末の設定に合わせる、タブ切替アニメーション、ナビゲーションバーの表示項目、過去ログ検索欄の上下配置、履歴保存件数（100～10,000件・無制限）を変更できます。", secondary)
        aboutHeading(a, content, "アプリ情報")
        aboutLine(a, content,
            "バージョン：" + version + "\n対応OS：Android 8.0以降\nアプリID：" +
                a.packageName + "\n開発：chikuwadon", secondary)
        aboutHeading(a, content, "データと通信")
        aboutLine(a, content,
            "ID検索履歴、過去ログ検索履歴、板・検索結果のお気に入り、設定、板一覧キャッシュは端末内に保存されます。独自のアカウント登録・同期・広告SDKはありません。検索結果は外部サイトの仕様に依存し、hissi.orgではHTTP通信を使用する場合があります。", secondary)
        aboutHeading(a, content, "開発とライセンス")
        aboutLine(a, content,
            "オリジナルの必死チェッカーdroidを参考に独立して開発しています。\n本プロジェクト：MIT License\nアプリ本体：Kotlin／AndroidX\nHTML解析：jsoup（MIT License）\nAndroidX（Apache License 2.0）\n掲示板や外部アプリの公式製品ではありません。", secondary)
        aboutHeading(a, content, "プロジェクト")
        aboutLink(a, content, "GitHub リポジトリ",
            "https://github.com/chikuwadon/hissi-checker-droid-plus")
        aboutLink(a, content, "変更履歴",
            "https://github.com/chikuwadon/hissi-checker-droid-plus/blob/main/CHANGELOG.md")
        aboutLink(a, content, "プライバシーポリシー",
            "https://github.com/chikuwadon/hissi-checker-droid-plus/blob/main/PRIVACY.md")
        aboutLink(a, content, "第三者ライセンス情報",
            "https://github.com/chikuwadon/hissi-checker-droid-plus/blob/main/THIRD_PARTY_NOTICES.md")
        val holder = arrayOfNulls<AlertDialog>(1)
        addClose(a, panel, holder)
        holder[0] = dialog(a, panel)
    }
}
