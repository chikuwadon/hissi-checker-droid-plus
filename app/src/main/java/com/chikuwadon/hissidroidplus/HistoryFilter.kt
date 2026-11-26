package com.chikuwadon.hissidroidplus

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Independent optional filters; ID comparison is case-sensitive. */
data class HistoryFilter(
    val board: String = "",
    val id: String = "",
    val from: String = "",
    val to: String = "",
    val byPostDate: Boolean = false
) {
    fun active(): Boolean = board.isNotEmpty() || id.isNotEmpty() || from.isNotEmpty() || to.isNotEmpty()
    fun matches(entry: SearchHistory.Entry): Boolean {
        if (!entry.board.contains(board, ignoreCase = true)) return false
        val needle = id.replace(Regex("(?i)^ID\\s*[:：]\\s*"), "")
        val actual = entry.id.replace(Regex("(?i)^ID\\s*[:：]\\s*"), "")
        if (!actual.contains(needle)) return false
        if (from.isEmpty() && to.isEmpty()) return true
        val date = if (byPostDate) entry.effectiveDate() else
            Instant.ofEpochMilli(entry.viewedAt).atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
                .format(DateTimeFormatter.BASIC_ISO_DATE)
        if (!date.matches(Regex("\\d{8}"))) return false
        return (from.isEmpty() || date >= from) && (to.isEmpty() || date <= to)
    }
}
