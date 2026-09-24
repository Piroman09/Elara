package com.piroman.elara.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refresh-on-return rule, tested directly because every way of getting it
 * wrong is invisible: too eager re-fetches eight Home rows on every app switch,
 * too shy leaves Home showing yesterday's rows. Neither throws.
 */
class HomeRefreshPolicyTest {

    private val sixHours = 6 * 60 * 60 * 1000L

    private fun shouldRefresh(
        requestedAtMs: Long,
        nowMs: Long,
        loading: Boolean = false,
        cameBackEmpty: Boolean = false
    ) = HomeRefreshPolicy.shouldRefresh(
        requestedAtMs = requestedAtMs,
        nowMs = nowMs,
        loading = loading,
        cameBackEmpty = cameBackEmpty,
        maxAgeMs = sixHours
    )

    @Test
    fun `a quick switch away and back does not refetch`() {
        assertFalse(
            shouldRefresh(requestedAtMs = 1_000_000L, nowMs = 1_000_000L + 60_000L)
        )
    }

    @Test
    fun `rows older than the window are refreshed`() {
        assertTrue(
            shouldRefresh(requestedAtMs = 1_000_000L, nowMs = 1_000_000L + sixHours)
        )
    }

    @Test
    fun `exactly at the window is refreshed`() {
        // The boundary is >=, not >. Off-by-one here would mean the rows are a
        // minute staler than the documented window, which nobody would notice.
        assertTrue(
            shouldRefresh(requestedAtMs = 1_000_000L, nowMs = 1_000_000L + sixHours)
        )
        assertFalse(
            shouldRefresh(requestedAtMs = 1_000_000L, nowMs = 1_000_000L + sixHours - 1)
        )
    }

    @Test
    fun `a failed fetch is retried immediately, whatever its age`() {
        // Opened offline a minute ago, connectivity back: Home is empty and must
        // not wait six hours to notice.
        assertTrue(
            shouldRefresh(
                requestedAtMs = 1_000_000L,
                nowMs = 1_000_000L + 60_000L,
                cameBackEmpty = true
            )
        )
    }

    @Test
    fun `an in-flight batch always wins, even when stale and empty`() {
        // This is the cold-start race: `init` starts the first batch, the resume
        // that follows arrives while it is still running. Refreshing here would
        // fire a second copy of every request.
        assertFalse(
            shouldRefresh(
                requestedAtMs = 1_000_000L,
                nowMs = 1_000_000L + sixHours * 4,
                loading = true,
                cameBackEmpty = true
            )
        )
    }

    @Test
    fun `nothing has been fetched yet means stay out of the way`() {
        // Not the same as "infinitely old": the initial load is `init`'s job.
        assertFalse(shouldRefresh(requestedAtMs = 0L, nowMs = 1_000_000L))
        assertFalse(shouldRefresh(requestedAtMs = 0L, nowMs = 1_000_000L, cameBackEmpty = true))
    }

    @Test
    fun `a clock that moved backwards does not trigger a refresh loop`() {
        // Wall-clock time is not monotonic: a timezone change or an NTP
        // correction can put `now` before `requestedAt`. A negative age must not
        // be read as "very old".
        assertFalse(shouldRefresh(requestedAtMs = 2_000_000L, nowMs = 1_000_000L))
    }
}
