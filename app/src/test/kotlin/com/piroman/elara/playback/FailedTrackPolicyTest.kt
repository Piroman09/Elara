package com.piroman.elara.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bound on auto-skipping unplayable tracks.
 *
 * Both directions are silent failures: an unbounded skip walks an offline
 * queue at 600 ms a song, and an over-eager bound leaves the user sitting in
 * silence after one bad track — the bug the skip exists to fix.
 */
class FailedTrackPolicyTest {

    @Test
    fun `skips while the budget lasts`() {
        assertTrue(FailedTrackPolicy.shouldSkip(failures = 1, queueSize = 10))
        assertTrue(FailedTrackPolicy.shouldSkip(failures = 2, queueSize = 10))
        assertTrue(FailedTrackPolicy.shouldSkip(failures = 3, queueSize = 10))
    }

    @Test
    fun `gives up once the budget is spent`() {
        assertFalse(FailedTrackPolicy.shouldSkip(failures = 4, queueSize = 10))
        assertFalse(FailedTrackPolicy.shouldSkip(failures = 50, queueSize = 10))
    }

    @Test
    fun `a queue with nowhere to go never skips`() {
        assertFalse(FailedTrackPolicy.shouldSkip(failures = 1, queueSize = 1))
        assertFalse(FailedTrackPolicy.shouldSkip(failures = 1, queueSize = 0))
    }

    @Test
    fun `the cap is the documented three, not more`() {
        assertTrue(FailedTrackPolicy.shouldSkip(FailedTrackPolicy.MAX_CONSECUTIVE_FAILURES, 5))
        assertFalse(FailedTrackPolicy.shouldSkip(FailedTrackPolicy.MAX_CONSECUTIVE_FAILURES + 1, 5))
    }

    @Test
    fun `a zero count is not a skip`() {
        // Defensive: the caller counts before asking, so 0 means the budget was
        // never incremented — moving the queue on then would be a phantom skip.
        assertFalse(FailedTrackPolicy.shouldSkip(failures = 0, queueSize = 10))
    }
}
