package com.chikuwadon.hissidroidplus

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import org.json.JSONTokener
import org.json.JSONObject

class KyodemoArchiveFormatException(message: String, cause: Throwable) : IOException(message, cause)

/** Browser-compatible fallback for a successful native response that was not a search page.
 * Scoped to the requested board; never navigate outside the Kyodemo HTTPS origin. */
internal class KyodemoArchiveBrowserFallback(private val host: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var callback: ((Result<String>) -> Unit)? = null
    private var submittedForm = false
    private var stage = "開始"
    private val timeout = Runnable { fail("タイムアウト（" + stage + "）") }

    private fun finish(result: Result<String>) {
        val done = callback ?: return
        close()
        done(result)
    }
    private fun fail(message: String) = finish(Result.failure(IOException("Kyodemo / WebView: " + message)))
    fun close() {
        callback = null
        handler.removeCallbacks(timeout)
        val view = web
        web = null
        view?.stopLoading()
        view?.destroy()
    }

    fun load(url: String, done: (Result<String>) -> Unit) {
        callback = done
        val expected = runCatching { URI.create(url) }.getOrNull()
        if (expected == null || !allowed(expected.toString(), expected.toString())) {
            fail("接続先が不正です")
            return
        }
        val fields = fields(expected)
        if (fields["k"].isNullOrEmpty() || fields["kt"] != "k_") {
            fail("検索条件が不正です")
            return
        }
        try {
            val view = WebView(host)
            web = view
            view.settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                loadsImagesAutomatically = false
                blockNetworkImage = true
                domStorageEnabled = true
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (allowed(request.url.toString(), url)) return false
                    if (request.isForMainFrame && web === view) {
                        val route = route(request.url.toString())
                        handler.post { if (web === view) fail("対象外のリダイレクト: " + route) }
                    }
                    return true
                }

                override fun onPageStarted(view: WebView, startedUrl: String, favicon: android.graphics.Bitmap?) {
                    if (web !== view) return
                    stage = "読込中: " + route(startedUrl)
                    if (!allowed(startedUrl, url)) fail("対象外のページ: " + route(startedUrl))
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val raw = request.url.toString()
                    val permitted = if (request.isForMainFrame) allowed(raw, url)
                        else ExternalUrlPolicy.isCheckerWebOrigin(raw)
                    return if (permitted) null else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (web === view && request.isForMainFrame) fail("HTTP " + response.statusCode)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (web === view && request.isForMainFrame) fail("通信エラー " + error.errorCode)
                }
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (web !== view) return
                    stage = "読込完了: " + route(finishedUrl)
                    if (!allowed(finishedUrl, url) || !allowed(view.url, url)) {
                        fail("対象外のページ: " + route(finishedUrl))
                        return
                    }
                    if (!matchesSearch(view.url, url)) {
                        // If redirected to the board index on page 1, submit the actual site form once.
                        // Do not silently drop date, sort or reply filters.
                        if (submittedForm || pageNumber(expected) != 1 ||
                            fields.keys.any { it !in setOf("bs", "kt", "fr", "to", "k") }) {
                            fail("検索条件を保持できませんでした")
                            return
                        }
                        submittedForm = true
                        stage = "検索フォーム送信"
                        val data = JSONObject(fields).toString()
                        val action = JSONObject.quote("https://www.kyodemo.net" + boardRoot(expected))
                        val script = "(function(){var v=$data;var a=$action;" +
                            "var f=Array.from(document.forms).find(function(x){var u=new URL(x.action);var t=new URL(a);return u.origin===t.origin&&u.pathname.replace(/\\/$/,'')===t.pathname.replace(/\\/$/,'')&&x.method.toLowerCase()==='get'&&x.elements.namedItem('k')&&x.elements.namedItem('kt');});" +
                            "if(!f)return false;for(var k in v){var e=f.elements.namedItem(k);if(!e)return false;e.value=v[k];if(e.value!==v[k])return false;}" +
                            "var btn=f.querySelector('button[type=submit],input[type=submit]');if(btn){btn.click();}else{HTMLFormElement.prototype.submit.call(f);}return true;})()"
                        view.evaluateJavascript(script) { raw ->
                            if (web === view && raw != "true") fail("検索フォームを確認できませんでした")
                        }
                        return
                    }
                    stage = "検索結果HTML取得"
                    view.evaluateJavascript("(function(){var h=document.documentElement.outerHTML;return h.length<=4194304?h:null;})()") { raw ->
                        if (web !== view) return@evaluateJavascript
                        if (!matchesSearch(view.url, url)) { fail("検索条件が変化しました"); return@evaluateJavascript }
                        val result = runCatching {
                            if (raw == null || raw.length > 16 * 1024 * 1024) throw IOException("HTMLが大きすぎます")
                            val html = JSONTokener(raw).nextValue() as? String ?: throw IOException("HTMLを取得できませんでした")
                            if (html.length > 4194304) throw IOException("HTMLが大きすぎます")
                            html
                        }
                        finish(result)
                    }
                }
            }
            handler.postDelayed(timeout, 25000)
            // For ordinary first-page searches, start at the board itself, then submit
            // its real GET form as a browser user would. Preserve all advanced filters.
            val formOnly = pageNumber(expected) == 1 && fields.keys.all { it in setOf("bs", "kt", "fr", "to", "k") }
            stage = if (formOnly) "掲示板を開く" else "検索リンクを開く"
            view.loadUrl(if (formOnly) "https://www.kyodemo.net" + boardRoot(expected) else url)
        } catch (failure: RuntimeException) {
            finish(Result.failure(IOException("Kyodemo / WebViewを開始できませんでした", failure)))
        }
    }

    companion object {
        private val pathPattern = Regex("^(/sdemo/b/[A-Za-z0-9_]{1,60})(?:/([0-9]+))?/?$")
        private fun boardRoot(uri: URI): String =
            pathPattern.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1)?.plus("/").orEmpty()
        private fun pageNumber(uri: URI): Int {
            val m = pathPattern.matchEntire(uri.rawPath.orEmpty()) ?: return -1
            val raw = m.groupValues[2]
            if (raw.isEmpty()) return 1
            val offset = raw.toLongOrNull() ?: return -1
            if (offset < 1 || (offset - 1) % 50 != 0L) return -1
            return ((offset - 1) / 50 + 1).takeIf { it <= ArchiveRepository.MAX_PAGE }?.toInt() ?: -1
        }
        private fun route(raw: String?): String = runCatching {
            val uri = URI.create(raw.orEmpty())
            val path = uri.rawPath.orEmpty().take(120).replace(Regex("[^A-Za-z0-9_/.-]"), "?")
            (uri.host ?: "?") + path + if (fields(uri)["k"].isNullOrEmpty()) " (検索条件なし)" else " (検索条件あり)"
        }.getOrDefault("不明")
        private fun fields(uri: URI): Map<String, String> = runCatching {
            uri.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
                it.substringBefore('=') to ArchiveExternalSearch.decodeKyodemoField(it.substringAfter('='))
            }
        }.getOrDefault(emptyMap())
        @JvmStatic fun allowed(raw: String?, requested: String): Boolean = runCatching {
            val uri = URI.create(raw.orEmpty())
            val expected = URI.create(requested)
            ExternalUrlPolicy.isCheckerWebOrigin(raw) && uri.rawFragment == null &&
                boardRoot(uri).isNotEmpty() && boardRoot(uri) == boardRoot(expected) &&
                pageNumber(uri) > 0 && (pageNumber(uri) == pageNumber(expected) || pageNumber(uri) == 1)
        }.getOrDefault(false)
        @JvmStatic fun matchesSearch(raw: String?, requested: String): Boolean = runCatching {
            if (!allowed(raw, requested)) return false
            val uri = URI.create(raw.orEmpty())
            val expected = URI.create(requested)
            val actual = fields(uri)
            val wanted = fields(expected)
            pageNumber(uri) == pageNumber(expected) && !wanted["k"].isNullOrEmpty() &&
                wanted.all { (key, value) -> actual[key].orEmpty() == value } &&
                actual.none { (key, value) -> key in setOf("bs","kt","fr","to","j","r","o1","o2","o3") &&
                    key !in wanted && value.isNotEmpty() && !(key == "j" && value == "1") }
        }.getOrDefault(false)
    }
}
