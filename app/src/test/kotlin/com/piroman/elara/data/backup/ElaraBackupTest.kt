package com.piroman.elara.data.backup

import com.piroman.elara.data.history.PlayStat
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.playlist.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The backup document is the one place where a serialisation mistake is not
 * merely a bug but **silent data loss**: R8 strips a field, or Gson fails to
 * find a key, and a restore quietly produces an empty library instead of
 * failing. These tests pin the contract that `verifyReleaseDex` checks in the
 * release dex.
 */
class ElaraBackupTest {

    private fun track(id: String, title: String = "T$id") = Track(
        id = id,
        title = title,
        artist = "Artist $id",
        album = "Album",
        albumArt = "art-$id",
        audioUrl = "url-$id",
        duration = 100,
        genre = "rock",
        energy = 5,
        isLiked = true,
        tags = listOf("tag")
    )

    private fun fullBackup() = ElaraBackup(
        version = ElaraBackup.CURRENT_VERSION,
        exportedAt = 1_700_000_000_000L,
        appPackage = "com.piroman.elara",
        playlists = listOf(
            Playlist(id = "p1", name = "Chill", createdAt = 5L, tracks = listOf(track("a"), track("b"))),
            Playlist(id = "p2", name = "Gym", createdAt = 6L, tracks = emptyList())
        ),
        likedTracks = listOf(track("c")),
        recentTracks = listOf(track("a"), track("c")),
        playStats = listOf(PlayStat(track("a"), 9), PlayStat(track("c"), 3)),
        musicSource = "JIOSAAVN"
    )

    @Test
    fun `a full backup survives a round trip unchanged`() {
        val original = fullBackup()

        val restored = ElaraBackup.decode(ElaraBackup.encode(original))

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `nested playlist tracks and play counts keep their contents`() {
        val restored = ElaraBackup.decode(ElaraBackup.encode(fullBackup()))!!

        assertEquals(2, restored.playlists.size)
        assertEquals(2, restored.playlists[0].tracks.size)
        assertEquals("Chill", restored.playlists[0].name)
        assertEquals("b", restored.playlists[0].tracks[1].id)
        assertEquals(9, restored.playStats[0].count)
        assertEquals("a", restored.playStats[0].track?.id)
    }

    /**
     * The important one.
     *
     * Note it uses **explicit `null`s, not missing keys.** `ElaraBackup` gives
     * every parameter a default, so Kotlin emits a synthetic no-arg constructor
     * and Gson uses it — a *missing* key therefore keeps its default and an
     * absent field is harmless. An explicit `"playlists": null` is different: it
     * is written straight into the field, producing a runtime null inside a
     * property the compiler believes is non-null. Without the `lenient(...)`
     * re-assertion in [ElaraBackup.decode] this throws NPE, so a restore crashes
     * instead of reporting a problem. (Verified by removing the guard: this test
     * fails.)
     */
    @Test
    fun `a document with explicit nulls decodes to empty lists, not null`() {
        val nulled = """
            {"version":1,"exportedAt":123,"playlists":null,"likedTracks":null,
             "recentTracks":null,"playStats":null,"musicSource":null}
        """.trimIndent()

        val decoded = ElaraBackup.decode(nulled)

        assertNotNull(decoded)
        assertTrue(decoded!!.playlists.isEmpty())
        assertTrue(decoded.likedTracks.isEmpty())
        assertTrue(decoded.recentTracks.isEmpty())
        assertTrue(decoded.playStats.isEmpty())
        assertNull(decoded.musicSource)
        assertTrue(decoded.isEmpty)
    }

    @Test
    fun `a document missing every list field still decodes`() {
        val minimal = """{"version":1,"exportedAt":123}"""

        val decoded = ElaraBackup.decode(minimal)

        assertNotNull(decoded)
        assertTrue(decoded!!.playlists.isEmpty())
        assertTrue(decoded.playStats.isEmpty())
        assertTrue(decoded.isEmpty)
    }

    @Test
    fun `a backup from a newer build is rejected rather than half-read`() {
        val fromTheFuture = """{"version":99,"playlists":[]}"""

        try {
            ElaraBackup.decode(fromTheFuture)
            fail("expected UnsupportedVersionException")
        } catch (e: UnsupportedVersionException) {
            assertEquals(99, e.found)
            assertEquals(1, e.supported)
        }
    }

    @Test
    fun `text that is not a backup decodes to null instead of throwing`() {
        assertNull(ElaraBackup.decode("this is not json at all"))
        assertNull(ElaraBackup.decode(""))
        assertNull(ElaraBackup.decode("[]"))
    }

    @Test
    fun `an empty backup reports itself as empty`() {
        assertTrue(ElaraBackup().isEmpty)
        assertTrue(!fullBackup().isEmpty)
    }

    @Test
    fun `the restore summary pluralises correctly`() {
        assertEquals(
            "1 playlist, 1 liked song, 1 history entry, 1 play count",
            RestoreReport(1, 1, 1, 1).summary
        )
        assertEquals(
            "2 playlists, 2 liked songs, 2 history entries, 2 play counts",
            RestoreReport(2, 2, 2, 2).summary
        )
    }

    @Test
    fun `the restore summary omits play counts when there are none`() {
        val summary = RestoreReport(0, 0, 0, 0).summary

        assertEquals("0 playlists, 0 liked songs, 0 history entries", summary)
    }
}
