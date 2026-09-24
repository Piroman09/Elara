package com.piroman.elara.ui.screens

import com.piroman.elara.data.download.DownloadSizeFormat.ONE_MB
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Downloads header's one-line summary, shared by the screen and the Library
 * card so the two can never disagree.
 *
 * Two behaviours are worth pinning rather than eyeballing:
 *  - a **null** size (not yet summed off the main thread) must print the count
 *    alone, never `0 B`, or the header briefly claims the library is empty;
 *  - the plural comes from the song count, so one song is `"1 song"`, not
 *    `"1 songs"`.
 */
class DownloadsSummaryLabelTest {

    @Test
    fun `an unknown size prints the count alone rather than zero bytes`() {
        assertEquals("130 songs", summaryLabel(130, null))
    }

    @Test
    fun `a known size is appended after a separator`() {
        assertEquals("130 songs · 520.0 MB", summaryLabel(130, 130L * 4 * ONE_MB))
    }

    @Test
    fun `a single song is singular in both clauses`() {
        assertEquals("1 song · 1.0 MB", summaryLabel(1, ONE_MB))
    }

    @Test
    fun `an empty library reads as zero, not as an error`() {
        assertEquals("0 songs", summaryLabel(0, null))
        assertEquals("0 songs · 0 B", summaryLabel(0, 0L))
    }
}
