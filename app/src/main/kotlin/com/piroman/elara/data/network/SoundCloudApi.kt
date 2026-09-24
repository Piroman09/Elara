package com.piroman.elara.data.network

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Full-length audio backend: SoundCloud's public web API (api-v2).
 *
 * Tracks with policy "ALLOW" stream in full; official uploads marked "SNIP"
 * are rights-managed 30s clips, so the resolver only picks ALLOW tracks.
 *
 * The API needs the web app's client_id. It rotates occasionally, so
 * [ClientIdProvider] can rediscover it from soundcloud.com's JS bundles.
 */
interface SoundCloudApi {

    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("client_id") clientId: String,
        @Query("limit") limit: Int = 20
    ): SCSearchResponse

    @GET
    suspend fun resolveTranscoding(
        @Url url: String,
        @Query("client_id") clientId: String
    ): SCTranscodingUrl
}

data class SCSearchResponse(val collection: List<SCTrack> = emptyList())

data class SCTrack(
    val id: Long = 0,
    val title: String = "",
    val duration: Long = 0,          // milliseconds
    val policy: String? = null,      // "ALLOW" = full stream, "SNIP" = 30s clip
    val permalinkUrl: String? = null,
    val artworkUrl: String? = null,
    val playbackCount: Long = 0,
    @SerializedName("likes_count") val likesCount: Long = 0,
    val user: SCUser? = null,
    val media: SCMedia? = null
)

data class SCUser(val username: String? = null)

data class SCMedia(val transcodings: List<SCTranscoding> = emptyList())

data class SCTranscoding(
    val url: String? = null,
    val format: SCFormat? = null
)

data class SCFormat(
    @SerializedName("protocol") val protocol: String? = null,   // "progressive" = direct mp3
    @SerializedName("mime_type") val mimeType: String? = null
)

data class SCTranscodingUrl(val url: String? = null)

/**
 * Discovers and caches the SoundCloud web client_id. Seeded with a known-good
 * value; refreshed from soundcloud.com's JS assets when the API starts
 * returning 401/403 (expired id).
 */
object ClientIdProvider {
    @Volatile
    private var clientId: String = "Pb72ranhoyt6gw7hM7TkzUItXlMWSNSo"

    @Volatile
    private var lastRefreshMs: Long = 0

    fun get(): String = clientId

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Best-effort refresh; keeps the old id on any failure. Rate-limited to 1/min. */
    suspend fun refresh(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < 60_000) return@withContext clientId
        lastRefreshMs = now
        runCatching {
            val home = http.newCall(
                okhttp3.Request.Builder().url("https://soundcloud.com/").build()
            ).execute().use { it.body?.string() } ?: return@runCatching

            val assets = Regex("""https://a-v2\.sndcdn\.com/assets/[^"]+\.js""")
                .findAll(home).map { it.value }.toList()
            val idRegex = Regex("""client_id[:=]"([a-zA-Z0-9]{28,40})""")

            for (asset in assets.asReversed()) {
                val js = http.newCall(
                    okhttp3.Request.Builder().url(asset).build()
                ).execute().use { it.body?.string() } ?: continue
                idRegex.find(js)?.let { match ->
                    clientId = match.groupValues[1]
                    return@runCatching
                }
            }
        }
        clientId
    }
}
