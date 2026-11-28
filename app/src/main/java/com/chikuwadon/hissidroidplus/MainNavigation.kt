package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.Context
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
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/** Lightweight native navigation; detail activities keep their existing toolbar. */
object MainNavigation {
    enum class Destination(val label: String) {
        HOME("ホーム"), RESULT("結果"), ARCHIVE("過去ログ"), HISTORY("履歴"), FAVORITES("お気に入り"), SETTINGS("設定")
    }

    const val EXTRA_DESTINATION = "main_navigation_destination"
    const val EXTRA_RESTORE_RESULT = "restore_result_token"
    private var restoreToken: String? = null
    fun consumeRestoreToken(token: String?): Boolean {
        if (token == null || token != restoreToken) return false
        restoreToken = null
        return true
    }
    val hosted = listOf(Destination.HOME, Destination.ARCHIVE, Destination.HISTORY, Destination.FAVORITES, Destination.SETTINGS)

    fun hostDestination(name: String?): Destination =
        hosted.firstOrNull { it.name == name } ?: Destination.HOME

    private const val RESULT_TAG = "main_navigation_result"
    private const val BAR_TAG = "main_navigation_bar"
    private const val DIVIDER_TAG = "main_navigation_divider"
    val configurable = listOf(Destination.ARCHIVE, Destination.HISTORY, Destination.FAVORITES)

    fun isVisible(context: Context, destination: Destination): Boolean =
        destination !in configurable ||
            AppSettings.prefs(context).getBoolean("navigation_visible_" + destination.name, true)

    fun setVisible(context: Context, destination: Destination, visible: Boolean) {
        if (destination !in configurable) return
        AppSettings.prefs(context).edit().putBoolean("navigation_visible_" + destination.name, visible).apply()
    }
    private val results = HashMap<Int, WeakReference<ResultActivity>>()
    private val homes = HashMap<Int, WeakReference<HomeActivity>>()

    fun registerHome(a: HomeActivity) {
        homes[a.taskId] = WeakReference(a)
    }

    fun unregisterHome(a: HomeActivity) {
        if (homes[a.taskId]?.get() === a) homes.remove(a.taskId)
    }

    fun hasHome(a: Activity): Boolean =
        homes[a.taskId]?.get()?.let { !it.isFinishing && !it.isDestroyed } == true

    /** Back from the root home leaves the checker, rather than revealing its retained peer. */
    fun exitFromHome(a: HomeActivity) {
        val retained = result(a)
        if (retained != null) {
            results.remove(a.taskId)
            retained.finish()
        }
        // Finish only our two activities in this task; never finish the caller's task.
        a.finish()
    }

    private fun result(a: Activity): ResultActivity? {
        val current = results[a.taskId]?.get()
        return current?.takeUnless { it.isFinishing || it.isDestroyed }
    }

    fun registerResult(a: ResultActivity) {
        val previous = result(a)
        results[a.taskId] = WeakReference(a)
        if (previous != null && previous !== a) previous.finish()
    }

    fun unregisterResult(a: ResultActivity) {
        if (results[a.taskId]?.get() === a) results.remove(a.taskId)
    }

    fun refresh(a: Activity) {
        val bar = a.window.decorView.findViewWithTag<LinearLayout>(BAR_TAG)
        if (bar != null) {
            Destination.values().forEachIndexed { index, destination ->
                bar.getChildAt(index)?.visibility = if (isVisible(a, destination)) View.VISIBLE else View.GONE
            }
        }
        val item = a.window.decorView.findViewWithTag<TextView>(RESULT_TAG) ?: return
        val available = result(a) != null || SavedSearchResults.hasId(a)
        item.isEnabled = available
        item.alpha = if (available) 1f else 0.4f
    }

    fun setArchiveImeVisible(a: Activity, visible: Boolean) {
        val visibility = if (visible) View.GONE else View.VISIBLE
        a.window.decorView.findViewWithTag<View>(BAR_TAG)?.visibility = visibility
        a.window.decorView.findViewWithTag<View>(DIVIDER_TAG)?.visibility = visibility
    }

