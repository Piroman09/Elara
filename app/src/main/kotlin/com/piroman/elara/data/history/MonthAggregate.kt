package com.piroman.elara.data.history

import android.content.Context
import android.util.Log
import com.piroman.elara.data.diagnostics.PlaybackDiagnostics
import com.piroman.elara.data.diagnostics.StartupTiming
import com.piroman.elara.data.model.SafeGson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * One calendar month of listening, for the Wrapped recap screen.
 *
 * Everything here is derived from events [WrappedStatsManager] receives —
 * plays from `PlaybackManager.playAt` (the single owner of that moment, same
 * as history) and listening time from the position ticker (which already
 * filters pauses and seeks for `ListeningStatsManager`). No new tracking
 * surfaces: Wrapped is a *presentation* of what playback already reports.
 *
 * **Persisted via Gson, so its field names are load-bearing** — the same rule
 * as `PlayStat`: the class is registered in `proguard-rules.pro` and in the
 * `requiredTypes`/`requiredFields` lists of `verify-release-dex.gradle`.
 * Losing the registration loses the month silently.
 *
 * Deliberately not part of [ElaraBackup] yet: a reinstall resetting "this
 * month" is the same blast radius as the existing listening total, and the
 * backup document's shape is versioned — adding a field belongs with a real
 * restore test, not a feature drive-by.
 */
data class MonthAggregate(
    /** `YearMonth.toString()` form, e.g. `2026-09`. Also the rollover key. */
    val month: String = "",
    /** Listening time accumulated this month, milliseconds. */
    val ms: Long = 0,
    /** Plays recorded this month. */
    val plays: Int = 0,
    /** Track id → play count this month, so the recap can rank songs. */
    val trackCounts: Map<String, Int> = emptyMap()
) {
    companion object {
        /**
         * Pure rollover: returns [current] when it belongs to [nowMonth],
         * otherwise a fresh aggregate for the new month. A pure function so
         * the rollover rule — the only way Wrapped can silently lie about
         * when listening happened — is unit-testable.
         *
         * This is also the whole retention policy: only the current month is
         * ever carried forward, so the recap shows one month (as designed) and
         * the stored document cannot accumulate stale ones. There is
         * deliberately no pruning constant to keep in step with this.
         */
        fun rollover(current: MonthAggregate?, nowMonth: String): MonthAggregate =
            if (current != null && current.month == nowMonth) current
            else MonthAggregate(month = nowMonth)

        /**
         * Pure count merge with a cap: when [counts] grows past [cap], the
         * least-played entries are dropped first, exactly like
         * `PlayHistoryManager`'s play-count pruning. Without a cap a heavy
         * month grows the stored map without bound.
         *
         * Ties go to the **newest** track: the input map is insertion-ordered
         * (Gson round-trips a LinkedHashMap), so reversing before the stable
         * sort makes later-added tracks win equal counts — this month's new
         * discovery stays ranked over something played once long ago.
         */
        fun bumpCounts(counts: Map<String, Int>, trackId: String, cap: Int): Map<String, Int> {
            val bumped = counts.toMutableMap()
            bumped[trackId] = (bumped[trackId] ?: 0) + 1
            if (bumped.size <= cap) return bumped
            val keep = bumped.entries.toList().asReversed()
                .sortedByDescending { it.value }
                .take(cap)
            return keep.associate { it.key to it.value }
        }
    }
}

/**
 * Tracks the current month's plays and listening time for Wrapped.
 *
 * Same durability posture as [PlayHistoryManager]: state updates happen on
 * the caller (playback thread / main), writes are coalesced onto one
 * background thread and land with `commit()`, so a process kill loses at
 * most the coalescing window — never a half-written document.
 */
class WrappedStatsManager private constructor(context: Context) {

