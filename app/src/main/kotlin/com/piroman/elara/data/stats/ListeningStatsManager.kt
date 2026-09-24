package com.piroman.elara.data.stats

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How much music the user has actually listened to, and how many unique
 * tracks that covers. Updated live during playback and persisted to disk so
 * reinstalling the app is the only way to lose it.
 *
 * Time is accumulated in the playback ticker (see [PlaybackManager]) so it
 * only counts seconds that were actually heard — pausing stops the clock.
 */
class ListeningStatsManager private constructor(context: Context) {

    companion object {
        private const val PREFS = "elara_stats"
        private const val KEY_TOTAL_MS = "total_ms"
        private const val KEY_TOTAL_TRACKS = "total_tracks"
        private const val KEY_TRACK_IDS = "track_ids"

        /**
         * How much listening time may accumulate before it is written to disk.
         * Bounds both the write rate and the worst-case loss on process death.
         */
        private const val PERSIST_INTERVAL_MS = 5_000L

        @Volatile private var instance: ListeningStatsManager? = null

        fun getInstance(context: Context): ListeningStatsManager =
            instance ?: synchronized(this) {
                instance ?: ListeningStatsManager(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Listening time accumulated since the last disk write. See [addTime]. */
    private var msSincePersist = 0L

    private val _totalMs = MutableStateFlow(prefs.getLong(KEY_TOTAL_MS, 0L))
    val totalMs: StateFlow<Long> = _totalMs

    private val _totalTracks = MutableStateFlow(prefs.getInt(KEY_TOTAL_TRACKS, 0))
    val totalTracks: StateFlow<Int> = _totalTracks

    private val _trackIds = MutableStateFlow(loadTrackIds())
    val trackIds: StateFlow<Set<String>> = _trackIds

    /**
     * Adds milliseconds to the running total. Called from the position ticker
     * when playback is actually advancing (not while paused or during a seek).
     *
     * The in-memory total moves on every call so the UI stays live, but the
     * **disk write is batched**. The ticker runs every 400 ms, so writing each
     * time rewrote the whole preferences file ~2.5 times a second — ~9,000
     * writes an hour of listening, for a counter nobody reads in between.
     *
     * At most [PERSIST_INTERVAL_MS] of listening time is lost to a process death,
     * which is the same trade the playback-position persistence already makes.
     * [flush] closes the window at every pause.
     */
    fun addTime(ms: Long) {
        if (ms <= 0) return
        val next = _totalMs.value + ms
        _totalMs.value = next

        msSincePersist += ms
        if (msSincePersist >= PERSIST_INTERVAL_MS) {
            msSincePersist = 0L
            prefs.edit().putLong(KEY_TOTAL_MS, next).apply()
        }
    }

    /**
     * Write any pending total to disk immediately.
     *
     * Called when playback stops and from [PlaybackManager.release], so a pause
     * or a shutdown never leaves the last few seconds unrecorded. A no-op when
     * nothing is pending, which is what lets the ticker call it on every tick
     * while paused without causing a write storm.
     */
    fun flush() {
        if (msSincePersist == 0L) return
        msSincePersist = 0L
        prefs.edit().putLong(KEY_TOTAL_MS, _totalMs.value).apply()
    }

    /** Remembers that this track has been played at least once. */
    fun recordTrack(id: String) {
        val current = _trackIds.value
        if (id in current) return
        val next = current + id
        _trackIds.value = next
        _totalTracks.value = next.size
        prefs.edit()
            .putStringSet(KEY_TRACK_IDS, next)
            .putInt(KEY_TOTAL_TRACKS, next.size)
            .apply()
    }

    private fun loadTrackIds(): Set<String> =
        prefs.getStringSet(KEY_TRACK_IDS, emptySet()) ?: emptySet()
}
