package com.piroman.elara.data.download

import com.piroman.elara.data.download.DownloadSizeFormat.ONE_GB
import com.piroman.elara.data.download.DownloadSizeFormat.ONE_KB
import com.piroman.elara.data.download.DownloadSizeFormat.ONE_MB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Downloads screen shows a total size, and that number is the one thing on
 * the screen a user can catch being wrong — "130 songs · 512 B" is obviously
 * broken, while a plausible-looking wrong figure is not. So the unit boundaries
 * are pinned exactly rather than sampled.
 *
 * The top of each range gets its own test because that is where the first
 * implementation broke: 1 byte under a megabyte is 1023.999 KB, which rounds to
 * **1024 KB**. It is arithmetically fine and it looks like a bug, so the unit
 * promotes to MB instead — and these tests hold that behaviour in place.
 */
class DownloadSizeFormatTest {

    @Test
    fun `zero renders as zero bytes`() {
        assertEquals("0 B", DownloadSizeFormat.format(0))
    }

    @Test
    fun `a negative size renders as zero rather than a negative label`() {
        assertEquals("0 B", DownloadSizeFormat.format(-1))
        assertEquals("0 B", DownloadSizeFormat.format(Long.MIN_VALUE))
    }

    @Test
    fun `under a kilobyte the byte count is exact`() {
        assertEquals("1 B", DownloadSizeFormat.format(1))
        assertEquals("512 B", DownloadSizeFormat.format(512))
        assertEquals("1023 B", DownloadSizeFormat.format(ONE_KB - 1))
    }

    @Test
    fun `exactly one kilobyte switches unit`() {
        assertEquals("1 KB", DownloadSizeFormat.format(ONE_KB))
    }

    @Test
    fun `kilobytes are whole numbers`() {
        assertEquals("2 KB", DownloadSizeFormat.format(1536))
        assertEquals("500 KB", DownloadSizeFormat.format(500 * ONE_KB))
    }

    @Test
    fun `exactly one megabyte switches unit and keeps one decimal`() {
        assertEquals("1.0 MB", DownloadSizeFormat.format(ONE_MB))
    }

    @Test
    fun `megabytes keep one decimal`() {
        assertEquals("1.5 MB", DownloadSizeFormat.format(ONE_MB + ONE_MB / 2))
        assertEquals("12.3 MB", DownloadSizeFormat.format((12.3 * ONE_MB).toLong()))
    }

    @Test
    fun `gigabytes use two decimals`() {
        assertEquals("1.00 GB", DownloadSizeFormat.format(ONE_GB))
        assertEquals("1.50 GB", DownloadSizeFormat.format(ONE_GB + ONE_GB / 2))
    }

    @Test
    fun `the top of the kilobyte range promotes to megabytes instead of printing 1024 KB`() {
        assertEquals("1.0 MB", DownloadSizeFormat.format(ONE_MB - 1))
    }

    @Test
    fun `the top of the megabyte range promotes to gigabytes instead of printing 1024 point 0 MB`() {
        assertEquals("1.00 GB", DownloadSizeFormat.format(ONE_GB - 1))
    }

    /**
     * The real shape this was written for: a 130-song playlist at roughly 4 MB
     * a track. Asserts the magnitude is sane, so a unit slip (showing bytes or
     * KB for a library this size) fails here rather than shipping.
     */
    @Test
    fun `a realistic library reads in megabytes`() {
        assertEquals("520.0 MB", DownloadSizeFormat.format(130L * 4 * ONE_MB))
    }

    /**
     * The invariant the promotion exists to protect: the number in front of a
     * unit is always smaller than that unit's own threshold. Checked at every
     * boundary, including the byte just below each one, because that is exactly
     * where rounding used to overflow it.
     */
    @Test
    fun `no printed number reaches the next unit's threshold`() {
        val edges = listOf(ONE_KB - 1, ONE_KB, ONE_MB - 1, ONE_MB, ONE_GB - 1, ONE_GB)
        edges.forEach { edge ->
            val text = DownloadSizeFormat.format(edge)
            val number = text.substringBefore(' ').toDouble()
            val unit = text.substringAfterLast(' ')
            // GB is the largest unit this formatter has, so nothing above it to overflow into.
            if (unit != "GB") {
                assertTrue(
                    "$edge printed '$text' — its number reaches the next unit",
                    number < 1024
                )
            }
        }
    }
}
