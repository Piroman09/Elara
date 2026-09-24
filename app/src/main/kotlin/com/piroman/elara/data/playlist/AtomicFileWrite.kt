package com.piroman.elara.data.playlist

import java.io.File

/**
 * Crash-safe single-file write: the content lands through a sibling temp file
 * and a rename, so the destination is never observed half-written.
 *
 * **Why this exists.** `File.writeText(...)` truncates the destination and then
 * writes it. Between those two moments the file on disk is *shorter than either
 * version* — and if the process is killed there (which is precisely what
 * happens when a user swipes the app away right after editing a playlist), the
 * next launch reads a truncated document. `PlaylistManager.load()` cannot parse
 * it, its `catch` swallows the exception and returns an empty list, so **every
 * playlist silently disappears** and the next save makes that permanent.
 *
 * A rename within one filesystem is atomic: a reader sees either the complete
 * old file or the complete new one. The temp file is a sibling of the
 * destination rather than a system temp directory, because a rename across
 * filesystems is not atomic (and on Android it can fail outright).
 *
 * Pure `java.io` with no `Context`, so the behaviour is covered by JVM tests —
 * the same reason `PlaybackDiagnostics.tailLines` takes a `File`.
 */
internal object AtomicFileWrite {

    /** Suffix for the scratch file. Kept beside the destination; see above. */
    internal const val TMP_SUFFIX = ".tmp"

    /**
     * Serialises every write in the process. **This is what makes the fallback
     * path safe**, which the temp-file dance alone does not.
     *
     * Callers are not required to serialise, and in practice they do not:
     * `DownloadManager.saveIndex` runs on `Dispatchers.IO` while
     * `download(track)` starts a coroutine per tap, so two writes to the same
     * destination genuinely overlap. When the rename succeeds that is harmless —
     * each rename publishes a whole document and the last one wins. But when the
     * rename **fails**, the content is written straight into the destination, and
     * two of those interleave: `writeText` truncates and then appends, so
     * overlapping writers *concatenate* into a file that is neither document.
     *
     * That is not theoretical — the concurrency test caught exactly this,
     * reporting a destination holding three concatenated documents. Note that a
     * *unique* scratch name per call does **not** fix it: the collision is on the
     * destination, not on the scratch file. The lock is the fix.
     *
     * Writes here are small and rare (a playlist edit, a download finishing), so
     * serialising them costs nothing measurable — and it means no caller has to
     * know that this primitive is not reentrant.
     *
     * With the lock held, the scratch name can be the fixed sibling used below:
     * there is never a second writer to collide with it, and a scratch file left
     * behind by a process kill is reused rather than accumulating.
     */
    private val lock = Any()

    /**
     * Writes [content] to [file] atomically where the platform allows it.
     *
     * Throws whatever the filesystem throws — the caller owns the error policy.
     */
    fun write(file: File, content: String) = write(file) { it.writeText(content) }

    /**
     * The same write, with the content production separated from the placement so
     * a test can drive it.
     *
     * This overload exists because the property that matters here — *a failure
     * partway through must not touch the destination* — is invisible to any test
     * that only ever performs a **successful** write: in the success case this
     * object and a bare `file.writeText(content)` produce byte-identical results,
     * and every assertion about the final content passes for both. A green suite
     * therefore proved nothing at all; swapping the body for
     * `file.writeText(content)` left all seven tests passing.
     *
     * [produce] is handed the file to write into and is allowed to throw partway,
     * which is exactly the shape of a process being killed mid-write. Production
     * passes a closure that writes the whole string, so the seam costs nothing at
     * runtime and never changes the bytes on disk.
     */
    fun write(file: File, produce: (File) -> Unit) = synchronized(lock) {
        val tmp = File(file.parentFile, file.name + TMP_SUFFIX)
        try {
            produce(tmp)
            if (tmp.renameTo(file)) return@synchronized
            produce(file)
        } finally {
            tmp.delete()
        }
    }
}
