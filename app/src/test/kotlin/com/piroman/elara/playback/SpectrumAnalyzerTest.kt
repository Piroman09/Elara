package com.piroman.elara.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the feel of the visualizer's pure DSP: log-shaped bar mapping, max
 * folding, and the fast-attack / slow-decay smoothing pair. These are the
 * three things that make raw FFT data read as "dancing to the music" — and
 * each has a specific way of being wrong that a regression would undo.
 */
class SpectrumAnalyzerTest {

    @Test
    fun `bar ranges start past DC and cover the spectrum`() {
        val ranges = SpectrumAnalyzer.barBinRanges(barCount = 24, binCount = 64)
        assertEquals(24, ranges.size)
        // Bin 0 is DC — no musical content — so bars start at 1.
        assertEquals(1, ranges.first().first)
        // The top of the spectrum is used, not wasted.
        assertEquals(63, ranges.last().last)
    }

    @Test
    fun `bar ranges are ordered and do not overlap`() {
        val ranges = SpectrumAnalyzer.barBinRanges(barCount = 24, binCount = 64)
        for (i in 0 until ranges.size - 1) {
            assertTrue(
                "range $i (${ranges[i]}) must end before range ${i + 1} (${ranges[i + 1]})",
                ranges[i].last < ranges[i + 1].first
            )
        }
        for (range in ranges) {
            assertTrue("range must not be empty: $range", !range.isEmpty())
        }
    }

    @Test
    fun `a bar takes the max of its bins, not the average`() {
        // 8 bands; put a spike in the middle of bar 2's range.
        val magnitudes = FloatArray(64)
        val ranges = SpectrumAnalyzer.barBinRanges(8, 64)
        magnitudes[ranges[2].first + 1] = 200f
        // Everything else near-silent.
        val bars = SpectrumAnalyzer.magnitudesToBars(magnitudes, ranges, 8)
        assertTrue("spike must reach bar 2 strongly", bars[2] > 0.5f)
        assertTrue("neighbour without energy stays low", bars[3] < 0.1f)
    }

    @Test
    fun `silence maps to zero bars`() {
        val bars = SpectrumAnalyzer.magnitudesToBars(
            FloatArray(64),
            SpectrumAnalyzer.barBinRanges(8, 64),
            8
        )
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun `attack is faster than decay`() {
        val rise = SpectrumAnalyzer.smooth(0.5f, 1f) - 0.5f
        val fall = 0.5f - SpectrumAnalyzer.smooth(0.5f, 0f)
        assertTrue(
            "rising toward a peak ($rise) must move further than falling ($fall)",
            rise > fall
        )
    }

    @Test
    fun `smoothing never overshoots the target`() {
        val up = SpectrumAnalyzer.smooth(0.2f, 0.8f)
        assertTrue(up in 0.2f..0.8f)
        val down = SpectrumAnalyzer.smooth(0.8f, 0.2f)
        assertTrue(down in 0.2f..0.8f)
    }

    @Test
    fun `smoothAll decays a whole array toward zero on silence`() {
        val current = floatArrayOf(0.9f, 0.5f, 0.1f)
        val targets = FloatArray(3)
        SpectrumAnalyzer.smoothAll(current, targets)
        assertTrue(current.all { it < 0.9f })
        // Proportional decay preserves the ordering: the loud bar stays the
        // loudest while everything sinks.
        assertTrue(current[0] > current[1] && current[1] > current[2])
    }
}
