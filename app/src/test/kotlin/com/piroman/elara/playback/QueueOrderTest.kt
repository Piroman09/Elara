package com.aesthetic.spotify.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for the cursor-queue index arithmetic.
 *
 * These run on the JVM (no emulator, no ExoPlayer) because [QueueOrder] is
 * deliberately free of Android types.
 */
class QueueOrderTest {

    // ── shuffleOrder ──────────────────────────────────────────────────────

    @Test
    fun `shuffleOrder is a permutation with the playing entry pinned first`() {
        val random = Random(1)
        for (size in 1..40) {
            val start = random.nextInt(size)
            val order = QueueOrder.shuffleOrder(size, start, random)
            assertEquals("size", size, order.size)
            assertEquals("permutation", (0 until size).toList(), order.sorted())
            assertEquals("pinned first", start, order.first())
        }
    }

    @Test
    fun `shuffleOrder varies between draws instead of repeating one order`() {
        val random = Random(7)
        val orders = (1..300).map { QueueOrder.shuffleOrder(6, 0, random) }.toSet()
        assertTrue("expected many distinct orderings, got ${orders.size}", orders.size > 20)
    }

    @Test
    fun `shuffleOrder with no valid start index is still a permutation`() {
        val order = QueueOrder.shuffleOrder(4, -1, Random(3))
        assertEquals((0 until 4).toList(), order.sorted())
    }

    // ── cursorAfterMove ───────────────────────────────────────────────────

    @Test
    fun `cursor follows the playing entry when it is the one moved`() {
        assertEquals(5, QueueOrder.cursorAfterMove(current = 2, from = 2, to = 5))
    }

    @Test
    fun `moving an earlier entry past the cursor shifts the cursor back`() {
        // queue [a b c d e] playing index 3; move index 1 to index 4
        assertEquals(2, QueueOrder.cursorAfterMove(current = 3, from = 1, to = 4))
    }

    @Test
    fun `moving a later entry before the cursor shifts the cursor forward`() {
        // playing index 1; move index 4 to index 0
        assertEquals(2, QueueOrder.cursorAfterMove(current = 1, from = 4, to = 0))
    }

    @Test
    fun `moving entries entirely on one side leaves the cursor alone`() {
        assertEquals(3, QueueOrder.cursorAfterMove(current = 3, from = 4, to = 6))
        assertEquals(3, QueueOrder.cursorAfterMove(current = 3, from = 0, to = 1))
    }

    @Test
    fun `cursor stays detached when nothing is playing`() {
        assertEquals(-1, QueueOrder.cursorAfterMove(current = -1, from = 0, to = 2))
    }

    // ── cursorAfterRemove ─────────────────────────────────────────────────

    @Test
    fun `removing an earlier entry shifts the cursor back`() {
        assertEquals(2, QueueOrder.cursorAfterRemove(current = 3, removed = 1))
    }

    @Test
    fun `removing a later entry leaves the cursor alone`() {
        assertEquals(3, QueueOrder.cursorAfterRemove(current = 3, removed = 5))
    }

    @Test
    fun `removing the playing entry detaches the cursor`() {
        assertEquals(-1, QueueOrder.cursorAfterRemove(current = 3, removed = 3))
    }

    // ── orderAfterInsert ──────────────────────────────────────────────────

    @Test
    fun `orderAfterInsert makes room and keeps every index valid`() {
        val shifted = QueueOrder.orderAfterInsert(listOf(0, 3, 1, 2), insertAt = 2)
        assertEquals(listOf(0, 4, 1, 3), shifted)
        // Slotting the new index 2 in right after the cursor completes it again.
        val withNew = shifted.toMutableList().apply { add(1, 2) }
        assertEquals((0..4).toList(), withNew.sorted())
    }

    // ── shuffleTailInsertPosition ─────────────────────────────────────────

    @Test
    fun `a track added mid-shuffle never lands before the next-up position`() {
        val random = Random(11)
        for (size in 1..12) {
            for (pos in 0 until size) {
                repeat(20) {
                    val at = QueueOrder.shuffleTailInsertPosition(size, pos, random)
                    assertTrue("size=$size pos=$pos -> $at jumped the queue", at >= pos + 1)
                    assertTrue("size=$size pos=$pos -> $at is not a valid insert index", at <= size)
                }
            }
        }
    }

    @Test
    fun `a track added while sitting on the last shuffle slot is appended`() {
        assertEquals(6, QueueOrder.shuffleTailInsertPosition(6, shufflePos = 5, random = Random(1)))
        // A cursor past the end (possible after removals) must still clamp.
        assertEquals(6, QueueOrder.shuffleTailInsertPosition(6, shufflePos = 9, random = Random(1)))
    }

