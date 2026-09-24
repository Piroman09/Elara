package com.piroman.elara.data.source

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the user wants the app to avoid background network use.
 *
 * Affects:
 * - Autoplay: skips the catalogue search fallback and only uses the local pool.
 * - Batch downloads: refuses to start a whole playlist, and the caller tells the
 *   user why. This is a hard block rather than a "download anyway?" prompt, so
 *   the toggle cannot be ignored by accident.
 *
 * Deliberately a separate prefs file so it can be toggled without touching
 * the music-source setting.
 */
class DataSaverPreferences private constructor(context: Context) {

    companion object {
        private const val PREFS = "elara_data"
        private const val KEY_ENABLED = "data_saver"

        @Volatile private var instance: DataSaverPreferences? = null

        fun getInstance(context: Context): DataSaverPreferences =
            instance ?: synchronized(this) {
                instance ?: DataSaverPreferences(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled

    fun set(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }
}
