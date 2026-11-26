package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.ColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageButton
import android.widget.TextView

/** Shared title typography and native vector toolbar actions. */
object AppBar {
    enum class Icon { SEARCH, SELECT, DONE, MORE }

    fun title(a: Activity, label: String, padded: Boolean = true): TextView =
        TextView(a).apply {
            text = label
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(a.getColor(R.color.text_primary))
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            if (padded) setPadding(MainNavigation.dp(a, 16), 0, MainNavigation.dp(a, 16), 0)
        }

    fun action(a: Activity, icon: Icon, description: String): ImageButton =
        ImageButton(a).apply {
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = description
            setIcon(a, this, icon, false)
            TouchFeedback.foreground(this, 10)
        }

    fun setIcon(a: Activity, view: ImageButton, icon: Icon, active: Boolean) {
        view.setImageDrawable(Symbol(icon, a.getColor(if (active) R.color.accent else R.color.text_secondary),
            MainNavigation.dp(a, 24)))
        view.isSelected = active
    }

    private class Symbol(private val icon: Icon, color: Int, private val size: Int) : Drawable() {
        private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            fun line(x: Float, y: Float, x2: Float, y2: Float) = canvas.drawLine(x, y, x2, y2, pen)
            fun check(x: Float, y: Float) {
                val path = Path().apply { moveTo(x, y); lineTo(x + 3f, y + 3f); lineTo(x + 8f, y - 3f) }
                canvas.drawPath(path, pen)
            }
            when (icon) {
                Icon.SEARCH -> { canvas.drawCircle(10.5f, 10.5f, 6.8f, pen); line(15.5f, 15.5f, 21f, 21f) }
                Icon.SELECT -> {
                    canvas.drawRoundRect(3f, 4f, 10f, 11f, 1.2f, 1.2f, pen)
                    canvas.drawRoundRect(3f, 14f, 10f, 21f, 1.2f, 1.2f, pen)
                    line(14f, 7.5f, 21f, 7.5f)
                    line(14f, 17.5f, 21f, 17.5f)
                }
                Icon.MORE -> {
                    pen.style = Paint.Style.FILL
                    for (y in listOf(5f, 12f, 19f)) canvas.drawCircle(12f, y, 1.7f, pen)
                    pen.style = Paint.Style.STROKE
                }
                Icon.DONE -> { val path = Path().apply { moveTo(4f, 12f); lineTo(10f, 18f); lineTo(21f, 6f) }; canvas.drawPath(path, pen) }
            }
            canvas.restore()
        }
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) { pen.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { pen.colorFilter = filter; invalidateSelf() }
        @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
