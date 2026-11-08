package com.sarthu.retrocam

import kotlin.math.roundToLong

/** Pure timeline arithmetic shared by validation, export duration and unit tests. */
object TimelineMath {
    fun resolvedEndMs(sourceDurationMs: Long, requestedEndMs: Long): Long =
        if (requestedEndMs < 0L) sourceDurationMs else requestedEndMs

    fun isValidTrim(sourceDurationMs: Long, startMs: Long, requestedEndMs: Long): Boolean {
        if (sourceDurationMs <= 0L || startMs < 0L || startMs >= sourceDurationMs) return false
        val endMs = resolvedEndMs(sourceDurationMs, requestedEndMs)
        return endMs > startMs && endMs <= sourceDurationMs
    }

    fun outputDurationMs(sourceDurationMs: Long, startMs: Long, requestedEndMs: Long, speed: Float): Long {
        require(isValidTrim(sourceDurationMs, startMs, requestedEndMs)) { "Invalid source trim range" }
        require(speed.isFinite() && speed > 0f) { "Playback speed must be positive" }
        val sourceRange = resolvedEndMs(sourceDurationMs, requestedEndMs) - startMs
        return (sourceRange / speed.toDouble()).roundToLong().coerceAtLeast(1L)
    }

    fun totalOutputDurationUs(
        clips: List<Triple<Long, Pair<Long, Long>, Float>>
    ): Long = clips.sumOf { (durationMs, range, speed) ->
        outputDurationMs(durationMs, range.first, range.second, speed) * 1000L
    }
}
