package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.content.Context
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
    var submitted: ArchiveQuery? = null
    var result: ArchivePage? = null
    var message = ""
    var form: ArchiveFormOptions? = null
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
    private lateinit var search: TextView
    private lateinit var options: TextView
    private lateinit var message: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
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
            val labels = providers.map { it.label + if (it.nativeSearch) "" else "（外部ブラウザのみ）" }.toTypedArray()
            ThemedMenu.choose(host, "検索サービス", labels, providers.indexOf(state.provider)) { index ->
                val selected = providers.getOrNull(index) ?: return@choose
                if (selected != state.provider) {
                    saveKeyword()
                    state.provider = selected
                    state.submitted = null
                    state.result = null
                    state.message = ""
                    refresh()
                }
            }
        }
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
            val target = if (state.provider.nativeSearch && state.draft.keyword.isNotBlank()) {
                try { ArchiveRepository.url(state.draft, 1) } catch (_: Exception) { state.provider.url }
            } else state.provider.url
            if (state.provider.nativeSearch && state.draft.maximum.isNotEmpty()) {
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
            state.result?.items?.getOrNull(position)?.let { ThreadOpener.open(host, it.url, true) }
        }
        val pager = LinearLayout(host)
        previous = ThemedMenu.formAction(host, "前のページ")
        next = ThemedMenu.formAction(host, "次のページ")
        pager.addView(previous, LinearLayout.LayoutParams(0, dp(44), 1f))
        pager.addView(next, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(pager, LinearLayout.LayoutParams(-1, -2))
        previous.setOnClickListener { state.submitted?.let { q -> state.result?.let { if (it.page > 1) load(q, it.page - 1) } } }
        next.setOnClickListener { state.submitted?.let { q -> state.result?.let { if (it.hasNext) load(q, it.page + 1) } } }
        refresh()
    }

    private fun saveKeyword() {
        if (::keyword.isInitialized) state.draft = state.draft.copy(keyword = keyword.text.toString())
    }

    private fun showOptions() {
        if (loading || disposed || !state.provider.nativeSearch) return
        saveKeyword()
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

    private fun showOptionsDialog() {
        if (disposed || !state.provider.nativeSearch) return
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
        if (loading || !state.provider.nativeSearch) return
        saveKeyword()
        val query = state.draft
        try { query.validate() } catch (error: Exception) {
            message.text = error.message ?: "検索条件が不正です"
            return
        }
        (host.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(keyword.windowToken, 0)
        keyword.clearFocus()
        load(query, 1)
    }

    private fun load(query: ArchiveQuery, page: Int) {
        if (loading || disposed || !state.provider.nativeSearch) return
        loading = true
        val requested = state.provider
        val request = ++generation
        refresh()
        worker.execute {
            val outcome = runCatching { ArchiveRepository.search(requested, query, page) }
            host.runOnUiThread {
                if (disposed || host.isFinishing || host.isDestroyed || request != generation) return@runOnUiThread
                loading = false
                outcome.onSuccess {
                    state.result = it
                    it.form?.let { current -> state.form = current }
                    state.submitted = query
                    state.message = ""
                    adapter.notifyDataSetChanged()
                    list.setSelection(0)
                }.onFailure {
                    state.message = "取得できませんでした: " + (it.message ?: "通信エラー")
                }
                refresh()
            }
        }
    }

    private fun refresh() {
        val native = state.provider.nativeSearch
        provider.text = state.provider.label + " ▾"
        provider.isEnabled = !loading
        keyword.isEnabled = native && !loading
        search.isEnabled = native && !loading
        options.isEnabled = native && !loading
        search.alpha = if (search.isEnabled) 1f else 0.45f
        options.alpha = if (options.isEnabled) 1f else 0.45f
        val page = state.result
        message.text = when {
            loading -> "検索中…"
            !native -> "このサービスのアプリ内検索は未対応です。外部ブラウザをご利用ください。"
            state.message.isNotEmpty() -> state.message
            page != null -> {
                val submitted = state.submitted
                if (!submitted?.maximum.isNullOrEmpty()) {
                    "サイト検索：${page.total}件　${page.page}ページ / 範囲内：${page.items.size}件"
                } else {
                    "検索結果：${page.total}件　${page.page}ページ" + if (page.total == 0L) "（該当なし）" else ""
                }
            }
            else -> "スレタイを入力して検索してください"
        }
        previous.isEnabled = !loading && native && page != null && page.page > 1
        next.isEnabled = !loading && native && page?.hasNext == true
        previous.alpha = if (previous.isEnabled) 1f else 0.45f
        next.alpha = if (next.isEnabled) 1f else 0.45f
        adapter.notifyDataSetChanged()
    }

    fun dispose() {
        saveKeyword()
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
            (row.getChildAt(1) as TextView).text = "レス: ${entry.replies}" + if (entry.date.isEmpty()) "" else "　" + entry.date
            return row
        }
    }
}
