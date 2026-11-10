package com.chikuwadon.hissidroidplus

import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Resolve a ChMate Edge board/date/ID to a genuine checker page.
 * Never invent the site's thread key or reroute an Edge ID to hissi.org.
 */
object EdgeInboundSearch {
    @JvmStatic
    @Throws(IOException::class)
    fun resolve(board: String, date: String, id: String): CheckerQuery? {
        val searchUrl = EdgeSearchLink.searchUrl(board, date, id)
        val html = HttpTransport.get(searchUrl, StandardCharsets.UTF_8)
        val matches = EdgeSearchResults.parse(html, searchUrl, board, date, id)
        var unverified: CheckerQuery? = null
        var inspected = 0
        for (item in matches) {
            if (inspected++ >= 6) break
            val candidate = try {
                CheckerQuery.parseUrl(item.checkerUrl)
            } catch (_: IllegalArgumentException) {
                continue
            }
            try {
                val page = EdgeAdapter().loadForVerification(candidate)
                if (page.posts.isEmpty()) {
                    if (unverified == null) unverified = candidate
                } else if (EdgeSearchValidation.matchingPosts(page, date, id).posts.isNotEmpty()) {
                    return candidate
                }
            } catch (_: IOException) {
                // Native network requests may fail while WebView can still load the site.
                if (unverified == null) unverified = candidate
            }
        }
        return unverified
    }
}
