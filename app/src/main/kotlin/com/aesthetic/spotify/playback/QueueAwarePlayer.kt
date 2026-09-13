package com.aesthetic.spotify.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

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
class QueueAwarePlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) : ForwardingPlayer(player) {

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
}
