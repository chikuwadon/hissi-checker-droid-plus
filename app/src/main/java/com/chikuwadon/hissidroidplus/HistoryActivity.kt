package com.chikuwadon.hissidroidplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import kotlin.math.roundToInt

/** Recent visits, independent favorites and explicit destructive actions. */
open class HistoryActivity : CheckerActivity() {
    protected open val favoritesScreen = false
    private lateinit var content: HistoryContent

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AppSettings.configureBars(this)
        content = HistoryContent(this, ViewModelProvider(this)[HistoryScreenState::class.java], favoritesScreen)
        val root = content.build(state)
        MainNavigation.attach(this, root, if (favoritesScreen) MainNavigation.Destination.FAVORITES else MainNavigation.Destination.HISTORY)
        setContentView(root)
        registerBackAction { if (!content.handleBack()) finish() }
        applyContentInsets(root)
    }

    override fun onResume() {
        super.onResume()
        MainNavigation.refresh(this)
        content.resume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        content.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::content.isInitialized) content.dispose()
        super.onDestroy()
    }
}

/** Shared native history UI; each destination owns a separate ViewModel and view lifetime. */
internal class HistoryContent(
    private val host: Activity,
    private val screenState: HistoryScreenState,
    private val favoritesScreen: Boolean
) {
    private var disposed = false
    private val isFinishing get() = host.isFinishing
    private val isDestroyed get() = disposed || host.isDestroyed
    private fun getColor(id: Int) = host.getColor(id)
    private fun getString(id: Int, vararg args: Any) = host.getString(id, *args)
    private fun runOnUiThread(action: () -> Unit) = host.runOnUiThread { action() }
    private var restoredPosition: Pair<Int, Int>? = null
    private var filter: HistoryFilter
        get() = screenState.filter
        set(value) { screenState.filter = value }
    private var refreshGeneration = 0
    private lateinit var searchButton: ImageButton
    private var totalCount = 0
    private var tab = 0
    private var sort: Int
        get() = screenState.sort
        set(value) { screenState.sort = value }
    private var selection: Boolean
        get() = screenState.selection
        set(value) { screenState.selection = value }
    private var pendingConfirmationUrl: String? = null
    private val chosen get() = screenState.chosen
    private val sorts = arrayOf("新しい順", "古い順", "板名順", "検索日順")
    private lateinit var recentTab: TextView
    private lateinit var favoriteTab: TextView
    private lateinit var sortLabel: TextView
    private lateinit var toolbarTitle: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var selectedActions: TextView
    private lateinit var deleteSelected: TextView
    private lateinit var list: ListView
    private lateinit var selectionActions: LinearLayout
    private val rows = ArrayList<SearchHistory.Entry>()
    private val favoriteUrls = HashSet<String>()
    private val pendingFavorites = HashMap<String, Boolean>()
    private val adapter = EntriesAdapter()

    private fun dp(n: Int) = (n * host.resources.displayMetrics.density).roundToInt()

    private fun text(value: String, size: Int, color: Int): TextView =
        TextView(host).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    fun build(state: Bundle?): LinearLayout {
        restoredPosition = state?.let { it.getInt("history_position") to it.getInt("history_offset") }
        tab = if (favoritesScreen) 1 else 0
        if (!screenState.initialized) {
            filter = HistoryFilter(state?.getString("filter_board").orEmpty(), state?.getString("filter_id").orEmpty(),
                state?.getString("filter_from").orEmpty(), state?.getString("filter_to").orEmpty(), state?.getBoolean("filter_post_date") ?: false)
            sort = state?.getInt("navigation_sort") ?: 0
            screenState.initialized = true
        }
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        val toolbar = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
        val back = text("‹", 32, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            contentDescription = "戻る"
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))
        back.visibility = View.GONE
        back.setOnClickListener { if (!handleBack()) host.finish() }
        TouchFeedback.foreground(back, 10)
        toolbarTitle = AppBar.title(host, if (favoritesScreen) "お気に入り" else "履歴", false)
        toolbar.addView(toolbarTitle, LinearLayout.LayoutParams(0, -2, 1f))
        sortLabel = text("新しい順 ▾", 13, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
        }
        toolbar.addView(sortLabel, LinearLayout.LayoutParams(-2, dp(48)))
        TouchFeedback.foreground(sortLabel)
        sortLabel.setOnClickListener {
            ThemedMenu.showOverflow(host, sortLabel, sorts) { index ->
                sort = index
                refresh()
            }
        }
        searchButton = AppBar.action(host, AppBar.Icon.SEARCH, "履歴内検索")
        toolbar.addView(searchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        searchButton.setOnClickListener { showHistoryFilter() }
        menuButton = AppBar.action(host, AppBar.Icon.SELECT, "複数選択")
        toolbar.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        menuButton.setOnClickListener {
            selection = !selection
            chosen.clear()
            updateSelectionUi()
        }
        val tabs = LinearLayout(host).apply { setPadding(dp(4), 0, dp(4), 0) }
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
        val actions = LinearLayout(host).apply {
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
        val clearAll = text(if (favoritesScreen) "全解除" else "全削除", 13, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(clearAll, LinearLayout.LayoutParams(dp(62), dp(46)))
        TouchFeedback.foreground(clearAll)
        clearAll.setOnClickListener { confirmClear() }
        selectedActions = text("", 13, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(selectedActions, LinearLayout.LayoutParams(0, dp(46), 1f))
        deleteSelected = text(if (favoritesScreen) "解除" else "削除", 14, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
        }
        actions.addView(deleteSelected, LinearLayout.LayoutParams(dp(74), dp(46)))
        TouchFeedback.foreground(deleteSelected)
        deleteSelected.setOnClickListener { confirmSelected() }
        actions.tag = "selectionActions"
        selectionActions = actions

        list = ListView(host).apply {
            divider = ColorDrawable(getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = TouchFeedback.listSelector(host)
            // Platform fast-scroll can intercept touches well inside the row.
            // A separate, exact-width right-hand rail avoids the star hit target.
            isFastScrollEnabled = false
            isVerticalScrollBarEnabled = false
            verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
            itemsCanFocus = false
            adapter = this@HistoryContent.adapter
        }
        val listFrame = FrameLayout(host)
        listFrame.addView(list, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(HistoryScrollRail(host, list),
            FrameLayout.LayoutParams(dp(12), -1, Gravity.RIGHT))
        root.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
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
                Toast.makeText(host, "履歴のURLが無効です", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            host.startActivity(Intent(host, ResultActivity::class.java).setData(Uri.parse(entry.url))
                .putExtra(ResultActivity.EXTRA_RETURN_DESTINATION,
                    if (favoritesScreen) MainNavigation.Destination.FAVORITES.name else MainNavigation.Destination.HISTORY.name))
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
                    host, "履歴の削除", "この履歴を削除しますか？\nお気に入り登録は維持されます。",
                    "削除", Runnable { changeHistory { SearchHistory.deleteRecent(host, entry.url) } },
                    clearPending
                )
            } else {
                ThemedMenu.confirm(
                    host, "お気に入りの解除", "お気に入り登録を解除しますか？",
                    "解除", Runnable {
                        changeHistory { SearchHistory.removeFavorite(host, entry.url) }
                    }, clearPending
                )
            }
            true
        }
        // The Activity or Fragment resumes the view after attachment.
        updateSelectionUi()
        return root
    }

    private fun toggleSelection(url: String) {
        if (!chosen.add(url)) chosen.remove(url)
        updateSelectionUi()
    }

    private fun confirmSelected() {
        if (chosen.isEmpty()) return
        val selected = HashSet(chosen)
        val favorite = favoritesScreen
        ThemedMenu.confirm(host, if (favorite) "選択したお気に入りの解除" else "選択した履歴の削除",
            if (favorite) "選択した${selected.size}件のお気に入りを解除しますか？\n通常履歴は維持されます。"
            else "選択した${selected.size}件を削除しますか？\nお気に入りは維持されます。",
            if (favorite) "解除" else "削除", Runnable {
                chosen.clear()
                selection = false
                updateSelectionUi()
                changeHistory {
                    if (favorite) SearchHistory.removeFavorites(host, selected)
                    else SearchHistory.deleteRecents(host, selected)
                }
            })
    }

    private fun confirmClear() {
        val favorite = favoritesScreen
        ThemedMenu.confirm(host, if (favorite) "お気に入りの全解除" else "履歴の全削除",
            if (favorite) "お気に入りをすべて解除しますか？\n通常履歴は残ります。"
            else "通常履歴をすべて削除しますか？\nお気に入りは残ります。",
            if (favorite) "全解除" else "全削除", Runnable {
                chosen.clear()
                selection = false
                updateSelectionUi()
                changeHistory {
                    if (favorite) SearchHistory.clearFavorites(host)
                    else SearchHistory.clearRecents(host)
                }
            })
    }

    fun save(outState: Bundle) {
        outState.putInt("navigation_tab", tab)
        outState.putInt("navigation_sort", sort)
        outState.putString("filter_board", filter.board)
        outState.putString("filter_id", filter.id)
        outState.putString("filter_from", filter.from)
        outState.putString("filter_to", filter.to)
        outState.putBoolean("filter_post_date", filter.byPostDate)
        if (::list.isInitialized) {
            val position = restoredPosition
            outState.putInt("history_position", position?.first ?: list.firstVisiblePosition)
            outState.putInt("history_offset", position?.second ?: (list.getChildAt(0)?.top ?: 0))
        }
    }

    fun resume() {
        if (!disposed && ::list.isInitialized) refresh()
    }

    fun dispose() {
        disposed = true
        refreshGeneration++
    }

    fun handleBack(): Boolean {
        if (!selection) return false
        selection = false
        chosen.clear()
        updateSelectionUi()
        return true
    }

    private fun refresh() {
        if (disposed || !::list.isInitialized) return
        val generation = ++refreshGeneration
        val requestedTab = tab
        val requestedSort = sort
        val requestedFilter = filter
        SearchHistory.runAsync {
            try {
                val recents = SearchHistory.recents(host)
                val favorites = SearchHistory.favorites(host)
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
                    restoredPosition?.let { (position, offset) ->
                        if (rows.isNotEmpty()) list.setSelectionFromTop(position.coerceIn(0, rows.lastIndex), offset)
                        restoredPosition = null
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && generation == refreshGeneration)
                        Toast.makeText(host, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun changeFavorite(entry: SearchHistory.Entry) {
        if (favoritesScreen || selection || pendingFavorites.containsKey(entry.url)) return
        val desired = !favoriteUrls.contains(entry.url)
        pendingFavorites[entry.url] = desired
        adapter.notifyDataSetChanged()
        SearchHistory.runAsync {
            val success = try {
                SearchHistory.setFavorite(host, entry, desired)
                true
            } catch (_: Exception) { false }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pendingFavorites.remove(entry.url)
                if (success) {
                    if (desired) favoriteUrls.add(entry.url) else favoriteUrls.remove(entry.url)
                } else {
                    Toast.makeText(host, "お気に入りの更新に失敗しました", Toast.LENGTH_SHORT).show()
                }
                adapter.notifyDataSetChanged()
                refresh()
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
                        Toast.makeText(host, "履歴の更新に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showHistoryFilter() {
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, "履歴内検索"))
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply { addView(content) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        fun field(label: String, value: String): EditText {
            content.addView(ThemedMenu.formLabel(host, label))
            return ThemedMenu.formInput(host, value).also {
                content.addView(it, LinearLayout.LayoutParams(-1, dp(48)))
            }
        }
        val boardInput = field("板名（部分一致）", filter.board)
        val idInput = field("ID（部分一致・大文字小文字を区別）", filter.id)
        var byPostDate = filter.byPostDate
        val dateMode = ThemedMenu.formButton(host, if (byPostDate) "投稿対象日" else "検索履歴の記録日")
        content.addView(ThemedMenu.formLabel(host, "期間の基準"))
        content.addView(dateMode, LinearLayout.LayoutParams(-1, dp(44)))
        dateMode.setOnClickListener {
            ThemedMenu.choose(host, "期間の基準", arrayOf("検索履歴の記録日", "投稿対象日"), if (byPostDate) 1 else 0) {
                byPostDate = it == 1
                dateMode.text = if (byPostDate) "投稿対象日" else "検索履歴の記録日"
            }
        }
        var from = filter.from
        var to = filter.to
        val format = DateTimeFormatter.BASIC_ISO_DATE
        val display = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        fun dateButton(heading: String, value: String, set: (String) -> Unit) {
            val line = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
            var selected = value
            fun label(): String = if (selected.isEmpty()) "$heading：指定なし"
                else "$heading：${LocalDate.parse(selected, format).format(display)}"
            val button = ThemedMenu.formButton(host, label())
            line.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f))
            val clear = ThemedMenu.formButton(host, "解除")
            line.addView(clear, LinearLayout.LayoutParams(dp(60), dp(44)).apply { marginStart = dp(8) })
            button.setOnClickListener {
                val day = try { LocalDate.parse(selected, format) }
                    catch (_: Exception) { LocalDate.now(ZoneId.of("Asia/Tokyo")) }
                CalendarDialog.show(host, day) {
                    selected = it.format(format)
                    set(selected)
                    button.text = label()
                }
            }
            clear.setOnClickListener {
                selected = ""
                set("")
                button.text = label()
            }
            content.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        dateButton("開始日", from) { from = it }
        dateButton("終了日", to) { to = it }
        val actions = LinearLayout(host).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val reset = ThemedMenu.formAction(host, "条件を解除")
        actions.addView(reset, LinearLayout.LayoutParams(-2, dp(48)))
        actions.addView(View(host), LinearLayout.LayoutParams(0, 1, 1f))
        val cancel = ThemedMenu.formAction(host, "キャンセル")
        actions.addView(cancel, LinearLayout.LayoutParams(-2, dp(48)))
        val apply = ThemedMenu.formAction(host, "検索")
        actions.addView(apply, LinearLayout.LayoutParams(-2, dp(48)))
        val dialog = ThemedMenu.showForm(host, panel)
        reset.setOnClickListener {
            filter = HistoryFilter()
            chosen.clear()
            refresh()
            dialog.dismiss()
        }
        cancel.setOnClickListener { dialog.dismiss() }
        apply.setOnClickListener {
            if (from.isNotEmpty() && to.isNotEmpty() && from > to) {
                Toast.makeText(host, "開始日は終了日以前にしてください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            filter = HistoryFilter(boardInput.text.toString().trim(), idInput.text.toString().trim(), from, to, byPostDate)
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
        menuButton.visibility = View.VISIBLE
        AppBar.setIcon(host, menuButton, if (selection) AppBar.Icon.DONE else AppBar.Icon.SELECT, selection)
        menuButton.contentDescription = if (selection) "完了" else "複数選択"
        searchButton.visibility = if (selection) View.GONE else View.VISIBLE
        AppBar.setIcon(host, searchButton, AppBar.Icon.SEARCH, filter.active())
        searchButton.contentDescription = if (filter.active()) "履歴内検索（絞り込み中）" else "履歴内検索"
        val count = if (filter.active()) "${rows.size}/$totalCount" else totalCount.toString()
        toolbarTitle.text = if (selection) (if (favoritesScreen) "お気に入りを選択" else "履歴を選択") else (if (tab == 0) "履歴" else "お気に入り") + " ($count)"
        selectionActions.visibility = if (selection) View.VISIBLE else View.GONE
        selectedActions.text = getString(R.string.history_selected_count, chosen.size)
        deleteSelected.isEnabled = chosen.isNotEmpty()
        deleteSelected.alpha = if (chosen.isEmpty()) 0.45f else 1f
        adapter.notifyDataSetChanged()
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
                val row = LinearLayout(host).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(6), dp(12), dp(6))
                    minimumHeight = dp(64)
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                val number = text("", 12, getColor(R.color.text_secondary)).apply {
                    gravity = Gravity.CENTER
                }
                row.addView(number, LinearLayout.LayoutParams(dp(34), dp(48)))
                val labels = LinearLayout(host).apply {
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
            val showStar = !favoritesScreen && !selection
            holder.star.visibility = if (showStar) View.VISIBLE else View.GONE
            if (showStar) {
                val starred = pendingFavorites[entry.url] ?: favoriteUrls.contains(entry.url)
                holder.star.text = if (starred) "★" else "☆"
                holder.star.setTextColor(getColor(
                    if (starred) R.color.accent else R.color.text_secondary
                ))
                holder.star.contentDescription = if (starred) "お気に入りを解除" else "お気に入りに登録"
                holder.star.setOnClickListener { changeFavorite(entry) }
                holder.star.isClickable = true
                holder.star.isFocusable = false
                holder.star.isFocusableInTouchMode = false
                holder.star.isEnabled = !pendingFavorites.containsKey(entry.url)
            } else {
                holder.star.setOnClickListener(null)
                holder.star.isClickable = false
                holder.star.contentDescription = null
            }
            return holder.root
        }
    }
}