    fun setSelected(a: Activity, selected: Destination) {
        if (selected != Destination.ARCHIVE) setArchiveImeVisible(a, false)
        val bar = a.window.decorView.findViewWithTag<LinearLayout>(BAR_TAG) ?: return
        Destination.values().forEachIndexed { index, destination ->
            val item = bar.getChildAt(index) as? TextView ?: return@forEachIndexed
            val active = destination == selected
            item.isSelected = active
            val color = a.getColor(if (active) R.color.accent else R.color.text_secondary)
            item.setTextColor(color)
            val symbol = NavIcon(destination, color)
            symbol.setBounds(0, 0, dp(a, 24), dp(a, 24))
            item.setCompoundDrawables(null, symbol, null, null)
        }
        refresh(a)
    }

    fun dp(a: Activity, value: Int): Int =
        (a.resources.displayMetrics.density * value).roundToInt()

    fun attach(a: Activity, root: LinearLayout, selected: Destination) {
        val divider = View(a).apply {
            tag = DIVIDER_TAG
            setBackgroundColor(a.getColor(R.color.divider))
        }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(a, 1)))
        val bar = LinearLayout(a).apply {
            tag = BAR_TAG
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
                visibility = if (isVisible(a, destination)) View.VISIBLE else View.GONE
                if (destination == Destination.RESULT) tag = RESULT_TAG
                contentDescription = destination.label
                setPadding(0, dp(a, 6), 0, dp(a, 5))
                val symbol = NavIcon(destination, color)
                symbol.setBounds(0, 0, dp(a, 24), dp(a, 24))
                setCompoundDrawables(null, symbol, null, null)
                compoundDrawablePadding = dp(a, 3)
                TouchFeedback.foreground(this, 10)
                setOnClickListener { if (!isSelected) navigate(a, destination) }
            }
            bar.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(a, 60)))
        refresh(a)
    }

    @Suppress("DEPRECATION")
    fun navigate(a: Activity, destination: Destination) {
        if (a.isFinishing || a.isDestroyed) return
        if (a is HomeActivity && destination in hosted) {
            a.showDestination(destination)
            return
        }
        val intent = when (destination) {
            Destination.HOME, Destination.ARCHIVE, Destination.HISTORY, Destination.FAVORITES, Destination.SETTINGS ->
                Intent(a, HomeActivity::class.java).putExtra(EXTRA_DESTINATION, destination.name)
            Destination.RESULT -> {
                val retained = result(a)
                val origin = when (a) {
                    is HomeActivity -> a.activeDestination
                    is FavoritesActivity -> Destination.FAVORITES
                    is HistoryActivity -> Destination.HISTORY
                    is ArchiveActivity -> Destination.ARCHIVE
                    is SettingsActivity -> Destination.SETTINGS
                    else -> Destination.HOME
                }
                if (retained != null) {
                    retained.setBackDestination(origin)
                    Intent(a, ResultActivity::class.java)
                } else {
                    if (!SavedSearchResults.hasId(a)) return
                    val token = java.util.UUID.randomUUID().toString()
                    restoreToken = token
                    Intent(a, ResultActivity::class.java).putExtra(EXTRA_RESTORE_RESULT, token)
                        .putExtra(ResultActivity.EXTRA_RETURN_DESTINATION, origin.name)
                }
            }
        }
        // Bottom destinations are peers, not a push/pop detail stack.
        // Suppress the window slide/scale for this explicit navigation only.
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        a.startActivity(intent, ActivityOptions.makeCustomAnimation(a, 0, 0).toBundle())
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
                Destination.RESULT -> {
                    path(5f, 3f, 19f, 3f, 19f, 21f, 5f, 21f, 5f, 3f)
                    line(8f, 8f, 16f, 8f)
                    line(8f, 12f, 16f, 12f)
                    line(8f, 16f, 13f, 16f)
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
