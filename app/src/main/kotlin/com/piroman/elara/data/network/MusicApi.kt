package com.piroman.elara.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Music catalog backend: Apple iTunes Search API + Apple RSS top-songs charts.
 * Both are public, keyless, and return directly streamable audio URLs.
 */
interface MusicApi {

    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("limit") limit: Int = 25
    ): ITunesSearchResponse

    @GET("rss/topsongs/limit/25/explicit/true/json")
    suspend fun getTrending(): ITunesRssResponse
}

data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesResult> = emptyList()
)

data class ITunesResult(
    val trackId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String? = null,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val trackTimeMillis: Long? = null
)

data class ITunesRssResponse(val feed: ITunesFeed? = null)

data class ITunesFeed(val entry: List<ITunesRssEntry>? = null)

data class ITunesRssEntry(
    @SerializedName("im:name") val name: RssLabel? = null,
    @SerializedName("im:artist") val artist: RssLabel? = null,
    @SerializedName("im:collection") val collection: RssCollection? = null,
    @SerializedName("im:image") val images: List<RssImage>? = null,
    val link: List<RssLink>? = null
) {
    val audioUrl: String?
        get() = link?.firstOrNull { it.attributes?.rel == "enclosure" }?.attributes?.href
}

data class RssLabel(val label: String? = null)

data class RssCollection(
    @SerializedName("im:name") val name: RssLabel? = null
)

data class RssImage(val label: String? = null)

data class RssLink(val attributes: RssLinkAttributes? = null)

data class RssLinkAttributes(
    val rel: String? = null,
    val type: String? = null,
    val href: String? = null
)

/**
 * Lyrics backend: LRCLIB — free, keyless, returns LRC-synced and plain lyrics.
 */
interface LyricsApi {

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): List<LrcLibResult>

    @GET("api/search")
    suspend fun searchLyricsFuzzy(
        @Query("q") q: String
    ): List<LrcLibResult>
}

data class LrcLibResult(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)
