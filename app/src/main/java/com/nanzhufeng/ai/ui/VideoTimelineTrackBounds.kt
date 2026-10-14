package com.nanzhufeng.ai.ui

import kotlin.math.roundToLong

/** Window coordinates of the visible timeline, shared by drawing and the gesture owner. */
internal data class VideoTimelineTrackBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float, horizontalSlop: Float, verticalSlop: Float): Boolean =
        x in (left - horizontalSlop)..(right + horizontalSlop) &&
            y in (top - verticalSlop)..(bottom + verticalSlop)

    fun positionAt(x: Float, durationMillis: Long): Long {
        if (durationMillis <= 0L || right <= left) return 0L
        val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
        return (fraction * durationMillis).roundToLong().coerceIn(0L, durationMillis)
    }
}
