package com.piroman.elara.data.repository

import android.content.Context
import android.util.Log
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.model.listTypeOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages daily-cached trending songs.
 * Ensures the app loads trending songs instantly on launch,
 * and refreshes them daily when a new calendar day arrives.
 */
class TrendingCache private constructor(context: Context) {

    companion object {
        private const val TAG = "TrendingCache"
        private const val PREFS_NAME = "elara_trending_daily_cache"
        private const val KEY_DATE = "trending_cached_date"
        private const val KEY_TIMESTAMP = "trending_cached_timestamp"
        private const val KEY_TRACKS_JSON = "trending_cached_tracks"

        @Volatile
        private var instance: TrendingCache? = null

        fun getInstance(context: Context): TrendingCache =
            instance ?: synchronized(this) {
                instance ?: TrendingCache(context.applicationContext).also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = SafeGson.instance

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Returns cached tracks only if they were saved today (and less than 24 hours ago).
     * Returns null if cache is empty or from an earlier calendar date, signaling a refresh is needed.
     */
    fun getDailyTracks(): List<Track>? {
        val today = getTodayDateString()
        val cachedDate = prefs.getString(KEY_DATE, null)
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        val ageMs = System.currentTimeMillis() - timestamp

        // Expire if not from today or older than 24 hours
        if (cachedDate != today || ageMs > 24 * 60 * 60 * 1000L) {
            Log.d(TAG, "Cache expired (cachedDate=$cachedDate, today=$today, ageMs=$ageMs)")
            return null
        }

        val json = prefs.getString(KEY_TRACKS_JSON, null) ?: return null
        return try {
            val type = listTypeOf(Track::class.java)
            val list = gson.fromJson<List<Track>>(json, type)
            if (list.isNullOrEmpty()) null else list
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing cached tracks: ${e.message}")
            null
        }
    }

    /**
     * Fallback method: returns cached tracks regardless of age (useful if device is offline).
     */
    fun getCachedTracksAnyAge(): List<Track>? {
        val json = prefs.getString(KEY_TRACKS_JSON, null) ?: return null
        return try {
            val type = listTypeOf(Track::class.java)
            gson.fromJson<List<Track>>(json, type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Persists fresh daily tracks with today's date and current timestamp.
     */
    fun saveDailyTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        try {
            val today = getTodayDateString()
            val json = gson.toJson(tracks)
            prefs.edit()
                .putString(KEY_DATE, today)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_TRACKS_JSON, json)
                .apply()
            Log.d(TAG, "Saved ${tracks.size} tracks to daily cache for $today")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save daily tracks: ${e.message}")
        }
    }

    /**
     * Returns the date string of the last successful trending update.
     */
    fun getLastUpdatedDate(): String {
        return prefs.getString(KEY_DATE, null) ?: "Today"
    }
}
