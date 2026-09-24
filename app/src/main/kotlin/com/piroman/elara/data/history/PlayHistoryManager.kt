package com.piroman.elara.data.history

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.piroman.elara.data.diagnostics.PlaybackDiagnostics
import com.piroman.elara.data.diagnostics.StartupTiming
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.model.listTypeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One track plus how many times it has been played.
 *
 * [track] is **nullable and defaulted, and both are load-bearing.** A parameter
 * with no default stops Kotlin emitting a synthetic no-arg constructor, so Gson
 * falls back to `Unsafe` allocation and writes a *missing* or `null` key
 * straight into the field — a runtime null inside a property the compiler
 * believes is non-null, which then escapes its type until something calls a
 * member on it. Defaulting it restores the no-arg path; the nullable type makes
 * the remaining case explicit instead of invisible. Entries without a track are
 * dropped on load.
 *
 * **Persisted via Gson, so its field names are load-bearing** — R8 is on and
 * `-dontobfuscate` alone does not stop shrinking. See `proguard-rules.pro` and
 * the `requiredTypes` list in `verify-release-dex.gradle`.
 */
@Immutable
data class PlayStat(
    val track: Track? = null,
    val count: Int = 0
)

/**
 * Most-recently-played tracks (newest first) and per-track play counts,
 * persisted across launches.
 *
 * The two live under **separate SharedPreferences keys on purpose.**
 * `recently_played` holds a bare `List<Track>` and is written by every play;
 * `play_stats` holds `List<PlayStat>` and is written by every play too. Folding
 * counts into the existing key would have meant migrating a format that
 * existing installs already have on disk, for no benefit — and a failed
 * migration here loses listening history.
 *
 * **Durability.** Writes go through [persist] rather than being handed straight
 * to `SharedPreferences.apply()`. `apply()` only *queues* the write on a
 * background thread and returns, so a process that dies before the queue drains
 * — a crash, a force-stop, an OEM task killer — loses it with no error
 * anywhere; "Recently played is empty after reopening" is that failure, and its
 * visible symptom is identical to "nothing was ever recorded", which is what
 * made it hard to pin down. [persist] writes with `commit()` on a single
 * background thread and [flushPending] lets `onStop` wait for it, so the bytes
 * are on disk before the process can be killed. The serialised *format* is
 * unchanged — this is only about when the bytes land.
 */
class PlayHistoryManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PlayHistory"
        private const val PREFS = "elara_settings"
        private const val KEY = "recently_played"
        private const val KEY_STATS = "play_stats"
        private const val MAX_ENTRIES = 20

        /**
         * How many tracks keep a play count. Past this the least-played are
         * dropped, so a long tail of one-off plays cannot grow the prefs file
         * without bound.
         */
        private const val MAX_STATS = 200

        /**
         * Plays a track needs before it is "most played". One play is just
         * "recently played", which the Home screen already shows — without this
         * threshold the two shelves would be identical on a fresh install.
         */
        const val MOST_PLAYED_MIN_PLAYS = 2

        private const val MOST_PLAYED_LIMIT = 20

        @Volatile private var instance: PlayHistoryManager? = null

        fun getInstance(context: Context): PlayHistoryManager =
            instance ?: synchronized(this) {
                instance ?: PlayHistoryManager(context.applicationContext).also { instance = it }
            }
    }

    private val gson = SafeGson.instance

    /** Retained only so [load] and [record] can write diagnostics lines. */
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * One dedicated thread, deliberately not `Dispatchers.IO`: that pool is
     * parallel, and two overlapping rewrites of the same prefs file is a
     * problem this class does not need to think about.
     *
     * `newSingleThreadExecutor` starts its thread lazily, so nothing is spawned
     * until the first play actually needs saving.
     */
    private val saveDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "history-save").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)

    /** True while a write is queued or running. See [persist] for why. */
    private val saveQueued = AtomicBoolean(false)

    /** The most recent queued write, so [flushPending] can wait for it. */
    @Volatile private var pendingSave: Job? = null

    /**
     * Blocks until the queued write has landed.
     *
     * Called from the Activity's `onStop` — the last callback guaranteed to run
     * before the system may kill the process — so history recorded moments
     * before the user leaves is on disk rather than in a queue that dies with
     * us. Android makes the same trade for `SharedPreferences.apply()` by
     * blocking the main thread to drain its queue in
     * `ActivityThread.handleStopActivity`; doing it for our own `commit()` is
     * the same few milliseconds spent for a guarantee instead of a hope.
     *
     * `runCatching` because `join()` on a cancelled job throws, and this is
     * cleanup: it must never take the app down on the way out.
     */
    fun flushPending() {
        val job = pendingSave ?: return
        runBlocking { runCatching { job.join() } }
    }

    private val _recent =
        MutableStateFlow<List<Track>>(StartupTiming.measure("PlayHistoryManager.load") { load() })
    val recent: StateFlow<List<Track>> = _recent

    private val _stats = MutableStateFlow<List<PlayStat>>(
        StartupTiming.measure("PlayHistoryManager.loadStats") { loadStats() }
    )

    /** Every tracked play count, newest-track-first. Unordered by play count. */
    val stats: StateFlow<List<PlayStat>> = _stats

    /**
     * Tracks played at least [MOST_PLAYED_MIN_PLAYS] times, most-played first.
     * Derived state, recomputed only when [stats] changes — the Home shelf reads
     * this rather than sorting on every recomposition.
     */
    private val _mostPlayed = MutableStateFlow(deriveMostPlayed(_stats.value))
    val mostPlayed: StateFlow<List<Track>> = _mostPlayed

    fun record(track: Track) {
        val updated = (listOf(track) + _recent.value.filterNot { it.id == track.id }).take(MAX_ENTRIES)
        _recent.value = updated
        bumpPlayCount(track)
        // One durable write for both keys, after both have been updated.
        // Previously this was two independent apply() calls — one here and one
        // inside bumpPlayCount — which rewrote the whole prefs file twice per
        // play and could leave the two keys describing different moments.
        persist()
    }

    /**
     * Replaces both history and play counts wholesale. Used by backup restore.
     *
     * Writes both keys in one `edit()` so a crash between them cannot leave the
     * recent list and the stats list describing different libraries.
     */
    fun replaceAll(recent: List<Track>, stats: List<PlayStat>) {
        val trimmedRecent = recent.take(MAX_ENTRIES)
        val trimmedStats = if (stats.size > MAX_STATS) {
            stats.sortedByDescending { it.count }.take(MAX_STATS)
        } else stats

        _recent.value = trimmedRecent
        _stats.value = trimmedStats
        _mostPlayed.value = deriveMostPlayed(trimmedStats)

        // Same single-write path as every other mutation, so a restore cannot
        // leave the recent list and the stats list describing different
        // libraries.
        persist()
    }

    private fun bumpPlayCount(track: Track) {
        val current = _stats.value.toMutableList()
        val index = current.indexOfFirst { it.track?.id == track.id }
        if (index >= 0) {
            // Refresh the stored metadata too: a track's artwork URL or title can
            // change between plays, and the newest copy is the accurate one.
            val existing = current[index]
            current[index] = existing.copy(track = track, count = existing.count + 1)
        } else {
            current.add(PlayStat(track, 1))
        }

        val pruned =
            if (current.size > MAX_STATS) current.sortedByDescending { it.count }.take(MAX_STATS)
            else current

        _stats.value = pruned
        _mostPlayed.value = deriveMostPlayed(pruned)
        // Deliberately no write here: [record] and [replaceAll] call [persist]
        // once, after this has finished, so both keys land together.
    }

    private fun deriveMostPlayed(stats: List<PlayStat>): List<Track> =
        stats.asSequence()
            .filter { it.count >= MOST_PLAYED_MIN_PLAYS }
            .sortedByDescending { it.count }
            // mapNotNull, not map: see PlayStat's note on why `track` is nullable.
            .mapNotNull { it.track }
            .take(MOST_PLAYED_LIMIT)
            .toList()

    /**
     * Persists both keys off the calling thread, coalescing bursts.
     *
     * Callers update the state flows *before* calling this, so the UI never
     * waits on disk. The write is `commit()`, not `apply()`, so it is on disk
     * before the coroutine completes — which is what makes [flushPending] a
     * real guarantee.
     *
     * Calls that arrive while a write is already pending are coalesced into it
     * rather than queueing one write per play. That is only correct because the
     * coroutine below reads the state flows *itself*, at the moment it runs — so
     * a coalesced-away call is still covered by the write that follows it. Do
     * not hoist those reads up to the call site: capturing a snapshot here would
     * silently drop the coalesced plays.
     */
    private fun persist() {
        if (!saveQueued.compareAndSet(false, true)) return
        pendingSave = saveScope.launch {
            // Cleared before the reads so that a play arriving *during* this
            // write queues a follow-up write instead of being skipped.
            saveQueued.set(false)
            writeBothKeys()
        }
    }

    /**
     * Writes `recently_played` and `play_stats` in a single `edit()`.
     *
     * One commit rather than two is not only fewer bytes: it means the two keys
     * can never be read back describing different moments, which a crash between
     * two separate writes would allow.
     *
     * A failed write is logged and reported through [PlaybackDiagnostics], never
     * thrown — a save must not take playback down, and `commit()` returns
     * `false` rather than throwing when it cannot write.
     */
    private fun writeBothKeys() {
        try {
            val ok = prefs.edit()
                .putString(KEY, gson.toJson(_recent.value))
                .putString(KEY_STATS, gson.toJson(_stats.value))
                .commit()
            if (ok) {
                PlaybackDiagnostics.append(
                    appContext,
                    "history: saved ${_recent.value.size} recent / ${_stats.value.size} stats"
                )
            } else {
                PlaybackDiagnostics.append(appContext, "history: SAVE FAILED (commit returned false)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "save: ${e.message}")
            PlaybackDiagnostics.append(appContext, "history: SAVE FAILED ${e.message}")
        }
    }

    private fun loadStats(): List<PlayStat> = try {
        val type = listTypeOf(PlayStat::class.java)
        val loaded: List<PlayStat>? = gson.fromJson(prefs.getString(KEY_STATS, null), type)
        // Drop entries Gson could not fully rebuild. A PlayStat with no track is
        // unusable, and keeping it would surface later as a null in the UI.
        loaded?.filter { it.track != null } ?: emptyList()
    } catch (e: Exception) {
        // Never fatal, but never silent: see [load].
        Log.e(TAG, "loadStats: ${e.message}")
        PlaybackDiagnostics.append(appContext, "history: stats LOAD FAILED ${e.message}")
        emptyList()
    }

    private fun load(): List<Track> = try {
        val type = listTypeOf(Track::class.java)
        val loaded: List<Track>? = gson.fromJson(prefs.getString(KEY, null), type)
        val result = loaded ?: emptyList()
        // The count settles "is this a write problem or a display problem": a
        // non-zero count on a launch that still shows an empty shelf points at
        // the UI, a zero points at the write. Without it the two are
        // indistinguishable, which is exactly why this took so long to pin down.
        PlaybackDiagnostics.append(appContext, "history: loaded ${result.size}")
        result
    } catch (e: Exception) {
        // A parse failure here is the *only* way persisted history can vanish,
        // and its visible symptom is indistinguishable from "nothing was ever
        // recorded" — the shelf just shows the Quick picks fallback. Returning
        // `emptyList()` is still the right recovery (history is a nicety, not
        // something worth crashing the launch over), but it used to do so with
        // no log line at all, which made the one user-visible failure the one
        // failure with no trace. `PlayHistoryRoundTripTest` pins the format so
        // this should not fire; if it does, the message names the reason.
        Log.e(TAG, "load: ${e.message}")
        PlaybackDiagnostics.append(
            appContext,
            "history: LOAD FAILED ${e.message} — shelf falls back to Quick picks"
        )
        emptyList()
    }
}
