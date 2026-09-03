package com.aesthetic.spotify.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aesthetic.spotify.data.download.DownloadManager
import com.aesthetic.spotify.data.model.LyricLine
import com.aesthetic.spotify.data.model.MusicSource
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.data.playlist.Playlist
import com.aesthetic.spotify.data.playlist.PlaylistManager
import com.aesthetic.spotify.data.repository.MusicRepository
import com.aesthetic.spotify.data.source.SourcePreferences
import com.aesthetic.spotify.data.source.SourceSetting
import com.aesthetic.spotify.data.network.YouTubeMusicApi
import com.aesthetic.spotify.playback.PlaybackManager
import com.aesthetic.spotify.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository()
    private val playbackManager = PlaybackManager.getInstance(application)
    private val downloadManager = DownloadManager.getInstance(application)
    private val playlistManager = PlaylistManager.getInstance(application)
    private val sourcePreferences = SourcePreferences.getInstance(application)
    private val playHistory = com.aesthetic.spotify.data.history.PlayHistoryManager.getInstance(application)
    private val appContext = application.applicationContext
    private val prefs = application.getSharedPreferences("aurasonic_prefs", Context.MODE_PRIVATE)

    /**
     * Long-lived MediaController: keeps the PlaybackService bound while the app
     * is alive, so OEM task managers can't kill it silently and the media
     * notification stays in sync with the UI player.
     */
    private var mediaControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val gson = Gson()

    // UI State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks: StateFlow<List<Track>> = _trendingTracks

    // Recently played (Home quick-picks grid)
    val recentlyPlayed: StateFlow<List<Track>> = playHistory.recent

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults

    // Music source setting (Auto / JioSaavn / YouTube Music)
    val sourceSetting: StateFlow<SourceSetting> = sourcePreferences.setting

    fun setSourceSetting(setting: SourceSetting) {
        sourcePreferences.set(setting)
    }

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    // Library
    private val _likedTracks = MutableStateFlow<List<Track>>(loadLikedTracks())
    val likedTracks: StateFlow<List<Track>> = _likedTracks

    // Playback
    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val positionMs: StateFlow<Long> = playbackManager.positionMs
    val durationMs: StateFlow<Long> = playbackManager.durationMs
    val queue: StateFlow<List<Track>> = playbackManager.queue
    val playbackError: StateFlow<String?> = playbackManager.playbackError

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _lyrics

    private val _lyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _lyricsLoading

    // Downloads
    val downloadStates: StateFlow<Map<String, DownloadManager.State>> = downloadManager.states
    val downloadedTracks: StateFlow<List<DownloadManager.Entry>> = downloadManager.entries

    // Playlists
    val playlists: StateFlow<List<Playlist>> = playlistManager.playlists

    fun createPlaylist(name: String): Playlist = playlistManager.create(name)

    fun renamePlaylist(id: String, newName: String) {
        playlistManager.rename(id, newName)
    }

    fun deletePlaylist(id: String) {
        playlistManager.delete(id)
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        playlistManager.addTrack(playlistId, track)
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistManager.removeTrack(playlistId, trackId)
    }

    fun getPlaylist(id: String): Playlist? = playlistManager.get(id)

    // ── Home: featured editorial playlists (browse, not saved) ──────────

    private val _featuredPlaylists =
        MutableStateFlow<List<MusicRepository.FeaturedPlaylist>>(emptyList())
    val featuredPlaylists: StateFlow<List<MusicRepository.FeaturedPlaylist>> = _featuredPlaylists

    private val _browsePlaylist = MutableStateFlow<MusicRepository.BrowsePlaylist?>(null)
    val browsePlaylist: StateFlow<MusicRepository.BrowsePlaylist?> = _browsePlaylist

    private val _isBrowseLoading = MutableStateFlow(false)
    val isBrowseLoading: StateFlow<Boolean> = _isBrowseLoading

    private fun loadFeaturedPlaylists() {
        viewModelScope.launch {
            _featuredPlaylists.value = runCatching {
                repository.getFeaturedPlaylists()
            }.getOrElse {
                Log.e("MainViewModel", "featured playlists: ${it.message}")
                emptyList()
            }
        }
    }

    fun loadBrowsePlaylist(featured: MusicRepository.FeaturedPlaylist) {
        viewModelScope.launch {
            _isBrowseLoading.value = true
            _browsePlaylist.value = runCatching {
                repository.getPlaylistSongs(featured.token, featured.image)
            }.getOrElse {
                Log.e("MainViewModel", "browse playlist: ${it.message}")
                null
            }
            _isBrowseLoading.value = false
        }
    }

    fun clearBrowsePlaylist() {
        _browsePlaylist.value = null
    }

    /** Saves the browsed editorial playlist into the user's library. */
    fun saveBrowsePlaylistToLibrary() {
        _browsePlaylist.value?.let { browse ->
            playlistManager.createWithTracks(browse.title, browse.tracks)
        }
    }

    fun isBrowsePlaylistSaved(): Boolean =
        browsePlaylist.value?.let { playlistManager.findByName(it.title) != null } == true

    /**
     * Removes the auto-seeded "recommended" playlists created by an earlier
     * build (only when the seeding flag confirms they were machine-created,
     * never user-made ones).
     */
    private fun cleanupOldSeededPlaylists() {
        val settings = appContext.getSharedPreferences("elara_settings", Context.MODE_PRIVATE)
        if (!settings.getBoolean("recommended_seeded", false)) return
        listOf(
            "Trending Now", "Bollywood Hits", "Chill Vibes",
            "Workout Energy", "Romance Mix"
        ).forEach { name ->
            playlistManager.findByName(name)?.let { playlistManager.delete(it.id) }
        }
        settings.edit().putBoolean("recommended_seeded", false).apply()
    }

    fun downloadTrack(track: Track) {
        downloadManager.download(track) { t ->
            when (t.source) {
                MusicSource.YOUTUBE_MUSIC ->
                    YouTubeMusicApi.resolveStreamUrl(t.videoId ?: t.id)
                MusicSource.JIOSAAVN -> repository.resolveFullStream(t)
            }
        }
    }

    fun deleteDownload(trackId: String) {
        downloadManager.delete(trackId)
    }

    init {
        Log.d("MainViewModel", "Initializing ViewModel")
        loadFeaturedPlaylists()
        cleanupOldSeededPlaylists()

        // Trending follows the Music Source setting; re-fetches on change.
        viewModelScope.launch {
            sourcePreferences.setting.collect { refreshTrending() }
        }

        // Bind the playback service for as long as this ViewModel lives.
        mediaControllerFuture = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync()

        // Playback URL chain: the player checks downloaded files FIRST via
        // localSourceResolver (so downloads always play offline), then falls
        // back to per-source network resolution.
        playbackManager.localSourceResolver = { track ->
            downloadManager.localPath(track.id)?.let { path ->
                android.net.Uri.fromFile(java.io.File(path)).toString()
            }
        }
        playbackManager.streamResolver = { track ->
            when (track.source) {
                MusicSource.YOUTUBE_MUSIC ->
                    YouTubeMusicApi.resolveStreamUrl(track.videoId ?: track.id)
                        ?: repository.resolveFullStream(track)
                MusicSource.JIOSAAVN -> repository.resolveFullStream(track)
            }
        }

        // A downloaded file that went missing (cleared storage, bad migration):
        // drop the dead entry, recover from the network automatically, and
        // re-download so it stays available offline.
        playbackManager.onDownloadMissing = { track ->
            downloadManager.delete(track.id)
            downloadTrack(track)
            playbackManager.retryCurrentWithoutLocal()
        }

        // Search honours the Music Source setting; re-runs when it changes.
        viewModelScope.launch {
            combine(searchQuery, sourcePreferences.setting) { q, s -> q to s }
                .debounce(400)
                .collect { (query, setting) ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                        _isConnecting.value = false
                    } else {
                        Log.d("MainViewModel", "Searching [$setting] for: $query")
                        _isConnecting.value = true
                        _searchResults.value = runCatching {
                            repository.searchBySource(query, setting)
                        }.getOrElse {
                            Log.e("MainViewModel", "search failed: ${it.message}")
                            emptyList()
                        }
                        _isConnecting.value = false
                    }
                }
        }

        // Load lyrics whenever the track changes (auto-next included).
        viewModelScope.launch {
            currentTrack.collect { track ->
                _lyrics.value = emptyList()
                if (track == null) {
                    _lyricsLoading.value = false
                    return@collect
                }
                _lyricsLoading.value = true
                val trackId = track.id
                val lyrics = runCatching { repository.getLyrics(track) }
                    .getOrElse { emptyList() }
                // Ignore stale results if the track changed while loading.
                if (currentTrack.value?.id == trackId) {
                    _lyrics.value = lyrics
                    _lyricsLoading.value = false
                }
            }
        }
    }

    fun refreshTrending() {
        viewModelScope.launch {
            Log.d("MainViewModel", "Refreshing trending tracks")
            _isConnecting.value = true
            _trendingTracks.value = runCatching {
                repository.getTrendingBySource(sourcePreferences.setting.value)
            }.getOrElse {
                Log.e("MainViewModel", "trending failed: ${it.message}")
                emptyList()
            }
            _isConnecting.value = false
        }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isConnecting.value = false
        }
    }

    fun playTrack(track: Track, contextTracks: List<Track> = emptyList()) {
        startPlaybackService()
        playHistory.record(track)
        playbackManager.playTrack(track, contextTracks)
    }

    fun next() {
        playbackManager.playNext()
    }

    fun previous() {
        playbackManager.playPrevious()
    }

    fun seekTo(ms: Long) {
        playbackManager.seekTo(ms)
    }

    fun togglePlay() {
        startPlaybackService()
        playbackManager.togglePlay()
    }

    private fun startPlaybackService() {
        // Plain startService, NOT startForegroundService: the latter imposes a
        // 5s Service.startForeground() deadline that Media3 only fulfils once
        // playback actually starts (it may still be resolving/buffering),
        // which crashes with ForegroundServiceDidNotStartInTimeException.
        // Safe here because playback is always triggered from the foreground
        // UI; Media3 promotes the service to foreground when audio begins.
        runCatching {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        }.onFailure { Log.e("MainViewModel", "Service start failed: ${it.message}") }
    }

    fun isLiked(track: Track): Boolean = _likedTracks.value.any { it.id == track.id }

    fun toggleLikeTrack(track: Track) {
        val current = _likedTracks.value.toMutableList()
        val existing = current.find { it.id == track.id }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(0, track.copy(isLiked = true))
        }
        _likedTracks.value = current
        saveLikedTracks(current)
    }

    private fun saveLikedTracks(tracks: List<Track>) {
        try {
            val json = gson.toJson(tracks)
            prefs.edit().putString("liked_tracks", json).apply()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Save Liked Error: ${e.message}")
        }
    }

    private fun loadLikedTracks(): List<Track> {
        return try {
            val json = prefs.getString("liked_tracks", null) ?: return emptyList()
            val type = object : TypeToken<List<Track>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        // Player is intentionally kept alive for background playback via PlaybackService.
    }
}
