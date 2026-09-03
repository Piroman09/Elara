package com.aesthetic.spotify.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aesthetic.spotify.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide player so UI and PlaybackService share one ExoPlayer instance:
 * lock-screen/notification controls drive the same playback the app shows.
 */
@OptIn(UnstableApi::class)
class PlaybackManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PlaybackManager"

        @Volatile private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager =
            instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(
                // DefaultDataSource routes file:// to FileDataSource (downloaded
                // songs) and http(s) to the HTTP factory (streaming). Passing
                // the HTTP factory alone made local playback fail with
                // ERROR_CODE_IO_UNSPECIFIED "Source error".
                DefaultDataSource.Factory(context, httpDataSourceFactory)
            )
        )
        .build()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    /** Human-readable reason for the latest playback failure (null = none). */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError

    private var queueIndex = -1

    /**
     * Full-length stream resolver (e.g. SoundCloud lookup). Given a catalog
     * track, returns a directly playable URL or null to use [Track.audioUrl].
     */
    var streamResolver: (suspend (Track) -> String?)? = null

    /**
     * Fast local-source lookup (downloaded files). Checked before every other
     * source so downloaded songs always play offline — even when a stale
     * remote URL sits in the resolved cache.
     */
    var localSourceResolver: ((Track) -> String?)? = null

    private val resolvedUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var playGeneration = 0

    /** Notifies the app when a downloaded file turned out to be missing/corrupt. */
    var onDownloadMissing: ((Track) -> Unit)? = null

    private var lastUrlWasLocal = false
    private val autoHealedIds = mutableSetOf<String>()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) _durationMs.value = d
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackManager", "Player Error: ${error.message}")
                _isPlaying.value = false
                // A cached network URL may have expired — drop it so the next
                // attempt re-resolves instead of failing again.
                exoPlayer.currentMediaItem?.mediaId?.let { resolvedUrls.remove(it) }

                val friendly = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Needs internet — this song isn't downloaded."
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                        "Downloaded file missing — re-download this song."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Stream refused by server — try again."
                    else -> "Playback error (${error.errorCodeName})"
                }
                _playbackError.value = friendly
                appendDiagnostic("PLAYER_ERROR id=${exoPlayer.currentMediaItem?.mediaId} code=${error.errorCodeName} msg=${error.message} cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")

                // Self-heal: the local file vanished (failed migration, cleared
                // storage). Drop the dead download entry and retry from the
                // network once. User taps reset the guard.
                val fileMissing = lastUrlWasLocal && (
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    )
                val currentId = exoPlayer.currentMediaItem?.mediaId
                val currentTrack = _queue.value.getOrNull(queueIndex)
                if (fileMissing && currentId != null && currentTrack != null &&
                    autoHealedIds.add(currentId)
                ) {
                    appendDiagnostic("SELF_HEAL retry from network id=$currentId")
                    onDownloadMissing?.invoke(currentTrack)
                }
            }
        })

        // Position ticker for the UI slider/lyrics sync.
        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0)
                    val d = exoPlayer.duration
                    if (d > 0) _durationMs.value = d
                }
                delay(400)
            }
        }
    }

    /** Replace the queue and start playing [startIndex]. */
    fun setQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        autoHealedIds.clear()
        _queue.value = tracks
        playAt(startIndex.coerceIn(0, tracks.lastIndex))
    }

    fun playTrack(track: Track, contextTracks: List<Track> = emptyList()) {
        val idx = contextTracks.indexOfFirst { it.id == track.id }
        if (idx >= 0 && contextTracks.size > 1) {
            setQueue(contextTracks, idx)
        } else {
            setQueue(listOf(track), 0)
        }
    }

    fun playAt(index: Int) {
        val tracks = _queue.value
        if (index !in tracks.indices) return
        queueIndex = index
        val track = tracks[index]
        _currentTrack.value = track
        _positionMs.value = 0

        val generation = ++playGeneration
        scope.launch {
            _playbackError.value = null

            // Priority: downloaded file → cached remote URL → fresh resolve.
            // The local check runs first so downloads always win offline,
            // even when a (now unusable) remote URL is cached.
            val localUrl = localSourceResolver?.invoke(track)
            lastUrlWasLocal = localUrl != null
            val url = localUrl
                ?: resolvedUrls[track.id]
                ?: streamResolver?.invoke(track)?.also { resolvedUrls[track.id] = it }
                ?: track.audioUrl

            // User moved on before resolution finished — skip this start.
            if (generation != playGeneration) return@launch

            if (url.isBlank()) {
                val msg = "Can't play \"${track.title}\" — download it or connect to the internet."
                _playbackError.value = msg
                Log.e(TAG, msg)
                appendDiagnostic("NO_SOURCE id=${track.id} local=${localUrl != null} offline?")
                return@launch
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(
                            track.albumArt.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                        )
                        .build()
                )
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun playNext() {
        if (queueIndex + 1 <= _queue.value.lastIndex) {
            playAt(queueIndex + 1)
        } else {
            exoPlayer.pause()
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
            _positionMs.value = 0
        } else if (queueIndex - 1 >= 0) {
            playAt(queueIndex - 1)
        } else {
            exoPlayer.seekTo(0)
            _positionMs.value = 0
        }
    }

    fun seekTo(ms: Long) {
        exoPlayer.seekTo(ms.coerceIn(0, _durationMs.value))
        _positionMs.value = ms
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    fun release() {
        exoPlayer.release()
    }

    /** Retries the current track without its (now removed) local file. */
    fun retryCurrentWithoutLocal() {
        scope.launch {
            kotlinx.coroutines.delay(150)
            playAt(queueIndex)
        }
    }

    /** Appends playback failures to a shareable diagnostics file on-device. */
    private fun appendDiagnostic(line: String) {
        runCatching {
            val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            java.io.File(dir, "playback_diag.txt")
                .appendText("\n${System.currentTimeMillis()}  $line\n")
        }
    }
}