    companion object {
        private const val TAG = "WrappedStats"
        private const val PREFS = "elara_settings"
        private const val KEY = "month_aggregate"

        /** Cap on tracked track ids per month, mirroring [bumpCounts]. */
        private const val MAX_TRACKS = 300

        /**
         * How much listening time may accumulate before it is written to
         * disk.
         *
         * The same figure, and the same reasoning, as
         * [com.piroman.elara.data.stats.ListeningStatsManager]: the position
         * ticker advances time every 400 ms, so persisting per call rewrote
         * the whole prefs file ~2.5 times a second for as long as music
         * played. This bounds both the write rate and the worst case loss on
         * process death.
         */
        private const val PERSIST_INTERVAL_MS = 5_000L

        @Volatile private var instance: WrappedStatsManager? = null

        fun getInstance(context: Context): WrappedStatsManager =
            instance ?: synchronized(this) {
                instance ?: WrappedStatsManager(context.applicationContext)
                    .also { instance = it }
            }

        /** The month key used for rollover decisions. Isolated for tests. */
        fun currentMonthKey(): String = YearMonth.now().toString()
    }

    private val gson = SafeGson.instance
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val saveDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wrapped-save").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)
    private val saveQueued = AtomicBoolean(false)
    @Volatile private var pendingSave: Job? = null

    /**
     * Milliseconds of listening time accumulated since the last disk write.
     * See [addListeningTime] and [flush].
     *
     * An `AtomicLong` rather than the plain counter its sibling in
     * `ListeningStatsManager` uses: there, every touch comes from the position
     * ticker, but here [flush] is also reached from `MainActivity.onStop` and
     * from [com.piroman.elara.playback.PlaybackManager.release], which are not
     * that coroutine's thread.
     */
    private val msSincePersist = AtomicLong(0L)

    private val _aggregate = MutableStateFlow(
        StartupTiming.measure("WrappedStatsManager.load") { load() }
    )
    val aggregate: StateFlow<MonthAggregate> = _aggregate

    /** A play happened. Called from `PlaybackManager.playAt`. */
    fun recordPlay(trackId: String) {
        // `rollover` returns a fresh zeroed aggregate when the month turned,
        // so plays and counts never leak across a month boundary.
        val base = MonthAggregate.rollover(_aggregate.value, currentMonthKey())
        val withCounts = MonthAggregate.bumpCounts(base.trackCounts, trackId, MAX_TRACKS)
        _aggregate.value = base.copy(plays = base.plays + 1, trackCounts = withCounts)
        persist()
    }

    /**
     * Listening time advanced. Called from the position ticker, real time only.
     *
     * The in-memory total moves on every call so the recap is live, but the
     * **disk write is batched** every [PERSIST_INTERVAL_MS]. At most that much
     * listening is lost to a process death — the same trade the playback
     * position makes — and [flush] closes the window at every pause and at
     * shutdown.
     */
    fun addListeningTime(ms: Long) {
        if (ms <= 0) return
        val base = MonthAggregate.rollover(_aggregate.value, currentMonthKey())
        _aggregate.value = base.copy(ms = base.ms + ms)
        if (msSincePersist.addAndGet(ms) < PERSIST_INTERVAL_MS) return
        msSincePersist.set(0L)
        persist()
    }

    /**
     * Writes any pending listening time immediately.
     *
     * A no-op when nothing is pending, which is what lets the position ticker
     * call it on every tick while playback is stopped without causing a write
     * storm. Like every other write here it is handed to the save thread, so
     * this does not block the caller on disk I/O.
     */
    fun flush() {
        if (msSincePersist.getAndSet(0L) == 0L) return
        persist()
    }

    private fun persist() {
        if (!saveQueued.compareAndSet(false, true)) return
        pendingSave = saveScope.launch {
            saveQueued.set(false)
            write()
        }
    }

    private fun write() {
        try {
            prefs.edit()
                .putString(KEY, gson.toJson(_aggregate.value))
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "save: ${e.message}")
            PlaybackDiagnostics.append(appContext, "wrapped: SAVE FAILED ${e.message}")
        }
    }

    private fun load(): MonthAggregate = try {
        val json = prefs.getString(KEY, null) ?: return MonthAggregate(month = currentMonthKey())
        val loaded = gson.fromJson(json, MonthAggregate::class.java)
            ?: return MonthAggregate(month = currentMonthKey())
        // A persisted aggregate from an earlier month is stale the moment it
        // loads — roll it forward so the UI never sees a foreign month.
        MonthAggregate.rollover(loaded, currentMonthKey())
    } catch (e: Exception) {
        Log.e(TAG, "load: ${e.message}")
        PlaybackDiagnostics.append(appContext, "wrapped: LOAD FAILED ${e.message}")
        MonthAggregate(month = currentMonthKey())
    }
}
