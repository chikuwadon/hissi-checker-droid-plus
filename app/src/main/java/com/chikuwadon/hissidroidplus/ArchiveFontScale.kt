package com.chikuwadon.hissidroidplus

/** Percent values shared by the inline slider and persistent settings. */
object ArchiveFontScale {
    const val MIN = 70
    const val MAX = 160
    const val DEFAULT = 100
    const val STEP = 5
    const val MAX_STEP = (MAX - MIN) / STEP

    @JvmStatic fun normalize(value: Int): Int {
        val bounded = value.coerceIn(MIN, MAX)
        return MIN + ((bounded - MIN + STEP / 2) / STEP) * STEP
    }
    @JvmStatic fun fromProgress(progress: Int): Int = MIN + progress.coerceIn(0, MAX_STEP) * STEP
    @JvmStatic fun toProgress(value: Int): Int = (normalize(value) - MIN) / STEP
}
