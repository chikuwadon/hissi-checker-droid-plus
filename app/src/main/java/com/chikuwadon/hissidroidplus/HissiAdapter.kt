package com.chikuwadon.hissidroidplus

import java.io.IOException
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.LinkedHashSet
import java.util.regex.Pattern
import org.jsoup.Jsoup

/** Independent HTML adapter for hissi.org results. */
class HissiAdapter {
    private data class Response(val html: String, val url: String)

    @Throws(IOException::class)
    private fun getPage(url: String): Response =
        try {
            Response(HttpTransport.get(url, SJIS), url)
        } catch (failure: IOException) {
            if (!HissiRetryPolicy.shouldRetry(url, failure)) throw failure
            val alternative = "https://" + url.substring("http://".length)
            try {
                Response(HttpTransport.get(alternative, SJIS), alternative)
            } catch (second: IOException) {
                second.addSuppressed(failure)
                throw second
            }
        }

    @Throws(IOException::class)
    fun load(query: CheckerQuery): CheckerResult {
        var url = query.url.ifEmpty { resultUrl(query.board, query.date, query.id) }
        val directHttp = httpFallbackUrl(url)
        if (directHttp.isNotEmpty()) url = directHttp
        var result = CheckerResult()
        var primaryError: IOException? = null
        try {
            val primary = getPage(url)
            url = primary.url
            result = parse(primary.html, url)
        } catch (failure: IOException) {
            primaryError = failure
        }
        if (result.posts.isEmpty()) {
            val all = url + (if (url.contains("?")) "&" else "?") + "thread=all&name=all"
            try {
                val secondary = getPage(all)
                val parsed = parse(secondary.html, secondary.url)
                if (parsed.posts.isNotEmpty() || result.reportedPostCount < 0) {
                    result = parsed
                    url = secondary.url.replaceFirst(Regex("[?&]thread=all&name=all$"), "")
                }
            } catch (failure: IOException) {
                if (primaryError != null) failure.addSuppressed(primaryError)
                if (result.reportedPostCount < 0) throw failure
            }
        }
        if (primaryError != null && result.posts.isEmpty() && result.reportedPostCount < 0) {
            throw primaryError
        }
        result.sourceUrl = url
        result.diagnostic = HttpTransport.lastDiagnostic() +
            " / 解析=" + result.posts.size + "件 / 表示集計=" +
            result.reportedPostCount + "件" +
            (if (result.diagnostic.isEmpty()) "" else " / " + result.diagnostic)
        return result
    }

    @Throws(IOException::class)
    fun nextPage(url: String, page: Int): CheckerResult {
        if (page !in 1..20) throw IOException("ページ番号が不正です")
        val link = url + (if (url.contains("?")) "&" else "?") + "p=" + page
        val response = getPage(link)
        return parse(response.html, response.url).also { it.sourceUrl = url }
    }

