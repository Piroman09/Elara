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

    /**
     * Fallback reference: when this object was first touched.
     *
     * This is *not* a good denominator on its own. `object` initialization is lazy,
     * so it happens at the first [measure] call — part-way through `MainViewModel`
     * construction, long after the process started. Reporting elapsed time against
     * it silently understates startup and makes the timed loads look like a much
     * larger share of it than they are.
     */
    private val objectInitNanos = System.nanoTime()

    /** Reference point for the elapsed figure in [flush]. Set by [start]. */
    @Volatile private var referenceNanos = 0L

    /**
     * Records the startup reference point. Call once, from `Application.onCreate`,
     * which is the earliest code this app controls. Without it the elapsed figure
     * falls back to [objectInitNanos] and is labelled accordingly.
     */
    fun start() {
        referenceNanos = System.nanoTime()
    }

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
        val reference = referenceNanos
        // Labelled for what it actually measures — "since process start" would be
        // wrong, and would flatter the loads by shrinking the denominator.
        val elapsed = if (reference != 0L) {
            "${(System.nanoTime() - reference) / 1_000_000}ms since Application.onCreate"
        } else {
            "${(System.nanoTime() - objectInitNanos) / 1_000_000}ms since first timed op " +
                "(StartupTiming.start() was never called)"
        }
        val detail = snapshot.joinToString(" ") { "${it.first}=${it.second}ms" }
        PlaybackDiagnostics.append(
            context,
            "startup: ${totalMs}ms on the main thread across ${snapshot.size} loads " +
                "($elapsed) | $detail"
        )
    }
}
