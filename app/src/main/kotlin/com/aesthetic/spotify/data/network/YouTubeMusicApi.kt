package com.aesthetic.spotify.data.network

import android.util.Log
import com.aesthetic.spotify.data.model.MusicSource
import com.aesthetic.spotify.data.model.Track
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/*
 * YouTube Music source for ELARA, speaking YouTube's InnerTube API directly.
 *
 * Protocol reference: Echo Music (github.com/EchoMusicApp/Echo-Music),
 * innertube module, GPL-3.0. This is an INDEPENDENT implementation written for
 * ELARA against the publicly observable InnerTube protocol — no Echo source
 * code is copied. Client identity values (client names / versions / user
 * agents) reflect publicly documented InnerTube behavior:
 *   - Search:        WEB_REMIX (no auth, no PoToken required)
 *   - Stream resolve: ANDROID "no SDK" context (no Authorization header,
 *                     no PoToken) — returns a muxed itag-18 mp4 (audio+video)
 *                     direct URL that ExoPlayer plays natively. Adaptive
 *                     (audio-only) formats are integrity-gated without a
 *                     PoToken, so we deliberately use the muxed stream.
 *
 * Stream URLs are time-limited by YouTube; they are resolved at play time and
 * never persisted.
 */
object YouTubeMusicApi {

    private const val TAG = "YouTubeMusic"
    private const val API = "https://music.youtube.com/youtubei/v1/"

    private const val WEB_REMIX_VERSION = "1.20260213.01.00"
    private const val ANDROID_VERSION = "21.03.38"
    private const val UA_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val UA_ANDROID =
        "com.google.android.youtube/$ANDROID_VERSION (Linux; U; Android 14) gzip"

    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Search YouTube Music; returns ELARA tracks tagged [MusicSource.YOUTUBE_MUSIC]. */
    suspend fun search(query: String, limit: Int = 15): List<Track> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = """
                    {"context":{"client":{"clientName":"WEB_REMIX",
                      "clientVersion":"$WEB_REMIX_VERSION","hl":"en","gl":"IN"}},"query":${jStr(query)}}
                """.trimIndent()

                val root = post(
                    url = API + "search?prettyPrint=false",
                    body = body,
                    headers = mapOf(
                        "User-Agent" to UA_WEB,
                        "X-YouTube-Client-Name" to "67",
                        "X-YouTube-Client-Version" to WEB_REMIX_VERSION
                    )
                ) ?: return@runCatching emptyList()

