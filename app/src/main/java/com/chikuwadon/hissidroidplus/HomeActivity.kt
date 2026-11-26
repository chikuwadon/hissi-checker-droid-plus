package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
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
class HomeActivity : CheckerActivity() {
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
    private var themeAtCreation: String? = null
    private lateinit var homeContent: View
    private lateinit var sectionContainer: FragmentContainerView
    private lateinit var sectionBack: OnBackPressedCallback
    private var pendingDestination: MainNavigation.Destination? = null
    var activeDestination = MainNavigation.Destination.HOME
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        val area = FrameLayout(this)
        homeContent = layoutInflater.inflate(R.layout.activity_home, area, false)
        area.addView(homeContent, FrameLayout.LayoutParams(-1, -1))
        sectionContainer = FragmentContainerView(this).apply {
            id = R.id.main_section_container
            visibility = View.GONE
        }
        area.addView(sectionContainer, FrameLayout.LayoutParams(-1, -1))
        shell.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(shell)
        AppTitle.apply(findViewById(R.id.app_title))
        AppSettings.configureBars(this)
        themeAtCreation = AppSettings.prefs(this).getString(AppSettings.THEME, AppSettings.THEME_DARK)
        MainNavigation.attach(this, shell, MainNavigation.Destination.HOME)
        sectionBack = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val active = supportFragmentManager.findFragmentByTag(activeDestination.name)
                if ((active as? HistoryFragment)?.handleBack() == true) return
                if (activeDestination != MainNavigation.Destination.HOME) {
                    showDestination(MainNavigation.Destination.HOME)
                } else {
                    MainNavigation.exitFromHome(this@HomeActivity)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, sectionBack)
        MainNavigation.registerHome(this)
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
            CalendarDialog.show(this, LocalDate.of(edgeSelected.get(Calendar.YEAR),
                edgeSelected.get(Calendar.MONTH) + 1, edgeSelected.get(Calendar.DAY_OF_MONTH))) { day ->
                edgeSelected.set(day.year, day.monthValue - 1, day.dayOfMonth)
                prefs.edit().putLong("edge_last_date", edgeSelected.timeInMillis).apply()
                refreshEdgeDate()
            }
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
            CalendarDialog.show(this, LocalDate.of(selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH) + 1, selected.get(Calendar.DAY_OF_MONTH))) { day ->
                selected.set(day.year, day.monthValue - 1, day.dayOfMonth)
                refreshDate()
                prefs.edit().putLong("last_date", selected.timeInMillis).apply()
            }
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
        val restored = state?.getString("main_destination")
        val requested = restored ?: intent.getStringExtra(MainNavigation.EXTRA_DESTINATION)
        showDestination(MainNavigation.hostDestination(requested))
        if (state == null) handleHissiScheme()
    }

    /** Top-level native sections share one window and navigation bar. */
    fun showDestination(destination: MainNavigation.Destination) {
        if (destination !in MainNavigation.hosted || isFinishing || isDestroyed) return
        if (supportFragmentManager.isStateSaved) {
            pendingDestination = destination
            return
        }
        // A hidden navigation item remains accessible via an explicit result menu or Back.
        val target = destination
        pendingDestination = null
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction().setReorderingAllowed(true)
        for (section in MainNavigation.hosted.filter { it != MainNavigation.Destination.HOME }) {
            val fragment = manager.findFragmentByTag(section.name)
            if (section == target) {
                val screen = fragment ?: when (section) {
                    MainNavigation.Destination.ARCHIVE -> ArchiveFragment()
                    MainNavigation.Destination.HISTORY -> HistoryFragment()
                    MainNavigation.Destination.FAVORITES -> FavoritesFragment()
                    MainNavigation.Destination.SETTINGS -> SettingsFragment()
                    else -> error("Unsupported hosted section: $section")
                }
                if (fragment == null) transaction.add(R.id.main_section_container, screen, section.name)
                else transaction.show(screen)
                transaction.setMaxLifecycle(screen, Lifecycle.State.RESUMED)
            } else if (fragment != null) {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction.commitNow()
        activeDestination = target
        homeContent.visibility = if (target == MainNavigation.Destination.HOME) View.VISIBLE else View.GONE
        sectionContainer.visibility = if (target == MainNavigation.Destination.HOME) View.GONE else View.VISIBLE
        MainNavigation.setSelected(this, target)
        if (target != MainNavigation.Destination.HOME) {
            currentFocus?.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(homeContent.windowToken, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("main_destination", activeDestination.name)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        if (newIntent.action == Intent.ACTION_VIEW &&
            newIntent.data?.scheme?.equals("hissi", ignoreCase = true) == true) {
            showDestination(MainNavigation.Destination.HOME)
            handleHissiScheme()
        } else {
            showDestination(MainNavigation.hostDestination(newIntent.getStringExtra(MainNavigation.EXTRA_DESTINATION)))
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        pendingDestination?.let { showDestination(it) }
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
                        .putExtra(ResultActivity.EXTRA_CONNECTED_ORIGIN, !AppSettings.standalone(this))
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
                        .putExtra(ResultActivity.EXTRA_CONNECTED_ORIGIN, finishAfterDeepLink && !AppSettings.standalone(this))
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
                            .putExtra(ResultActivity.EXTRA_CONNECTED_ORIGIN, finishAfterDeepLink && !AppSettings.standalone(this))
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
        TouchFeedback.foreground(update, 12)
        val list = ListView(this).apply {
            divider = ColorDrawable(getColor(R.color.divider))
            dividerHeight = dp(1)
            selector = TouchFeedback.listSelector(this@HomeActivity)
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
        TouchFeedback.foreground(close, 10)
        val dialog = AlertDialog.Builder(this).create().apply {
            setView(panel)
            create()
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.CENTER)
                setLayout(resources.displayMetrics.widthPixels - dp(34),
                    min((resources.displayMetrics.heightPixels * 0.77f).toInt(), dp(700)))
            }
            show()
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
        applyContentInsets(findViewById(android.R.id.content))
    }

    override fun onResume() {
        super.onResume()
        MainNavigation.refresh(this)
        if (!isFinishing && themeAtCreation != AppSettings.prefs(this).getString(AppSettings.THEME, AppSettings.THEME_DARK)) {
            recreate()
        }
    }

    override fun onDestroy() {
        MainNavigation.unregisterHome(this)
        worker.shutdownNow()
        super.onDestroy()
    }
}
