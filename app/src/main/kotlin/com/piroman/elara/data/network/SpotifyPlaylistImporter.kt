package com.aesthetic.spotify.data.network

import android.util.Log
import com.aesthetic.spotify.data.model.MusicSource
import com.aesthetic.spotify.data.model.Track
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SpotifyImportResult(
    val name: String,
    val coverArt: String,
    val tracks: List<Track>,
    val totalInPlaylist: Int = tracks.size
)

object SpotifyPlaylistImporter {
    private const val TAG = "SpotifyImporter"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val PLAYLIST_QUERY_HASH = "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Pattern for Spotify web and URI formats:
    // Matches playlist or album with id
    private val SPOTIFY_ENTITY_PATTERN = Pattern.compile(
        "(?:open\\.spotify\\.com/(?:intl-[a-z]{2,5}/)?(?:user/[^/]+/)?|spotify:)(playlist|album)[/:]([a-zA-Z0-9]{15,30})",
        Pattern.CASE_INSENSITIVE
    )

    private val RAW_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{15,30}$")

    data class ResolvedEntity(val type: String, val id: String)

    /**
     * Resolves Spotify URL or ID, following redirects if it's a shortened URL (e.g. spotify.link).
     */
    suspend fun resolveEntity(input: String): ResolvedEntity? = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return@withContext null

        // Extract any URL present in the input string (users may paste "Check out this playlist: https://...")
        val urlMatcher = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        var targetUrl = if (urlMatcher.find()) urlMatcher.group(0) else trimmed

