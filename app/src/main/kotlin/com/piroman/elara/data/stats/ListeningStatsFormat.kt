package com.piroman.elara.data.stats

/**
 * Turns a listening total into display text.
 *
 * Extracted because two screens rendered the same number with two different
 * hand-rolled implementations, and both got the same thing wrong: they printed
 * the plural unconditionally, so a one-hour total read "1 hours" and a one-minute
 * total read "1 minutes". The two copies had also drifted apart in format — the
 * stats card said "1 h 30m" while the library row said "1 hours" for the same
 * value.
 *
 * One implementation, one pluralisation rule. Deliberately free of Android types
 * so it can be exercised by JVM unit tests (see `ListeningStatsFormatTest`).
 */
internal object ListeningStatsFormat {

    /** Split form for the stat card, which renders [value] large and [unit] small. */
    data class Duration(val value: String, val unit: String)

    /**
     * The whole number of hours and the remaining whole minutes in [totalMs].
     * Negative input is clamped to zero rather than producing "-1 minutes".
     */
    private data class Parts(val hours: Long, val minutes: Long)

    private fun parts(totalMs: Long): Parts {
        val safe = totalMs.coerceAtLeast(0L)
        return Parts(
            hours = safe / 3_600_000L,
            minutes = (safe % 3_600_000L) / 60_000L
        )
    }

    private fun plural(count: Long, singular: String, plural: String): String =
        if (count == 1L) singular else plural

    /**
     * Split form, e.g. `("1", "h 30m")`, `("2", "hours")`, `("45", "minutes")`.
     *
     * Under an hour the minutes carry the unit; at or over an hour the hours do,
     * with any remainder folded into the unit string as "h {m}m".
     */
    fun duration(totalMs: Long): Duration {
        val (hours, minutes) = parts(totalMs)
        return when {
            hours == 0L -> Duration("$minutes", plural(minutes, "minute", "minutes"))
            minutes == 0L -> Duration("$hours", plural(hours, "hour", "hours"))
            else -> Duration("$hours", "h ${minutes}m")
        }
    }

    /**
     * Single-line form for the library row, e.g. `"1 hour 30 minutes listened"`,
     * `"2 hours listened"`, `"1 minute listened"`.
     */
    fun listenedLabel(totalMs: Long): String {
        val (hours, minutes) = parts(totalMs)
        return when {
            hours == 0L -> "$minutes ${plural(minutes, "minute", "minutes")} listened"
            minutes == 0L -> "$hours ${plural(hours, "hour", "hours")} listened"
            else ->
                "$hours ${plural(hours, "hour", "hours")} " +
                    "$minutes ${plural(minutes, "minute", "minutes")} listened"
        }
    }
}
