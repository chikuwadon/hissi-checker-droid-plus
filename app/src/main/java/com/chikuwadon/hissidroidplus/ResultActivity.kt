package com.chikuwadon.hissidroidplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONArray

/** Native 5ch/Edge result reader, with validated WebView fallback for Edge. */
class ResultActivity : Activity() {
    companion object {
        const val EXTRA_VERIFY_EDGE_SEARCH_DATE = "verify_edge_search_date"

        /** Preserve an active text-selection controller when a row is recycled. */
        private fun bindResultText(target: TextView, content: CharSequence, reused: Boolean) {
            if (reused || !TextUtils.equals(target.text, content)) {
                target.text = content
                (target as SelectableResultTextView).refreshSelectionAfterRebind()
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var query: CheckerQuery? = null
    private var inboundEdgeSearch = false
    private var result = CheckerResult()
    private var threadRows: List<Map.Entry<String, String>> = emptyList()
    private var threadCounts: Map<String, Int> = emptyMap()
    private var nameCounts: Map<String, Int> = emptyMap()
    private var tab = 0
    private val positions = IntArray(3)
    private val offsets = IntArray(3)
    private lateinit var status: TextView
    private lateinit var list: ListView
    private val adapter = RowAdapter()
    private var loading = true
    private var paging = false
    private var noMorePages = false
    private var nextPage = 1
    private var fallbackAttempted = false
    private var edgeDiagnostic = "未取得"
    private var fallbackView: WebView? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    private fun verifyEdgeSearchDate(): Boolean =
        query?.site == CheckerQuery.Site.EDGE &&
            (inboundEdgeSearch ||
                intent.getBooleanExtra(EXTRA_VERIFY_EDGE_SEARCH_DATE, false))

    private fun showWrongDay() {
        loading = false
        showMessage("指定日の投稿なし",
            "このIDについて、選択した日付に一致する投稿は確認できませんでした。別の日付の投稿は表示しません。",
            "戻る", { exitResults() }, null, null)
    }

    // ListView owns accessibility clicks; a separate listener handles swipes.
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (redirectNonCheckerWebLink()) return
        if (routeStandaloneLinkToOwnTask()) return
        setContentView(R.layout.activity_results)
        AppTitle.apply(findViewById(R.id.toolbar_title))
        AppSettings.configureBars(this)
        findViewById<View>(R.id.stat_summary).setOnClickListener {
            val chart = findViewById<View>(R.id.hourly_chart)
            val expanding = chart.visibility != View.VISIBLE
            chart.visibility = if (expanding) View.VISIBLE else View.GONE
            AppSettings.prefs(this).edit().putBoolean("show_hourly_chart", expanding).apply()
            updateStats()
        }
        HttpTransport.setUserAgent(WebSettings.getDefaultUserAgent(this))
        HttpTransport.enableWebViewCookies()
        applySystemInsets()
        findViewById<View>(R.id.edge_analysis_link).setOnClickListener { openEdgeAnalysis() }
        status = findViewById(R.id.status)
        list = findViewById(R.id.items)
        list.adapter = adapter
        findViewById<View>(R.id.tab_posts).setOnClickListener { selectTab(0) }
        findViewById<View>(R.id.tab_threads).setOnClickListener { selectTab(1) }
        findViewById<View>(R.id.tab_names).setOnClickListener { selectTab(2) }
        findViewById<View>(R.id.toolbar_back).setOnClickListener { exitResults() }
        findViewById<View>(R.id.toolbar_more).setOnClickListener { anchor ->
            val edge = query?.site == CheckerQuery.Site.EDGE
            val labels = if (edge) arrayOf(
                "ホーム画面に戻る", "検索履歴", "書き込み分析",
                "検索結果を外部ブラウザで開く", "URLをコピー", "IDをコピー",
                "書き込みスレをコピー", "表示中のレスをすべてコピー", "閉じる"
            ) else arrayOf(
                "ホーム画面に戻る", "検索履歴",
                "検索結果を外部ブラウザで開く", "URLをコピー", "IDをコピー",
                "書き込みスレをコピー", "表示中のレスをすべてコピー", "閉じる"
            )
            ThemedMenu.showOverflow(this, anchor, labels) { which ->
                if (which < 0 || which >= labels.size) return@showOverflow
                when {
                    which == 0 -> openHome()
                    which == 1 -> startActivity(Intent(this, HistoryActivity::class.java))
                    edge && which == 2 -> openEdgeAnalysis()
                    else -> when (which - if (edge) 3 else 2) {
                        0 -> openExternalBrowser(resultUrl())
                        1 -> copyResultUrl()
                        2 -> copySearchedId()
                        3 -> copyWrittenThreads()
                        4 -> copyAllVisible()
                        else -> exitResults()
                    }
                }
            }
        }
        selectTab(0)
        list.setOnItemClickListener { _, _, index, _ -> showEntry(index) }
        list.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (abs(dx) > dp(65) && abs(dx) > abs(dy) * 1.35f) {
                            val next = tab + if (dx < 0) 1 else -1
                            if (next in 0..2) selectTab(next)
                            return true
                        }
                    }
                }
                return false
            }
        })
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, state: Int) = Unit

            override fun onScroll(
                view: AbsListView, firstVisibleItem: Int,
                visibleItemCount: Int, totalItemCount: Int
            ) {
                if (tab == 0 && totalItemCount > 0 &&
                    firstVisibleItem + visibleItemCount >= totalItemCount - 2
                ) loadNextPage()
            }
        })
        query = try {
            extract(intent)
        } catch (bad: Exception) {
            showError(bad.message)
            return
        }
        val initial = query ?: return
        inboundEdgeSearch = initial.site == CheckerQuery.Site.EDGE && initial.url.isEmpty()
        status.text = if (initial.site == CheckerQuery.Site.EDGE)
            "エッヂ / 取得中…" else "5ch / 取得中…"
        worker.execute {
            var resolved = initial
            try {
                if (inboundEdgeSearch) {
                    val found = EdgeInboundSearch.resolve(initial.board, initial.date, initial.id)
                    if (found == null) {
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            loading = false
                            showMessage("該当する投稿が見つかりませんでした",
                                "エッヂで指定した板・日付・IDの投稿を確認できませんでした。",
                                "閉じる", { exitResults() }, null, null)
                        }
                        return@execute
                    }
                    resolved = found
                }
                val target = resolved
                val parsed = if (target.site == CheckerQuery.Site.EDGE)
                    EdgeAdapter().load(target) else HissiAdapter().load(target)
                val wrongDay = verifyEdgeSearchDate() && parsed.posts.isNotEmpty()
                val received = if (wrongDay)
                    EdgeSearchValidation.matchingPosts(parsed, target.date, target.id) else parsed
                val rejected = wrongDay && received.posts.isEmpty()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    query = target
                    if (rejected) {
                        showWrongDay()
                        return@runOnUiThread
                    }
                    if (target.site == CheckerQuery.Site.EDGE && received.posts.isEmpty()) {
                        edgeDiagnostic = received.diagnostic
                        loadEdgeWithWebView(
                            "通常通信では抽出できません: " + received.diagnostic
                        )
                        return@runOnUiThread
                    }
                    result = received
                    rebuildIndexes()
                    loading = false
                    adapter.notifyDataSetChanged()
                    updateHeader()
                    if (result.posts.isNotEmpty()) SearchHistory.record(this, target, result)
                    if (result.posts.isEmpty()) showEmpty()
                }
            } catch (error: Exception) {
                val lastResolved = resolved
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (inboundEdgeSearch && lastResolved.url.isEmpty()) {
                        loading = false
                        showMessage("エッヂのID検索に失敗しました",
                            error.message ?: "検索結果を取得できませんでした",
                            "閉じる", { exitResults() }, null, null)
                    } else if (lastResolved.site == CheckerQuery.Site.EDGE) {
                        query = lastResolved
                        loadEdgeWithWebView(
                            (error.message ?: "通信失敗") +
                                " / " + HttpTransport.lastDiagnostic()
                        )
                    } else {
                        showError(error.message)
                    }
                }
            }
        }
    }

    private fun loadNextPage() {
        val current = query ?: return
        if (current.site != CheckerQuery.Site.HISSI || loading || paging || noMorePages ||
            result.sourceUrl.isEmpty() || result.posts.isEmpty() || nextPage > 20 ||
            result.reportedPostCount < 0 ||
            result.posts.size >= result.reportedPostCount
        ) return
        paging = true
        val page = nextPage
        worker.execute {
            try {
                val addition = HissiAdapter().nextPage(result.sourceUrl, page)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (addition.posts.isEmpty()) {
                        noMorePages = true
                    } else {
                        val before = result.posts.size
                        val seen = HashSet<String>()
                        result.posts.forEach { seen.add(it.link + it.meta) }
                        for (post in addition.posts) {
                            if (seen.add(post.link + post.meta)) result.posts.add(post)
                        }
                        if (result.posts.size == before) noMorePages = true
                    }
                    nextPage = page + 1
                    paging = false
                    rebuildIndexes()
                    adapter.notifyDataSetChanged()
                    updateHeader()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    paging = false
                    noMorePages = true
                    if (!isFinishing && !isDestroyed) {
                        toast("追加レスを取得できませんでした", Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    /**
     * On old Android versions App Links cannot be restricted by query.
     * Redirect non-ID kyodemo pages to a browser without looping.
     */
    private fun redirectNonCheckerWebLink(): Boolean {
        val incoming = intent ?: return false
        if (incoming.action != Intent.ACTION_VIEW || incoming.data == null) return false
        val url = incoming.dataString ?: return false
        return try {
            val host = URI.create(url).host
            if (host == null ||
                (!host.equals("kyodemo.net", true) &&
                    !host.equals("www.kyodemo.net", true))
            ) return false
            CheckerQuery.parseUrl(url)
            false
        } catch (_: IllegalArgumentException) {
            BrowserLauncher.open(this, url)
            finish()
            true
        }
    }

    /** Connected mode stays in the caller task; standalone mode starts our task once. */
    private fun routeStandaloneLinkToOwnTask(): Boolean {
        val incoming = intent ?: return false
        val action = incoming.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return false
        if (!AppSettings.standalone(this) ||
            incoming.getBooleanExtra("internal_standalone_routed", false)
        ) return false
        val standalone = Intent(this, ResultActivity::class.java).apply {
            this.action = action
            if (incoming.type != null) setDataAndType(incoming.data, incoming.type)
            else data = incoming.data
            incoming.extras?.let { putExtras(it) }
            putExtra("internal_standalone_routed", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(standalone)
        finish()
        return true
    }

    private fun launchedExternally(): Boolean =
        intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW

    /** Open our own home task even when this result was launched from ChMate. */
    private fun openHome() {
        val home = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(home)
        finish()
    }

    private fun exitResults() {
        if (launchedExternally() && isTaskRoot) finishAndRemoveTask() else finish()
    }

    private fun extract(incoming: Intent): CheckerQuery {
        val shared = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)
        return CheckerQuery.fromExternalPayload(incoming.dataString, shared?.toString(),
            incoming.getStringExtra("board"), incoming.getStringExtra("date"),
            incoming.getStringExtra("id"))
    }

    private fun updateHeader() {
        val request = query ?: return
        val source = if (request.site == CheckerQuery.Site.EDGE) "エッヂ" else "5ch"
        val reported = if (result.reportedPostCount < 0) ""
            else getString(R.string.result_reported_count, result.reportedPostCount)
        status.text = getString(R.string.result_summary_status, source,
            request.id.ifEmpty { request.board }, result.posts.size, reported)
        findViewById<TextView>(R.id.tab_posts).text =
            getString(R.string.result_post_tab, result.posts.size)
        findViewById<TextView>(R.id.tab_threads).text =
            getString(R.string.result_thread_tab, threadRows.size)
        findViewById<TextView>(R.id.tab_names).text =
            getString(R.string.result_name_tab, result.names.size)
        updateStats()
    }

    private fun openEdgeAnalysis() {
        val request = query ?: return
        if (request.site != CheckerQuery.Site.EDGE) return
        val url = result.sourceUrl.ifEmpty { request.url }
        if (url.isEmpty()) return
        startActivity(Intent(this, EdgeAnalysisActivity::class.java)
            .putExtra(EdgeAnalysisActivity.EXTRA_URL, url))
    }

    private fun updateStats() {
        val block = findViewById<View>(R.id.stat_block)
        val summary = findViewById<TextView>(R.id.stat_summary)
        val chart = findViewById<HourlyChartView>(R.id.hourly_chart)
        if (result.posts.isEmpty()) {
            block.visibility = View.GONE
            return
        }
        block.visibility = View.VISIBLE
        findViewById<View>(R.id.edge_analysis_link).visibility =
            if (query?.site == CheckerQuery.Site.EDGE) View.VISIBLE else View.GONE
        var dated = chart.setPosts(result.posts)
        result.hourlyCounts?.let {
            chart.setCounts(it)
            dated = 1
        }
        val label = StringBuilder()
        if (result.rank.isNotEmpty()) label.append(result.rank).append("  ·  ")
        label.append("レス ").append(if (result.reportedPostCount >= 0)
            result.reportedPostCount else result.posts.size)
        label.append("  /  スレ ").append(if (result.reportedThreadCount >= 0)
            result.reportedThreadCount else threadRows.size)
        if (result.referencedCount >= 0) label.append("  /  被参照 ")
            .append(result.referencedCount)
        if (dated > 0) {
            val show = AppSettings.prefs(this).getBoolean("show_hourly_chart", true)
            chart.visibility = if (show) View.VISIBLE else View.GONE
            label.append(if (show) "  ▴" else "  ▾")
        } else {
            chart.visibility = View.GONE
        }
        summary.text = label.toString()
    }

    private fun selectTab(index: Int) {
        positions[tab] = list.firstVisiblePosition
        offsets[tab] = list.getChildAt(0)?.top ?: 0
        tab = index
        val tabs = intArrayOf(R.id.tab_posts, R.id.tab_threads, R.id.tab_names)
        tabs.forEachIndexed { position, res ->
            val button = findViewById<TextView>(res)
            // Underline selection only, retaining the flat tab background.
            button.isSelected = position == tab
            button.setTextColor(getColor(
                if (position == tab) R.color.accent else R.color.text_secondary
            ))
            button.contentDescription = button.text.toString() +
                if (position == tab) "、選択中" else ""
        }
        adapter.notifyDataSetChanged()
        list.setSelectionFromTop(positions[tab], offsets[tab])
    }

    private fun showEntry(index: Int) {
        if (loading || query == null) return
        when (tab) {
            0 -> {
                if (index >= result.posts.size) return
                val post = result.posts[index]
                showMenu(post.title, arrayOf(
                    "スレを開く", "スレタイと全体をコピー", "全体をコピー",
                    "本文をコピー", "スレタイと全体を追加コピー", "全体を追加コピー",
                    "IDをコピー", "リンクをコピー"
                )) { which ->
                    when (which) {
                        0 -> openThread(post.link)
                        1 -> copy(PostFormatter.withThread(post), false)
                        2 -> copy(PostFormatter.full(post), false)
                        3 -> copy(post.body.trim(), false)
                        4 -> copy(PostFormatter.withThread(post), true)
                        5 -> copy(PostFormatter.full(post), true)
                        6 -> {
                            val id = PostFormatter.id(post)
                            if (id.isEmpty()) toast("IDが見つかりません")
                            else copy(id, false)
                        }
                        7 -> copy(post.link, false)
                    }
                }
            }
            1 -> {
                if (index >= threadRows.size) return
                val entry = threadRows[index]
                showMenu(entry.value, arrayOf(
                    "スレを開く", "スレタイをコピー", "リンクをコピー",
                    "スレタイとリンクをコピー", "スレタイとリンクを追加コピー"
                )) { which ->
                    when (which) {
                        0 -> openThread(entry.key)
                        1 -> copy(entry.value, false)
                        2 -> copy(entry.key, false)
                        3 -> copy(entry.value + "\n" + entry.key, false)
                        4 -> copy(entry.value + "\n" + entry.key, true)
                    }
                }
            }
            else -> {
                if (index >= result.names.size) return
                val name = result.names[index]
                showMenu(name, arrayOf("名前をコピー", "名前を追加コピー")) {
                    copy(name, it == 1)
                }
            }
        }
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(getColor(R.color.panel))
        cornerRadius = dp(17).toFloat()
        setStroke(dp(1), getColor(R.color.divider))
    }

    private fun dialogText(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            fontFeatureSettings = "kern"
        }

    private fun dialogPanel(title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(12))
            background = panelBackground()
            addView(dialogText(title, 17, getColor(R.color.text_primary)).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, dp(11))
            })
        }

    private fun buttonText(value: String): TextView =
        dialogText(value, 14, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
            minWidth = dp(76)
            minHeight = dp(46)
            background = RippleDrawable(
                ColorStateList.valueOf(0x3345BDB6),
                ColorDrawable(Color.TRANSPARENT), null
            )
        }

    private fun finishPanel(
        panel: LinearLayout, dialog: AlertDialog, right: String,
        rightAction: (() -> Unit)?
    ) {
        val buttons = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val cancel = buttonText(right)
        cancel.setOnClickListener {
            dialog.dismiss()
            rightAction?.invoke()
        }
        buttons.addView(cancel)
        panel.addView(buttons)
        dialog.setView(panel)
        showCentered(dialog)
    }

    private fun showMenu(title: String?, items: Array<String>, callback: (Int) -> Unit) {
        val panel = dialogPanel(title?.takeIf { it.isNotEmpty() } ?: "操作")
        val dialog = AlertDialog.Builder(this).create()
        items.forEachIndexed { index, item ->
            val row = dialogText(item, 15, getColor(R.color.text_primary)).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(49)
                setPadding(dp(9), 0, dp(9), 0)
                background = RippleDrawable(
                    ColorStateList.valueOf(0x3345BDB6),
                    ColorDrawable(Color.TRANSPARENT), null
                )
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2))
            row.setOnClickListener {
                dialog.dismiss()
                callback(index)
            }
        }
        finishPanel(panel, dialog, "キャンセル", null)
    }

    private fun showMessage(
        title: String, body: String, left: String?, onLeft: (() -> Unit)?,
        right: String?, onRight: (() -> Unit)?
    ) {
        val panel = dialogPanel(title)
        val message = dialogText(body, 14, getColor(R.color.text_secondary)).apply {
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(15))
        }
        panel.addView(message)
        val dialog = AlertDialog.Builder(this).create()
        val buttons = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        if (left != null) {
            val leftButton = buttonText(left)
            buttons.addView(leftButton)
            leftButton.setOnClickListener {
                dialog.dismiss()
                onLeft?.invoke()
            }
        }
        if (right != null) {
            val rightButton = buttonText(right).apply {
                setTextColor(getColor(R.color.text_primary))
            }
            buttons.addView(rightButton)
            rightButton.setOnClickListener {
                dialog.dismiss()
                onRight?.invoke()
            }
        }
        panel.addView(buttons)
        dialog.setView(panel)
        showCentered(dialog)
    }

    private fun showCentered(dialog: AlertDialog) {
        if (isFinishing || isDestroyed) return
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes = attributes.apply {
                gravity = Gravity.CENTER
                y = 0
            }
            setLayout(min(resources.displayMetrics.widthPixels - dp(28), dp(480)),
                WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun rebuildIndexes() {
        PostIndex.collectNames(result)
        threadRows = ArrayList(PostIndex.uniqueThreads(result).entries)
        threadCounts = PostIndex.threadPostCounts(result)
        nameCounts = PostIndex.namePostCounts(result)
    }

    private fun applySystemInsets() {
        val root = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val startTop = root.paddingTop
            val startBottom = root.paddingBottom
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
                )
                view.setPadding(view.paddingLeft, startTop + bars.top,
                    view.paddingRight, startBottom + bars.bottom)
                WindowInsets.CONSUMED
            }
            root.requestApplyInsets()
        }
    }

    private fun showEmpty() {
        val request = query
        val page = result.sourceUrl.ifEmpty { request?.url ?: "" }
        var message = "ページが存在しないか、HTML形式が想定と違う可能性があります。"
        if (request?.site == CheckerQuery.Site.HISSI && result.reportedPostCount > 0) {
            message = "5chのサイトでは" + result.reportedPostCount +
                "件の投稿が確認できますが、レス本文を解析できませんでした。\n診断: " +
                result.diagnostic
        }
        showMessage("レスを確認できませんでした", message,
            page.takeIf { it.isNotEmpty() }, { openExternalBrowser(page) },
            "閉じる", { exitResults() })
    }

    private fun copy(value: String?, append: Boolean) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        var text = value ?: ""
        val clip = clipboard.primaryClip
        if (append && clipboard.hasPrimaryClip() && clip != null && clip.itemCount > 0) {
            val old = clip.getItemAt(0).coerceToText(this)?.toString() ?: ""
            text = PostFormatter.append(old, value)
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("必死チェッカーdroid +", text))
        // Android 13+ provides its own clipboard confirmation overlay.
        if (Build.VERSION.SDK_INT < 33) toast("コピーしました")
    }

    /** WebView fallback for anti-bot/JS pages, never mislabeling access denial as no posts. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun loadEdgeWithWebView(firstError: String?) {
        if (fallbackAttempted) {
            showError(firstError ?: "エッヂのページ構造を解析できませんでした")
            return
        }
        val request = query ?: return
        fallbackAttempted = true
        edgeDiagnostic = firstError ?: "通信成功／ネイティブ解析0件"
        status.text = "エッヂ / ブラウザ方式で再取得中…"
        val web = WebView(this).apply {
            alpha = 0.01f
            isClickable = false
        }
        fallbackView = web
        findViewById<FrameLayout>(android.R.id.content).addView(
            web, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        val completed = booleanArrayOf(false)
        val parsing = booleanArrayOf(false)
        val detail = arrayOf(firstError ?: "解析できるレスがありませんでした")
        CookieManager.getInstance().setAcceptCookie(true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, incoming: WebResourceRequest
            ): Boolean {
                val uri = incoming.url
                val host = uri.host
                if (!uri.scheme.equals("https", true) || host == null ||
                    (host != "www.kyodemo.net" && host != "kyodemo.net")
                ) {
                    detail[0] = "外部サイトへリダイレクトされました"
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView, incoming: WebResourceRequest, error: WebResourceError
            ) {
                if (incoming.isForMainFrame) {
                    detail[0] = "WebView通信エラー: " + error.errorCode
                    edgeDiagnostic = detail[0]
                }
            }

            override fun onReceivedHttpError(
                view: WebView, incoming: WebResourceRequest, response: WebResourceResponse
            ) {
                if (incoming.isForMainFrame) {
                    detail[0] = "エッヂ側のHTTP応答: " + response.statusCode
                    edgeDiagnostic = detail[0]
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Some pages attach posts after DOMContentLoaded; poll DOM.
                for (delay in intArrayOf(900, 2200, 4200, 7100)) {
                    view.postDelayed({
                        if (completed[0] || isFinishing || isDestroyed) return@postDelayed
                        view.evaluateJavascript(
                            "(function(){return document.documentElement.outerHTML})()"
                        ) { json ->
                            if (completed[0] || parsing[0] || isFinishing || isDestroyed) {
                                return@evaluateJavascript
                            }
                            if (json == null || json.length > 12 * 1024 * 1024) {
                                detail[0] = "WebViewから取得したHTMLが大きすぎます"
                                return@evaluateJavascript
                            }
                            parsing[0] = true
                            val verify = verifyEdgeSearchDate()
                            try {
                                worker.execute {
                                    try {
                                        val html = JSONArray("[" + json + "]").getString(0)
                                        val original = EdgeAdapter.parse(html, request)
                                        val found = if (verify)
                                            EdgeSearchValidation.matchingPosts(original,
                                                request.date, request.id) else original
                                        val wrongDay = verify && original.posts.isNotEmpty() &&
                                            found.posts.isEmpty()
                                        val cause = if (found.posts.isEmpty())
                                            EdgeAdapter.explainEmptyPage(html) else ""
                                        val length = html.length
                                        runOnUiThread {
                                            parsing[0] = false
                                            if (completed[0] || isFinishing || isDestroyed ||
                                                fallbackView !== view
                                            ) return@runOnUiThread
                                            if (wrongDay) {
                                                completed[0] = true
                                                cleanupWebView(view)
                                                showWrongDay()
                                                return@runOnUiThread
                                            }
                                            if (found.posts.isEmpty()) {
                                                if (!detail[0].matches(Regex(".*HTTP.*\\d{3}.*")) ||
                                                    !cause.contains("HTML構造未対応")
                                                ) detail[0] = cause
                                                edgeDiagnostic = detail[0] +
                                                    " / HTML=" + length + "文字"
                                            } else {
                                                completed[0] = true
                                                found.sourceUrl = request.url
                                                result = found
                                                rebuildIndexes()
                                                loading = false
                                                adapter.notifyDataSetChanged()
                                                updateHeader()
                                                SearchHistory.record(this@ResultActivity,
                                                    request, result)
                                                cleanupWebView(view)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        runOnUiThread {
                                            parsing[0] = false
                                            if (!completed[0]) {
                                                detail[0] = "WebViewのHTML解析に失敗しました"
                                            }
                                        }
                                    }
                                }
                            } catch (_: RejectedExecutionException) {
                                parsing[0] = false
                            }
                        }
                    }, delay.toLong())
                }
            }
        }
        web.loadUrl(request.url)
        web.postDelayed({
            if (!completed[0] && !isFinishing && !isDestroyed) {
                completed[0] = true
                showError(detail[0])
                cleanupWebView(web)
            }
        }, 11000L)
    }

    private fun cleanupWebView(view: WebView?) {
        if (view == null) return
        view.stopLoading()
        view.webViewClient = WebViewClient()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        if (fallbackView === view) fallbackView = null
    }

    private fun showError(message: String?) {
        loading = false
        status.text = "取得に失敗しました"
        var text = message ?: "通信・解析エラーが発生しました"
        val missing = HissiSearchFeedback.missingSearchResult(query, text)
        if (missing) {
            text = "指定した板・日付・IDに該当するページが見つかりませんでした。" +
                "投稿がない場合や、サイト側で結果が公開されていない場合などに表示されます。"
            status.text = "該当する投稿が見つかりませんでした"
        }
        if (query?.site == CheckerQuery.Site.EDGE) edgeDiagnostic = text
        val url = query?.url ?: ""
        showMessage(if (missing) "該当する投稿が見つかりませんでした"
            else "レスを取得できませんでした", text,
            url.takeIf { it.isNotEmpty() }, { openExternalBrowser(url) },
            "閉じる", { exitResults() })
    }

    private fun openThread(url: String) {
        ThreadOpener.open(this, url, query?.site == CheckerQuery.Site.EDGE)
    }

    /** Browser-only actions must never bounce back through our own deep links. */
    private fun openExternalBrowser(url: String?) {
        if (url.isNullOrEmpty()) {
            toast("リンクがありません")
            return
        }
        BrowserLauncher.open(this, url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "表示中のレスをすべてコピー")
        menu.add(0, 2, 1, "検索結果URLを開く")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            1 -> {
                copyAllVisible()
                true
            }
            2 -> {
                openExternalBrowser(result.sourceUrl.ifEmpty { query?.url ?: "" })
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun resultUrl(): String {
        if (result.sourceUrl.isNotEmpty()) return result.sourceUrl
        val current = query ?: return ""
        if (current.url.isNotEmpty()) return current.url
        if (current.site == CheckerQuery.Site.HISSI &&
            current.date.isNotEmpty() && current.id.isNotEmpty()
        ) return HissiAdapter.resultUrl(current.board, current.date, current.id)
        return ""
    }

    private fun copyResultUrl() {
        val url = resultUrl()
        if (url.isEmpty()) {
            toast("URLがありません")
            return
        }
        copy(url, false)
    }

    private fun copyWrittenThreads() {
        val value = ThreadSummaryFormatter.format(query, result)
        if (value.isEmpty()) {
            toast("書き込みスレがありません")
            return
        }
        copy(value, false)
    }

    private fun copySearchedId() {
        var id = query?.id ?: ""
        if (id.isNotEmpty()) id = "ID:" +
            id.replaceFirst(Regex("(?i)^ID\\s*[:：]\\s*"), "")
        if (id.isEmpty() && result.posts.isNotEmpty()) {
            id = PostFormatter.id(result.posts[0])
        }
        if (id.isEmpty()) {
            toast("IDが見つかりません")
            return
        }
        copy(id, false)
    }

    private fun copyAllVisible() {
        val builder = StringBuilder()
        val titlesAdded = HashSet<String>()
        for (post in result.posts) {
            if (builder.isNotEmpty()) builder.append("\n\n")
            val key = PostIndex.threadKey(post.link)
            val first = titlesAdded.add(key)
            builder.append(if (first) PostFormatter.withThread(post) else PostFormatter.full(post))
        }
        copy(builder.toString(), false)
    }

    override fun onDestroy() {
        worker.shutdownNow()
        cleanupWebView(fallbackView)
        super.onDestroy()
    }

    /** Muted reference badge is UI-only, excluded from copied data and name aggregation. */
    private fun displayHeader(post: CheckerResult.Post): CharSequence {
        val header = PostFormatter.header(post)
        if (post.referenceCount <= 0) return header
        val number = Pattern.compile("^\\d+").matcher(header)
        if (!number.find()) return header
        val at = number.end()
        val mark = "(" + post.referenceCount + ")"
        val span = SpannableString(header.substring(0, at) + mark + header.substring(at))
        val accent = getColor(R.color.accent)
        val secondary = getColor(R.color.text_secondary)
        val mixed = Color.rgb(
            (Color.red(accent) * 55 + Color.red(secondary) * 45) / 100,
            (Color.green(accent) * 55 + Color.green(secondary) * 45) / 100,
            (Color.blue(accent) * 55 + Color.blue(secondary) * 45) / 100
        )
        span.setSpan(ForegroundColorSpan(mixed), at, at + mark.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(RelativeSizeSpan(0.88f), at, at + mark.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    /** Count suffix uses ordinary text color; it is never part of copied data. */
    private fun labelWithCount(label: String, count: Int): CharSequence {
        if (count <= 0) return label
        val span = SpannableString(label + " (" + count + ")")
        span.setSpan(ForegroundColorSpan(getColor(R.color.text_primary)),
            label.length, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = when (tab) {
            0 -> result.posts.size
            1 -> threadRows.size
            else -> result.names.size
        }

        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(index: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@ResultActivity)
                .inflate(R.layout.item_row, parent, false)
            val title = view.findViewById<SelectableResultTextView>(R.id.item_title)
            val subtitle = view.findViewById<SelectableResultTextView>(R.id.item_subtitle)
            val body = view.findViewById<SelectableResultTextView>(R.id.item_body)
            // Recycled rows can retain stale text-selection controllers.
            val identity = tab * 1_000_000 + index
            val reused = (view.tag as? Int) != identity
            view.tag = identity
            // Selectable text receives the original long-press; short taps
            // continue to open the existing action menu.
            val showActions = View.OnClickListener { showEntry(index) }
            title.setOnClickListener(showActions)
            subtitle.setOnClickListener(showActions)
            body.setOnClickListener(showActions)
            when (tab) {
                0 -> {
                    val post = result.posts[index]
                    val sameThread = index > 0 &&
                        PostIndex.threadKey(post.link) ==
                        PostIndex.threadKey(result.posts[index - 1].link)
                    title.visibility = if (sameThread) View.GONE else View.VISIBLE
                    bindResultText(title, post.title, reused)
                    subtitle.visibility = View.VISIBLE
                    bindResultText(subtitle, displayHeader(post), reused)
                    bindResultText(body, post.body, reused)
                    body.visibility = View.VISIBLE
                }
                1 -> {
                    title.visibility = View.VISIBLE
                    val entry = threadRows[index]
                    val count = threadCounts.getOrDefault(PostIndex.threadKey(entry.key), 0)
                    bindResultText(title, labelWithCount(entry.value, count), reused)
                    subtitle.visibility = View.GONE
                    body.visibility = View.GONE
                }
                else -> {
                    title.visibility = View.VISIBLE
                    val name = result.names[index]
                    val count = nameCounts.getOrDefault(name, 0)
                    bindResultText(title, labelWithCount(name, count), reused)
                    subtitle.visibility = View.GONE
                    body.visibility = View.GONE
                }
            }
            return view
        }
    }
}
