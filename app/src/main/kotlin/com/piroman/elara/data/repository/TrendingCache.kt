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

        /**
         * Upper bound on what the cache persists.
         *
         * The cache backs Home's trending row on a failed fetch — not an
         * archive. The largest feed it can hold is a full API page (30
         * tracks); 40 leaves margin without letting the prefs file grow with
         * every differently-sized response.
         */
        private const val MAX_CACHED_TRACKS = 40

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
     * Fallback method: returns cached tracks regardless of age (useful if device is offline).
     *
     * Age is deliberately ignored here. The cache's consumer is
     * `TrendingCachePolicy`, which only reaches for it when the *fetch* came
     * back empty — where "stale songs the user can play" strictly beats "row
     * gone". A date check here would turn a dead network into an empty row
     * whenever the last successful fetch was yesterday.
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
            val json = gson.toJson(tracks.take(MAX_CACHED_TRACKS))
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
