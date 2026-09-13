package com.aesthetic.spotify.data.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.aesthetic.spotify.data.diagnostics.PlaybackDiagnostics
import com.aesthetic.spotify.data.model.SafeGson
import com.aesthetic.spotify.data.model.Track
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    data class Entry(val track: Track, val path: String)

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

    private val client = OkHttpClient.Builder()
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

    private val _entries = MutableStateFlow<List<Entry>>(loadIndex())
    val entries: StateFlow<List<Entry>> = _entries

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    init {
        migrateLegacyExternalDownloads()
        scope.launch {
            dir().listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
            reconcilePending()
            val valid = _entries.value.filter { File(it.path).exists() }
            if (valid.size != _entries.value.size) {
                _entries.value = valid
                saveIndex(valid)
            }
        }
    }

    private fun dir(): File = File(context.filesDir, "downloads").apply { mkdirs() }

    fun localPath(trackId: String): String? =
        _entries.value.firstOrNull { it.track.id == trackId }
            ?.path?.takeIf { File(it).exists() }

    fun isDownloaded(trackId: String): Boolean = localPath(trackId) != null

    /** Download [track]; [resolveUrl] supplies the full-length stream URL. */
    fun download(track: Track, resolveUrl: suspend (Track) -> String?) {
        if (isDownloaded(track.id) || !inFlight.add(track.id)) return
        scope.launch {
            diag("START id=${track.id} title=${track.title}")
            setState(track.id, State.Downloading(-1f))
            try {
                val url = resolveUrl(track) ?: track.audioUrl
                diag("RESOLVED id=${track.id} url=${url.take(90)}")
                if (url.isBlank()) throw IOException("No stream URL resolved (${track.source.label})")

                val ext = if (url.substringBefore('?').endsWith(".mp3")) "mp3" else "m4a"
                val final = File(dir(), "${track.id}.$ext")
                if (final.exists() && final.length() > 0) {
                    markDone(track, final)
                    return@launch
                }

                // Primary: system download agent (background-capable).
                val downloadId = runCatching { enqueueSystem(track, url, ext) }
                    .onFailure { Log.e(TAG, "enqueue failed: ${it.message}") }
                    .getOrDefault(-1L)

                if (downloadId > 0) {
                    watchSystem(track, downloadId, final)
                } else {
                    // Fallback writes app-private (public dir needs the system
                    // agent on Android 10+); offline playback still works.
                    val privateFinal = File(dir(), "${track.id}.$ext")
                    transferDirect(track, url, privateFinal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                diag("FAIL id=${track.id} error=${e.message}")
                setState(track.id, State.Error(e.message ?: "Download failed"))
                toast("Download failed: ${e.message}")
            } finally {
                inFlight.remove(track.id)
            }
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
        _entries.value = _entries.value.filter { it.track.id != trackId }
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

    private fun savePending(pending: Pending) {
        val all = loadPending().filterNot { it.track.id == pending.track.id } + pending
        runCatching { pendingFile.writeText(gson.toJson(all)) }
    }

    private fun clearPending(downloadId: Long) {
        val all = loadPending().filterNot { it.downloadId == downloadId }
        runCatching { pendingFile.writeText(gson.toJson(all)) }
    }

    private fun loadPending(): List<Pending> = try {
        if (pendingFile.exists()) {
            val type = object : TypeToken<List<Pending>>() {}.type
            gson.fromJson(pendingFile.readText(), type) ?: emptyList()
        } else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

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
        _entries.value = listOf(Entry(track, final.absolutePath)) +
                _entries.value.filter { it.track.id != track.id }
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

    private fun saveIndex(entries: List<Entry>) {
        try {
            indexFile.writeText(gson.toJson(entries))
        } catch (e: Exception) {
            Log.e(TAG, "saveIndex: ${e.message}")
        }
    }

    private fun loadIndex(): List<Entry> = try {
        if (indexFile.exists()) {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson(indexFile.readText(), type) ?: emptyList()
        } else emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "loadIndex: ${e.message}")
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
                            _entries.value = _entries.value.map { entry ->
                                if (entry.path == f.absolutePath) entry.copy(path = dest.absolutePath)
                                else entry
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
