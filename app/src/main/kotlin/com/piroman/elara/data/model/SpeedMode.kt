package com.piroman.elara.data.model

/**
 * The three playback "speed modes" the player supports.
 *
 * [speed] is applied as *both* the rate and the pitch factor, which is what
 * gives each mode its character:
 *
 *  - [SLOWED_REVERB] drops rate and pitch together — the "slowed + reverb"
 *    trend. The name is the cultural one: there is no DSP reverb here, just
 *    the slowed, low-pitched feel the trend actually sounds like. (An
 *    `EnvironmentalReverb` is attached on top when the device supports it —
 *    see PlaybackManager.applySpeedMode.)
 *  - [NIGHTCORE] raises both — the fast, bright counterpart.
 *
 * Pure data on purpose: the UI labels and the ExoPlayer parameters both read
 * from this enum, and the value is persisted as `name` in `elara_settings`.
 */
enum class SpeedMode(val speed: Float, val label: String) {
    NORMAL(1.00f, "Normal"),
    SLOWED_REVERB(0.85f, "Slowed"),
    NIGHTCORE(1.25f, "Nightcore");

    companion object {
        /** Tolerant parse for values persisted by older builds. */
        fun fromNameOrDefault(name: String?): SpeedMode =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
