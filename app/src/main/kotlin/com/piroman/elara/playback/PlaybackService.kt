package com.piroman.elara.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult

/**
 * Holds the MediaSession around the shared PlaybackManager player, so system
 * media controls (notification, lock screen, Bluetooth) drive app playback.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    /**
     * Held in a field so the wrapper that advertises next/previous to the
     * system outlives [onCreate]. The session is built around this rather than
     * the raw ExoPlayer — see [QueueAwarePlayer] for why a single-MediaItem
     * player otherwise hides the notification's next/previous buttons.
     */
    private var sessionPlayer: QueueAwarePlayer? = null

    /**
     * media3-native controllers (the app's own MediaController, and the
     * PendingIntents the notification builds via ActionFactory) request
     * next/previous as COMMAND_SEEK_TO_NEXT / COMMAND_SEEK_TO_PREVIOUS, which
     * media3 maps onto Player.seekToNextMediaItem() / seekToPreviousMediaItem().
     * Those are no-ops against a one-item playlist, so route them to the real
     * cursor queue. The legacy path (SystemUI, lock screen, Bluetooth) does not
     * come through here at all — it calls seekToNext()/seekToPrevious()
     * directly, which [QueueAwarePlayer] handles.
     *
     * `onPlayerCommandRequest` is deprecated in media3 1.4.x (a replacement is
     * planned) but is still the only pre-command hook, and
     * `MediaSessionImpl.onPlayerCommandRequestOnHandler` does invoke it.
     */
    private val sessionCallback = object : MediaSession.Callback {
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            val manager = PlaybackManager.getInstance(this@PlaybackService)
            return when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    manager.playNext()
                    SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    manager.playPrevious()
                    SessionResult.RESULT_SUCCESS
                }
                else -> super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }
    }

    // MediaSession.Builder (and MediaNotification.Provider, below) are media3's
    // @UnstableApi surface, acknowledged here rather than class-wide because
    // nothing else on this service touches it.
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val manager = PlaybackManager.getInstance(this)
        val player = QueueAwarePlayer(
            player = manager.exoPlayer,
            onNext = manager::playNext,
            onPrevious = manager::playPrevious
        )
        sessionPlayer = player
        val provider = PlaybackNotificationProvider(this)
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()
        setMediaNotificationProvider(provider)
        provider.ensureChannel()
        // NOTE: no eager startForeground() here. The service may be created by
        // a bound-only MediaController; foregrounding is only legal via
        // media3's own flow once playback starts (it pairs
        // startForegroundService + startForeground correctly for the ROM).
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        sessionPlayer = null
        super.onDestroy()
    }
}
