package com.piroman.elara.data.download

import com.piroman.elara.data.model.listTypeOf
import com.piroman.elara.data.model.MusicSource
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download index is the third store that can silently empty itself: an index that
 * fails to parse makes `loadIndex()` return `emptyList()`, and the app forgets every
 * download while the audio files sit untouched on disk.
 *
 * `DownloadManager.Entry` is also the **last** persisted type carrying the
 * `Unsafe`-allocation hazard that wiped playlists: neither parameter has a default, so
 * Kotlin emits no synthetic no-arg constructor, so Gson allocates the object without
 * running a constructor — and an absent key becomes a **null inside a non-null
 * property**. `Entry.track` is read as `it.track.id` at ten call sites, so that null is
 * a crash rather than a blank row.
 *
 * The real load path is not reachable from a JVM test (`loadIndex` needs a `Context`),
 * so [repairIndex] was extracted to be pure and is tested directly — the same move that
 * made `Playlist.repaired()` testable.
 */
class DownloadIndexRepairTest {

    private val gson = SafeGson.instance
    private val listType = listTypeOf(DownloadManager.Entry::class.java)

    /** A track with every non-defaulted field populated. */
    private fun fullTrack(i: Int) = Track(
        id = "id$i",
        title = "Title $i",
        artist = "Artist $i",
        album = "Album $i",
        albumArt = "https://art/$i.jpg",
        audioUrl = "https://audio/$i.m4a",
        duration = 200 + i,
        source = MusicSource.YOUTUBE_MUSIC
    )

    /**
     * The exact pair of calls `saveIndex`/`loadIndex` make, through the same explicit
     * `TypeToken`. The repair must be a no-op on a well-formed index — if it ever drops
     * a legitimate entry, downloads start disappearing from the Library.
     */
    @Test
    fun `the exact save-then-load round trip preserves an index`() {
        val index = listOf(
            DownloadManager.Entry(fullTrack(1), "/data/one.m4a"),
            DownloadManager.Entry(fullTrack(2), "/data/two.m4a")
        )

        val restored: List<DownloadManager.Entry> = gson.fromJson(gson.toJson(index), listType)
        val repaired = repairIndex(restored)

        assertEquals("nothing legitimate may be dropped", index.size, repaired.size)
        assertEquals(index, repaired)
    }

    /**
     * **The hazard, demonstrated and then caught.**
     *
     * The first assertion is the whole point: Gson really does leave a `null` inside a
     * property declared non-null `Track`. Without it the rest of the test would be
     * asserting about a situation that cannot happen — which is exactly how a suite
     * ends up vacuous.
     */
    @Test
    fun `an entry whose track key is missing is dropped, not loaded as a null track`() {
        val missingTrack = """[{"path":"/data/orphan.m4a"}]"""

        val parsed: List<DownloadManager.Entry> = gson.fromJson(missingTrack, listType)
        val rawTrack: Track? = parsed[0].track
        assertNull("an absent key must become a null here, or this test proves nothing", rawTrack)

        val repaired = repairIndex(parsed)
        assertTrue(
            "an entry with no track can be neither listed nor removed, only crashed on",
            repaired.isEmpty()
        )
    }

    @Test
    fun `an entry with an explicit null track is dropped`() {
        val nullTrack = """[{"track":null,"path":"/data/orphan.m4a"}]"""

        val parsed: List<DownloadManager.Entry> = gson.fromJson(nullTrack, listType)

        assertTrue(repairIndex(parsed).isEmpty())
    }

    @Test
    fun `an entry with a blank path is dropped`() {
        val noPath = """[{"track":{"id":"t1","title":"T","artist":"A","album":"B",""" +
            """"albumArt":"c","audioUrl":"d","duration":1},"path":""}]"""

        val parsed: List<DownloadManager.Entry> = gson.fromJson(noPath, listType)

        assertTrue(
            "a download with no file location cannot be played back",
            repairIndex(parsed).isEmpty()
        )
    }

