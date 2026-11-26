package com.chikuwadon.hissidroidplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Recent visits, independent favorites and explicit destructive actions. */
open class HistoryActivity : Activity() {
    protected open val favoritesScreen = false
    private var filter = HistoryFilter()
    private var refreshGeneration = 0
    private lateinit var searchButton: TextView
    private var totalCount = 0
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
        tab = if (favoritesScreen) 1 else 0
        filter = HistoryFilter(state?.getString("filter_board").orEmpty(), state?.getString("filter_id").orEmpty(),
            state?.getString("filter_from").orEmpty(), state?.getString("filter_to").orEmpty(), state?.getBoolean("filter_post_date") ?: false)
        sort = state?.getInt("navigation_sort") ?: 0
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
        back.visibility = View.GONE
        back.setOnClickListener { navigateBack() }
        TouchFeedback.foreground(back, 10)
        toolbarTitle = text("検索履歴", 16, getColor(R.color.text_primary)).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        toolbar.addView(toolbarTitle, LinearLayout.LayoutParams(0, -2, 1f))
        sortLabel = text("新しい順 ▾", 13, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }
        toolbar.addView(sortLabel, LinearLayout.LayoutParams(-2, dp(48)))
        TouchFeedback.foreground(sortLabel)
        sortLabel.setOnClickListener {
            ThemedMenu.showOverflow(this, sortLabel, sorts) { index ->
                sort = index
                refresh()
            }
        }
        searchButton = text("検索", 13, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            contentDescription = "履歴内検索"
        }
        toolbar.addView(searchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        TouchFeedback.foreground(searchButton, 10)
        searchButton.setOnClickListener { showHistoryFilter() }
        menuButton = text("複数選択", 13, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            contentDescription = "複数選択"
        }
        toolbar.addView(menuButton, LinearLayout.LayoutParams(dp(76), dp(48)))
        TouchFeedback.foreground(menuButton, 10)
        menuButton.setOnClickListener {
            selection = !selection
            chosen.clear()
            updateSelectionUi()
        }
        val tabs = LinearLayout(this).apply { setPadding(dp(4), 0, dp(4), 0) }
        root.addView(tabs, LinearLayout.LayoutParams(-1, dp(44)))
        tabs.visibility = View.GONE
        recentTab = text("履歴", 14, getColor(R.color.accent))
        favoriteTab = text("お気に入り", 14, getColor(R.color.text_secondary))
        recentTab.gravity = Gravity.CENTER
        favoriteTab.gravity = Gravity.CENTER
        tabs.addView(recentTab, LinearLayout.LayoutParams(0, -1, 1f))
        tabs.addView(favoriteTab, LinearLayout.LayoutParams(0, -1, 1f))
        TouchFeedback.foreground(recentTab)
        TouchFeedback.foreground(favoriteTab)
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
        val cancelSelection = text("全選択", 13, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(cancelSelection, LinearLayout.LayoutParams(dp(62), dp(46)))
        TouchFeedback.foreground(cancelSelection)
        cancelSelection.setOnClickListener {
            if (rows.isNotEmpty() && rows.all { it.url in chosen }) chosen.clear()
            else chosen.addAll(rows.map { it.url })
            updateSelectionUi()
        }
        val clearAll = text("全削除", 13, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(clearAll, LinearLayout.LayoutParams(dp(62), dp(46)))
        TouchFeedback.foreground(clearAll)
        clearAll.setOnClickListener { confirmClear() }
        selectedActions = text("", 13, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(selectedActions, LinearLayout.LayoutParams(0, dp(46), 1f))
        deleteSelected = text("削除", 14, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(deleteSelected, LinearLayout.LayoutParams(dp(74), dp(46)))
        TouchFeedback.foreground(deleteSelected)
        deleteSelected.setOnClickListener { confirmSelected() }
        actions.tag = "selectionActions"
        selectionActions = actions

        list = ListView(this).apply {
            divider = ColorDrawable(getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = TouchFeedback.listSelector(this@HistoryActivity)
            isFastScrollEnabled = true
            adapter = this@HistoryActivity.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        MainNavigation.attach(this, root, if (tab == 0) MainNavigation.Destination.HISTORY else MainNavigation.Destination.FAVORITES)
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
                    "削除", Runnable { changeHistory { SearchHistory.deleteRecent(this, entry.url) } },
                    clearPending
                )
            } else {
                ThemedMenu.confirm(
                    this, "お気に入りの解除", "お気に入り登録を解除しますか？",
                    "解除", Runnable {
                        changeHistory { SearchHistory.removeFavorite(this, entry.url) }
                    }, clearPending
                )
            }
            true
        }
        // onResume performs the initial data refresh as well as subsequent refreshes.
    }

    private fun toggleSelection(url: String) {
        if (!chosen.add(url)) chosen.remove(url)
        updateSelectionUi()
    }

    private fun confirmSelected() {
        if (chosen.isEmpty()) return
        ThemedMenu.confirm(this, "選択した履歴の削除",
            "選択した" + chosen.size + "件を削除しますか？\nお気に入りは維持されます。",
            "削除", Runnable {
                val selected = HashSet(chosen)
                chosen.clear()
                selection = false
                updateSelectionUi()
                changeHistory { SearchHistory.deleteRecents(this, selected) }
            })
    }

    private fun confirmClear() {
        ThemedMenu.confirm(this, "履歴の全削除",
            "通常履歴をすべて削除しますか？\nお気に入りは残ります。",
            "全削除", Runnable {
                chosen.clear()
                selection = false
                updateSelectionUi()
                changeHistory { SearchHistory.clearRecents(this) }
            })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("navigation_tab", tab)
        outState.putInt("navigation_sort", sort)
        outState.putString("filter_board", filter.board)
        outState.putString("filter_id", filter.id)
        outState.putString("filter_from", filter.from)
        outState.putString("filter_to", filter.to)
        outState.putBoolean("filter_post_date", filter.byPostDate)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        MainNavigation.refresh(this)
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
        val generation = ++refreshGeneration
        val requestedTab = tab
        val requestedSort = sort
        val requestedFilter = filter
        SearchHistory.runAsync {
            try {
                val recents = SearchHistory.recents(this)
                val favorites = SearchHistory.favorites(this)
                val source = if (requestedTab == 0) recents else favorites
                val filtered = SearchHistory.sorted(source.filter { requestedFilter.matches(it) }, requestedSort)
                val stars = favorites.mapTo(HashSet()) { it.url }
                runOnUiThread {
                    if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                    rows.clear()
                    rows.addAll(filtered)
                    totalCount = source.size
                    chosen.retainAll(rows.mapTo(HashSet()) { it.url })
                    favoriteUrls.clear()
                    favoriteUrls.addAll(stars)
                    recentTab.text = getString(R.string.history_recent_count, recents.size)
                    favoriteTab.text = getString(R.string.history_favorite_count, favorites.size)
                    updateSelectionUi()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && generation == refreshGeneration)
                        Toast.makeText(this, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun changeHistory(action: () -> Unit) {
        SearchHistory.runAsync {
            try {
                action()
                runOnUiThread { if (!isFinishing && !isDestroyed) refresh() }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed)
                        Toast.makeText(this, "履歴の更新に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showHistoryFilter() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val boardInput = EditText(this).apply {
            hint = "板名（部分一致）"
            setSingleLine(true)
            setText(filter.board)
        }
        val idInput = EditText(this).apply {
            hint = "ID（部分一致・大文字小文字を区別）"
            setSingleLine(true)
            setText(filter.id)
        }
        panel.addView(boardInput)
        panel.addView(idInput)
        val postDate = CheckBox(this).apply {
            text = "投稿対象日で検索（OFF：検索した日）"
            isChecked = filter.byPostDate
            buttonTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent))
        }
        panel.addView(postDate)
        var from = filter.from
        var to = filter.to
        val format = DateTimeFormatter.BASIC_ISO_DATE
        fun dateButton(heading: String, value: String, set: (String) -> Unit) {
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val button = Button(this).apply { text = "$heading：${value.ifEmpty { "指定なし" }}" }
            line.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            val clear = Button(this).apply { text = "解除" }
            line.addView(clear)
            var selected = value
            button.setOnClickListener {
                val day = try { LocalDate.parse(selected, format) }
                    catch (_: Exception) { LocalDate.now(ZoneId.of("Asia/Tokyo")) }
                DatePickerDialog(this, { _, year, month, dayOfMonth ->
                    selected = LocalDate.of(year, month + 1, dayOfMonth).format(format)
                    button.text = "$heading：$selected"
                    set(selected)
                }, day.year, day.monthValue - 1, day.dayOfMonth).show()
            }
            clear.setOnClickListener {
                selected = ""
                set("")
                button.text = "$heading：指定なし"
            }
            panel.addView(line)
        }
        dateButton("開始日", from) { from = it }
        dateButton("終了日", to) { to = it }
        val scroll = ScrollView(this).apply { addView(panel) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("履歴内検索")
            .setView(scroll)
            .setNegativeButton("キャンセル", null)
            .setNeutralButton("条件を解除") { _, _ ->
                filter = HistoryFilter()
                chosen.clear()
                refresh()
            }
            .setPositiveButton("検索", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (from.isNotEmpty() && to.isNotEmpty() && from > to) {
                Toast.makeText(this, "開始日は終了日以前にしてください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            filter = HistoryFilter(boardInput.text.toString().trim(), idInput.text.toString().trim(), from, to, postDate.isChecked)
            chosen.clear()
            refresh()
            dialog.dismiss()
        }
    }

    private fun updateSelectionUi() {
        recentTab.setTextColor(getColor(if (tab == 0) R.color.accent else R.color.text_secondary))
        favoriteTab.setTextColor(getColor(if (tab == 1) R.color.accent else R.color.text_secondary))
        sortLabel.text = getString(R.string.history_sort_label, sorts[sort])
        sortLabel.visibility = if (selection) View.GONE else View.VISIBLE
        menuButton.visibility = if (tab == 1) View.GONE else View.VISIBLE
        menuButton.text = if (selection) "完了" else "複数選択"
        menuButton.contentDescription = menuButton.text
        searchButton.visibility = if (selection) View.GONE else View.VISIBLE
        searchButton.text = if (filter.active()) "検索中" else "検索"
        val count = if (filter.active()) "${rows.size}/$totalCount" else totalCount.toString()
        toolbarTitle.text = if (selection) "履歴を選択" else (if (tab == 0) "履歴" else "お気に入り") + " ($count)"
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
                TouchFeedback.foreground(row, 6)
                TouchFeedback.foreground(star, 10)
                holder = HistoryRow(row, number, title, detail, star)
                row.tag = holder
            }
            val checked = selection && entry.url in chosen
            if (checked || entry.url == pendingConfirmationUrl) {
                holder.root.setBackgroundColor(getColor(R.color.panel_pressed))
            } else {
                // The existing foreground already handles touch feedback.
                holder.root.background = null
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
                    changeHistory { SearchHistory.toggleFavorite(this@HistoryActivity, entry) }
                }
            } else holder.star.setOnClickListener(null)
            return holder.root
        }
    }
}
