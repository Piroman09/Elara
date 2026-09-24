package com.piroman.elara.data.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.piroman.elara.data.diagnostics.PlaybackDiagnostics
import com.piroman.elara.data.diagnostics.StartupTiming
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.model.sanitized
import com.piroman.elara.data.playlist.AtomicFileWrite
import com.piroman.elara.data.model.listTypeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Song downloads for ELARA.
 *
 * Transfer runs through Android's SYSTEM DownloadManager (an OS-level agent):
 * downloads continue in the background even if ELARA is killed, and the agent
 * handles connectivity retries itself. ELARA polls progress, then moves the
 * finished file into app-private storage and indexes it for offline playback.
 * If the system agent is unavailable, an in-app OkHttp transfer is used as a
 * fallback.
 */
class DownloadManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"
        private const val INDEX_FILE = "downloads_index.json"
        private const val PENDING_FILE = "downloads_pending.json"
        private const val UA = "Mozilla/5.0 (Linux; Android 13) Elara/1.0"

        @Volatile private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager =
            instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
    }

    sealed class State {
        /** Progress in 0..1; negative = indeterminate (server sent no length). */
        data class Downloading(val progress: Float) : State()
        object Done : State()
        data class Error(val message: String) : State()
    }

    /**
     * One downloaded track: the [Track] it belongs to and where the audio lives.
     *
     * **Neither parameter has a default, and that is load-bearing.** Kotlin emits a
     * synthetic no-arg constructor only when *every* parameter has one, so Gson
     * cannot instantiate this through the constructor and falls back to `Unsafe`
     * allocation — where an absent key becomes a **null in a non-null property**.
     * [track] is read at ten call sites as `it.track.id`, so a null there is a
     * crash rather than a blank row.
     *
     * [repaired] catches that on the way in, which is why `loadIndex` filters
     * through it. Adding a field here means adding a check there — or giving the
     * new field a default, which restores the no-arg constructor and makes the
     * whole class safe at once (what `Playlist` does).
     */
    data class Entry(val track: Track, val path: String)

    /**
     * A download the system agent has accepted but not yet finished.
     *
     * **No parameter has a default, and that is load-bearing in a way that is easy
     * to miss:** Kotlin only emits a synthetic no-arg constructor when *every*
     * parameter has one, so Gson cannot instantiate this through the constructor
     * and falls back to `Unsafe` allocation — where an absent key becomes a null
     * in a non-null property. Both fields are always written today, so a
     * well-formed queue is fine, but **adding a field here would silently put a
     * null into every existing install's queue.** [loadPending] filters through
     * [isUsable] for exactly that reason; do not remove that guard.
     *
     * The proper fix, when someone has the appetite for it, is the one `PlayStat`
     * already uses — a nullable reference type plus a drop-on-load filter — which
     * is also what `DownloadManager.Entry` still needs.
     */
    private data class Pending(
        val downloadId: Long,
        val track: Track,
        val finalPath: String
    )

    private val gson = SafeGson.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexFile = File(context.filesDir, INDEX_FILE)
    private val pendingFile = File(context.filesDir, PENDING_FILE)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val systemDm =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager

    // Not shared with RetrofitClient's client on purpose: these timeouts are
    // longer, because a download is a large transfer rather than an API call.
    // It is still built on the main thread at construction, hence the timing.
    private val client = StartupTiming.measure("DownloadManager.okHttp") {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", UA)
                        .build()
                )
            }
            .build()
    }

    /**
     * The downloaded tracks, newest first.
     *
     * **Mutate it with `update { }`, never with `value = …`.** The playlist and
     * history stores can get away with a plain assignment because every one of
     * their mutators runs on the main thread. This one cannot: it is written from
     * [scope], which is `Dispatchers.IO`, and `download(track)` starts a coroutine
     * per tap — so two `markDone` calls genuinely overlap. `value = current
     * .filter { … }` is a read-modify-write: both readers see the same list, and
     * the later assignment discards the other's entry. The user-visible result is
     * a download that finishes and then is simply not in the list. `update`
     * retries against the current value, so concurrent writes compose instead of
     * clobbering.
     */
    private val _entries =
        MutableStateFlow<List<Entry>>(StartupTiming.measure("DownloadManager.loadIndex") { loadIndex() })
    val entries: StateFlow<List<Entry>> = _entries

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    /**
     * Total bytes the downloaded files occupy on disk, for the Downloads screen.
     *
     * **Derived, not stored.** The index records paths, not sizes, and a file can
     * be deleted out from under us (storage cleared, another app removing it), so
     * a persisted total would drift. Summing `File.length()` when the entry list
     * changes cannot go stale.
     *
     * Recomputed on [scope] (`Dispatchers.IO`) rather than where [entries] is
     * written: each entry costs a `stat()`, and `markDone` runs inside a download
     * coroutine that must not be slowed by it — while the screen reads this
     * during composition, where disk I/O is forbidden outright.
     *
     * **`null` means "not computed yet", not zero.** The sum runs off the main
     * thread, so there is a window after launch where the entry list is already
     * known but the total is not. Reporting `0` there would flash a wrong
     * "130 songs · 0 B" on the Downloads screen; `null` lets the caller omit the
     * figure until it is real.
     */
    private val _totalBytes = MutableStateFlow<Long?>(null)
    val totalBytes: StateFlow<Long?> = _totalBytes

    init {
        migrateLegacyExternalDownloads()
        scope.launch {
            dir().listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
            reconcilePending()
            val before = _entries.value
            // The predicate is applied *inside* `update` rather than to a snapshot:
            // a download finishing between a snapshot read and the assignment would
            // have its entry dropped by this reconcile.
            _entries.update { current -> current.filter { File(it.path).exists() } }
            val valid = _entries.value
            if (valid.size != before.size) {
                saveIndex(valid)
            }
        }
        // Collecting the StateFlow rather than recomputing at each of the five
        // places that write `_entries` — markDone, delete, the reconcile above,
        // the backup restore and the index load — means a future sixth writer
        // cannot forget to update the total.
        scope.launch {
            entries.collect { current ->
                _totalBytes.value = current.sumOf { entry ->
                    runCatching { File(entry.path).length() }.getOrDefault(0L)
                }
            }
        }
    }

    private fun dir(): File = File(context.filesDir, "downloads").apply { mkdirs() }

    fun localPath(trackId: String): String? =
        _entries.value.firstOrNull { it.track.id == trackId }
            ?.path?.takeIf { File(it).exists() }

    fun isDownloaded(trackId: String): Boolean = localPath(trackId) != null

    /** Fire-and-forget single download (used by per-track taps). */
    fun download(track: Track, resolveUrl: suspend (Track) -> String?) {
        if (isDownloaded(track.id) || !inFlight.add(track.id)) return
        scope.launch {
            diag("START id=${track.id} title=${track.title}")
            doDownload(track, resolveUrl)
        }
    }

    /**
     * Downloads one track and **blocks** until it finishes or fails. Used by
     * [downloadAll] so a batch runs sequentially rather than swamping the
     * network with every track at once.
     */
    suspend fun downloadOne(track: Track, resolveUrl: suspend (Track) -> String?): Result<Unit> {
        if (isDownloaded(track.id)) return Result.success(Unit)
        if (!inFlight.add(track.id)) {
            // Another download for this track is already running — wait it out.
            while (inFlight.contains(track.id)) { delay(200) }
            return Result.success(Unit)
        }
        diag("BATCH_START id=${track.id} title=${track.title}")
        return doDownload(track, resolveUrl)
    }

    /**
     * Queue every undownloaded track in [tracks] for sequential download.
     * One-at-a-time keeps the scraped API from rate-limiting the app.
     */
    fun downloadAll(tracks: List<Track>, resolveUrl: suspend (Track) -> String?) {
        val todo = tracks.filterNot { isDownloaded(it.id) }
        if (todo.isEmpty()) return
        scope.launch {
            todo.forEach { track ->
                downloadOne(track, resolveUrl)
                delay(800) // Polite gap between requests.
            }
        }
    }

    /**
     * The actual transfer, shared by the fire-and-forget and batch paths.
     * Returns success only when the file is on disk and indexed.
     */
    private suspend fun doDownload(track: Track, resolveUrl: suspend (Track) -> String?): Result<Unit> {
        setState(track.id, State.Downloading(-1f))
        return try {
            val url = resolveUrl(track) ?: track.audioUrl
            diag("RESOLVED id=${track.id} url=${url.take(90)}")
            if (url.isBlank()) throw IOException("No stream URL resolved (${track.source.label})")

            val ext = if (url.substringBefore('?').endsWith(".mp3")) "mp3" else "m4a"
            val final = File(dir(), "${track.id}.$ext")
            if (final.exists() && final.length() > 0) {
                markDone(track, final)
                Result.success(Unit)
            } else {
                val downloadId = runCatching { enqueueSystem(track, url, ext) }
                    .onFailure { Log.e(TAG, "enqueue failed: ${it.message}") }
                    .getOrDefault(-1L)

                if (downloadId > 0) {
                    watchSystem(track, downloadId, final)
                } else {
                    transferDirect(track, url, final)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            diag("FAIL id=${track.id} error=${e.message}")
            setState(track.id, State.Error(e.message ?: "Download failed"))
            Result.failure(e)
        } finally {
            inFlight.remove(track.id)
        }
    }

    fun delete(trackId: String) {
        localPath(trackId)?.let { path ->
            runCatching { File(path).delete() }
            // Public Music files live in MediaStore — remove the row too so
            // other apps stop showing the deleted song.
            runCatching {
                val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.delete(
                    uri,
                    android.provider.MediaStore.Audio.Media.DATA + "=?",
                    arrayOf(path)
                )
            }
        }
        _entries.update { current -> current.filter { it.track.id != trackId } }
        saveIndex(_entries.value)
        setState(trackId, null)
    }

    // ── System DownloadManager flow ──────────────────────────────────────

    private fun enqueueSystem(track: Track, url: String, ext: String): Long {
        val dm = systemDm ?: throw IOException("System download agent unavailable")
        val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(track.title.take(60))
            setDescription("Elara")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_HIDDEN)
            setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_MUSIC, "${track.id}.$ext.part"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader("User-Agent", UA)
        }
        return dm.enqueue(request)
    }

    /** Public, user-visible file: Music/Elara/<name>.<ext> */
    private fun publicMusicFile(nameWithoutExt: String, ext: String): File {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Elara"
        ).apply { mkdirs() }
        return File(musicDir, "${safeFileName(nameWithoutExt, "track")}.$ext")
    }

    /** "My Song!: Best Mix" -> "My Song_ Best Mix" (filesystem-safe, readable). */
    private fun safeFileName(title: String, id: String): String {
        val clean = title
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim('.', ' ')
            .take(60)
        return clean.ifEmpty { "track_$id" }
    }

    private suspend fun watchSystem(track: Track, downloadId: Long, final: File) {
        savePending(Pending(downloadId, track, final.absolutePath))
        val query = android.app.DownloadManager.Query().setFilterById(downloadId)

        while (true) {
            val cursor = systemDm?.query(query)
            if (cursor == null) throw IOException("Download agent unavailable")
            cursor.use { c ->
                if (!c.moveToFirst()) throw IOException("System agent lost the download")
                val statusIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)
                val status = c.getInt(statusIdx)

                if (status == android.app.DownloadManager.STATUS_RUNNING) {
                    val bytesIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val bytes = c.getLong(bytesIdx)
                    val total = c.getLong(totalIdx)
                    if (total > 0) setState(track.id, State.Downloading(bytes.toFloat() / total))
                } else when (status) {
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        finalizeSystemDownload(track, downloadId, final)
                        clearPending(downloadId)
                        return
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        val reasonIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON)
                        val reason = c.getInt(reasonIdx)
                        clearPending(downloadId)
                        throw IOException("System agent error ($reason)")
                    }
                }
            }
            delay(600)
        }
    }

    private suspend fun finalizeSystemDownload(track: Track, downloadId: Long, final: File) {
        val cursor = systemDm?.query(android.app.DownloadManager.Query().setFilterById(downloadId))
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val localUriIdx = c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI)
                val localUri = c.getString(localUriIdx)
                val part = localUri?.let { Uri.parse(it).path }?.let { File(it) }
                if (part != null && part.exists() && part.absolutePath != final.absolutePath) {
                    final.parentFile?.mkdirs()
                    if (final.exists()) final.delete()
                    if (!part.renameTo(final)) {
                        part.copyTo(final, overwrite = true)
                        part.delete()
                    }
                }
            }
        }
        markDone(track, final)
        copyToPublicMusic(track, final)
    }

    /**
     * Best-effort user-visible copy in Music/Elara (BloomeeTunes-style).
     * Requires audio-read permissions on 13+; silently skipped otherwise —
     * offline playback always uses the app-private original.
     */
    private fun copyToPublicMusic(track: Track, privateFile: File) {
        runCatching {
            val publicFile = publicMusicFile(track.title, privateFile.extension)
            if (publicFile.exists()) publicFile.delete()
            privateFile.copyTo(publicFile, overwrite = true)
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(publicFile.absolutePath), null, null
            )
            diag("PUBLIC_COPY ${publicFile.absolutePath}")
        }
    }

    /** If ELARA died mid-download, finish the bookkeeping on next launch. */
    private suspend fun reconcilePending() {
        val pendings = loadPending()
        if (pendings.isEmpty()) return
        pendings.forEach { pending ->
            val cursor = systemDm?.query(android.app.DownloadManager.Query().setFilterById(pending.downloadId))
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    when (c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))) {
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                            finalizeSystemDownload(pending.track, pending.downloadId, File(pending.finalPath))
                            clearPending(pending.downloadId)
                        }
                        android.app.DownloadManager.STATUS_FAILED -> {
                            clearPending(pending.downloadId)
                            setState(pending.track.id, State.Error("Download interrupted"))
                        }
                        else -> watchSystem(pending.track, pending.downloadId, File(pending.finalPath))
                    }
                }
            }
        }
    }

    /**
     * Records a download the system agent has accepted.
     *
     * Through [AtomicFileWrite] for the same reason as [saveIndex], and the stakes
     * are higher here: this file is the only mapping from a system download id to
     * the app's track. A truncated queue is not just missing bookkeeping — the
     * download still completes, and nothing is left that knows what to do with
     * the file, so the user's download silently never appears.
     */
    private fun savePending(pending: Pending) {
        val all = loadPending().filterNot { it.track.id == pending.track.id } + pending
        runCatching { AtomicFileWrite.write(pendingFile, gson.toJson(all)) }
    }

    private fun clearPending(downloadId: Long) {
        val all = loadPending().filterNot { it.downloadId == downloadId }
        runCatching { AtomicFileWrite.write(pendingFile, gson.toJson(all)) }
    }

    /**
     * Reads the in-progress download queue.
     *
     * Reports through [PlaybackDiagnostics] because this used to swallow every
     * exception to an empty list **without logging anything at all** — so a queue
     * that failed to load was indistinguishable from one that was never written,
     * and the only symptom was a download that never showed up.
     *
     * Entries that cannot be used are dropped rather than kept; see [isUsable].
     * That also protects [savePending]'s `it.track.id`, which would otherwise
     * dereference a null on the next save and make the loss permanent.
     */
    private fun loadPending(): List<Pending> = try {
        if (pendingFile.exists()) {
            val type = listTypeOf(Pending::class.java)
            val parsed: List<Pending>? = gson.fromJson(pendingFile.readText(), type)
            val usable = parsed.orEmpty().filter { it.isUsable() }
            PlaybackDiagnostics.append(
                context,
                "downloads: pending ${usable.size} of ${parsed?.size ?: 0} resumable"
            )
            usable
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "loadPending: ${e.message}")
        PlaybackDiagnostics.append(
            context,
            "downloads: PENDING LOAD FAILED ${e.message} — in-progress downloads cannot be resumed"
        )
        emptyList()
    }

    /**
     * Whether a deserialised [Pending] can actually be used to resume a download.
     *
     * The null check looks redundant because [track] is declared non-null, and it
     * is not redundant: [Pending] has **no defaulted parameters**, so Kotlin emits
     * no synthetic no-arg constructor and Gson cannot instantiate through the
     * constructor — it falls back to `Unsafe` allocation, where a *missing* key
     * arrives as a null in a non-null property. The declared type is a promise the
     * runtime does not enforce for reflectively-populated objects. Same reasoning
     * as `Track.sanitized()`, including the suppression.
     *
     * `isNullOrBlank()` rather than `isNotBlank()` on purpose: the latter has a
     * non-null receiver and would throw on exactly the null it is meant to catch.
     *
     * The filter never drops a legitimate entry — a system download id is always
     * positive and `finalPath` is always a real path — so this is a guard, not a
     * behaviour change.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun Pending.isUsable(): Boolean =
        track != null && downloadId > 0L && !finalPath.isNullOrBlank()

    // ── Fallback: direct in-app transfer ─────────────────────────────────

    private suspend fun transferDirect(track: Track, url: String, final: File) {
        val tmp = File(dir(), "${track.id}.tmp")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var soFar = 0L
                    var lastPct = -5
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        soFar += read
                        if (total > 0) {
                            val pct = ((soFar * 100) / total).toInt()
                            if (pct >= lastPct + 5) {
                                lastPct = pct
                                setState(track.id, State.Downloading(soFar.toFloat() / total))
                            }
                        }
                    }
                }
            }
        }
        if (final.exists()) final.delete()
        if (!tmp.renameTo(final)) {
            tmp.copyTo(final, overwrite = true)
            tmp.delete()
        }
        markDone(track, final)
        copyToPublicMusic(track, final)
    }

    // ── Shared helpers ───────────────────────────────────────────────────

    private suspend fun markDone(track: Track, final: File) {
        _entries.update { current ->
            listOf(Entry(track, final.absolutePath)) + current.filter { it.track.id != track.id }
        }
        saveIndex(_entries.value)
        setState(track.id, State.Done)
        diag("OK id=${track.id} file=${final.name} bytes=${final.length()}")
        toast("Saved for offline: ${track.title}")
        Log.d(TAG, "Downloaded ${track.title} -> ${final.name} (${final.length()} bytes)")
    }

    private fun setState(id: String, state: State?) {
        _states.value = _states.value.toMutableMap().apply {
            if (state == null) remove(id) else put(id, state)
        }
    }

    private suspend fun toast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun diag(line: String) {
        PlaybackDiagnostics.append(context, "DL  $line")
    }

    /**
     * Rewrites the index of downloaded files.
     *
     * Through [AtomicFileWrite], for the same reason `PlaylistManager` uses it:
     * this file is the app's only record of *what* is downloaded, and
     * [loadIndex] answers a parse failure with `emptyList()`. A plain
     * `writeText` truncates before it writes, so being killed mid-write would
     * leave a fragment that fails to parse — and the app would come back
     * believing nothing had ever been downloaded, with the next save making that
     * permanent. The audio files would still be on disk; the app would simply
     * have forgotten them.
     */
    private fun saveIndex(entries: List<Entry>) {
        try {
            AtomicFileWrite.write(indexFile, gson.toJson(entries))
        } catch (e: Exception) {
            Log.e(TAG, "saveIndex: ${e.message}")
        }
    }

    /**
     * Reads the download index.
     *
     * Reports what it found through [PlaybackDiagnostics] for the same reason the
     * playlist and history stores do: a failed load and an empty store draw
     * exactly the same UI, so without a line in the log "my downloads vanished"
     * has no evidence trail at all.
     *
     * Everything parsed goes through [repairIndex], which drops entries whose
     * `track` came back null from Gson's `Unsafe` path and sanitises the track it
     * keeps. Dropping is safe: an entry with no track cannot be matched against a
     * download, shown, or removed. The dropped count is reported, so a non-zero
     * figure here is direct evidence that the hazard was real.
     */
    private fun loadIndex(): List<Entry> = try {
        if (indexFile.exists()) {
            val type = listTypeOf(Entry::class.java)
            val parsed: List<Entry>? = gson.fromJson(indexFile.readText(), type)
            val result = repairIndex(parsed)
            val dropped = parsed.orEmpty().size - result.size
            PlaybackDiagnostics.append(
                context,
                "downloads: loaded ${result.size} from ${indexFile.length()}B" +
                    if (dropped > 0) " (dropped $dropped unusable)" else ""
            )
            result
        } else {
            // Separates "nothing downloaded yet" from "the index is gone".
            PlaybackDiagnostics.append(context, "downloads: no index at ${indexFile.absolutePath}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "loadIndex: ${e.message}")
        PlaybackDiagnostics.append(
            context,
            "downloads: LOAD FAILED (${indexFile.length()}B) ${e.message} — list reads as EMPTY"
        )
        emptyList()
    }

    /** Older builds stored downloads on secondary external storage. Copy them in. */
    private fun migrateLegacyExternalDownloads() {
        scope.launch {
            runCatching {
                val legacy = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?.let { File(it, "downloads") } ?: return@runCatching
                if (!legacy.isDirectory) return@runCatching
                val target = dir()
                legacy.listFiles()?.forEach { f ->
                    val dest = File(target, f.name)
                    if (dest.exists() && dest.length() == f.length()) {
                        f.delete()
                    } else if (!dest.exists()) {
                        val copied = runCatching {
                            f.copyTo(dest, overwrite = false).length() == f.length()
                        }.getOrDefault(false)
                        if (copied) {
                            _entries.update { current ->
                                current.map { entry ->
                                    if (entry.path == f.absolutePath) entry.copy(path = dest.absolutePath)
                                    else entry
                                }
                            }
                            saveIndex(_entries.value)
                            f.delete()
                            diag("MIGRATED ${f.name} -> internal")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Repairs one index entry as it comes off disk, or rejects it.
 *
 * [DownloadManager.Entry] has no defaulted constructor parameters, so Kotlin emits no
 * synthetic no-arg constructor and Gson cannot instantiate it through the constructor —
 * it falls back to `Unsafe` allocation, where a **missing key becomes a null in a
 * non-null property**. The declared type is a promise the runtime does not enforce for
 * reflectively-populated objects. Same reasoning as `Track.sanitized()`, including the
 * suppression: `isNullOrBlank()` on the path rather than `isNotBlank()`, because the
 * latter has a non-null receiver and would throw on exactly the null it guards.
 *
 * Returning `null` — rather than a patched entry — is the correct rejection. An entry
 * with no usable track has no id to match a download against, no title to show and no
 * artwork to draw, so it can be neither listed nor removed, only crashed on. The audio
 * file stays on disk either way; the index row pointing at it is all that is dropped.
 *
 * Top-level and free of `Context` so it is covered by JVM tests — the same reason
 * `PlaybackDiagnostics.tailLines` takes a `File`.
 */
@Suppress("SENSELESS_COMPARISON")
internal fun DownloadManager.Entry.repaired(): DownloadManager.Entry? {
    // Both are declared non-null; the `Unsafe` path can still put a null in either.
    if (track == null || path.isNullOrBlank()) return null
    // `sanitized()` is not strictly needed here — `SafeGson` already registers a Track
    // deserializer, so a track parsed through it is normalised at any depth. It is kept
    // because it is what makes the `id.isBlank()` check below *safe*: on a raw parse the
    // id can be a null inside a non-null String, and `.isBlank()` on that is an NPE.
    val cleanTrack = track.sanitized()
    if (cleanTrack.id.isBlank()) return null
    return DownloadManager.Entry(cleanTrack, path)
}

/**
 * Applies [repaired] across a parsed index, dropping the entries it rejects.
 *
 * A `null` input is normal rather than an error: Gson returns null for the literal
 * document `null`, and an empty list is the correct reading of it.
 */
internal fun repairIndex(
    parsed: List<DownloadManager.Entry>?
): List<DownloadManager.Entry> = parsed.orEmpty().mapNotNull { it.repaired() }
