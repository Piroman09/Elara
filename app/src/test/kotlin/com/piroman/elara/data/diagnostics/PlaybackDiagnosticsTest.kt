package com.piroman.elara.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The diagnostics log is the only record of playback/download failures on a
 * device without adb, so it must not silently lose history — and it must not
 * grow without bound either. These cover the two file-level helpers that
 * [PlaybackDiagnostics] exposes for exactly that reason.
 */
class PlaybackDiagnosticsTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ── tailLines ─────────────────────────────────────────────────────────

    @Test
    fun `missing file yields no lines instead of throwing`() {
        val missing = File(temp.root, "nope.txt")
        assertFalse(missing.exists())

        assertEquals(emptyList<String>(), PlaybackDiagnostics.tailLines(missing, 30))
    }

    @Test
    fun `tail keeps the newest lines and preserves their order`() {
        val file = temp.newFile("diag.txt")
        file.writeText((1..100).joinToString("\n") { "line$it" })

        val tail = PlaybackDiagnostics.tailLines(file, 3)

        // Newest last, not reversed.
        assertEquals(listOf("line98", "line99", "line100"), tail)
    }

    @Test
    fun `tail returns everything when the limit exceeds the file`() {
        val file = temp.newFile("diag.txt")
        file.writeText("a\nb")

        assertEquals(listOf("a", "b"), PlaybackDiagnostics.tailLines(file, 30))
    }

    // ── rotateIfNeeded ────────────────────────────────────────────────────

    @Test
    fun `rotation is a no-op while the file is under the cap`() {
        val file = temp.newFile("diag.txt")
        file.writeText("x".repeat(500))

        PlaybackDiagnostics.rotateIfNeeded(file, maxBytes = 1000, keepBytes = 100)

        assertEquals(500, file.length().toInt())
    }

    @Test
    fun `rotation keeps the tail once the file passes the cap`() {
        val file = temp.newFile("diag.txt")
        file.writeText("A".repeat(400) + "B".repeat(600))

        PlaybackDiagnostics.rotateIfNeeded(file, maxBytes = 500, keepBytes = 100)

        // The last 100 characters survive — i.e. the newest history, and
        // crucially the "B" tail rather than the stale "A" head.
        assertEquals(100, file.length().toInt())
        assertTrue(file.readText().all { it == 'B' })
    }

    @Test
    fun `rotation keeps a readable log that still accepts appends`() {
        val file = temp.newFile("diag.txt")
        file.writeText("old\n".repeat(200))

        PlaybackDiagnostics.rotateIfNeeded(file, maxBytes = 100, keepBytes = 40)
        file.appendText("\nfresh entry\n")

        val tail = PlaybackDiagnostics.tailLines(file, 5)
        assertTrue("newest entry must survive rotation", tail.any { it == "fresh entry" })
    }

    @Test
    fun `real caps are sane relative to each other`() {
        // Guards against a future edit making KEEP_BYTES >= MAX_BYTES, which
        // would rotate to no effect and let the file grow forever.
        assertTrue(PlaybackDiagnostics.KEEP_BYTES < PlaybackDiagnostics.MAX_BYTES)
    }
}
