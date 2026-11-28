package com.chikuwadon.hissidroidplus

import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** All selectable archive providers support native search. */
enum class ArchiveProvider(val label: String, val url: String) {
    EDGE_ARCHIVE("エッヂの過去ログ（3代目）", "https://eddiarchive3rd.boy.jp/"),
    EDGE_KYODEMO("Kyodemo / エッヂ", "https://www.kyodemo.net/sdemo/b/e_e_liveedge/?bs=&kt=k_&fr=&to=&k="),
    FIVECH_KYODEMO("Kyodemo / 5ch", "https://www.kyodemo.net/sdemo/"),
    OTHER_KYODEMO("Kyodemo / その他の掲示板", "https://www.kyodemo.net/sdemo/"),
    FIVECH_NUKO("ぬこ / 5ch", "https://n2ch.net/q/-/?kako=1&guid=ON")
}

data class ArchiveQuery @JvmOverloads constructor(
    val keyword: String,
    val exclude: String = "",
    val minimum: String = "",
    val from: String = "",
    val to: String = "",
    val maximum: String = "",
    val sort: String = "new",
    val fuzzy: String = "",
    val exact: Boolean = false,
    val externalBoards: Boolean = false,
    val boardCode: String = "",
    val boardName: String = ""
) {
    fun validate() {
        require(boardCode.isEmpty() || boardCode.matches(Regex("[A-Za-z0-9_]{1,60}"))) { "板コードが不正です" }
        require(keyword.isNotBlank() && keyword.length <= 200) { "スレタイを1～200文字で入力してください" }
        require(exclude.length <= 200) { "除外ワードは200文字以内で入力してください" }
        require(minimum.isEmpty() || (minimum.toIntOrNull() ?: -1) in 0..1000000) { "レス数が不正です" }
        require(maximum.isEmpty() || (maximum.toIntOrNull() ?: -1) in 0..1000000) { "最大レス数が不正です" }
        require(minimum.isEmpty() || maximum.isEmpty() || minimum.toInt() <= maximum.toInt()) {
            "最小レス数は最大レス数以下にしてください"
        }
        require(sort.isNotEmpty() && sort.length <= 64 && sort.none { it.isISOControl() }) { "ソートが不正です" }
        require(fuzzy.length <= 64 && fuzzy.none { it.isISOControl() }) { "あいまい検索が不正です" }
        val start = if (from.isEmpty()) null else LocalDate.parse(from)
        val end = if (to.isEmpty()) null else LocalDate.parse(to)
        require(start == null || end == null || !start.isAfter(end)) { "開始日は終了日以前にしてください" }
    }
}

data class ArchiveThread @JvmOverloads constructor(val title: String, val url: String, val replies: Int, val date: String, val board: String = "", val originalUrl: String = "")
data class ArchiveSortOption(val value: String, val label: String)
data class ArchiveFormOptions(val sorts: List<ArchiveSortOption>, val fuzzyValue: String?)
data class ArchivePage @JvmOverloads constructor(
    val items: List<ArchiveThread>, val total: Long, val page: Int, val hasNext: Boolean,
    val form: ArchiveFormOptions? = null,
    val pageSize: Int = ArchiveRepository.PAGE_SIZE,
    val totalCapped: Boolean = false
) {
    /** Page size is provider-specific. Range refers to server results,
     *  not the locally filtered upper-bound subset. */
    val totalPages: Int get() = if (total <= 0) 0 else
        ((total - 1) / pageSize + 1).coerceAtMost(ArchiveRepository.MAX_PAGE.toLong()).toInt()
    val firstIndex: Long get() = if (total <= 0 || page !in 1..totalPages) 0 else
        (page.toLong() - 1) * pageSize + 1
    val lastIndex: Long get() = if (firstIndex == 0L) 0 else
        minOf(total, page.toLong() * pageSize)
}

object ArchiveRepository {
    const val PAGE_SIZE = 50
    const val MAX_PAGE = 1000000
    private val count = Regex("検索結果[：:]\\s*([0-9,]+)\\s*件")
    private val titleCount = Regex("^(.*?)\\s*\\(([0-9,]+)\\)\\s*$")
    private val date = Regex("20\\d{2}/\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}:\\d{2}")
    private val edgePath = Regex("^/test/read\\.cgi/[A-Za-z0-9_]{1,60}/[0-9]{10,20}/?$")

