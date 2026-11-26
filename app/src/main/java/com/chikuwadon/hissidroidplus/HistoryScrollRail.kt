package com.chikuwadon.hissidroidplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.AbsListView
import android.widget.ListView
import kotlin.math.roundToInt

/**
 * Right-hand fast-scroll lane. Its View bounds are exactly the row's 12dp
 * end padding, so the platform's expanded FastScroller hit box is unnecessary.
 */
internal class HistoryScrollRail(context: Context, private val list: ListView) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging = false
    private var shown = false
    private val hide = Runnable { if (!dragging) { shown = false; invalidate() } }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, state: Int) {
                if (state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) reveal()
            }
            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) {
                if (total > visible) reveal() else invalidate()
            }
        })
    }

    private fun dp(n: Int): Float = n * resources.displayMetrics.density
    private fun maxPosition(): Int = (list.count - list.childCount.coerceAtLeast(1)).coerceAtLeast(0)
    private fun scrollable(): Boolean = list.canScrollVertically(1) || list.canScrollVertically(-1)

    private fun reveal() {
        shown = true
        invalidate()
        removeCallbacks(hide)
        if (!dragging) postDelayed(hide, 900L)
    }

    private fun thumbHeight(): Float {
        if (height <= 0) return 0f
        val fraction = list.childCount.toFloat() / list.count.coerceAtLeast(1)
        return (height * fraction).coerceIn(dp(32).coerceAtMost(height.toFloat()), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if ((!shown && !dragging) || !scrollable() || height == 0) return
        val thumb = thumbHeight()
        val fraction = if (maxPosition() == 0) 0f else
            (list.firstVisiblePosition.toFloat() / maxPosition()).coerceIn(0f, 1f)
        val top = (height - thumb) * fraction
        paint.color = context.getColor(if (dragging) R.color.accent else R.color.text_secondary)
        paint.alpha = if (dragging) 255 else 145
        val right = width - dp(2)
        val left = (right - dp(if (dragging) 7 else 4)).coerceAtLeast(0f)
        canvas.drawRoundRect(left, top, right, top + thumb, dp(2), dp(2), paint)
    }

    private fun seek(y: Float) {
        val range = (height - thumbHeight()).coerceAtLeast(1f)
        val fraction = ((y - thumbHeight() / 2f) / range).coerceIn(0f, 1f)
        list.setSelectionFromTop((maxPosition() * fraction).roundToInt(), 0)
        reveal()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scrollable() || event.x < 0f || event.x >= width) return false
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                seek(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) { seek(event.y); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                reveal()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }
}
