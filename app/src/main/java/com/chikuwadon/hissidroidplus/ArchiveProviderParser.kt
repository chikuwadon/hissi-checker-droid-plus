package com.chikuwadon.hissidroidplus

import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NukoPageFormatException(message: String) : IOException(message)

/** Parsed from the saved first and second search pages, without executing site scripts. */
object ArchiveProviderParser {
    private val kyodemoCount = Regex("([0-9,]+)(\\+?)\\s*スレ\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)")
    private val otherCount = Regex("([0-9,]+)(\\+?)\\s*(?:スレッド|スレ|件)\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)")
    private val countOnly = Regex("(?:検索結果\\s*[:：]?\\s*)?([0-9,]+)(\\+?)\\s*(?:スレッド|スレ|件)(?:\\s*(?:ヒット|該当))?")
    private val explicitEmpty = Regex("(?:該当(?:する)?(?:スレッド|スレ|検索結果|データ)(?:が|は)?(?:ありません|見つかりません)|検索結果\\s*[:：]?\\s*0\\s*(?:スレッド|スレ|件)|0\\s*(?:スレッド|スレ)\\s*\\(\\s*0\\s*/\\s*0\\s*\\))")
    private val nukoCount = Regex("検索結果\\s*[:：]?\\s*\\(\\s*([0-9,]+)\\s*スレ")
    private fun normalizedNukoText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    private val kyodemoPath = Regex("^/sdemo/r/([A-Za-z0-9_]{1,60})/(\\d{10})/?$")
    private val nukoPath = Regex("^/r/-/([A-Za-z0-9_]{1,60})/(\\d{10})/?$")
    private val titleCount = Regex("^(.*?)\\s*\\(([0-9,]+)\\)\\s*$")
    private val resultNumber = Regex("^\\s*\\d+[:：]\\s*")
    private val nukoRange = Regex("([0-9,]+)\\s*[-～〜]\\s*([0-9,]+)件目")
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"))
    private fun timestamp(key: String): String = dateFormat.format(Instant.ofEpochSecond(key.toLong()))
    private fun safeUri(raw: String, host: String): URI? = try {
        URI.create(raw).takeIf { it.scheme.equals("https", true) && it.host.equals(host, true) &&
            it.rawUserInfo == null && it.port == -1 && it.rawFragment == null }
    } catch (_: IllegalArgumentException) { null }

    @JvmStatic
    fun kyodemoEdge(html: String, page: Int): ArchivePage = parseKyodemo(html, page, "e_e_liveedge", "エッヂ")

    @JvmStatic
    fun kyodemoBoard(html: String, page: Int, code: String, name: String): ArchivePage {
        require(code.matches(Regex("[A-Za-z0-9_]{1,60}")))
        return parseKyodemo(html, page, code, name.ifBlank { code })
    }

    @JvmStatic
    fun kyodemoOther(html: String, page: Int, code: String, name: String): ArchivePage {
        require(KyodemoOtherBoards.validBoard(code))
        return parseKyodemo(html, page, code, name.ifBlank { code }, true)
    }

