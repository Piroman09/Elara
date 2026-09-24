package com.piroman.elara.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the volume-fade arithmetic.
 *
 * These run on the JVM (no emulator, no ExoPlayer) because [VolumeMix] is
 * deliberately free of Android types. The point of the extraction was that the
 * behaviour of the fade-out and sleep-timer settings is otherwise unverifiable
 * here — there is no device to listen on.
 */
class VolumeMixTest {

    private val tolerance = 1e-4f

    // ── isInsideFadeWindow ────────────────────────────────────────────────

    @Test
    fun `fade window is open only inside the last crossfadeMs of the track`() {
        // 200 s track, 10 s fade → window is (190_000, 200_000]
        assertFalse(VolumeMix.isInsideFadeWindow(0L, 200_000L, 10_000L))
        assertFalse(VolumeMix.isInsideFadeWindow(189_999L, 200_000L, 10_000L))
        assertTrue(VolumeMix.isInsideFadeWindow(190_000L, 200_000L, 10_000L))
        assertTrue(VolumeMix.isInsideFadeWindow(199_999L, 200_000L, 10_000L))
    }

    @Test
    fun `fade window is closed at and past the end of the track`() {
        // remaining == 0 is the end, not a fade — this is what keeps a finished
        // track from reporting a stale fully-faded volume.
        assertFalse(VolumeMix.isInsideFadeWindow(200_000L, 200_000L, 10_000L))
        assertFalse(VolumeMix.isInsideFadeWindow(200_001L, 200_000L, 10_000L))
    }

    @Test
    fun `fade window is closed when fade-out is off`() {
        assertFalse(VolumeMix.isInsideFadeWindow(199_000L, 200_000L, 0L))
        assertFalse(VolumeMix.isInsideFadeWindow(199_000L, 200_000L, -1L))
    }

    @Test
    fun `fade window is closed while the duration is still unknown`() {
        // ExoPlayer reports duration 0 until the media is prepared.
        assertFalse(VolumeMix.isInsideFadeWindow(0L, 0L, 10_000L))
        assertFalse(VolumeMix.isInsideFadeWindow(0L, -1L, 10_000L))
    }

    @Test
    fun `fade window closes again when a seek moves back before it`() {
        // Seeking backwards must restore full volume rather than stay faded.
        assertTrue(VolumeMix.isInsideFadeWindow(195_000L, 200_000L, 10_000L))
        assertFalse(VolumeMix.isInsideFadeWindow(30_000L, 200_000L, 10_000L))
    }

    // ── fadeFactorForRemaining ────────────────────────────────────────────

    @Test
    fun `fade factor ramps linearly from full volume to silence`() {
        assertEquals(1f, VolumeMix.fadeFactorForRemaining(10_000L, 10_000L), tolerance)
        assertEquals(0.5f, VolumeMix.fadeFactorForRemaining(5_000L, 10_000L), tolerance)
        assertEquals(0f, VolumeMix.fadeFactorForRemaining(0L, 10_000L), tolerance)
    }

    @Test
    fun `fade factor is clamped when the tick overshoots the end`() {
        assertEquals(0f, VolumeMix.fadeFactorForRemaining(-250L, 10_000L), tolerance)
        assertEquals(1f, VolumeMix.fadeFactorForRemaining(11_000L, 10_000L), tolerance)
    }

    @Test
    fun `fade factor is full volume when fade-out is off`() {
        assertEquals(1f, VolumeMix.fadeFactorForRemaining(500L, 0L), tolerance)
    }

    // ── crossfadeFactor ───────────────────────────────────────────────────

    @Test
    fun `crossfade factor is full volume outside the window`() {
        assertEquals(1f, VolumeMix.crossfadeFactor(0L, 200_000L, 10_000L), tolerance)
        assertEquals(1f, VolumeMix.crossfadeFactor(189_000L, 200_000L, 10_000L), tolerance)
    }

    @Test
    fun `crossfade factor decreases monotonically across the window`() {
        // Sample the open window only: 190_000 … 199_999 of a 200_000 ms track.
        val samples = (0..9).map { i ->
            VolumeMix.crossfadeFactor(190_000L + i * 1_000L, 200_000L, 10_000L)
        }
        for (i in 1 until samples.size) {
            assertTrue(
                "expected non-increasing fade, got $samples",
                samples[i] <= samples[i - 1] + tolerance,
            )
        }
        assertEquals(1f, samples.first(), tolerance)
        assertEquals(0.1f, samples.last(), tolerance)
    }

    @Test
    fun `crossfade factor returns to full volume at the end of the track`() {
        // Guards the bug where a finished track left volume at 0 for the next one.
        assertEquals(1f, VolumeMix.crossfadeFactor(200_000L, 200_000L, 10_000L), tolerance)
    }

    // ── sleepFadeFactor ───────────────────────────────────────────────────

    @Test
    fun `sleep fade counts down from full volume to silence`() {
        val steps = 15
        assertEquals(1f, VolumeMix.sleepFadeFactor(steps, steps), tolerance)
        assertEquals(0.5f, VolumeMix.sleepFadeFactor(8, 16), tolerance)
        assertEquals(0f, VolumeMix.sleepFadeFactor(0, steps), tolerance)
    }

    @Test
    fun `sleep fade is full volume with no steps to run`() {
        assertEquals(1f, VolumeMix.sleepFadeFactor(0, 0), tolerance)
        assertEquals(1f, VolumeMix.sleepFadeFactor(3, -1), tolerance)
    }

    @Test
    fun `sleep fade is clamped above one step count`() {
        assertEquals(1f, VolumeMix.sleepFadeFactor(20, 15), tolerance)
    }

    // ── mix ───────────────────────────────────────────────────────────────

    @Test
    fun `mix multiplies independent fades instead of letting one clobber the other`() {
        // The regression this guards: with fade-out on, the 400 ms ticker reset
        // the sleep fade to full volume, so the bedtime fade never happened.
        assertEquals(0.5f, VolumeMix.mix(1f, 0.5f), tolerance)
        assertEquals(0.25f, VolumeMix.mix(0.5f, 0.5f), tolerance)
        assertEquals(0f, VolumeMix.mix(0f, 1f), tolerance)
        assertEquals(0f, VolumeMix.mix(1f, 0f), tolerance)
    }

    @Test
    fun `mix is full volume when neither fade is running`() {
        assertEquals(1f, VolumeMix.mix(1f, 1f), tolerance)
    }

    @Test
    fun `mix never exceeds full volume`() {
        assertEquals(1f, VolumeMix.mix(1.5f, 1.5f), tolerance)
        assertEquals(1f, VolumeMix.mix(1f, 2f), tolerance)
    }

    // ── the two settings together ─────────────────────────────────────────

    @Test
    fun `sleep fade still progresses while the outro fade is running`() {
        // Both settings on: at 195 s of a 200 s track with a 10 s fade the outro
        // factor is 0.5. The sleep fade must continue to lower the total.
        val crossfade = VolumeMix.crossfadeFactor(195_000L, 200_000L, 10_000L)
        val early = VolumeMix.mix(crossfade, VolumeMix.sleepFadeFactor(15, 15))
        val late = VolumeMix.mix(crossfade, VolumeMix.sleepFadeFactor(3, 15))
        assertEquals(0.5f, early, tolerance)
        assertEquals(0.1f, late, tolerance)
        assertTrue("sleep fade must keep lowering the volume", late < early)
    }
}
