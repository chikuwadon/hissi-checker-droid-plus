package com.chikuwadon.hissidroidplus

import java.util.Locale
import java.util.regex.Pattern

/**
 * Verify date and ID in real post headers, not the query or site page title.
 * Existing externally opened checker links retain their previous behavior.
 */
object EdgeSearchValidation {
    private val DATE = Pattern.compile("(?<!\\d)(20\\d{2})[/-](\\d{1,2})[/-](\\d{1,2})(?!\\d)")
    private val ID = Pattern.compile(
        "(?i)(?:^|[^A-Za-z0-9+./_-])ID\\s*[:：]\\s*([A-Za-z0-9+./_-]{1,64})(?=$|[^A-Za-z0-9+./_-])"
    )

    @JvmStatic
    fun matches(post: CheckerResult.Post?, date: String?, id: String?): Boolean {
        if (post == null || date == null || id == null) return false
        val meta = post.meta ?: return false
        val matchedId = ID.matcher(meta)
        var verifiedId = false
        while (matchedId.find()) {
            if (id == matchedId.group(1)) {
                verifiedId = true
                break
            }
        }
        if (!verifiedId) return false
        val days = DATE.matcher(meta)
        while (days.find()) {
            val actual = String.format(
                Locale.ROOT, "%04d%02d%02d",
                days.group(1).toInt(), days.group(2).toInt(), days.group(3).toInt()
            )
            if (date == actual) return true
        }
        return false
    }

    @JvmStatic
    fun matchingPosts(source: CheckerResult, date: String, id: String): CheckerResult {
        CheckerQuery.validateDate(date)
        val verified = CheckerResult()
        verified.rank = source.rank
        verified.sourceUrl = source.sourceUrl
        verified.diagnostic = source.diagnostic
        verified.hourlyCounts = null
        for (post in source.posts) {
            if (!matches(post, date, id)) continue
            verified.posts.add(post)
            val link = post.link
            if (!link.isNullOrEmpty()) {
                verified.threads.putIfAbsent(
                    PostIndex.openableThreadUrl(link),
                    if (post.title.isEmpty()) "スレッド" else post.title
                )
            }
        }
        verified.reportedPostCount = verified.posts.size
        verified.reportedThreadCount = verified.threads.size
        verified.referencedCount = -1
        PostIndex.collectNames(verified)
        return verified
    }
}
