package com.piroman.elara.data.playlist

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
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fallback for a playlist whose stored name is missing or blank.
 *
 * One source of truth, shared by [Playlist]'s default (which is what makes a
 * *missing* key safe) and by [PlaylistManager.create] (which is what makes a
 * *blank* one safe). They were two independent string literals before.
 */
internal const val DEFAULT_NAME = "New Playlist"

/**
 * Repairs the fields a previous build could have written as null.
 *
 * `tracks` was the only field repaired originally, because it was the only one
 * observed. The others are the same failure: Gson writes an explicit `null` for
 * a field that is null in memory, and a load that does not repair it reads null
 * *again* — a corruption that survives every restart and is re-saved each time.
 * Repairing on load means the next save writes a good document, so the store
 * heals instead of staying broken.
 *
 * `id` matters most: it is the `LazyColumn` key and the argument to
 * `PlaylistManager.update`/`delete`, so a null id makes a playlist impossible to
 * open or remove. `name` is rendered and compared with `equals`, so
 * `findByName` would throw on it.
 *
 * Top-level and `internal` rather than private, for the same reason
 * `Track.sanitized()` is: it is the only part of the load path that is not tied
 * to a `Context`, so it is the only part a JVM test can reach.
 * `PlaylistRoundTripTest` covers it.
 *
 * The `?:` branches look redundant because both properties are declared
 * non-null. They are not — see [Playlist]'s note on `Unsafe` allocation: the
 * type is a promise the runtime does not enforce for reflectively-populated
 * objects, which is exactly why this repair exists. Same suppression as
 * `Track.sanitized()`.
 */
@Suppress("USELESS_ELVIS")
internal fun Playlist.repaired(): Playlist = copy(
    id = id ?: UUID.randomUUID().toString(),
    name = name ?: DEFAULT_NAME,
    tracks = tracks.orEmpty()
)

/**
 * `@Immutable` for the same reason as [com.piroman.elara.data.model.Track]:
 * all properties are `val`, but `tracks` is a `List`, which Compose infers as
 * unstable — so without this every playlist row in the Library re-composed on
 * any unrelated state change. Nothing mutates a playlist in place; updates go
 * through `copy()`.
 *
 * **Every field has a default, and `name`'s is load-bearing.** Gson fills fields
 * reflectively, and a constructor parameter *without* a default stops Kotlin
 * emitting a synthetic no-arg constructor — Gson then cannot instantiate through
 * the constructor at all and falls back to `Unsafe` allocation, where **every**
 * absent key becomes a null (or `0` for a primitive) rather than its default.
 * That is not hypothetical: with `name` undefaulted, a `playlists.json` written
 * before `createdAt` existed loaded `createdAt` as `0` and a missing `name` as
 * `null` inside a non-null `String`. `PlaylistRoundTripTest` pins both.
 *
 * The practical consequence is a rule: **a field added to this class in future
 * is a latent null for every install whose file predates it** unless the no-arg
 * constructor path is available. Do not remove a default to "make a field
 * required" — the compiler will not warn you, and the failure is a null that
 * escapes its type at runtime.
 */
@Immutable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String = DEFAULT_NAME,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList()
)

/**
 * User-created playlists, persisted as JSON in app-private storage.
 *
 * Every mutator updates [playlists] synchronously and returns, then persists in
 * the background — see [save]. Nothing here blocks the caller on disk I/O.
 */
class PlaylistManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistManager"
        private const val FILE_NAME = "playlists.json"

        @Volatile private var instance: PlaylistManager? = null

        fun getInstance(context: Context): PlaylistManager =
            instance ?: synchronized(this) {
                instance ?: PlaylistManager(context.applicationContext).also { instance = it }
            }
    }

    private val gson = SafeGson.instance
    private val file = File(context.filesDir, FILE_NAME)

    /**
     * One dedicated thread, deliberately not `Dispatchers.IO`. That pool is
     * parallel, so two edits in quick succession would serialise and write
     * concurrently and could interleave into a truncated file.
     *
     * `newSingleThreadExecutor` starts the thread lazily, so nothing is spawned
     * until the first edit actually needs saving.
     */
    private val saveDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "playlist-save").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)

    /** True while a write is queued or running. See [save] for why it exists. */
    private val saveQueued = AtomicBoolean(false)

    /** The most recent queued write, so [flushPendingSave] can wait for it. */
    @Volatile private var pendingSave: Job? = null

    /**
     * Blocks until the queued write has landed.
     *
     * Called from the Activity's `onStop`. [save] deliberately hands the write to
     * a background thread, which means an edit made in the last moments before
     * the process is killed can be lost — and "I made a playlist, closed the app
     * and it was gone" is exactly that window. Android solves the same problem
     * for `SharedPreferences.apply()` by blocking the main thread inside
     * `ActivityThread.handleStopActivity`; doing it here for our own file is the
     * same trade — a few milliseconds of `onStop` for an edit that survives.
     *
     * `runCatching` because `join()` on a cancelled job throws, and this is
     * cleanup: it must never take the app down on the way out.
     */
    fun flushPendingSave() {
        val job = pendingSave ?: return
        runBlocking { runCatching { job.join() } }
    }

    private val _playlists =
        MutableStateFlow<List<Playlist>>(StartupTiming.measure("PlaylistManager.load") { load() })
    val playlists: StateFlow<List<Playlist>> = _playlists

    fun get(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    fun findByName(name: String): Playlist? =
        _playlists.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Creates a playlist pre-filled with tracks (used by Home recommendations). */
    fun createWithTracks(name: String, tracks: List<Track>): Playlist {
        findByName(name)?.let { return it }
        val playlist = Playlist(name = name.trim(), tracks = tracks)
        _playlists.value = listOf(playlist) + _playlists.value
        save()
        return playlist
    }

    fun create(name: String): Playlist {
        val clean = name.trim()
        val playlist = Playlist(
            name = if (clean.isEmpty()) DEFAULT_NAME else clean
        )
        _playlists.value = listOf(playlist) + _playlists.value
        save()
        return playlist
    }

    fun rename(id: String, newName: String) {
        val clean = newName.trim().takeIf { it.isNotEmpty() } ?: return
        update(id) { it.copy(name = clean) }
    }

    fun delete(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        save()
    }

    /**
     * Replaces the entire library in one shot. Used by backup restore.
     *
     * Deliberately a wholesale replace rather than a merge: a restore is meant
     * to reproduce the state that was exported, and merging would silently keep
     * whatever happened to be on the device — the usual way a restore appears to
     * work while producing the wrong library. Existing data is overwritten, so
     * callers should confirm with the user first.
     */
    fun replaceAll(playlists: List<Playlist>) {
        _playlists.value = playlists.map { p -> p.copy(tracks = p.tracks.orEmpty()) }
        save()
    }

    /** Appends a track; no duplicates within the same playlist. */
    fun addTrack(playlistId: String, track: Track): Boolean {
        var added = false
        update(playlistId) { p ->
            if (p.tracks.any { it.id == track.id }) p
            else {
                added = true
                p.copy(tracks = p.tracks + track)
            }
        }
        return added
    }

    fun removeTrack(playlistId: String, trackId: String) {
        update(playlistId) { p -> p.copy(tracks = p.tracks.filterNot { it.id == trackId }) }
    }

    /**
     * Move the entry at [from] to [to], keeping every other track's order.
     *
     * Duplicate ids cannot occur inside one playlist ([addTrack] rejects them),
     * so an index-based move is unambiguous — there is no risk of moving "the
     * wrong copy" of a song.
     */
    fun moveTrack(playlistId: String, from: Int, to: Int) {
        if (from == to) return
        update(playlistId) { p ->
            if (from !in p.tracks.indices || to !in p.tracks.indices) {
                p
            } else {
                val next = p.tracks.toMutableList()
                next.add(to, next.removeAt(from))
                p.copy(tracks = next)
            }
        }
    }

    /**
     * Replace a playlist's track order wholesale. Used by "Save this order"
     * after a display-only sort, and by restore.
     */
    fun setTracks(playlistId: String, tracks: List<Track>) {
        update(playlistId) { p -> p.copy(tracks = tracks) }
    }

    private fun update(id: String, transform: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) transform(it) else it }
        save()
    }

    /**
     * Persists the current list off the calling thread.
     *
     * Callers mutate `_playlists.value` *before* calling this, so the UI never
     * waits on disk — both the `toJson` serialisation and the file write happen
     * on [saveDispatcher]. Previously both ran on the caller (main) thread,
     * which is what made editing a playlist hitch.
     *
     * Durability trade-off, the same one `SharedPreferences.apply()` makes: the
     * write is no longer guaranteed to have landed when this returns, so an edit
     * made in the last moments before the process is killed can be lost. The old
     * synchronous version had it on disk before returning.
     *
     * Calls that arrive while a write is already pending are coalesced into it
     * rather than queueing one write per edit. That is only correct because the
     * coroutine below reads `_playlists.value` *itself*, at the moment it runs —
     * so a coalesced-away call is still covered by the write that follows it.
     * Do not hoist that read up to the call site: capturing a snapshot here
     * would silently drop the coalesced edits.
     */
    private fun save() {
        if (!saveQueued.compareAndSet(false, true)) return
        pendingSave = saveScope.launch {
            // Cleared before the read so that an edit arriving *during* this
            // write queues a follow-up write instead of being skipped.
            saveQueued.set(false)
            writeAtomically()
        }
    }

    /**
     * Writes the library through [AtomicFileWrite], so `playlists.json` is never
     * observed half-written, then records the outcome for the diagnostics log.
     *
     * The atomicity argument lives in [AtomicFileWrite] — including why a
     * truncated file means "every playlist silently disappears" rather than a
     * visible error. This function owns only the error policy: a failed write is
     * logged and reported, never thrown, because a save must not take the UI
     * down.
     */
    private fun writeAtomically() {
        try {
            AtomicFileWrite.write(file, gson.toJson(_playlists.value))
            PlaybackDiagnostics.append(context, "playlists: saved ${_playlists.value.size}")
        } catch (e: Exception) {
            Log.e(TAG, "save: ${e.message}")
            PlaybackDiagnostics.append(context, "playlists: SAVE FAILED ${e.message}")
        }
    }

    /**
     * The load path reports what it found through [PlaybackDiagnostics].
     *
     * This matters more than it looks. A failed load and an empty library are
     * **indistinguishable from the UI** — both draw the same empty list — so
     * "my playlists vanished" had no evidence trail at all: the exception went
     * to a `Log.e` nobody can read on a sideloaded build. Recording the count,
     * the file size and any exception turns a silent wipe into something the
     * diagnostics button can show.
     */
    private fun load(): List<Playlist> = try {
        if (file.exists()) {
            val type = listTypeOf(Playlist::class.java)
            val loaded: List<Playlist>? = gson.fromJson(file.readText(), type)
            // Nested tracks are normalised by SafeGson; the list itself can still
            // come back null because Gson ignores the constructor default.
            val result = loaded?.map { it.repaired() } ?: emptyList()
            PlaybackDiagnostics.append(
                context,
                "playlists: loaded ${result.size} from ${file.length()}B"
            )
            result
        } else {
            // Separates "nothing saved yet" from "the file is gone".
            PlaybackDiagnostics.append(context, "playlists: no file at ${file.absolutePath}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "load: ${e.message}")
        PlaybackDiagnostics.append(
            context,
            "playlists: LOAD FAILED (${file.length()}B) ${e.message} — library reads as EMPTY"
        )
        emptyList()
    }
}
