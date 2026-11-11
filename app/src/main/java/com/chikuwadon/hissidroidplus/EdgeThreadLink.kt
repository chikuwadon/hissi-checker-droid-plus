package com.chikuwadon.hissidroidplus

import java.net.URI
import java.util.regex.Pattern

/** Normalize known Edge URLs to the ChMate-compatible /test/read.cgi/ form. */
object EdgeThreadLink {
    private val ARCHIVE = Pattern.compile(
        "^/sdemo/r/e_e_([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$"
    )
    private val SHORT = Pattern.compile(
        "^/([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$"
    )
    private val READ_CGI = Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$"
    )

    @JvmStatic
    fun original(input: String?): String {
        if (input == null) return ""
        return try {
            val uri = URI.create(input)
            if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return input
            if (uri.rawUserInfo != null || uri.port != -1) return input
            val host = uri.host ?: return input
            val path = uri.path ?: return input
            val matched = when {
                host.equals("kyodemo.net", true) || host.equals("www.kyodemo.net", true) ->
                    ARCHIVE.matcher(path)
                host.equals("bbs.eddibb.cc", true) -> {
                    val short = SHORT.matcher(path)
                    if (short.matches()) short else READ_CGI.matcher(path)
                }
                else -> return input
            }
            if (!matched.matches()) return input
            buildString {
                append("https://bbs.eddibb.cc/test/read.cgi/")
                append(matched.group(1))
                append('/')
                append(matched.group(2))
                append('/')
                matched.group(3)?.let { append(it) }
            }
        } catch (_: Exception) {
            input
        }
    }
}
