package com.piroman.elara.playback

/**
 * Pure smoothing + shaping logic for the playback visualizer.
 *
 * The audio tap ([SpectrumTapper]) delivers raw FFT magnitudes — 0..255 per
 * frequency bin, exactly as `Visualizer.getFft` and Media3's `TeeAudioProcessor`
 * pipeline surface them — and the UI draws bars from what this returns.
 *
 * Why a smoothing pass at all: raw FFT frames flicker violently frame to
 * frame. Every music visualizer you have ever liked applies some form of
 * attack/decay — bars jump up instantly (attack) but fall slowly (decay) —
 * which reads as "dancing to the music" instead of noise. The attack/decay
 * pair below is that standard trick, expressed as pure functions so it can be
 * unit-tested without audio hardware.
 *
 * The frequency mapping is also deliberate: FFT bins are *linear* in
 * frequency, but music energy lives mostly in the low bins — voice, bass and
 * rhythm occupy the first ~10% of the spectrum, and the top half is nearly
 * silent hiss. Drawing one bar per linear bin makes a visualizer where only
 * the left few bars ever move. Bins are therefore sampled on a **logarithmic
 * curve**, so each bar covers a similar musical interval (an octave-ish feel)
 * and the whole row dances.
 */
object SpectrumAnalyzer {

    /**
     * Number of bars the UI draws. 24 is the sweet spot for a full-width
     * player row on a phone: dense enough to read as a spectrum, sparse
     * enough that each bar's motion is visible.
     */
    const val BAR_COUNT = 24

    /** How fast a bar rises to a new peak, per frame (0..1). Instant. */
    private const val ATTACK = 0.9f

    /** How fast a bar falls toward a new low, per frame (0..1). The "decay". */
    private const val DECAY = 0.18f

    /** Bin values at or below this are treated as silence for a bar. */
    private const val FLOOR = 4f

    /**
     * Maps [barCount] bars onto the [binCount] FFT bins on a logarithmic-ish
     * curve, returning each bar's first (inclusive) and last (exclusive) bin.
     *
     * Bar 0 always starts at bin 1 (bin 0 is DC and carries no musical
     * content), and the last bar always ends at [binCount] so the top of the
     * spectrum is not wasted. The exponent 1.8 (rather than the strict log
     * value) shapes the curve so bass — where the visual interest is — gets
     * several distinct bars without starving the treble entirely.
     *
     * Boundaries are forced strictly increasing after the curve is evaluated:
     * with 24 bars over 63 usable bins the raw curve is flat enough at the
     * bass end that several bars would otherwise collapse onto the same bin,
     * drawing identical (and partly dead) bars. Requires [barCount] ≤ usable
     * bins; callers use 24 over 64.
     */
    fun barBinRanges(barCount: Int, binCount: Int): List<IntRange> {
        require(barCount > 0 && binCount > 0) { "barCount/binCount must be positive" }
        val usableBins = binCount - 1 // skip DC at index 0
        // Boundary i is the first bin of bar i; boundary barCount is the end.
        val bounds = IntArray(barCount + 1)
        bounds[0] = 1
        for (i in 1..barCount) {
            val curved = (Math.pow(i.toDouble() / barCount, 1.8) * usableBins).toInt()
            bounds[i] = (1 + curved).coerceAtMost(binCount)
        }
        // Strictly increasing: every bar owns at least one bin, none overlap.
        for (i in 1..barCount) {
            bounds[i] = maxOf(bounds[i], bounds[i - 1] + 1).coerceAtMost(binCount)
        }
        bounds[barCount] = binCount

        return (0 until barCount).map { i -> bounds[i] until bounds[i + 1] }
    }

    /**
     * Turns one raw FFT magnitude frame into [barCount] bar heights 0..1.
     *
     * Each bar takes the **max** of its bins rather than the average: an
     * average dilutes a sharp kick drum across the bins it spans, and the bar
     * stops reaching the top. Max keeps transients punchy.
     */
    fun magnitudesToBars(
        magnitudes: FloatArray,
        ranges: List<IntRange>,
        barCount: Int = BAR_COUNT
    ): FloatArray {
        val bars = FloatArray(barCount)
        for (i in 0 until barCount) {
            val range = ranges.getOrNull(i) ?: continue
            var peak = 0f
            for (bin in range) {
                if (bin in magnitudes.indices) {
                    val m = magnitudes[bin]
                    if (m > peak) peak = m
                }
            }
            // Normalise 0..255 → 0..1, drop the noise floor, apply a mild
            // perceptual curve so quiet bars are still visible.
            val normalised = ((peak - FLOOR) / (255f - FLOOR)).coerceIn(0f, 1f)
            bars[i] = Math.pow(normalised.toDouble(), 0.7).toFloat()
        }
        return bars
    }

    /**
     * One step of attack/decay smoothing: `current` moves toward `target`
     * instantly when the target is higher (attack) and by [DECAY] when lower.
     *
     * Exposed so the UI cannot get this wrong and so tests can pin the feel:
     * fast up, slow down is the whole trick.
     */
    fun smooth(current: Float, target: Float): Float =
        if (target > current) {
            current + (target - current) * ATTACK
        } else {
            current + (target - current) * DECAY
        }

    /**
     * Smooths a whole bar array in place, returning the same array for
     * convenience. Bars whose target vanished (silence) decay gracefully
     * instead of snapping to zero.
     */
    fun smoothAll(current: FloatArray, target: FloatArray): FloatArray {
        for (i in current.indices) {
            current[i] = smooth(current[i], target.getOrElse(i) { 0f })
        }
        return current
    }
}
