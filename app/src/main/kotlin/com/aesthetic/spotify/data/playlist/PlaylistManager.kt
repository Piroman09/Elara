package com.aesthetic.spotify.data.playlist

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.aesthetic.spotify.data.model.SafeGson
import com.aesthetic.spotify.data.model.Track
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

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

    private val _playlists = MutableStateFlow<List<Playlist>>(load())
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

    private fun save() {
        try {
            file.writeText(gson.toJson(_playlists.value))
        } catch (e: Exception) {
            Log.e(TAG, "save: ${e.message}")
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
