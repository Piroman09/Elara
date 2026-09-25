package com.piroman.elara.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the speed-mode contract: the labels the settings UI shows, the rate
 * values playback applies, and tolerant parsing of persisted names.
 */
class SpeedModeTest {

    @Test
    fun `slowed and nightcore move rate and pitch together`() {
        // Each mode's speed value is used for BOTH rate and pitch — that
        // combination is the whole character of the mode. A value of exactly
        // 1f would be a silent no-op, which is the failure mode to guard.
        for (mode in SpeedMode.entries) {
            assertTrue(
                "${mode.name} must carry a non-neutral rate",
                mode.speed != 1f || mode == SpeedMode.NORMAL
            )
        }
        assertTrue(SpeedMode.SLOWED_REVERB.speed < 1f)
        assertTrue(SpeedMode.NIGHTCORE.speed > 1f)
        assertEquals(1f, SpeedMode.NORMAL.speed, 0f)
    }

    @Test
    fun `labels match the settings segmented control`() {
        assertEquals(listOf("Normal", "Slowed", "Nightcore"), SpeedMode.entries.map { it.label })
    }

    @Test
    fun `parsing is tolerant of unknown or legacy names`() {
        assertEquals(SpeedMode.NIGHTCORE, SpeedMode.fromNameOrDefault("NIGHTCORE"))
        assertEquals(SpeedMode.NORMAL, SpeedMode.fromNameOrDefault("garbage"))
        assertEquals(SpeedMode.NORMAL, SpeedMode.fromNameOrDefault(null))
    }
}
