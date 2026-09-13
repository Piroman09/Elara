package com.aesthetic.spotify.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YouTube Music rows were the only source with an empty lyrics pane, and the
 * cause was metadata rather than the lyrics providers: search results carry the
 * *video's* packaging, which LRCLIB cannot match. Measured against the live
 * API before writing this, `Kesariya` by `Arijit Singh & Nikhita Gandhi` and
 * `Tum Hi Ho` by `T-Series` both returned **0 rows**; the normalised forms
 * returned 20 each.
 *
 * The fixtures below are verbatim from a real WEB_REMIX `search` response.
 */
class LyricQueryTest {

    // ── cleanTitle ───────────────────────────────────────────────────────

    @Test
    fun `strips the album attribution YouTube adds`() {
        assertEquals("Kesariya", LyricQuery.cleanTitle("""Kesariya (From "Brahmastra")"""))
    }

    @Test
    fun `strips stacked edit labels`() {
        assertEquals(
            "Kesariya",
            LyricQuery.cleanTitle("""Kesariya (From "Brahmastra") (Dance Mix)""")
        )
        assertEquals("Kesariya", LyricQuery.cleanTitle("Kesariya (Lofi Flip)"))
        assertEquals("Kesariya", LyricQuery.cleanTitle("Kesariya (Synthwave)"))
    }

    @Test
    fun `strips square-bracket labels`() {
        assertEquals("Tum Hi Ho", LyricQuery.cleanTitle("Tum Hi Ho (Official Video)"))
        assertEquals("Tum Hi Ho", LyricQuery.cleanTitle("Tum Hi Ho [Official Video]"))
    }

    @Test
    fun `cuts a pipe-separated upload title down to the song`() {
        assertEquals(
            "Kesariya Karaoke",
            LyricQuery.cleanTitle(
                "Kesariya Karaoke| Low Scale| Brahmastra| Arijit Singh| Pritam| Ranbir-Alia| #karaokewithvarun"
            )
        )
    }

    @Test
    fun `leaves an already-clean title untouched`() {
        // The no-regression guarantee: JioSaavn metadata must pass through
        // byte-identical or the fix would trade one source's lyrics for another's.
        assertEquals("Blinding Lights", LyricQuery.cleanTitle("Blinding Lights"))
        assertEquals("Kesariya Balma", LyricQuery.cleanTitle("Kesariya Balma"))
        assertEquals("Shape of You", LyricQuery.cleanTitle("Shape of You"))
    }

    @Test
    fun `does not mangle a title that is entirely a parenthetical`() {
        // Degenerate input must not produce a query of "" — an empty
        // track_name makes LRCLIB return its whole catalogue.
        assertTrue(LyricQuery.cleanTitle("(Official Video)").isEmpty())
    }

    // ── primaryArtist ────────────────────────────────────────────────────

    @Test
    fun `takes the first artist from a comma list`() {
        assertEquals(
            "Pritam",
            LyricQuery.primaryArtist("Pritam, Shashwat Singh, Antara Mitra & Arijit Singh")
        )
    }

    @Test
    fun `takes the first artist from an ampersand pair`() {
        assertEquals("Arijit Singh", LyricQuery.primaryArtist("Arijit Singh & Nikhita Gandhi"))
        assertEquals("VDJ Fly", LyricQuery.primaryArtist("VDJ Fly & Arijit Singh"))
    }

    @Test
    fun `strips YouTubes auto-generated topic channel suffix`() {
        assertEquals("Ed Sheeran", LyricQuery.primaryArtist("Ed Sheeran - Topic"))
    }

    @Test
    fun `leaves a band name with an unspaced slash intact`() {
        assertEquals("AC/DC", LyricQuery.primaryArtist("AC/DC"))
    }

    /**
     * A known limitation, locked in as a test because it is the reason
     * [LyricQuery.candidates] tries the raw artist *before* the split one.
     * `Simon & Garfunkel` is spaced, so the separator rule fires — but the raw
     * string is queried first, so the real artist is still used and this
     * fallback is never reached for that row.
     */
    @Test
    fun `splits a spaced ampersand band name but only as a fallback`() {
        assertEquals("Simon", LyricQuery.primaryArtist("Simon & Garfunkel"))
    }

    // ── candidates ───────────────────────────────────────────────────────

    @Test
    fun `clean metadata yields the original query first`() {
        val first = LyricQuery.candidates("Blinding Lights", "The Weeknd").first()
        assertEquals("Blinding Lights", first.title)
        assertEquals("The Weeknd", first.artist)
    }

    @Test
    fun `deduplicates when the raw artist is already the primary one`() {
        val candidates = LyricQuery.candidates("Blinding Lights", "The Weeknd")
        // raw == primary is dropped, leaving the raw form and the title-only form.
        assertEquals(2, candidates.size)
        assertEquals("The Weeknd", candidates[0].artist)
        assertTrue(candidates.last().artist.isEmpty())
    }

