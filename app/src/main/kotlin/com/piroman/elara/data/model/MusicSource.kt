package com.piroman.elara.data.model

/**
 * Which backend a [Track] came from / should play through.
 * Gson default (JIOSAAVN) keeps previously persisted likes/playlists/downloads valid.
 */
enum class MusicSource {
    JIOSAAVN,
    YOUTUBE_MUSIC;

    val label: String
        get() = when (this) {
            JIOSAAVN -> "JioSaavn"
            YOUTUBE_MUSIC -> "YouTube Music"
        }
}
