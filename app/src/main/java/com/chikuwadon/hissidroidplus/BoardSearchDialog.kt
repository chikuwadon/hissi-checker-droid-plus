package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Shared board search UI for Home and Archive; category navigation stays with the caller. */
internal object BoardSearchDialog {
    private fun dp(host: Activity, value: Int) = MainNavigation.dp(host, value)

    fun icon(host: Activity): View = object : View(host) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = host.getColor(R.color.text_secondary)
            style = Paint.Style.STROKE
            strokeWidth = dp(host, 2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            val x = width / 2f - dp(host, 2)
            val y = height / 2f - dp(host, 2)
            canvas.drawCircle(x, y, dp(host, 7).toFloat(), paint)
            canvas.drawLine(x + dp(host, 5), y + dp(host, 5), x + dp(host, 11), y + dp(host, 11), paint)
        }
    }.apply {
        contentDescription = "板を検索"
        isFocusable = true
        TouchFeedback.foreground(this, 8)
    }

    fun show(host: Activity, groups: List<BoardMenu.Category>, back: () -> Unit, pick: (BoardPickerSearch.Entry) -> Unit) {
        if (host.isFinishing || host.isDestroyed) return
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, "板を検索"))
        val input = ThemedMenu.formInput(host, "").apply {
            hint = "板名・板コード"
            setHintTextColor(host.getColor(R.color.text_secondary))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(host, 44)))
        val status = ThemedMenu.formLabel(host, "")
        panel.addView(status)
        val index = BoardPickerSearch.index(groups)
        var hits = index
        val rows = object : BaseAdapter() {
            override fun getCount() = hits.size
            override fun getItem(position: Int) = hits[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convert: View?, parent: ViewGroup): View {
                val label = convert as? TextView ?: TextView(host).apply {
                    textSize = 14f
                    setTextColor(host.getColor(R.color.text_primary))
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(host, 49)
                    setPadding(dp(host, 12), dp(host, 6), dp(host, 12), dp(host, 6))
                }
                val entry = getItem(position)
                label.text = entry.name + " (" + entry.code + ")"
                return label
            }
        }
        val choices = ListView(host).apply {
            divider = android.graphics.drawable.ColorDrawable(host.getColor(R.color.divider))
            dividerHeight = dp(host, 1)
            selector = TouchFeedback.listSelector(host)
            adapter = rows
        }
        panel.addView(choices, LinearLayout.LayoutParams(-1,
            minOf(dp(host, 280), (host.resources.displayMetrics.heightPixels * 0.30f).toInt())))
        val close = ThemedMenu.formAction(host, "カテゴリに戻る")
        panel.addView(close, LinearLayout.LayoutParams(-1, dp(host, 46)))
        val dialog = ThemedMenu.showForm(host, panel)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        fun filter() {
            hits = BoardPickerSearch.filter(index, input.text.toString())
            status.text = hits.size.toString() + "板"
            rows.notifyDataSetChanged()
            choices.setSelection(0)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = filter()
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                (host.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(input.windowToken, 0)
                true
            } else false
        }
        choices.setOnItemClickListener { _, _, position, _ ->
            val entry = hits.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            pick(entry)
        }
        close.setOnClickListener { dialog.dismiss(); back() }
        filter()
        input.requestFocus()
        input.post {
            if (dialog.isShowing) (host.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

}
