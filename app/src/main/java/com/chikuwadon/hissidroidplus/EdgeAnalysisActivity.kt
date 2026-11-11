package com.chikuwadon.hissidroidplus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.regex.Pattern
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONArray

/** Displays site-provided analysis and triggers site analysis only on a user tap. */
class EdgeAnalysisActivity : Activity() {
    companion object {
        const val EXTRA_URL = "analysis_url"
    }

    private var web: WebView? = null
    private var url = ""
    private lateinit var entries: LinearLayout
    private lateinit var notice: TextView
    private lateinit var runButton: TextView
    private var loaded = false
    private var started = false
    private var refreshNeeded = false
    private var refreshInProgress = false
    private val parserWorker = Executors.newSingleThreadExecutor()
    private var parsingPage = false
    private var cycle = 0
    private var lastHttpError = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.themeContext(base))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun makeText(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    // Required by the site's analysis flow; same-origin HTTPS navigation is enforced.
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AppSettings.configureBars(this)
        url = try {
            val query = CheckerQuery.parseUrl(intent.getStringExtra(EXTRA_URL))
            if (query.site != CheckerQuery.Site.EDGE) throw IllegalArgumentException()
            query.url
        } catch (_: Exception) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.surface))
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.displayCutout()
                )
                view.setPadding(0, bars.top, 0, bars.bottom)
                WindowInsets.CONSUMED
            }
            root.requestApplyInsets()
        }

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(48)))
        val back = makeText("‹", 32, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(42), -1))
        back.setOnClickListener { finish() }
        toolbar.addView(
            makeText("書き込み分析", 17, getColor(R.color.text_primary)),
            LinearLayout.LayoutParams(0, -1, 1f)
        )
        val open = makeText("サイトで開く", 12, getColor(R.color.accent)).apply {
            gravity = Gravity.CENTER
        }
        toolbar.addView(open, LinearLayout.LayoutParams(-2, -1))
        open.setOnClickListener { openBrowser() }

        notice = makeText("サイトの分析情報を確認しています…", 13,
            getColor(R.color.text_secondary))
        notice.setPadding(dp(16), dp(10), dp(16), dp(10))
        root.addView(notice, LinearLayout.LayoutParams(-1, -2))

        runButton = makeText("分析を開始", 14, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            minHeight = dp(44)
            visibility = View.GONE
            setPadding(dp(12), 0, dp(12), 0)
            val shape = GradientDrawable().apply {
                setColor(getColor(R.color.button_surface))
                cornerRadius = dp(9).toFloat()
            }
            background = RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.panel_pressed)), shape, null
            )
        }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(44)).apply {
            setMargins(dp(16), dp(6), dp(16), dp(6))
        }
        root.addView(runButton, buttonParams)
        runButton.setOnClickListener {
            if (refreshNeeded) refreshSiteAnalysis() else startSiteAnalysis()
        }

        val scroll = ScrollView(this).apply { isFillViewport = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        entries = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(20))
        }
        scroll.addView(entries)

        // Keep a same-origin WebView alive for the site's own JS/session,
        // but render sanitized plain-text results in native widgets.
        val siteWeb = WebView(this)
        web = siteWeb
        siteWeb.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        siteWeb.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val requested: Uri = request.url
                val host = requested.host
                return !requested.scheme.equals("https", ignoreCase = true) ||
                    !(host.equals("www.kyodemo.net", ignoreCase = true) ||
                        host.equals("kyodemo.net", ignoreCase = true))
            }

            override fun onReceivedHttpError(
                view: WebView, request: WebResourceRequest, response: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    lastHttpError = response.statusCode
                    showNotice("サイトのHTTP応答: " + lastHttpError +
                        "。アクセス制限がある場合はブラウザで操作してください。")
                }
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showNotice("サイトに接続できません。外部ブラウザで確認してください。")
                }
            }

            override fun onPageFinished(view: WebView, at: String) {
                loaded = true
                inspectPage()
            }
        }
        root.addView(siteWeb, LinearLayout.LayoutParams(dp(1), dp(1)))
        siteWeb.alpha = 0.01f
        siteWeb.loadUrl(url)
    }

    /** Status is visible only while checking, waiting or reporting errors. */
    private fun showNotice(value: String) {
        notice.visibility = View.VISIBLE
        notice.text = value
    }

    private fun inspectPage() {
        val currentWeb = web ?: return
        if (isFinishing || isDestroyed || parsingPage) return
        parsingPage = true
        val requestedCycle = cycle
        currentWeb.evaluateJavascript(
            "(function(){return document.documentElement.outerHTML})()"
        ) { json ->
            if (json == null || json == "null" || json.length > 12 * 1024 * 1024 ||
                isFinishing || isDestroyed
            ) {
                parsingPage = false
                return@evaluateJavascript
            }
            try {
                parserWorker.execute {
                    try {
                        val html = JSONArray("[" + json + "]").getString(0)
                        val analysis = EdgeAnalysis.parse(html)
                        runOnUiThread {
                            parsingPage = false
                            if (isFinishing || isDestroyed || requestedCycle != cycle) {
                                return@runOnUiThread
                            }
                            if (analysis.hasResult()) {
                                started = false
                                refreshNeeded = false
                                refreshInProgress = false
                                cycle++
                                runButton.visibility = View.GONE
                                // Hide the redundant success notice so the cards
                                // begin immediately below the toolbar.
                                notice.visibility = View.GONE
                                entries.removeAllViews()
                                analysis.sections.forEach(::renderSection)
                            } else if (!started && !refreshInProgress) {
                                entries.removeAllViews()
                                if (refreshNeeded) {
                                    showNotice("サイトの分析結果をまだ確認できません。再読み込みできます。")
                                    runButton.text = "分析結果を更新"
                                    runButton.visibility = View.VISIBLE
                                    runButton.isEnabled = true
                                } else if (analysis.startAvailable) {
                                    showNotice("まだ分析されていません。実行するとサイト側で分析が開始されます。")
                                    runButton.text = "分析を開始"
                                    runButton.visibility = View.VISIBLE
                                    runButton.isEnabled = true
                                } else {
                                    runButton.visibility = View.GONE
                                    if (lastHttpError == 0) {
                                        showNotice(
                                            if (analysis.hasStartButton)
                                                "サイト側の分析ボタンが利用できません。サイトで確認してください。"
                                            else
                                                "分析結果または分析ボタンを確認できませんでした。サイトで確認してください。"
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread {
                            parsingPage = false
                            if (!isFinishing && !isDestroyed && !started) {
                                showNotice("分析情報を読み取れません。サイトで確認してください。")
                            }
                        }
                    }
                }
            } catch (_: RejectedExecutionException) {
                parsingPage = false
            }
        }
    }

    /** Group known metric patterns into rows while preserving unknown sections. */
    private fun renderSection(section: EdgeAnalysis.Section) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(13))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.panel))
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), getColor(R.color.divider))
            }
        }
        val params = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, dp(10))
        }
        entries.addView(panel, params)
        val heading = makeText(section.title, 15, getColor(R.color.accent))
        heading.setPadding(0, 0, 0, dp(10))
        panel.addView(heading)
        val body = section.body.trim()
        val isPercent = section.title.contains("分類") ||
            body.matches(Regex("(?s).*[:：]\\s*\\d{1,3}%.*"))
        val pattern = if (isPercent)
            Pattern.compile("([^/:：\\n]{1,30})\\s*[:：]\\s*(\\d{1,3})%")
        else
            Pattern.compile("([^/:：\\n]{1,30})\\s*[:：]\\s*([SABCDEF][+-]?)(?=\\s|/|$)")
        val matcher = pattern.matcher(body)
        var rendered = 0
        while (matcher.find() && rendered < 30) {
            val name = (matcher.group(1) ?: "").trim()
                .replaceFirst(Regex("^[/\\s]+"), "").trim()
            val value = matcher.group(2) ?: ""
            if (name.isEmpty()) continue
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(11))
            }
            panel.addView(row)
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(line)
            val label = makeText(name, 13, getColor(R.color.text_primary))
            line.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(makeText(value + if (isPercent) "%" else "",
                13, getColor(R.color.accent)))
            if (isPercent) {
                val count = min(100, value.toInt())
                val track = LinearLayout(this).apply {
                    background = GradientDrawable().apply {
                        setColor(getColor(R.color.panel_pressed))
                        cornerRadius = dp(3).toFloat()
                    }
                    weightSum = 100f
                }
                val barParams = LinearLayout.LayoutParams(-1, dp(5)).apply {
                    setMargins(0, dp(5), 0, 0)
                }
                row.addView(track, barParams)
                val bar = View(this).apply {
                    background = GradientDrawable().apply {
                        setColor(getColor(R.color.accent))
                        cornerRadius = dp(3).toFloat()
                    }
                }
                track.addView(bar, LinearLayout.LayoutParams(0, -1, count.toFloat()))
                if (count < 100) {
                    track.addView(View(this),
                        LinearLayout.LayoutParams(0, -1, (100 - count).toFloat()))
                }
            }
            // Explanations stay directly under their corresponding metrics.
            val explanation = section.explanations[name]
            if (!explanation.isNullOrEmpty()) {
                val margin = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(5), 0, 0)
                }
                val description = makeText(explanation, 12,
                    getColor(R.color.text_secondary)).apply {
                    setTextIsSelectable(true)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    gravity = Gravity.START
                }
                row.addView(description, margin)
            }
            rendered++
        }
        if (rendered == 0) {
            addNarrative(panel, body, 13, getColor(R.color.text_primary))
        } else {
            val remainder = pattern.matcher(body).replaceAll(" ")
                .replace(Regex("[/\\s]+"), " ").trim()
            if (remainder.isNotEmpty() && remainder != "/") {
                addNarrative(panel, remainder, 12, getColor(R.color.text_secondary))
            }
        }
        if (section.notes.trim().isNotEmpty()) {
            val divider = View(this).apply {
                setBackgroundColor(getColor(R.color.divider))
            }
            val dividerParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                setMargins(0, dp(5), 0, dp(9))
            }
            panel.addView(divider, dividerParams)
            addNarrative(panel, section.notes, 12, getColor(R.color.text_secondary))
        }
    }

    private fun addNarrative(parent: LinearLayout, value: String, size: Int, color: Int) {
        if (value.isEmpty()) return
        val body = makeText(value, size, color).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(3).toFloat(), 1f)
            gravity = Gravity.START
        }
        parent.addView(body)
    }

    private fun startSiteAnalysis() {
        val currentWeb = web ?: return
        if (!loaded || started || refreshInProgress) return
        started = true
        refreshNeeded = false
        runButton.text = "分析を実行中…"
        runButton.visibility = View.VISIBLE
        runButton.isEnabled = false
        showNotice("サイト側の分析を実行しています…")
        // The site's analysis action must only be triggered by a user tap.
        val script = "(function(){" +
            "var a=Array.from(document.querySelectorAll('a.button,button')).find(function(e){" +
            "var t=(e.textContent||'').trim();return t==='分析'||t==='分析を開始';});" +
            "if(!a)return 'missing';" +
            "if(a.hasAttribute('disabled')||a.classList.contains('is-disabled'))return 'disabled';" +
            "a.click();return 'clicked';" +
            "})()"
        currentWeb.evaluateJavascript(script) { value ->
            if (isFinishing || isDestroyed) return@evaluateJavascript
            if (value?.contains("clicked") == true) {
                cycle++
                val mark = cycle
                // Read the DOM while the site processes its async analysis.
                for (delay in intArrayOf(1200, 3000, 5500, 9000, 13000,
                    18000, 23000, 30000, 37000, 45000)) {
                    currentWeb.postDelayed({
                        if (web != null && started && mark == cycle) inspectPage()
                    }, delay.toLong())
                }
                currentWeb.postDelayed({
                    if (web != null && started && mark == cycle) loadFreshPage()
                }, 27000L)
                currentWeb.postDelayed({
                    if (started && mark == cycle && !isFinishing) {
                        started = false
                        refreshNeeded = true
                        showRefreshAvailable("分析結果がまだ反映されていません。更新して再確認できます。")
                    }
                }, 48000L)
            } else {
                started = false
                refreshNeeded = true
                showRefreshAvailable("サイト側の分析ボタンを実行できません。ページを更新して確認してください。")
            }
        }
    }

    private fun showRefreshAvailable(message: String) {
        showNotice(message)
        runButton.text = "分析結果を更新"
        runButton.visibility = View.VISIBLE
        runButton.isEnabled = true
    }

    private fun loadFreshPage() {
        web?.loadUrl(url, mapOf(
            "Cache-Control" to "no-cache, no-store",
            "Pragma" to "no-cache"
        ))
    }

    private fun refreshSiteAnalysis() {
        val currentWeb = web ?: return
        if (started || refreshInProgress) return
        refreshNeeded = true
        refreshInProgress = true
        cycle++
        runButton.text = "分析結果を更新中…"
        runButton.visibility = View.VISIBLE
        runButton.isEnabled = false
        showNotice("サイトから最新の分析結果を取得しています…")
        val mark = cycle
        loadFreshPage()
        for (delay in intArrayOf(800, 2400, 4800, 8000, 13000)) {
            currentWeb.postDelayed({
                if (web != null && refreshInProgress && mark == cycle) inspectPage()
            }, delay.toLong())
        }
        currentWeb.postDelayed({
            if (web != null && refreshInProgress && mark == cycle && !isFinishing) {
                refreshInProgress = false
                showRefreshAvailable("分析結果をまだ確認できません。再度更新できます。")
            }
        }, 15000L)
    }

    override fun onResume() {
        super.onResume()
        // Returning from another screen can mean analysis completed server-side.
        if (loaded && refreshNeeded && !started && !refreshInProgress) {
            refreshSiteAnalysis()
        }
    }

    private fun openBrowser() {
        BrowserLauncher.open(this, url + "#wt")
    }

    override fun onDestroy() {
        cycle++
        parserWorker.shutdownNow()
        web?.let { view ->
            view.stopLoading()
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        web = null
        super.onDestroy()
    }
}
