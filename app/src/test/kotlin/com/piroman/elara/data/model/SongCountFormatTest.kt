package com.piroman.elara.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bug this pins: the playlist header rendered **"1 songs"** for a one-song
 * playlist, because the label appended `" songs"` unconditionally. Exactly the
 * mistake that previously shipped as "1 hours" / "1 minutes" in two separate
 * hand-rolled duration labels.
 */
class SongCountFormatTest {

    @Test
    fun `one is singular`() {
        assertEquals("1 song", SongCountFormat.songs(1))
    }

    @Test
    fun `zero takes the plural, not the singular`() {
        assertEquals("0 songs", SongCountFormat.songs(0))
    }

    @Test
    fun `two and above are plural`() {
        assertEquals("2 songs", SongCountFormat.songs(2))
        assertEquals("130 songs", SongCountFormat.songs(130))
    }

    @Test
    fun `negative counts still read as plural rather than crashing`() {
        assertEquals("-1 songs", SongCountFormat.songs(-1))
    }
}
