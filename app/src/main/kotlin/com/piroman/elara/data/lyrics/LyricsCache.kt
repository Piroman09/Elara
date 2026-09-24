package com.piroman.elara.data.lyrics

import android.content.Context
import com.piroman.elara.data.model.LyricLine
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.listTypeOf
import java.io.File

/**
 * On-disk lyrics cache so the same song does not hit the network every time
 * its lyrics are shown. Lyrics are tiny (~2 KB per song) and rarely change,
 * so there is no eviction or TTL — the only expiry is a reinstall.
 */
class LyricsCache private constructor(context: Context) {

    companion object {
        private const val DIR = "lyrics"

        @Volatile private var instance: LyricsCache? = null

        fun getInstance(context: Context): LyricsCache =
            instance ?: synchronized(this) {
                instance ?: LyricsCache(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val dir = File(context.filesDir, DIR).apply { mkdirs() }
    private val gson = SafeGson.instance

    fun get(trackId: String): List<LyricLine>? {
        val file = File(dir, "$trackId.json")
        if (!file.exists()) return null
        return runCatching {
            val type = listTypeOf(LyricLine::class.java)
            gson.fromJson<List<LyricLine>>(file.readText(), type)
        }.getOrNull()
    }

    fun put(trackId: String, lyrics: List<LyricLine>) {
        val file = File(dir, "$trackId.json")
        runCatching { file.writeText(gson.toJson(lyrics)) }
    }
}
