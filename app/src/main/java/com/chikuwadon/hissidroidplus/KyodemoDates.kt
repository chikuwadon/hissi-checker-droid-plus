package com.chikuwadon.hissidroidplus

import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Thread IDs are not necessarily timestamps; read only the creation metadata. */
object KyodemoDates {
    private val japan = ZoneId.of("Asia/Tokyo")
    private val date = Regex("(?<![\\d/])(?:(20\\d{2}|\\d{2})[/-])?(\\d{1,2})[/-](\\d{1,2})(?:\\s*\\([^)]{1,8}\\))?(?:[ T]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?(?![\\d/:])")
    private val fullFormat = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss")
    @JvmStatic @JvmOverloads fun parse(value: String, currentYear: Int = LocalDate.now(japan).year): String {
        runCatching {
            val time = OffsetDateTime.parse(value.trim()).atZoneSameInstant(japan)
            return fullFormat.format(time)
        }
        for (m in date.findAll(value)) {
            val statedYear = m.groupValues[1].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            val hour = m.groupValues[4].toIntOrNull()
            // Kyodemo shows full dates for earlier years and MM/dd HH:mm for this year.
            if (statedYear == null && hour == null) continue
            val result = runCatching {
                val day = LocalDate.of(statedYear ?: currentYear, m.groupValues[2].toInt(), m.groupValues[3].toInt())
                val dateText = day.format(DateTimeFormatter.ofPattern("uuuu/MM/dd"))
                if (hour == null) dateText else {
                    val second = m.groupValues[6].toIntOrNull()
                    val time = LocalTime.of(hour, m.groupValues[5].toInt(), second ?: 0)
                    dateText + " " + time.format(DateTimeFormatter.ofPattern(if (second == null) "HH:mm" else "HH:mm:ss"))
                }
            }.getOrDefault("")
            if (result.isNotEmpty()) return result
        }
        return ""
    }

    @JvmStatic @JvmOverloads fun created(anchor: Element, currentYear: Int = LocalDate.now(japan).year): String {
        val metadata = (anchor.closest("article.panel-block") ?: anchor).clone()
        metadata.select("h2.cltitle").remove()
        for (el in metadata.select("time[datetime], [data-created], [data-created-at]")) {
            for (attr in listOf("data-created", "data-created-at", "datetime")) {
                parse(el.attr(attr), currentYear).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        // Dates can be plain text, a clock's tooltip, or a nested span within the metadata.
        metadata.select("small.right_i").remove()
        for (el in metadata.select("small.left_i, .left_i, time, small")) {
            for (node in el.getAllElements()) {
                for (attr in listOf("datetime", "title", "data-original-title", "aria-label")) {
                    parse(node.attr(attr), currentYear).takeIf { it.isNotEmpty() }?.let { return it }
                }
            }
            parse(el.text(), currentYear).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return parse(metadata.text(), currentYear)
    }
}
