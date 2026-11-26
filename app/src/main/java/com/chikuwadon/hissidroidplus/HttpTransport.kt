package com.chikuwadon.hissidroidplus

import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** Size-limited, origin-validated HTTP transport, only invoked from worker threads. */
object HttpTransport {
    private const val LIMIT = 4 * 1024 * 1024
    private val diagnostics = HttpRequestDiagnostics()
    @Volatile private var userAgent = ""
    private val CHARSET = Pattern.compile("(?i)charset\\s*=\\s*[\"' ]*([A-Za-z0-9_-]+)")

    interface CookieJar {
        fun read(url: String): String?
        fun save(url: String, cookie: String)
    }

    private val WEBVIEW_COOKIES: CookieJar = object : CookieJar {
        override fun read(url: String): String? = CookieManager.getInstance().getCookie(url)
        override fun save(url: String, cookie: String) {
            CookieManager.getInstance().setCookie(url, cookie)
        }
    }
    @Volatile private var cookieJar: CookieJar? = null

    /** Cookie bridge has no Activity reference and imports no external-app cookies. */
    @JvmStatic fun enableWebViewCookies() { cookieJar = WEBVIEW_COOKIES }

    @JvmStatic
    fun setUserAgent(ua: String?) {
        if (!ua.isNullOrBlank()) userAgent = ua
    }

    @JvmStatic
    fun lastDiagnostic(): String = diagnostics.get()

    @JvmStatic
    @Throws(IOException::class)
    fun get(url: String, charset: Charset): String = request(url, null, charset, 10_000, 15_000)

    @JvmStatic
    @Throws(IOException::class)
    fun post(url: String, data: String, charset: Charset): String =
        request(url, data, charset, 10_000, 15_000)

    /** Short, bounded per-connection wait for checking a search candidate. */
    @JvmStatic
    @Throws(IOException::class)
    fun getForVerification(url: String, charset: Charset): String =
        request(url, null, charset, 3_000, 4_000)

    @Throws(IOException::class)
    private fun request(
        url: String, payload: String?, charset: Charset, connectMs: Int, readMs: Int
    ): String {
        diagnostics.set("HTTP応答未取得")
        val requestUserAgent = userAgent
        val requestCookies = cookieJar
        var current = url
        var form = payload
        for (redirects in 0..3) {
            HttpResponseReader.checkInterrupted()
            val target = URL(current)
            validate(target)
            val connection = target.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = connectMs
                connection.readTimeout = readMs
                connection.instanceFollowRedirects = false
                if (requestUserAgent.isNotEmpty()) connection.setRequestProperty("User-Agent", requestUserAgent)
                connection.setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.5")
                connection.setRequestProperty(
                    "Accept", "text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.6"
                )
                connection.setRequestProperty("Accept-Encoding", "identity")
                val isKyodemo = target.host.equals("kyodemo.net", true) ||
                    target.host.equals("www.kyodemo.net", true)
                if (isKyodemo) {
                    val cookie = try { requestCookies?.read(target.toExternalForm()) }
                        catch (_: RuntimeException) { null }
                    if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)
                    connection.setRequestProperty("Referer", "https://www.kyodemo.net/sdemo/")
                }
                // Request properties must be read before outputStream connects a POST.
                val hadCookie = connection.getRequestProperty("Cookie") != null
                HttpResponseReader.checkInterrupted()
                if (form != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                    connection.outputStream.use {
                        it.write(form.toByteArray(StandardCharsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val contentType = connection.contentType
                diagnostics.set("HTTP=" + status + " / 形式=" +
                    (contentType?.replace(Regex("[\r\n]"), " ") ?: "未指定") +
                    " / リダイレクト=" + redirects + " / Cookie送信=" +
                    (if (hadCookie) "あり" else "なし") +
                    " / UA=" + (if (requestUserAgent.isNotEmpty()) "Android WebView" else "標準HTTP"))
                if (isKyodemo) {
                    for ((header, values) in connection.headerFields) {
                        if (!header.equals("Set-Cookie", ignoreCase = true)) continue
                        for (value in values) {
                            try {
                                requestCookies?.save(target.toExternalForm(), value)
                            } catch (_: RuntimeException) {
                                // A cookie bridge failure must not hide a valid HTTP response.
                            }
                        }
                    }
                }
                if (status == 301 || status == 302 || status == 303 ||
                    status == 307 || status == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect without Location")
                    val next = URL(target, location)
                    validate(next)
                    current = next.toExternalForm()
                    if (status == 301 || status == 302 || status == 303) form = null
                    continue
                }
                if (status != 200) throw IOException("HTTP " + status)
                if (connection.contentLengthLong > LIMIT) throw IOException("HTMLが大きすぎます")
                val data = connection.inputStream.use { input ->
                    HttpResponseReader.read(input, LIMIT, connection.contentLengthLong)
                }
                var chosen = charset
                if (contentType != null) {
                    val match = CHARSET.matcher(contentType)
                    if (match.find()) {
                        try { chosen = Charset.forName(match.group(1)) }
                        catch (_: Exception) { /* fall back to caller's encoding */ }
                    }
                }
                if (contentType == null || !CHARSET.matcher(contentType).find()) {
                    val head = String(
                        data, 0, data.size.coerceAtMost(3072), StandardCharsets.ISO_8859_1
                    )
                    val match = CHARSET.matcher(head)
                    if (match.find()) {
                        try { chosen = Charset.forName(match.group(1)) }
                        catch (_: Exception) { /* keep selected encoding */ }
                    }
                }
                var decoded = HissiHtmlDecoder.decode(
                    data, chosen, target.host.equals("hissi.org", true) ||
                        target.host.equals("www.hissi.org", true)
                )
                diagnostics.set(diagnostics.get() + " / 文字コード=" + chosen.name() +
                    (if (decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER))
                        "(Shift_JIS再判定)" else "") + " / 受信=" + data.size + "B")
                if (decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER)) {
                    decoded = decoded.substring(HissiHtmlDecoder.FALLBACK_MARKER.length)
                }
                return decoded
            } catch (error: IOException) {
                diagnostics.set(diagnostics.get() + " / 通信失敗")
                throw error
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Redirectが多すぎます")
    }

    /** Visible for reflection-backed allowlist tests, never allows arbitrary hosts. */
    @JvmStatic
    @Throws(IOException::class)
    fun validate(target: URL) {
        val host = target.host
        val proto = target.protocol
        if (target.userInfo != null || target.port != -1) throw IOException("不正なURLです")
        if ((host.equals("kyodemo.net", true) || host.equals("www.kyodemo.net", true)) &&
            proto == "https") return
        if ((host.equals("hissi.org", true) || host.equals("www.hissi.org", true)) &&
            (proto == "http" || proto == "https")) return
        if (host.equals("menu.5ch.io", true) && proto == "https") return
        if (host.equals("n2ch.net", true) && proto == "https" && target.path == "/q/-/") return
        if (host.equals("eddiarchive3rd.boy.jp", true) && proto == "https" && target.path == "/") return
        throw IOException("通信先が許可されていません")
    }
}
