package com.piroman.elara.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SafeGson] is the app-wide guard against the Gson/Kotlin null-safety trap: it
 * normalises every [Track] it parses, at any depth, so no call site has to
 * remember to call [sanitized] on the way out.
 */
class SafeGsonTest {

    private val gson = SafeGson.instance

    @Test
    fun `a list of tracks comes back with no nulls`() {
        val json = """[{"id":"a","duration":1},{"id":"b","duration":2}]"""
        val type = listTypeOf(Track::class.java)

        val tracks: List<Track> = gson.fromJson(json, type)

        assertEquals(2, tracks.size)
        tracks.forEach { track ->
            assertEquals("", track.title)
            assertEquals("", track.artist)
            assertEquals("", track.album)
            assertEquals("", track.albumArt)
            assertEquals("", track.audioUrl)
            assertEquals("", track.genre)
            assertTrue(track.tags.isEmpty())
        }
    }

    @Test
    fun `tracks nested inside a playlist are normalised too`() {
        val json = """
            {"id":"p","name":"P","description":"d","coverArt":"c",
             "tracks":[{"id":"a","duration":1}]}
        """.trimIndent()

        val playlist = gson.fromJson(json, Playlist::class.java)

        assertEquals(1, playlist.tracks.size)
        assertEquals("", playlist.tracks[0].genre)
        assertEquals("", playlist.tracks[0].artist)
    }

    @Test
    fun `serialising and re-reading a complete track is lossless`() {
        val original = Track(
            id = "x",
            title = "Title",
            artist = "Artist",
            album = "Album",
            albumArt = "art",
            audioUrl = "url",
            duration = 123,
            genre = "rock",
            energy = 7,
            tags = listOf("a", "b")
        )

        val roundTripped = gson.fromJson(gson.toJson(original), Track::class.java)

        assertEquals(original, roundTripped)
    }
}
