package com.chikuwadon.hissidroidplus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Recent visits, independent favorites and explicit destructive actions. */
class HistoryActivity : Activity() {
    private var tab = 0
    private var sort = 0
    private var selection = false
    private var pendingConfirmationUrl: String? = null
    private val chosen = HashSet<String>()
    private val sorts = arrayOf("新しい順", "古い順", "板名順", "検索日順")
    private lateinit var recentTab: TextView
    private lateinit var favoriteTab: TextView
    private lateinit var sortLabel: TextView
    private lateinit var toolbarTitle: TextView
    private lateinit var menuButton: TextView
    private lateinit var selectedActions: TextView
    private lateinit var deleteSelected: TextView
    private lateinit var list: ListView
    private lateinit var selectionActions: LinearLayout
    private val rows = ArrayList<SearchHistory.Entry>()
    private val favoriteUrls = HashSet<String>()
    private val adapter = EntriesAdapter()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()

    private fun text(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AppSettings.configureBars(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { navigateBack() }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
                )
                view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
                WindowInsets.CONSUMED
            }
            root.requestApplyInsets()
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(52)))
        val back = text("‹", 32, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            contentDescription = "戻る"
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))
        back.setOnClickListener { navigateBack() }
        toolbarTitle = text("検索履歴", 18, getColor(R.color.text_primary))
        toolbar.addView(toolbarTitle, LinearLayout.LayoutParams(0, -2, 1f))
        sortLabel = text("新しい順 ▾", 13, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }
        toolbar.addView(sortLabel, LinearLayout.LayoutParams(-2, dp(48)))
        sortLabel.setOnClickListener {
            ThemedMenu.showOverflow(this, sortLabel, sorts) { index ->
                sort = index
                refresh()
            }
        }
        menuButton = text("⋮", 25, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            contentDescription = "履歴の操作"
        }
        toolbar.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(48)))
        menuButton.setOnClickListener {
            ThemedMenu.showOverflow(
                this, menuButton, arrayOf("複数選択", "履歴を全削除")
            ) { index ->
                if (index == 0) {
                    tab = 0
                    selection = true
                    chosen.clear()
                    refresh()
                } else confirmClear()
            }
        }
        val tabs = LinearLayout(this).apply { setPadding(dp(4), 0, dp(4), 0) }
        root.addView(tabs, LinearLayout.LayoutParams(-1, dp(44)))
        recentTab = text("履歴", 14, getColor(R.color.accent))
        favoriteTab = text("お気に入り", 14, getColor(R.color.text_secondary))
        recentTab.gravity = Gravity.CENTER
        favoriteTab.gravity = Gravity.CENTER
        tabs.addView(recentTab, LinearLayout.LayoutParams(0, -1, 1f))
        tabs.addView(favoriteTab, LinearLayout.LayoutParams(0, -1, 1f))
        recentTab.setOnClickListener { tab = 0; refresh() }
        favoriteTab.setOnClickListener {
            if (selection) {
                selection = false
                chosen.clear()
            }
            tab = 1
            refresh()
        }
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(9), 0)
        }
        root.addView(actions, LinearLayout.LayoutParams(-1, dp(48)))
        val cancelSelection = text("解除", 13, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(cancelSelection, LinearLayout.LayoutParams(dp(62), dp(46)))
        cancelSelection.setOnClickListener {
            selection = false
            chosen.clear()
            refresh()
        }
        selectedActions = text("", 13, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(selectedActions, LinearLayout.LayoutParams(0, dp(46), 1f))
        deleteSelected = text("削除", 14, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(deleteSelected, LinearLayout.LayoutParams(dp(74), dp(46)))
        deleteSelected.setOnClickListener { confirmSelected() }
        actions.tag = "selectionActions"
        selectionActions = actions

        list = ListView(this).apply {
            divider = ColorDrawable(getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = ColorDrawable(Color.TRANSPARENT)
            isFastScrollEnabled = true
            adapter = this@HistoryActivity.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, position, _ ->
            if (position !in rows.indices) return@setOnItemClickListener
            val entry = rows[position]
            if (selection) {
                toggleSelection(entry.url)
                return@setOnItemClickListener
            }
            try {
                CheckerQuery.parseUrl(entry.url)
            } catch (_: Exception) {
                Toast.makeText(this, "履歴のURLが無効です", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            startActivity(Intent(this, ResultActivity::class.java).setData(Uri.parse(entry.url)))
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            if (position !in rows.indices) return@setOnItemLongClickListener true
            val entry = rows[position]
            if (selection) {
                toggleSelection(entry.url)
                return@setOnItemLongClickListener true
            }
            pendingConfirmationUrl = entry.url
            adapter.notifyDataSetChanged()
            val clearPending = Runnable {
                pendingConfirmationUrl = null
                if (!isFinishing && !isDestroyed) adapter.notifyDataSetChanged()
            }
            if (tab == 0) {
                ThemedMenu.confirm(
                    this, "履歴の削除", "この履歴を削除しますか？\nお気に入り登録は維持されます。",
                    "削除", Runnable { SearchHistory.deleteRecent(this, entry.url); refresh() },
                    clearPending
                )
            } else {
                ThemedMenu.confirm(
                    this, "お気に入りの解除", "お気に入り登録を解除しますか？",
                    "解除", Runnable {
                        if (SearchHistory.isFavorite(this, entry.url)) {
                            SearchHistory.toggleFavorite(this, entry)
                        }
                        refresh()
                    }, clearPending
                )
            }
            true
        }
        refresh()
    }

    private fun toggleSelection(url: String) {
        if (!chosen.add(url)) chosen.remove(url)
        refresh()
    }

    private fun confirmSelected() {
        if (chosen.isEmpty()) return
        ThemedMenu.confirm(this, "選択した履歴の削除",
            "選択した" + chosen.size + "件を削除しますか？\nお気に入りは維持されます。",
            "削除", Runnable {
                SearchHistory.deleteRecents(this, HashSet(chosen))
                chosen.clear()
                selection = false
                refresh()
            })
    }

    private fun confirmClear() {
        ThemedMenu.confirm(this, "履歴の全削除",
            "通常履歴をすべて削除しますか？\nお気に入りは残ります。",
            "全削除", Runnable {
                SearchHistory.clearRecents(this)
                chosen.clear()
                selection = false
                refresh()
            })
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }

    private fun navigateBack() {
        if (selection) {
            selection = false
            chosen.clear()
            refresh()
        } else finish()
    }

    private fun refresh() {
        if (!::list.isInitialized) return
        val recents = SearchHistory.recents(this)
        val favorites = SearchHistory.favorites(this)
        rows.clear()
        rows.addAll(SearchHistory.sorted(if (tab == 0) recents else favorites, sort))
        favoriteUrls.clear()
        for (favorite in favorites) favoriteUrls.add(favorite.url)
        recentTab.text = getString(R.string.history_recent_count, recents.size)
        favoriteTab.text = getString(R.string.history_favorite_count, favorites.size)
        recentTab.setTextColor(getColor(if (tab == 0) R.color.accent else R.color.text_secondary))
        favoriteTab.setTextColor(getColor(if (tab == 1) R.color.accent else R.color.text_secondary))
        sortLabel.text = getString(R.string.history_sort_label, sorts[sort])
        sortLabel.visibility = if (selection) View.GONE else View.VISIBLE
        menuButton.visibility = if (selection) View.GONE else View.VISIBLE
        toolbarTitle.text = if (selection) "履歴を選択" else "検索履歴"
        selectionActions.visibility = if (selection) View.VISIBLE else View.GONE
        selectedActions.text = getString(R.string.history_selected_count, chosen.size)
        deleteSelected.isEnabled = chosen.isNotEmpty()
        deleteSelected.alpha = if (chosen.isEmpty()) 0.45f else 1f
        adapter.notifyDataSetChanged()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        navigateBack()
    }

    private class HistoryRow(
        val root: LinearLayout,
        val number: TextView,
        val title: TextView,
        val detail: TextView,
        val star: TextView
    )

    private inner class EntriesAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convert: View?, parent: ViewGroup): View {
            val entry = rows[position]
            val holder: HistoryRow
            if (convert is LinearLayout && convert.tag is HistoryRow) {
                holder = convert.tag as HistoryRow
            } else {
                val row = LinearLayout(this@HistoryActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(6), dp(6), dp(6))
                    minimumHeight = dp(64)
                }
                val number = text("", 12, getColor(R.color.text_secondary)).apply {
                    gravity = Gravity.CENTER
                }
                row.addView(number, LinearLayout.LayoutParams(dp(34), dp(48)))
                val labels = LinearLayout(this@HistoryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val title = text("", 14, getColor(R.color.text_primary)).apply {
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                labels.addView(title)
                val detail = text("", 11, getColor(R.color.text_secondary)).apply {
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                labels.addView(detail)
                row.addView(labels, LinearLayout.LayoutParams(0, dp(52), 1f))
                val star = text("", 23, getColor(R.color.text_secondary)).apply {
                    gravity = Gravity.CENTER
                }
                row.addView(star, LinearLayout.LayoutParams(dp(48), dp(48)))
                holder = HistoryRow(row, number, title, detail, star)
                row.tag = holder
            }
            val checked = selection && entry.url in chosen
            if (checked || entry.url == pendingConfirmationUrl) {
                holder.root.setBackgroundColor(getColor(R.color.panel_pressed))
            } else {
                holder.root.background = RippleDrawable(
                    ColorStateList.valueOf(getColor(R.color.panel_pressed)),
                    ColorDrawable(Color.TRANSPARENT), null
                )
            }
            holder.number.text = if (selection) (if (checked) "☑" else "☐") else (position + 1).toString()
            holder.number.textSize = if (selection) 21f else 12f
            holder.number.setTextColor(getColor(
                if (checked) R.color.accent else R.color.text_secondary
            ))
            holder.title.text = entry.title()
            holder.detail.text = entry.detail()
            holder.star.visibility = if (selection) View.GONE else View.VISIBLE
            if (!selection) {
                val starred = favoriteUrls.contains(entry.url)
                holder.star.text = if (starred) "★" else "☆"
                holder.star.setTextColor(getColor(
                    if (starred) R.color.accent else R.color.text_secondary
                ))
                holder.star.contentDescription = if (starred) "お気に入りを解除" else "お気に入りに登録"
                holder.star.setOnClickListener {
                    SearchHistory.toggleFavorite(this@HistoryActivity, entry)
                    refresh()
                }
            } else holder.star.setOnClickListener(null)
            return holder.root
        }
    }
}
