package com.chikuwadon.hissidroidplus

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/**
 * Preserve native selectable text and responsive row clicks.
 * Defer platform long clicks so deliberate taps do not start selection.
 */
class SelectableResultTextView(context: Context, attrs: AttributeSet?) : TextView(context, attrs) {
    companion object {
        const val MIN_LONG_PRESS_MS = 700
        @JvmStatic
        fun selectionDelayMs(systemTimeout: Int): Int =
            max(MIN_LONG_PRESS_MS, systemTimeout + 150)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchActive = false
    private var moved = false
    private var selectionStarted = false
    private var dispatchNativeLongClick = false
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var pendingLongClick: Runnable? = null
    private var selectionRefreshGeneration = 0

    private fun clearPending() {
        pendingLongClick?.let { removeCallbacks(it) }
        pendingLongClick = null
    }

    override fun performLongClick(): Boolean {
        if (dispatchNativeLongClick || !touchActive) return super.performLongClick()
        if (moved) return false
        val elapsed = SystemClock.uptimeMillis() - downTime
        val wait = selectionDelayMs(ViewConfiguration.getLongPressTimeout()) - elapsed
        if (wait <= 0) {
            selectionStarted = true
            return super.performLongClick()
        }
        if (pendingLongClick == null) {
            val x = downX
            val y = downY
            val runnable = Runnable {
                pendingLongClick = null
                if (!touchActive || moved || !isAttachedToWindow) return@Runnable
                selectionStarted = true
                dispatchNativeLongClick = true
                try {
                    performLongClick(x, y)
                } finally {
                    dispatchNativeLongClick = false
                }
            }
            pendingLongClick = runnable
            postDelayed(runnable, wait)
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearPending()
                touchActive = true
                moved = false
                selectionStarted = false
                downTime = event.eventTime
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchActive &&
                    (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)
                ) {
                    moved = true
                    clearPending()
                }
            }
            MotionEvent.ACTION_UP -> {
                val ordinaryTap = touchActive && !moved && !selectionStarted
                touchActive = false
                clearPending()
                if (ordinaryTap) {
                    // CANCEL prevents a duplicate native click or long press after our click.
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    try {
                        super.onTouchEvent(cancel)
                    } finally {
                        cancel.recycle()
                    }
                    performClick()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                touchActive = false
                clearPending()
            }
        }
        return super.onTouchEvent(event)
    }

    /** Recreate selection controllers when a recycled row is attached and rebound. */
    fun refreshSelectionAfterRebind() {
        clearPending()
        touchActive = false
        selectionStarted = false
        val generation = ++selectionRefreshGeneration
        if (!isTextSelectable) return
        post {
            if (generation != selectionRefreshGeneration ||
                !isAttachedToWindow || touchActive || !isTextSelectable
            ) return@post
            setTextIsSelectable(false)
            setTextIsSelectable(true)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSelectionAfterRebind()
    }

    override fun onDetachedFromWindow() {
        selectionRefreshGeneration++
        touchActive = false
        clearPending()
        super.onDetachedFromWindow()
    }
}
