package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/** Independent search UI with a live and cached official 5ch board menu. */
class HomeActivity : Activity() {
    private lateinit var board: EditText
    private lateinit var id: EditText
    private lateinit var edge: EditText
    private lateinit var edgeBoard: EditText
    private lateinit var edgeId: EditText
    private lateinit var edgeDateSummary: Button
    private lateinit var hissiAnyDate: CheckBox
    private lateinit var edgeAnyDate: CheckBox
    private val edgeSelected = Calendar.getInstance()
    private lateinit var dateSummary: Button
    private val selected = Calendar.getInstance()
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var prefs: SharedPreferences
    private var busy = false
    private var finishAfterDeepLink = false
    private var boardGroups: List<BoardMenu.Category>? = null
    private var boardSource = ""

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_home)
        AppTitle.apply(findViewById(R.id.app_title))
        AppSettings.configureBars(this)
        findViewById<View>(R.id.home_more).setOnClickListener { showSettings(it) }
        applyInsets()
        board = findViewById(R.id.board)
        id = findViewById(R.id.user_id)
        edge = findViewById(R.id.edge_url)
        edgeBoard = findViewById(R.id.edge_board)
        edgeId = findViewById(R.id.edge_user_id)
        edgeDateSummary = findViewById(R.id.edge_date_summary)
        prefs = getPreferences(MODE_PRIVATE)
        edgeBoard.setText(prefs.getString("edge_board", "liveedge"))
        dateSummary = findViewById(R.id.date_summary)
        hissiAnyDate = findViewById(R.id.hissi_any_date)
        edgeAnyDate = findViewById(R.id.edge_any_date)
        hissiAnyDate.isChecked = prefs.getBoolean("hissi_recent_search", false)
        edgeAnyDate.isChecked = prefs.getBoolean("edge_any_date", false)
        dateSummary.visibility = if (hissiAnyDate.isChecked) View.GONE else View.VISIBLE
        edgeDateSummary.visibility = if (edgeAnyDate.isChecked) View.GONE else View.VISIBLE
        hissiAnyDate.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("hissi_recent_search", checked).apply()
            dateSummary.visibility = if (checked) View.GONE else View.VISIBLE
        }
        edgeAnyDate.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("edge_any_date", checked).apply()
            edgeDateSummary.visibility = if (checked) View.GONE else View.VISIBLE
        }
        board.setText(prefs.getString("board", ""))
        id.setText(prefs.getString("last_id", ""))
        val time = prefs.getLong("last_date", -1L)
        if (time > 0) selected.timeInMillis = time
        refreshDate()
        val savedEdgeDay = prefs.getLong("edge_last_date", -1L)
        if (savedEdgeDay > 0) edgeSelected.timeInMillis = savedEdgeDay
        refreshEdgeDate()
        edgeDateSummary.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                edgeSelected.set(year, month, day)
                prefs.edit().putLong("edge_last_date", edgeSelected.timeInMillis).apply()
                refreshEdgeDate()
            }, edgeSelected.get(Calendar.YEAR), edgeSelected.get(Calendar.MONTH),
                edgeSelected.get(Calendar.DAY_OF_MONTH)).show()
        }
        findViewById<View>(R.id.edge_search).setOnClickListener {
            try {
                val code = edgeBoard.text.toString().trim()
                val user = edgeId.text.toString().trim()
                    .replaceFirst(Regex("(?i)^ID\\s*[:：]\\s*"), "").trim()
                if (!code.matches(Regex("[A-Za-z0-9_]{1,60}"))) {
                    throw IllegalArgumentException("エッヂの板名が不正です")
                }
                if (!user.matches(Regex("[A-Za-z0-9+./_-]{1,64}"))) {
                    throw IllegalArgumentException("エッヂのIDが不正です")
                }
                val date = formatDate(edgeSelected)
                CheckerQuery.validateDate(date)
                prefs.edit().putString("edge_board", code)
                    .putString("edge_last_id", user).apply()
                // Resolve the site's real thread key without intermediate UI.
                openEdgeDirect(code, date, user, edgeAnyDate.isChecked)
            } catch (bad: IllegalArgumentException) {
                toast(bad.message ?: "検索条件が不正です", Toast.LENGTH_LONG)
            }
        }
        edgeId.setText(prefs.getString("edge_last_id", ""))
        dateSummary.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                selected.set(year, month, day)
                refreshDate()
                prefs.edit().putLong("last_date", selected.timeInMillis).apply()
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)).show()
        }
        findViewById<View>(R.id.search).setOnClickListener {
            try {
                val query = CheckerQuery.hissi(
                    board.text.toString().trim(), formatDate(selected), id.text.toString().trim()
                )
                prefs.edit().putString("board", query.board)
                    .putString("last_id", query.id).apply()
                if (hissiAnyDate.isChecked) {
                    searchHissiRecent(query.board, query.id)
                } else {
                    startActivity(Intent(this, ResultActivity::class.java)
                        .putExtra("board", query.board)
                        .putExtra("date", query.date)
                        .putExtra("id", query.id))
                }
            } catch (bad: IllegalArgumentException) {
                toast(bad.message ?: "検索条件が不正です", Toast.LENGTH_LONG)
            }
        }
        findViewById<View>(R.id.open_edge).setOnClickListener {
            try {
                val query = CheckerQuery.parseUrl(edge.text.toString())
                if (query.site != CheckerQuery.Site.EDGE) {
                    throw IllegalArgumentException("エッヂのURLを指定してください")
                }
                startActivity(Intent(this, ResultActivity::class.java)
                    .setData(Uri.parse(query.url)))
            } catch (bad: IllegalArgumentException) {
                toast(bad.message ?: "URLが不正です", Toast.LENGTH_LONG)
            }
        }
        findViewById<View>(R.id.choose_board).setOnClickListener { showBoards() }
        findViewById<View>(R.id.add_favorite).setOnClickListener {
            val current = board.text.toString().trim()
            if (!current.matches(Regex("[A-Za-z0-9_]+"))) {
                toast("板名を指定してください")
                return@setOnClickListener
            }
            val favorites = HashSet(prefs.getStringSet("favorite_boards", emptySet())
                ?: emptySet())
            favorites.add(current)
            prefs.edit().putStringSet("favorite_boards", favorites).apply()
            toast("お気に入りに登録しました")
        }
        findViewById<View>(R.id.show_favorites).setOnClickListener {
            val favorites = (prefs.getStringSet("favorite_boards", emptySet())
                ?: emptySet()).toMutableList().sorted()
            if (favorites.isEmpty()) {
                toast("お気に入りがありません")
                return@setOnClickListener
            }
            showList("お気に入り", favorites, { index ->
                board.setText(favorites[index])
            }, {
                ThemedMenu.choose(this, "お気に入りをすべて削除しますか？",
                    arrayOf("戻る", "すべて削除"), -1) { choice ->
                    if (choice == 1) prefs.edit().remove("favorite_boards").apply()
                }
            })
        }
        handleHissiScheme()
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private fun formatDate(calendar: Calendar): String = String.format(
        Locale.ROOT, "%04d%02d%02d", calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)
    )

    /** ChMate custom URL: hissi:{host}/{board}/{id}[?date=YYYYMMDD]. */
    private fun handleHissiScheme() {
        val inbound = intent
        if (inbound?.action != Intent.ACTION_VIEW ||
            inbound.data?.scheme?.equals("hissi", ignoreCase = true) != true
        ) return
        try {
            val request = HissiScheme.parse(inbound.dataString)
            if (request.site == CheckerQuery.Site.EDGE) {
                edgeBoard.setText(request.board)
                edgeId.setText(request.id)
                val anyDate = request.date.isEmpty()
                edgeAnyDate.isChecked = anyDate
                finishAfterDeepLink = true
                val day = if (anyDate)
                    LocalDate.now(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.BASIC_ISO_DATE)
                else request.date
                openEdgeDirect(request.board, day, request.id, anyDate)
            } else {
                board.setText(request.board)
                id.setText(request.id)
                if (request.date.isEmpty()) {
                    hissiAnyDate.isChecked = true
                    finishAfterDeepLink = true
                    searchHissiRecent(request.board, request.id)
                } else {
                    hissiAnyDate.isChecked = false
                    startActivity(Intent(this, ResultActivity::class.java)
                        .putExtra("board", request.board)
                        .putExtra("date", request.date)
                        .putExtra("id", request.id))
                    finish()
                }
            }
        } catch (bad: IllegalArgumentException) {
            toast(bad.message ?: "hissiリンクが不正です", Toast.LENGTH_LONG)
        }
    }

    /** Toolbar overflow stays attached to the three-dot anchor. */
    private fun showSettings(anchor: View) {
        ThemedMenu.showOverflow(this, anchor,
            arrayOf("検索履歴", "テーマ", "スレの開き方", "リンクの開き方", "このアプリについて")
        ) { index ->
            when (index) {
                0 -> startActivity(Intent(this, HistoryActivity::class.java))
                1 -> showThemePicker()
                2 -> showThreadModePicker()
                3 -> showLinkModePicker()
                else -> ThemedMenu.showAbout(this)
            }
        }
    }

    private fun showThemePicker() {
        val values = arrayOf(AppSettings.THEME_DARK, AppSettings.THEME_LIGHT,
            AppSettings.THEME_SYSTEM)
        val labels = arrayOf("ダーク", "ライト", "端末の設定に合わせる")
        val current = AppSettings.prefs(this).getString(AppSettings.THEME,
            AppSettings.THEME_DARK)
        ThemedMenu.choose(this, "テーマ", labels, values.indexOf(current)) { index ->
            AppSettings.prefs(this).edit().putString(AppSettings.THEME, values[index]).apply()
            recreate()
        }
    }

    private fun showThreadModePicker() {
        val labels = arrayOf("毎回選択", "ChMateで開く", "Haiagaruで開く",
            "他のアプリで開く", "外部ブラウザで開く")
        val current = AppSettings.threadOpenMode(this)
        ThemedMenu.choose(this, "スレの開き方", labels,
            AppSettings.THREAD_MODES.indexOf(current)) { index ->
            AppSettings.setThreadOpenMode(this, AppSettings.THREAD_MODES[index])
        }
    }

    private fun showLinkModePicker() {
        val labels = arrayOf("ChMate連携（元の画面へ戻る）", "単独アプリ（別タスクで開く）")
        ThemedMenu.choose(this, "リンクの開き方", labels,
            if (AppSettings.standalone(this)) 1 else 0) { index ->
            AppSettings.prefs(this).edit().putString(
                AppSettings.LINK_MODE,
                if (index == 1) AppSettings.MODE_STANDALONE else AppSettings.MODE_CONNECTED
            ).apply()
        }
    }

    private fun refreshDate() {
        dateSummary.text = formatDisplayDate(selected)
    }

    private fun refreshEdgeDate() {
        edgeDateSummary.text = formatDisplayDate(edgeSelected)
    }

    private fun formatDisplayDate(calendar: Calendar): String = String.format(
        Locale.JAPAN, "%04d年%02d月%02d日  ▾",
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    /** Seven one-day lookups at most. Never traverse the whole archive. */
    private fun searchHissiRecent(code: String, user: String) {
        if (busy) return
        busy = true
        toast("5chの直近7日を検索中…")
        worker.execute {
            val found = HissiRecentSearch.find(
                code, user, LocalDate.now(ZoneId.of("Asia/Tokyo"))
            ) { boardName, day, idValue ->
                val url = HissiAdapter.resultUrl(boardName, day, idValue)
                val html = HttpTransport.getForVerification(url, Charset.forName("Shift_JIS"))
                val check = HissiAdapter.parse(html, url)
                if (check.posts.isEmpty() && check.reportedPostCount < 0) {
                    throw IOException("取得ページの形式を確認できませんでした")
                }
                check.posts.isNotEmpty() || check.reportedPostCount > 0
            }
            runOnUiThread {
                busy = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (found.day != null) {
                    startActivity(Intent(this, ResultActivity::class.java)
                        .putExtra("board", code)
                        .putExtra("date", found.day)
                        .putExtra("id", user))
                    if (finishAfterDeepLink) finish()
                } else if (found.error != null) {
                    toast("検索を完了できませんでした（" + found.checkedDays +
                        "日目）: " + found.error, Toast.LENGTH_LONG)
                } else {
                    toast("直近7日間に該当する投稿は見つかりませんでした", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun openEdgeDirect(code: String, date: String, user: String, anyDate: Boolean) {
        if (busy) return
        busy = true
        toast("エッヂのIDを検索中…")
        worker.execute {
            try {
                val url = if (anyDate) EdgeSearchLink.searchUrlAnyDate(code, user)
                    else EdgeSearchLink.searchUrl(code, date, user)
                val html = HttpTransport.get(url, StandardCharsets.UTF_8)
                val matches = EdgeSearchResults.parse(
                    html, url, code, if (anyDate) null else date, user
                )
                // A search-date query alone cannot prove that any post matched.
                // Inspect at most six checker pages. Preserve WebView fallback
                // for inaccessible pages, with ResultActivity re-verification.
                var chosen: EdgeSearchResults.Item? = null
                var unverified: EdgeSearchResults.Item? = null
                var inspected = 0
                for (item in matches) {
                    if (anyDate) {
                        chosen = item
                        break
                    }
                    if (inspected++ >= 6) break
                    try {
                        val candidate = CheckerQuery.parseUrl(item.checkerUrl)
                        val check = EdgeAdapter().loadForVerification(candidate)
                        if (check.posts.isEmpty()) {
                            unverified = item
                            break
                        } else if (EdgeSearchValidation.matchingPosts(
                                check, date, user).posts.isNotEmpty()
                        ) {
                            chosen = item
                            break
                        }
                    } catch (_: IOException) {
                        unverified = item
                        break
                    }
                }
                val ready = chosen ?: unverified
                val noMatches = matches.isNotEmpty() && ready == null
                runOnUiThread {
                    busy = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (ready != null) {
                        startActivity(Intent(this, ResultActivity::class.java)
                            .setData(Uri.parse(ready.checkerUrl))
                            .putExtra(ResultActivity.EXTRA_VERIFY_EDGE_SEARCH_DATE, !anyDate))
                        if (finishAfterDeepLink) finish()
                    } else {
                        toast(if (noMatches)
                            "指定した日付・IDに一致する投稿はありません"
                        else if (anyDate)
                            "該当するIDの投稿が見つかりませんでした"
                        else
                            "指定した日付・IDの投稿が見つかりませんでした",
                            Toast.LENGTH_LONG)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    busy = false
                    if (!isFinishing && !isDestroyed) {
                        toast("検索に失敗しました: " +
                            (error.message ?: "通信エラー"), Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    private fun showBoards() {
        if (busy) return
        boardGroups?.takeIf { it.isNotEmpty() }?.let {
            showCategories(it, boardSource)
            return
        }
        busy = true
        worker.execute {
            var groups: List<BoardMenu.Category>? = null
            var origin = "保存済みの5ch公式メニュー"
            try {
                openFileInput("bbsmenu_5ch.json").use {
                    groups = BoardMenu.parseJson(readCached(it))
                }
            } catch (_: Exception) {}
            if (groups.isNullOrEmpty()) {
                try {
                    openFileInput("bbsmenu_5ch.html").use {
                        groups = BoardMenu.parseHtml(readCached(it))
                    }
                } catch (_: Exception) {}
            }
            if (groups.isNullOrEmpty()) {
                groups = fetchOfficialMenu()
                origin = "5ch公式メニュー（最新取得）"
            }
            if (groups.isNullOrEmpty()) {
                // Generated independently from the public official 5ch menu.
                try {
                    InputStreamReader(assets.open("offline_boards.tsv"),
                        StandardCharsets.UTF_8).use {
                        groups = BoardMenu.parseOfflineSnapshot(it)
                        origin = "内蔵板一覧（5ch公式メニューの保存版）"
                    }
                } catch (_: Exception) {}
            }
            val ready = groups
            val label = origin
            runOnUiThread {
                busy = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ready.isNullOrEmpty()) {
                    toast("板一覧を取得できませんでした。通信環境をご確認ください。板名は直接入力できます",
                        Toast.LENGTH_LONG)
                    return@runOnUiThread
                }
                boardGroups = ready
                boardSource = label
                showCategories(ready, label)
            }
        }
    }

    private fun fetchOfficialMenu(): List<BoardMenu.Category>? {
        try {
            val json = HttpTransport.get(BoardMenu.JSON_URL, StandardCharsets.UTF_8)
            val groups = BoardMenu.parseJson(json)
            if (groups.isNotEmpty()) {
                openFileOutput("bbsmenu_5ch.json", MODE_PRIVATE).use {
                    it.write(json.toByteArray(StandardCharsets.UTF_8))
                }
                deleteFile("bbsmenu_5ch.html")
                return groups
            }
        } catch (_: Exception) {}
        try {
            val html = HttpTransport.get(BoardMenu.HTML_URL, StandardCharsets.UTF_8)
            val groups = BoardMenu.parseHtml(html)
            if (groups.isNotEmpty()) {
                openFileOutput("bbsmenu_5ch.html", MODE_PRIVATE).use {
                    it.write(html.toByteArray(StandardCharsets.UTF_8))
                }
                deleteFile("bbsmenu_5ch.json")
                return groups
            }
        } catch (_: Exception) {}
        return null
    }

    private fun refreshBoards() {
        if (busy) return
        busy = true
        toast("5ch公式メニューを更新中…")
        worker.execute {
            val latest = fetchOfficialMenu()
            runOnUiThread {
                busy = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!latest.isNullOrEmpty()) {
                    boardGroups = latest
                    boardSource = "5ch公式メニュー（更新済み）"
                    showCategories(latest, boardSource)
                } else {
                    toast("更新できませんでした。保存済み一覧を表示します", Toast.LENGTH_LONG)
                    boardGroups?.let { showCategories(it, boardSource) }
                }
            }
        }
    }

    private fun readCached(input: InputStream): String {
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                if (output.size() + n > 4 * 1024 * 1024) throw IOException("Too large")
                output.write(buffer, 0, n)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }

    private fun showCategories(groups: List<BoardMenu.Category>, source: String) {
        val labels = groups.map { it.name + "  （" + it.boards.size + "）" }
        showList("カテゴリを選択\n" + source, labels,
            { index -> showCategory(groups, source, index) }, null, true)
    }

    private fun showCategory(groups: List<BoardMenu.Category>, source: String, index: Int) {
        val category = groups[index]
        val labels = category.boards.map { it.toString() }
        showList(category.name + "  ·  " + labels.size + "板", labels,
            { position -> board.setText(category.boards[position].code) },
            { showCategories(groups, source) })
    }

    /** Scrollable centered dialog, never a bottom sheet. */
    private fun showList(
        title: String, labels: List<String>, pick: (Int) -> Unit,
        back: (() -> Unit)?, refresh: Boolean = false
    ) {
        if (isFinishing || isDestroyed) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(getColor(R.color.panel))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), getColor(R.color.border))
            }
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        panel.addView(titleRow)
        val heading = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(18), dp(17), 0, dp(11))
        }
        titleRow.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val update = TextView(this).apply {
            text = "⟳"
            contentDescription = "5ch公式メニューを手動更新"
            textSize = 26f
            setTextColor(getColor(R.color.accent))
            gravity = Gravity.CENTER
            visibility = if (refresh) View.VISIBLE else View.GONE
        }
        titleRow.addView(update, LinearLayout.LayoutParams(dp(54), dp(54)))
        val list = ListView(this).apply {
            divider = ColorDrawable(getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = ColorDrawable(getColor(R.color.panel_pressed))
        }
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 14f
                    minHeight = dp(49)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(8), dp(14), dp(8))
                }
            }
        }
        list.adapter = adapter
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = TextView(this).apply {
            text = if (back == null) "閉じる" else "カテゴリに戻る"
            textSize = 14f
            setTextColor(getColor(R.color.accent))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            height = dp(52)
        }
        panel.addView(close)
        val dialog = AlertDialog.Builder(this).create().apply {
            setView(panel)
            show()
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.CENTER)
                setLayout(resources.displayMetrics.widthPixels - dp(34),
                    min((resources.displayMetrics.heightPixels * 0.77f).toInt(), dp(700)))
            }
        }
        update.setOnClickListener {
            dialog.dismiss()
            refreshBoards()
        }
        list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            pick(position)
        }
        close.setOnClickListener {
            dialog.dismiss()
            back?.invoke()
        }
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).roundToInt()

    private fun applyInsets() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val root = findViewById<View>(android.R.id.content)
            val top = root.paddingTop
            val bottom = root.paddingBottom
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
                )
                view.setPadding(view.paddingLeft, top + bars.top,
                    view.paddingRight, bottom + bars.bottom)
                WindowInsets.CONSUMED
            }
            root.requestApplyInsets()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
