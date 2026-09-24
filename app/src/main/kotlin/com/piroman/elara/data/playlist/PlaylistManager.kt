package com.aesthetic.spotify.data.playlist

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.aesthetic.spotify.data.diagnostics.StartupTiming
import com.aesthetic.spotify.data.model.SafeGson
import com.aesthetic.spotify.data.model.Track
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `@Immutable` for the same reason as [com.aesthetic.spotify.data.model.Track]:
 * all properties are `val`, but `tracks` is a `List`, which Compose infers as
 * unstable — so without this every playlist row in the Library re-composed on
 * any unrelated state change. Nothing mutates a playlist in place; updates go
 * through `copy()`.
 */
@Immutable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
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
    private val file = java.io.File(context.filesDir, FILE_NAME)

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
            name = if (clean.isEmpty()) "New Playlist" else clean
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
        saveScope.launch {
            // Cleared before the read so that an edit arriving *during* this
            // write queues a follow-up write instead of being skipped.
            saveQueued.set(false)
            try {
                file.writeText(gson.toJson(_playlists.value))
            } catch (e: Exception) {
                Log.e(TAG, "save: ${e.message}")
            }
        }
    }

    private fun load(): List<Playlist> = try {
        if (file.exists()) {
            val type = object : TypeToken<List<Playlist>>() {}.type
            val loaded: List<Playlist>? = gson.fromJson(file.readText(), type)
            // Nested tracks are normalised by SafeGson; the list itself can still
            // come back null because Gson ignores the constructor default.
            loaded?.map { playlist -> playlist.copy(tracks = playlist.tracks.orEmpty()) }
                ?: emptyList()
        } else emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "load: ${e.message}")
        emptyList()
    }
}
