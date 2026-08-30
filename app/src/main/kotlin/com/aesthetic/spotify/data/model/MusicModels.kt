package com.aesthetic.spotify.data.model

data class Track(
    val id: String,
    val videoId: String? = null,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String,
    val albumArt: String,
    val audioUrl: String,
    val duration: Int,
    val accentColor: String? = null,
    val isLiked: Boolean = false,
    val source: MusicSource = MusicSource.JIOSAAVN
)

data class VibeCoords(val x: Float, val y: Float)

data class LyricLine(
    val id: String,
    val time: Float,
    val text: String
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val coverArt: String,
    val tracks: List<Track>
)
