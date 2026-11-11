package com.chikuwadon.hissidroidplus

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

/** Immutable, validated search request; no Android or mod dependencies. */
class CheckerQuery private constructor(
    @JvmField val site: Site,
    @JvmField val board: String,
    @JvmField val id: String,
    @JvmField val date: String,
    @JvmField val key: String,
    @JvmField val url: String
) {
    enum class Site { HISSI, EDGE }

    companion object {
        private val BOARD = Pattern.compile("[A-Za-z0-9_]{1,60}")
        private val HISSI_PATH = Pattern.compile("^/read\\.php/([A-Za-z0-9_]+)/[^?#]*\\.html$")
        private val SHARED_BOARD = Pattern.compile("(?:read\\.cgi|read\\.php)/([A-Za-z0-9_]+)/")
        private val SHARED_DATE = Pattern.compile("([0-9]{4})/([0-9]{1,2})/([0-9]{1,2})")
        private val SHARED_ID = Pattern.compile("(?i)\\bID\\s*[:：]\\s*([A-Za-z0-9+./_-]+)")
        private val EDGE_PATH = Pattern.compile("^/sdemo/b/e_e_([A-Za-z0-9_]+)/?$")
        private val URL = Pattern.compile("https?://[^\\s<>'\\\"）]+", Pattern.CASE_INSENSITIVE)

        @JvmStatic
        fun hissi(board: String?, date: String?, id: String?): CheckerQuery {
            if (board == null || !BOARD.matcher(board).matches()) {
                throw IllegalArgumentException("板名が不正です")
            }
            checkDate(date)
            if (id == null || id.trim().isEmpty()) throw IllegalArgumentException("IDが不正です")
            val cleaned = id.trim().replaceFirst(Regex("(?i)^ID\\s*[:：]\\s*"), "").trim()
            if (cleaned.length > 80 || cleaned.isEmpty()) throw IllegalArgumentException("IDが不正です")
            return CheckerQuery(Site.HISSI, board, cleaned, date!!, "", "")
        }

        /** Edge searches received from an external client lack a checker page key. */
        @JvmStatic
        fun edgeSearch(board: String?, date: String?, id: String?): CheckerQuery {
            if (board == null || !BOARD.matcher(board).matches()) {
                throw IllegalArgumentException("エッヂの板名が不正です")
            }
            checkDate(date)
            if (id == null) throw IllegalArgumentException("エッヂのIDが不正です")
            val cleaned = id.trim().replaceFirst(Regex("(?i)^ID\\s*[:：]\\s*"), "").trim()
            if (!cleaned.matches(Regex("[A-Za-z0-9+./_-]{1,64}"))) {
                throw IllegalArgumentException("エッヂのIDが不正です")
            }
            return CheckerQuery(Site.EDGE, board, cleaned, date!!, "", "")
        }

        @JvmStatic
        fun isKnownEdgeBoard(board: String?): Boolean = board.equals("liveedge", ignoreCase = true)

        @JvmStatic
        fun parseUrl(input: String?): CheckerQuery {
            if (input == null) throw IllegalArgumentException("URLがありません")
            val url = findUrl(input)
            val uri = try { URI.create(url) }
                catch (error: Exception) { throw IllegalArgumentException("URLが不正です", error) }
            val host = uri.host
            if (host == null || uri.rawUserInfo != null || uri.port != -1) {
                throw IllegalArgumentException("接続先が不正です")
            }
            val path = uri.path ?: ""
            if ((host.equals("hissi.org", true) || host.equals("www.hissi.org", true)) &&
                (uri.scheme == "http" || uri.scheme == "https")
            ) {
                val matched = HISSI_PATH.matcher(path)
                if (!matched.matches()) throw IllegalArgumentException("hissi.orgの結果URLを指定してください")
                return CheckerQuery(Site.HISSI, matched.group(1), "", "", "", url)
            }
            if ((host.equals("kyodemo.net", true) || host.equals("www.kyodemo.net", true)) &&
                uri.scheme == "https"
            ) {
                val matched = EDGE_PATH.matcher(path)
                if (!matched.matches()) throw IllegalArgumentException("エッヂ用のURLではありません")
                val name = matched.group(1)
                val bs = arg(uri.rawQuery, "bs")
                val search = arg(uri.rawQuery, "k")
                if (bs == "hi" && search != null &&
                    search.matches(Regex("(?i)id:[A-Za-z0-9+./_-]{1,64}"))
                ) {
                    val searchedId = search.substring(3)
                    val canonicalSearch = "https://www.kyodemo.net/sdemo/b/e_e_" + name +
                        "/?bs=hi&k=" + enc("id:" + searchedId)
                    return CheckerQuery(Site.EDGE, name, searchedId, "", "", canonicalSearch)
                }
                val id = arg(uri.rawQuery, "hi")
                val key = arg(uri.rawQuery, "key")
                val date = arg(uri.rawQuery, "date")
                if (id == null || !id.matches(Regex("[A-Za-z0-9+./_-]{1,64}")) ||
                    key == null || !key.matches(Regex("\\d{10,20}"))
                ) throw IllegalArgumentException("hiまたはkeyが不正です")
                checkDate(date)
                var canonical = "https://www.kyodemo.net/sdemo/b/e_e_" + name +
                    "/?hi=" + enc(id) + "&key=" + key + "&date=" + date
                val token = arg(uri.rawQuery, "c")
                if (token != null && token.matches(Regex("[A-Za-z0-9_-]{1,48}"))) {
                    canonical += "&c=" + enc(token)
                }
                return CheckerQuery(Site.EDGE, name, id, date!!, key, canonical)
            }
            throw IllegalArgumentException("未対応のURLです")
        }

        /** Identify the originating service by the true host, not a board name. */
        @JvmStatic
        fun sourceFromThreadUrl(input: String?): Site? {
            if (input == null) return null
            return try {
                val uri = URI.create(input.replace(Regex("[.,。]+$"), ""))
                if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return null
                if (uri.rawUserInfo != null || uri.port != -1) return null
                val host = uri.host ?: return null
                val path = uri.path ?: return null
                if (host.equals("bbs.eddibb.cc", true) &&
                    path.matches(Regex("^/(?:test/read\\.cgi/)?[A-Za-z0-9_]+/[0-9]{10,20}(?:/.*)?$"))
                ) return Site.EDGE
                if ((host.equals("kyodemo.net", true) || host.equals("www.kyodemo.net", true)) &&
                    path.matches(Regex("^/sdemo/r/e_e_[A-Za-z0-9_]+/[0-9]{10,20}(?:/.*)?$"))
                ) return Site.EDGE
                val lower = host.lowercase(Locale.ROOT)
                if ((lower == "5ch.net" || lower.endsWith(".5ch.net") ||
                    lower == "2ch.net" || lower.endsWith(".2ch.net") ||
                    lower == "bbspink.com" || lower.endsWith(".bbspink.com")) &&
                    path.matches(Regex("^/test/read\\.cgi/[A-Za-z0-9_]+/[0-9]{10,20}(?:/.*)?$"))
                ) Site.HISSI else null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun sourceFromShare(text: String, board: String): Site? {
            val urls = URL.matcher(text.replace("&amp;", "&"))
            while (urls.find()) {
                val link = urls.group()
                val site = sourceFromThreadUrl(link) ?: continue
                try {
                    val path = URI.create(link).path ?: continue
                    if (site == Site.EDGE && path.contains("/e_e_" + board + "/")) return site
                    if (path.contains("/" + board + "/")) return site
                } catch (_: IllegalArgumentException) {
                    // Keep inspecting later URLs.
                }
            }
            return null
        }

        /** Bridge for ChMate, Haiagaru and existing external URLs/shares. */
        @JvmStatic
        fun fromExternalPayload(
            data: String?, shared: String?, board: String?, date: String?, id: String?
        ): CheckerQuery {
            if (!data.isNullOrBlank()) {
                return when (sourceFromThreadUrl(data.trim())) {
                    Site.EDGE -> edgeSearch(board, date, id)
                    Site.HISSI -> hissi(board, date, id)
                    else -> fromSharedText(data)
                }
            }
            if (!shared.isNullOrBlank()) {
                return when (sourceFromThreadUrl(shared.trim())) {
                    Site.EDGE -> edgeSearch(board, date, id)
                    Site.HISSI -> hissi(board, date, id)
                    else -> fromSharedText(shared)
                }
            }
            return if (isKnownEdgeBoard(board)) edgeSearch(board, date, id) else hissi(board, date, id)
        }

        @JvmStatic
        fun fromSharedText(shared: String?): CheckerQuery {
            if (shared.isNullOrEmpty()) throw IllegalArgumentException("共有データがありません")
            if (shared.contains("kyodemo.net/sdemo/b/") || shared.contains("hissi.org/read.php/")) {
                return parseUrl(shared)
            }
            val board = SHARED_BOARD.matcher(shared)
            val date = SHARED_DATE.matcher(shared)
            val id = SHARED_ID.matcher(shared)
            if (!board.find() || !date.find() || !id.find()) {
                throw IllegalArgumentException("板・日付・IDを共有テキストから取得できません")
            }
            val day = String.format(Locale.ROOT, "%s%02d%02d", date.group(1),
                date.group(2).toInt(), date.group(3).toInt())
            val code = board.group(1)
            val name = id.group(1)
            return when (sourceFromShare(shared, code)) {
                Site.EDGE -> edgeSearch(code, day, name)
                Site.HISSI -> hissi(code, day, name)
                else -> if (isKnownEdgeBoard(code)) edgeSearch(code, day, name) else hissi(code, day, name)
            }
        }

        private fun findUrl(shared: String): String {
            val matcher = URL.matcher(shared.replace("&amp;", "&"))
            if (!matcher.find()) throw IllegalArgumentException("URLが見つかりません")
            return matcher.group().replace(Regex("[.,。]+$"), "")
        }

        private fun arg(query: String?, name: String): String? {
            if (query == null) return null
            for (segment in query.split("&")) {
                val equal = segment.indexOf('=')
                if (equal <= 0 || segment.substring(0, equal) != name) continue
                return URLDecoder.decode(segment.substring(equal + 1), StandardCharsets.UTF_8.name())
            }
            return null
        }

        private fun enc(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

        @JvmStatic
        fun validateDate(date: String?) { checkDate(date) }

        private fun checkDate(date: String?) {
            if (date == null || !date.matches(Regex("\\d{8}"))) {
                throw IllegalArgumentException("日付はYYYYMMDD形式にしてください")
            }
            try {
                LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (error: DateTimeParseException) {
                throw IllegalArgumentException("日付が不正です", error)
            }
        }
    }
}
