package com.aesthetic.spotify.data.network

import android.util.Base64
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * JioSaavn's public web API: returns official full-length songs (up to
 * 320kbps AAC from their CDN). Media URLs arrive DES-encrypted with the
 * well-known static web-player key, so the app decrypts them on-device.
 */
interface JioSaavnApi {

    @GET("api.php")
    suspend fun searchSongs(
        @Query("__call") call: String = "search.getResults",
        @Query("q") query: String,
        @Query("n") limit: Int = 8,
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): JioSearchResponse

    /** Editorial featured playlists shown on ELARA's Home. */
    @GET("api.php")
    suspend fun getFeaturedPlaylists(
        @Query("__call") call: String = "content.getFeaturedPlaylists",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): JioFeaturedResponse

    /** Full song list of a playlist, resolved from its perma-url token. */
    @GET("api.php")
    suspend fun getPlaylistByToken(
        @Query("__call") call: String = "webapi.get",
        @Query("token") token: String,
        @Query("type") type: String = "playlist",
        @Query("n") limit: Int = 100,
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): JioWebapiPlaylistResponse

    /** Song details (incl. encrypted media URL) keyed by song id. */
    @GET("api.php")
    suspend fun getSongDetails(
        @Query("__call") call: String = "song.getDetails",
        @Query("pids") pids: String,
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4"
    ): Map<String, JioSong>
}

data class JioSearchResponse(val results: List<JioSong> = emptyList())

data class JioFeaturedResponse(val data: List<JioFeaturedPlaylist> = emptyList())

data class JioFeaturedPlaylist(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val image: String? = null,
    @SerializedName("perma_url") val permaUrl: String? = null
)

data class JioWebapiPlaylistResponse(
    val title: String? = null,
    @SerializedName("list") val songs: List<JioSong> = emptyList(),
    @SerializedName("list_count") val listCount: Int = 0
)

data class JioSong(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val image: String? = null,
    @SerializedName("play_count") val playCount: String? = null,
    @SerializedName("more_info") val moreInfo: JioMoreInfo? = null
)

data class JioMoreInfo(
    val album: String? = null,
    val duration: String? = null,
    @SerializedName("320kbps") val has320: String? = null,
    @SerializedName("encrypted_media_url") val encryptedMediaUrl: String? = null
)

object JioSaavnCrypto {
    private val key = SecretKeySpec("38346591".toByteArray(), "DES")

    /** Decrypt the web player's media URL (DES/ECB, base64 input). */
    fun decryptMediaUrl(encrypted: String): String {
        val data = Base64.decode(encrypted, Base64.DEFAULT)
        val plain = runCatching {
            Cipher.getInstance("DES/ECB/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, key)
            }.doFinal(data)
        }.getOrElse {
            // Some payloads aren't strictly PKCS5-padded; strip trailing
            // padding/control bytes manually instead.
            Cipher.getInstance("DES/ECB/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key)
            }.doFinal(data)
        }
        return String(plain).trimEnd { it.code < 0x20 || it.code == 0x7f }
    }

    /** Decrypted URLs point at 96kbps; swap to 320kbps when the song offers it. */
    fun toStreamUrl(encrypted: String, prefer320: Boolean): String {
        val url = decryptMediaUrl(encrypted)
        return if (prefer320) url.replace("_96.mp4", "_320.mp4") else url
    }

    /** Album art comes at 150x150; upgrade to 500x500. */
    fun hiResArt(url: String?): String =
        url?.replace("150x150", "500x500") ?: ""
}