                parseSearchResults(root)
                    .distinctBy { it.id }
                    .take(limit)
            }.getOrElse {
                Log.e(TAG, "search failed: ${it.message}")
                emptyList()
            }
        }

    /**
     * Resolve a directly playable URL for a YouTube video. Prefers the highest
     * bitrate muxed (audio+video) format with a direct URL; falls back to any
     * adaptive format that carries a direct URL. Returns null when YouTube
     * gates playback (bot checks, paid/private videos, network errors).
     */
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                {"context":{"client":{"clientName":"ANDROID",
                  "clientVersion":"$ANDROID_VERSION","hl":"en","gl":"IN",
                  "osName":"Android","osVersion":"14"}},
                 "videoId":${jStr(videoId)},"contentCheckOk":true,"racyCheckOk":true}
            """.trimIndent()

            val root = post(
                url = API + "player?prettyPrint=false",
                body = body,
                headers = mapOf(
                    "User-Agent" to UA_ANDROID,
                    "X-YouTube-Client-Name" to "3",
                    "X-YouTube-Client-Version" to ANDROID_VERSION
                )
            ) ?: return@runCatching null

            val rootObj = JsonParser.parseString(root ?: return@runCatching null).asJsonObject

            val status = rootObj.get("playabilityStatus")
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("status")?.takeIf { !it.isJsonNull }?.asString
            if (status != "OK") {
                Log.e(TAG, "player status: $status for $videoId")
                return@runCatching null
            }

            val streamingData = rootObj.get("streamingData")
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null

            val candidates = mutableListOf<Pair<Int, String>>() // bitrate to url

            streamingData.get("formats")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                val f = el.asJsonObject
                val url = f.get("url")?.takeIf { !it.isJsonNull }?.asString
                if (url != null) candidates += (f.get("bitrate")?.asInt ?: 0) to url
            }
            streamingData.get("adaptiveFormats")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                val f = el.asJsonObject
                val url = f.get("url")?.takeIf { !it.isJsonNull }?.asString
                val mime = f.get("mimeType")?.asString ?: ""
                if (url != null && mime.startsWith("audio/")) {
                    candidates += (f.get("bitrate")?.asInt ?: 0) to url
                }
            }

            candidates.maxByOrNull { it.first }?.second
        }.getOrElse {
            Log.e(TAG, "resolveStreamUrl failed: ${it.message}")
            null
        }
    }

    // ── Search parsing ───────────────────────────────────────────────────

    private fun parseSearchResults(root: String): List<Track> {
        val out = mutableListOf<Track>()
        val jsonRoot = JsonParser.parseString(root)

        fun walk(node: com.google.gson.JsonElement?) {
            if (node == null || node.isJsonNull) return
            if (node.isJsonObject) {
                val obj = node.asJsonObject
                if (obj.has("musicResponsiveListItemRenderer")) {
                    parseListItem(obj.getAsJsonObject("musicResponsiveListItemRenderer"))
                        ?.let { out += it }
                }
                obj.entrySet().forEach { walk(it.value) }
            } else if (node.isJsonArray) {
                node.asJsonArray.forEach { walk(it) }
            }
        }
        walk(jsonRoot)
        return out
    }

    private fun parseListItem(item: com.google.gson.JsonObject): Track? {
        val videoId = item.getAsJsonObject("playlistItemData")
            ?.get("videoId")?.takeIf { !it.isJsonNull }?.asString
            ?: return null

        val flex = item.getAsJsonArray("flexColumns") ?: return null
        if (flex.size() < 2) return null

        fun flexText(index: Int): String = runCatching {
            flex.get(index).asJsonObject
                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                .getAsJsonObject("text")
                .getAsJsonArray("runs")
                .joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
        }.getOrDefault("")

        val title = flexText(0).trim()
        if (title.isEmpty()) return null

        // Subtitle shapes: "Artist • Album • 3:47" (songs) or
        // "Channel • 117M views • 3:47" (videos). Top-result rows prefix a
        // type label ("Video" / "Song"), which we skip.
        val typeLabels = setOf("video", "song", "single", "ep", "album")
        val subtitleParts = flexText(1).split("•").map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in typeLabels }
        val durationRegex = Regex("\\d+:\\d{2}")
        val durationText = subtitleParts.lastOrNull { durationRegex.matches(it) }
            ?: fixedColumnText(item)?.trim()?.takeIf { durationRegex.matches(it) }
        val durationSec = durationText?.split(":")
            ?.takeIf { it.size == 2 }
            ?.let { (it[0].toIntOrNull() ?: 0) * 60 + (it[1].toIntOrNull() ?: 0) }
            ?: 0

        val artist = subtitleParts.firstOrNull().orEmpty().ifEmpty { "Unknown Artist" }
        val album = subtitleParts.getOrNull(1)
            ?.takeUnless { durationRegex.matches(it) || it.endsWith("views") }

        val art = runCatching {
            item.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.maxByOrNull { it.asJsonObject.get("width")?.asInt ?: 0 }
                ?.asJsonObject?.get("url")?.asString
        }.getOrNull() ?: ""

        return Track(
            id = videoId,
            videoId = videoId,
            title = title,
            artist = artist,
            album = album ?: "YouTube Music",
            albumArt = art,
            audioUrl = "", // resolved at play time via [resolveStreamUrl]
            duration = durationSec,
            source = MusicSource.YOUTUBE_MUSIC
        )
    }

    /** Song-shelf rows carry the duration in a fixed column rather than the subtitle. */
    private fun fixedColumnText(item: com.google.gson.JsonObject): String? = runCatching {
        item.getAsJsonArray("fixedColumns")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("musicResponsiveListItemFixedColumnRenderer")
            ?.getAsJsonObject("text")
            ?.getAsJsonArray("runs")
            ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
    }.getOrNull()

    // ── HTTP ─────────────────────────────────────────────────────────────

    private fun post(url: String, body: String, headers: Map<String, String>): String? {
        val builder = Request.Builder().url(url).post(body.toRequestBody(json))
        headers.forEach { (k, v) -> builder.header(k, v) }
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e(TAG, "HTTP ${resp.code} for ${url.substringBefore('?')}")
                return null
            }
            return resp.body?.string()
        }
    }

    private fun jStr(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        return "\"$escaped\""
    }
}