    @JvmStatic
    fun url(query: ArchiveQuery, page: Int): String {
        query.validate()
        require(page in 1..MAX_PAGE)
        // Only known server parameters are sent. The optional upper bound is filtered locally.
        val parameters = linkedMapOf("keyword" to query.keyword.trim(), "keyword_exclude" to query.exclude.trim(),
            "NoR" to query.minimum, "start_date" to query.from, "end_date" to query.to,
            "onor" to "", "fuzzy" to query.fuzzy, "sort" to query.sort, "page" to page.toString())
        return ArchiveProvider.EDGE_ARCHIVE.url + "?" + parameters.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())
        }
    }

    @JvmOverloads
    fun search(provider: ArchiveProvider, query: ArchiveQuery, page: Int, nukoUserAgent: String = ""): ArchivePage {
        val target = ArchiveExternalSearch.url(provider, query, page)
        val charset = if (provider == ArchiveProvider.EDGE_ARCHIVE) StandardCharsets.UTF_8 else java.nio.charset.Charset.forName("windows-31j")
        val html = if (provider == ArchiveProvider.FIVECH_NUKO)
            HttpTransport.getForNuko(target, charset, nukoUserAgent)
        else if (provider == ArchiveProvider.OTHER_KYODEMO)
            HttpTransport.getForKyodemoArchive(target, charset, nukoUserAgent)
        else HttpTransport.get(target, charset)
        if (provider == ArchiveProvider.FIVECH_NUKO && html.isEmpty())
            throw NukoPageFormatException("ぬこのHTTP応答本文が空です / " + HttpTransport.lastDiagnostic())
        val result = when (provider) {
            ArchiveProvider.EDGE_ARCHIVE -> parse(html, page)
            ArchiveProvider.EDGE_KYODEMO -> ArchiveProviderParser.kyodemoEdge(html, page)
            ArchiveProvider.FIVECH_KYODEMO -> ArchiveProviderParser.kyodemoBoard(html, page, query.boardCode, query.boardName)
            ArchiveProvider.OTHER_KYODEMO -> try {
                ArchiveProviderParser.kyodemoOther(html, page, query.boardCode, query.boardName)
            } catch (failure: IOException) {
                throw KyodemoArchiveFormatException(failure.message.orEmpty() + " / " + HttpTransport.lastDiagnostic(), failure)
            }
            ArchiveProvider.FIVECH_NUKO -> try {
                ArchiveProviderParser.nuko(html, page, query.boardCode)
            } catch (failure: NukoPageFormatException) {
                throw NukoPageFormatException(failure.message.orEmpty() + " / " + HttpTransport.lastDiagnostic())
            }
        }
        return filterRange(result, query)
    }

    /** Read option values from the current site rather than guessing undocumented codes. */
    @JvmStatic
    fun parseForm(html: String): ArchiveFormOptions? = formOptions(Jsoup.parse(html))

    private fun formOptions(doc: org.jsoup.nodes.Document): ArchiveFormOptions? {
        val select = doc.selectFirst("select[name=sort]") ?: return null
        val choices = select.select("option").filterNot { it.hasAttr("disabled") }.mapNotNull {
            val label = it.text().trim()
            val value = if (it.hasAttr("value")) it.attr("value") else label
            if (label.isEmpty() || value.isEmpty() || value.length > 64 || value.any { c -> c.isISOControl() }) null
            else ArchiveSortOption(value, label)
        }.distinctBy { it.value }
        if (choices.isEmpty()) return null
        val checkbox = doc.selectFirst("input[name=fuzzy][type=checkbox]")
        val fuzzyValue = checkbox?.let { if (it.hasAttr("value")) it.attr("value") else "on" }
            ?.takeIf { it.isNotEmpty() && it.length <= 64 && it.none { c -> c.isISOControl() } }
        return ArchiveFormOptions(choices, fuzzyValue)
    }

    fun fetchForm(): ArchiveFormOptions =
        parseForm(HttpTransport.get(ArchiveProvider.EDGE_ARCHIVE.url, StandardCharsets.UTF_8))
            ?: throw IOException("サイトの検索条件を取得できませんでした")

    @JvmStatic
    fun filterRange(page: ArchivePage, query: ArchiveQuery): ArchivePage {
        if (query.minimum.isEmpty() && query.maximum.isEmpty()) return page
        val min = query.minimum.toIntOrNull() ?: 0
        val max = query.maximum.toIntOrNull() ?: Int.MAX_VALUE
        // Keep the original page count and navigation even when this page has no matches.
        return page.copy(items = page.items.filter { it.replies in min..max })
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
        return ArchivePage(threads.values.toList(), total, page, hasNext && threads.isNotEmpty(), formOptions(doc))
    }
}
