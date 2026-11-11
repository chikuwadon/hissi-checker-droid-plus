package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
        view.background = RippleDrawable(
            ColorStateList.valueOf(a.getColor(R.color.panel_pressed)),
            ColorDrawable(Color.TRANSPARENT), null
        )
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
        items.forEachIndexed { index, value ->
            val row = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                minHeight = dp(a, 48)
                setPadding(dp(a, 15), dp(a, 10), dp(a, 12), dp(a, 10))
                isSingleLine = false
            }
            applyTouch(a, row)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2))
            row.setOnClickListener {
                popup.dismiss()
                selected.onSelected(index)
            }
        }
        popup.showAsDropDown(anchor, 0, -dp(a, 2), Gravity.END)
    }

    private fun dialog(a: Activity, panel: LinearLayout): AlertDialog =
        AlertDialog.Builder(a).create().apply {
            setView(panel)
            show()
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.CENTER)
                setLayout(panelWidth(a, 420), WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }

    private fun title(a: Activity, name: String): TextView =
        text(a, name, 17, a.getColor(R.color.text_primary)).apply {
            setPadding(dp(a, 8), dp(a, 4), dp(a, 8), dp(a, 11))
        }

    private fun addClose(a: Activity, panel: LinearLayout, holder: Array<AlertDialog?>) {
        val close = text(a, "閉じる", 14, a.getColor(R.color.accent)).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(a, 10), dp(a, 3), dp(a, 12), dp(a, 3))
            minHeight = dp(a, 46)
        }
        applyTouch(a, close)
        panel.addView(close)
        close.setOnClickListener { holder[0]?.dismiss() }
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
        labels.forEachIndexed { index, value ->
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(a, 48)
            }
            applyTouch(a, row)
            val option = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                setPadding(dp(a, 10), dp(a, 8), 0, dp(a, 8))
            }
            row.addView(option, LinearLayout.LayoutParams(0, -2, 1f))
            val check = text(a, if (index == selectedIndex) "✓" else "", 19,
                a.getColor(R.color.accent)).apply { gravity = Gravity.CENTER }
            row.addView(check, LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)))
            panel.addView(row)
            row.setOnClickListener {
                holder[0]?.dismiss()
                onChoice.onSelected(index)
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
        val always = CheckBox(a).apply {
            text = "次回から確認しない"
            textSize = 14f
            setTextColor(a.getColor(R.color.text_primary))
            buttonTintList = ColorStateList.valueOf(a.getColor(R.color.accent))
            setPadding(dp(a, 10), 0, dp(a, 8), 0)
            minHeight = dp(a, 48)
        }
        labels.forEachIndexed { index, value ->
            val row = LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(a, 48)
            }
            applyTouch(a, row)
            val option = text(a, value, 14, a.getColor(R.color.text_primary)).apply {
                setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8))
            }
            row.addView(option, LinearLayout.LayoutParams(-1, -2))
            panel.addView(row)
            row.setOnClickListener {
                val checked = always.isChecked
                holder[0]?.dismiss()
                onChoice.onSelected(index, checked)
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
            minHeight = dp(a, 48)
            setPadding(dp(a, 14), 0, dp(a, 14), 0)
        }
        applyTouch(a, cancel)
        buttons.addView(cancel)
        cancel.setOnClickListener { holder[0]?.dismiss() }
        val apply = text(a, affirmative, 14, a.getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            minHeight = dp(a, 48)
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
            minHeight = dp(a, 42)
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
            version = a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "不明"
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
            "ID検索、板選択、レス・スレ・名前の一覧、投稿のコピー、ChMate連携、時間帯別の投稿グラフ。エッヂのID検索と書き込み分析にも対応しています。", secondary)
        aboutHeading(a, content, "アプリ情報")
        aboutLine(a, content,
            "バージョン：" + version + "\n対応OS：Android 8.0以降\nアプリID：" +
                a.packageName + "\n開発：chikuwadon", secondary)
        aboutHeading(a, content, "データと通信")
        aboutLine(a, content,
            "検索時は指定先サイトへアクセスします。板や検索条件の一部は端末内に保存されます。各サイトとの通信や取得結果は、接続先の仕様に依存します。", secondary)
        aboutHeading(a, content, "開発とライセンス")
        aboutLine(a, content,
            "アプリのコードとアイコンは独自に制作しています。\nHTML解析：jsoup（MIT License）\n掲示板や外部アプリの公式製品ではありません。", secondary)
        aboutHeading(a, content, "プロジェクト")
        aboutLink(a, content, "GitHub リポジトリ",
            "https://github.com/chikuwadon/hissi-checker-droid-plus")
        aboutLink(a, content, "第三者ライセンス情報",
            "https://github.com/chikuwadon/hissi-checker-droid-plus/blob/main/THIRD_PARTY_NOTICES.md")
        val holder = arrayOfNulls<AlertDialog>(1)
        addClose(a, panel, holder)
        holder[0] = dialog(a, panel)
    }
}
