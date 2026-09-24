package com.piroman.elara.data.playlist

import com.piroman.elara.data.model.listTypeOf
import com.piroman.elara.data.model.MusicSource
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "My playlists vanished" can only come from one place: `PlaylistManager.load()`
 * returning an empty list. That happens either because the write did not survive
 * (see [AtomicFileWriteTest]) or because the parse did not.
 *
 * `PlayHistoryRoundTripTest` pins the round trip for `List<Track>` and
 * `List<PlayStat>`. `Playlist` is a **different and harder** shape: it nests a
 * `List<Track>` inside each element, so it exercises the `Track` adapter at
 * depth, and its own fields are what a *missing key* can poison.
 *
 * `StartupCostBenchmarkTest` parses `List<Playlist>` too, but only ever
 * well-formed JSON, and only asserts the happy path. These tests cover the
 * documents a real install actually has on disk — including the ones written by
 * an older build, which is the case that matters after a field is added.
 */
class PlaylistRoundTripTest {

    private val gson = SafeGson.instance
    private val listType = listTypeOf(Playlist::class.java)

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

    /**
     * The exact pair of calls `PlaylistManager` makes: `toJson` on a
     * `List<Playlist>` (runtime type `ArrayList`, so Gson picks the element
     * adapter from the runtime class) and `fromJson` through an explicit
     * `TypeToken<List<Playlist>>`. Two different code paths in Gson, and only
     * the second one can silently return nothing.
     */
    @Test
    fun `the exact save-then-load round trip preserves a library`() {
        val library = listOf(
            Playlist(id = "p1", name = "Road Trip", createdAt = 1_700_000_000_000, tracks = (1..5).map { fullTrack(it) }),
            Playlist(id = "p2", name = "Focus", createdAt = 1_700_000_000_001, tracks = emptyList()),
            Playlist(id = "p3", name = "Gym", createdAt = 1_700_000_000_002, tracks = listOf(fullTrack(6)))
        )

        val restored: List<Playlist> = gson.fromJson(gson.toJson(library), listType)

        assertEquals("library length must survive", library.size, restored.size)
        assertEquals(library, restored)
        assertEquals("nested tracks must survive at depth", 5, restored[0].tracks.size)
    }

    /**
     * **The rule-5 case, and the reason this file exists.**
     *
     * `Playlist.name` is the one constructor parameter with **no default**. Per
     * the repo's own rule that is not a detail: a type with a parameter lacking a
     * default has no synthetic no-arg constructor, so Gson cannot instantiate it
     * through the constructor and falls back to `Unsafe` allocation — and once it
     * does, **every** absent key becomes a null, not just `name`.
     *
     * `PlaylistManager.load()` only repairs `tracks` (`tracks.orEmpty()`). So a
     * `playlists.json` written before `createdAt` existed would come back with a
     * null inside a non-null `Long`, and `name` — the field the Library renders
     * and `findByName` calls `.equals()` on — would be null too.
     *
     * With a default on every field, Kotlin emits the no-arg constructor, Gson
     * uses it, and absent keys keep their defaults instead. That is the fix this
     * test pins.
     */
    @Test
    fun `a playlist written by an older build without createdAt still loads with real defaults`() {
        // No `createdAt` key — the shape an older build would have written.
        val older = """[{"id":"p1","name":"Road Trip","tracks":[]}]"""

        val parsed: List<Playlist> = gson.fromJson(older, listType)
        val playlist = parsed[0]

        assertEquals("Road Trip", playlist.name)
        assertNotNull("id must not be null", playlist.id)
        assertTrue(
            "an absent createdAt must fall back to the constructor default, not 0",
            playlist.createdAt > 0L
        )
    }

    /**
     * The same failure from the other direction: an entry missing `name`, the
     * parameter without a default. Before the fix this loads a `Playlist` whose
     * `name` is null, and the first `.equals()` or `Text(...)` on it is a crash.
     */
    @Test
    fun `a playlist entry missing its name does not load a null into a non-null field`() {
        val nameless = """[{"id":"p1","createdAt":1700000000000,"tracks":[]}]"""

        val parsed: List<Playlist> = gson.fromJson(nameless, listType)
        val playlist = parsed[0]

        assertNotNull("name is rendered and compared; it must never be null", playlist.name)
        assertTrue("name must be non-empty", playlist.name.isNotEmpty())
    }

    /**
     * The nested-`Track` normalisation claim, at depth. `SafeGson` is documented
     * as normalising every track "inside a queue list, a playlist, a download
     * entry" — this is the playlist case, and it is the one a `Track` field
     * addition would break for existing installs.
     */
    @Test
    fun `a nested track with absent fields is sanitised, not left null`() {
        val withBareTrack =
            """[{"id":"p1","name":"Mix","createdAt":1,"tracks":[{"id":"t1","duration":10}]}]"""

        val parsed: List<Playlist> = gson.fromJson(withBareTrack, listType)
        val track = parsed[0].tracks.single()

        // Reading these is the assertion: a null in any of them is an NPE here.
        assertEquals("t1", track.id)
        assertNotNull("title must be sanitised, not null", track.title)
        assertNotNull("artist must be sanitised, not null", track.artist)
        assertNotNull("genre is lowercased elsewhere; it must not be null", track.genre)
        assertNotNull("tags are iterated; they must not be null", track.tags)
        assertNotNull("source is switched on; it must not be null", track.source)
    }

    /**
     * The self-healing half. [repaired] runs on every element `load()` reads, and
     * it is the only part of the load path a JVM test can reach — so it is tested
     * directly rather than through the manager.
     *
     * An explicit `null` is the case a constructor default does **not** cover:
     * Gson writes null straight into the field, and without this repair the next
     * save would write the null back out, so the file stays corrupt across every
     * restart.
     */
    @Test
    fun `explicit nulls in a stored document are repaired`() {
        val withNulls = """[{"id":null,"name":null,"createdAt":0,"tracks":null}]"""

        val parsed: List<Playlist> = gson.fromJson(withNulls, listType)
        val repaired = parsed.map { it.repaired() }.single()

        assertNotNull("id is the LazyColumn key; a null makes the row unusable", repaired.id)
        assertTrue("id must not be blank", repaired.id.isNotBlank())
        assertEquals(DEFAULT_NAME, repaired.name)
        assertTrue("tracks must be a real list, not null", repaired.tracks.isEmpty())
    }

    /**
     * Why [AtomicFileWrite] exists, stated as a test rather than as a comment: a
     * document cut off mid-write does not parse, so `load()`'s `catch` takes over
     * and the library reads as empty. Without the atomic write, a process killed
     * during a save is a silent wipe.
     */
    @Test
    fun `a truncated document fails to parse, which is what makes the atomic write necessary`() {
        val whole = """[{"id":"p1","name":"Road Trip","createdAt":1,"tracks":[]}]"""
        val truncated = whole.substring(0, whole.length / 2)

        var threw = false
        try {
            gson.fromJson<List<Playlist>>(truncated, listType)
        } catch (e: Exception) {
            threw = true
        }

        assertTrue(
            "a half-written document must not be silently accepted as an empty library",
            threw
        )
    }
}
