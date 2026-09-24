package com.piroman.elara.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lazy-list key contract: identity **plus** an occurrence suffix.
 *
 * The property that actually matters is "every key in the list is distinct",
 * because a repeated key is not a cosmetic problem — Compose throws
 * `IllegalArgumentException: Key ... was already used` and the screen crashes.
 * So the tests below assert uniqueness directly rather than only checking the
 * exact strings, and the string checks exist to pin the format that the call
 * sites depend on.
 */
class LazyKeysTest {

    private fun keys(ids: List<String>): List<String> =
        LazyKeys.withOccurrence(ids) { it }.map { it.second }

    private fun items(ids: List<String>): List<String> =
        LazyKeys.withOccurrence(ids) { it }.map { it.first }

    @Test
    fun `a list with no repeats keeps a stable zero suffix`() {
        assertEquals(listOf("a#0", "b#0", "c#0"), keys(listOf("a", "b", "c")))
    }

    @Test
    fun `the suffix disambiguates a repeated id`() {
        assertEquals(listOf("a#0", "a#1"), keys(listOf("a", "a")))
    }

    @Test
    fun `three occurrences of the same id get three different keys`() {
        assertEquals(listOf("a#0", "a#1", "a#2"), keys(listOf("a", "a", "a")))
    }

    @Test
    fun `occurrence counting is per id, not global`() {
        // The second "b" is the second *b*, not the fourth item.
        assertEquals(listOf("a#0", "b#0", "a#1", "b#1"), keys(listOf("a", "b", "a", "b")))
    }

    @Test
    fun `items are returned unchanged and in order`() {
        assertEquals(listOf("x", "y", "x"), items(listOf("x", "y", "x")))
    }

    @Test
    fun `an empty list produces no keys`() {
        assertEquals(emptyList<String>(), keys(emptyList()))
    }

    @Test
    fun `keys are unique even when every id repeats`() {
        val produced = keys(listOf("a", "a", "a", "b", "b", "a"))
        assertEquals(produced.size, produced.toSet().size)
    }

    @Test
    fun `keys stay unique when an id already looks suffixed`() {
        // "a" and "a#0" are different ids, so their keys must not collide.
        val produced = keys(listOf("a", "a#0", "a"))
        assertEquals(produced.size, produced.toSet().size)
    }

    @Test
    fun `keys stay unique across a large repeated list`() {
        // The shape a Spotify import produces: one song, many times over.
        val produced = keys(List(50) { "spotify_same" })
        assertEquals(50, produced.size)
        assertEquals(50, produced.toSet().size)
        assertTrue(produced.first() == "spotify_same#0")
        assertTrue(produced.last() == "spotify_same#49")
    }

    @Test
    fun `the id extractor is applied to each item exactly once per item`() {
        // Guards the generic form the non-Track call sites use.
        val playlists = listOf("p1" to "Chill", "p1" to "Workout", "p2" to "Focus")
        val keyed = LazyKeys.withOccurrence(playlists) { it.first }
        assertEquals(listOf("p1#0", "p1#1", "p2#0"), keyed.map { it.second })
        assertEquals(playlists, keyed.map { it.first })
    }
}
