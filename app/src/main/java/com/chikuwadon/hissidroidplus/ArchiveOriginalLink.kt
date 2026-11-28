package com.chikuwadon.hissidroidplus

import java.net.URI
import org.jsoup.Jsoup

/** Resolve archive links against a validated official board address. */
object ArchiveOriginalLink {
    @JvmStatic fun kyodemo5ch(raw: String, board: String, address: String): String = original5ch(raw, board, address, false)

    @JvmStatic fun nuko5ch(raw: String, board: String, address: String): String = original5ch(raw, board, address, true)

    @JvmStatic fun boardCode(raw: String): String = try {
        val uri = URI.create(raw)
        val path = when (uri.host) {
            "n2ch.net" -> Regex("^/r/-/([A-Za-z0-9_]{1,60})/[0-9]{10}/?$")
            "www.kyodemo.net" -> Regex("^/sdemo/r/([A-Za-z0-9_]{1,60})/[0-9]{10}/?$")
            else -> null
        }
        path?.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1).orEmpty()
    } catch (_: IllegalArgumentException) { "" }

    private val otherPath = Regex("^/sdemo/r/([A-Za-z0-9_]{1,60})/(\\d{1,20})/?$")
    private fun otherSource(raw: String): MatchResult? = runCatching {
        val uri = URI.create(raw)
        if (uri.scheme != "https" || uri.host != "www.kyodemo.net" || uri.port != -1 ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        otherPath.matchEntire(uri.rawPath.orEmpty())?.takeIf { KyodemoOtherBoards.validBoard(it.groupValues[1]) }
    }.getOrNull()

    @JvmStatic fun hasTimestampKey(code: String): Boolean =
        listOf("m_", "s_", "o_", "e_j_", "e_p_", "e_o_").any { code.startsWith(it) }

    /** Known original formats, verified from the board sites / Kyodemo thread source. */
    @JvmStatic fun kyodemoOther(raw: String): String {
        val m = otherSource(raw) ?: return ""
        val board = m.groupValues[1]
        val key = m.groupValues[2]
        val shitaraba = Regex("^s_([A-Za-z0-9]+)_(\\d+)$").matchEntire(board)
        if (shitaraba != null && key.length == 10)
            return "https://jbbs.shitaraba.net/bbs/read.cgi/" + shitaraba.groupValues[1] + "/" + shitaraba.groupValues[2] + "/$key/"
        if (key.length == 10) {
            val suffix = board.substringAfter('_')
            if (board.startsWith("m_")) return "https://machi.to/bbs/read.cgi/$suffix/$key/"
            if (board.startsWith("e_p_")) return "https://bbs.punipuni.eu/test/read.cgi/" + board.removePrefix("e_p_") + "/$key/"
            if (board.startsWith("e_j_")) return "https://bbs.jpnkn.com/test/read.cgi/" + board.removePrefix("e_j_") + "/$key/"
            if (board.startsWith("e_o_")) return "https://kenmo.org/test/read.cgi/" + board.removePrefix("e_o_") + "/$key/"
        }
        return ""
    }

    /** Accept only an explicitly labelled source link matching the archive thread key.
     * Do not follow arbitrary links in posts or use this URL for an HTTP fetch. */
    @JvmStatic fun originalFromDetail(raw: String, html: String): String {
        val source = otherSource(raw) ?: return ""
        val key = source.groupValues[2]
        val doc = Jsoup.parse(html, raw)
        val candidates = linkedSetOf<String>()
        for (a in doc.select("a[href]")) {
            if (a.closest("article, blockquote, .res, .res-body, .post, .post-body") != null) continue
            val label = a.text().trim()
            val before = (a.previousSibling() as? org.jsoup.nodes.TextNode)?.text().orEmpty().trim()
            if (!label.startsWith("元スレ") && !before.matches(Regex(".*元スレ(?:ッド)?\\s*[:：]?\\s*$"))) continue
            val uri = runCatching { URI.create(a.absUrl("href")) }.getOrNull() ?: continue
            if (!ExternalUrlPolicy.isWebUrl(uri.toString()) || uri.port != -1 || uri.rawQuery != null ||
                uri.rawFragment != null || uri.host.equals("www.kyodemo.net", true) ||
                uri.host.equals("kyodemo.net", true)) continue
            val host = uri.host.lowercase(java.util.Locale.ROOT)
            // A public DNS hostname and a thread-shaped path with the identical key are required.
            if (!host.matches(Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) ||
                !host.substringAfterLast('.').matches(Regex("[a-z]{2,63}")) ||
                host.endsWith(".local") || host.endsWith(".localhost") || host.endsWith(".internal")) continue
            if (!uri.rawPath.orEmpty().matches(Regex("(?:/test/read\\.cgi/[A-Za-z0-9_]+/|/bbs/read\\.cgi/(?:[A-Za-z0-9_]+/)+|/[A-Za-z0-9_]+/res/)" +
                    Regex.escape(key) + "(?:/|\\.htm[l]?)?"))) continue
            candidates.add(uri.toASCIIString())
        }
        return candidates.singleOrNull().orEmpty()
    }

    @JvmStatic fun resolveOther(raw: String): String {
        val known = kyodemoOther(raw)
        if (known.isNotEmpty()) return known
        if (otherSource(raw) == null) return ""
        return originalFromDetail(raw, HttpTransport.get(raw, java.nio.charset.Charset.forName("windows-31j")))
    }

    private fun original5ch(raw: String, board: String, address: String, nuko: Boolean): String {
        if (!BoardFavorites.valid(board)) return raw
        return try {
            val source = URI.create(raw)
            val site = URI.create(address)
            val prefix = if (nuko) "/r/-/" else "/sdemo/r/"
            val path = Regex("^" + prefix + "([A-Za-z0-9_]{1,60})/(\\d{10})/?$").matchEntire(source.rawPath.orEmpty()) ?: return raw
            val host = site.host ?: return raw
            if (!source.scheme.equals("https", true) || !source.host.equals(if (nuko) "n2ch.net" else "www.kyodemo.net", true) ||
                source.rawUserInfo != null || source.port != -1 || (!nuko && source.rawQuery != null) || source.rawFragment != null ||
                path.groupValues[1] != board || !(site.scheme.equals("https", true) || site.scheme.equals("http", true)) ||
                !(host.endsWith(".5ch.io") || host.endsWith(".5ch.net")) ||
                site.rawUserInfo != null || site.port != -1 || site.rawQuery != null || site.rawFragment != null ||
                site.rawPath !in listOf("/$board", "/$board/")) return raw
            "https://$host/test/read.cgi/$board/${path.groupValues[2]}/"
        } catch (_: IllegalArgumentException) { raw }
    }
}
