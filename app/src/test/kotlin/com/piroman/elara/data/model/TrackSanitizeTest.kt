package com.piroman.elara.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Gson/Kotlin null-safety trap.
 *
 * Gson populates fields reflectively instead of through the constructor, so a
 * [Track] parsed from JSON that predates a field ends up holding `null` in a
 * property the compiler believes is non-null. That is exactly what crashed the
 * queue screen: `findSimilarTracks` called `genre.lowercase()` on a track
 * restored from a queue persisted before `genre` existed.
 */
class TrackSanitizeTest {

    private val gson = Gson()

    @Test
    fun `gson really does leave nulls in non-null fields`() {
        // Documents the underlying hazard — this is not hypothetical.
        val json = """
            {"id":"a","title":"T","artist":"A","album":"Al",
             "albumArt":"u","audioUrl":"s","duration":10}
        """.trimIndent()

        val raw = gson.fromJson(json, Track::class.java)

        assertNull("genre is absent from the JSON, so Gson leaves it null", raw.genre)
        assertNull(raw.tags)
    }

    @Test
    fun `sanitized fills every null a Gson parse can produce`() {
        val raw = gson.fromJson("""{"id":"a","duration":10}""", Track::class.java)

        val clean = raw.sanitized()

        assertEquals("a", clean.id)
        assertEquals(10, clean.duration)
        assertEquals("", clean.title)
        assertEquals("", clean.artist)
        assertEquals("", clean.album)
        assertEquals("", clean.albumArt)
        assertEquals("", clean.audioUrl)
        assertEquals("", clean.genre)
        assertTrue("tags must be an empty list, not null", clean.tags.isEmpty())
        assertEquals(MusicSource.JIOSAAVN, clean.source)
    }

    @Test
    fun `sanitized leaves an already-complete track untouched`() {
        val original = Track(
            id = "x",
            title = "Title",
            artist = "Artist",
            album = "Album",
            albumArt = "art",
            audioUrl = "url",
            duration = 123,
            genre = "rock",
            tags = listOf("a")
        )

        assertEquals(original, original.sanitized())
    }
}