    @Test
    fun `appending mid-shuffle spreads across the tail instead of always landing last`() {
        val random = Random(5)
        val positions = (1..400)
            .map { QueueOrder.shuffleTailInsertPosition(8, shufflePos = 2, random) }
            .toSet()
        assertTrue("expected several tail positions, got $positions", positions.size > 2)
        assertTrue("all positions must stay in the unplayed tail", positions.all { it in 3..8 })
    }

    @Test
    fun `inserting the appended track keeps the permutation complete`() {
        val random = Random(13)
        val order = QueueOrder.shuffleOrder(6, 0, random)
        val at = QueueOrder.shuffleTailInsertPosition(order.size, shufflePos = 1, random)
        val withNew = order.toMutableList().apply { add(at, 6) }
        assertEquals(7, withNew.size)
        assertEquals((0..6).toList(), withNew.sorted())
    }

    // ── shuffleStateFor ───────────────────────────────────────────────────

    @Test
    fun `shuffle state is all-or-nothing, never a partial permutation`() {
        val random = Random(21)
        for (size in 0..6) {
            for (cursor in -1 until size) {
                for (on in listOf(true, false)) {
                    val state = QueueOrder.shuffleStateFor(size, cursor, on, random)
                    val active = on && cursor >= 0 && size > 0
                    if (active) {
                        assertEquals("size=$size cursor=$cursor on=$on", size, state.size)
                        assertEquals((0 until size).toList(), state.sorted())
                    } else {
                        assertTrue(
                            "size=$size cursor=$cursor on=$on should stay empty",
                            state.isEmpty()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `clearing the queue with shuffle on keeps a usable permutation`() {
        // Regression: clearQueue used to blank the permutation while leaving
        // shuffle enabled, so playback silently went sequential afterwards.
        val afterClear = QueueOrder.shuffleStateFor(
            size = 1, cursor = 0, shuffleOn = true, random = Random(1)
        )
        assertEquals(listOf(0), afterClear)
    }

    // ── the invariant that actually matters ───────────────────────────────

    /**
     * The dangerous failure mode of this code is index juggling that looks
     * plausible but silently slides the cursor onto a *different* track, so
     * reordering or deleting mid-shuffle starts playing the wrong song.
     *
     * So rather than assert the arithmetic case-by-case, drive long random
     * sequences of moves and removals and assert the two invariants:
     *
     *  1. the cursor keeps pointing at the same physical track, and
     *  2. the shuffle permutation keeps describing the same sequence of tracks.
     */
    @Test
    fun `random move and remove sequences never retarget the cursor or shuffle order`() {
        val random = Random(20260911)

        repeat(300) { round ->
            var queue = (0 until 7).toList()          // distinct stand-in track ids
            var cursor = random.nextInt(queue.size)
            var order = QueueOrder.shuffleOrder(queue.size, cursor, random)

            var step = 0
            while (step < 30 && queue.size >= 2 && cursor >= 0) {
                step++

                if (random.nextInt(4) == 0) {
                    // ── remove ──
                    val removed = random.nextInt(queue.size)
                    val playing = queue[cursor]
                    val removedTrack = queue[removed]
                    val orderTracksBefore = order.map { queue[it] }.filter { it != removedTrack }

                    queue = queue.toMutableList().apply { removeAt(removed) }
                    cursor = QueueOrder.cursorAfterRemove(cursor, removed)
                    order = QueueOrder.orderAfterRemove(order, removed)

                    if (removedTrack == playing) {
                        // The real code clears shuffle state here; end this round.
                        assertEquals("cursor must detach when its track is removed", -1, cursor)
                        continue
                    }
                    assertEquals(
                        "round $round step $step: cursor left the playing track after removing $removed",
                        playing, queue[cursor]
                    )
                    assertEquals(
                        "round $round step $step: shuffle order changed after removing $removed",
                        orderTracksBefore, order.map { queue[it] }
                    )
                } else {
                    // ── move ──
                    val from = random.nextInt(queue.size)
                    val to = random.nextInt(queue.size)
                    if (from == to) continue

                    val playing = queue[cursor]
                    val orderTracksBefore = order.map { queue[it] }

                    queue = queue.toMutableList().apply { add(to, removeAt(from)) }
                    cursor = QueueOrder.cursorAfterMove(cursor, from, to)
                    order = QueueOrder.orderAfterMove(order, from, to)

                    assertEquals(
                        "round $round step $step: cursor left the playing track after moving $from->$to",
                        playing, queue[cursor]
                    )
                    assertEquals(
                        "round $round step $step: shuffle order changed after moving $from->$to",
                        orderTracksBefore, order.map { queue[it] }
                    )
                }

                assertEquals(
                    "round $round step $step: shuffle order is no longer a permutation",
                    (0 until queue.size).toList(), order.sorted()
                )
            }
        }
    }
}
