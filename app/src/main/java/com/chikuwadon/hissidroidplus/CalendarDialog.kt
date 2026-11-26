package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact calendar using the same palette and rounded dialog shell as the app. */
object CalendarDialog {
    private val monthFormat = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPAN)

    fun show(a: Activity, initial: LocalDate, onSelected: (LocalDate) -> Unit) {
        if (a.isFinishing || a.isDestroyed) return
        fun dp(n: Int) = MainNavigation.dp(a, n)
        val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
        var selected = initial
        var month = YearMonth.from(initial)
        val panel = ThemedMenu.formPanel(a)
        panel.addView(ThemedMenu.formTitle(a, "日付を選択"))
        val content = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(a).apply { addView(content) }, LinearLayout.LayoutParams(-1, -2))
        val summary = TextView(a).apply {
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(a.getColor(R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        content.addView(summary, LinearLayout.LayoutParams(-1, -2))
        val header = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        content.addView(header, LinearLayout.LayoutParams(-1, dp(48)))
        val previous = ThemedMenu.formAction(a, "‹").apply {
            textSize = 26f
            contentDescription = "前の月"
        }
        header.addView(previous, LinearLayout.LayoutParams(dp(44), -1))
        val monthLabel = ThemedMenu.formButton(a, "")
        monthLabel.contentDescription = "年月を選択"
        header.addView(monthLabel, LinearLayout.LayoutParams(0, dp(40), 1f))
        val next = ThemedMenu.formAction(a, "›").apply {
            textSize = 26f
            contentDescription = "次の月"
        }
        header.addView(next, LinearLayout.LayoutParams(dp(44), -1))
        val weekdays = LinearLayout(a)
        content.addView(weekdays, LinearLayout.LayoutParams(-1, dp(30)))
        listOf("日", "月", "火", "水", "木", "金", "土").forEach { day ->
            weekdays.addView(TextView(a).apply {
                text = day
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(a.getColor(R.color.text_secondary))
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
        fun render() {
            summary.text = selected.format(dateFormat)
            monthLabel.text = month.format(monthFormat) + " ▾"
            monthLabel.contentDescription = month.format(monthFormat) + "、年月を選択"
            grid.removeAllViews()
            val start = month.atDay(1).dayOfWeek.value % 7
            for (week in 0 until 6) {
                val row = LinearLayout(a)
                grid.addView(row, LinearLayout.LayoutParams(-1, dp(44)))
                for (column in 0 until 7) {
                    val number = week * 7 + column - start + 1
                    if (number !in 1..month.lengthOfMonth()) {
                        row.addView(View(a), LinearLayout.LayoutParams(0, -1, 1f))
                        continue
                    }
                    val date = month.atDay(number)
                    val active = date == selected
                    val cell = TextView(a).apply {
                        text = number.toString()
                        textSize = 14f
                        gravity = Gravity.CENTER
                        contentDescription = date.format(dateFormat)
                        isSelected = active
                        setTextColor(a.getColor(if (active) R.color.accent else R.color.text_primary))
                        if (active || date == today) {
                            background = GradientDrawable().apply {
                                cornerRadius = dp(20).toFloat()
                                setColor(a.getColor(if (active) R.color.panel_pressed else R.color.panel))
                                setStroke(dp(1), a.getColor(R.color.accent))
                            }
                        }
                        TouchFeedback.foreground(this, 20)
                    }
                    row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    })
                    cell.setOnClickListener { selected = date; render() }
                }
            }
        }
        previous.setOnClickListener { month = month.minusMonths(1); render() }
        next.setOnClickListener { month = month.plusMonths(1); render() }
        monthLabel.setOnClickListener {
            // Year and month selectors remain compact even for long archive ranges.
            val firstYear = minOf(1900, month.year)
            val lastYear = maxOf(today.year + 10, month.year)
            val picker = ThemedMenu.formPanel(a)
            picker.addView(ThemedMenu.formTitle(a, "年月を選択"))
            val year = android.widget.NumberPicker(a).apply {
                minValue = firstYear
                maxValue = lastYear
                value = month.year
                wrapSelectorWheel = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    setTextColor(a.getColor(R.color.text_primary))
                    setSelectionDividerHeight(dp(1))
                }
            }
            val monthNumber = android.widget.NumberPicker(a).apply {
                minValue = 1
                maxValue = 12
                value = month.monthValue
                wrapSelectorWheel = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    setTextColor(a.getColor(R.color.text_primary))
                    setSelectionDividerHeight(dp(1))
                }
            }
            val numbers = LinearLayout(a).apply { gravity = Gravity.CENTER }
            numbers.addView(year, LinearLayout.LayoutParams(0, dp(160), 1f))
            numbers.addView(monthNumber, LinearLayout.LayoutParams(0, dp(160), 1f))
            picker.addView(numbers)
            val buttons = LinearLayout(a).apply { gravity = Gravity.END }
            val cancel = ThemedMenu.formAction(a, "キャンセル")
            val accept = ThemedMenu.formAction(a, "選択")
            buttons.addView(cancel, LinearLayout.LayoutParams(-2, dp(48)))
            buttons.addView(accept, LinearLayout.LayoutParams(-2, dp(48)))
            picker.addView(buttons)
            val dialog = ThemedMenu.showForm(a, picker)
            cancel.setOnClickListener { dialog.dismiss() }
            accept.setOnClickListener {
                month = YearMonth.of(year.value, monthNumber.value)
                render()
                dialog.dismiss()
            }
        }
        val actions = LinearLayout(a).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val todayButton = ThemedMenu.formAction(a, "今日")
        actions.addView(todayButton, LinearLayout.LayoutParams(-2, dp(48)))
        actions.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        val cancel = ThemedMenu.formAction(a, "キャンセル")
        val accept = ThemedMenu.formAction(a, "選択")
        actions.addView(cancel, LinearLayout.LayoutParams(-2, dp(48)))
        actions.addView(accept, LinearLayout.LayoutParams(-2, dp(48)))
        panel.addView(actions)
        render()
        val dialog = ThemedMenu.showForm(a, panel)
        todayButton.setOnClickListener { selected = today; month = YearMonth.from(today); render() }
        cancel.setOnClickListener { dialog.dismiss() }
        accept.setOnClickListener {
            dialog.dismiss()
            onSelected(selected)
        }
    }
}
