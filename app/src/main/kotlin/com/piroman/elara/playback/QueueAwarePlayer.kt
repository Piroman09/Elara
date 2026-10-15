package com.piroman.elara.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Session-facing view of the shared ExoPlayer that presents the cursor queue's
 * neighbours as if they were real playlist entries.
 *
 * ELARA deliberately keeps a *single* MediaItem loaded at a time: the queue is a
 * cursor in [PlaybackManager] and every track's stream URL is resolved lazily at
 * play time, so `hasNextMediaItem()` / `hasPreviousMediaItem()` are always false.
 * media3 derives what a media notification is allowed to show from the player's
 * available commands, not from the notification's own action list:
 *
 *   Util.getAvailableCommands()                    (media3-common)
 *     -> COMMAND_SEEK_TO_NEXT only when player.hasNextMediaItem()
 *   PlayerWrapper.createPlaybackStateCompat()
 *     -> actions = intersect(controllerCommands, player.getAvailableCommands())
 *
 * With no SEEK_TO_NEXT the legacy PlaybackStateCompat never carries
 * ACTION_SKIP_TO_NEXT, and SystemUI — which builds the shade/lock-screen media
 * buttons from exactly those actions — hides the next/previous buttons outright.
 * The same commands also gate dispatch: `ConnectedControllersManager
 * .isPlayerCommandAvailable` checks the player's commands, so even a forced
 * button would be rejected.
 *
 * Advertising them here fixes both halves: the buttons reappear, and the seek
 * calls land on [onNext] / [onPrevious] — a bare `seekToNextMediaItem()` is a
 * no-op against a one-item playlist.
 *
 * The commands are advertised unconditionally rather than only while a neighbour
 * exists, on purpose: media3 snapshots a controller's command set when it
 * connects and refreshes it only on EVENT_AVAILABLE_COMMANDS_CHANGED, which a
 * synthetic addition never raises. Advertising per-track would leave the buttons
 * permanently hidden for any session that happened to connect on the last track
 * of the queue.
 */
// ForwardingPlayer, and the Player members overridden below, are media3's
// @UnstableApi surface: acknowledged here once rather than at every override,
// the same way PlaybackManager does it. The opt-in is the point — it records
// that this class is deliberately built on API media3 may change between
// versions, so a media3 upgrade has to re-check it.
@OptIn(UnstableApi::class)
class QueueAwarePlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) : ForwardingPlayer(player) {

    /**
     * Keeps the service in the *foreground* across ELARA's track transitions.
     *
     * media3 decides whether the playback service may hold a foreground status
     * — the thing that stops Android (and every aggressive OEM task cleaner)
     * from reclaiming a backgrounded music app — from the session player's
     * reported state. In media3 1.4.1 `MediaNotificationManager`:
     *
     *   shouldRunInForeground(session, isPaused) =
     *     controller != null
     *       && (playWhenReady || isPaused)
     *       && playbackState in { STATE_BUFFERING, STATE_READY }   // 2 or 3
     *
     * and `MediaSessionService.isPlaybackOngoing()` is literally that flag.
     * `STATE_IDLE` and `STATE_ENDED` are **not** in that set, so the moment the
     * player reaches either one media3 calls `stopForeground(DETACH)`: the
     * notification stays, the foreground status does not. That is the design
     * working as intended for a normal playlist player, which never leaves
     * READY between tracks.
     *
     * ELARA does, routinely, and by design: the queue is a cursor and every
     * track's stream URL is resolved lazily at play time, so between tracks the
     * player sits at ENDED (the previous item finished) and then IDLE until the
     * resolver returns. That gap is a second or two of network latency on every
     * single track change — and it is exactly when the app is backgrounded and
     * most likely to be killed. Playback then never resumes, which the user
     * reports as "the song suddenly stops" or "it stops when I open another
     * app".
     *
     * Reporting a playback *session* that is still running is the honest
     * description of what is happening: `playWhenReady` is true, so the user has
     * asked for sound and we are fetching the next source. Only when
     * `playWhenReady` is false — a pause, the end of the queue with autoplay
     * exhausted, the sleep timer — is the real state passed through, so a paused
     * app correctly gives up its foreground status and its notification becomes
     * dismissible, exactly as media3 intends.
     *
     * Only media3 sees this: the wrapper is the *session-facing* view, and both
     * the UI and [PlaybackManager] read the shared ExoPlayer directly. See
     * [sessionPlaybackState] for the rule itself.
     */
    override fun getPlaybackState(): Int = sessionPlaybackState(
        realState = super.getPlaybackState(),
        playWhenReady = super.getPlayWhenReady()
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true

        else -> super.isCommandAvailable(command)
    }

    // The notification, lock screen and Bluetooth controls all arrive through
    // the legacy session, which calls these directly rather than requesting a
    // command first.
    override fun seekToNext() = onNext()

    override fun seekToNextMediaItem() = onNext()

    override fun seekToPrevious() = onPrevious()

    override fun seekToPreviousMediaItem() = onPrevious()

    companion object {
        /**
         * The playback state the media session should see.
         *
         * Pure so the rule is testable without a player: while the user wants
         * sound ([playWhenReady]) a gap between tracks is *buffering the next
         * one*, not "playback over" — see [getPlaybackState] for why media3
         * depends on that distinction. Anything else is reported unchanged.
         */
        internal fun sessionPlaybackState(realState: Int, playWhenReady: Boolean): Int =
            if (playWhenReady && (realState == Player.STATE_IDLE || realState == Player.STATE_ENDED)) {
                Player.STATE_BUFFERING
            } else {
                realState
            }
    }
}