    /**
     * The third guard, and the one the mutation control caught as untested: a track that
     * survives the null checks but has no usable id. Every consumer matches downloads by
     * `track.id`, so such an entry can never be found again — it would sit in the list
     * forever and never be removable.
     */
    @Test
    fun `an entry whose track has no id is dropped`() {
        val noId = """[{"track":{"title":"T","artist":"A","album":"B","albumArt":"c",""" +
            """"audioUrl":"d","duration":1},"path":"/data/one.m4a"}]"""

        val parsed: List<DownloadManager.Entry> = gson.fromJson(noId, listType)
        val rawId: String? = parsed[0].track.id
        assertTrue("an absent id must normalise to blank, not stay null", rawId.isNullOrBlank())

        assertTrue(
            "an entry whose track has no id can never be matched to a download",
            repairIndex(parsed).isEmpty()
        )
    }

    /**
     * Pins *why* the repair's own `sanitized()` call cannot be observed by a test, so the
     * mutation control's survivor is explained rather than tolerated.
     *
     * `SafeGson` registers a `Track` deserializer, so a nested track is already normalised
     * by the parse itself — before [repaired] ever runs. That is why mutating [repaired]
     * to an identity function leaves the nested-track test green: the property it asserts
     * is real, but it is provided by `SafeGson`, not by the repair.
     */
    @Test
    fun `SafeGson already normalises the nested track before the repair runs`() {
        val bareTrack = """[{"track":{"id":"t1","duration":10},"path":"/data/one.m4a"}]"""

        val parsed: List<DownloadManager.Entry> = gson.fromJson(bareTrack, listType)

        assertNotNull("the parse itself must sanitise; see SafeGson", parsed[0].track.title)
        assertNotNull("tags are iterated; they must not be null", parsed[0].track.tags)
        assertNotNull("source is switched on; it must not be null", parsed[0].track.source)
    }

    /**
     * The nested-`Track` normalisation claim, at depth. `SafeGson` is documented as
     * normalising every track "inside a queue list, a playlist, a download entry" — this
     * is the download-entry case.
     */
    @Test
    fun `a nested track with absent fields is sanitised, not left null`() {
        val bareTrack = """[{"track":{"id":"t1","duration":10},"path":"/data/one.m4a"}]"""

        val parsed: List<DownloadManager.Entry> = gson.fromJson(bareTrack, listType)
        val repaired = repairIndex(parsed).single()

        // Reading these is the assertion: a null in any of them is an NPE here.
        assertEquals("t1", repaired.track.id)
        assertNotNull("title is rendered; it must not be null", repaired.track.title)
        assertNotNull("artist is rendered; it must not be null", repaired.track.artist)
        assertNotNull("genre is lowercased elsewhere; it must not be null", repaired.track.genre)
        assertNotNull("tags are iterated; they must not be null", repaired.track.tags)
        assertNotNull("source is switched on; it must not be null", repaired.track.source)
        assertEquals("the path must survive the repair", "/data/one.m4a", repaired.path)
    }

    /**
     * Gson returns `null` for the literal document `null`, which is normal rather than an
     * error — an empty index is the correct reading of it.
     */
    @Test
    fun `a null document is read as an empty index rather than an error`() {
        val parsed: List<DownloadManager.Entry>? = gson.fromJson("null", listType)

        assertTrue(repairIndex(parsed).isEmpty())
    }

    /**
     * Why [AtomicFileWrite] is used for the index, stated as a test rather than as a
     * comment: a document cut off mid-write does not parse, so `loadIndex()`'s `catch`
     * takes over and the app reads as having nothing downloaded.
     */
    @Test
    fun `a truncated document fails to parse, which is what makes the atomic write necessary`() {
        val whole = """[{"track":{"id":"t1"},"path":"/data/one.m4a"}]"""
        val truncated = whole.substring(0, whole.length / 2)

        var threw = false
        try {
            gson.fromJson<List<DownloadManager.Entry>>(truncated, listType)
        } catch (e: Exception) {
            threw = true
        }

        assertTrue(
            "a half-written index must not be silently accepted as an empty one",
            threw
        )
    }
}
