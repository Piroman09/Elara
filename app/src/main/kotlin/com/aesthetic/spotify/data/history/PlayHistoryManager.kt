package com.aesthetic.spotify.data.history

import android.content.Context
import android.util.Log
import com.aesthetic.spotify.data.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Most-recently-played tracks, newest first, persisted across launches. */
class PlayHistoryManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PlayHistory"
        private const val PREFS = "elara_settings"
        private const val KEY = "recently_played"
        private const val MAX_ENTRIES = 20

        @Volatile private var instance: PlayHistoryManager? = null

        fun getInstance(context: Context): PlayHistoryManager =
            instance ?: synchronized(this) {
                instance ?: PlayHistoryManager(context.applicationContext).also { instance = it }
            }
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _recent = MutableStateFlow<List<Track>>(load())
    val recent: StateFlow<List<Track>> = _recent

    fun record(track: Track) {
        val updated = (listOf(track) + _recent.value.filterNot { it.id == track.id }).take(MAX_ENTRIES)
        _recent.value = updated
        try {
            prefs.edit().putString(KEY, gson.toJson(updated)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "save: ${e.message}")
        }
    }

    private fun load(): List<Track> = try {
        val type = object : TypeToken<List<Track>>() {}.type
        gson.fromJson(prefs.getString(KEY, null), type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
