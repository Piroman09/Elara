package com.piroman.elara.data.source

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Playback behaviour toggles: autoplay and crossfade.
 *
 * Kept in the same `elara_settings` prefs file as [SourcePreferences] so
 * there is not a proliferation of tiny files.
 */
class PlaybackPreferences private constructor(context: Context) {

    companion object {
        private const val PREFS = "elara_settings"
        private const val KEY_AUTOPLAY = "autoplay_enabled"
        private const val KEY_CROSSFADE = "crossfade_seconds"
        private const val KEY_SMART_SHUFFLE = "smart_shuffle_enabled"

        @Volatile private var instance: PlaybackPreferences? = null

        fun getInstance(context: Context): PlaybackPreferences =
            instance ?: synchronized(this) {
                instance ?: PlaybackPreferences(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _autoplayEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled

    fun setAutoplay(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
        _autoplayEnabled.value = value
    }

    /** Crossfade duration in seconds, 0 = off. */
    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt(KEY_CROSSFADE, 0))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds

    fun setCrossfade(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        prefs.edit().putInt(KEY_CROSSFADE, clamped).apply()
        _crossfadeSeconds.value = clamped
    }

    /** When true, shuffle orders tracks by similarity to the current song. */
    private val _smartShuffleEnabled = MutableStateFlow(prefs.getBoolean(KEY_SMART_SHUFFLE, false))
    val smartShuffleEnabled: StateFlow<Boolean> = _smartShuffleEnabled

    fun setSmartShuffle(value: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_SHUFFLE, value).apply()
        _smartShuffleEnabled.value = value
    }
}
