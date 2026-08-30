package com.aesthetic.spotify.data.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.aesthetic.spotify.data.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * Downloads full-length audio (resolved via the app's stream resolver) into
 * app-private storage — no storage permission needed. An index file keeps
 * track metadata so downloads survive restarts and play offline.
 */
class DownloadManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"
        private const val INDEX_FILE = "downloads_index.json"

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

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexFile = File(context.filesDir, INDEX_FILE)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Elara/1.0")
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
        // Drop orphaned temp files from interrupted downloads.
        scope.launch {
            dir().listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
            // Forget index entries whose file disappeared.
            val valid = _entries.value.filter { File(it.path).exists() }
            if (valid.size != _entries.value.size) {
                _entries.value = valid
                saveIndex(valid)
            }
        }
    }

    /**
     * Older builds stored downloads on secondary external storage, which some
     * devices report as unavailable. Move any existing files into the
     * always-writable internal dir so prior downloads keep working.
     */
    private fun migrateLegacyExternalDownloads() {
        scope.launch {
            runCatching {
                val legacy = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?.let { File(it, "downloads") } ?: return@runCatching
                if (!legacy.isDirectory) return@runCatching
                val target = dir()
                legacy.listFiles()?.forEach { f ->
                    val dest = File(target, f.name)
                    if (!dest.exists()) {
                        if (f.renameTo(dest)) {
                            // Point existing index entries at the new path.
                            _entries.value = _entries.value.map { entry ->
                                if (entry.path == f.absolutePath) {
                                    entry.copy(path = dest.absolutePath)
                                } else entry
                            }
                            saveIndex(_entries.value)
                        }
                    }
                    f.delete()
                }
            }
        }
    }

    private fun dir(): File =
        File(context.filesDir, "downloads").apply { mkdirs() }

    fun localPath(trackId: String): String? =
        _entries.value.firstOrNull { it.track.id == trackId }
            ?.path?.takeIf { File(it).exists() }

    fun isDownloaded(trackId: String): Boolean = localPath(trackId) != null

    /** Download [track]; [resolveUrl] supplies the full-length stream URL. */
    fun download(track: Track, resolveUrl: suspend (Track) -> String?) {
        if (isDownloaded(track.id) || !inFlight.add(track.id)) return
        scope.launch {
            setState(track.id, State.Downloading(-1f))
            try {
                val url = resolveUrl(track) ?: track.audioUrl
                val ext = if (url.substringBefore('?').endsWith(".mp3")) "mp3" else "m4a"
                val final = File(dir(), "${track.id}.$ext")
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

                _entries.value = listOf(Entry(track, final.absolutePath)) +
                        _entries.value.filter { it.track.id != track.id }
                saveIndex(_entries.value)
                setState(track.id, State.Done)
                Log.d(TAG, "Downloaded ${track.title} -> ${final.name} (${final.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                File(dir(), "${track.id}.tmp").delete()
                setState(track.id, State.Error(e.message ?: "Download failed"))
            } finally {
                inFlight.remove(track.id)
            }
        }
    }

    fun delete(trackId: String) {
        localPath(trackId)?.let { File(it).delete() }
        _entries.value = _entries.value.filter { it.track.id != trackId }
        saveIndex(_entries.value)
        setState(trackId, null)
    }

    private fun setState(id: String, state: State?) {
        _states.value = _states.value.toMutableMap().apply {
            if (state == null) remove(id) else put(id, state)
        }
    }

    private fun loadIndex(): List<Entry> = try {
        val json = indexFile.readText()
        val type = object : TypeToken<List<Entry>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveIndex(entries: List<Entry>) = try {
        indexFile.writeText(gson.toJson(entries))
    } catch (e: Exception) {
        Log.e(TAG, "saveIndex: ${e.message}")
    }
}
