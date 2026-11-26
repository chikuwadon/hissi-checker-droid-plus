package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Lightweight native navigation; detail activities keep their existing toolbar. */
object MainNavigation {
    enum class Destination(val label: String) {
        HOME("ホーム"), ARCHIVE("過去ログ"), HISTORY("履歴"), FAVORITES("お気に入り"), SETTINGS("設定")
    }

    const val FAVORITES_EXTRA = "navigation_favorites"

    fun dp(a: Activity, value: Int): Int =
        (a.resources.displayMetrics.density * value).roundToInt()

    fun attach(a: Activity, root: LinearLayout, selected: Destination) {
        val divider = View(a).apply { setBackgroundColor(a.getColor(R.color.divider)) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(a, 1)))
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(a.getColor(R.color.panel))
            gravity = Gravity.CENTER_VERTICAL
        }
        for (destination in Destination.values()) {
            val active = destination == selected
            val color = a.getColor(if (active) R.color.accent else R.color.text_secondary)
            val item = TextView(a).apply {
                text = destination.label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(color)
                isSingleLine = true
                isSelected = active
                contentDescription = destination.label
                setPadding(0, dp(a, 6), 0, dp(a, 5))
                val symbol = NavIcon(destination, color)
                symbol.setBounds(0, 0, dp(a, 24), dp(a, 24))
                setCompoundDrawables(null, symbol, null, null)
                compoundDrawablePadding = dp(a, 3)
                TouchFeedback.foreground(this, 10)
                setOnClickListener { if (!active) navigate(a, destination) }
            }
            bar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(a, 60)))
    }

    @Suppress("DEPRECATION")
    private fun navigate(a: Activity, destination: Destination) {
        if (a.isFinishing || a.isDestroyed) return
        val intent = when (destination) {
            Destination.HOME -> Intent(a, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            Destination.ARCHIVE -> Intent(a, ArchiveActivity::class.java)
            Destination.HISTORY -> Intent(a, HistoryActivity::class.java)
            Destination.FAVORITES -> Intent(a, HistoryActivity::class.java)
                .putExtra(FAVORITES_EXTRA, true)
            Destination.SETTINGS -> Intent(a, SettingsActivity::class.java)
        }
        // Bottom destinations are peers, not a push/pop detail stack.
        // Suppress the window slide/scale for this explicit navigation only.
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        a.startActivity(intent, ActivityOptions.makeCustomAnimation(a, 0, 0).toBundle())
        if (a !is HomeActivity) a.finish()
        a.overridePendingTransition(0, 0)
    }

    private class NavIcon(private val destination: Destination, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            fun line(x: Float, y: Float, xx: Float, yy: Float) =
                canvas.drawLine(x, y, xx, yy, paint)
            fun path(vararg points: Float) {
                val p = Path()
                p.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1])
                canvas.drawPath(p, paint)
            }
            when (destination) {
                Destination.HOME -> {
                    path(3f, 11f, 12f, 3f, 21f, 11f)
                    path(5f, 10f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 10f)
                }
                Destination.ARCHIVE -> {
                    canvas.drawCircle(10f, 10f, 6.5f, paint)
                    line(15f, 15f, 21f, 21f)
                }
                Destination.HISTORY -> {
                    canvas.drawCircle(12f, 12f, 9f, paint)
                    path(12f, 6f, 12f, 12f, 16f, 14f)
                }
                Destination.FAVORITES -> path(12f, 3f, 15f, 9f, 22f, 10f, 17f, 15f,
                    18f, 22f, 12f, 18f, 6f, 22f, 7f, 15f, 2f, 10f, 9f, 9f, 12f, 3f)
                Destination.SETTINGS -> {
                    canvas.drawCircle(12f, 12f, 4f, paint)
                    canvas.drawCircle(12f, 12f, 8f, paint)
                    for (i in 0 until 8) {
                        canvas.save()
                        canvas.rotate(i * 45f, 12f, 12f)
                        line(12f, 2f, 12f, 4f)
                        canvas.restore()
                    }
                }
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
