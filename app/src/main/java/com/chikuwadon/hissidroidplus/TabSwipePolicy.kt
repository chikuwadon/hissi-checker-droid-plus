package com.chikuwadon.hissidroidplus

import kotlin.math.abs

/** Small, Android-independent gesture decision policy shared by all result tabs. */
object TabSwipePolicy {
    @JvmStatic
    fun destination(
        current: Int, count: Int, dx: Float, dy: Float, velocityX: Float,
        distanceThreshold: Float, flingDistance: Float, flingVelocity: Float
    ): Int {
        if (count <= 1 || current !in 0 until count) return current
        // Never turn vertical list scrolling into a page switch.
        if (abs(dx) <= abs(dy) * 1.15f) return current
        val deliberate = abs(dx) >= distanceThreshold
        val fling = abs(dx) >= flingDistance && abs(velocityX) >= flingVelocity &&
            dx * velocityX > 0f
        if (!deliberate && !fling) return current
        return (current + if (dx < 0f) 1 else -1).coerceIn(0, count - 1)
    }
}
