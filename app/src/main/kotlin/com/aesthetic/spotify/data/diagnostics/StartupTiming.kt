package com.aesthetic.spotify.data.diagnostics

import android.content.Context

/**
 * Cold-start timing for the work that runs on the **main thread** while
 * [com.aesthetic.spotify.ui.MainViewModel] is being constructed.
 *
 * Why this exists: the ViewModel's field initializers construct five process
 * singletons, and each of those does its own disk read (or player construction)
 * on the calling thread. The cost scales with library size — a Gson parse per
 * `Track`, 15 fields each — so the only honest way to decide whether the
 * restructuring is worth its risk is to measure it on a real install. That is
 * what this records.
 *
 * Two deliberate properties:
 *
 *  1. **Timings buffer in memory and [flush] writes a single line.** Recording
 *     per-operation would add several file writes to the very startup path being
 *     measured.
 *  2. **`System.nanoTime()`, not `SystemClock`.** The mockable `android.jar` in
 *     `testDebugUnitTest` throws on unmocked framework calls (which is why
 *     `testOptions.unitTests.isReturnDefaultValues` must stay off), and
 *     `System.nanoTime()` is monotonic anyway — the right clock for a duration.
 *
 * Read the numbers via the diagnostics button in the music-source dialog.
 *
 * **A line only appears on a genuine cold start.** The singletons are cached, so
 * a second `MainViewModel` in the same process never calls their `load()` again
 * and there is nothing to report.
 */
object StartupTiming {

    private val entries = mutableListOf<Pair<String, Long>>()
    private val processStartNanos = System.nanoTime()

    /** Times [block], recording the label even when the block throws. */
    inline fun <T> measure(label: String, block: () -> T): T {
        val t0 = System.nanoTime()
        return try {
            block()
        } finally {
            record(label, (System.nanoTime() - t0) / 1_000_000)
        }
    }

    fun record(label: String, ms: Long) {
        synchronized(entries) { entries.add(label to ms) }
    }

    /**
     * Writes one summary line and clears the buffer. No-op when nothing was
     * recorded, so a warm ViewModel does not append an empty entry.
     */
    fun flush(context: Context) {
        val snapshot = synchronized(entries) {
            val copy = entries.toList()
            entries.clear()
            copy
        }
        if (snapshot.isEmpty()) return

        val totalMs = snapshot.sumOf { it.second }
        val sinceProcessStartMs = (System.nanoTime() - processStartNanos) / 1_000_000
        val detail = snapshot.joinToString(" ") { "${it.first}=${it.second}ms" }
        PlaybackDiagnostics.append(
            context,
            "startup: ${totalMs}ms on the main thread across ${snapshot.size} loads " +
                "(${sinceProcessStartMs}ms since process start) | $detail"
        )
    }
}
