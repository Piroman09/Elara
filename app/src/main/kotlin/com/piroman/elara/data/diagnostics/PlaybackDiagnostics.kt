package com.aesthetic.spotify.data.diagnostics

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Append-only playback/download failure log, surfaced by the diagnostics button
 * in the music-source dialog.
 *
 * This replaces two independent inline copies of
 * `File(dir, "playback_diag.txt").appendText(...)` (one in `PlaybackManager`,
 * one in `DownloadManager`) that had two problems:
 *
 *  1. **They ran on the caller's thread.** `PlaybackManager.appendDiagnostic` is
 *     called from `Player.Listener.onPlayerError` and from the NO_SOURCE branch
 *     of `playAt` — both on the main dispatcher — so every playback failure did
 *     an open/write/close on the UI thread.
 *  2. **Nothing ever truncated the file.** It grew without bound, and the
 *     dialog's reader loaded the *entire* file with `readText()` just to show
 *     the last 30 lines. On a long-lived install that is an unbounded
 *     main-thread read, i.e. a freeze waiting to happen.
 *
 * Writes are now dispatched to IO, reads are a suspend function, and the file is
 * rotated once it passes [MAX_BYTES].
 *
 * [tailLines] and [rotateIfNeeded] take a plain [File] so the logic is covered
 * by JVM tests in `app/src/test/` — no `Context` needed.
 */
object PlaybackDiagnostics {

    private const val TAG = "PlaybackDiag"
    private const val FILE_NAME = "playback_diag.txt"

    /** Rotate once the log passes this size… */
    internal const val MAX_BYTES = 256L * 1024

    /** …keeping roughly this much of the most recent history. */
    internal const val KEEP_BYTES = 64 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget append. Safe to call from the main thread. */
    fun append(context: Context, line: String) {
        val appContext = context.applicationContext
        scope.launch { write(appContext, line) }
    }

    /** The most recent [limit] lines, newest last. Empty when there is no log. */
    suspend fun tail(context: Context, limit: Int): List<String> = withContext(Dispatchers.IO) {
        runCatching { tailLines(fileFor(context.applicationContext), limit) }
            .getOrElse {
                Log.w(TAG, "tail failed: ${it.message}")
                emptyList()
            }
    }

    /** Newest-last tail of the log. Returns empty for a missing file. */
    internal fun tailLines(file: File, limit: Int): List<String> =
        if (!file.exists()) emptyList() else file.readLines().takeLast(limit)

    /**
     * Drops the oldest entries once the file exceeds [maxBytes], keeping the last
     * [keepBytes] characters. A no-op while the file is under the cap.
     */
    internal fun rotateIfNeeded(
        file: File,
        maxBytes: Long = MAX_BYTES,
        keepBytes: Int = KEEP_BYTES
    ) {
        if (file.length() <= maxBytes) return
        file.writeText(file.readText().takeLast(keepBytes))
    }

    private fun fileFor(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    private fun write(context: Context, line: String) {
        runCatching {
            val file = fileFor(context)
            rotateIfNeeded(file)
            file.appendText("\n${System.currentTimeMillis()}  $line\n")
        }.onFailure { Log.w(TAG, "append failed: ${it.message}") }
    }
}
