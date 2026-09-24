package com.piroman.elara.data.network

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

    /**
     * Editorial charts — "India Superhits Top 50", "Hindi 1990s" and friends.
     *
     * Returns a **bare JSON array**, not a `{ "data": [...] }` envelope like
     * [getFeaturedPlaylists], so it maps to a `List` directly. Every item is
     * `type: "playlist"` with a perma-url token, which means charts open through
     * exactly the same [getPlaylistByToken] call the editorial row already uses.
     *
     * Verified live 2026-09-21: 9 charts, all 9 return songs. The sibling names
     * `content.getTopCharts` / `content.getFeaturedCharts` are *not* supported by
     * this host and answer `{"error":{"code":"INPUT_INVALID"}}`.
     */
    @GET("api.php")
    suspend fun getCharts(
        @Query("__call") call: String = "content.getCharts",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): List<JioFeaturedPlaylist>

    /**
     * New releases. Despite the name this is a **mixed feed** — verified live
     * 2026-09-21 at 13 albums and 17 loose songs out of 30 — so callers must
     * filter on [JioFeaturedPlaylist.type] before treating an item as an album.
     *
     * `content.getNewReleases` does not exist on this host; this is the call that
     * actually backs a "new releases" row.
     */
    @GET("api.php")
    suspend fun getNewReleases(
        @Query("__call") call: String = "content.getAlbums",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): JioFeaturedResponse

    /**
     * What people are searching for right now — a **bare array** of 10 items.
     *
     * The list is a live mix of `album`, `song` and `artist` entries, and the size
     * is fixed: an `n` parameter is accepted and ignored. Its `perma_url` points at
     * `www.jiosaavn.com` (unlike [getTrending], whose items carry an internal URL
     * with no usable token), so the titles are trustworthy as search terms.
     *
     * Home uses this as a chip row that *searches* each title rather than opening
     * it — a mixed feed has no single open action, and a search is honest for all
     * three types.
     */
    @GET("api.php")
    suspend fun getTopSearches(
        @Query("__call") call: String = "content.getTopSearches",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): List<JioFeaturedPlaylist>

    /**
     * Songs of an album, resolved from the album's perma-url token.
     *
     * Separate from [getPlaylistByToken] because `webapi.get` returns a
     * **different shape** for `type=album`: the songs are flat
     * ([JioAlbumSong]) rather than the nested `more_info` form [JioSong] expects.
     */
    @GET("api.php")
    suspend fun getAlbumByToken(
        @Query("__call") call: String = "webapi.get",
        @Query("token") token: String,
        @Query("type") type: String = "album",
        @Query("n") limit: Int = 100,
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0",
        @Query("api_version") apiVersion: String = "4",
        @Query("ctx") ctx: String = "web6dot0"
    ): JioWebapiAlbumResponse

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

/**
 * One tile from any of the `content.get*` browse feeds — a featured playlist, a
 * chart, or an album.
 *
 * [type] is what tells them apart, and it is load-bearing: `content.getAlbums`
 * mixes albums and loose songs in one array (13 vs 17 on 2026-09-21), so a caller
 * building a "new releases" row must filter on `type == "album"` or it will try
 * to open a single song as an album.
 */
data class JioFeaturedPlaylist(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val image: String? = null,
    val type: String? = null,
    val count: Int = 0,
    @SerializedName("perma_url") val permaUrl: String? = null,
    /**
     * `content.getAlbums` is a **mixed** feed — albums *and* songs. A song item
     * carries its playable stream here (`more_info.encrypted_media_url`), so the
     * field has to survive parsing for the "New songs" row to be buildable. An
     * album item's `more_info` is a different, irrelevant shape and is ignored.
     */
    @SerializedName("more_info") val moreInfo: JioMoreInfo? = null
)

data class JioWebapiPlaylistResponse(
    val title: String? = null,
    @SerializedName("list") val songs: List<JioSong> = emptyList(),
    @SerializedName("fullsongs") val fullSongs: List<JioSong> = emptyList(),
    @SerializedName("list_count") val listCount: Int = 0
) {
    /**
     * The playlist's songs, under whichever key the server used this time.
     *
     * `webapi.get?type=playlist` normally answers with `list`. On 2026-09-21 one
     * response came back with **`fullsongs`** plus a `contents` token string and
     * no `list` key at all; the identical request repeated six times immediately
     * after returned `list` every time, so the variant could not be reproduced on
     * demand. Mapping both is cheap, and the failure it guards is silent: an
     * unmapped key yields an empty list, the caller converts that to `null`, and
     * the browse screen opens blank with nothing logged anywhere.
     *
     * The `fullsongs` item shape was not re-observed, so this fallback may itself
     * come back empty. It can only ever add songs, never drop any.
     */
    val tracks: List<JioSong> get() = songs.ifEmpty { fullSongs }
}

/** `webapi.get?type=album` — songs live under `songs`, and [title] is the album name. */
data class JioWebapiAlbumResponse(
    val title: String? = null,
    val songs: List<JioAlbumSong> = emptyList(),
    @SerializedName("list_count") val listCount: Int = 0
)

/**
 * A song inside an album.
 *
 * **This is a different shape from [JioSong], not a variant of it**, and the
 * difference is silent rather than loud. Album songs carry no `more_info` object
 * at all — the media URL, duration and bitrate are flat top-level fields — and
 * the title is in `song`, not `title`. Feeding these through [JioSong] therefore
 * yields a null `moreInfo`, and `jioSongToTrack` drops every one of them on its
 * first guard, so an album would open to an empty list with no error anywhere.
 *
 * Verified live 2026-09-21: 6/6 songs of the "Vibe" album carry a top-level
 * `encrypted_media_url`.
 */
data class JioAlbumSong(
    val id: String = "",
    @SerializedName("song") val title: String = "",
    val image: String? = null,
    val duration: String? = null,
    @SerializedName("320kbps") val has320: String? = null,
    @SerializedName("encrypted_media_url") val encryptedMediaUrl: String? = null,
    val album: String? = null,
    @SerializedName("primary_artists") val primaryArtists: String? = null,
    @SerializedName("more_info") val moreInfo: JioMoreInfo? = null
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
