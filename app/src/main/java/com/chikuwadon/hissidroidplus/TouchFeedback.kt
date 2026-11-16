package com.chikuwadon.hissidroidplus

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View

/**
 * Touch-position-based expanding ripple, shared by menus, tabs and actions.
 * Foreground usage leaves existing backgrounds, tab underlines and borders intact.
 */
object TouchFeedback {
    private fun dp(context: Context, value: Int): Float =
        context.resources.displayMetrics.density * value

    @JvmStatic
    fun ripple(context: Context, cornerDp: Int = 8): RippleDrawable {
        val accent = context.getColor(R.color.accent)
        val rippleColor = Color.argb(75, Color.red(accent), Color.green(accent), Color.blue(accent))
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(context, cornerDp)
        }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            ColorDrawable(Color.TRANSPARENT),
            mask
        )
    }

    /** Overlay, rather than replace, an existing background. */
    @JvmStatic
    fun foreground(view: View, cornerDp: Int = 8) {
        view.foreground = ripple(view.context, cornerDp)
    }

    /**
     * Some tab labels do not remain pressed long enough for a visible ripple
     * after ListView content changes. Pulse only the foreground drawable after
     * the click dispatch; never change the tab background / underline colors.
     */
    @JvmStatic
    fun pulse(view: View) {
        val ripple = view.foreground as? RippleDrawable ?: return
        view.post {
            if (!view.isAttachedToWindow || view.foreground !== ripple) return@post
            ripple.setHotspot(view.width / 2f, view.height / 2f)
            ripple.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
            view.invalidate()
            view.postDelayed({
                if (view.foreground === ripple) {
                    ripple.state = intArrayOf(android.R.attr.state_enabled)
                    view.invalidate()
                }
            }, 170L)
        }
    }

    /** A ripple to use as a ListView selector without losing its list gestures. */
    @JvmStatic
    fun listSelector(context: Context): RippleDrawable = ripple(context, 6)
}
