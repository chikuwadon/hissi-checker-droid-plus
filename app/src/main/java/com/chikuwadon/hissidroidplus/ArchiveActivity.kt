package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import android.widget.ArrayAdapter
import android.graphics.Canvas
import android.graphics.Paint
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.LocalDate
import java.util.concurrent.Executors

class ArchiveScreenState : ViewModel() {
    var preferencesRestored = false
    var provider = ArchiveProvider.EDGE_ARCHIVE
    var draft = ArchiveQuery("")
    val drafts = mutableMapOf<ArchiveProvider, ArchiveQuery>()
    var submitted: ArchiveQuery? = null
    var result: ArchivePage? = null
    var message = ""
    var form: ArchiveFormOptions? = null
    val pages = ArchivePageCache()
}

class ArchiveActivity : MainSectionActivity() {
    private var content: ArchiveContent? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AppSettings.configureBars(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        content = ArchiveContent(this, ViewModelProvider(this)[ArchiveScreenState::class.java])
        content?.buildScreen(root)
        MainNavigation.attach(this, root, MainNavigation.Destination.ARCHIVE)
        setContentView(root)
        applyContentInsets(root, resizeForIme = true)
    }

    override fun onResume() {
        super.onResume()
        content?.resume()
    }

    override fun onDestroy() {
        content?.dispose()
        super.onDestroy()
    }
}

/** Native form and bounded, recycled result list shared by both hosts. */
internal class ArchiveContent(host: Activity, private val state: ArchiveScreenState) : SectionContent(host) {
    private val worker = Executors.newSingleThreadExecutor()
    private var disposed = false
    private var generation = 0
    private var loading = false
    private lateinit var provider: TextView
    private lateinit var keyword: EditText
    private lateinit var boardButton: TextView
    private lateinit var boardRow: LinearLayout
    private lateinit var saveBoardButton: TextView
    private lateinit var favoriteBoardsButton: TextView
    private lateinit var searchTop: LinearLayout
    private lateinit var searchBottom: LinearLayout
    private lateinit var searchControls: LinearLayout
    private lateinit var searchLine: LinearLayout
    private lateinit var searchLinks: LinearLayout
    private lateinit var fontControls: LinearLayout
    private lateinit var fontButton: TextView
    private var fontPercent = AppSettings.archiveFontPercent(host)
    private var bottomPlacement: Boolean? = null
    private lateinit var searchHistoryButton: View
    private var boardGroups: List<BoardMenu.Category>? = null
    private var nukoBrowser: NukoBrowserFallback? = null
    private val nukoUserAgent by lazy { runCatching { android.webkit.WebSettings.getDefaultUserAgent(host) }.getOrDefault("") }
    private var resolvedBoardAddresses: Map<String, String> = emptyMap()
    private fun otherBoards() = state.provider == ArchiveProvider.OTHER_KYODEMO
    private fun requiresBoard() = state.provider == ArchiveProvider.FIVECH_KYODEMO || otherBoards()
    private fun supportsBoardSelection() = requiresBoard() || state.provider == ArchiveProvider.FIVECH_NUKO
    private lateinit var search: TextView
    private lateinit var options: TextView
    private lateinit var message: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private lateinit var pageJump: TextView
    private lateinit var browser: TextView
    private lateinit var list: ListView
    private val adapter = ThreadsAdapter()

    private fun archiveButton(label: String): TextView = ThemedMenu.formButton(host, label, outlined = false)

