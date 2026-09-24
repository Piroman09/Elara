package com.aesthetic.spotify.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Guards the notification fix.
 *
 * media3 builds the media notification's buttons from the legacy
 * `PlaybackStateCompat.actions`, which `PlayerWrapper.createPlaybackStateCompat`
 * derives as `intersect(controllerCommands, player.getAvailableCommands())`.
 * `Util.getAvailableCommands` only offers SEEK_TO_NEXT / SEEK_TO_PREVIOUS while
 * the player reports a neighbouring media item, and ELARA's player never has
 * one — so SystemUI hid the next/previous buttons outright.
 * [QueueAwarePlayer] is what puts them back; these are the properties that make
 * it work.
 */
class QueueAwarePlayerTest {

    private var nextCalls = 0
    private var previousCalls = 0

    /**
     * Stands in for the shared ExoPlayer holding a single MediaItem: no
     * neighbours, so its own command set stops at play/pause.
     *
     * `Player` is a ~100-method interface and media3 offers no JVM-friendly stub
     * (`BasePlayer` still demands a real `android.os.Looper`), so this is a
     * dynamic proxy implementing only what [QueueAwarePlayer] touches.
     */
    private fun playerWithOneItem(): Player {
        val baseCommands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_GET_METADATA)
            .build()

        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAvailableCommands" -> baseCommands
                "isCommandAvailable" -> baseCommands.contains(args!![0] as Int)
                "seekToNext", "seekToNextMediaItem" -> { nextCalls++; null }
                "seekToPrevious", "seekToPreviousMediaItem" -> { previousCalls++; null }
                "toString" -> "FakePlayer(one item)"
                else -> defaultValueFor(method.returnType)
            }
        } as Player
    }

    /** An unhandled method must still return a type the proxy can unbox. */
    private fun defaultValueFor(type: Class<*>): Any? = when (type) {
        java.lang.Void.TYPE -> null
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun queueAwarePlayer() = QueueAwarePlayer(
        player = playerWithOneItem(),
        onNext = { nextCalls++ },
        onPrevious = { previousCalls++ }
    )

    @Test
    fun `advertises next and previous even when the player has no neighbours`() {
        val commands = queueAwarePlayer().availableCommands
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
    }

    @Test
    fun `keeps whatever the underlying player already offered`() {
        val commands = queueAwarePlayer().availableCommands
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_GET_METADATA))
    }

    @Test
    fun `does not claim unrelated commands`() {
        val player = queueAwarePlayer()
        assertFalse(player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_SPEED_AND_PITCH))
    }

    @Test
    fun `reports the queue commands as available`() {
        val player = queueAwarePlayer()
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
    }

    @Test
    fun `routes every seek-to-next variant into the queue`() {
        val player = queueAwarePlayer()
        player.seekToNext()
        player.seekToNextMediaItem()
        assertEquals(2, nextCalls)
        assertEquals(0, previousCalls)
    }

    @Test
    fun `routes every seek-to-previous variant into the queue`() {
        val player = queueAwarePlayer()
        player.seekToPrevious()
        player.seekToPreviousMediaItem()
        assertEquals(0, nextCalls)
        assertEquals(2, previousCalls)
    }
}
