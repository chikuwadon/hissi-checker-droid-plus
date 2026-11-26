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
    private companion object {
        val ID_PREFIX = Regex("(?i)^ID\\s*[:：]\\s*")
        val DAY = Regex("\\d{8}")
        val TOKYO = ZoneId.of("Asia/Tokyo")
    }
    private val needle = id.replace(ID_PREFIX, "")

    fun active(): Boolean = board.isNotEmpty() || id.isNotEmpty() || from.isNotEmpty() || to.isNotEmpty()
    fun matches(entry: SearchHistory.Entry): Boolean {
        if (!entry.board.contains(board, ignoreCase = true)) return false
        if (needle.isNotEmpty() && !entry.id.replace(ID_PREFIX, "").contains(needle)) return false
        if (from.isEmpty() && to.isEmpty()) return true
        val date = if (byPostDate) entry.effectiveDate() else
            Instant.ofEpochMilli(entry.viewedAt).atZone(TOKYO).toLocalDate()
                .format(DateTimeFormatter.BASIC_ISO_DATE)
        if (!date.matches(DAY)) return false
        return (from.isEmpty() || date >= from) && (to.isEmpty() || date <= to)
    }
}