    fun buildScreen(root: LinearLayout) {
        val restore = !state.preferencesRestored
        if (!state.preferencesRestored) {
            state.provider = ArchiveSelectionStore.provider(host)
            for (choice in ArchiveProvider.entries) state.drafts[choice] = ArchiveSelectionStore.draft(host, choice)
            state.draft = state.drafts.getValue(state.provider)
            state.preferencesRestored = true
        }
        val controls = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        provider = archiveButton(state.provider.label + " ▾")
        controls.addView(provider, LinearLayout.LayoutParams(-1, dp(44)))
        provider.setOnClickListener {
            if (loading) return@setOnClickListener
            val providers = ArchiveProvider.entries
            val labels = providers.map { it.label }.toTypedArray()
            ThemedMenu.choose(host, "検索サービス", labels, providers.indexOf(state.provider)) { index ->
                val selected = providers.getOrNull(index) ?: return@choose
                if (selected != state.provider) {
                    saveKeyword()
                    state.drafts[state.provider] = state.draft
                    val previousKeyword = state.draft.keyword
                    state.form = null
                    state.provider = selected
                    boardGroups = null
                    state.draft = state.drafts[selected]?.let { if (it.keyword.isEmpty()) it.copy(keyword = previousKeyword) else it } ?: ArchiveQuery(previousKeyword)
                    ArchiveSelectionStore.save(host, state.provider, state.draft)
                    keyword.setText(state.draft.keyword)
                    state.pages.clear()
                    state.submitted = null
                    state.result = null
                    SavedSearchResults.clearArchive(host)
                    state.message = ""
                    refresh()
                }
            }
        }
        boardRow = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        controls.addView(boardRow, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        boardButton = archiveButton("板を選択 ▾")
        boardRow.addView(boardButton, LinearLayout.LayoutParams(0, -1, 1f))
        boardButton.setOnClickListener { showBoards() }
        saveBoardButton = archiveButton("☆ 保存").apply {
            contentDescription = "選択した板をお気に入りに保存"
            setOnClickListener { saveFavoriteBoard() }
        }
        boardRow.addView(saveBoardButton, LinearLayout.LayoutParams(dp(82), -1).apply { marginStart = dp(4) })
        favoriteBoardsButton = archiveButton("★").apply {
            contentDescription = "板のお気に入り"
            setOnClickListener { showFavoriteBoards() }
        }
        boardRow.addView(favoriteBoardsButton, LinearLayout.LayoutParams(dp(48), -1).apply { marginStart = dp(4) })
        searchTop = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(searchTop, LinearLayout.LayoutParams(-1, -2))
        searchControls = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val line = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        searchLine = line
        searchControls.addView(line, LinearLayout.LayoutParams(-1, dp(52)))
        keyword = ThemedMenu.formInput(host, state.draft.keyword).apply {
            hint = "スレタイ検索"
            setHintTextColor(host.getColor(R.color.text_secondary))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    startSearch()
                    true
                } else false
            }
        }
        line.addView(keyword, LinearLayout.LayoutParams(0, dp(44), 1f))
        searchHistoryButton = object : View(host) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = host.getColor(R.color.text_secondary)
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
                strokeCap = Paint.Cap.ROUND
            }
            override fun onDraw(canvas: Canvas) {
                val x = width / 2f
                val y = height / 2f
                val radius = dp(10).toFloat()
                canvas.drawCircle(x, y, radius, paint)
                canvas.drawLine(x, y - dp(6), x, y, paint)
                canvas.drawLine(x, y, x + dp(4), y + dp(3), paint)
            }
        }.apply {
            contentDescription = "過去ログ検索履歴"
            isFocusable = true
            TouchFeedback.foreground(this, 8)
            setOnClickListener { showKeywordHistory() }
        }
        line.addView(searchHistoryButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        search = archiveButton("検索")
        line.addView(search, LinearLayout.LayoutParams(dp(72), dp(44)).apply { marginStart = dp(6) })
        search.setOnClickListener { startSearch() }
        val links = LinearLayout(host)
        searchLinks = links
        searchControls.addView(links, LinearLayout.LayoutParams(-1, dp(42)))
        options = ThemedMenu.formAction(host, "詳細条件")
        browser = ThemedMenu.formAction(host, "外部ブラウザ")
        links.addView(options, LinearLayout.LayoutParams(0, -1, 1f))
        links.addView(browser, LinearLayout.LayoutParams(0, -1, 1f))
        fontButton = ThemedMenu.formAction(host, "Aa").apply {
            textSize = 18f
            contentDescription = "検索結果の文字サイズ"
            isFocusable = true
        }
        links.addView(fontButton, LinearLayout.LayoutParams(dp(48), -1))
        buildFontControls()
        searchControls.addView(fontControls, LinearLayout.LayoutParams(-1, -2))
        fontButton.setOnClickListener {
            fontControls.visibility = if (fontControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            fontButton.isSelected = fontControls.visibility == View.VISIBLE
        }
        options.setOnClickListener { showOptions() }
        browser.setOnClickListener {
            if (loading || disposed) return@setOnClickListener
            saveKeyword()
            if (requiresBoard() && state.draft.boardCode.isEmpty()) {
                showBoards()
                return@setOnClickListener
            }
            val target = if (state.draft.keyword.isNotBlank()) {
                try { ArchiveExternalSearch.url(state.provider, state.draft) }
                catch (error: Exception) {
                    message.text = error.message ?: "検索条件が不正です"
                    return@setOnClickListener
                }
            } else if (requiresBoard()) "https://www.kyodemo.net/sdemo/b/" + state.draft.boardCode + "/" else state.provider.url
            if (state.provider == ArchiveProvider.EDGE_ARCHIVE && state.draft.maximum.isNotEmpty()) {
                android.widget.Toast.makeText(host, "レス数の上限はアプリ内表示にのみ適用されます", android.widget.Toast.LENGTH_SHORT).show()
            }
            BrowserLauncher.open(host, target)
        }
        message = TextView(host).apply {
            textSize = 13f
            setTextColor(host.getColor(R.color.text_secondary))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        root.addView(message, LinearLayout.LayoutParams(-1, -2))
        list = ListView(host).apply {
            divider = android.graphics.drawable.ColorDrawable(host.getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = TouchFeedback.listSelector(host)
            adapter = this@ArchiveContent.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, position, _ ->
            state.result?.items?.getOrNull(position)?.let { entry ->
                val original = entry.originalUrl
                when {
                    ThreadOpener.validated5chThreadUrl(original).isNotEmpty() -> ThreadOpener.open(host, original, false)
                    ThreadOpener.validatedThreadUrl(original).isNotEmpty() -> ThreadOpener.open(host, original, true)
                    ThreadOpener.validated5chThreadUrl(entry.url).isNotEmpty() -> ThreadOpener.open(host, entry.url, false)
                    ThreadOpener.validatedThreadUrl(entry.url).isNotEmpty() -> ThreadOpener.open(host, entry.url, true)
                    state.provider == ArchiveProvider.OTHER_KYODEMO -> BrowserLauncher.open(host, entry.url)
                    else -> ThemedMenu.confirm(host, "元スレッドURLを確認できませんでした",
                        "検索サービスのページを外部ブラウザで開きますか？", "開く",
                        Runnable { BrowserLauncher.open(host, entry.url) })
                }
            }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val entry = state.result?.items?.getOrNull(position)
            if (entry == null) false else {
                showCopyMenu(entry)
                true
            }
        }
        val pager = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        previous = ThemedMenu.formAction(host, "前のページ")
        pageJump = archiveButton("ページ指定")
        next = ThemedMenu.formAction(host, "次のページ")
        pager.addView(previous, LinearLayout.LayoutParams(0, dp(44), 1f))
        pager.addView(pageJump, LinearLayout.LayoutParams(0, dp(40), 1.15f))
        pager.addView(next, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(pager, LinearLayout.LayoutParams(-1, -2))
        searchBottom = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        root.addView(searchBottom, LinearLayout.LayoutParams(-1, -2))
        applySearchPlacement()
        previous.setOnClickListener { state.submitted?.let { q -> state.result?.let { if (it.page > 1) load(q, it.page - 1) } } }
        next.setOnClickListener { state.submitted?.let { q -> state.result?.let { if (it.hasNext) load(q, it.page + 1) } } }
        pageJump.setOnClickListener { showPageJump() }
        refresh()
        val restored = state.submitted?.let { query -> state.result?.let { state.pages.get(state.provider, query, it.page) } }
        restored?.let { restorePosition(it.position) }
        if (restore && state.result == null && SavedSearchResults.hasArchive(host)) restoreSavedPage()
    }

    /** Inline controls leave the actual search results visible while adjusting text. */
    private fun buildFontControls() {
        fontControls = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        val value = TextView(host).apply {
            text = "$fontPercent%"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(host.getColor(R.color.text_primary))
        }
        val slider = SeekBar(host).apply {
            max = ArchiveFontScale.MAX_STEP
            progress = ArchiveFontScale.toProgress(fontPercent)
            contentDescription = "検索結果の文字サイズ（パーセント）"
            progressTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
            thumbTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
        }
        val reset = ThemedMenu.formAction(host, "標準")
        fontControls.addView(value, LinearLayout.LayoutParams(dp(52), dp(48)))
        fontControls.addView(slider, LinearLayout.LayoutParams(0, dp(48), 1f))
        fontControls.addView(reset, LinearLayout.LayoutParams(dp(56), dp(48)))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val percent = ArchiveFontScale.fromProgress(progress)
                value.text = "$percent%"
                applyFontPercent(percent)
                if (fromUser) AppSettings.setArchiveFontPercent(host, percent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        reset.setOnClickListener {
            slider.progress = ArchiveFontScale.toProgress(ArchiveFontScale.DEFAULT)
            AppSettings.setArchiveFontPercent(host, ArchiveFontScale.DEFAULT)
        }
    }

    private fun applyFontPercent(percent: Int) {
        val nextPercent = ArchiveFontScale.normalize(percent)
        if (fontPercent == nextPercent) return
        val index = if (::list.isInitialized) list.firstVisiblePosition else 0
        val offset = if (::list.isInitialized) list.getChildAt(0)?.let { it.top - list.paddingTop } ?: 0 else 0
        val scaledOffset = (offset.toFloat() * nextPercent / fontPercent).toInt()
        fontPercent = nextPercent
        adapter.notifyDataSetChanged()
        if (::list.isInitialized && adapter.count > 0) {
            list.setSelectionFromTop(index.coerceIn(0, adapter.count - 1), scaledOffset)
            rememberPosition()
        }
    }

    /** Move the existing search views; do not recreate the list or discard its state. */
    private fun applySearchPlacement() {
        val bottom = AppSettings.archiveSearchAtBottom(host)
        if (bottomPlacement == bottom) return
        (searchControls.parent as? ViewGroup)?.removeView(searchControls)
        searchControls.removeView(searchLinks)
        searchControls.removeView(fontControls)
        searchControls.addView(searchLinks, if (bottom) 0 else 1, LinearLayout.LayoutParams(-1, dp(42)))
        searchControls.addView(fontControls, if (bottom) 1 else 2, LinearLayout.LayoutParams(-1, -2))
        val target = if (bottom) searchBottom else searchTop
        target.addView(searchControls, LinearLayout.LayoutParams(-1, -2))
        searchTop.visibility = if (bottom) View.GONE else View.VISIBLE
        searchBottom.visibility = if (bottom) View.VISIBLE else View.GONE
        bottomPlacement = bottom
    }

    fun resume() {
        if (disposed || !::searchControls.isInitialized) return
        applySearchPlacement()
        updateBoardButtons()
    }

    private fun showCopyMenu(entry: ArchiveThread) {
        ThemedMenu.choose(host, "コピー", arrayOf("スレタイとURLをコピー", "スレタイをコピー", "URLをコピー"), -1) { index ->
            val original = entry.originalUrl.ifEmpty { if (otherBoards()) entry.url else "" }
            if (index != 1 && original.isEmpty()) {
                Toast.makeText(host, "元スレッドURLを確認できませんでした", Toast.LENGTH_SHORT).show()
                return@choose
            }
            val value = when (index) {
                0 -> entry.title + "\n" + original
                1 -> entry.title
                2 -> original
                else -> return@choose
            }
            val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@choose
            clipboard.setPrimaryClip(ClipData.newPlainText("過去ログ検索", value))
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(host, "コピーしました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPageJump() {
        if (loading || disposed) return
        val page = state.result ?: return
        val query = state.submitted ?: return
        if (page.totalPages <= 1) return
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, "ページ指定"))
        panel.addView(ThemedMenu.formLabel(host, "1～" + number(page.totalPages.toLong()) + "ページ"))
        val input = ThemedMenu.formInput(host, page.page.toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            selectAll()
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        val buttons = LinearLayout(host)
        val cancel = ThemedMenu.formAction(host, "キャンセル")
        val go = ThemedMenu.formAction(host, "移動")
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(go, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(buttons)
        val dialog = ThemedMenu.showForm(host, panel)
        cancel.setOnClickListener { dialog.dismiss() }
        go.setOnClickListener {
            val target = input.text.toString().toIntOrNull()
            if (target == null || target !in 1..page.totalPages) {
                input.error = "1～" + number(page.totalPages.toLong()) + "を指定してください"
                return@setOnClickListener
            }
            dialog.dismiss()
            if (target != page.page) load(query, target)
        }
    }

    private fun number(value: Long): String = String.format(Locale.JAPAN, "%,d", value)

    private fun rememberPosition() {
        if (!::list.isInitialized) return
        val query = state.submitted ?: return
        val page = state.result ?: return
        state.pages.rememberPosition(state.provider, query, page.page, list.firstVisiblePosition,
            list.getChildAt(0)?.let { it.top - list.paddingTop } ?: 0)
    }

    private fun restorePosition(position: ArchivePageCache.Position) {
        if (adapter.count > 0) list.setSelectionFromTop(position.index.coerceIn(0, adapter.count - 1), position.offset)
    }

    private fun restoreSavedPage() {
        loading = true
        val request = ++generation
        refresh()
        worker.execute {
            val saved = SavedSearchResults.readArchive(host)
            host.runOnUiThread {
                if (disposed || host.isDestroyed || host.isFinishing || request != generation) return@runOnUiThread
                loading = false
                if (saved != null && AppSettings.keepSearchResults(host) &&
                    saved.provider == state.provider && saved.query.boardCode == state.draft.boardCode) {
                    state.draft = saved.query
                    keyword.setText(saved.query.keyword)
                    state.pages.put(saved.provider, saved.query, saved.page)
                    displayPage(saved.query, ArchivePageCache.Cached(saved.page))
                } else refresh()
            }
        }
    }

    private fun displayPage(query: ArchiveQuery, cached: ArchivePageCache.Cached) {
        state.result = cached.result
        cached.result.form?.let { state.form = it }
        state.submitted = query
        state.message = ""
        refresh()
        restorePosition(cached.position)
        SavedSearchResults.saveArchive(host, state.provider, query, cached.result)
    }

    private fun readBoardGroups(): List<BoardMenu.Category> {
        return runCatching {
                fun cached(name: String, json: Boolean): List<BoardMenu.Category> {
                    return host.openFileInput(name).use { input ->
                        val bytes = HttpResponseReader.read(input, 4 * 1024 * 1024, -1)
                        val text = String(bytes, StandardCharsets.UTF_8)
                        if (json) BoardMenu.parseJson(text) else BoardMenu.parseHtml(text)
                    }
                }
                val json = runCatching { cached("bbsmenu_5ch.json", true) }.getOrDefault(emptyList())
                val html = if (json.isEmpty()) runCatching { cached("bbsmenu_5ch.html", false) }.getOrDefault(emptyList()) else emptyList()
                (json.ifEmpty { html }).ifEmpty {
                    runCatching { BoardMenu.fetch() }.getOrDefault(emptyList()).ifEmpty {
                        InputStreamReader(host.assets.open("offline_boards.tsv"), StandardCharsets.UTF_8).use {
                            BoardMenu.parseOfflineSnapshot(it)
                        }
                    }
                }
            }.getOrDefault(emptyList())
    }

    private fun showBoards() {
        if (loading || disposed || !supportsBoardSelection()) return
        if (otherBoards()) { showOtherServices(); return }
        boardGroups?.takeIf { it.isNotEmpty() }?.let { showBoardCategories(it); return }
        loading = true
        val request = ++generation
        refresh()
        message.text = "板一覧を取得中…"
        worker.execute {
            val groups = readBoardGroups()
            host.runOnUiThread {
                if (disposed || host.isFinishing || host.isDestroyed || request != generation) return@runOnUiThread
                loading = false
                boardGroups = groups.takeIf { it.isNotEmpty() }
                refresh()
                if (!boardButton.isShown || !host.hasWindowFocus()) return@runOnUiThread
                if (groups.isEmpty()) Toast.makeText(host, "板一覧を取得できませんでした", Toast.LENGTH_LONG).show()
                else showBoardCategories(groups)
            }
        }
    }

    private fun showOtherServices() {
        val services = KyodemoOtherBoards.services
        showBoardList("掲示板を選択", services.map { it.label }, null) { index ->
            services.getOrNull(index)?.let { loadOtherMenu(it, it.root) }
        }
    }

    private fun loadOtherMenu(service: KyodemoOtherBoards.Service, category: String) {
        if (loading || disposed || !otherBoards()) return
        loading = true
        val request = ++generation
        refresh()
        message.text = "板一覧を取得中…"
        worker.execute {
            val loaded = runCatching { KyodemoOtherBoards.fetch(service, category) }
            host.runOnUiThread {
                if (disposed || host.isFinishing || host.isDestroyed || request != generation) return@runOnUiThread
                loading = false
                refresh()
                if (!boardButton.isShown || !host.hasWindowFocus() || !otherBoards()) return@runOnUiThread
                loaded.onSuccess { menu ->
                    val entries = menu.categories + menu.boards.map { KyodemoOtherBoards.Item(it.code, it.name) }
                    if (entries.isEmpty()) {
                        Toast.makeText(host, "板一覧を取得できませんでした", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    val group = BoardMenu.Category(service.label).apply { boards.addAll(menu.boards) }
                    showBoardList(service.label, entries.map { it.label },
                        { if (category == service.root) showOtherServices() else loadOtherMenu(service, service.root) },
                        search = if (menu.boards.isEmpty()) null else {
                            {
                                BoardSearchDialog.show(host, listOf(group), { loadOtherMenu(service, category) }) { entry ->
                                    selectBoard(entry.code, entry.name)
                                }
                            }
                        }) { index ->
                        val entry = entries[index]
                        if (index < menu.categories.size) loadOtherMenu(service, entry.code)
                        else selectBoard(entry.code, entry.label)
                    }
                }.onFailure { Toast.makeText(host, "板一覧を取得できませんでした", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showBoardCategories(groups: List<BoardMenu.Category>) {
        val allAllowed = state.provider == ArchiveProvider.FIVECH_NUKO
        val labels = (if (allAllowed) listOf("全板") else emptyList()) + groups.map { it.name + "（" + it.boards.size + "）" }
        showBoardList("カテゴリを選択", labels, null,
            search = { showBoardSearch(groups) }) { index ->
            if (allAllowed && index == 0) {
                selectBoard("", "")
                return@showBoardList
            }
            val category = groups[index - if (allAllowed) 1 else 0]
            showBoardList(category.name, category.boards.map { it.toString() }, { showBoardCategories(groups) },
                search = { showBoardSearch(groups) }) { position ->
                if (disposed || !supportsBoardSelection()) return@showBoardList
                val board = category.boards[position]
                selectBoard(board.code, board.name)
            }
        }
    }

    private fun selectBoard(code: String, name: String) {
        if (disposed || loading || !supportsBoardSelection() || (!BoardFavorites.valid(code) && !(state.provider == ArchiveProvider.FIVECH_NUKO && code.isEmpty()))) return
        if (otherBoards() && !KyodemoOtherBoards.validBoard(code)) return
        saveKeyword()
        if (state.draft.boardCode != code) {
            state.pages.clear()
            state.result = null
                    SavedSearchResults.clearArchive(host)
            state.submitted = null
            state.message = ""
        }
        state.draft = state.draft.copy(boardCode = code, boardName = name.ifBlank { code })
        ArchiveSelectionStore.save(host, state.provider, state.draft)
        refresh()
    }

    private fun saveFavoriteBoard() {
        if (loading || disposed || !supportsBoardSelection()) return
        val code = state.draft.boardCode
        if (!BoardFavorites.valid(code)) {
            Toast.makeText(host, "板を選択してください", Toast.LENGTH_SHORT).show()
            return
        }
        BoardFavorites.add(host, code)
        updateBoardButtons()
        Toast.makeText(host, "お気に入りに登録しました", Toast.LENGTH_SHORT).show()
    }

    private fun showFavoriteBoards() {
        if (loading || disposed || !supportsBoardSelection()) return
        val favorites = BoardFavorites.read(host)
        if (favorites.isEmpty()) {
            Toast.makeText(host, "板のお気に入りがありません", Toast.LENGTH_SHORT).show()
            return
        }
        // Home and archive share the same code list. Resolve names when the menu is available.
        val names = boardGroups.orEmpty().flatMap { it.boards }.associate { it.code to it.name }
        val labels = favorites.map { code ->
            val name = names[code] ?: if (code == state.draft.boardCode) state.draft.boardName else ""
            if (name.isNotBlank() && name != code) "$name ($code)" else code
        }
        showBoardList("板のお気に入り", labels, null) { index ->
            val code = favorites[index]
            selectBoard(code, names[code] ?: if (code == state.draft.boardCode) state.draft.boardName else code)
        }
    }

    private fun updateBoardButtons() {
        val fivech = supportsBoardSelection()
        boardRow.visibility = if (fivech) View.VISIBLE else View.GONE
        boardButton.text = (state.draft.boardName.ifBlank { state.draft.boardCode.ifBlank { if (state.provider == ArchiveProvider.FIVECH_NUKO) "全板" else "板を選択" } }) + " ▾"
        boardButton.isEnabled = !loading
        saveBoardButton.visibility = if (otherBoards()) View.GONE else View.VISIBLE
        favoriteBoardsButton.visibility = if (otherBoards()) View.GONE else View.VISIBLE
        saveBoardButton.isEnabled = !loading && BoardFavorites.valid(state.draft.boardCode)
        saveBoardButton.alpha = if (saveBoardButton.isEnabled) 1f else 0.45f
        saveBoardButton.text = if (state.draft.boardCode in BoardFavorites.read(host)) "★ 保存済" else "☆ 保存"
        favoriteBoardsButton.isEnabled = !loading
    }

    private fun showBoardList(title: String, labels: List<String>, back: (() -> Unit)?,
                              search: (() -> Unit)? = null, pick: (Int) -> Unit) {
        val panel = ThemedMenu.formPanel(host)
        val heading = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(ThemedMenu.formTitle(host, title), LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(heading)
        val searchIcon = if (search != null) boardSearchIcon().also {
            heading.addView(it, LinearLayout.LayoutParams(dp(48), dp(48)))
        } else null
        val choices = ListView(host).apply {
            divider = android.graphics.drawable.ColorDrawable(host.getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = TouchFeedback.listSelector(host)
            adapter = object : ArrayAdapter<String>(host, android.R.layout.simple_list_item_1, labels) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getView(position, convertView, parent) as TextView).apply {
                        setTextColor(host.getColor(R.color.text_primary))
                        textSize = 14f
                        minHeight = dp(49)
                    }
            }
        }
        panel.addView(choices, LinearLayout.LayoutParams(-1, minOf(dp(540), (host.resources.displayMetrics.heightPixels * 0.60f).toInt())))
        val close = ThemedMenu.formAction(host, if (back == null) "閉じる" else "カテゴリに戻る")
        panel.addView(close, LinearLayout.LayoutParams(-1, dp(46)))
        val dialog = ThemedMenu.showForm(host, panel)
        choices.setOnItemClickListener { _, _, position, _ -> dialog.dismiss(); pick(position) }
        close.setOnClickListener { dialog.dismiss(); back?.invoke() }
        searchIcon?.setOnClickListener { dialog.dismiss(); search?.invoke() }
    }

    private fun boardSearchIcon(): View = BoardSearchDialog.icon(host)

    private fun showBoardSearch(groups: List<BoardMenu.Category>) {
        if (disposed || loading || !supportsBoardSelection()) return
        BoardSearchDialog.show(host, groups, { showBoardCategories(groups) }) { entry ->
            selectBoard(entry.code, entry.name)
        }
    }

    private fun showKeywordHistory() {
        if (disposed || loading) return
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, "過去ログ検索履歴"))
        val scroll = ScrollView(host)
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, minOf(dp(380), (host.resources.displayMetrics.heightPixels * 0.5f).toInt())))
        val buttons = LinearLayout(host)
        val clear = ThemedMenu.formAction(host, "全削除")
        val close = ThemedMenu.formAction(host, "閉じる")
        buttons.addView(clear, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(close, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(buttons)
        val dialog = ThemedMenu.showForm(host, panel)
        fun render() {
            rows.removeAllViews()
            val entries = ArchiveKeywordHistory.read(host)
            clear.isEnabled = entries.isNotEmpty()
            clear.alpha = if (clear.isEnabled) 1f else 0.5f
            if (entries.isEmpty()) rows.addView(ThemedMenu.formLabel(host, "検索履歴はありません"))
            for (term in entries) {
                val row = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
                val word = ThemedMenu.formAction(host, term).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(host.getColor(R.color.text_primary))
                    maxLines = 2
                    setOnClickListener { keyword.setText(term); keyword.setSelection(keyword.length()); saveKeyword(); dialog.dismiss() }
                }
                val remove = ThemedMenu.formAction(host, "削除").apply {
                    contentDescription = "検索履歴を削除：" + term
                    setOnClickListener { ArchiveKeywordHistory.remove(host, term); render() }
                }
                row.addView(word, LinearLayout.LayoutParams(0, dp(48), 1f))
                row.addView(remove, LinearLayout.LayoutParams(dp(56), dp(48)))
                rows.addView(row)
            }
        }
        render()
        close.setOnClickListener { dialog.dismiss() }
        clear.setOnClickListener {
            ThemedMenu.confirm(host, "検索履歴を全削除", "過去ログ検索のキーワード履歴をすべて削除しますか？", "削除") {
                ArchiveKeywordHistory.clear(host)
                render()
            }
        }
    }

    private fun saveKeyword() {
        if (::keyword.isInitialized) state.draft = state.draft.copy(keyword = keyword.text.toString())
    }

    private fun showOptions() {
        if (loading || disposed) return
        saveKeyword()
        if (state.provider != ArchiveProvider.EDGE_ARCHIVE) {
            showProviderOptions()
            return
        }
        if (state.form != null && state.form?.fuzzyValue != null) {
            showOptionsDialog()
            return
        }
        loading = true
        val request = ++generation
        refresh()
        message.text = "サイトの検索条件を取得中…"
        worker.execute {
            val loaded = runCatching { ArchiveRepository.fetchForm() }
            host.runOnUiThread {
                if (disposed || host.isFinishing || host.isDestroyed || request != generation) return@runOnUiThread
                loading = false
                loaded.onSuccess { state.form = it }
                refresh()
                if (options.isShown && host.hasWindowFocus()) showOptionsDialog()
            }
        }
    }

    private fun showProviderOptions() {
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, "詳細条件"))
        val nuko = state.provider == ArchiveProvider.FIVECH_NUKO
        var draft = state.draft
        var excluded: EditText? = null
        var sort = draft.sort
        var minimum = draft.minimum
        var exact: CheckBox? = null
        var external: CheckBox? = null
        fun check(label: String, selected: Boolean): CheckBox = CheckBox(host).apply {
            text = label
            textSize = 14f
            setTextColor(host.getColor(R.color.text_primary))
            buttonTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
            isChecked = selected
            minimumHeight = dp(44)
            panel.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        if (nuko) {
            panel.addView(ThemedMenu.formLabel(host, "過去ログ検索：ON"))
            exact = check("完全一致", draft.exact)
            external = check("外部板も検索", draft.externalBoards)
        } else {
            panel.addView(ThemedMenu.formLabel(host, "除外ワード（空白区切り）"))
            excluded = ThemedMenu.formInput(host, draft.exclude)
            panel.addView(excluded, LinearLayout.LayoutParams(-1, dp(44)))
            fun day(label: String, start: Boolean) {
                panel.addView(ThemedMenu.formLabel(host, label))
                val row = LinearLayout(host)
                val pick = archiveButton((if (start) draft.from else draft.to).ifEmpty { "指定なし" })
                val clear = ThemedMenu.formAction(host, "解除")
                row.addView(pick, LinearLayout.LayoutParams(0, dp(44), 1f))
                row.addView(clear, LinearLayout.LayoutParams(dp(64), dp(44)))
                panel.addView(row)
                fun set(value: String) {
                    draft = if (start) draft.copy(from = value) else draft.copy(to = value)
                    pick.text = value.ifEmpty { "指定なし" }
                }
                pick.setOnClickListener {
                    val value = if (start) draft.from else draft.to
                    CalendarDialog.show(host, value.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) } ?: LocalDate.now()) {
                        set(it.toString())
                    }
                }
                clear.setOnClickListener { set("") }
            }
            day("開始日", true)
            day("終了日", false)
            panel.addView(ThemedMenu.formLabel(host, "複数の検索ワードは空白区切りでAND検索"))
            if (state.provider == ArchiveProvider.EDGE_KYODEMO || requiresBoard()) {
                fun choice(label: String, values: List<String>, labels: List<String>, selected: String, apply: (String) -> Unit) {
                    panel.addView(ThemedMenu.formLabel(host, label))
                    var index = values.indexOf(selected).coerceAtLeast(0)
                    val button = archiveButton(labels[index] + " ▾")
                    panel.addView(button, LinearLayout.LayoutParams(-1, dp(44)))
                    button.setOnClickListener {
                        ThemedMenu.choose(host, label, labels.toTypedArray(), index) { selectedIndex ->
                            if (selectedIndex in values.indices) {
                                index = selectedIndex
                                button.text = labels[index] + " ▾"
                                apply(values[index])
                            }
                        }
                    }
                }
                choice("ソート", listOf("new", "old", "replies"), listOf("立降順（新しい順）", "立昇順（古い順）", "レス数順"), sort) { sort = it }
                choice("レス数", listOf("", "50", "300", "900"), listOf("指定なし", "50以上", "300以上", "900以上"), minimum) { minimum = it }
            }
        }
        val buttons = LinearLayout(host)
        val cancel = ThemedMenu.formAction(host, "キャンセル")
        val apply = ThemedMenu.formAction(host, "適用")
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(apply, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(buttons)
        val dialog = ThemedMenu.showForm(host, panel)
        cancel.setOnClickListener { dialog.dismiss() }
        apply.setOnClickListener {
            val updated = draft.copy(exclude = excluded?.text?.toString() ?: "",
                exact = exact?.isChecked ?: false, externalBoards = external?.isChecked ?: false,
                sort = sort, minimum = minimum)
            try {
                updated.copy(keyword = updated.keyword.ifBlank { "x" }).validate()
                state.draft = updated
                dialog.dismiss()
                refresh()
            } catch (error: Exception) {
                Toast.makeText(host, error.message ?: "条件が不正です", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOptionsDialog() {
        if (disposed || state.provider != ArchiveProvider.EDGE_ARCHIVE) return
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, "詳細条件"))
        val scroll = ScrollView(host)
        val fields = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(fields)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, minOf(dp(440), (host.resources.displayMetrics.heightPixels * 0.55f).toInt())))
        fun field(label: String, value: String): EditText {
            fields.addView(ThemedMenu.formLabel(host, label))
            return ThemedMenu.formInput(host, value).also { fields.addView(it, LinearLayout.LayoutParams(-1, dp(44))) }
        }
        val excluded = field("スレタイ除外ワード", state.draft.exclude)
        fields.addView(ThemedMenu.formLabel(host, "レス数範囲"))
        val range = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        val min = ThemedMenu.formInput(host, state.draft.minimum).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "以上"
            setHintTextColor(host.getColor(R.color.text_secondary))
        }
        val max = ThemedMenu.formInput(host, state.draft.maximum).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "以下"
            setHintTextColor(host.getColor(R.color.text_secondary))
        }
        range.addView(min, LinearLayout.LayoutParams(0, dp(44), 1f))
        range.addView(ThemedMenu.formLabel(host, "～").apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }, LinearLayout.LayoutParams(dp(28), dp(44)))
        range.addView(max, LinearLayout.LayoutParams(0, dp(44), 1f))
        fields.addView(range)
        fields.addView(ThemedMenu.formLabel(host, "上限は取得ページごとに絞り込みます"))
        val form = state.form
        val choices = form?.sorts.orEmpty()
        var sort = state.draft.sort
        fields.addView(ThemedMenu.formLabel(host, "ソート"))
        val sortButton = archiveButton(
            (choices.firstOrNull { it.value == sort }?.label ?: "新しい順") + " ▾")
        sortButton.isEnabled = choices.isNotEmpty()
        sortButton.alpha = if (sortButton.isEnabled) 1f else 0.5f
        fields.addView(sortButton, LinearLayout.LayoutParams(-1, dp(44)))
        sortButton.setOnClickListener {
            ThemedMenu.choose(host, "ソート", choices.map { it.label }.toTypedArray(),
                choices.indexOfFirst { it.value == sort }) { index ->
                choices.getOrNull(index)?.let { choice ->
                    sort = choice.value
                    sortButton.text = choice.label + " ▾"
                }
            }
        }
        val fuzzy = CheckBox(host).apply {
            text = "半角全角あいまい検索"
            textSize = 14f
            setTextColor(host.getColor(R.color.text_primary))
            buttonTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
            isChecked = state.draft.fuzzy.isNotEmpty()
            isEnabled = form?.fuzzyValue != null
            alpha = if (isEnabled) 1f else 0.5f
            minimumHeight = dp(44)
        }
        fields.addView(fuzzy, LinearLayout.LayoutParams(-1, -2))
        if (form == null || form.fuzzyValue == null) {
            fields.addView(ThemedMenu.formLabel(host, "一部のサイト検索条件を取得できませんでした。詳細条件を開き直して再確認してください。"))
        }
        var from = state.draft.from
        var to = state.draft.to
        fun dateRow(label: String, initial: String, change: (String) -> Unit) {
            var day = initial
            fields.addView(ThemedMenu.formLabel(host, label))
            val row = LinearLayout(host)
            val pick = archiveButton(day.ifEmpty { "指定なし" })
            val clear = ThemedMenu.formAction(host, "解除")
            row.addView(pick, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(clear, LinearLayout.LayoutParams(dp(64), dp(44)))
            fields.addView(row)
            pick.setOnClickListener {
                CalendarDialog.show(host, day.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) } ?: LocalDate.now()) {
                    day = it.toString()
                    pick.text = day
                    change(day)
                }
            }
            clear.setOnClickListener { day = ""; pick.text = "指定なし"; change(day) }
        }
        dateRow("開始日", from) { from = it }
        dateRow("終了日", to) { to = it }
        val buttons = LinearLayout(host)
        val cancel = ThemedMenu.formAction(host, "キャンセル")
        val apply = ThemedMenu.formAction(host, "適用")
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(apply, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(buttons)
        val dialog = ThemedMenu.showForm(host, panel)
        cancel.setOnClickListener { dialog.dismiss() }
        apply.setOnClickListener {
            val updated = state.draft.copy(exclude = excluded.text.toString(), minimum = min.text.toString(),
                maximum = max.text.toString(), from = from, to = to, sort = sort,
                fuzzy = if (fuzzy.isChecked) form?.fuzzyValue ?: state.draft.fuzzy else "")
            try {
                updated.copy(keyword = updated.keyword.ifBlank { "x" }).validate()
                state.draft = updated
                dialog.dismiss()
                refresh()
            } catch (error: Exception) {
                android.widget.Toast.makeText(host, error.message ?: "条件が不正です", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSearch() {
        if (loading || disposed) return
        saveKeyword()
        val query = state.draft
        if (requiresBoard() && query.boardCode.isEmpty()) {
            showBoards()
            return
        }
        try { query.validate() } catch (error: Exception) {
            message.text = error.message ?: "検索条件が不正です"
            return
        }
        (host.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(keyword.windowToken, 0)
        keyword.clearFocus()
        ArchiveKeywordHistory.record(host, query.keyword)
        load(query, 1, fresh = true)
    }

    private fun load(query: ArchiveQuery, page: Int, fresh: Boolean = false) {
        if (loading || disposed) return
        rememberPosition()
        val requested = state.provider
        if (!fresh) {
            state.pages.get(requested, query, page)?.let {
                displayPage(query, it)
                return
            }
        }
        loading = true
        val request = ++generation
        val browserUa = if (requested == ArchiveProvider.FIVECH_NUKO) nukoUserAgent else ""
        refresh()
        worker.execute {
            val outcome = runCatching {
                val fetched = ArchiveRepository.search(requested, query, page, browserUa)
                if (requested == ArchiveProvider.FIVECH_NUKO) android.util.Log.i("HissiArchive",
                    "Nuko native success / " + HttpTransport.lastDiagnostic() + " / rows=" + fetched.items.size)
                resolveOriginalLinks(requested, fetched)
            }
            host.runOnUiThread {
                if (disposed || host.isFinishing || host.isDestroyed || request != generation) return@runOnUiThread
                val failure = outcome.exceptionOrNull()
                if (requested == ArchiveProvider.FIVECH_NUKO && failure is NukoPageFormatException) {
                    android.util.Log.w("HissiArchive", "Nuko native: " + failure.message)
                    message.text = "ぬこ / ブラウザ方式で再取得中…"
                    nukoBrowser = NukoBrowserFallback(host)
                    nukoBrowser?.load(ArchiveExternalSearch.url(requested, query, page)) { html ->
                        nukoBrowser = null
                        if (disposed || request != generation) return@load
                        if (html.isFailure) {
                            finishLoad(request, requested, query, fresh, Result.failure(html.exceptionOrNull()!!))
                        } else {
                            worker.execute {
                                val parsed = runCatching {
                                    val body = html.getOrThrow()
                                    val fetched = ArchiveProviderParser.nuko(body, page, query.boardCode)
                                    android.util.Log.i("HissiArchive", "Nuko WebView success / chars=" + body.length + " / rows=" + fetched.items.size)
                                    resolveOriginalLinks(requested, ArchiveRepository.filterRange(fetched, query))
                                }
                                host.runOnUiThread { finishLoad(request, requested, query, fresh, parsed) }
                            }
                        }
                    }
                } else finishLoad(request, requested, query, fresh, outcome)
            }
        }
    }

    private fun finishLoad(request: Int, requested: ArchiveProvider, query: ArchiveQuery, fresh: Boolean, outcome: Result<ArchivePage>) {
        if (disposed || host.isFinishing || host.isDestroyed || request != generation) return
        loading = false
        outcome.onSuccess {
            if (fresh) state.pages.clear()
            state.pages.put(requested, query, it)
            displayPage(query, ArchivePageCache.Cached(it))
        }.onFailure {
            android.util.Log.w("HissiArchive", requested.name + " search failed: " + it.javaClass.simpleName +
                (if (it is NukoPageFormatException) " / " + it.message else ""))
            state.message = "取得できませんでした: " + (it.message ?: "通信エラー")
            refresh()
        }
    }

    /** Keep archive reading URLs and original-copy URLs separate. Resolve each board once. */
    private fun resolveOriginalLinks(requested: ArchiveProvider, result: ArchivePage): ArchivePage {
        if (result.items.isEmpty()) return result
        if (requested == ArchiveProvider.OTHER_KYODEMO) return result
        if (requested == ArchiveProvider.EDGE_ARCHIVE || requested == ArchiveProvider.EDGE_KYODEMO) {
            return result.copy(items = result.items.map { entry ->
                val original = ThreadOpener.validatedThreadUrl(entry.url)
                entry.copy(originalUrl = original)
            })
        }
        val codes = result.items.map { ArchiveOriginalLink.boardCode(it.url) }.filter { it.isNotEmpty() }.toSet()
        if (codes.any { it !in resolvedBoardAddresses }) {
            fun addresses(groups: List<BoardMenu.Category>) = groups.asSequence().flatMap { it.boards.asSequence() }
                .filter { it.address.isNotBlank() }.associate { it.code to it.address }
            resolvedBoardAddresses = resolvedBoardAddresses + addresses(readBoardGroups())
            if (codes.any { it !in resolvedBoardAddresses }) {
                resolvedBoardAddresses = resolvedBoardAddresses + addresses(runCatching { BoardMenu.fetch() }.getOrDefault(emptyList()))
            }
        }
        return result.copy(items = result.items.map { entry ->
            val board = ArchiveOriginalLink.boardCode(entry.url)
            val address = resolvedBoardAddresses[board].orEmpty()
            val converted = if (requested == ArchiveProvider.FIVECH_NUKO)
                ArchiveOriginalLink.nuko5ch(entry.url, board, address)
            else ArchiveOriginalLink.kyodemo5ch(entry.url, board, address)
            val original = ThreadOpener.validated5chThreadUrl(converted)
            entry.copy(originalUrl = original, url = if (requested == ArchiveProvider.FIVECH_KYODEMO && original.isNotEmpty()) original else entry.url)
        })
    }

    private fun refresh() {
        provider.text = state.provider.label + " ▾"
        provider.isEnabled = !loading
        updateBoardButtons()
        searchHistoryButton.isEnabled = !loading
        keyword.isEnabled = !loading
        search.isEnabled = !loading
        options.isEnabled = !loading
        browser.isEnabled = !loading
        browser.alpha = if (browser.isEnabled) 1f else 0.45f
        search.alpha = if (search.isEnabled) 1f else 0.45f
        options.alpha = if (options.isEnabled) 1f else 0.45f
        val page = state.result
        message.text = when {
            loading -> "検索中…"
            state.message.isNotEmpty() -> state.message
            page != null -> {
                val submitted = state.submitted
                if (page.total == 0L) "検索結果：0件（該当なし）"
                else {
                    val range = number(page.firstIndex) + "～" + number(page.lastIndex) + "件目 / 全" + number(page.total) + (if (page.totalCapped) "件以上（取得上限）" else "件")
                    if (!submitted?.maximum.isNullOrEmpty() || !submitted?.minimum.isNullOrEmpty()) {
                        range + "（サイト）\n範囲内：" + number(page.items.size.toLong()) + "件"
                    } else range
                }
            }
            else -> "スレタイを入力して検索してください"
        }
        previous.isEnabled = !loading && page != null && page.page > 1
        next.isEnabled = !loading && page?.hasNext == true && page.page < page.totalPages
        pageJump.isEnabled = !loading && page != null && page.totalPages > 1
        pageJump.text = if (page == null) "ページ指定" else
            (if (page.totalPages == 0) "0" else number(page.page.toLong())) + " / " + number(page.totalPages.toLong())
        pageJump.contentDescription = "ページ指定：" + pageJump.text
        pageJump.alpha = if (pageJump.isEnabled) 1f else 0.55f
        previous.alpha = if (previous.isEnabled) 1f else 0.45f
        next.alpha = if (next.isEnabled) 1f else 0.45f
        adapter.notifyDataSetChanged()
    }

    fun dispose() {
        saveKeyword()
        rememberPosition()
        disposed = true
        generation++
        nukoBrowser?.close()
        nukoBrowser = null
        worker.shutdownNow()
    }

    private inner class ThreadsAdapter : BaseAdapter() {
        override fun getCount() = state.result?.items?.size ?: 0
        override fun getItem(position: Int) = state.result!!.items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convert: View?, parent: ViewGroup): View {
            val row = (convert as? LinearLayout) ?: LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(host).apply { setTextColor(host.getColor(R.color.text_primary)) },
                    LinearLayout.LayoutParams(-1, -2))
                addView(TextView(host).apply { setTextColor(host.getColor(R.color.text_secondary)) },
                    LinearLayout.LayoutParams(-1, -2))
            }
            val scale = fontPercent / 100f
            val padding = dp((8 * scale).toInt())
            row.setPadding(dp(12), padding, dp(12), padding)
            row.minimumHeight = dp((58 * scale).toInt())
            val entry = getItem(position)
            (row.getChildAt(0) as TextView).apply {
                textSize = 15f * scale
                text = entry.title
            }
            (row.getChildAt(1) as TextView).apply {
                textSize = 12f * scale
                text = ArchiveExternalSearch.metadata(entry)
            }
            return row
        }
    }
}
