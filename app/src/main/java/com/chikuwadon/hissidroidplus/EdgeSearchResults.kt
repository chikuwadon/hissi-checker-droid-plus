package com.chikuwadon.hissidroidplus

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import java.util.regex.Pattern
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses actual site search links without inventing posts or thread keys. */
object EdgeSearchResults {
    class Item(@JvmField val title: String, @JvmField val checkerUrl: String)

    private val VISIBLE_DATE = Pattern.compile(
        "(?<![0-9])(20[0-9]{2})[/-]([0-9]{1,2})[/-]([0-9]{1,2})(?![0-9])"
    )
    private val EPOCH_IN_URL = Pattern.compile("/([0-9]{10})(?:/|$)")
    private val JAPAN = ZoneId.of("Asia/Tokyo")

    /**
     * Prefer visible date in search results; otherwise use thread creation
     * as a starting point, never as proof of the post's actual date.
     */
    @JvmStatic
    fun fallbackDateForLink(link: Element, href: String): String {
        val row = link.closest("tr, li, article, .thread-item, .search-result")
        if (row != null) {
            val matched = VISIBLE_DATE.matcher(row.text())
            if (matched.find()) {
                try {
                    val date = LocalDate.of(matched.group(1).toInt(),
                        matched.group(2).toInt(), matched.group(3).toInt())
                    return date.format(DateTimeFormatter.BASIC_ISO_DATE)
                } catch (_: Exception) {
                    // Fall back to the thread's creation timestamp.
                }
            }
        }
        return try {
            val path = URI.create(href).path ?: return ""
            val matched = EPOCH_IN_URL.matcher(path)
            if (matched.find()) {
                Instant.ofEpochSecond(matched.group(1).toLong()).atZone(JAPAN)
                    .toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    /** A thread cannot contain posts from before its creation in JST. */
    @JvmStatic
    fun threadStartsAfterSelectedDay(checkerUrl: String, date: String?): Boolean {
        return try {
            val query = CheckerQuery.parseUrl(checkerUrl)
            if (!query.key.matches(Regex("[0-9]{10}"))) return false
            val created = Instant.ofEpochSecond(query.key.toLong()).atZone(JAPAN).toLocalDate()
            val selected = LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
            created.isAfter(selected)
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun parse(html: String?, url: String, board: String, date: String?, id: String): List<Item> {
        val doc = Jsoup.parse(html ?: "", url)
        val unique = LinkedHashMap<String, Item>()
        for (anchor in doc.select("a[href]")) {
            val href = anchor.absUrl("href")
            val day = date ?: fallbackDateForLink(anchor, href)
            if (day.isEmpty()) continue
            val checker = EdgeSearchLink.checkerFromThread(board, day, id, href)
            if (checker.isEmpty() || threadStartsAfterSelectedDay(checker, date)) continue
            var title = anchor.text().trim()
            if (title.isEmpty() || title.matches(Regex("\\d+"))) {
                val nearby = anchor.closest("li, tr, article, div.post, .thread-item")
                if (nearby != null) title = nearby.text().trim()
            }
            if (title.isEmpty()) title = "スレッド " + (unique.size + 1)
            if (title.length > 180) title = title.substring(0, 180)
            unique.putIfAbsent(checker, Item(title, checker))
        }
        return ArrayList(unique.values)
    }
}
