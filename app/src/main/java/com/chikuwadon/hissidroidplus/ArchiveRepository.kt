package com.chikuwadon.hissidroidplus

import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** Archive providers share a request/result model; native support is enabled individually. */
enum class ArchiveProvider(val label: String, val url: String, val nativeSearch: Boolean) {
    EDGE_ARCHIVE("エッヂの過去ログ（3代目）", "https://eddiarchive3rd.boy.jp/", true),
    EDGE_KYODEMO("エッヂ / Kyodemo", "https://www.kyodemo.net/sdemo/", false),
    FIVECH_KYODEMO("5ch / Kyodemo", "https://www.kyodemo.net/sdemo/", false),
    FIVECH_NUKO("5ch / ぬこ", "https://n2ch.net/?guid=ON", false)
}

data class ArchiveQuery(
    val keyword: String,
    val exclude: String = "",
    val minimum: String = "",
    val from: String = "",
    val to: String = ""
) {
    fun validate() {
        require(keyword.isNotBlank() && keyword.length <= 200) { "スレタイを1～200文字で入力してください" }
        require(exclude.length <= 200) { "除外ワードは200文字以内で入力してください" }
        require(minimum.isEmpty() || (minimum.toIntOrNull() ?: -1) in 0..1000000) { "レス数が不正です" }
        val start = if (from.isEmpty()) null else LocalDate.parse(from)
        val end = if (to.isEmpty()) null else LocalDate.parse(to)
        require(start == null || end == null || !start.isAfter(end)) { "開始日は終了日以前にしてください" }
    }
}

data class ArchiveThread(val title: String, val url: String, val replies: Int, val date: String)
data class ArchivePage(val items: List<ArchiveThread>, val total: Long, val page: Int, val hasNext: Boolean)

object ArchiveRepository {
    private val count = Regex("検索結果[：:]\\s*([0-9,]+)\\s*件")
    private val titleCount = Regex("^(.*?)\\s*\\(([0-9,]+)\\)\\s*$")
    private val date = Regex("20\\d{2}/\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}:\\d{2}")
    private val edgePath = Regex("^/test/read\\.cgi/[A-Za-z0-9_]{1,60}/[0-9]{10,20}/?$")

    @JvmStatic
    fun url(query: ArchiveQuery, page: Int): String {
        query.validate()
        require(page in 1..1000000)
        // Names are taken from the provider's public pagination links. Default sort is new.
        val parameters = linkedMapOf("keyword" to query.keyword.trim(), "keyword_exclude" to query.exclude.trim(),
            "NoR" to query.minimum, "start_date" to query.from, "end_date" to query.to,
            "onor" to "", "fuzzy" to "", "sort" to "new", "page" to page.toString())
        return ArchiveProvider.EDGE_ARCHIVE.url + "?" + parameters.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())
        }
    }

    fun search(provider: ArchiveProvider, query: ArchiveQuery, page: Int): ArchivePage {
        require(provider == ArchiveProvider.EDGE_ARCHIVE) { "アプリ内検索は未対応です" }
        return parse(HttpTransport.get(url(query, page), StandardCharsets.UTF_8), page)
    }

    @JvmStatic
    fun parse(html: String, page: Int): ArchivePage {
        val doc = Jsoup.parse(html, ArchiveProvider.EDGE_ARCHIVE.url)
        val total = count.find(doc.text())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
            ?: throw IOException("検索結果の形式を確認できませんでした")
        val threads = LinkedHashMap<String, ArchiveThread>()
        var hasNext = false
        for (anchor in doc.select("a[href]")) {
            val uri = try { URI.create(anchor.absUrl("href")) } catch (_: IllegalArgumentException) { continue }
            if (uri.rawUserInfo != null || uri.port != -1) continue
            if (uri.host.equals("eddiarchive3rd.boy.jp", true) && uri.scheme == "https" && uri.path == "/") {
                val next = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("page=") }
                    ?.substringAfter('=')?.toIntOrNull()
                if (next != null && next > page) hasNext = true
                continue
            }
            if (!uri.host.equals("bbs.eddibb.cc", true) ||
                (uri.scheme != "http" && uri.scheme != "https") || uri.rawQuery != null ||
                uri.rawFragment != null || !edgePath.matches(uri.path.orEmpty())) continue
            val matched = titleCount.matchEntire(anchor.text()) ?: continue
            val name = matched.groupValues[1].trim()
            val replies = matched.groupValues[2].replace(",", "").toIntOrNull() ?: continue
            if (name.isEmpty()) continue
            val canonical = EdgeThreadLink.original(uri.toString())
            if (ThreadOpener.validatedThreadUrl(canonical).isEmpty()) continue
            val time = date.find(anchor.nextElementSibling()?.text().orEmpty())?.value
                ?: anchor.parent()?.takeIf { it.select("a[href]").size == 1 }?.let { date.find(it.text())?.value }
                ?: ""
            threads.putIfAbsent(canonical, ArchiveThread(name, canonical, replies, time))
        }
        if (total > 0 && threads.isEmpty()) throw IOException("スレッド一覧を解析できませんでした")
        return ArchivePage(threads.values.toList(), total, page, hasNext && threads.isNotEmpty())
    }
}
