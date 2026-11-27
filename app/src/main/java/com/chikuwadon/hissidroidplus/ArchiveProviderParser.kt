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

    private fun parseKyodemo(html: String, page: Int, code: String, name: String): ArchivePage {
        val doc = Jsoup.parse(html, "https://www.kyodemo.net/sdemo/")
        val count = kyodemoCount.find(doc.text()) ?: throw IOException("Kyodemoの検索結果件数を確認できませんでした")
        val total = count.groupValues[1].replace(",", "").toLong()
        val capped = count.groupValues[2] == "+"
        val current = count.groupValues[3].toInt()
        val last = count.groupValues[4].toInt()
        if (total > 0 && current != page) throw IOException("指定ページと取得ページが一致しません")
        val rows = LinkedHashMap<String, ArchiveThread>()
        for (anchor in doc.select("article.panel-block a[href]")) {
            val uri = safeUri(anchor.absUrl("href"), "www.kyodemo.net") ?: continue
            val path = kyodemoPath.matchEntire(uri.path.orEmpty()) ?: continue
            if (uri.rawQuery != null || path.groupValues[1] != code) continue
            val title = anchor.selectFirst("h2.cltitle")?.wholeText()?.replaceFirst(resultNumber, "")?.trim().orEmpty()
            val replies = anchor.selectFirst("small.right_i b")?.text()?.replace(",", "")?.toIntOrNull() ?: continue
            if (title.isBlank()) continue
            val board = name
            val link = uri.toASCIIString()
            rows.putIfAbsent(link, ArchiveThread(title, link, replies, timestamp(path.groupValues[2]), board))
        }
        if (total > 0 && rows.isEmpty()) throw IOException("Kyodemoのスレッド一覧を解析できませんでした")
        return ArchivePage(rows.values.toList(), total, page, page < last, pageSize = 50, totalCapped = capped)
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
