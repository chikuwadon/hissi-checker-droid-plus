package com.chikuwadon.hissidroidplus

import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** Size-limited, origin-validated HTTP transport, only invoked from worker threads. */
object HttpTransport {
    private const val LIMIT = 4 * 1024 * 1024
    @Volatile private var diagnostics = "未取得"
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
    fun lastDiagnostic(): String = diagnostics

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
        var current = url
        var form = payload
        for (redirects in 0..3) {
            val target = URL(current)
            validate(target)
            val connection = target.openConnection() as HttpURLConnection
            connection.connectTimeout = connectMs
            connection.readTimeout = readMs
            connection.instanceFollowRedirects = false
            if (userAgent.isNotEmpty()) connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.5")
            connection.setRequestProperty(
                "Accept", "text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.6"
            )
            connection.setRequestProperty("Accept-Encoding", "identity")
            val isKyodemo = target.host.endsWith("kyodemo.net")
            if (isKyodemo) {
                val cookie = cookieJar?.read(target.toExternalForm())
                if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)
                connection.setRequestProperty("Referer", "https://www.kyodemo.net/sdemo/")
            }
            try {
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
                val hadCookie = connection.getRequestProperty("Cookie") != null
                val status = connection.responseCode
                val contentType = connection.contentType
                diagnostics = "HTTP=" + status + " / 形式=" +
                    (contentType?.replace(Regex("[\r\n]"), " ") ?: "未指定") +
                    " / リダイレクト=" + redirects + " / Cookie送信=" +
                    (if (hadCookie) "あり" else "なし") +
                    " / UA=" + (if (userAgent.isNotEmpty()) "Android WebView" else "標準HTTP")
                if (isKyodemo) {
                    for ((header, values) in connection.headerFields) {
                        if (!header.equals("Set-Cookie", ignoreCase = true)) continue
                        for (value in values) {
                            try {
                                cookieJar?.save(target.toExternalForm(), value)
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
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (output.size() + n > LIMIT) throw IOException("HTMLが大きすぎます")
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
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
                diagnostics += " / 文字コード=" + chosen.name() +
                    (if (decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER))
                        "(Shift_JIS再判定)" else "") + " / 受信=" + data.size + "B"
                if (decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER)) {
                    decoded = decoded.substring(HissiHtmlDecoder.FALLBACK_MARKER.length)
                }
                return decoded
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
        throw IOException("通信先が許可されていません")
    }
}
