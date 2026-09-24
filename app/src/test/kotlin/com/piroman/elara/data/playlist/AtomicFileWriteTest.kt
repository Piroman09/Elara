package com.piroman.elara.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CyclicBarrier

/**
 * The write that stands between a user's playlists and a silent wipe.
 *
 * This is the highest-stakes file operation in the app: if it fails or leaves a
 * fragment, `PlaylistManager.load()` throws, swallows the exception and returns
 * an empty list — so the library reads as empty and the *next* save makes that
 * permanent. There is no emulator here to observe that, so it is pinned by test
 * instead.
 *
 * **One platform caveat, so nobody over-reads these results.** `File.renameTo`
 * overwrites an existing destination on POSIX (Android, and CI's Linux) but
 * *fails* when the destination exists on Windows, which is where this suite runs
 * locally. On Windows the assertions below are therefore satisfied by the
 * direct-write fallback rather than by the rename. That is not a hole in the
 * contract — the two properties under test (previous content survives a
 * mid-write failure; the bytes are staged in a temp sibling) hold either way —
 * but the rename itself is only exercised on Android and in CI.
 */
class AtomicFileWriteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `creates the file when it does not exist yet`() {
        val target = File(tmp.root, "playlists.json")
        AtomicFileWrite.write(target, "[{\"id\":\"a\"}]")
        assertTrue(target.exists())
        assertEquals("[{\"id\":\"a\"}]", target.readText())
    }

    @Test
    fun `replaces existing content`() {
        val target = File(tmp.root, "playlists.json")
        AtomicFileWrite.write(target, "old")
        AtomicFileWrite.write(target, "new")
        assertEquals("new", target.readText())
    }

    /**
     * The truncation case, and the reason the temp-file dance exists: replacing a
     * large document with a small one must not leave the tail of the old one
     * behind. `rename` swaps the inode, so the result is exactly the new bytes.
     */
    @Test
    fun `replacing a large file with a small one leaves no trailing bytes`() {
        val target = File(tmp.root, "playlists.json")
        AtomicFileWrite.write(target, "a".repeat(100_000))
        AtomicFileWrite.write(target, "[]")
        assertEquals("[]", target.readText())
        assertEquals(2L, target.length())
    }

    @Test
    fun `leaves no temporary file behind`() {
        val target = File(tmp.root, "playlists.json")
        AtomicFileWrite.write(target, "content")
        val leftovers = tmp.root.listFiles().orEmpty()
            .filter { it.name.endsWith(AtomicFileWrite.TMP_SUFFIX) }
        assertTrue("temp files left behind: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `an empty write is a real write, not a no-op`() {
        val target = File(tmp.root, "playlists.json")
        AtomicFileWrite.write(target, "something")
        AtomicFileWrite.write(target, "")
        assertTrue(target.exists())
        assertEquals("", target.readText())
    }

    @Test
    fun `handles content larger than a single buffer without corruption`() {
        val target = File(tmp.root, "playlists.json")
        val big = "x".repeat(512 * 1024)
        AtomicFileWrite.write(target, big)
        assertEquals(big.length, target.readText().length)
    }

    /**
     * The caller owns the error policy, which only works if a failure actually
     * surfaces. A directory in the destination's place defeats both the rename
     * and the direct-write fallback, so this must throw rather than report
     * success — a save that fails silently is the bug being guarded against.
     */
    @Test
    fun `a failed write throws instead of reporting success`() {
        val target = File(tmp.root, "playlists.json").apply { mkdirs() }
        var threw = false
        try {
            AtomicFileWrite.write(target, "content")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("expected a throw when the destination cannot be written", threw)
    }

    /**
     * **The test that actually pins atomicity**, and the reason the `produce`
     * overload exists.
     *
     * Every other test here performs a *successful* write, and in that case an
     * atomic write and a bare `file.writeText(content)` are indistinguishable —
     * both leave exactly the new bytes. This was verified by mutation: swapping
     * the body for `file.writeText(content)` left all seven tests green, so the
     * suite was pinning the wrong thing.
     *
     * A write that dies partway is where the two diverge, and it is also the
     * real-world case: the user edits a playlist and swipes the app away.
     * `writeText` has already truncated the destination by then, so the old
     * library is gone and the new one is a fragment. Going through a temp file
     * means the destination still holds the *previous* document, and the next
     * launch reads the library as it was rather than as empty.
     */
    @Test
    fun `a failure partway through the write leaves the previous content intact`() {
        val target = File(tmp.root, "playlists.json")
        AtomicFileWrite.write(target, """[{"id":"keepme"}]""")

        var threw = false
        try {
            AtomicFileWrite.write(target) { scratch ->
                scratch.writeText("""[{"id":"half""")
                throw IOException("process killed mid-write")
            }
        } catch (e: IOException) {
            threw = true
        }

        assertTrue("the simulated failure must propagate", threw)
        assertEquals(
            "a partial write must not be able to destroy the previous library",
            """[{"id":"keepme"}]""",
            target.readText()
        )
    }

    /**
     * The placement half of the same property: the bytes must be produced into
     * a scratch file and published by a rename, never written into the
     * destination directly.
     *
     * This fails for any implementation that hands the destination itself to
     * the writer — including the naive `file.writeText(content)` body — which
     * makes it the second guard against silently regressing to a truncating
     * write.
     *
     * Asserted as a *shape* rather than an exact name, so the assertion states the
     * property that matters ("a sibling scratch file, not the destination") and
     * does not accidentally pin an implementation detail.
     */
    @Test
    fun `the content is produced into a sibling temp file, not the destination`() {
        val target = File(tmp.root, "playlists.json")
        val writtenInto = mutableListOf<String>()

        AtomicFileWrite.write(target) { scratch ->
            scratch.writeText("payload")
            writtenInto += scratch.name
        }

        val staged = writtenInto.single()
        assertNotEquals("the destination must not be written in place", target.name, staged)
        assertTrue(
            "the scratch file must be a sibling of the destination, was $staged",
            staged.startsWith(target.name) && staged.endsWith(AtomicFileWrite.TMP_SUFFIX)
        )
        assertEquals("payload", target.readText())
    }

    /**
     * Overlapping writes to the same destination must still leave one whole
     * document behind.
     *
     * This is not hypothetical: `DownloadManager.saveIndex` runs on
     * `Dispatchers.IO` while `download(track)` starts a coroutine per tap, so two
     * writes to the same file genuinely overlap.
     *
     * **This test earned its place by failing.** The first version of the atomic
     * write used a *unique* scratch name per call, on the theory that the danger
     * was two writers sharing one scratch file. It reported a destination holding
     * **three concatenated documents** — so the theory was wrong. The real
     * collision is on the *destination*: when `renameTo` fails (which it does
     * whenever the destination already exists, and on Windows that is the normal
     * case, not an edge case), the content is written straight into the
     * destination, and overlapping `writeText`-then-append writers concatenate
     * there. A per-call scratch name cannot help; serialising the write can, which
     * is what `AtomicFileWrite`'s lock does.
     *
     * Each producer writes in small chunks with a yield between them, so
     * overlapping writers interleave with high probability rather than by luck.
     * The assertion is exact: the destination must hold **one writer's whole
     * document**, never a mixture. A mixture can have the same length as a single
     * payload, so only a content comparison catches it.
     */
    @Test
    fun `concurrent writes to one destination never publish a mixed document`() {
        val target = File(tmp.root, "index.json")
        val payloads = (0 until 4).map { i -> "w$i:".repeat(2_000) }
        val startTogether = CyclicBarrier(payloads.size)

        val threads = payloads.map { payload ->
            Thread {
                startTogether.await()
                AtomicFileWrite.write(target) { scratch ->
                    scratch.writeText("")
                    payload.chunked(256).forEach { chunk ->
                        scratch.appendText(chunk)
                        Thread.yield()
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val published = target.readText()
        assertTrue(
            "the destination must hold exactly one writer's document, not a mixture " +
                "(got ${published.length} bytes; a single document is ${payloads.first().length})",
            published in payloads
        )
    }
}
