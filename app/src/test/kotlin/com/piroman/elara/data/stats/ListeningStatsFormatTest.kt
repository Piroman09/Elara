package com.piroman.elara.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for the listening-total formatting.
 *
 * These run on the JVM (no emulator) because [ListeningStatsFormat] is
 * deliberately free of Android types.
 *
 * The regression under test: both call sites printed the plural unconditionally,
 * so "1 hour" and "1 minute" rendered as "1 hours" and "1 minutes".
 */
class ListeningStatsFormatTest {

    private val minute = 60_000L
    private val hour = 3_600_000L

    // ── duration (the stat card) ──────────────────────────────────────────

    @Test
    fun `under an hour is reported in minutes`() {
        assertEquals(
            ListeningStatsFormat.Duration("0", "minutes"),
            ListeningStatsFormat.duration(0L)
        )
        assertEquals(
            ListeningStatsFormat.Duration("45", "minutes"),
            ListeningStatsFormat.duration(45 * minute)
        )
    }

    @Test
    fun `a single minute is singular`() {
        assertEquals(
            ListeningStatsFormat.Duration("1", "minute"),
            ListeningStatsFormat.duration(minute)
        )
        assertEquals(
            ListeningStatsFormat.Duration("2", "minutes"),
            ListeningStatsFormat.duration(2 * minute)
        )
    }

    @Test
    fun `a single hour is singular`() {
        // The exact string the old code produced was "1 hours".
        assertEquals(
            ListeningStatsFormat.Duration("1", "hour"),
            ListeningStatsFormat.duration(hour)
        )
        assertEquals(
            ListeningStatsFormat.Duration("2", "hours"),
            ListeningStatsFormat.duration(2 * hour)
        )
    }

    @Test
    fun `a whole hour never leaks a remainder`() {
        // 59 minutes must stay in minutes, 60 must not still say 60.
        assertEquals(
            ListeningStatsFormat.Duration("59", "minutes"),
            ListeningStatsFormat.duration(59 * minute)
        )
        assertEquals(
            ListeningStatsFormat.Duration("1", "hour"),
            ListeningStatsFormat.duration(60 * minute)
        )
    }

    @Test
    fun `an hour with a remainder folds the minutes into the unit`() {
        assertEquals(
            ListeningStatsFormat.Duration("1", "h 30m"),
            ListeningStatsFormat.duration(hour + 30 * minute)
        )
        assertEquals(
            ListeningStatsFormat.Duration("3", "h 5m"),
            ListeningStatsFormat.duration(3 * hour + 5 * minute)
        )
    }

    @Test
    fun `partial minutes are truncated, not rounded up`() {
        // 59.9 seconds has not reached a minute yet.
        assertEquals(
            ListeningStatsFormat.Duration("0", "minutes"),
            ListeningStatsFormat.duration(59_900L)
        )
    }

    @Test
    fun `negative input is clamped instead of rendering a minus sign`() {
        assertEquals(
            ListeningStatsFormat.Duration("0", "minutes"),
            ListeningStatsFormat.duration(-5 * hour)
        )
    }

    // ── listenedLabel (the library row) ───────────────────────────────────

    @Test
    fun `the label never prints a plural for one`() {
        assertEquals("1 minute listened", ListeningStatsFormat.listenedLabel(minute))
        assertEquals("1 hour listened", ListeningStatsFormat.listenedLabel(hour))
        assertEquals(
            "1 hour 1 minute listened",
            ListeningStatsFormat.listenedLabel(hour + minute)
        )
    }

    @Test
    fun `the label pluralises correctly`() {
        assertEquals("0 minutes listened", ListeningStatsFormat.listenedLabel(0L))
        assertEquals("45 minutes listened", ListeningStatsFormat.listenedLabel(45 * minute))
        assertEquals("2 hours listened", ListeningStatsFormat.listenedLabel(2 * hour))
        assertEquals(
            "1 hour 30 minutes listened",
            ListeningStatsFormat.listenedLabel(hour + 30 * minute)
        )
        assertEquals(
            "2 hours 15 minutes listened",
            ListeningStatsFormat.listenedLabel(2 * hour + 15 * minute)
        )
    }

    @Test
    fun `the two forms never disagree about the leading number`() {
        // Both forms come from the same decomposition, so the big number on the
        // stat card must always be the first word of the library label.
        val samples = listOf(
            0L, minute, 59 * minute, hour, hour + minute,
            2 * hour + 15 * minute, 11 * hour + 59 * minute
        )
        for (ms in samples) {
            val value = ListeningStatsFormat.duration(ms).value
            val label = ListeningStatsFormat.listenedLabel(ms)
            assertEquals(
                "for $ms the card said \"$value\" but the label said \"$label\"",
                value,
                label.substringBefore(' ')
            )
        }
    }

    @Test
    fun `neither form ever contains a plural noun directly after a one`() {
        // The exact regression, swept across every minute of three days rather
        // than at a handful of hand-picked values.
        //
        // Word-by-word, not `contains`: "11 hours" contains the substring
        // "1 hours", which would make a substring check fail for a correct
        // implementation. The bug is a *token* "1" followed by a plural token.
        val pluralNouns = setOf("hours", "minutes")

        fun assertNoSingularPlural(text: String, source: String) {
            val words = text.split(' ')
            words.forEachIndexed { i, word ->
                if (word == "1" && i + 1 < words.size) {
                    assertFalse(
                        "\"$text\" (from $source) says \"1 ${words[i + 1]}\"",
                        words[i + 1] in pluralNouns
                    )
                }
            }
        }

        for (minutes in 0L..(60L * 72)) {
            val ms = minutes * minute
            assertNoSingularPlural(ListeningStatsFormat.listenedLabel(ms), "$minutes min")
            assertNoSingularPlural(ListeningStatsFormat.duration(ms).unit, "$minutes min")
        }
    }
}
