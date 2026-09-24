package com.piroman.elara.playback

import kotlin.random.Random

/**
 * Pure index arithmetic for the cursor queue.
 *
 * [PlaybackManager] models the queue as an ordered list plus a cursor
 * ([PlaybackManager.queueIndex]) pointing at the playing entry, and keeps a
 * separate shuffle permutation of queue indices. Every mutation therefore has
 * to remap *two* things — the cursor and the permutation — and getting either
 * wrong silently plays the wrong song.
 *
 * This logic deliberately holds no Android or ExoPlayer types so it can be
 * exercised directly by JVM unit tests (see `QueueOrderTest`).
 */
internal object QueueOrder {

    /**
     * Where the cursor ends up after the entry at [from] is moved to [to].
     *
     * - the playing entry itself moves → cursor follows it
     * - an earlier entry moves to after the cursor → everything shifts back one
     * - a later entry moves to before the cursor → everything shifts forward one
     */
    fun cursorAfterMove(current: Int, from: Int, to: Int): Int = when {
        current < 0 -> current
        current == from -> to
        from < current && to >= current -> current - 1
        from > current && to <= current -> current + 1
        else -> current
    }

    /**
     * Where the cursor ends up after the entry at [removed] is deleted.
     * Removing the playing entry leaves the cursor at -1: the track keeps
     * playing, it is simply no longer part of the queue.
     */
    fun cursorAfterRemove(current: Int, removed: Int): Int = when {
        current < 0 -> current
        removed < current -> current - 1
        removed == current -> -1
        else -> current
    }

    /**
     * Shuffle permutation after a move. Mirrors [cursorAfterMove] so the
     * sequence of *tracks* the permutation describes is unchanged — otherwise
     * reordering the queue mid-shuffle would jump to a different song.
     */
    fun orderAfterMove(order: List<Int>, from: Int, to: Int): List<Int> = order.map { i ->
        when {
            i == from -> to
            from < to && i > from && i <= to -> i - 1
            from > to && i >= to && i < from -> i + 1
            else -> i
        }
    }

    /** Shuffle permutation after a deletion; the removed entry simply drops out. */
    fun orderAfterRemove(order: List<Int>, removed: Int): List<Int> =
        order.filter { it != removed }.map { if (it > removed) it - 1 else it }

    /** Shift permutation entries at/after [insertAt] to make room for a new index. */
    fun orderAfterInsert(order: List<Int>, insertAt: Int): List<Int> =
        order.map { if (it >= insertAt) it + 1 else it }

    /**
     * Queue index the *previous* transport button should move to, or `null`
     * when the current track should simply restart from 0.
     *
     * Mirrors the shuffle handling of the forward direction. The asymmetry this
     * exists to prevent: at the **head of the shuffle lap** there is no earlier
     * entry to walk back to — the permutation records the current lap only, not
     * a history of laps — so falling back to `cursor - 1` would jump to a
     * queue-order neighbour that the shuffle never played. Restarting the
     * current track is the only honest answer, and it matches what a
     * non-shuffled queue already does at index 0.
     *
     * A stale permutation (position outside [shuffleOrder]) is treated the same
     * way rather than trusted.
     */
    fun previousTarget(
        queueSize: Int,
        cursor: Int,
        shuffleOn: Boolean,
        shuffleOrder: List<Int>,
        shufflePos: Int
    ): Int? {
        if (shuffleOn && shuffleOrder.isNotEmpty()) {
            if (shufflePos in 1..shuffleOrder.lastIndex) {
                val candidate = shuffleOrder[shufflePos - 1]
                if (candidate in 0 until queueSize) return candidate
            }
            return null
        }
        return if (cursor - 1 >= 0) cursor - 1 else null
    }

    /**
     * Where a fresh shuffle lap should start.
     *
     * A lap is built by [shuffleOrder], which pins the just-played entry at
     * position 0 so it is never immediately repeated — so position 1 is where
     * the lap actually begins. A one-entry lap has no position 1, so it starts
     * at 0: that single entry *is* the lap.
     *
     * Without the size check, repeat-all on a one-track queue computed no
     * target at all and playback stopped — the one thing repeat-all promises
     * not to do.
     */
    fun nextLapPosition(order: List<Int>): Int = if (order.size > 1) 1 else 0

    /**
     * Where a newly appended track should be slotted into the shuffle
     * permutation. It must land in the *unplayed* tail — at or after the next
     * position — so adding a song mid-shuffle neither jumps the queue nor
     * leaves the new track stranded behind the cursor.
     *
     * Returns a value in `shufflePos + 1 .. orderSize` (inclusive), i.e. a valid
     * insertion index for a list of [orderSize] entries.
     */
    fun shuffleTailInsertPosition(orderSize: Int, shufflePos: Int, random: Random): Int {
        val minPos = (shufflePos + 1).coerceAtMost(orderSize)
        val span = (orderSize - minPos).coerceAtLeast(0)
        return minPos + if (span > 0) random.nextInt(span + 1) else 0
    }

    /**
     * Fisher-Yates permutation of `0 until size` with [startIndex] pinned first,
     * so toggling shuffle on never interrupts the track already playing.
     * [random] is injected so callers (and tests) can make it deterministic.
     */
    fun shuffleOrder(size: Int, startIndex: Int, random: Random): List<Int> {
        val rest = (0 until size).filter { it != startIndex }.toMutableList()
        for (i in rest.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = rest[i]
            rest[i] = rest[j]
            rest[j] = tmp
        }
        return if (startIndex in 0 until size) listOf(startIndex) + rest else rest
    }

    /**
     * The shuffle permutation that should exist after a queue mutation.
     *
     * Deliberately all-or-nothing: whenever shuffle is on and there is a cursor
     * inside a non-empty queue, the result is a *full* permutation; otherwise it
     * is empty. Callers must assign this rather than blanking the permutation by
     * hand — clearing it while shuffle stays enabled is what made the shuffle
     * toggle lie: playback went sequential, and `addToQueue` stopped slotting
     * tracks in because it guards on the permutation being non-empty.
     */
    fun shuffleStateFor(
        size: Int,
        cursor: Int,
        shuffleOn: Boolean,
        random: Random
    ): List<Int> =
        if (shuffleOn && cursor >= 0 && size > 0) {
            shuffleOrder(size, cursor, random)
        } else {
            emptyList()
        }

    /**
     * Smart-shuffle permutation. The entry at [cursor] is pinned first; the
     * remaining indices are bucketed by similarity score, shuffled within each
     * bucket, then concatenated as high → medium → low.
     *
     * @param scores map of queue index to similarity score (higher = more similar).
     *   Missing entries are treated as 0.
     * @param highThreshold minimum score for the high-similarity bucket
     * @param mediumThreshold minimum score for the medium-similarity bucket
     *   (scores below this land in the low bucket)
     */
    fun smartShuffleOrder(
        size: Int,
        cursor: Int,
        scores: Map<Int, Int>,
        random: Random,
        highThreshold: Int = 30,
        mediumThreshold: Int = 15
    ): List<Int> {
        require(cursor in 0 until size) { "cursor $cursor out of bounds for size $size" }
        val tail = (0 until size).filter { it != cursor }
        val high = tail.filter { (scores[it] ?: 0) >= highThreshold }.shuffled(random)
        val medium = tail.filter {
            val s = scores[it] ?: 0
            s >= mediumThreshold && s < highThreshold
        }.shuffled(random)
        val low = tail.filter { (scores[it] ?: 0) < mediumThreshold }.shuffled(random)
        return listOf(cursor) + high + medium + low
    }
}
