package com.piroman.elara.data.playlist

import com.piroman.elara.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for playlist display ordering and filtering.
 *
 * [PlaylistOrdering] is deliberately free of Android types so this runs on the
 * JVM — there is no emulator on this machine, so anything that can be a plain
 * unit test has to be one.
 */
class PlaylistOrderingTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "",
        album: String = "",
        duration: Int = 0
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArt = "",
        audioUrl = "",
        duration = duration
    )

    // ── apply ─────────────────────────────────────────────────────────────

    @Test
    fun `custom order returns the stored order untouched`() {
        val tracks = listOf(
            track("c", "Zebra"),
            track("a", "Apple"),
            track("b", "Mango")
        )
        assertEquals(tracks, PlaylistOrdering.apply(tracks, PlaylistSort.CUSTOM))
    }

    @Test
    fun `title sort is case insensitive`() {
        val tracks = listOf(
            track("1", "banana"),
            track("2", "Apple"),
            track("3", "cherry")
        )
        assertEquals(
            listOf("Apple", "banana", "cherry"),
            PlaylistOrdering.apply(tracks, PlaylistSort.TITLE).map { it.title }
        )
    }

    @Test
    fun `title sort breaks ties on the stored order, not randomly`() {
        // Same title, so the comparator sees them as equal. A non-stable sort
        // would be free to reorder them on every recomposition.
        val tracks = listOf(
            track("first", "Same"),
            track("second", "Same"),
            track("third", "Same")
        )
        assertEquals(
            listOf("first", "second", "third"),
            PlaylistOrdering.apply(tracks, PlaylistSort.TITLE).map { it.id }
        )
    }

    @Test
    fun `artist sort falls back to title within one artist`() {
        val tracks = listOf(
            track("1", "Zither", artist = "Adele"),
            track("2", "Alpha", artist = "Adele"),
            track("3", "Beta", artist = "Beyonce")
        )
        assertEquals(
            listOf("2", "1", "3"),
            PlaylistOrdering.apply(tracks, PlaylistSort.ARTIST).map { it.id }
        )
    }

    @Test
    fun `duration sort puts the longest first`() {
        val tracks = listOf(
            track("short", "A", duration = 100),
            track("long", "B", duration = 400),
            track("mid", "C", duration = 250)
        )
        assertEquals(
            listOf("long", "mid", "short"),
            PlaylistOrdering.apply(tracks, PlaylistSort.DURATION).map { it.id }
        )
    }

    // ── filter ────────────────────────────────────────────────────────────

    @Test
    fun `blank query matches everything`() {
        val tracks = listOf(track("1", "A"), track("2", "B"))
        assertEquals(tracks, PlaylistOrdering.filter(tracks, ""))
        assertEquals(tracks, PlaylistOrdering.filter(tracks, "   "))
    }

    @Test
    fun `filter matches title, artist and album case insensitively`() {
        val tracks = listOf(
            track("1", "Midnight City", artist = "M83", album = "Hurry Up"),
            track("2", "Daylight", artist = "Joji", album = "Nectar"),
            track("3", "Olympus", artist = "Someone", album = "Other")
        )
        // title
        assertEquals(listOf("1"), PlaylistOrdering.filter(tracks, "midnight").map { it.id })
        // artist
        assertEquals(listOf("1"), PlaylistOrdering.filter(tracks, "m83").map { it.id })
        // album
        assertEquals(listOf("2"), PlaylistOrdering.filter(tracks, "nectar").map { it.id })
    }

    @Test
    fun `filter returns nothing when there is no match`() {
        val tracks = listOf(track("1", "Midnight City"))
        assertTrue(PlaylistOrdering.filter(tracks, "zzzz").isEmpty())
    }
}
