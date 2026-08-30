package com.aesthetic.spotify.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import coil.imageLoader
import coil.request.ImageRequest
import com.aesthetic.spotify.MainActivity
import com.aesthetic.spotify.R
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom media notification, used instead of media3's default provider: some
 * OEM ROMs suppress the default notification, so we render our own with album
 * art, play/pause, prev/next and the compat media style (seekbar on 13+).
 */
class PlaybackNotificationProvider(private val context: Context) : MediaNotification.Provider {

    companion object {
        private const val TAG = "PlaybackNotif"
        const val CHANNEL_ID = "elara_playback"
        const val NOTIFICATION_ID = 2001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        ensureChannel()
        // Never let a notification-building issue crash playback — fall back
        // to a bare notification if anything (style, token, icons) throws.
        val notification = try {
            build(mediaSession, actionFactory, bitmap = null)
        } catch (e: Exception) {
            Log.e(TAG, "createNotification failed, using fallback", e)
            fallbackNotification()
        }

        // Album art loads async — re-post the notification once it arrives.
        try {
            mediaSession.player.currentMediaItem?.mediaMetadata?.artworkUri?.let { uri ->
                scope.launch {
                    val bitmap = runCatching { loadBitmap(uri) }.getOrNull()
                    if (bitmap != null) {
                        runCatching {
                            onNotificationChangedCallback.onNotificationChanged(
                                build(mediaSession, actionFactory, bitmap)
                            )
                        }.onFailure { Log.e(TAG, "art re-post failed: ${it.message}") }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "artwork launch failed: ${e.message}")
        }
        return notification
    }

    private fun fallbackNotification(): MediaNotification =
        MediaNotification(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_playback)
                .setContentTitle("Elara")
                .setOnlyAlertOnce(true)
                .build()
        )

    private fun build(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
        bitmap: Bitmap?
    ): MediaNotification {
        val player = mediaSession.player
        val metadata: MediaMetadata? = player.currentMediaItem?.mediaMetadata
        val playing = player.isPlaying

        val builder = NotificationCompat.Builder(context, PlaybackNotificationProvider.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(metadata?.title ?: "Elara")
            .setContentText(metadata?.artist ?: "")
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stat_prev, "Previous",
                    actionFactory.createMediaActionPendingIntent(
                        mediaSession, Player.COMMAND_SEEK_TO_PREVIOUS.toLong()
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (playing) R.drawable.ic_stat_pause else R.drawable.ic_stat_play,
                    if (playing) "Pause" else "Play",
                    actionFactory.createMediaActionPendingIntent(
                        mediaSession, Player.COMMAND_PLAY_PAUSE.toLong()
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stat_next, "Next",
                    actionFactory.createMediaActionPendingIntent(
                        mediaSession, Player.COMMAND_SEEK_TO_NEXT.toLong()
                    )
                )
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        bitmap?.let { builder.setLargeIcon(it) }

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private suspend fun loadBitmap(uri: android.net.Uri): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .size(512)
            .build()
        (context.imageLoader.execute(request).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }.getOrNull()

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false
}
