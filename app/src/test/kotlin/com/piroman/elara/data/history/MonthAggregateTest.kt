package com.piroman.elara.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins Wrapped's two pure rules: month rollover (the only way Wrapped can
 * silently listen about *when* something played) and the capped count merge
 * (the only way the stored map can grow without bound).
 */
class MonthAggregateTest {

    @Test
    fun `rollover keeps the aggregate within the same month`() {
        val current = MonthAggregate(month = "2026-09", ms = 5_000, plays = 3)
        val kept = MonthAggregate.rollover(current, "2026-09")
        assertEquals(current, kept)
    }

    @Test
    fun `rollover zeroes everything when the month turns`() {
        val current = MonthAggregate(
            month = "2026-08",
            ms = 999_999,
            plays = 42,
            trackCounts = mapOf("a" to 10, "b" to 4)
        )
        val next = MonthAggregate.rollover(current, "2026-09")
        assertEquals("2026-09", next.month)
        assertEquals(0L, next.ms)
        assertEquals(0, next.plays)
        assertTrue(next.trackCounts.isEmpty())
    }

    @Test
    fun `rollover handles a null first month`() {
        val next = MonthAggregate.rollover(null, "2026-09")
        assertEquals("2026-09", next.month)
        assertEquals(0, next.plays)
    }

    @Test
    fun `bumpCounts counts a track twice as two plays`() {
        val once = MonthAggregate.bumpCounts(emptyMap(), "t1", cap = 10)
        val twice = MonthAggregate.bumpCounts(once, "t1", cap = 10)
        assertEquals(2, twice["t1"])
    }

    @Test
    fun `bumpCounts drops the least-played when over the cap`() {
        var counts = mapOf("a" to 5, "b" to 3, "c" to 1)
        counts = MonthAggregate.bumpCounts(counts, "d", cap = 3)
        assertEquals(3, counts.size)
        assertEquals(setOf("a", "b", "d"), counts.keys)
        assertEquals(1, counts["d"])
    }
}
