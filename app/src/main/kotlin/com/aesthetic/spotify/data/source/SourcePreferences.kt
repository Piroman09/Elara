package com.aesthetic.spotify.data.source

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SourceSetting(val label: String) {
    AUTO("Auto"),
    JIOSAAVN("JioSaavn"),
    YOUTUBE_MUSIC("YouTube Music")
}

/**
 * Music Source preference (Auto / JioSaavn / YouTube Music), persisted in
 * ELARA's SharedPreferences-backed settings storage.
 */
class SourcePreferences private constructor(context: Context) {

    companion object {
        private const val PREFS = "elara_settings"
        private const val KEY = "music_source"

        @Volatile private var instance: SourcePreferences? = null

        fun getInstance(context: Context): SourcePreferences =
            instance ?: synchronized(this) {
                instance ?: SourcePreferences(context.applicationContext).also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _setting = MutableStateFlow(load())
    val setting: StateFlow<SourceSetting> = _setting

    fun set(value: SourceSetting) {
        prefs.edit().putString(KEY, value.name).apply()
        _setting.value = value
    }

    private fun load(): SourceSetting =
        runCatching { prefs.getString(KEY, null) }
            .getOrNull()
            ?.let { name -> SourceSetting.entries.firstOrNull { it.name == name } }
            ?: SourceSetting.AUTO
}
