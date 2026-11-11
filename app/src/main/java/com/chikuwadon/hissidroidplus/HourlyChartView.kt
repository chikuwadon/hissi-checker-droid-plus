package com.chikuwadon.hissidroidplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Lightweight local histogram: six-hour guides and selectable hourly counts. */
class HourlyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hours = IntArray(24)
    private var selected = -1

    companion object {
        private val TIME = Pattern.compile(
            "(?:\\d{4}/\\d{1,2}/\\d{1,2}\\([^)]*\\)\\s+)?([01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?"
        )
        /** Exact shared centers for bars, guides and labels. */
        @JvmStatic
        fun hourX(hour: Int, width: Float, margin: Float): Float {
            val safeHour = hour.coerceIn(0, 23)
            return margin + (width - 2 * margin) * safeHour / 23f
        }
    }

    init {
        minimumHeight = dp(74)
        isClickable = true
        contentDescription = "時間帯別投稿数グラフ。時間をタップすると投稿数を確認できます"
    }

    private fun dp(amount: Int): Int = (amount * resources.displayMetrics.density).roundToInt()

    fun setPosts(posts: List<CheckerResult.Post>): Int {
        Arrays.fill(hours, 0)
        var total = 0
        for (post in posts) {
            val matched = TIME.matcher(post.meta)
            if (matched.find()) {
                hours[matched.group(1).toInt()]++
                total++
            }
        }
        invalidate()
        return total
    }

    fun setCounts(count: IntArray?) {
        Arrays.fill(hours, 0)
        if (count != null && count.size == 24) count.copyInto(hours, 0, 0, 24)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(dp(74), MeasureSpec.EXACTLY))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val margin = dp(12).toFloat()
                val width = max(1f, this.width - 2 * margin)
                selected = ((event.x - margin) * 23f / width).roundToInt().coerceIn(0, 23)
                contentDescription = String.format(Locale.JAPAN, "%d時台: %d件", selected, hours[selected])
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = this.width
        val height = this.height
        if (width <= 0) return
        val top = dp(17)
        val bottom = height - dp(18)
        val highest = max(1, hours.maxOrNull() ?: 0)
        val margin = dp(12).toFloat()
        val interval = (width - 2 * margin) / 23f
        val guide = resources.getColor(R.color.text_secondary, null)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, resources.displayMetrics.density * 0.7f)
        paint.color = Color.argb(60, Color.red(guide), Color.green(guide), Color.blue(guide))
        val ticks = intArrayOf(0, 6, 12, 18, 23)
        for (hour in ticks) {
            val x = hourX(hour, width.toFloat(), margin)
            canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = resources.getColor(R.color.accent, null)
        val halfBar = max(1f, min(dp(4).toFloat(), interval * 0.38f))
        for (hour in hours.indices) {
            val bar = (bottom - top) * hours[hour] / highest.toFloat()
            if (hours[hour] > 0) {
                val x = hourX(hour, width.toFloat(), margin)
                canvas.drawRoundRect(x-halfBar, bottom-bar, x+halfBar, bottom.toFloat(),
                    dp(1).toFloat(), dp(1).toFloat(), paint)
            }
        }
        paint.color = guide
        paint.textSize = dp(10).toFloat()
        paint.textAlign = Paint.Align.CENTER
        for (hour in ticks) {
            canvas.drawText(hour.toString(), hourX(hour, width.toFloat(), margin),
                (height-dp(3)).toFloat(), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        if (selected >= 0) {
            paint.color = resources.getColor(R.color.text_primary, null)
            paint.textSize = dp(12).toFloat()
            val info = selected.toString()+"時台: "+hours[selected]+"件"
            val x = hourX(selected, width.toFloat(), margin)
            canvas.drawText(info,
                max(dp(2).toFloat(), min(x, width - paint.measureText(info) - dp(2))),
                dp(12).toFloat(), paint)
        }
    }
}