        // Follow redirect for shortened links using a GET request
        if (targetUrl.contains("spotify.link", ignoreCase = true) || targetUrl.contains("spoti.fi", ignoreCase = true)) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    Log.d(TAG, "Shortened link resolved from $targetUrl to $finalUrl")
                    targetUrl = finalUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving redirect: ${e.message}")
            }
        }

        // Match against standard Spotify entity pattern
        val matcher = SPOTIFY_ENTITY_PATTERN.matcher(targetUrl)
        if (matcher.find()) {
            val type = matcher.group(1)?.lowercase() ?: "playlist"
            val id = matcher.group(2) ?: return@withContext null
            return@withContext ResolvedEntity(type, id)
        }

        // Check if raw ID was provided
        if (RAW_ID_PATTERN.matcher(trimmed).matches()) {
            return@withContext ResolvedEntity("playlist", trimmed)
        }

        null
    }

    /**
     * Attempts to obtain an anonymous bearer token from the embed page state or fallback track embed.
     */
    private fun extractOrFetchToken(pageProps: JsonObject?): String? {
        val session = pageProps?.getAsJsonObject("state")
            ?.getAsJsonObject("settings")
            ?.getAsJsonObject("session")
        val token = session?.get("accessToken")?.asString
        if (!token.isNullOrBlank()) {
            return token
        }

        // Fallback: bootstrap anonymous token from a known public track embed
        return try {
            val req = Request.Builder()
                .url("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@use null
                val m = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(body)
                if (m.find()) {
                    val root = JsonParser.parseString(m.group(1)).asJsonObject
                    root.getAsJsonObject("props")
                        ?.getAsJsonObject("pageProps")
                        ?.getAsJsonObject("state")
                        ?.getAsJsonObject("settings")
                        ?.getAsJsonObject("session")
                        ?.get("accessToken")?.asString
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bootstrap anonymous Spotify token: ${e.message}")
            null
        }
    }

    /**
     * Queries Spotify's internal Pathfinder GraphQL service to paginate through all playlist tracks.
     * Overcomes the 100-track limit on public embed pages.
     */
    private fun fetchPlaylistViaPathfinder(
        playlistId: String,
        token: String,
        playlistName: String,
        coverArt: String,
        onProgress: ((imported: Int, total: Int, name: String, coverArt: String) -> Unit)?
    ): Pair<List<Track>, Int>? {
        return try {
            var offset = 0
            val limit = 100
            var totalCount = -1
            val tracks = mutableListOf<Track>()

            while (true) {
                val variables = JsonObject().apply {
                    addProperty("uri", "spotify:playlist:$playlistId")
                    addProperty("offset", offset)
                    addProperty("limit", limit)
                    addProperty("enableWatchFeedEntrypoint", false)
                }
                val extensions = JsonObject().apply {
                    val pq = JsonObject().apply {
                        addProperty("version", 1)
                        addProperty("sha256Hash", PLAYLIST_QUERY_HASH)
                    }
                    add("persistedQuery", pq)
                }

                val qParams = "operationName=fetchPlaylist" +
                        "&variables=" + URLEncoder.encode(variables.toString(), StandardCharsets.UTF_8.name()) +
                        "&extensions=" + URLEncoder.encode(extensions.toString(), StandardCharsets.UTF_8.name())

                val req = Request.Builder()
                    .url("$PATHFINDER_URL?$qParams")
                    .header("Authorization", "Bearer $token")
                    .header("app-platform", "WebPlayer")
                    .header("User-Agent", USER_AGENT)
                    .build()

                val respStr = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Pathfinder query failed with code: ${resp.code}")
                        return@use null
                    }
                    resp.body?.string()
                } ?: break

                val root = JsonParser.parseString(respStr).asJsonObject
                val plData = root.getAsJsonObject("data")?.getAsJsonObject("playlistV2")
                val content = plData?.getAsJsonObject("content") ?: break

                if (totalCount == -1) {
                    totalCount = content.get("totalCount")?.asInt ?: 0
                }

                val items = content.getAsJsonArray("items")
                if (items == null || items.size() == 0) {
                    break
                }

                for (itemElem in items) {
                    if (!itemElem.isJsonObject) continue
                    val it = itemElem.asJsonObject
                    val itemV2 = it.getAsJsonObject("itemV2")
                    val itemData = itemV2?.getAsJsonObject("data") ?: it.getAsJsonObject("data") ?: continue

                    val title = itemData.get("name")?.asString?.trim() ?: continue
                    if (title.isEmpty()) continue

                    val artistList = mutableListOf<String>()
                    val artistsItems = itemData.getAsJsonObject("artists")?.getAsJsonArray("items")
                    if (artistsItems != null) {
                        for (a in artistsItems) {
                            val aName = a.asJsonObject?.getAsJsonObject("profile")?.get("name")?.asString?.trim()
                            if (!aName.isNullOrEmpty()) artistList.add(aName)
                        }
                    }
                    val artist = if (artistList.isNotEmpty()) artistList.joinToString(", ") else "Unknown Artist"

                    val albumObj = itemData.getAsJsonObject("albumOfTrack")
                    val albumName = albumObj?.get("name")?.asString?.trim()?.ifEmpty { playlistName } ?: playlistName
                    var trackArt = coverArt
                    val albumSources = albumObj?.getAsJsonObject("coverArt")?.getAsJsonArray("sources")
                    if (albumSources != null && albumSources.size() > 0) {
                        val src = albumSources[0].asJsonObject.get("url")?.asString
                        if (!src.isNullOrEmpty()) trackArt = src
                    }

                    val durMs = itemData.getAsJsonObject("trackDuration")?.get("totalMilliseconds")?.asLong ?: 0L
                    val durationSec = (durMs / 1000).toInt()

                    val uri = itemData.get("uri")?.asString ?: ""
                    val trackId = if (uri.isNotEmpty()) {
                        "spotify_${uri.substringAfterLast(":")}"
                    } else {
                        "spotify_${UUID.randomUUID()}"
                    }

                    tracks.add(
                        Track(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = albumName,
                            albumArt = trackArt,
                            audioUrl = "",
                            duration = durationSec,
                            source = MusicSource.JIOSAAVN
                        )
                    )
                }

                val reportedTotal = if (totalCount > 0) totalCount else tracks.size
                onProgress?.invoke(tracks.size, reportedTotal, playlistName, coverArt)

                offset += items.size()
                if (totalCount > 0 && offset >= totalCount) {
                    break
                }
            }

            if (tracks.isNotEmpty()) {
                val finalTotal = if (totalCount > 0) totalCount else tracks.size
                Pair(tracks, finalTotal)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pathfinder pagination failed: ${e.message}")
            null
        }
    }

    /**
     * Helper to parse tracks from the embed page's __NEXT_DATA__ trackList array (used for albums or fallback).
     */
    private fun parseEmbedTracks(
        trackListArray: com.google.gson.JsonArray?,
        playlistName: String,
        coverArt: String
    ): List<Track> {
        if (trackListArray == null) return emptyList()
        val tracks = mutableListOf<Track>()
        for (elem in trackListArray) {
            if (!elem.isJsonObject) continue
            val tObj = elem.asJsonObject

            val title = tObj.get("title")?.asString?.trim()
                ?: tObj.get("name")?.asString?.trim()
                ?: continue
            if (title.isEmpty()) continue

            val artist = tObj.get("subtitle")?.asString?.trim()
                ?: tObj.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.get("name")?.asString?.trim()
                ?: "Unknown Artist"

            val durationMs = tObj.get("duration")?.asLong
                ?: tObj.get("duration_ms")?.asLong
                ?: 0L
            val durationSec = (durationMs / 1000).toInt()

            val uri = tObj.get("uri")?.asString ?: ""
            val trackId = if (uri.isNotEmpty()) {
                "spotify_${uri.substringAfterLast(":")}"
            } else {
                "spotify_${UUID.randomUUID()}"
            }

            val audioPreview = tObj.getAsJsonObject("audioPreview")?.get("url")?.asString
                ?: tObj.get("preview_url")?.asString
                ?: ""

            tracks.add(
                Track(
                    id = trackId,
                    title = title,
                    artist = artist,
                    album = playlistName,
                    albumArt = coverArt,
                    audioUrl = audioPreview,
                    duration = durationSec,
                    source = MusicSource.JIOSAAVN
                )
            )
        }
        return tracks
    }

    /**
     * Fetches public playlist or album metadata and all tracks from Spotify without the 100-song limit.
     */
    suspend fun fetchPlaylist(
        urlOrId: String,
        onProgress: ((imported: Int, total: Int, name: String, coverArt: String) -> Unit)? = null
    ): SpotifyImportResult = withContext(Dispatchers.IO) {
        val entityRef = resolveEntity(urlOrId)
            ?: throw IllegalArgumentException(
                "Could not recognize a valid Spotify playlist or album link. Please paste a link like https://open.spotify.com/playlist/..."
            )

        val embedUrl = "https://open.spotify.com/embed/${entityRef.type}/${entityRef.id}?utm_source=oembed"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw IllegalStateException(
                        "Playlist not found or is set to Private. Please make sure the playlist is public on Spotify."
                    )
                }
                throw IllegalStateException("Spotify responded with error code ${response.code}")
            }
            response.body?.string() ?: throw IllegalStateException("Empty response from Spotify")
        }

        val scriptPattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val matcher = scriptPattern.matcher(html)
        if (!matcher.find()) {
            throw IllegalStateException("Could not extract playlist data. The playlist might be private or unavailable.")
        }

        val jsonStr = matcher.group(1)
        val root = JsonParser.parseString(jsonStr).asJsonObject
        val pageProps = root.getAsJsonObject("props")?.getAsJsonObject("pageProps")
            ?: throw IllegalStateException("Unable to read Spotify page data.")

        // Check for Spotify soft-404 or error status inside pageProps
        val status = pageProps.get("status")?.asInt ?: 200
        val pageTitle = pageProps.get("title")?.asString ?: ""
        val pageDesc = pageProps.get("description")?.asString ?: ""

        if (status == 404 || pageTitle.contains("not found", ignoreCase = true) || pageDesc.contains("can't seem to find", ignoreCase = true)) {
            throw IllegalStateException(
                "This playlist is private or does not exist. In the Spotify app, tap '...' > 'Make public' (or 'Add to profile') and try again."
            )
        }

        if (status >= 400 || pageTitle.contains("Page not available", ignoreCase = true)) {
            val err = pageDesc.ifBlank { pageTitle }.ifBlank { "Playlist is currently unavailable on Spotify" }
            throw IllegalStateException("$err. Please make sure the playlist is set to Public.")
        }

        // Try to locate entity across possible JSON paths
        val state = pageProps.getAsJsonObject("state")
        val stateData = state?.getAsJsonObject("data")
        val entity: JsonObject? = stateData?.getAsJsonObject("entity")
            ?: pageProps.getAsJsonObject("entity")
            ?: pageProps.getAsJsonObject("data")?.getAsJsonObject("entity")

        val playlistName = entity?.get("name")?.asString
            ?: entity?.get("title")?.asString
            ?: pageProps.get("title")?.asString
            ?: "Imported Spotify ${entityRef.type.replaceFirstChar { it.uppercase() }}"

        // Extract cover artwork
        var coverArt = ""
        val coverSources = entity?.getAsJsonObject("coverArt")?.getAsJsonArray("sources")
            ?: entity?.getAsJsonArray("images")
        if (coverSources != null && coverSources.size() > 0) {
            val first = coverSources[0]
            coverArt = if (first.isJsonObject) {
                first.asJsonObject.get("url")?.asString ?: ""
            } else ""
        }

        onProgress?.invoke(0, 0, playlistName, coverArt)

        // If it's a playlist, attempt to paginate all tracks using Spotify's Pathfinder API
        if (entityRef.type == "playlist") {
            val token = extractOrFetchToken(pageProps)
            if (!token.isNullOrBlank()) {
                val pfResult = fetchPlaylistViaPathfinder(
                    playlistId = entityRef.id,
                    token = token,
                    playlistName = playlistName,
                    coverArt = coverArt,
                    onProgress = onProgress
                )
                if (pfResult != null && pfResult.first.isNotEmpty()) {
                    return@withContext SpotifyImportResult(
                        name = playlistName,
                        coverArt = coverArt,
                        tracks = pfResult.first,
                        totalInPlaylist = pfResult.second
                    )
                }
            }
        }

        // Fallback / Album branch: parse tracks directly from embed __NEXT_DATA__
        val trackListArray = entity?.getAsJsonArray("trackList")
            ?: pageProps.getAsJsonArray("trackList")
            ?: stateData?.getAsJsonArray("trackList")

        val tracks = parseEmbedTracks(trackListArray, playlistName, coverArt)
        if (tracks.isEmpty()) {
            throw IllegalStateException("No tracks found in this playlist. Please ensure the playlist is public and has songs.")
        }

        onProgress?.invoke(tracks.size, tracks.size, playlistName, coverArt)

        SpotifyImportResult(
            name = playlistName,
            coverArt = coverArt,
            tracks = tracks,
            totalInPlaylist = tracks.size
        )
    }
}
