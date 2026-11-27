package com.chikuwadon.hissidroidplus

import java.net.URI

/** Resolve archive links against a validated official board address. */
object ArchiveOriginalLink {
    @JvmStatic fun kyodemo5ch(raw: String, board: String, address: String): String {
        if (!BoardFavorites.valid(board)) return raw
        return try {
            val source = URI.create(raw)
            val site = URI.create(address)
            val path = Regex("^/sdemo/r/([A-Za-z0-9_]{1,60})/(\\d{10})/?$").matchEntire(source.rawPath.orEmpty()) ?: return raw
            val host = site.host ?: return raw
            if (!source.scheme.equals("https", true) || !source.host.equals("www.kyodemo.net", true) ||
                source.rawUserInfo != null || source.port != -1 || source.rawQuery != null || source.rawFragment != null ||
                path.groupValues[1] != board || !(site.scheme.equals("https", true) || site.scheme.equals("http", true)) ||
                !(host.endsWith(".5ch.io") || host.endsWith(".5ch.net")) ||
                site.rawUserInfo != null || site.port != -1 || site.rawQuery != null || site.rawFragment != null ||
                site.rawPath !in listOf("/$board", "/$board/")) return raw
            "https://$host/test/read.cgi/$board/${path.groupValues[2]}/"
        } catch (_: IllegalArgumentException) { raw }
    }
}
