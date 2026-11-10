package com.chikuwadon.hissidroidplus

import java.util.Locale
import java.util.regex.Pattern

/** Copy available per-ID thread summaries without inventing site statistics. */
object ThreadSummaryFormatter {
    private val DATE = Pattern.compile("(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})")
    private val RESULT_DATE = Pattern.compile("/read\\.php/[A-Za-z0-9_]+/(\\d{8})/")

    @JvmStatic
    fun format(query: CheckerQuery?, result: CheckerResult?): String {
        if (result == null) return ""
        val source = result.sourceThreadSummary
        if (!source.isNullOrBlank()) return source.trim()
        val threads = PostIndex.uniqueThreads(result)
        if (threads.isEmpty()) return ""
        var date = query?.date ?: ""
        if (!date.matches(Regex("\\d{8}"))) {
            if (query != null && query.site == CheckerQuery.Site.HISSI) {
                val fromUrl = RESULT_DATE.matcher(query.url)
                if (fromUrl.find()) date = fromUrl.group(1)
            }
            if (!date.matches(Regex("\\d{8}"))) {
                for (post in result.posts) {
                    val day = DATE.matcher(post.meta)
                    if (day.find()) {
                        date = String.format(Locale.ROOT, "%s%02d%02d",
                            day.group(1), day.group(2).toInt(), day.group(3).toInt())
                        break
                    }
                }
            }
        }
        var id = query?.id ?: ""
        if (id.isEmpty()) {
            for (post in result.posts) {
                val found = PostFormatter.id(post)
                if (found.isNotEmpty()) {
                    id = found.substring(3)
                    break
                }
            }
        }
        var url = result.sourceUrl.ifEmpty { query?.url ?: "" }
        if (url.isEmpty() && query != null && query.site == CheckerQuery.Site.HISSI &&
            query.date.isNotEmpty() && query.id.isNotEmpty()
        ) url = HissiAdapter.resultUrl(query.board, query.date, query.id)
        return buildString {
            if (date.matches(Regex("\\d{8}"))) {
                append("日付: ").append(date.substring(0, 4)).append('/')
                    .append(date.substring(4, 6)).append('/').append(date.substring(6, 8))
                    .append('\n')
            }
            append("レス数: ")
                .append(if (result.reportedPostCount >= 0) result.reportedPostCount else result.posts.size)
                .append('\n')
            if (result.referencedCount >= 0) append("被参照数: ").append(result.referencedCount).append('\n')
            append("スレッド数: ")
                .append(if (result.reportedThreadCount >= 0) result.reportedThreadCount else threads.size)
                .append('\n')
            append("ID:").append(id).append(" の書き込みスレッド一覧：\n")
            val counts = PostIndex.threadPostCounts(result)
            for ((link, value) in threads) {
                val title = if (value.isNullOrBlank()) "スレッド" else value.trim()
                val count = counts.getOrDefault(PostIndex.threadKey(link), 0)
                append(title)
                if (count > 0) append(" (").append(count).append(')')
                append('\n')
            }
            if (url.isNotEmpty()) append(url).append('\n')
        }.trim()
    }
}
