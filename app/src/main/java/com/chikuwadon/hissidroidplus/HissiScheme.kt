package com.chikuwadon.hissidroidplus

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Input from ChMate's custom URL: hissi:{host}/{board}/{id}.
 * An optional ?date=YYYYMMDD chooses a specific day; otherwise the caller
 * searches the recent seven days on 5ch or any date on Edge.
 * The origin is determined ONLY from the host, never the board name.
 */
class HissiScheme private constructor(
    @JvmField val site: CheckerQuery.Site,
    @JvmField val board: String,
    @JvmField val id: String,
    @JvmField val date: String
) {
    companion object {
        @JvmStatic
        fun parse(value: String?): HissiScheme {
            val uri = try {
                URI.create(value ?: throw IllegalArgumentException("リンクがありません"))
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("hissiリンクが不正です", e)
            }
            if (!uri.scheme.equals("hissi", ignoreCase = true) || !uri.isOpaque ||
                uri.rawFragment != null || uri.rawUserInfo != null) {
                throw IllegalArgumentException("hissiリンクの形式が不正です")
            }
            val raw = uri.rawSchemeSpecificPart ?: ""
            val first = raw.indexOf('/')
            val second = if (first < 0) -1 else raw.indexOf('/', first + 1)
            if (first <= 0 || second <= first + 1 || second == raw.lastIndex) {
                throw IllegalArgumentException("ホスト・板・IDを指定してください")
            }
            val host = raw.substring(0, first).lowercase(Locale.ROOT)
            val board = raw.substring(first + 1, second)
            val idAndQuery = raw.substring(second + 1)
            val marker = idAndQuery.indexOf('?')
            val encodedId = if (marker < 0) idAndQuery else idAndQuery.substring(0, marker)
            val query = if (marker < 0) "" else idAndQuery.substring(marker + 1)
            val date = if (query.isEmpty()) "" else {
                if (!query.matches(Regex("date=[0-9]{8}"))) {
                    throw IllegalArgumentException("日付パラメータが不正です")
                }
                query.substring("date=".length)
            }
            if (date.isNotEmpty()) CheckerQuery.validateDate(date)
            // URLDecoder treats '+' as a space; preserve literal ID '+' characters.
            val id = try {
                URLDecoder.decode(encodedId.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("IDのエンコードが不正です", e)
            }
            val site = when {
                host == "bbs.eddibb.cc" || host == "eddibb.cc" ||
                    host == "kyodemo.net" || host == "www.kyodemo.net" ->
                    CheckerQuery.Site.EDGE
                host == "5ch.net" || host.endsWith(".5ch.net") ||
                    host == "2ch.net" || host.endsWith(".2ch.net") ||
                    host == "bbspink.com" || host.endsWith(".bbspink.com") ->
                    CheckerQuery.Site.HISSI
                else -> throw IllegalArgumentException("未対応の掲示板ホストです")
            }
            // Validate before any network or Activity routing occurs.
            val validated = if (site == CheckerQuery.Site.EDGE) {
                CheckerQuery.edgeSearch(board, if (date.isEmpty()) "20260101" else date, id)
            } else {
                CheckerQuery.hissi(board, if (date.isEmpty()) "20260101" else date, id)
            }
            return HissiScheme(site, validated.board, validated.id, date)
        }
    }
}