    companion object {
        private val SJIS = Charset.forName("Shift_JIS")
        private val POST = Pattern.compile(
            "(?is)<a\\b[^>]*?\\bhref\\s*=\\s*['\"]?(https?://[^\\s'\"<>]+)['\"]?[^>]*>(.*?)</a>\\s*<br\\s*/?>\\s*" +
            "(\\d{1,6})\\s*[:：]\\s*<b\\b[^>]*>(.*?)</b>\\s*(.*?)<dd\\b[^>]*>(.*?)(?=</dl>|$)"
        )
        private val NUMBER = Pattern.compile(
            "(?i)<font\\s+size\\s*=\\s*5\\s+color\\s*=\\s*#FF6347[^>]*>(.*?)</td>.*?<td\\s+width\\s*=\\s*40[^>]*>(\\d+)</td>",
            Pattern.DOTALL
        )
        private val RANK = Pattern.compile("(\\d[\\d,]*)\\s*位\\s*/\\s*(\\d[\\d,]*)\\s*ID中")
        private val HOURLY = Pattern.compile("書き込み数\\s+((?:\\d+\\s+){24})\\d+")
        private val RESPONSE_URL = Pattern.compile("/(\\d{1,6})/?(?:[?#].*)?$")
        private val LEADING_NUMBER = Pattern.compile("^\\s*(\\d{1,6})\\b")
        private val DATE_IN_HEADER = Pattern.compile("20\\d{2}[/.-]\\d{1,2}[/.-]\\d{1,2}")
        private val MAIL_IN_HEADER = Pattern.compile("\\[[^]]*\\]")

        /** Standard Base64 without padding, same path and transport as the Java version. */
        @JvmStatic
        fun resultUrl(board: String, date: String, id: String): String {
            val encoded = Base64.getEncoder().withoutPadding()
                .encodeToString(id.toByteArray(StandardCharsets.UTF_8))
            return "http://hissi.org/read.php/" + board + "/" + date + "/" + encoded + ".html"
        }

        /** Canonicalize known Hissi result pages to working HTTP before loading. */
        @JvmStatic
        fun httpFallbackUrl(url: String): String =
            try {
                val uri = URI(url)
                if (!uri.scheme.equals("https", true) ||
                    !(uri.host.equals("hissi.org", true) ||
                        uri.host.equals("www.hissi.org", true)) ||
                    uri.port != -1 || uri.rawUserInfo != null ||
                    !uri.rawPath.startsWith("/read.php/")
                ) return ""
                URI("http", null, uri.host, -1, uri.rawPath, uri.rawQuery, uri.rawFragment)
                    .toASCIIString()
            } catch (_: Exception) {
                ""
            }

        @JvmStatic
        fun parse(html: String?, base: String): CheckerResult {
            val result = CheckerResult()
            if (html.isNullOrEmpty()) return result
            val doc = Jsoup.parse(html, base)
            val flat = doc.text().replace(Regex("\\s+"), " ")
            val placement = RANK.matcher(flat)
            if (placement.find()) {
                result.rank = placement.group(1) + "位 / " + placement.group(2) + " ID中"
            }
            val hourly = HOURLY.matcher(flat)
            if (hourly.find()) {
                val values = hourly.group(1).trim().split(Regex("\\s+"))
                if (values.size == 24) {
                    result.hourlyCounts = try {
                        IntArray(24) { index -> values[index].toInt() }
                    } catch (_: NumberFormatException) {
                        null
                    }
                }
            }
            val count = NUMBER.matcher(html)
            if (count.find()) {
                if (result.rank.isEmpty()) {
                    result.rank = Jsoup.parseBodyFragment(count.group(1)).text()
                }
                result.rank = result.rank.replace(Regex("(\\d)\\s*ID中"), "$1 ID中")
                result.reportedPostCount = count.group(2).toIntOrNull() ?: result.reportedPostCount
            }
            val threadStart = html.indexOf("書き込んだスレッド一覧")
            val postsStart = html.indexOf("書き込みレス一覧")
            if (threadStart >= 0 && postsStart > threadStart) {
                val fragment = Jsoup.parseBodyFragment(
                    html.substring(threadStart, postsStart), base
                )
                for (link in fragment.select("a[href]")) {
                    val absolute = link.absUrl("href")
                    val title = link.text().trim()
                    if (absolute.isNotEmpty() && title.isNotEmpty()) {
                        result.threads.putIfAbsent(absolute, title)
                    }
                }
                val first = fragment.selectFirst("td")
                if (first != null) {
                    val names = LinkedHashSet<String>()
                    for (part in first.html().split(Regex("(?i)<br\\s*/?>"))) {
                        val name = Jsoup.parseBodyFragment(part).text().trim()
                        if (name.isNotEmpty() && name.length < 128 &&
                            !name.contains("書き込んだスレッド一覧")
                        ) names.add(name)
                    }
                    result.names.addAll(names)
                }
            }
            val section = if (postsStart < 0) html else html.substring(postsStart)
            val seen = HashSet<String>()
            parseDomPosts(section, base, result, seen)
            if (result.posts.isEmpty()) {
                val matcher = POST.matcher(section)
                while (matcher.find()) {
                    val anchor = Jsoup.parseBodyFragment(
                        "<a href=\"" + matcher.group(1) + "\">open</a>", base
                    ).selectFirst("a") ?: continue
                    val link = anchor.absUrl("href")
                    if (!link.startsWith("http://") && !link.startsWith("https://")) continue
                    val thread = Jsoup.parseBodyFragment(matcher.group(2)).text().trim()
                    val number = matcher.group(3)
                    val author = PostFormatter.normalizedInfo(
                        Jsoup.parseBodyFragment(matcher.group(4)).text()
                    )
                    val extras = PostFormatter.normalizedInfo(
                        Jsoup.parseBodyFragment(matcher.group(5)).text()
                    )
                    val meta = PostFormatter.normalizedInfo(number + " " + author + " " + extras)
                    val body = Jsoup.parseBodyFragment(matcher.group(6)).wholeText().trim()
                    if (seen.add(link + "|" + number)) {
                        result.posts.add(CheckerResult.Post(thread, author, meta, body, link))
                        result.threads.putIfAbsent(PostIndex.openableThreadUrl(link), thread)
                    }
                }
            }
            PostIndex.collectNames(result)
            if (result.posts.isEmpty()) {
                val structure = Jsoup.parseBodyFragment(section, base)
                result.diagnostic = "HTML構造: 見出し=" + (postsStart >= 0) +
                    ", dl=" + structure.select("dl").size +
                    ", dt=" + structure.select("dt").size +
                    ", dd=" + structure.select("dd").size +
                    ", スレURL=" + structure.select("a[href*=read.cgi]").size
            }
            return result
        }

        private fun parseDomPosts(
            html: String, base: String, result: CheckerResult, seen: MutableSet<String>
        ) {
            val doc = Jsoup.parseBodyFragment(html, base)
            for (dd in doc.select("dd")) {
                val dt = dd.previousElementSibling() ?: continue
                if (dt.normalName() != "dt") continue
                val link = dt.select("a[href]").firstOrNull {
                    isThreadLink(it.absUrl("href"))
                } ?: continue
                val href = link.absUrl("href")
                val urlNumber = RESPONSE_URL.matcher(href)
                if (!urlNumber.find()) continue
                val number = urlNumber.group(1)
                val withoutLink = dt.clone()
                withoutLink.select("a[href]").remove()
                val raw = PostFormatter.normalizedInfo(withoutLink.text())
                val displayedNumber = LEADING_NUMBER.matcher(raw)
                if (!displayedNumber.find() || number != displayedNumber.group(1)) continue
                var tail = raw.substring(displayedNumber.end()).trim()
                tail = tail.replaceFirst(Regex("^(?:[:：]|\\uFFFD[Ff])\\s*"), "")
                if (!DATE_IN_HEADER.matcher(tail).find()) continue
                var author = tail
                val date = DATE_IN_HEADER.matcher(author)
                if (date.find()) author = author.substring(0, date.start())
                val mail = MAIL_IN_HEADER.matcher(author)
                if (mail.find()) author = author.substring(0, mail.start())
                author = author.replaceFirst(Regex("\\s+[（(][^）)]*[）)]\\s*$"), "").trim()
                author = PostIndex.cleanAuthor(author)
                val meta = PostFormatter.normalizedInfo(number + " " + tail)
                    .replace(Regex("\\uFFFD[Ff](?=20\\d{2}[/.-])"), " ")
                val title = link.text().trim()
                val body = dd.wholeText().trim()
                if (seen.add(href + "|" + number)) {
                    result.posts.add(CheckerResult.Post(title, author, meta, body, href))
                    result.threads.putIfAbsent(PostIndex.openableThreadUrl(href), title)
                }
            }
        }

        private fun isThreadLink(href: String): Boolean =
            (href.startsWith("http://") || href.startsWith("https://")) &&
                (href.contains("/test/read.cgi/") || href.contains("/sdemo/r/e_e_"))
    }
}
