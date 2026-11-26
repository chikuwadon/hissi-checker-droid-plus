package com.chikuwadon.hissidroidplus

import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Parsed from the saved first and second search pages, without executing site scripts. */
object ArchiveProviderParser {
    private val kyodemoCount = Regex("([0-9,]+)\\s*スレ\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)")
    private val nukoCount = Regex("検索結果\\s*\\(\\s*([0-9,]+)\\s*[ｽス][ﾚレ]")
    private val kyodemoPath = Regex("^/sdemo/r/([A-Za-z0-9_]{1,60})/(\\d{10})/?$")
    private val nukoPath = Regex("^/r/-/([A-Za-z0-9_]{1,60})/(\\d{10})/?$")
    private val titleCount = Regex("^(.*?)\\s*\\(([0-9,]+)\\)\\s*$")
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"))
    private fun timestamp(key: String): String = dateFormat.format(Instant.ofEpochSecond(key.toLong()))
    private fun safeUri(raw: String, host: String): URI? = try {
        URI.create(raw).takeIf { it.scheme.equals("https", true) && it.host.equals(host, true) &&
            it.rawUserInfo == null && it.port == -1 && it.rawFragment == null }
    } catch (_: IllegalArgumentException) { null }

    @JvmStatic
    fun kyodemo(html: String, page: Int): ArchivePage {
        val doc = Jsoup.parse(html, "https://www.kyodemo.net/sdemo/s")
        val count = kyodemoCount.find(doc.text()) ?: throw IOException("Kyodemoの検索結果件数を確認できませんでした")
        val total = count.groupValues[1].replace(",", "").toLong()
        val current = count.groupValues[2].toInt()
        val last = count.groupValues[3].toInt()
        if (total > 0 && current != page) throw IOException("指定ページと取得ページが一致しません")
        val rows = LinkedHashMap<String, ArchiveThread>()
        for (anchor in doc.select("article.panel-block a[href]")) {
            val uri = safeUri(anchor.absUrl("href"), "www.kyodemo.net") ?: continue
            val path = kyodemoPath.matchEntire(uri.path.orEmpty()) ?: continue
            if (uri.rawQuery != null) continue
            val title = anchor.selectFirst("h2.cltitle")?.wholeText()?.replaceFirst(Regex("^\\s*\\d+[:：]\\s*"), "")?.trim().orEmpty()
            val replies = anchor.selectFirst("small.right_i b")?.text()?.replace(",", "")?.toIntOrNull() ?: continue
            if (title.isBlank()) continue
            val board = anchor.selectFirst("small.left_i span")?.text().orEmpty()
            val link = uri.toASCIIString()
            rows.putIfAbsent(link, ArchiveThread(title, link, replies, timestamp(path.groupValues[2]), board))
        }
        if (total > 0 && rows.isEmpty()) throw IOException("Kyodemoのスレッド一覧を解析できませんでした")
        return ArchivePage(rows.values.toList(), total, page, page < last, pageSize = 50)
    }

    @JvmStatic
    fun nuko(html: String, page: Int): ArchivePage {
        val doc = Jsoup.parse(html, "https://n2ch.net/q/-/")
        val total = nukoCount.find(doc.text())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
            ?: throw IOException("ぬこの検索結果件数を確認できませんでした")
        val range = Regex("([0-9,]+)\\s*[-～〜]\\s*([0-9,]+)件目").find(doc.text())
        if (total > 0 && range?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() != (page.toLong() - 1) * 20 + 1) {
            throw IOException("指定ページと取得ページが一致しません")
        }
        val rows = LinkedHashMap<String, ArchiveThread>()
        for (anchor in doc.select("a[href]")) {
            val uri = safeUri(anchor.absUrl("href"), "n2ch.net") ?: continue
            val path = nukoPath.matchEntire(uri.path.orEmpty()) ?: continue
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
