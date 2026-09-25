package com.piroman.elara.data.source

import android.content.Context
import com.piroman.elara.data.model.SpeedMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The speed-mode setting, persisted in `elara_settings` alongside the other
 * playback toggles.
 *
 * The default is written as `SpeedMode.NORMAL.name` rather than a bare string
 * so the key and the enum cannot drift apart.
 */
class SpeedModePreferences private constructor(context: Context) {

    companion object {
        private const val PREFS = "elara_settings"
        private const val KEY_MODE = "speed_mode"

        @Volatile private var instance: SpeedModePreferences? = null

        fun getInstance(context: Context): SpeedModePreferences =
            instance ?: synchronized(this) {
                instance ?: SpeedModePreferences(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        SpeedMode.fromNameOrDefault(prefs.getString(KEY_MODE, SpeedMode.NORMAL.name))
    )
    val mode: StateFlow<SpeedMode> = _mode

    fun setMode(value: SpeedMode) {
        prefs.edit().putString(KEY_MODE, value.name).apply()
        _mode.value = value
    }
}
