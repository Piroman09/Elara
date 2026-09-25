package com.piroman.elara.playback

import android.content.Context
import android.media.audiofx.EnvironmentalReverb
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.piroman.elara.data.diagnostics.PlaybackDiagnostics
import com.piroman.elara.data.diagnostics.StartupTiming
import com.piroman.elara.data.history.PlayHistoryManager
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.stats.ListeningStatsManager
import com.piroman.elara.data.model.SpeedMode
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.model.listTypeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

        /**
         * Playback offset saved alongside the queue so reopening the app resumes
         * the song where it was left rather than from the top.
         */
        private const val KEY_POSITION = "queue_position"

        /**
         * How often the playing offset is written to disk. Every tick (400 ms)
         * would mean ~2.5 `SharedPreferences` commits per second for a value
         * that only matters on the next cold start.
         */
        private const val POSITION_PERSIST_INTERVAL_MS = 5_000L

        /**
         * Resuming this close to the end is worse than not resuming: the track
         * would end a second or two after the app opens. Anything inside this
         * window of the outro restarts from 0 instead.
         */
        private const val RESUME_TAIL_GRACE_MS = 10_000L

        /**
         * Sleep-timer countdown granularity. One second is what the label needs;
         * coarser and the displayed minutes would visibly skip.
         */
        private const val SLEEP_TICK_MS = 1_000L

        /**
         * How long the volume takes to reach zero before pausing when the sleep
         * timer expires. A hard cut at bedtime is jarring; 10 seconds is enough
         * to notice the fade without dragging it out.
         */
        private const val FADE_OUT_MS = 10_000L

        /**
         * Ramp granularity for the crossfade fade-out. The position ticker runs
         * every 400 ms, which is far too coarse for the short end of the slider
         * (2 s = five audible steps). The ramp gets its own 50 ms job; the ticker
         * only decides whether we are inside the fade window.
         */
        private const val CROSSFADE_STEP_MS = 50L

        @Volatile private var instance: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager =
            instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext

    /**
     * Last line of defence against "the app suddenly stops". Every fallible
     * `scope.launch` body must still guard its own work (see [playAt]); this
     * handler covers anything that slips through — a tick in the position
     * ticker, a sleep-timer fade, an autoplay append — so an unexpected
     * exception degrades into a log line instead of killing the process.
     *
     * Without it, any uncaught exception inside a `Dispatchers.Main` coroutine
     * crashes the app exactly like an exception on the UI thread, and the user
     * sees a silent vanish. `PlaybackDiagnostics.append` is itself throw-safe
     * (its write runs in `runCatching`), so the handler cannot become a new
     * crash site.
     */
    private val scope = CoroutineScope(
        SupervisorJob() +
            CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Unhandled playback coroutine failure", throwable)
                PlaybackDiagnostics.append(
                    appContext,
                    "CRASH_GUARD ${throwable.javaClass.simpleName}: ${throwable.message}"
                )
            } +
            Dispatchers.Main
    )

    private val prefs = appContext.getSharedPreferences("aurasonic_prefs", Context.MODE_PRIVATE)
    private val gson = SafeGson.instance
    private val statsManager = ListeningStatsManager.getInstance(appContext)

    /**
     * "Recently played" / "Most played" history.
     *
     * Held here, not in the UI, because the only place that sees *every* way a
     * track can start is [playAt]. See the note there.
     */
    private val playHistory = PlayHistoryManager.getInstance(appContext)

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)

    /**
     * Music attributes with `handleAudioFocus = true`, which is what makes the
     * player duck/pause for calls, alarms and other apps. Declaring the usage
     * also keeps us out of the "media" bucket on Android 12+ for free.
     */
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /**
     * Observes decoded PCM on its way to the speaker so the full-screen player
     * can draw a live spectrum. Installed in the sink via
     * [VisualizerRenderersFactory]; costs nothing while [SpectrumTapper.active]
     * is false (zero-copy pass-through).
     */
    val spectrumTapper = SpectrumTapper()

    val exoPlayer: ExoPlayer = StartupTiming.measure("ExoPlayer.build") {
        ExoPlayer.Builder(context, VisualizerRenderersFactory(context, spectrumTapper))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(
                    // DefaultDataSource routes file:// to FileDataSource (downloaded
                    // songs) and http(s) to the HTTP factory (streaming). Passing
                    // the HTTP factory alone made local playback fail with
                    // ERROR_CODE_IO_UNSPECIFIED "Source error".
                    DefaultDataSource.Factory(context, httpDataSourceFactory)
                )
            )
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // Pause when headphones are unplugged / Bluetooth disconnects.
            // Without this the phone keeps blasting music out of the speaker.
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /**
     * Milliseconds left on the sleep timer, or null when it is off.
     *
     * Owned here rather than by the ViewModel on purpose: a ViewModel dies with
     * the UI, so a timer kept there would stop counting the moment the app is
     * backgrounded — which is precisely when a sleep timer is wanted. This
     * instance is process-wide and the playback service keeps it alive.
     */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs

    private var sleepTimerJob: Job? = null

    /** The fine-grained outro fade, when one is in flight. */
    private var crossfadeJob: Job? = null

    /**
     * How many milliseconds before the end of a track the volume should start
     * fading to zero. 0 = no fade (full volume until the cut).
     *
     * This is a **fade-out**, not a true crossfade: only the outgoing track is
     * attenuated and the next one starts at full volume. An overlapping blend
     * needs two players producing audio at once, but [exoPlayer] holds a single
     * MediaItem and `PlaybackService` binds one MediaSession to one Player — so
     * it would mean a dual-player rewrite of this class plus a session
     * hand-off. The UI copy says "fade out" to match what actually happens.
     */
    var crossfadeMs = 0L

    /**
     * Volume is owned here so that independent fades *compose* instead of
     * clobbering each other. Both the crossfade fade-out and the sleep-timer
     * fade-out used to write `exoPlayer.volume` directly, so the 400 ms position
     * ticker would reset the sleep fade to full volume whenever crossfade was on.
     */
    @Volatile private var crossfadeFactor = 1f
    @Volatile private var sleepFadeFactor = 1f

    /**
     * The single writer for the player's volume. The combination rule lives in
     * [VolumeMix] so it can be unit-tested without a device.
     */
    private fun applyVolume() {
        val target = VolumeMix.mix(crossfadeFactor, sleepFadeFactor)
        if (exoPlayer.volume != target) exoPlayer.volume = target
    }

    /** When true, shuffle orders tracks by similarity to the current song. */
    var smartShuffle = false

    /**
     * Scores tracks for smart shuffle. Given the current track and the full
     * queue, returns a map of queue index to similarity score (0–100+).
     * Higher = more similar. A null provider disables smart shuffle even when
     * [smartShuffle] is true.
     */
    var smartShuffleScorer: ((Track, List<Track>) -> Map<Int, Int>)? = null

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

    /**
     * Supplies tracks to keep playing when the queue runs off its end with
     * repeat off. Given the last track, returns candidates to append — the
     * caller decides what "similar" means, and returning an empty list stops
     * playback as before.
     */
    var autoplayProvider: (suspend (Track) -> List<Track>)? = null

    /**
     * The active speed mode. Written by MainViewModel from [SpeedModePreferences];
     * applied to the player (rate + pitch + optional reverb) by [applySpeedMode].
     */
    @Volatile var speedMode: SpeedMode = SpeedMode.NORMAL

    /** Reverb engine for Slowed mode. Null until first attached to a session. */
    private var environmentalReverb: EnvironmentalReverb? = null

    /** The audio session the reverb is currently attached to. */
    private var reverbSessionId = 0

    private val resolvedUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var playGeneration = 0

    /** In-flight autoplay fetch, so a second queue-end can't stack another one. */
    private var autoplayJob: Job? = null

    /**
     * Offset to resume at the next time playback starts, restored from
     * [KEY_POSITION] at construction. Held separately from [_positionMs] so a
     * cold start can show the resume point in the UI *before* the player has
     * anything loaded; cleared once consumed by [togglePlay].
     */
    private var restoredPositionMs = 0L

    /** Last offset written to disk, so the ticker only persists real movement. */
    private var lastPersistedPositionMs = -1L

    /** The mode that was last applied to the player, so mode switches re-apply. */
    private var appliedSpeedMode: SpeedMode? = null

    /** Notifies the app when a downloaded file turned out to be missing/corrupt. */
    var onDownloadMissing: ((Track) -> Unit)? = null

    /**
     * Monthly listening aggregate for Wrapped. Set by MainViewModel at init;
     * [playAt] and the position ticker feed it the same events history and
     * listening stats already receive — no third path that could disagree.
     */
    var wrappedStats: com.piroman.elara.data.history.WrappedStatsManager? = null

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

        StartupTiming.measure("PlaybackManager.restoreQueue") { restoreQueue() }

        // Position ticker for the UI slider/lyrics sync.
        var lastTickPos = 0L
        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0)
                    val delta = pos - lastTickPos
                    // Only count real forward progress (ignore seeks and resets).
                    if (delta in 1..10_000) {
                        statsManager.addTime(delta)
                        wrappedStats?.addListeningTime(delta)
                    }
                    lastTickPos = pos
                    _positionMs.value = pos
                    // Throttled: this is the only place playback progress reaches
                    // disk, so a crash or a kill still resumes within 5 seconds
                    // of where it stopped.
                    if (kotlin.math.abs(pos - lastPersistedPositionMs) >=
                        POSITION_PERSIST_INTERVAL_MS
                    ) {
                        persistPosition(pos)
                    }
                    val d = exoPlayer.duration
                    if (d > 0) {
                        _durationMs.value = d
                        // Fade the outro down as it approaches. This ticker only
                        // decides *whether* we are inside the fade window; the ramp
                        // itself runs on its own finer-grained job, because 400 ms
                        // steps are audible at the short end of the slider.
                        updateCrossfadeFade(pos, d)
                    }
                } else {
                    // Playback stopped (pause, sleep timer, end of queue). Flush
                    // the listening total that addTime batched — this one hook
                    // covers every pause path, and flush() is a no-op once there
                    // is nothing pending, so it does not write on every tick.
                    statsManager.flush()
                }
                delay(400)
            }
        }
    }

    /**
     * Start or stop the outro fade for the current playback position.
     *
     * Called from the 400 ms position ticker, which is the only thing that knows
     * where we are — but is far too coarse to *drive* the ramp.
     */
    private fun updateCrossfadeFade(positionMs: Long, durationMs: Long) {
        val remaining = durationMs - positionMs
        if (crossfadeMs > 0L && remaining in 1..crossfadeMs) {
            if (crossfadeJob?.isActive != true) startCrossfadeFade(remaining)
        } else {
            // Either crossfade is off, or we are outside the window (including
            // after a seek backwards) — restore full volume.
            endCrossfadeFade()
        }
    }

    private fun startCrossfadeFade(remainingMs: Long) {
        crossfadeJob = scope.launch {
            var left = remainingMs
            while (left > 0 && isActive) {
                crossfadeFactor = (left / crossfadeMs.toFloat()).coerceIn(0f, 1f)
                applyVolume()
                delay(CROSSFADE_STEP_MS)
                left -= CROSSFADE_STEP_MS
            }
            if (isActive) {
                crossfadeFactor = 0f
                applyVolume()
            }
        }
    }

    /**
     * Cancel any in-flight fade and restore full volume. Safe to call when no
     * fade is running, so callers do not have to track that themselves.
     */
    private fun endCrossfadeFade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        if (crossfadeFactor != 1f) {
            crossfadeFactor = 1f
            applyVolume()
        }
    }

    /**
     * Restart the current track from the top, at full volume.
     *
     * Three paths need this: repeat-one when a track ends, and both of
     * `playPrevious`'s "restart the song" branches. All three used to inline
     * `seekTo(0)` and leave the outro fade alone — so with Fade Out on, the track
     * had already ramped to `crossfadeFactor = 0f`, the restart began **silent**,
     * and it stayed silent until the next 400 ms ticker tick happened to notice
     * the position was back outside the fade window. On repeat-one that is an
     * audible clipped start on every single loop, for a gap whose length depends
     * on where the ticker happened to be.
     *
     * `playAt` already resets the fade for every other transition; this keeps the
     * bare restarts in step with it. Extracted rather than repeated because the
     * reset is exactly the step three copies of `seekTo(0)` had all missed.
     */
    private fun restartCurrentTrack() {
        endCrossfadeFade()
        exoPlayer.seekTo(0)
        _positionMs.value = 0
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

    /**
     * Start the queue entry at [index], optionally from [startPositionMs].
     *
     * The offset is only ever non-zero on the resume-after-cold-start path; the
     * seek is issued before `prepare()` completes, which ExoPlayer honours as
     * the initial position.
     *
     * **This is the single owner of "recently played" history.** Every way a
     * track can start funnels through here — tapping a track ([setQueue]),
     * Next/Previous, the queue drawer ([jumpTo]), autoplay, the
     * notification/lock-screen controls (they drive [playNext]/[playPrevious]),
     * and pressing play on the queue restored from disk ([togglePlay]). That is
     * why the recording lives here rather than in `MainViewModel.playTrack`,
     * which is where it used to be: recording only on a UI tap meant a session
     * spent skipping, autoplaying, or resuming a restored queue wrote **no
     * history at all**, so "Recently played" was empty the next time the app
     * opened. [recordHistory] is false only for [retryCurrentWithoutLocal],
     * where the same track is being re-attached rather than played again.
     */
    fun playAt(index: Int, startPositionMs: Long = 0L, recordHistory: Boolean = true) {
        val tracks = _queue.value
        if (index !in tracks.indices) return
        _queueIndex.value = index
        val track = tracks[index]
        _currentTrack.value = track
        statsManager.recordTrack(track.id)
        wrappedStats?.recordPlay(track.id)
        if (recordHistory) playHistory.record(track)
        val start = startPositionMs.coerceAtLeast(0L)
        _positionMs.value = start
        persistQueue()
        // Reset immediately: otherwise quitting within the 5 s persist window
        // would leave the *previous* track's offset on disk, which the next cold
        // start would then apply to a different song.
        persistPosition(start)
        // A new track means a fresh start: clear any in-flight outro fade, or the
        // next song would inherit a ducked volume from the previous one's fade.
        endCrossfadeFade()

        val generation = ++playGeneration
        scope.launch {
            _playbackError.value = null
            // The whole resolve chain is guarded because every step can throw
            // raw RuntimeExceptions, and before this guard one did exactly
            // what users report as "the app suddenly stops": an uncaught
            // exception inside a main-dispatcher coroutine crashes the process.
            // The resolvers are lambdas owned by MainViewModel (network
            // libraries are the usual offenders), but so is the ExoPlayer tail —
            // `setMediaItem`/`prepare` have thrown IllegalStateException on a
            // player released mid-transition. Any of them failing is a
            // *playback* problem, so it becomes a [playbackError] the UI
            // already knows how to show, plus a diagnostics line, never a
            // crash. Cancellation is rethrown: swallowing it would break
            // structured concurrency for the scope's other jobs.
            try {
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
                // Seek before play(): ExoPlayer keeps the position across prepare,
                // so the track starts at the resume point rather than the intro.
                if (start > 0) exoPlayer.seekTo(start)
                exoPlayer.play()
                // Apply the speed mode on every start, not once at init: the
                // mode can change between tracks, and some codec paths reset
                // sink parameters on stream changes. Cheap when already applied.
                applySpeedMode(exoPlayer.isPlaying)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "playAt: starting \"${track.title}\" failed", e)
                appendDiagnostic(
                    "RESOLVE_FAIL id=${track.id} ${e.javaClass.simpleName}: ${e.message}"
                )
                // A superseded generation's failure is stale news — the user has
                // already picked something else, and its own playAt owns the
                // error surface now.
                if (generation == playGeneration) {
                    _isPlaying.value = false
                    _playbackError.value =
                        "Couldn't play \"${track.title}\" — ${e.message ?: "please try again"}."
                }
            }
        }
    }

    private fun onTrackFinished() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                restartCurrentTrack()
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
                // The lap starts at QueueOrder.nextLapPosition — position 1, or
                // position 0 when the queue holds a single track (there is no
                // position 1 to start from, and repeat-all must still loop).
                val order = QueueOrder.shuffleOrder(tracks.size, idx, Random)
                shuffleOrder = order
                val lap = QueueOrder.nextLapPosition(order)
                shufflePos = lap
                target = order[lap]
            }
        } else if (idx >= 0 && idx + 1 <= tracks.lastIndex) {
            target = idx + 1
        } else if (idx < 0) {
            target = 0
        } else if (_repeatMode.value == RepeatMode.ALL) {
            target = 0
        }

        if (target in tracks.indices) {
            playAt(target)
        } else {
            // Ran off the end of the queue with repeat off. Rather than dead
            // air, ask for something similar; an absent/failing provider pauses
            // exactly as before.
            val last = tracks.getOrNull(idx)
            if (last != null) startAutoplay(last) else exoPlayer.pause()
        }
    }

    /**
     * Extends the queue with similar tracks once it runs out, then plays the
     * first of them. Nothing already in the queue is accepted, so a provider
     * that keeps returning the same tracks can't build an endless loop.
     */
    private fun startAutoplay(from: Track) {
        val provider = autoplayProvider
        if (provider == null) {
            exoPlayer.pause()
            return
        }
        if (autoplayJob?.isActive == true) return

        val generation = playGeneration
        autoplayJob = scope.launch {
            val candidates = runCatching { provider(from) }.getOrElse { emptyList() }
            // The user picked something while we were fetching — drop this.
            if (generation != playGeneration) return@launch

            val known = _queue.value.mapTo(HashSet()) { it.id }
            val fresh = candidates.filter { it.id !in known }
            if (fresh.isEmpty()) {
                exoPlayer.pause()
                appendDiagnostic("AUTOPLAY exhausted after id=${from.id}")
                return@launch
            }

            appendToQueue(fresh)
            persistQueue()
            val first = _queue.value.size - fresh.size
            if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
                val pos = shuffleOrder.indexOf(first)
                if (pos >= 0) shufflePos = pos
            }
            appendDiagnostic("AUTOPLAY appended ${fresh.size} after id=${from.id}")
            playAt(first)
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000) {
            restartCurrentTrack()
            return
        }

        // `null` means "restart the current track" — at the head of a shuffle
        // lap, or at index 0 of a sequential queue. See QueueOrder.previousTarget.
        val target = QueueOrder.previousTarget(
            queueSize = _queue.value.size,
            cursor = _queueIndex.value,
            shuffleOn = _isShuffle.value,
            shuffleOrder = shuffleOrder,
            shufflePos = shufflePos
        )
        if (target != null) {
            playAt(target)
        } else {
            restartCurrentTrack()
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
        appendToQueue(listOf(track))
        persistQueue()
        return false
    }

    /**
     * Append [tracks] to the end of the queue.
     *
     * When shuffle is on each new index is slotted into the *unplayed* tail
     * (see [QueueOrder.shuffleTailInsertPosition]) so appending mid-shuffle
     * neither jumps the queue nor strands the new songs behind the cursor.
     */
    private fun appendToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val base = _queue.value.size
        _queue.value = _queue.value + tracks
        if (_isShuffle.value && shuffleOrder.isNotEmpty()) {
            var order = shuffleOrder
            for (i in tracks.indices) {
                val insertAt = QueueOrder.shuffleTailInsertPosition(order.size, shufflePos, Random)
                order = order.toMutableList().apply { add(insertAt.coerceAtMost(size), base + i) }
            }
            shuffleOrder = order
        }
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
     *
     * The `!isShuffle` clause on the smart path keeps the invariant **total** in
     * the other direction too. [QueueOrder.shuffleStateFor] returns an empty list
     * when shuffle is off, but [QueueOrder.smartShuffleOrder] has no `shuffleOn`
     * parameter and always returns a full permutation — so with Smart Shuffle on
     * and Shuffle off, this method used to leave a populated `shuffleOrder`
     * behind. That was inert only because every reader guards on `isShuffle`
     * (see [playNext], [jumpTo], [removeFromQueue], [moveInQueue], [appendToQueue],
     * [playNextInQueue]); the next reader that forgets the guard would have
     * silently played a shuffled queue while the toggle said sequential. Falling
     * through to [QueueOrder.shuffleStateFor] produces the same empty state the
     * non-smart path already produces, so this changes nothing observable.
     *
     * When [smartShuffle] is on and a [smartShuffleScorer] is wired, the tail
     * (everything after the current track) is bucketed by similarity score:
     * high-similarity tracks play first, then medium, then low. Within each
     * bucket the order is still random, so the result is "guided" rather than
     * deterministic.
     */
    private fun rebuildShuffleOrder(index: Int) {
        if (!smartShuffle || !_isShuffle.value || _queue.value.size <= 2) {
            shuffleOrder = QueueOrder.shuffleStateFor(
                size = _queue.value.size,
                cursor = index,
                shuffleOn = _isShuffle.value,
                random = Random
            )
            shufflePos = 0
            return
        }

        val scorer = smartShuffleScorer
        if (scorer == null) {
            shuffleOrder = QueueOrder.shuffleStateFor(
                size = _queue.value.size,
                cursor = index,
                shuffleOn = _isShuffle.value,
                random = Random
            )
            shufflePos = 0
            return
        }

        val tracks = _queue.value
        val current = tracks.getOrNull(index) ?: run {
            shuffleOrder = QueueOrder.shuffleStateFor(
                size = tracks.size, cursor = index,
                shuffleOn = _isShuffle.value, random = Random
            )
            shufflePos = 0
            return
        }

        val scores = scorer(current, tracks)
        shuffleOrder = QueueOrder.smartShuffleOrder(
            size = tracks.size,
            cursor = index,
            scores = scores,
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
            // Capture the exact pause point; the ticker's 5 s throttle would
            // otherwise leave the saved offset up to 5 s behind.
            persistPosition(exoPlayer.currentPosition.coerceAtLeast(0))
        } else {
            if (exoPlayer.mediaItemCount == 0) {
                // Queue restored from disk: the player has no source attached
                // yet, so a bare play() would silently do nothing. Re-attach
                // the track the cursor is sitting on and start it — from the
                // offset saved before the app was closed, if there is one.
                val idx = _queueIndex.value
                if (idx >= 0) {
                    val resumeAt = restoredPositionMs
                    restoredPositionMs = 0
                    playAt(idx, resumeAt)
                    return
                }
            }
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    /**
     * Starts a sleep timer, or turns it off when [durationMs] is null.
     *
     * Pausing goes through `exoPlayer.pause()` rather than [togglePlay] so that a
     * timer expiring while playback is *already* paused cannot start it — the
     * toggle would see "not playing" and resume the track at bedtime.
     *
     * When the timer expires the volume fades out over [FADE_OUT_MS] rather than
     * cutting abruptly, so the pause at bedtime is gentle.
     */
    fun setSleepTimer(durationMs: Long?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        // If a previous fade was interrupted, restore full volume immediately.
        sleepFadeFactor = 1f
        applyVolume()

        if (durationMs == null) {
            _sleepTimerRemainingMs.value = null
            return
        }

        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(SLEEP_TICK_MS)
                remaining -= SLEEP_TICK_MS
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            // Gentle fade-out before pausing.
            val fadeSteps = (FADE_OUT_MS / SLEEP_TICK_MS).toInt()
            for (step in fadeSteps downTo 1) {
                if (!isActive) return@launch
                sleepFadeFactor = VolumeMix.sleepFadeFactor(step, fadeSteps)
                applyVolume()
                delay(SLEEP_TICK_MS)
            }
            exoPlayer.pause()
            sleepFadeFactor = 1f
            applyVolume()
            _sleepTimerRemainingMs.value = null
            sleepTimerJob = null
            appendDiagnostic("SLEEP_TIMER faded and paused playback")
        }
    }

    fun cancelSleepTimer() = setSleepTimer(null)

    fun release() {
        // Otherwise the countdown coroutine outlives the player and calls
        // pause() on a released ExoPlayer.
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        // An autoplay fetch may be mid-flight; it would call playAt on a
        // released player when it returns.
        autoplayJob?.cancel()
        autoplayJob = null
        // Last chance to record where we were before the process goes away.
        persistPosition(exoPlayer.currentPosition.coerceAtLeast(0))
        // Same for the listening total, which is batched — the ticker that
        // normally flushes it is about to be torn down with the player.
        statsManager.flush()
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

    /**
     * Writes just the playback offset. Kept separate from [persistQueue] because
     * this runs on a timer while the queue JSON does not — re-serialising the
     * whole queue every 5 seconds would be pure waste.
     */
    private fun persistPosition(ms: Long) {
        lastPersistedPositionMs = ms
        runCatching { prefs.edit().putLong(KEY_POSITION, ms).apply() }
    }

    /** Restores the last queue paused, so the app reopens where it left off. */
    private fun restoreQueue() {
        try {
            val json = prefs.getString(KEY_QUEUE, null) ?: return
            val type = listTypeOf(Track::class.java)
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

                // Resume offset, discarded when it lands in the outro: opening
                // the app to a song that ends two seconds later reads as a bug.
                val totalMs = track.duration * 1000L
                val saved = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
                restoredPositionMs = if (totalMs > 0 && saved > totalMs - RESUME_TAIL_GRACE_MS) {
                    0L
                } else {
                    saved
                }
                lastPersistedPositionMs = restoredPositionMs
                // Show the resume point in the UI straight away, even though the
                // player has nothing loaded yet.
                _positionMs.value = restoredPositionMs
            }
        } catch (e: Exception) {
            Log.w(TAG, "Queue restore failed: ${e.message}")
        }
    }

    /**
     * Applies [speedMode] to the player: rate and pitch move together so the
     * track slows down *and* drops in pitch (or speeds up and rises) — that
     * combination is what gives Slowed and Nightcore their character. A rate
     * change alone would time-stretch at constant pitch and sound like nothing.
     *
     * Slowed mode additionally attaches an `EnvironmentalReverb` to the audio
     * session, giving the wash the trend is named for. The effect must be
     * constructed with the session id (attachment happens at construction), and
     * the session can be re-created after a stream-format change — so the
     * reverb is rebuilt whenever the id differs from the one it was attached
     * to. This runs after play() on every track start for exactly that reason.
     *
     * Everything is guarded: audio-effect APIs throw and misbehave freely on
     * odd devices, and a cosmetic effect must never take playback down (see
     * the 1.16 hardening).
     *
     * `force` skips the already-applied check — used when the mode changes,
     * because the reverb must be attached or detached even when the rate ends
     * up the same.
     */
    private fun applySpeedMode(force: Boolean = false) {
        val mode = speedMode
        if (!force && appliedSpeedMode == mode) return
        appliedSpeedMode = mode
        runCatching {
            val params = exoPlayer.playbackParameters
            if (params.speed != mode.speed || params.pitch != mode.speed) {
                exoPlayer.playbackParameters =
                    PlaybackParameters(mode.speed, mode.speed)
            }
            val sessionId = exoPlayer.audioSessionId
            if (mode == SpeedMode.SLOWED_REVERB && sessionId > 0) {
                var reverb = environmentalReverb
                if (reverb == null || reverbSessionId != sessionId) {
                    // Session changed (or first use): rebuild the effect against
                    // the current one. An effect constructed against a dead
                    // session id simply never fires.
                    runCatching { environmentalReverb?.release() }
                    reverb = EnvironmentalReverb(0, sessionId)
                    environmentalReverb = reverb
                    reverbSessionId = sessionId
                }
                reverb.enabled = false
                // Every Settings field is Short (Android's audio-effect API is
                // 16-bit throughout): ratios and levels in permille/millibels.
                reverb.properties = EnvironmentalReverb.Settings().apply {
                    decayHFRatio = 600 // 0.6 ratio
                    decayTime = 1800 // ms
                    density = 700 // ‰
                    diffusion = 800 // ‰
                    reflectionsDelay = 60 // ms
                    reflectionsLevel = -6000 // mB
                    reverbDelay = 40 // ms
                    reverbLevel = -1000 // mB — wet but not cavernous
                    roomHFLevel = -800 // mB
                    roomLevel = -600 // mB
                }
                reverb.enabled = true
            } else {
                environmentalReverb?.enabled = false
            }
        }.onFailure {
            Log.w(TAG, "applySpeedMode(${mode.name}) failed: ${it.message}")
            appendDiagnostic("SPEED_MODE_FAIL mode=${mode.name} ${it.javaClass.simpleName}")
        }
    }

    /**
     * Init-time apply from a stored preference: the parameters take effect
     * immediately; the reverb attaches on the first track start (see
     * [applySpeedMode] — it needs a live session id, which does not exist yet).
     */
    fun applyStoredSpeedMode() = applySpeedMode(force = true)

    /**
     * Public hook for a mode change (MainViewModel.setSpeedMode): resets the
     * applied marker so [applySpeedMode] re-attaches the reverb / rewrites the
     * parameters even when the new mode's rate matches what is playing.
     */
    fun onSpeedModeChanged() {
        appliedSpeedMode = null
        applySpeedMode()
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