    private fun parseKyodemo(html: String, page: Int, code: String, name: String, other: Boolean = false): ArchivePage {
        val doc = Jsoup.parse(html, "https://www.kyodemo.net/sdemo/")
        val summary = doc.clone().apply { select("article.panel-block, h2.cltitle, script, style").remove() }
        val summaryText = Normalizer.normalize(summary.text(), Normalizer.Form.NFKC)
        val count = kyodemoCount.find(summaryText) ?: if (other) otherCount.find(summaryText) else null
        val total = count?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        val capped = count?.groupValues?.get(2) == "+"
        val current = count?.groupValues?.get(3)?.toIntOrNull()
        val last = count?.groupValues?.get(4)?.toIntOrNull()
        if (total != null && total > 0 && current != page) throw IOException("指定ページと取得ページが一致しません")
        if (!other && total == null) throw IOException("Kyodemoの検索結果件数を確認できませんでした")
        val rows = LinkedHashMap<String, ArchiveThread>()
        for (anchor in doc.select("article.panel-block a[href]")) {
            val uri = safeUri(anchor.absUrl("href"), "www.kyodemo.net") ?: continue
            val path = (if (other) Regex("^/sdemo/r/([A-Za-z0-9_]{1,60})/(\\d{1,20})/?$") else kyodemoPath).matchEntire(uri.path.orEmpty()) ?: continue
            if (uri.rawQuery != null || path.groupValues[1] != code) continue
            val title = anchor.selectFirst("h2.cltitle")?.wholeText()?.replaceFirst(resultNumber, "")?.trim().orEmpty()
            val row = anchor.closest("article.panel-block") ?: anchor
            val replies = (anchor.selectFirst("small.right_i b") ?: row.selectFirst(".right_i b"))
                ?.text()?.replace(",", "")?.toIntOrNull()
                ?: if (other) Regex("^\\s*([0-9,]+)(?:\\s|$)").find(row.selectFirst(".right_i")?.text().orEmpty())
                    ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() else null
            if (replies == null) continue
            if (title.isBlank()) continue
            val board = name
            val link = uri.toASCIIString()
            val key = path.groupValues[2]
            val time = if (!other) timestamp(key) else KyodemoDates.created(anchor)
                .ifEmpty { if (!code.startsWith("f_") && key.length == 10 && code.startsWith("o_")) runCatching { timestamp(key) }.getOrDefault("") else "" }
            rows.putIfAbsent(link, ArchiveThread(title, link, replies, time, board))
        }
        if (total != null && last != null) {
            if (total > 0 && rows.isEmpty()) throw IOException("Kyodemoのスレッド一覧を解析できませんでした")
            return ArchivePage(rows.values.toList(), total, page, page < last, pageSize = 50, totalCapped = capped)
        }
        // Different board families can omit the standard count/pagination heading.
        // Never accept an ordinary board index or an access-denied page as a search result.
        val searchPage = doc.title().contains("検索結果") || summaryText.contains("全ログ検索結果") ||
            summaryText.contains("過去ログ検索結果") || summaryText.contains("検索結果")
        val headingCount = if (searchPage) countOnly.find(summaryText) else null
        val reported = headingCount?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        if (rows.isEmpty()) {
            if (page == 1 && (explicitEmpty.containsMatchIn(summaryText) || (searchPage && reported == 0L)))
                return ArchivePage(emptyList(), 0, page, false, pageSize = 50)
            throw IOException("Kyodemoの検索結果件数を確認できませんでした / 検索結果見出し=$searchPage / スレリンク=" +
                doc.select("a[href*=/sdemo/r/]").size)
        }
        if (!searchPage) throw IOException("Kyodemoの検索結果ページを確認できませんでした")
        val boardPath = Regex("^/sdemo/b/" + Regex.escape(code) + "/(\\d+)?/?$")
        val pageLinks = summary.select("a[href]").mapNotNull { a ->
            val uri = safeUri(a.absUrl("href"), "www.kyodemo.net") ?: return@mapNotNull null
            if (uri.rawQuery.orEmpty().split('&').none { it.startsWith("k=") }) return@mapNotNull null
            val offset = boardPath.matchEntire(uri.path.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
                ?: return@mapNotNull null
            if (offset < 1 || (offset - 1) % 50 != 0L) return@mapNotNull null
            ((offset - 1) / 50 + 1).takeIf { it <= ArchiveRepository.MAX_PAGE }?.toInt()
        }
        val firstRow = doc.selectFirst("article.panel-block h2.cltitle")?.text()?.let {
            Regex("^\\s*(\\d+)[:：]").find(it)?.groupValues?.get(1)?.toLongOrNull()
        }
        if (page > 1 && firstRow != (page.toLong() - 1) * 50 + 1)
            throw IOException("指定ページと取得ページが一致しません")
        val observed = (page.toLong() - 1) * 50 + rows.size
        val nextPage = pageLinks.any { it > page }
        val estimated = maxOf(observed, pageLinks.maxOrNull()?.let { (it.toLong() - 1) * 50 + 1 } ?: 0L)
        if (reported != null && reported < observed) throw IOException("Kyodemoの検索結果件数が一致しません")
        val found = reported ?: estimated
        val hasNext = nextPage || found > page.toLong() * 50
        return ArchivePage(rows.values.toList(), found, page, hasNext, pageSize = 50,
            totalCapped = headingCount?.groupValues?.get(2) == "+" || reported == null && nextPage)
    }

    @JvmStatic
    @JvmOverloads
    fun nuko(html: String, page: Int, boardCode: String = ""): ArchivePage {
        val doc = Jsoup.parse(html, "https://n2ch.net/q/-/")
        val plain = normalizedNukoText(doc.text())
        val total = nukoCount.find(plain)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
            ?: throw NukoPageFormatException("ぬこの検索結果件数を確認できませんでした" +
                " / 検索フォーム=" + (doc.selectFirst("input[name=q]") != null) +
                " / 件数見出し=" + plain.contains("検索結果") +
                " / スレリンク=" + doc.select("a[href*=/r/-/]").size)
        val range = nukoRange.find(plain)
        if (total > 0 && range?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() != (page.toLong() - 1) * 20 + 1) {
            throw IOException("指定ページと取得ページが一致しません")
        }
        val rows = LinkedHashMap<String, ArchiveThread>()
        for (anchor in doc.select("a[href]")) {
            val uri = safeUri(anchor.absUrl("href"), "n2ch.net") ?: continue
            val path = nukoPath.matchEntire(uri.path.orEmpty()) ?: continue
            if (boardCode.isNotEmpty() && path.groupValues[1] != boardCode) continue
            val matched = titleCount.matchEntire(anchor.text()) ?: continue
            val title = matched.groupValues[1].trim()
            val replies = matched.groupValues[2].replace(",", "").toIntOrNull() ?: continue
            if (title.isEmpty()) continue
            val board = (anchor.nextSibling() as? org.jsoup.nodes.TextNode)?.text()?.trim()?.removePrefix("[")?.removeSuffix("]").orEmpty()
            val link = "https://n2ch.net" + uri.path + "?guid=ON&rc=" + replies
            rows.putIfAbsent(link, ArchiveThread(title, link, replies, timestamp(path.groupValues[2]), board))
        }
        if (total > 0 && rows.isEmpty()) throw IOException("ぬこのスレッド一覧を解析できませんでした")
        return ArchivePage(rows.values.toList(), total, page, page.toLong() * 20 < total, pageSize = 20)
    }
}
