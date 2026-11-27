package com.chikuwadon.hissidroidplus

/** 100-step history capacity slider; the final position is unlimited. */
object HistoryRetention {
    const val DEFAULT = 100
    const val MAX = 10000
    const val UNLIMITED = 0
    const val LAST_PROGRESS = 100

    @JvmStatic fun normalize(value: Int): Int =
        if (value == UNLIMITED) UNLIMITED else (value.coerceIn(DEFAULT, MAX) / 100) * 100

    @JvmStatic fun fromProgress(progress: Int): Int =
        if (progress >= LAST_PROGRESS) UNLIMITED else (progress.coerceAtLeast(0) + 1) * 100

    @JvmStatic fun toProgress(limit: Int): Int =
        if (limit == UNLIMITED) LAST_PROGRESS else normalize(limit) / 100 - 1

    @JvmStatic fun reduced(old: Int, next: Int): Boolean =
        next != UNLIMITED && (old == UNLIMITED || next < old)

    fun label(limit: Int): String = if (limit == UNLIMITED) "無制限" else "${limit}件"
}
