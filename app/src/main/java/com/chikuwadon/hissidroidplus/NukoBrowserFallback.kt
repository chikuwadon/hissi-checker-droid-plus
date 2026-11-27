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
import java.io.IOException
import java.net.URI
import org.json.JSONTokener

/** One bounded browser-compatible request after an HTTP-success parse failure.
 * No JS bridge, no external navigation, no retry of HTTP access-denied responses. */
internal class NukoBrowserFallback(private val host: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var callback: ((Result<String>) -> Unit)? = null
    private val timeout = Runnable { finish(Result.failure(IOException("ぬこ / ブラウザ方式タイムアウト"))) }
    private fun finish(result: Result<String>) {
        val done = callback ?: return
        close()
        done(result)
    }
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
        if (!allowed(url)) {
            finish(Result.failure(IOException("ぬこ / 接続先が不正です")))
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
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !allowed(request.url.toString())
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && web === view)
                        finish(Result.failure(IOException("ぬこ / HTTP " + response.statusCode)))
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && web === view)
                        finish(Result.failure(IOException("ぬこ / 通信エラー " + error.errorCode)))
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (web !== view || !allowed(url) || !allowed(view.url)) return
                    view.evaluateJavascript("(function(){var h=document.documentElement.outerHTML;return h.length<=4194304?h:null;})()") { raw ->
                        if (web !== view || !allowed(view.url)) return@evaluateJavascript
                        val result = runCatching {
                            if (raw == null || raw.length > 16 * 1024 * 1024) throw IOException("ぬこ / HTMLが大きすぎます")
                            val html = JSONTokener(raw).nextValue() as? String ?: throw IOException("ぬこ / HTMLを取得できませんでした")
                            if (html.length > 4194304) throw IOException("ぬこ / HTMLが大きすぎます")
                            html
                        }
                        finish(result)
                    }
                }
            }
            handler.postDelayed(timeout, 20000)
            view.loadUrl(url)
        } catch (failure: RuntimeException) {
            finish(Result.failure(IOException("ぬこ / ブラウザ方式を開始できませんでした", failure)))
        }
    }
    companion object {
        fun allowed(raw: String?): Boolean = try {
            val uri = URI.create(raw ?: "")
            uri.scheme.equals("https", true) && uri.host.equals("n2ch.net", true) &&
                uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null && uri.rawPath == "/q/-/"
        } catch (_: IllegalArgumentException) { false }
    }
}
