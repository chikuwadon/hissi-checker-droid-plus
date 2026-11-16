package com.chikuwadon.hissidroidplus

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * Intercepts horizontal gestures at the container dispatch boundary.
 *
 * Selectable TextViews consume ACTION_DOWN themselves, so a ListView touch
 * listener cannot consistently receive their MOVE/UP events. Dispatching
 * through this parent guarantees horizontal swipe recognition over every row,
 * while untouched vertical gestures and long presses reach the native ListView.
 */
class SwipeTabContainer(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    interface Callback {
        fun onStart(dx: Float)
        fun onDrag(dx: Float)
        fun onRelease(dx: Float, dy: Float, velocityX: Float)
        fun onCancel()
    }

    var callback: Callback? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val horizontalSlop = max(touchSlop * 1.25f, context.resources.displayMetrics.density * 9f)
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var horizontal = false
    private var vertical = false
    private var velocity: VelocityTracker? = null

    private fun clearTracker() {
        velocity?.recycle()
        velocity = null
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearTracker()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                horizontal = false
                vertical = false
                // Always own the gesture sequence, even when the list is empty.
                super.dispatchTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                vertical = true
                clearTracker()
                if (horizontal) {
                    horizontal = false
                    callback?.onCancel()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1) return super.dispatchTouchEvent(event)
                velocity?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!horizontal && !vertical) {
                    // Preserve normal text selection after a stationary long press.
                    if (event.eventTime - downTime >= longPressMs) {
                        vertical = true
                    } else if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                        vertical = true
                    } else if (abs(dx) >= horizontalSlop &&
                        abs(dx) > abs(dy) * 1.15f
                    ) {
                        horizontal = true
                        // Cancel the child tap/selection before taking ownership.
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        try {
                            super.dispatchTouchEvent(cancel)
                        } finally {
                            cancel.recycle()
                        }
                        callback?.onStart(dx)
                    }
                }
                if (horizontal) {
                    callback?.onDrag(dx)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val owned = horizontal
                horizontal = false
                if (owned) {
                    velocity?.addMovement(event)
                    velocity?.computeCurrentVelocity(1000)
                    callback?.onRelease(
                        event.x - downX, event.y - downY, velocity?.xVelocity ?: 0f
                    )
                    clearTracker()
                    return true
                }
                clearTracker()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (horizontal) callback?.onCancel()
                horizontal = false
                clearTracker()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        if (horizontal) callback?.onCancel()
        horizontal = false
        clearTracker()
        super.onDetachedFromWindow()
    }
}
