package com.sarthu.retrocam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMathTest {
    @Test fun blankEndMeansSourceEnd() {
        assertEquals(12_000L, TimelineMath.resolvedEndMs(12_000L, -1L))
    }

    @Test fun trimMustBeInsideSourceAndNonEmpty() {
        assertTrue(TimelineMath.isValidTrim(10_000L, 2_000L, 8_000L))
        assertTrue(TimelineMath.isValidTrim(10_000L, 2_000L, -1L))
        assertFalse(TimelineMath.isValidTrim(10_000L, -1L, 8_000L))
        assertFalse(TimelineMath.isValidTrim(10_000L, 8_000L, 8_000L))
        assertFalse(TimelineMath.isValidTrim(10_000L, 2_000L, 10_001L))
    }

    @Test fun playbackSpeedChangesOutputDuration() {
        assertEquals(4_000L, TimelineMath.outputDurationMs(10_000L, 2_000L, 10_000L, 2f))
        assertEquals(16_000L, TimelineMath.outputDurationMs(10_000L, 2_000L, 10_000L, 0.5f))
    }

    @Test fun totalOutputDurationSumsSpeedAdjustedClips() {
        val clips = listOf(
            Triple(10_000L, 0L to 10_000L, 1f),
            Triple(8_000L, 2_000L to 8_000L, 2f)
        )
        assertEquals(13_000_000L, TimelineMath.totalOutputDurationUs(clips))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteSpeedIsRejected() {
        TimelineMath.outputDurationMs(10_000L, 0L, -1L, Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveSpeedIsRejected() {
        TimelineMath.outputDurationMs(10_000L, 0L, -1L, 0f)
    }
}
