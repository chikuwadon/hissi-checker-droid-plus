package com.chikuwadon.hissidroidplus

import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Futaba thread IDs are sequential, not Unix timestamps. Read the displayed creation metadata. */
object KyodemoDates {
    private val date = Regex("(?<!\\d)(?:(20\\d{2}|\\d{2})[/-])?(\\d{1,2})[/-](\\d{1,2})(?:\\s*\\([^)]{1,8}\\))?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)")
    private val fullFormat = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss")
    @JvmStatic fun parse(value: String): String {
        runCatching {
            val time = OffsetDateTime.parse(value.trim()).atZoneSameInstant(ZoneId.of("Asia/Tokyo"))
            return fullFormat.format(time)
        }
        val m = date.find(value) ?: return ""
        return runCatching {
            val year = m.groupValues[1].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            val hour = m.groupValues[4].toInt()
            val minute = m.groupValues[5].toInt()
            val second = m.groupValues[6].toIntOrNull()
            LocalDate.of(year ?: 2000, month, day)
            LocalTime.of(hour, minute, second ?: 0)
            val dateText = if (year == null) "%02d/%02d".format(java.util.Locale.ROOT, month, day)
                else "%04d/%02d/%02d".format(java.util.Locale.ROOT, year, month, day)
            dateText + " " + "%02d:%02d".format(java.util.Locale.ROOT, hour, minute) +
                (second?.let { ":%02d".format(java.util.Locale.ROOT, it) } ?: "")
        }.getOrDefault("")
    }

    @JvmStatic fun created(anchor: Element): String {
        // Prefer explicit creation fields; do not parse dates embedded in thread titles.
        val row = anchor.closest("article.panel-block") ?: anchor
        for (el in row.select("time[datetime], [data-created], [data-created-at]")) {
            if (el.closest("h2.cltitle") != null) continue
            for (attr in listOf("data-created", "data-created-at", "datetime")) {
                parse(el.attr(attr)).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        val metadata = row.clone()
        metadata.select("h2.cltitle").remove()
        for (el in metadata.select("small, time")) {
            for (value in listOf(el.attr("title"), el.text())) {
                parse(value).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return ""
    }
}
