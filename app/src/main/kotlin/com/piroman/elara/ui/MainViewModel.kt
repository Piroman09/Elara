package com.piroman.elara.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.piroman.elara.data.backup.ElaraBackup
import com.piroman.elara.data.backup.RestoreReport
import com.piroman.elara.data.backup.UnsupportedVersionException
import com.piroman.elara.data.download.DownloadManager
import com.piroman.elara.data.model.LyricLine
import com.piroman.elara.data.diagnostics.StartupTiming
import com.piroman.elara.data.model.MusicSource
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.playlist.Playlist
import com.piroman.elara.data.playlist.PlaylistManager
import com.piroman.elara.data.repository.MusicRepository
import com.piroman.elara.data.repository.TrendingCache
import com.piroman.elara.data.repository.TrendingCachePolicy
import com.piroman.elara.data.lyrics.LyricsCache
import com.piroman.elara.data.source.DataSaverPreferences
import com.piroman.elara.data.source.PlaybackPreferences
import com.piroman.elara.data.source.SourcePreferences
import com.piroman.elara.data.source.SourceSetting
import com.piroman.elara.data.stats.ListeningStatsManager
import com.piroman.elara.data.network.SpotifyPlaylistImporter
import com.piroman.elara.data.network.YouTubeMusicApi
import com.piroman.elara.playback.PlaybackManager
import com.piroman.elara.playback.PlaybackService
import com.piroman.elara.playback.RepeatMode
import com.piroman.elara.data.model.listTypeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        StartupTiming.measure("MusicRepository.init") { MusicRepository() }
    private val playbackManager = PlaybackManager.getInstance(application)
    private val downloadManager = DownloadManager.getInstance(application)
    private val playlistManager = PlaylistManager.getInstance(application)
    private val sourcePreferences = SourcePreferences.getInstance(application)
    private val dataSaverPrefs = DataSaverPreferences.getInstance(application)
    private val playbackPrefs = PlaybackPreferences.getInstance(application)
    private val lyricsCache = LyricsCache.getInstance(application)
    private val statsManager = ListeningStatsManager.getInstance(application)
    private val trendingCache = TrendingCache.getInstance(application)
    private val playHistory = com.piroman.elara.data.history.PlayHistoryManager.getInstance(application)
    private val appContext = application.applicationContext
    private val prefs = application.getSharedPreferences("aurasonic_prefs", Context.MODE_PRIVATE)

    /**
     * Long-lived MediaController: keeps the PlaybackService bound while the app
     * is alive, so OEM task managers can't kill it silently and the media
     * notification stays in sync with the UI player.
     */
    private var mediaControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val gson = SafeGson.instance

    // UI State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchHistory = MutableStateFlow(loadSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks: StateFlow<List<Track>> = _trendingTracks

    // Recently played (Home quick-picks shelf)
    val recentlyPlayed: StateFlow<List<Track>> = playHistory.recent

    // Tracks played at least twice, most-played first. Empty on a fresh install
    // — see PlayHistoryManager.MOST_PLAYED_MIN_PLAYS for why.
    val mostPlayed: StateFlow<List<Track>> = playHistory.mostPlayed

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults

    // Music source setting (Auto / JioSaavn / YouTube Music)
    val sourceSetting: StateFlow<SourceSetting> = sourcePreferences.setting

    fun setSourceSetting(setting: SourceSetting) {
        sourcePreferences.set(setting)
    }

    /** When true the app avoids background network use (autoplay, bulk download). */
    val dataSaverEnabled: StateFlow<Boolean> = dataSaverPrefs.enabled

    fun setDataSaver(enabled: Boolean) {
        dataSaverPrefs.set(enabled)
    }

    /** Whether autoplay (append similar tracks when the queue ends) is on. */
    val autoplayEnabled: StateFlow<Boolean> = playbackPrefs.autoplayEnabled
    val crossfadeSeconds: StateFlow<Int> = playbackPrefs.crossfadeSeconds
    val smartShuffleEnabled: StateFlow<Boolean> = playbackPrefs.smartShuffleEnabled

    /**
     * The player's audio session, so the system equalizer panel can attach to
     * *our* playback instead of opening unscoped. ExoPlayer reports 0 until the
     * audio sink is initialised, so callers must check for a positive value
     * before passing it to [android.media.audiofx.AudioEffect].
     */
    val audioSessionId: Int get() = playbackManager.exoPlayer.audioSessionId

    /** Total milliseconds actually listened to (not including pauses or seeks). */
    val totalListeningMs: StateFlow<Long> = statsManager.totalMs
    val uniqueTracksListened: StateFlow<Int> = statsManager.totalTracks

    fun setAutoplay(enabled: Boolean) {
        playbackPrefs.setAutoplay(enabled)
        playbackManager.autoplayProvider = if (enabled) {
            { track -> autoplayCandidates(track) }
        } else null
    }

    fun setCrossfade(seconds: Int) {
        playbackPrefs.setCrossfade(seconds)
        playbackManager.crossfadeMs = seconds * 1_000L
    }

    fun setSmartShuffle(enabled: Boolean) {
        playbackPrefs.setSmartShuffle(enabled)
        playbackManager.smartShuffle = enabled
    }

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    // Library
    private val _likedTracks =
        MutableStateFlow<List<Track>>(StartupTiming.measure("MainViewModel.loadLikedTracks") { loadLikedTracks() })
    val likedTracks: StateFlow<List<Track>> = _likedTracks

    // Playback
    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val positionMs: StateFlow<Long> = playbackManager.positionMs
    val durationMs: StateFlow<Long> = playbackManager.durationMs
    val queue: StateFlow<List<Track>> = playbackManager.queue
    val queueIndex: StateFlow<Int> = playbackManager.queueIndex
    val isShuffle: StateFlow<Boolean> = playbackManager.isShuffle
    val repeatMode: StateFlow<RepeatMode> = playbackManager.repeatMode
    val playbackError: StateFlow<String?> = playbackManager.playbackError

    /** Sleep-timer countdown in ms, or null when off. See PlaybackManager. */
    val sleepTimerRemainingMs: StateFlow<Long?> = playbackManager.sleepTimerRemainingMs

    fun setSleepTimer(minutes: Int?) {
        playbackManager.setSleepTimer(minutes?.let { it * 60_000L })
    }

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded

    private val _isQueueOpen = MutableStateFlow(false)
    val isQueueOpen: StateFlow<Boolean> = _isQueueOpen

    private val _similarTracks = MutableStateFlow<List<Track>>(emptyList())
    val similarTracks: StateFlow<List<Track>> = _similarTracks

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _lyrics

    private val _lyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _lyricsLoading

    // Downloads
    val downloadStates: StateFlow<Map<String, DownloadManager.State>> = downloadManager.states
    val downloadedTracks: StateFlow<List<DownloadManager.Entry>> = downloadManager.entries

    /** Bytes those downloads occupy on disk; null until the sum is computed. */
    val downloadedBytes: StateFlow<Long?> = downloadManager.totalBytes

    // Playlists
    val playlists: StateFlow<List<Playlist>> = playlistManager.playlists

    fun createPlaylist(name: String): Playlist = playlistManager.create(name)

    /**
     * Outcome of a Spotify import, shaped for the import dialog: the saved
     * playlist plus the counts it reports and the cover Spotify returned
     * ([Playlist] itself has no cover field, so it is carried alongside).
     */
    data class SpotifyImportSummary(
        val playlist: Playlist,
        val coverArt: String,
        val totalInSpotify: Int,
        val importedCount: Int
    )

    /**
     * Imports a public Spotify playlist or album by link/URI/ID and saves it as
     * a local playlist.
     *
     * The tracks arrive as Spotify *metadata* with JioSaavn as their source and
     * no usable stream URL, so they play through the normal resolve-at-play-time
     * path (see [PlaybackManager.streamResolver]) rather than a stored URL.
     * Returns a failure instead of throwing so the dialog can render the error.
     */
    suspend fun importSpotifyPlaylist(
        urlOrId: String,
        onProgress: (imported: Int, total: Int, name: String, coverArt: String) -> Unit = { _, _, _, _ -> }
    ): Result<SpotifyImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val result = SpotifyPlaylistImporter.fetchPlaylist(urlOrId, onProgress)
            val playlist = playlistManager.createWithTracks(result.name, result.tracks)
            SpotifyImportSummary(
                playlist = playlist,
                coverArt = result.coverArt,
                totalInSpotify = result.totalInPlaylist,
                importedCount = playlist.tracks.size
            )
        }
    }

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

    /** Drag-reorder inside a user playlist. See [PlaylistManager.moveTrack]. */
    fun moveTrackInPlaylist(playlistId: String, from: Int, to: Int) {
        playlistManager.moveTrack(playlistId, from, to)
    }

    /**
     * Commits a display-only sort to disk, so it survives leaving the screen.
     * Sorting itself is deliberately non-destructive — see [PlaylistSort].
     */
    fun savePlaylistOrder(playlistId: String, tracks: List<Track>) {
        playlistManager.setTracks(playlistId, tracks)
    }

    // ── Home: browsable collections (browse, not saved) ─────────────────

    private val _featuredPlaylists =
        MutableStateFlow<List<MusicRepository.BrowseTile>>(emptyList())
    val featuredPlaylists: StateFlow<List<MusicRepository.BrowseTile>> = _featuredPlaylists

    /** Current chart rankings — "India Superhits Top 50" and friends. */
    private val _charts = MutableStateFlow<List<MusicRepository.BrowseTile>>(emptyList())
    val charts: StateFlow<List<MusicRepository.BrowseTile>> = _charts

    /** Decade charts — "Hindi 1990s". Filled by the same call as [charts]. */
    private val _throwback = MutableStateFlow<List<MusicRepository.BrowseTile>>(emptyList())
    val throwback: StateFlow<List<MusicRepository.BrowseTile>> = _throwback

    private val _newReleases = MutableStateFlow<List<MusicRepository.BrowseTile>>(emptyList())
    val newReleases: StateFlow<List<MusicRepository.BrowseTile>> = _newReleases

    /**
     * The loose songs in the *same* feed as [newReleases], which the album filter
     * used to throw away. Filled by the same request — one call, two rows.
     */
    private val _newSongs = MutableStateFlow<List<Track>>(emptyList())
    val newSongs: StateFlow<List<Track>> = _newSongs

    /**
     * What people are searching for right now. Titles, not tiles: the feed mixes
     * albums, songs and artists, so the row *searches* each term rather than
     * pretending to open it. See [MusicRepository.getTrendingSearches].
     */
    private val _trendingSearches = MutableStateFlow<List<String>>(emptyList())
    val trendingSearches: StateFlow<List<String>> = _trendingSearches

    /**
     * Home's YouTube Music rows. Empty — and *kept* empty — when the Music
     * Source setting is JioSaavn-only; see [loadYouTubeMusicRows].
     *
     * Search-backed rather than feed-backed, which is a measured limitation of
     * the YouTube Music API surface, not a shortcut. The probing that ruled out
     * the browse feeds is recorded on [MusicRepository.YouTubeMusicRows].
     *
     * Named `hipHop` rather than `newSongs`: the row was built from a "new songs"
     * query that returned 2018–2022 releases, so the old name described something
     * the row never was. See [MusicRepository.YouTubeMusicRows].
     */
    private val _youTubeTop = MutableStateFlow<List<Track>>(emptyList())
    val youTubeTop: StateFlow<List<Track>> = _youTubeTop

    private val _youTubeHipHop = MutableStateFlow<List<Track>>(emptyList())
    val youTubeHipHop: StateFlow<List<Track>> = _youTubeHipHop

    private val _browsePlaylist = MutableStateFlow<MusicRepository.BrowsePlaylist?>(null)
    val browsePlaylist: StateFlow<MusicRepository.BrowsePlaylist?> = _browsePlaylist

    private val _isBrowseLoading = MutableStateFlow(false)
    val isBrowseLoading: StateFlow<Boolean> = _isBrowseLoading

    /**
     * When Home's rows were last *requested*. Set by [loadHomeRows], which is the
     * only thing that fetches them.
     */
    @Volatile private var homeRowsRequestedAtMs = 0L

    /**
     * True while a batch is in flight, so two batches can never overlap — the
     * failure mode being a resume that arrives while the previous fetch is still
     * running and doubles every request.
     */
    @Volatile private var homeRowsLoading = false

    /**
     * True when the last batch finished with Home's two critical rows empty —
     * the signature of a failed fetch, not of a thin catalogue (see
     * [homeCameBackEmpty]). Surface it as a banner with a Retry button: every
     * other failure mode in this class logs and moves on, which leaves a user
     * on flaky connectivity staring at an empty screen with no explanation.
     */
    private val _homeLoadFailed = MutableStateFlow(false)
    val homeLoadFailed: StateFlow<Boolean> = _homeLoadFailed

    /**
     * Every Home row, as one batch.
     *
     * These four loaders used to each launch their own coroutine straight from
     * `init`, which meant nothing could tell when Home had finished loading.
     * [refreshHomeIfStale] needs exactly that: "is the last fetch still running?"
     * and "did the last fetch come back empty?" are the same question, and
     * without an answer the refresh either double-fetches at startup or never
     * heals a failed load.
     *
     * They still run **concurrently** — they are independent feeds, and awaiting
     * them one after another would serialise four round trips — so this costs the
     * same wall-clock time as before.
     *
     * `loadYouTubeMusicRows` is deliberately not part of the batch: it is gated
     * by the Music Source setting and owned by the collector in `init`, and
     * calling it from here too would fetch the YouTube rows twice on every cold
     * start.
     */
    private fun loadHomeRows() {
        homeRowsLoading = true
        homeRowsRequestedAtMs = System.currentTimeMillis()
        // A retry must clear the banner the moment it starts, not when the
        // batch lands — otherwise the button looks dead for the request's duration.
        _homeLoadFailed.value = false
        viewModelScope.launch {
            try {
                coroutineScope {
                    launch { loadFeaturedPlaylists() }
                    launch { loadCharts() }
                    launch { loadNewReleases() }
                    launch { loadTrendingSearches() }
                }
            } finally {
                homeRowsLoading = false
                // All four loaders have completed by here (coroutineScope
                // awaited them), so the two critical rows' final states are
                // authoritative.
                _homeLoadFailed.value = homeCameBackEmpty()
            }
        }
    }

    /**
     * The Home banner's Retry: re-fetch the rows and trending now.
     *
     * Deliberately not routed through [refreshHomeIfStale] — its staleness
     * window exists to spare *fresh* rows a network round trip, which is the
     * opposite of what a user pressing Retry is asking for.
     */
    fun retryHome() {
        loadHomeRows()
        refreshTrending()
        loadYouTubeMusicRows(sourcePreferences.setting.value)
    }

    /**
     * Re-fetches Home's rows when the user comes back to the app.
     *
     * Called from `MainActivity.onResume`. The decision about whether a fetch is
     * worth making is [HomeRefreshPolicy]'s, and the reasoning behind it lives
     * there — it is a pure function so it can be tested, because every way of
     * getting it wrong is silent.
     *
     * The short version: Home's rows are fetched once, when this ViewModel is
     * created, and nothing re-fetched them afterwards. The ViewModel is
     * Activity-scoped, so an app left open in the background for days kept
     * showing the rows from its last cold start, and with no pull-to-refresh the
     * user had no way to ask for newer ones. This is the trigger that was missing.
     */
    fun refreshHomeIfStale() {
        val shouldRefresh = HomeRefreshPolicy.shouldRefresh(
            requestedAtMs = homeRowsRequestedAtMs,
            nowMs = System.currentTimeMillis(),
            loading = homeRowsLoading,
            cameBackEmpty = homeCameBackEmpty(),
            maxAgeMs = HOME_ROWS_MAX_AGE_MS
        )
        if (!shouldRefresh) return

        loadHomeRows()
        refreshTrending()
        loadYouTubeMusicRows(sourcePreferences.setting.value)
    }

    /**
     * True when the last completed batch left Home with nothing to show.
     *
     * Only the first two rows are consulted. They are the ones a failure empties
     * together, and requiring *both* to be empty keeps a genuinely thin catalogue
     * — or a source that simply has no charts today — from being read as an
     * error and re-fetched on every resume.
     */
    private fun homeCameBackEmpty(): Boolean =
        _featuredPlaylists.value.isEmpty() && _charts.value.isEmpty()

    private suspend fun loadFeaturedPlaylists() {
        _featuredPlaylists.value = runCatching {
            repository.getFeaturedPlaylists()
        }.getOrElse {
            Log.e("MainViewModel", "featured playlists: ${it.message}")
            emptyList()
        }
    }

    /**
     * Fills Home's two chart rows.
     *
     * `content.getCharts` is a single feed containing both current rankings and
     * decade retrospectives, so this is deliberately one request writing two
     * states rather than two requests that could disagree.
     */
    private suspend fun loadCharts() {
        val rows = runCatching { repository.getCharts() }.getOrElse {
            Log.e("MainViewModel", "charts: ${it.message}")
            MusicRepository.ChartRows()
        }
        _charts.value = rows.top
        _throwback.value = rows.throwback
    }

    private suspend fun loadNewReleases() {
        val rows = runCatching { repository.getNewReleases() }.getOrElse {
            Log.e("MainViewModel", "new releases: ${it.message}")
            MusicRepository.NewReleaseRows()
        }
        _newReleases.value = rows.albums
        _newSongs.value = rows.songs
    }

    private suspend fun loadTrendingSearches() {
        _trendingSearches.value =
            runCatching { repository.getTrendingSearches() }.getOrElse {
                Log.e("MainViewModel", "trending searches: ${it.message}")
                emptyList()
            }
    }

    /**
     * Fills Home's two YouTube Music rows, and **clears** them when the Music
     * Source setting is JioSaavn-only.
     *
     * Clearing matters as much as filling: a row titled "YouTube Music trending"
     * sitting under a setting the user set to "JioSaavn only" contradicts the
     * setting, and looks like the setting was ignored. Both directions are
     * driven by the collector in `init`, so switching the setting back and forth
     * adds and removes the rows without a restart.
     *
     * The JioSaavn discovery rows are deliberately *not* cleared in the
     * YouTube-Music-only direction — that would empty Home rather than change it.
     */
    private fun loadYouTubeMusicRows(setting: SourceSetting) {
        if (setting == SourceSetting.JIOSAAVN) {
            _youTubeTop.value = emptyList()
            _youTubeHipHop.value = emptyList()
            return
        }
        viewModelScope.launch {
            val rows = runCatching { repository.getYouTubeMusicRows() }.getOrElse {
                Log.e("MainViewModel", "youtube music rows: ${it.message}")
                MusicRepository.YouTubeMusicRows()
            }
            _youTubeTop.value = rows.top
            _youTubeHipHop.value = rows.genre
        }
    }

    /**
     * Opens a Home tile. The tile carries its own [MusicRepository.BrowseTile.Kind],
     * so a chart, an editorial playlist and an album all reach the right endpoint
     * through this one entry point.
     */
    fun loadBrowsePlaylist(tile: MusicRepository.BrowseTile) {
        viewModelScope.launch {
            _isBrowseLoading.value = true
            _browsePlaylist.value = runCatching {
                repository.getCollectionSongs(tile)
            }.getOrElse {
                Log.e("MainViewModel", "browse collection: ${it.message}")
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

    // There is deliberately no `isBrowsePlaylistSaved()` here. The browse screen
    // needs the answer to be *observable* so the label flips to "Saved in
    // Library ✓" the moment a save lands, and a plain function reading
    // `PlaylistManager` is not — the caller would have to poll it. The screen
    // therefore derives it from the collected `playlists` state, which is
    // reactive by construction. `PlaylistManager.findByName` is the same
    // case-insensitive rule, so there is one rule, not two.

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
        downloadManager.download(track) { t -> resolveStreamUrl(t) }
    }

    /**
     * Sequential batch download, one track at a time to avoid rate-limiting.
     *
     * Returns false when Data Saver blocked the batch — a whole-playlist fetch is
     * exactly the background network use that setting promises to avoid, and
     * before this it was not checked anywhere. A single explicit [downloadTrack]
     * is a deliberate foreground action, so it is deliberately *not* gated.
     */
    fun downloadPlaylistTracks(tracks: List<Track>): Boolean {
        if (dataSaverPrefs.enabled.value) return false
        downloadManager.downloadAll(tracks) { t -> resolveStreamUrl(t) }
        return true
    }

    private suspend fun resolveStreamUrl(track: Track): String? = when (track.source) {
        MusicSource.YOUTUBE_MUSIC ->
            YouTubeMusicApi.resolveStreamUrl(track.videoId ?: track.id)
                ?: repository.resolveFullStream(track)
        MusicSource.JIOSAAVN -> repository.resolveFullStream(track)
    }

    fun deleteDownload(trackId: String) {
        downloadManager.delete(trackId)
    }

    /** Share a track through the system sheet. */
    fun shareTrack(track: Track) {
        val text = "Listening to \"${track.title}\" by ${track.artist} on Elara."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Share track")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
    }

    init {
        Log.d("MainViewModel", "Initializing ViewModel")
        loadHomeRows()
        cleanupOldSeededPlaylists()

        // Every property initializer in this class has run by now, so this is the
        // one place that can report the whole cold-start cost in a single line.
        // Warm constructions report nothing: the singletons are cached, so their
        // load() never runs again. See StartupTiming.
        StartupTiming.flush(appContext)

        // Trending and the YouTube Music rows both follow the Music Source
        // setting; re-fetches on change. `collect` emits the current value
        // immediately, so this is also the initial load for both.
        viewModelScope.launch {
            sourcePreferences.setting.collect {
                refreshTrending()
                loadYouTubeMusicRows(it)
            }
        }

        // Bind the playback service for as long as this ViewModel lives.
        mediaControllerFuture = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync()

        // Apply stored playback preferences.
        playbackManager.crossfadeMs = playbackPrefs.crossfadeSeconds.value * 1_000L
        playbackManager.smartShuffle = playbackPrefs.smartShuffleEnabled.value
        playbackManager.smartShuffleScorer = { current, tracks ->
            tracks.mapIndexed { index, track ->
                index to scoreTrack(current, track)
            }.toMap()
        }

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

        // Keep playing when the queue runs out: append similar tracks instead
        // of stopping. See `autoplayCandidates` for where they come from.
        if (playbackPrefs.autoplayEnabled.value) {
            playbackManager.autoplayProvider = { track -> autoplayCandidates(track) }
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
                        // Only remember searches that actually found something, so
                        // a typo or a dead-end query does not become a permanent
                        // chip. `recordSearch` also collapses the prefixes the
                        // 400 ms debounce produces while typing — see there.
                        if (_searchResults.value.isNotEmpty()) recordSearch(query)
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
                val trackId = track.id

                // Cached lyrics are instant — no spinner, no network.
                val cached = lyricsCache.get(trackId)
                if (cached != null) {
                    _lyrics.value = cached
                    _lyricsLoading.value = false
                    return@collect
                }

                _lyricsLoading.value = true
                val lyrics = runCatching { repository.getLyrics(track) }
                    .getOrElse { emptyList() }
                lyricsCache.put(trackId, lyrics)
                // Ignore stale results if the track changed while loading.
                if (currentTrack.value?.id == trackId) {
                    _lyrics.value = lyrics
                    _lyricsLoading.value = false
                }
            }
        }
    }

    /**
     * Refreshes Home's trending row, falling back to the daily cache when the
     * fetch fails.
     *
     * The decision is [TrendingCachePolicy]'s; this site only executes it. On a
     * successful fetch the cache is overwritten so the next failure serves
     * today's tracks, not last week's.
     */
    fun refreshTrending() {
        viewModelScope.launch {
            // Cold start paints the cache *before* the fetch, so the row is
            // never empty while the network works. Only an empty state is
            // painted — a mid-session refresh must never regress fresh rows to
            // yesterday's list. What wins once the fetch returns is
            // TrendingCachePolicy's decision below, not this placeholder.
            if (_trendingTracks.value.isEmpty()) {
                trendingCache.getCachedTracksAnyAge()?.takeIf { it.isNotEmpty() }?.let {
                    _trendingTracks.value = it
                }
            }
            Log.d("MainViewModel", "Refreshing trending tracks")
            _isConnecting.value = true
            val fetched = runCatching {
                repository.getTrendingBySource(sourcePreferences.setting.value)
            }.getOrElse {
                Log.e("MainViewModel", "trending failed: ${it.message}")
                emptyList()
            }
            when (val outcome = TrendingCachePolicy.resolve(fetched, trendingCache.getCachedTracksAnyAge())) {
                is TrendingCachePolicy.Outcome.Fetched -> {
                    _trendingTracks.value = outcome.tracks
                    withContext(Dispatchers.IO) { trendingCache.saveDailyTracks(outcome.tracks) }
                }
                is TrendingCachePolicy.Outcome.Stale -> {
                    Log.w("MainViewModel", "trending fetch empty — serving ${outcome.tracks.size} cached tracks")
                    _trendingTracks.value = outcome.tracks
                }
                TrendingCachePolicy.Outcome.None -> Unit
            }
            _isConnecting.value = false
        }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    fun setQueueOpen(open: Boolean) {
        _isQueueOpen.value = open
        if (open) {
            computeSimilarTracks()
        }
    }

    private fun computeSimilarTracks() {
        val current = currentTrack.value
        val queue = playbackManager.queue.value
        if (current == null) {
            _similarTracks.value = emptyList()
            return
        }

        // Use trending tracks and recently played as candidate pool for similar tracks
        val candidates = (trendingTracks.value + recentlyPlayed.value + queue).distinctBy { it.id }
        val similar = findSimilarTracks(current, candidates, queue)
        _similarTracks.value = similar
    }

    private fun scoreTrack(current: Track, track: Track): Int {
        var score = 0
        val currentArtist = current.artist.orEmpty().trim()
        val currentGenre = current.genre.orEmpty().trim()
        if (currentArtist.isNotEmpty() &&
            track.artist.orEmpty().equals(currentArtist, ignoreCase = true)
        ) score += 50
        if (currentGenre.isNotEmpty() &&
            track.genre.orEmpty().equals(currentGenre, ignoreCase = true)
        ) score += 30
        if (track.source == current.source) score += 5
        val trackEnergy = track.energy
        val currentEnergy = current.energy
        if (trackEnergy != null && currentEnergy != null) {
            val energyDiff = kotlin.math.abs(trackEnergy - currentEnergy)
            score += kotlin.math.max(0, 15 - energyDiff)
        }
        return score
    }

    private fun findSimilarTracks(
        current: Track,
        candidates: List<Track>,
        queue: List<Track>
    ): List<Track> {
        val excludedIds = (queue.map { it.id } + current.id).toSet()
        return candidates
            .filter { it.id !in excludedIds }
            .map { track -> track to scoreTrack(current, track) }
            .filter { it.second > 10 }
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }
    }

    /**
     * Autoplay pool for [PlaybackManager.autoplayProvider].
     *
     * The local pool (recently played, most played, trending) is scored first —
     * it is instant and offline. When it yields too little to keep a queue
     * going, a catalogue search for the same artist widens it, so autoplay can
     * surface music the user has never played rather than looping what they
     * already have. Everything goes through [findSimilarTracks], which is also
     * what excludes the current queue.
     */
    private suspend fun autoplayCandidates(current: Track): List<Track> {
        val queue = playbackManager.queue.value
        val local = buildList {
            addAll(recentlyPlayed.value)
            addAll(mostPlayed.value)
            addAll(trendingTracks.value)
        }
        val fromLocal = findSimilarTracks(current, local.distinctBy { it.id }, queue)
        // Data saver: never hit the catalogue for autoplay; the local pool is
        // enough or playback simply stops rather than burning mobile data.
        if (fromLocal.size >= AUTOPLAY_MIN_TRACKS || dataSaverPrefs.enabled.value) {
            return fromLocal
        }

        // Thin local pool — likely a fresh install. Ask the catalogue instead.
        // A blank artist would search for nothing and return the whole trending
        // page, which is not "similar", so bail out rather than degrade.
        val artist = current.artist.orEmpty().trim()
        if (artist.isEmpty()) return fromLocal

        val fetched = runCatching {
            repository.searchBySource(artist, sourcePreferences.setting.value)
        }.getOrElse {
            Log.w("MainViewModel", "autoplay search failed: ${it.message}")
            emptyList()
        }
        val fromSearch = findSimilarTracks(current, fetched, queue)

        return (fromLocal + fromSearch).distinctBy { it.id }.take(AUTOPLAY_MAX_TRACKS)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isConnecting.value = false
        }
    }

    /**
     * Plays [track], with [contextTracks] becoming the queue.
     *
     * "Recently played" is **not** recorded here. `PlaybackManager.playAt` owns
     * it, because that is the one point every playback path shares — a tap,
     * Next/Previous, the queue drawer, autoplay, the notification controls, and
     * resuming the queue restored from disk. Recording here as well would count
     * a single tap as two plays, which is what `MOST_PLAYED_MIN_PLAYS` would
     * then read as "played twice".
     */
    fun playTrack(track: Track, contextTracks: List<Track> = emptyList()) {
        startPlaybackService()
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

    // ── Queue management ──────────────────────────────────────────────────

    /** Jump to a queue position without discarding the rest of the queue. */
    fun jumpToQueueIndex(index: Int) {
        playbackManager.jumpTo(index)
    }

    fun removeFromQueue(index: Int) {
        playbackManager.removeFromQueue(index)
    }

    fun clearQueue() {
        playbackManager.clearQueue()
    }

    /**
     * Append to the queue.
     *
     * @return `true` if this started playback instead, because nothing was
     *   playing and there was no queue to append to. Callers use it to say
     *   "Playing now" rather than "Added to queue".
     */
    fun addToQueue(track: Track): Boolean = playbackManager.addToQueue(track)

    /** Queue a track so it plays right after the current one. */
    fun playNextInQueue(track: Track) {
        playbackManager.playNextInQueue(track)
    }

    fun moveInQueue(from: Int, to: Int) {
        playbackManager.moveInQueue(from, to)
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun cycleRepeat() {
        playbackManager.cycleRepeat()
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

    /**
     * One-shot read of the liked set — **not observable**.
     *
     * `_likedTracks.value` is a plain read, so calling this from a composable does not
     * subscribe that composable to the flow: it keeps rendering its first answer.
     * Tapping like still updated the stored list, but the heart never repainted, which
     * is indistinguishable from "the like button is broken". Compose callers must
     * collect [likedTracks] instead. This is for click handlers and non-Compose callers.
     */
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
            val type = listTypeOf(Track::class.java)
            val loaded: List<Track>? = gson.fromJson(json, type)
            loaded ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Search history ────────────────────────────────────────────────────

    /**
     * Remembers a query that returned results.
     *
     * Search runs off a 400 ms debounce, so typing "bon jovi" at an unhurried
     * pace fires for "bon", "bon j", "bon jo"… A naive prepend would turn the
     * chips into a list of every prefix, so when the newest entry is itself a
     * prefix of the incoming query it is **replaced** rather than prepended —
     * collapsing that whole run into the single query the user meant. A repeat
     * of an older query is moved to the front instead of duplicated.
     */
    private fun recordSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length < MIN_SEARCH_HISTORY_LENGTH) return

        val current = _searchHistory.value
        val newest = current.firstOrNull()
        val collapsed = if (newest != null && query.startsWith(newest, ignoreCase = true)) {
            listOf(query) + current.drop(1)
        } else {
            listOf(query) + current
        }

        val deduped = (listOf(query) + collapsed.filterNot { it.equals(query, ignoreCase = true) })
            .take(MAX_SEARCH_HISTORY)

        _searchHistory.value = deduped
        prefs.edit().putString(KEY_SEARCH_HISTORY, gson.toJson(deduped)).apply()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    private fun loadSearchHistory(): List<String> = try {
        val type = listTypeOf(String::class.java)
        val loaded: List<String>? = gson.fromJson(prefs.getString(KEY_SEARCH_HISTORY, null), type)
        loaded ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    // ── Backup & restore ──────────────────────────────────────────────────

    /**
     * The whole library as one JSON document.
     *
     * Touches only in-memory state, so there is no disk access here — the caller
     * writes the returned string wherever the user chose. It does serialise the
     * full library, which is CPU work proportional to its size, so call it from
     * a background dispatcher.
     */
    fun exportBackupJson(): String = ElaraBackup.encode(
        ElaraBackup(
            appPackage = appContext.packageName,
            playlists = playlistManager.playlists.value,
            likedTracks = _likedTracks.value,
            recentTracks = playHistory.recent.value,
            playStats = playHistory.stats.value,
            musicSource = sourcePreferences.setting.value.name
        )
    )

    /**
     * Replaces the library with the contents of [json].
     *
     * Returns null when the text is not a backup at all, and throws
     * [UnsupportedVersionException] for a document written by a newer build.
     * This **overwrites** what is on the device — the caller must confirm first.
     */
    fun importBackupJson(json: String): RestoreReport? {
        val backup = ElaraBackup.decode(json) ?: return null

        playlistManager.replaceAll(backup.playlists)
        playHistory.replaceAll(backup.recentTracks, backup.playStats)

        // Stored liked tracks carry isLiked = true; re-assert it rather than
        // trusting the file, because the flag is what the UI reads to draw the
        // filled heart.
        val liked = backup.likedTracks.map { it.copy(isLiked = true) }
        _likedTracks.value = liked
        saveLikedTracks(liked)

        backup.musicSource
            ?.let { name -> SourceSetting.entries.firstOrNull { it.name == name } }
            ?.let { sourcePreferences.set(it) }

        return RestoreReport(
            playlists = backup.playlists.size,
            likedTracks = liked.size,
            recentTracks = backup.recentTracks.size,
            playStats = backup.playStats.size,
            source = backup.musicSource
        )
    }

    private companion object {
        /**
         * How long Home's rows are considered fresh.
         *
         * Six hours is a deliberate middle: long enough that switching away from
         * the app and back never costs a network round trip, short enough that
         * "left it open overnight" is self-healing by the next morning.
         *
         * This is a **floor on freshness, not a guarantee**. There is no push
         * channel for these feeds, so the rows are only as new as the last fetch,
         * and the fetch that most users will actually experience is the one on
         * every cold start — see [refreshHomeIfStale].
         */
        const val HOME_ROWS_MAX_AGE_MS = 6 * 60 * 60 * 1000L

        const val KEY_SEARCH_HISTORY = "search_history"
        const val MAX_SEARCH_HISTORY = 10

        /** One character matches almost everything, so it is not worth a chip. */
        const val MIN_SEARCH_HISTORY_LENGTH = 2

        /**
         * Autoplay only bothers searching the catalogue when the local pool
         * can't supply this many tracks — enough to keep playing for a while.
         */
        const val AUTOPLAY_MIN_TRACKS = 5

        /** Upper bound on what one autoplay extension appends. */
        const val AUTOPLAY_MAX_TRACKS = 10
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        // Player is intentionally kept alive for background playback via PlaybackService.
    }
}
