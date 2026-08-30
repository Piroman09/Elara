package com.aesthetic.spotify.playback

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aesthetic.spotify.MainActivity
import com.aesthetic.spotify.R

/**
 * Holds the MediaSession around the shared PlaybackManager player, so system
 * media controls (notification, lock screen, Bluetooth) drive app playback.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackManager.getInstance(this).exoPlayer
        val provider = PlaybackNotificationProvider(this)
        mediaSession = MediaSession.Builder(this, player).build()
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
        super.onDestroy()
    }
}
