package com.piroman.elara.data.repository

import com.piroman.elara.data.model.MusicSource
import com.piroman.elara.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules that decide what a YouTube Music Home row is allowed to contain.
 *
 * Both were written in response to defects that shipped in 1.10 (build 11) and
 * that the user then reported on Home:
 *
 *  - **"Bollywood Dj Non Stop Remix" at 50:23** and **"Party Mashup" at 30:17**,
 *    rendered as songs in a shelf of cards. Tapping one starts a 50-minute DJ set.
 *  - **5 of 25 songs appeared in both YouTube rows**, which reads as a bug.
 *
 * Neither is an error anywhere in the app, which is exactly why they are tested
 * here rather than left to a reviewer to notice.
 */
class YouTubeRowPolicyTest {

    private fun track(id: String, durationSec: Int) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumArt = "",
        audioUrl = "",
        duration = durationSec,
        source = MusicSource.YOUTUBE_MUSIC
    )

    // ── The duration cap ──────────────────────────────────────────────────

    @Test
    fun `an ordinary song is presentable`() {
        assertTrue(YouTubeRowPolicy.isPresentable(track("a", 3 * 60 + 22)))
    }

    @Test
    fun `a long album track is still presentable`() {
        // 14 minutes — long, but a song. The cap is not a "typical length" filter.
        assertTrue(YouTubeRowPolicy.isPresentable(track("a", 14 * 60)))
    }

    @Test
    fun `a fifty minute DJ set is not a song`() {
        // The exact entry that shipped: "Bollywood Dj Non Stop Remix" (50:23).
        assertFalse(YouTubeRowPolicy.isPresentable(track("dj", 50 * 60 + 23)))
    }

    @Test
    fun `a thirty minute mashup is not a song`() {
        // The other one that shipped: "Party Mashup - DJ Praveen" (30:17).
        assertFalse(YouTubeRowPolicy.isPresentable(track("mash", 30 * 60 + 17)))
    }

    @Test
    fun `the boundary is inclusive`() {
        assertTrue(YouTubeRowPolicy.isPresentable(track("a", YouTubeRowPolicy.MAX_TRACK_SECONDS)))
        assertFalse(
            YouTubeRowPolicy.isPresentable(track("b", YouTubeRowPolicy.MAX_TRACK_SECONDS + 1))
        )
    }

    @Test
    fun `an unknown duration is kept, not dropped`() {
        // Deliberate. Dropping unknown durations would turn any change in
        // YouTube's response shape into a silently *empty* row, and an empty row
        // is indistinguishable from a broken one. Home's cards do not show a
        // duration, so keeping them costs nothing visible.
        assertTrue(YouTubeRowPolicy.isPresentable(track("unknown", 0)))
    }

    @Test
    fun `filtering keeps order and drops only the long entries`() {
        val tracks = listOf(
            track("a", 200),
            track("dj", 3600),
            track("b", 0),
            track("c", 300)
        )
        assertEquals(listOf("a", "b", "c"), YouTubeRowPolicy.presentable(tracks).map { it.id })
    }

    // ── De-duplication across rows ────────────────────────────────────────

    @Test
    fun `a song already in the first row is dropped from the second`() {
        // The measured case: "8 Parche", "Apna Bana Le", "Gehra Hua",
        // "Saiyaara" and "Kesariya" appeared in both rows.
        val row1 = listOf(track("8 Parche", 210), track("Kesariya", 269))
        val row2 = listOf(track("8 Parche", 210), track("Zaalima", 300))
        assertEquals(listOf("Zaalima"), YouTubeRowPolicy.withoutDuplicates(row1, row2).map { it.id })
    }

    @Test
    fun `de-duplication preserves the second row's order`() {
        val row1 = listOf(track("b", 200))
        val row2 = listOf(track("a", 200), track("b", 200), track("c", 200))
        assertEquals(
            listOf("a", "c"),
            YouTubeRowPolicy.withoutDuplicates(row1, row2).map { it.id }
        )
    }

    @Test
    fun `an empty first row removes nothing`() {
        val row2 = listOf(track("a", 200), track("b", 200))
        assertEquals(row2, YouTubeRowPolicy.withoutDuplicates(emptyList(), row2))
    }

    @Test
    fun `an empty second row stays empty`() {
        assertTrue(YouTubeRowPolicy.withoutDuplicates(listOf(track("a", 200)), emptyList()).isEmpty())
    }

    @Test
    fun `duplicate ids within the candidates are all removed`() {
        // A live feed can legitimately repeat an item; `search` de-duplicates
        // within one call, so this only has to not resurrect them.
        val row1 = listOf(track("x", 200))
        val row2 = listOf(track("x", 200), track("x", 200), track("y", 200))
        assertEquals(listOf("y"), YouTubeRowPolicy.withoutDuplicates(row1, row2).map { it.id })
    }
}
