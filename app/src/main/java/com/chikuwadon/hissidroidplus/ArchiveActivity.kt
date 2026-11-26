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
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.LocalDate
import java.util.concurrent.Executors

class ArchiveScreenState : ViewModel() {
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
        applyContentInsets(root)
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
    private lateinit var searchHistoryButton: View
    private var boardGroups: List<BoardMenu.Category>? = null
    private lateinit var search: TextView
    private lateinit var options: TextView
    private lateinit var message: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private lateinit var pageJump: TextView
    private lateinit var browser: TextView
    private lateinit var list: ListView
    private val adapter = ThreadsAdapter()

    fun buildScreen(root: LinearLayout) {
        root.addView(AppBar.title(host, "過去ログ検索"), LinearLayout.LayoutParams(-1, dp(56)))
        val controls = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        provider = ThemedMenu.formButton(host, state.provider.label + " ▾")
        controls.addView(provider, LinearLayout.LayoutParams(-1, dp(44)))
        provider.setOnClickListener {
            if (loading) return@setOnClickListener
            val providers = ArchiveProvider.entries
            val labels = providers.map { it.label + if (it.nativeSearch) "" else "（ブラウザ検索）" }.toTypedArray()
            ThemedMenu.choose(host, "検索サービス", labels, providers.indexOf(state.provider)) { index ->
                val selected = providers.getOrNull(index) ?: return@choose
                if (selected != state.provider) {
                    saveKeyword()
                    state.drafts[state.provider] = state.draft
                    val previousKeyword = state.draft.keyword
                    state.form = null
                    state.provider = selected
                    state.draft = state.drafts[selected] ?: ArchiveQuery(previousKeyword)
                    keyword.setText(state.draft.keyword)
                    state.pages.clear()
                    state.submitted = null
                    state.result = null
                    state.message = ""
                    refresh()
                    if (selected == ArchiveProvider.FIVECH_KYODEMO && state.draft.boardCode.isEmpty()) showBoards()
                }
            }
        }
        boardButton = ThemedMenu.formButton(host, "板を選択 ▾")
        controls.addView(boardButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        boardButton.setOnClickListener { showBoards() }
        val line = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        controls.addView(line, LinearLayout.LayoutParams(-1, dp(52)))
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
        search = ThemedMenu.formButton(host, "検索")
        line.addView(search, LinearLayout.LayoutParams(dp(72), dp(44)).apply { marginStart = dp(6) })
        search.setOnClickListener { startSearch() }
        val links = LinearLayout(host)
        controls.addView(links, LinearLayout.LayoutParams(-1, dp(42)))
        options = ThemedMenu.formAction(host, "詳細条件")
        browser = ThemedMenu.formAction(host, "外部ブラウザ")
        links.addView(options, LinearLayout.LayoutParams(0, -1, 1f))
        links.addView(browser, LinearLayout.LayoutParams(0, -1, 1f))
        options.setOnClickListener { showOptions() }
        browser.setOnClickListener {
            saveKeyword()
            if (state.provider == ArchiveProvider.FIVECH_KYODEMO && state.draft.boardCode.isEmpty()) {
                showBoards()
                return@setOnClickListener
            }
            val target = if (state.draft.keyword.isNotBlank()) {
                try { ArchiveExternalSearch.url(state.provider, state.draft) }
                catch (error: Exception) {
                    message.text = error.message ?: "検索条件が不正です"
                    return@setOnClickListener
                }
            } else if (state.provider == ArchiveProvider.FIVECH_KYODEMO) "https://www.kyodemo.net/sdemo/b/" + state.draft.boardCode + "/" else state.provider.url
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
                when {
                    state.provider == ArchiveProvider.EDGE_ARCHIVE -> ThreadOpener.open(host, entry.url, true)
                    ThreadOpener.validatedThreadUrl(entry.url).isNotEmpty() -> ThreadOpener.open(host, entry.url, true)
                    else -> BrowserLauncher.open(host, entry.url)
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
        val pager = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        previous = ThemedMenu.formAction(host, "前のページ")
        pageJump = ThemedMenu.formButton(host, "ページ指定")
        next = ThemedMenu.formAction(host, "次のページ")
        pager.addView(previous, LinearLayout.LayoutParams(0, dp(44), 1f))
        pager.addView(pageJump, LinearLayout.LayoutParams(0, dp(40), 1.15f))
        pager.addView(next, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(pager, LinearLayout.LayoutParams(-1, -2))
        previous.setOnClickListener { state.submitted?.let { q -> state.result?.let { if (it.page > 1) load(q, it.page - 1) } } }
        next.setOnClickListener { state.submitted?.let { q -> state.result?.let { if (it.hasNext) load(q, it.page + 1) } } }
        pageJump.setOnClickListener { showPageJump() }
        refresh()
        val restored = state.submitted?.let { query -> state.result?.let { state.pages.get(state.provider, query, it.page) } }
        restored?.let { restorePosition(it.position) }
    }

    private fun showCopyMenu(entry: ArchiveThread) {
        ThemedMenu.choose(host, "コピー", arrayOf("スレタイとURLをコピー", "スレタイをコピー", "URLをコピー"), -1) { index ->
            val value = when (index) {
                0 -> entry.title + "\n" + entry.url
                1 -> entry.title
                2 -> entry.url
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

    private fun displayPage(query: ArchiveQuery, cached: ArchivePageCache.Cached) {
        state.result = cached.result
        cached.result.form?.let { state.form = it }
        state.submitted = query
        state.message = ""
        refresh()
        restorePosition(cached.position)
    }

    private fun showBoards() {
        if (loading || disposed || state.provider != ArchiveProvider.FIVECH_KYODEMO) return
        boardGroups?.takeIf { it.isNotEmpty() }?.let { showBoardCategories(it); return }
        loading = true
        val request = ++generation
        refresh()
        message.text = "板一覧を取得中…"
        worker.execute {
            val groups = runCatching {
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

    private fun showBoardCategories(groups: List<BoardMenu.Category>) {
        showBoardList("カテゴリを選択", groups.map { it.name + "（" + it.boards.size + "）" }, null) { index ->
            val category = groups[index]
            showBoardList(category.name, category.boards.map { it.toString() }, { showBoardCategories(groups) }) { position ->
                if (disposed || state.provider != ArchiveProvider.FIVECH_KYODEMO) return@showBoardList
                val board = category.boards[position]
                saveKeyword()
                if (state.draft.boardCode != board.code) {
                    state.draft = state.draft.copy(boardCode = board.code, boardName = board.name)
                    state.pages.clear()
                    state.result = null
                    state.submitted = null
                    state.message = ""
                }
                refresh()
            }
        }
    }

    private fun showBoardList(title: String, labels: List<String>, back: (() -> Unit)?, pick: (Int) -> Unit) {
        val panel = ThemedMenu.formPanel(host)
        panel.addView(ThemedMenu.formTitle(host, title))
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
            showExternalOptions()
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

    private fun showExternalOptions() {
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
                val pick = ThemedMenu.formButton(host, (if (start) draft.from else draft.to).ifEmpty { "指定なし" })
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
            if (state.provider == ArchiveProvider.EDGE_KYODEMO || state.provider == ArchiveProvider.FIVECH_KYODEMO) {
                fun choice(label: String, values: List<String>, labels: List<String>, selected: String, apply: (String) -> Unit) {
                    panel.addView(ThemedMenu.formLabel(host, label))
                    var index = values.indexOf(selected).coerceAtLeast(0)
                    val button = ThemedMenu.formButton(host, labels[index] + " ▾")
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
        range.addView(ThemedMenu.formLabel(host, "～"), LinearLayout.LayoutParams(dp(28), -2))
        range.addView(max, LinearLayout.LayoutParams(0, dp(44), 1f))
        fields.addView(range)
        fields.addView(ThemedMenu.formLabel(host, "上限は取得ページごとに絞り込みます"))
        val form = state.form
        val choices = form?.sorts.orEmpty()
        var sort = state.draft.sort
        fields.addView(ThemedMenu.formLabel(host, "ソート"))
        val sortButton = ThemedMenu.formButton(host,
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
            val pick = ThemedMenu.formButton(host, day.ifEmpty { "指定なし" })
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
        if (state.provider == ArchiveProvider.FIVECH_KYODEMO && query.boardCode.isEmpty()) {
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
        if (state.provider.nativeSearch) {
            load(query, 1, fresh = true)
        } else {
            BrowserLauncher.open(host, ArchiveExternalSearch.url(state.provider, query))
        }
    }

    private fun load(query: ArchiveQuery, page: Int, fresh: Boolean = false) {
        if (loading || disposed || !state.provider.nativeSearch) return
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
        refresh()
        worker.execute {
            val outcome = runCatching { ArchiveRepository.search(requested, query, page) }
            host.runOnUiThread {
                if (disposed || host.isFinishing || host.isDestroyed || request != generation) return@runOnUiThread
                loading = false
                outcome.onSuccess {
                    if (fresh) state.pages.clear()
                    state.pages.put(requested, query, it)
                    displayPage(query, ArchivePageCache.Cached(it))
                }.onFailure {
                    state.message = "取得できませんでした: " + (it.message ?: "通信エラー")
                    refresh()
                }
            }
        }
    }

    private fun refresh() {
        val native = state.provider.nativeSearch
        provider.text = state.provider.label + " ▾"
        provider.isEnabled = !loading
        boardButton.visibility = if (state.provider == ArchiveProvider.FIVECH_KYODEMO) View.VISIBLE else View.GONE
        boardButton.text = (state.draft.boardName.ifBlank { state.draft.boardCode.ifBlank { "板を選択" } }) + " ▾"
        boardButton.isEnabled = !loading
        searchHistoryButton.isEnabled = !loading
        keyword.isEnabled = !loading
        search.isEnabled = !loading
        search.text = if (native) "検索" else "ブラウザ検索"
        options.isEnabled = !loading
        search.alpha = if (search.isEnabled) 1f else 0.45f
        options.alpha = if (options.isEnabled) 1f else 0.45f
        val page = state.result
        message.text = when {
            loading -> "検索中…"
            !native -> "検索条件を入力するとブラウザで結果を開きます（アプリ内結果表示は未対応）"
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
        previous.isEnabled = !loading && native && page != null && page.page > 1
        next.isEnabled = !loading && native && page?.hasNext == true && page.page < page.totalPages
        pageJump.isEnabled = !loading && native && page != null && page.totalPages > 1
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
        worker.shutdownNow()
    }

    private inner class ThreadsAdapter : BaseAdapter() {
        override fun getCount() = state.result?.items?.size ?: 0
        override fun getItem(position: Int) = state.result!!.items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convert: View?, parent: ViewGroup): View {
            val row = (convert as? LinearLayout) ?: LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minimumHeight = dp(58)
                addView(TextView(host).apply { textSize = 15f; setTextColor(host.getColor(R.color.text_primary)) })
                addView(TextView(host).apply { textSize = 12f; setTextColor(host.getColor(R.color.text_secondary)) })
            }
            val entry = getItem(position)
            (row.getChildAt(0) as TextView).text = entry.title
            (row.getChildAt(1) as TextView).text = ArchiveExternalSearch.metadata(entry)
            return row
        }
    }
}
