package com.piroman.elara.playback

/**
 * Pure arithmetic for the player's volume fades.
 *
 * Two fades can be active at once — the outro fade-out driven by the fade-out
 * setting, and the sleep-timer fade-out — and they used to write
 * `exoPlayer.volume` directly, so whichever ran last won. Concretely, the 400 ms
 * position ticker reset the sleep fade to full volume whenever fade-out was on,
 * which silently defeated the bedtime fade.
 *
 * They now compose by multiplication, and this object computes that product.
 *
 * Deliberately free of Android and ExoPlayer types so it can be exercised
 * directly by JVM unit tests (see `VolumeMixTest`).
 */
internal object VolumeMix {

    /**
     * True when [positionMs] sits inside the outro fade window — i.e. the fade
     * is enabled and there is still between 1 ms and [crossfadeMs] of track left.
     *
     * A non-positive [durationMs] means the player has not reported a duration
     * yet, so there is nothing to fade against.
     */
    fun isInsideFadeWindow(positionMs: Long, durationMs: Long, crossfadeMs: Long): Boolean {
        if (crossfadeMs <= 0L || durationMs <= 0L) return false
        val remaining = durationMs - positionMs
        return remaining in 1..crossfadeMs
    }

    /**
     * Fade multiplier for a track with [remainingMs] left. 1f means "not fading",
     * 0f means "fully faded". Clamped so a caller that overshoots (a tick landing
     * past the end) cannot produce a negative or greater-than-unity volume.
     */
    fun fadeFactorForRemaining(remainingMs: Long, crossfadeMs: Long): Float =
        if (crossfadeMs <= 0L) {
            1f
        } else {
            (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * The outro fade multiplier at [positionMs]. Exactly 1f outside the window —
     * including *past* the end of the track, so a finished track never reports a
     * stale fully-faded volume to whoever reads it next.
     */
    fun crossfadeFactor(positionMs: Long, durationMs: Long, crossfadeMs: Long): Float =
        if (isInsideFadeWindow(positionMs, durationMs, crossfadeMs)) {
            fadeFactorForRemaining(durationMs - positionMs, crossfadeMs)
        } else {
            1f
        }

    /**
     * The sleep-timer fade multiplier at [step] of [fadeSteps]. Counting *down*
     * (step = fadeSteps → 1f, step = 0 → 0f) is what the caller's loop does, so
     * this mirrors it rather than inventing its own direction.
     */
    fun sleepFadeFactor(step: Int, fadeSteps: Int): Float =
        if (fadeSteps <= 0) {
            1f
        } else {
            (step.toFloat() / fadeSteps.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * The player's volume for two independent fade multipliers. Multiplicative
     * rather than last-writer-wins, so neither fade can cancel the other.
     */
    fun mix(crossfadeFactor: Float, sleepFadeFactor: Float): Float =
        (crossfadeFactor * sleepFadeFactor).coerceIn(0f, 1f)
}
