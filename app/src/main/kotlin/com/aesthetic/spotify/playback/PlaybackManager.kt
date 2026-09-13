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
import com.aesthetic.spotify.data.diagnostics.PlaybackDiagnostics
import com.aesthetic.spotify.data.model.SafeGson
import com.aesthetic.spotify.data.model.Track
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** How the queue behaves once it runs off the end. */
enum class RepeatMode { OFF, ALL, ONE }

/**
 * Process-wide player so UI and PlaybackService share one ExoPlayer instance:
 * lock-screen/notification controls drive the same playback the app shows.
 *
 * The queue is a *cursor* over an ordered list: [queue] holds every track in
 * running order and [queueIndex] points at the one currently playing. Tracks
 * before the cursor have already played (reachable with Previous), tracks after
 * it are up next. Nothing is ever removed from the list by normal navigation.
 */
@OptIn(UnstableApi::class)
class PlaybackManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PlaybackManager"
        private const val KEY_QUEUE = "queue_tracks"
        private const val KEY_QUEUE_INDEX = "queue_index"
        private const val KEY_SHUFFLE = "queue_shuffle"
        private const val KEY_REPEAT = "queue_repeat"

        @Volatile private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager =
            instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs = appContext.getSharedPreferences("aurasonic_prefs", Context.MODE_PRIVATE)
    private val gson = SafeGson.instance

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

    /** Index of the playing track inside [queue]; -1 when it isn't part of it. */
    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle

    /**
     * Defaults to [RepeatMode.OFF] so the starting state matches the first step
     * of [cycleRepeat]'s `OFF → ALL → ONE` rotation.
     *
     * It used to default to ALL, which put the app out of step with its own
     * cycle: the repeat button opened already tinted violet ("on"), and the
     * first tap jumped straight to repeat-*one*. Getting back to OFF took two
     * taps. Keep this default and the head of [cycleRepeat] in agreement.
     */
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    /**
     * Playback order used while shuffle is on: a permutation of queue indices
     * with the current track pinned at position 0. [shufflePos] is how far
     * along that order we are, so nothing repeats until every track has played.
     */
    private var shuffleOrder: List<Int> = emptyList()
    private var shufflePos = 0

    /** Human-readable reason for the latest playback failure (null = none). */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError

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
                    onTrackFinished()
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
                val playing = _queue.value.getOrNull(_queueIndex.value)
                if (fileMissing && currentId != null && playing != null &&
                    autoHealedIds.add(currentId)
                ) {
                    appendDiagnostic("SELF_HEAL retry from network id=$currentId")
                    onDownloadMissing?.invoke(playing)
                }
            }
        })

        restoreQueue()

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

    // ── Queue construction ────────────────────────────────────────────────

    /** Replace the queue and start playing [startIndex]. */
    fun setQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        autoHealedIds.clear()
        _queue.value = tracks
        rebuildShuffleOrder(startIndex.coerceIn(0, tracks.lastIndex))
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

    // ── Navigation ────────────────────────────────────────────────────────

    /** Jump to a specific queue position without disturbing the rest of it. */
    fun jumpTo(index: Int) {
        if (index !in _queue.value.indices) return
        if (index == _queueIndex.value) {
            if (!exoPlayer.isPlaying) togglePlay()
            return
        }
        if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(index)
            if (pos >= 0) shufflePos = pos else rebuildShuffleOrder(index)
        }
        playAt(index)
    }

    fun playAt(index: Int) {
        val tracks = _queue.value
        if (index !in tracks.indices) return
        _queueIndex.value = index
        val track = tracks[index]
        _currentTrack.value = track
        _positionMs.value = 0
        persistQueue()

        val generation = ++playGeneration
        scope.launch {
            _playbackError.value = null

            // Priority: downloaded file → cached remote URL → fresh resolve.
            // The local check runs first so downloads always win offline,
            // even when a (now unusable) remote URL is cached.
            //
            // Both halves are dispatched to IO because both touch the disk or
            // the network: the local check stats a file
            // (`DownloadManager.localPath` ends in `File.exists()`), and the
            // JioSaavn resolver AES-decrypts the stream URL in-process. This
            // coroutine is scoped to `Dispatchers.Main`, so without the hop that
            // was a `stat()` — and an in-process decrypt — on the UI thread on
            // every single track change.
            val localUrl = withContext(Dispatchers.IO) { localSourceResolver?.invoke(track) }
            lastUrlWasLocal = localUrl != null
            val url = localUrl
                ?: resolvedUrls[track.id]
                ?: withContext(Dispatchers.IO) { streamResolver?.invoke(track) }
                    ?.also { resolvedUrls[track.id] = it }
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

    private fun onTrackFinished() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                exoPlayer.seekTo(0)
                _positionMs.value = 0
                exoPlayer.play()
            }
            else -> playNext()
        }
    }

    fun playNext() {
        val tracks = _queue.value
        if (tracks.isEmpty()) return
        val idx = _queueIndex.value

        var target = -1
        if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            if (shufflePos + 1 < shuffleOrder.size) {
                target = shuffleOrder[shufflePos + 1]
            } else if (_repeatMode.value == RepeatMode.ALL) {
                // New lap: reshuffle but never repeat the track we just heard.
                val order = QueueOrder.shuffleOrder(tracks.size, idx, Random)
                if (order.size > 1) {
                    shuffleOrder = order
                    shufflePos = 1
                    target = order[1]
                }
            }
        } else if (idx >= 0 && idx + 1 <= tracks.lastIndex) {
            target = idx + 1
        } else if (idx < 0) {
            target = 0
        } else if (_repeatMode.value == RepeatMode.ALL) {
            target = 0
        }

        if (target in tracks.indices) playAt(target) else exoPlayer.pause()
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
            _positionMs.value = 0
            return
        }

        val tracks = _queue.value
        val idx = _queueIndex.value

        if (_isShuffle.value && shufflePos > 0 && shuffleOrder.isNotEmpty()) {
            val target = shuffleOrder[shufflePos - 1]
            if (target in tracks.indices) {
                playAt(target)
                return
            }
        }

        if (idx - 1 >= 0) {
            playAt(idx - 1)
        } else {
            exoPlayer.seekTo(0)
            _positionMs.value = 0
        }
    }

    // ── Queue mutation ────────────────────────────────────────────────────

    /**
     * Append to the end of the queue.
     *
     * Returns `true` when this started playback instead of appending, which is
     * the only correct thing to do when nothing is playing: there is no queue to
     * append to, and a cursor sitting at -1 leaves the queue **unreachable** —
     * `currentTrack` stays null, so the mini player never appears and the queue
     * drawer cannot be opened at all. The song would silently vanish. Reachable
     * from a restored-but-unplayed queue too, where [restoreQueue] clamps an
     * out-of-range saved index to -1.
     */
    fun addToQueue(track: Track): Boolean {
        if (_currentTrack.value == null) {
            playTrack(track)
            return true
        }

        val next = _queue.value + track
        _queue.value = next
        if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            // Slot it into the unplayed tail so shuffle progress survives.
            val insertAt = QueueOrder.shuffleTailInsertPosition(
                shuffleOrder.size, shufflePos, Random
            )
            shuffleOrder = shuffleOrder.toMutableList().apply {
                add(insertAt.coerceAtMost(size), next.lastIndex)
            }
        }
        persistQueue()
        return false
    }

    /** Insert directly after the playing track so it plays next. */
    fun playNextInQueue(track: Track) {
        val idx = _queueIndex.value
        val insertAt = if (idx >= 0) idx + 1 else _queue.value.size
        val next = _queue.value.toMutableList().apply { add(insertAt, track) }
        _queue.value = next

        if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            // Shift everything at/after the insertion point, then slot the new
            // index in right after the current shuffle position.
            shuffleOrder = QueueOrder.orderAfterInsert(shuffleOrder, insertAt)
                .toMutableList()
                .apply { add((shufflePos + 1).coerceAtMost(size), insertAt) }
        }
        persistQueue()
    }

    /** Remove a queue entry, keeping the cursor on the same physical track. */
    fun removeFromQueue(index: Int) {
        val tracks = _queue.value
        if (index !in tracks.indices) return

        _queue.value = tracks.toMutableList().apply { removeAt(index) }

        val idx = _queueIndex.value
        // Removing the playing entry leaves the cursor at -1: the track keeps
        // playing, it is simply no longer part of the queue.
        val nextIndex = QueueOrder.cursorAfterRemove(idx, index)
        _queueIndex.value = nextIndex

        if (nextIndex < 0) {
            shuffleOrder = emptyList()
            shufflePos = 0
        } else if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            val order = QueueOrder.orderAfterRemove(shuffleOrder, index)
            shuffleOrder = order
            shufflePos = order.indexOf(nextIndex).coerceAtLeast(0)
        }
        persistQueue()
    }

    /** Move an entry within the queue, keeping the cursor on the same track. */
    fun moveInQueue(from: Int, to: Int) {
        val tracks = _queue.value
        if (from == to) return
        if (from !in tracks.indices || to !in tracks.indices) return

        val next = tracks.toMutableList()
        val moved = next.removeAt(from)
        next.add(to, moved)
        _queue.value = next

        val idx = _queueIndex.value
        _queueIndex.value = QueueOrder.cursorAfterMove(idx, from, to)

        if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            val order = QueueOrder.orderAfterMove(shuffleOrder, from, to)
            shuffleOrder = order
            shufflePos = order.indexOf(_queueIndex.value).coerceAtLeast(0)
        }
        persistQueue()
    }

    /** Drop everything that is still queued, without stopping the current song. */
    fun clearQueue() {
        val tracks = _queue.value
        val idx = _queueIndex.value
        val playing = tracks.getOrNull(idx)
        if (playing != null) {
            _queue.value = listOf(playing)
            _queueIndex.value = 0
        } else {
            _queue.value = emptyList()
            _queueIndex.value = -1
        }
        // Rebuild rather than blank: with shuffle still on, an empty permutation
        // would silently make playback sequential while the toggle claims
        // otherwise, and later addToQueue calls would stop slotting tracks in.
        rebuildShuffleOrder(_queueIndex.value)
        persistQueue()
    }

    // ── Shuffle & repeat ──────────────────────────────────────────────────

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        rebuildShuffleOrder(_queueIndex.value)
        persistQueue()
    }

    fun cycleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        persistQueue()
    }

    /**
     * Rebuilds the shuffle permutation around [index], or clears it when shuffle
     * is off. All-or-nothing so shuffle can never be left enabled with a blank
     * permutation — see [QueueOrder.shuffleStateFor].
     */
    private fun rebuildShuffleOrder(index: Int) {
        shuffleOrder = QueueOrder.shuffleStateFor(
            size = _queue.value.size,
            cursor = index,
            shuffleOn = _isShuffle.value,
            random = Random
        )
        shufflePos = 0
    }

    // ── Transport ─────────────────────────────────────────────────────────

    fun seekTo(ms: Long) {
        exoPlayer.seekTo(ms.coerceIn(0, _durationMs.value))
        _positionMs.value = ms
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.mediaItemCount == 0) {
                // Queue restored from disk: the player has no source attached
                // yet, so a bare play() would silently do nothing. Re-attach
                // the track the cursor is sitting on and start it.
                val idx = _queueIndex.value
                if (idx >= 0) {
                    playAt(idx)
                    return
                }
            }
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
            delay(150)
            playAt(_queueIndex.value)
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private fun persistQueue() {
        runCatching {
            prefs.edit()
                .putString(KEY_QUEUE, gson.toJson(_queue.value))
                .putInt(KEY_QUEUE_INDEX, _queueIndex.value)
                .putBoolean(KEY_SHUFFLE, _isShuffle.value)
                .putString(KEY_REPEAT, _repeatMode.value.name)
                .apply()
        }
    }

    /** Restores the last queue paused, so the app reopens where it left off. */
    private fun restoreQueue() {
        try {
            val json = prefs.getString(KEY_QUEUE, null) ?: return
            val type = object : TypeToken<List<Track>>() {}.type
            // SafeGson normalises every track it parses, so tracks persisted
            // before a field existed can't hand nulls to the rest of the app.
            val tracks: List<Track>? = gson.fromJson(json, type)
            if (tracks.isNullOrEmpty()) return

            val savedIndex = prefs.getInt(KEY_QUEUE_INDEX, -1)
            val index = if (savedIndex in tracks.indices) savedIndex else -1

            _queue.value = tracks
            _queueIndex.value = index
            _isShuffle.value = prefs.getBoolean(KEY_SHUFFLE, false)
            _repeatMode.value =
                runCatching { RepeatMode.valueOf(prefs.getString(KEY_REPEAT, null) ?: "") }
                    .getOrDefault(RepeatMode.OFF)
            if (_isShuffle.value) rebuildShuffleOrder(index)

            tracks.getOrNull(index)?.let { track ->
                _currentTrack.value = track
                _durationMs.value = track.duration * 1000L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Queue restore failed: ${e.message}")
        }
    }

    /**
     * Appends playback failures to the shareable on-device diagnostics log.
     * Dispatched to IO by [PlaybackDiagnostics] — this is called from the player
     * error listener and from `playAt`, both on the main dispatcher.
     */
    private fun appendDiagnostic(line: String) {
        PlaybackDiagnostics.append(appContext, line)
    }
}