    @Test
    fun `falls from the joined credit string to the first artist`() {
        val candidates = LyricQuery.candidates("Kesariya", "Arijit Singh & Nikhita Gandhi")
        assertEquals(
            listOf("Arijit Singh & Nikhita Gandhi", "Arijit Singh", ""),
            candidates.map { it.artist }
        )
        // Every step keeps the cleaned title.
        assertTrue(candidates.all { it.title == "Kesariya" })
    }

    @Test
    fun `always ends with a title-only attempt`() {
        // The only step that can rescue a row whose artist is a TV channel.
        val candidates = LyricQuery.candidates("Tum Hi Ho (Official Video)", "T-Series")
        assertEquals("Tum Hi Ho", candidates.first().title)
        assertTrue(candidates.last().artist.isEmpty())
    }

    @Test
    fun `returns nothing for a title that cleans away to empty`() {
        assertTrue(LyricQuery.candidates("(Official Video)", "Someone").isEmpty())
    }

    // ── relevance ────────────────────────────────────────────────────────

    @Test
    fun `prefers the exact title and artist over anything else`() {
        val exact = LyricQuery.relevance("Kesariya", "Arijit Singh", "Kesariya", "Arijit Singh")
        val cover = LyricQuery.relevance("Kesariya", "Some Cover Band", "Kesariya", "Arijit Singh")
        val other = LyricQuery.relevance("Kesariya Balma", "Lata Mangeshkar", "Kesariya", "Arijit Singh")
        assertTrue("exact must outrank a cover", exact > cover)
        assertTrue("exact must outrank an unrelated song", exact > other)
    }

    @Test
    fun `ranks a matching artist above a non-matching one for a title-only query`() {
        val original = LyricQuery.relevance("Tum Hi Ho", "Arijit Singh, Mithoon", "Tum Hi Ho", "")
        val unrelated = LyricQuery.relevance("Tum Hi Ho", "Karaoke Kings", "Tum Hi Ho", "")
        assertEquals("title match scores the same either way", original, unrelated)

        // With an artist to compare against, the original must win.
        val withArtist = LyricQuery.relevance(
            "Tum Hi Ho", "Arijit Singh, Mithoon", "Tum Hi Ho", "Arijit Singh"
        )
        val wrongArtist = LyricQuery.relevance(
            "Tum Hi Ho", "Karaoke Kings", "Tum Hi Ho", "Arijit Singh"
        )
        assertTrue(withArtist > wrongArtist)
    }

    @Test
    fun `tolerates a punctuation-different artist credit`() {
        // LRCLIB stores "Arijit Singh, Mithoon"; the query says "Arijit Singh".
        val score = LyricQuery.relevance(
            "Tum Hi Ho", "Arijit Singh, Mithoon", "Tum Hi Ho", "Arijit Singh"
        )
        assertTrue("token overlap should score above zero", score > 0)
    }

    @Test
    fun `scores nothing when neither field matches`() {
        assertEquals(
            0,
            LyricQuery.relevance("Something Else", "Nobody", "Kesariya", "Arijit Singh")
        )
    }

    // ── real rows ────────────────────────────────────────────────────────

    /**
     * Every row from the captured search response, asserting the cascade opens
     * with the song's real name. This is the regression net for the actual bug.
     */
    @Test
    fun `cleans every captured YouTube Music search row`() {
        val rows = listOf(
            Triple("""Kesariya (From "Brahmastra")""", "Arijit Singh", "Kesariya"),
            Triple("""Kesariya (From "Brahmastra") (Dance Mix)""",
                "Pritam, Shashwat Singh, Antara Mitra & Arijit Singh", "Kesariya"),
            Triple("""Deva Deva (From "Brahmastra")""",
                "Pritam, Arijit Singh, Amitabh Bhattacharya & Jonita Gandhi", "Deva Deva"),
            Triple("Kesariya Balma", "Lata Mangeshkar", "Kesariya Balma"),
            Triple("Kesariya (Lofi Flip)",
                "VIBIE, Arijit Singh, Pritam & Bollywood Lofi", "Kesariya"),
            Triple("Kesariya (Synthwave)", "VDJ Fly & Arijit Singh", "Kesariya"),
            Triple("Kesariya", "Arijit Singh & Nikhita Gandhi", "Kesariya"),
            Triple("Tum Hi Ho (Official Video)", "T-Series", "Tum Hi Ho"),
            Triple("Shape of You", "Ed Sheeran - Topic", "Shape of You"),
        )

        rows.forEach { (rawTitle, rawArtist, expectedTitle) ->
            val candidates = LyricQuery.candidates(rawTitle, rawArtist)
            assertFalse("no candidates for $rawTitle", candidates.isEmpty())
            assertEquals("title for $rawTitle", expectedTitle, candidates.first().title)
            assertTrue("last step must be title-only for $rawTitle",
                candidates.last().artist.isEmpty())
        }
    }
}
