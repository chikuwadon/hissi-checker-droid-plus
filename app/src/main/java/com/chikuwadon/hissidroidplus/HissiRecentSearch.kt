package com.chikuwadon.hissidroidplus

import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Bounded seven-day ID lookup. A 404 means the day's result URL is absent. */
class HissiRecentSearch private constructor() {
    fun interface Probe {
        @Throws(IOException::class)
        fun hasPosts(board: String, date: String, id: String): Boolean
    }

    data class Result(
        @JvmField val day: String?,
        @JvmField val error: String?,
        @JvmField val checkedDays: Int
    )

    companion object {
        @JvmStatic
        fun find(board: String, id: String, today: LocalDate?, probe: Probe?): Result {
            if (today == null || probe == null) {
                throw IllegalArgumentException("Date or fetcher missing")
            }
            CheckerQuery.hissi(board, today.format(DateTimeFormatter.BASIC_ISO_DATE), id)
            for (index in 0 until 7) {
                val date = today.minusDays(index.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE)
                try {
                    if (probe.hasPosts(board, date, id)) {
                        return Result(date, null, index + 1)
                    }
                } catch (failure: IOException) {
                    if (failure.message == "HTTP 404") continue
                    return Result(null,
                        "通信・解析エラー (${failure.message ?: "原因不明"})", index + 1)
                }
            }
            return Result(null, null, 7)
        }
    }
}
