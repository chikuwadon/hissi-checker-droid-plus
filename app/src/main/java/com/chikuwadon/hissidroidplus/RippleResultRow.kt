package com.chikuwadon.hissidroidplus

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Drives a row-wide touch-origin ripple even when child text views consume
 * ACTION_DOWN and handle their own taps / selectable-text long presses.
 *
 * A parent OnTouchListener is not invoked in that case; dispatchTouchEvent is.
 * Returning super's result preserves vertical scrolling and swipe interception.
 */
class RippleResultRow(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressMs = SelectableResultTextView.selectionDelayMs(
        ViewConfiguration.getLongPressTimeout()
    ).toLong()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private val clearPress = Runnable { isPressed = false }

    init {
        TouchFeedback.foreground(this, 6)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(clearPress)
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                moved = false
                (foreground as? RippleDrawable)?.setHotspot(event.x, event.y)
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    moved = true
                    isPressed = false
                }
                if (event.eventTime - downTime >= longPressMs) {
                    isPressed = false
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                moved = true
                removeCallbacks(clearPress)
                isPressed = false
            }
        }

        // Let the selectable TextView own long press, and let ListView /
        // SwipeTabContainer intercept vertical or horizontal scrolling.
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            removeCallbacks(clearPress)
            if (!moved && event.eventTime - downTime < longPressMs) {
                // Keep the ripple visible briefly after a short tap.
                isPressed = true
                postDelayed(clearPress, 160L)
            } else {
                isPressed = false
            }
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clearPress)
        isPressed = false
        super.onDetachedFromWindow()
    }
}
