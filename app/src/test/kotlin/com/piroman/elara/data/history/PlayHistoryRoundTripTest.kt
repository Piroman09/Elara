package com.piroman.elara.data.history

import com.piroman.elara.data.model.listTypeOf
import com.piroman.elara.data.model.MusicSource
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Recently played is empty after reopening the app" can only come from one
 * place: `PlayHistoryManager.load()` returning an empty list. That happens
 * either because nothing was written, or because the write did not survive the
 * round trip.
 *
 * `SafeGsonTest` covers a **single** [Track] going out and back. Production
 * never does that — `record()` calls `gson.toJson(updated)` on a `List<Track>`
 * (whose runtime type is a raw `ArrayList`, so Gson picks the element adapter
 * from the *runtime* class) and `load()` reads it back through an explicit
 * `TypeToken<List<Track>>`. Those are two different code paths in Gson, and
 * only the second one can silently return nothing.
 *
 * So these tests reproduce the **exact** pair of calls `PlayHistoryManager`
 * makes, on data shaped like real history, rather than trusting that the
 * single-track case generalises.
 */
class PlayHistoryRoundTripTest {

    private val gson = SafeGson.instance
    private val listType = listTypeOf(Track::class.java)

    /** A track with every field populated, including both non-default enums. */
    private fun fullTrack(i: Int) = Track(
        id = "id$i",
        videoId = "vid$i",
        title = "Title $i",
        artist = "Artist $i",
        artistId = "aid$i",
        album = "Album $i",
        albumArt = "https://art/$i.jpg",
        audioUrl = "https://audio/$i.m4a",
        duration = 200 + i,
        genre = "genre$i",
        energy = i,
        accentColor = "#ff00ff",
        isLiked = i % 2 == 0,
        source = if (i % 2 == 0) MusicSource.YOUTUBE_MUSIC else MusicSource.JIOSAAVN,
        tags = listOf("t$i", "u$i")
    )

    @Test
    fun `the exact record-then-load round trip preserves a full history list`() {
        val history = (1..20).map { fullTrack(it) }

        // record(): gson.toJson(List<Track>)
        val json = gson.toJson(history)
        // load(): gson.fromJson(json, TypeToken<List<Track>>)
        val restored: List<Track> = gson.fromJson(json, listType)

        assertEquals("history length must survive", history.size, restored.size)
        assertEquals(history, restored)
    }

    /**
     * The failure this guards against: if serialisation emitted anything Gson
     * cannot read back as `List<Track>`, `load()` swallows the exception and
     * returns `emptyList()` — which is exactly the reported symptom.
     */
    @Test
    fun `a single-entry history also survives`() {
        val history = listOf(fullTrack(1))

        val restored: List<Track> = gson.fromJson(gson.toJson(history), listType)

        assertEquals(history, restored)
    }

    /**
     * Tracks reach history straight from a network response, so the nullable
     * fields are genuinely absent on some of them. A `null` inside the list must
     * not poison the parse of its siblings.
     */
    @Test
    fun `tracks with absent nullable fields do not poison the list`() {
        val history = listOf(
            fullTrack(1),
            Track(
                id = "bare",
                title = "Bare",
                artist = "A",
                album = "Al",
                albumArt = "",
                audioUrl = "",
                duration = 0
            ),
            fullTrack(2)
        )

        val restored: List<Track> = gson.fromJson(gson.toJson(history), listType)

        assertEquals(3, restored.size)
        assertEquals(history, restored)
    }

    /**
     * `PlayStat` is the second key on disk and is written on every play too. Its
     * `track` is nullable precisely so a bad entry can be dropped rather than
     * crash — so a null inside the list must be tolerated by the parser.
     */
    @Test
    fun `a play-stat list round trips and tolerates a null track`() {
        val statsType = listTypeOf(PlayStat::class.java)
        val stats = listOf(PlayStat(fullTrack(1), 3), PlayStat(fullTrack(2), 1))

        val restored: List<PlayStat> = gson.fromJson(gson.toJson(stats), statsType)

        assertEquals(stats, restored)

        val withNull = gson.fromJson<List<PlayStat>>("""[{"count":2}]""", statsType)
        assertEquals(1, withNull.size)
        assertEquals(2, withNull[0].count)
        assertTrue("a stat with no track must be detectable", withNull[0].track == null)
    }
}
