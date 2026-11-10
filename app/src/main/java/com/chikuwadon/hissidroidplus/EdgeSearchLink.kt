package com.chikuwadon.hissidroidplus

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** Real Edge site search and explicit thread-to-checker conversion, never fabricated keys. */
object EdgeSearchLink {
    private val KYODEMO_THREAD = Pattern.compile(
        "^/sdemo/r/e_e_([A-Za-z0-9_]+)/([0-9]{10,20})(?:/.*)?$"
    )
    private val BBS_THREAD = Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]+)/([0-9]{10,20})(?:/.*)?$"
    )
    private val BBS_SHORT = Pattern.compile(
        "^/([A-Za-z0-9_]+)/([0-9]{10,20})(?:/.*)?$"
    )

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    @JvmStatic
    fun searchUrlAnyDate(board: String?, id: String?): String {
        if (board == null || !board.matches(Regex("[A-Za-z0-9_]{1,60}"))) {
            throw IllegalArgumentException("Invalid board")
        }
        if (id == null || !id.matches(Regex("[A-Za-z0-9+./_-]{1,64}"))) {
            throw IllegalArgumentException("Invalid ID")
        }
        return "https://www.kyodemo.net/sdemo/b/e_e_" + board +
            "/?bs=hi&kt=k_&k=" + enc(id)
    }

    @JvmStatic
    fun searchUrl(board: String, date: String, id: String): String {
        CheckerQuery.validateDate(date)
        val day = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6)
        return searchUrlAnyDate(board, id) + "&fr=" + day + "&to=" + day
    }

    @JvmStatic
    fun checkerFromThread(board: String, date: String, id: String, clicked: String): String {
        return try {
            val uri = URI.create(clicked)
            val host = uri.host ?: return ""
            val path = uri.path ?: return ""
            if (uri.port != -1 || uri.userInfo != null) return ""
            val matched = when {
                host.equals("www.kyodemo.net", true) || host.equals("kyodemo.net", true) ->
                    KYODEMO_THREAD.matcher(path)
                host.equals("bbs.eddibb.cc", true) -> {
                    val regular = BBS_THREAD.matcher(path)
                    if (regular.matches()) regular else BBS_SHORT.matcher(path)
                }
                else -> return ""
            }
            if (!matched.matches() || board != matched.group(1)) return ""
            "https://www.kyodemo.net/sdemo/b/e_e_" + board +
                "/?hi=" + enc(id) + "&key=" + matched.group(2) + "&date=" + date
        } catch (_: Exception) {
            ""
        }
    }
}
