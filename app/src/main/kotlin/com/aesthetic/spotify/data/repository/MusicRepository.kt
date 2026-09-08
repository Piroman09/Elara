package com.aesthetic.spotify.data.repository

import android.util.Log
import com.aesthetic.spotify.data.model.LyricLine
import com.aesthetic.spotify.data.model.MusicSource
import com.aesthetic.spotify.data.model.Playlist
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.data.network.ClientIdProvider
import com.aesthetic.spotify.data.network.JioSaavnApi
import com.aesthetic.spotify.data.network.JioSaavnCrypto
import com.aesthetic.spotify.data.network.LyricsApi
import com.aesthetic.spotify.data.network.MusixmatchClient
import com.aesthetic.spotify.data.network.MusicApi
import com.aesthetic.spotify.data.network.RetrofitClient
import com.aesthetic.spotify.data.network.SCSearchResponse
import com.aesthetic.spotify.data.network.SoundCloudApi
import com.aesthetic.spotify.data.network.SpotifyPlaylistImporter
import com.aesthetic.spotify.data.network.YouTubeMusicApi
import com.aesthetic.spotify.data.source.SourceSetting
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

class MusicRepository(
    private val api: MusicApi = RetrofitClient.musicApi,
    private val lyricsApi: LyricsApi = RetrofitClient.lyricsApi,
    private val soundCloud: SoundCloudApi = RetrofitClient.soundCloudApi,
    private val jioSaavn: JioSaavnApi = RetrofitClient.jioSaavnApi
) {
    companion object {
        private const val TAG = "MusicRepository"
        private val ACCENTS = listOf("#8B5CF6", "#06B6D4", "#EC4899", "#F59E0B", "#10B981")

        /** Upscale Apple's 100x100 artwork to 600x600. */
        private fun hiRes(url: String?): String =
            url?.replace("100x100bb", "600x600bb")
                ?.replace("170x170bb", "600x600bb")
                ?.replace("60x60bb", "600x600bb")
                ?.replace("55x55bb", "600x600bb")
                ?.replace("30x30bb", "600x600bb")
                ?: ""
    }

    /** Verified iTunes preview URLs — shown instantly while the live chart loads. */
    private val fallbackHits = listOf(
        Track(
            id = "1440870375",
            title = "Starboy (feat. Daft Punk)",
            artist = "The Weeknd",
            album = "Starboy",
            albumArt = "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/b5/92/bb/b592bb72-52e3-e756-9b26-9f56d08f47ab/16UMGIM67864.rgb.jpg/600x600bb.jpg",
            audioUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/11/71/d6/1171d6ad-3c96-e027-2af6-58028426588c/mzaf_15137631797407745471.plus.aac.p.m4a",
            duration = 230,
            accentColor = "#8B5CF6"
        ),
        Track(
            id = "1411628233",
            title = "Believer",
            artist = "Imagine Dragons",
            album = "Evolve",
            albumArt = "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/11/7a/b8/117ab805-6811-8929-18b9-0fad7baf0c25/17UMGIM98210.rgb.jpg/600x600bb.jpg",
            audioUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/c0/3f/36/c03f367a-b66b-fd0a-a54c-30f8250c4410/mzaf_12768434238801682952.plus.aac.p.m4a",
            duration = 204,
            accentColor = "#06B6D4"
        ),
        Track(
            id = "1538003843",
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            albumArt = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/6c/11/d6/6c11d681-aa3a-d59e-4c2e-f77e181026ab/190295092665.jpg/600x600bb.jpg",
            audioUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/59/dc/4d/59dc4dda-93ff-8f1c-c536-f005f6ea6af5/mzaf_3066686759813252385.plus.aac.p.m4a",
            duration = 203,
            accentColor = "#EC4899"
        )
    )

    fun getTrendingTracks(): Flow<List<Track>> = flow {
        // Emit fallback static hits first for instant UI.
        emit(fallbackHits)
        // Primary source: JioSaavn editorial chart playlist.
        try {
            val jioTracks = getJioSaavnTrending()
            if (jioTracks.isNotEmpty()) {
                emit(jioTracks)
                return@flow
            }
        } catch (e: Exception) {
            Log.e(TAG, "JioSaavn Trending Error: ${e.message}")
        }
        // Secondary source: Spotify daily charts.
        try {
            val entries = api.getTrending().feed?.entry ?: emptyList()
            val mapped = entries.mapIndexedNotNull { index, entry ->
                val audio = entry.audioUrl
                if (audio.isNullOrBlank()) null
                else entry.toTrack(audio, index)
            }
            if (mapped.isNotEmpty()) emit(mapped)
        } catch (e: Exception) {
            Log.e(TAG, "Spotify Trending Error: ${e.message}")
        }
        // Tertiary source: YouTube Music top songs.
        try {
            val yt = YouTubeMusicApi.search("top songs this week", limit = 30)
            if (yt.isNotEmpty()) emit(yt)
        } catch (e: Exception) {
            Log.e(TAG, "YouTubeMusic Trending Error: ${e.message}")
        }
    }

    fun searchTracks(query: String): Flow<List<Track>> = flow {
        try {
            val results = api.searchTracks(query).results
            emit(results.mapIndexedNotNull { index, r ->
                val preview = r.previewUrl
                if (preview.isNullOrBlank()) null
                else r.toTrack(preview, index)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Search Error: ${e.message}")
            emit(emptyList())
        }
    }

    /**
     * Search the JioSaavn catalog directly (existing JioSaavn implementation,
     * reused as a first-class source). Results play through the existing
     * [resolveFullStream] JioSaavn flow.
     */
    suspend fun searchJioSaavn(query: String, limit: Int = 15): List<Track> = try {
        jioSaavn.searchSongs(query = query, limit = limit).results.mapNotNull { jioSongToTrack(it) }
    } catch (e: Exception) {
        Log.e(TAG, "JioSaavn search Error: ${e.message}")
        emptyList()
    }

    data class FeaturedPlaylist(
        val id: String,
        val title: String,
        val subtitle: String,
        val image: String,
        val token: String
    )

    data class BrowsePlaylist(
        val token: String,
        val title: String,
        val subtitle: String,
        val image: String,
        val tracks: List<Track>
    )

    /** Editorial playlists from JioSaavn for Home's browse row. */
    suspend fun getFeaturedPlaylists(): List<FeaturedPlaylist> = try {
        jioSaavn.getFeaturedPlaylists().data.mapNotNull { fp ->
            val token = fp.permaUrl?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            FeaturedPlaylist(
                id = fp.id,
                title = fp.title,
                subtitle = fp.subtitle ?: "Playlist",
                image = JioSaavnCrypto.hiResArt(fp.image),
                token = token
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Featured playlists Error: ${e.message}")
        emptyList()
    }

    /** Songs of an editorial playlist (browsed on Home, not saved to the library). */
    suspend fun getPlaylistSongs(token: String, image: String): BrowsePlaylist? = try {
        val resp = jioSaavn.getPlaylistByToken(token = token)
        val tracks = resp.songs.mapNotNull { jioSongToTrack(it) }
        if (tracks.isEmpty()) null
        else BrowsePlaylist(
            token = token,
            title = resp.title ?: "Playlist",
            subtitle = "JioSaavn • ${tracks.size} songs",
            image = image,
            tracks = tracks
        )
    } catch (e: Exception) {
        Log.e(TAG, "Playlist songs Error: ${e.message}")
        null
    }

    /**
     * Home's Trending Songs:
     * Primary: Spotify's official daily trending charts ("Today's Top Hits" / "Top 50 Global").
     * Fallback: YouTube Music top songs or JioSaavn chartbusters based on source preference.
     */
    suspend fun getTrendingTracks(setting: SourceSetting = SourceSetting.AUTO): List<Track> {
        // 1. Attempt to fetch Spotify's premier daily chart: "Today's Top Hits" (37i9dQZF1DXcBWIGoYBM5M)
        try {
            val spotifyResult = SpotifyPlaylistImporter.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")
            if (spotifyResult.tracks.isNotEmpty()) {
                Log.d(TAG, "Fetched ${spotifyResult.tracks.size} trending tracks from Spotify Today's Top Hits")
                return spotifyResult.tracks
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spotify Today's Top Hits fetch failed: ${e.message}")
        }

        // 2. Secondary Spotify attempt: "Top 50 - Global" (37i9dQZEVXbMDoHDwVN2tF)
        try {
            val globalResult = SpotifyPlaylistImporter.fetchPlaylist("37i9dQZEVXbMDoHDwVN2tF")
            if (globalResult.tracks.isNotEmpty()) {
                Log.d(TAG, "Fetched ${globalResult.tracks.size} trending tracks from Spotify Top 50 Global")
                return globalResult.tracks
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spotify Top 50 Global fetch failed: ${e.message}")
        }

        // 3. Fallback based on Music Source setting
        return when (setting) {
            SourceSetting.YOUTUBE_MUSIC -> {
                try {
                    YouTubeMusicApi.search("top songs this week", limit = 30)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            SourceSetting.JIOSAAVN -> getJioSaavnTrending()
            SourceSetting.AUTO -> {
                try {
                    val yt = YouTubeMusicApi.search("top songs this week", limit = 25)
                    if (yt.isNotEmpty()) yt else getJioSaavnTrending()
                } catch (e: Exception) {
                    getJioSaavnTrending()
                }
            }
        }.ifEmpty { fallbackHits }
    }

    suspend fun getTrendingBySource(setting: SourceSetting): List<Track> =
        getTrendingTracks(setting)

    suspend fun getJioSaavnTrending(): List<Track> {
        val featured = getFeaturedPlaylists()
        val chartPlaylist = featured.firstOrNull { it.title.contains("chart", ignoreCase = true) }
            ?: featured.firstOrNull()
            ?: return emptyList()
        return getPlaylistSongs(chartPlaylist.token, chartPlaylist.image)
            ?.tracks?.take(15)
            ?: emptyList()
    }

    private fun jioSongToTrack(s: com.aesthetic.spotify.data.network.JioSong): Track? {
        val info = s.moreInfo ?: return null
        val enc = info.encryptedMediaUrl ?: return null
        return Track(
            id = s.id,
            title = decodeHtmlEntities(s.title),
            artist = decodeHtmlEntities(
                s.subtitle
                    ?.substringBefore(" - ")?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "Unknown Artist"
            ),
            album = decodeHtmlEntities(info.album ?: "JioSaavn"),
            albumArt = JioSaavnCrypto.hiResArt(s.image),
            audioUrl = "", // resolved at play time via [resolveFullStream]
            duration = info.duration?.toIntOrNull() ?: 0,
            source = MusicSource.JIOSAAVN
        )
    }

    /** JioSaavn returns XML-escaped text ("&quot;Brahmastra&quot;") — unescape it. */
    private fun decodeHtmlEntities(text: String): String = try {
        android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    } catch (e: Exception) {
        text
    }

    /**
     * Multi-source search for the Music Source setting.
     * AUTO runs JioSaavn and YouTube Music in parallel, merges and de-duplicates
     * (same normalized title+artist keeps the JioSaavn entry — official upload).
     */
    suspend fun searchBySource(query: String, setting: SourceSetting): List<Track> =
        when (setting) {
            SourceSetting.JIOSAAVN -> searchJioSaavn(query)
            SourceSetting.YOUTUBE_MUSIC -> YouTubeMusicApi.search(query)
            SourceSetting.AUTO -> coroutineScope {
                val jioDeferred = async { searchJioSaavn(query) }
                val ytDeferred = async { YouTubeMusicApi.search(query) }
                dedupe(jioDeferred.await() + ytDeferred.await())
            }
        }

    /** Collapse tracks with the same normalized title+primary artist. */
    private fun dedupe(tracks: List<Track>): List<Track> {
        val seen = HashSet<String>()
        return tracks.filter { t ->
            val key = normalizeKey(t)
            seen.add(key)
        }
    }

    private fun normalizeKey(t: Track): String {
        val cleanTitle = t.title
            .replace(Regex("\\((feat|ft|with)\\..*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(.*?)]"), "")
            .trim()
            .lowercase()
        val primaryArtist = t.artist.split(",")[0].trim().lowercase()
        return "$cleanTitle|$primaryArtist"
    }

    suspend fun getStreamUrl(track: Track): String = track.audioUrl

    /**
     * Resolve a full-length stream for a catalog track.
     * Primary: JioSaavn (official full songs, up to 320kbps) — matched by
     * duration when the catalog knows it. Fallback: SoundCloud full uploads.
     * Returns null on failure — caller falls back to the 30s preview.
     */
    suspend fun resolveFullStream(track: Track): String? {
        // JioSaavn-sourced tracks carry their exact song id — resolve directly
        // (precise, no search/duration matching to fail).
        if (track.source == MusicSource.JIOSAAVN && !track.id.startsWith("spotify_")) {
            resolveViaJioSaavnId(track.id)?.let { return it }
        }
        return resolveViaJioSaavn(track)
            ?: resolveViaYouTubeMusic(track)
            ?: resolveViaSoundCloud(track)
    }

    private suspend fun resolveViaYouTubeMusic(track: Track): String? {
        return try {
            val primaryArtist = track.artist.substringBefore(",").trim()
            val results = YouTubeMusicApi.search("${track.title} $primaryArtist", limit = 5)
            val best = results.firstOrNull() ?: return null
            val videoId = best.videoId ?: best.id
            YouTubeMusicApi.resolveStreamUrl(videoId)
        } catch (e: Exception) {
            Log.e(TAG, "YouTubeMusic resolve Error: ${e.message}")
            null
        }
    }


    /** Exact resolution by JioSaavn song id (used for JIOSAAVN-source tracks). */
    private suspend fun resolveViaJioSaavnId(id: String): String? {
        return try {
            // The details endpoint occasionally rate-limits; one quick retry.
            var song = jioSaavn.getSongDetails(pids = id)[id]
            if (song == null) {
                kotlinx.coroutines.delay(400)
                song = jioSaavn.getSongDetails(pids = id)[id]
            }
            val info = song?.moreInfo ?: return null
            val enc = info.encryptedMediaUrl ?: return null
            JioSaavnCrypto.toStreamUrl(enc, info.has320 == "true")
        } catch (e: Exception) {
            Log.e(TAG, "JioSaavn by-id Error: ${e.message}")
            null
        }
    }

    private suspend fun resolveViaJioSaavn(track: Track): String? {
        return try {
            // Query with the primary artist only — full multi-artist strings
            // produce garbage search results.
            val primaryArtist = track.artist.substringBefore(",").trim()
            val resp = jioSaavn.searchSongs(query = "${track.title} $primaryArtist")
            val expected = track.duration
            val best = resp.results.firstOrNull { s ->
                val dur = s.moreInfo?.duration?.toIntOrNull()
                dur != null && expected > 0 && kotlin.math.abs(dur - expected) <= 20
            } ?: if (expected <= 0) resp.results.firstOrNull() else null

            val info = best?.moreInfo ?: return null
            val enc = info.encryptedMediaUrl ?: return null
            JioSaavnCrypto.toStreamUrl(enc, info.has320 == "true")
        } catch (e: Exception) {
            Log.e(TAG, "JioSaavn Error: ${e.message}")
            null
        }
    }

    /** Fallback: SoundCloud "policy=ALLOW" full uploads (may be remixes). */
    private suspend fun resolveViaSoundCloud(track: Track): String? {
        return try {
            val response = scCall { clientId ->
                soundCloud.searchTracks("${track.title} ${track.artist}", clientId)
            }
            val best = pickFullVersion(response, track) ?: return null
            val progressive = best.media?.transcodings?.firstOrNull {
                it.format?.protocol == "progressive" && it.url != null
            } ?: return null

            scCall { clientId ->
                soundCloud.resolveTranscoding(progressive.url!!, clientId)
            }.url
        } catch (e: Exception) {
            Log.e(TAG, "Resolve Error: ${e.message}")
            null
        }
    }

    /** Run a SoundCloud call; on expired client_id (401/403) refresh it and retry once. */
    private suspend fun <T> scCall(block: suspend (String) -> T): T {
        return try {
            block(ClientIdProvider.get())
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                block(ClientIdProvider.refresh())
            } else throw e
        }
    }

    private fun pickFullVersion(
        response: SCSearchResponse,
        track: Track
    ): com.aesthetic.spotify.data.network.SCTrack? {
        val candidates = response.collection.filter { it.policy == "ALLOW" }
        if (candidates.isEmpty()) return null

        val expectedMs = track.duration * 1000L
        data class Scored(
            val t: com.aesthetic.spotify.data.network.SCTrack,
            val durationCloseness: Long,
            val titleMatch: Boolean
        )

        val scored = candidates.map { c ->
            Scored(
                t = c,
                durationCloseness = if (expectedMs > 0) kotlin.math.abs(c.duration - expectedMs) else 0L,
                titleMatch = c.title.contains(track.title, ignoreCase = true)
            )
        }

        // Prefer tracks inside a plausible duration window (0.6x–1.5x of the
        // real song), then exact-title matches, popular uploads, closest duration.
        val inWindow = if (expectedMs > 0) {
            val lo = expectedMs * 0.6
            val hi = expectedMs * 1.5
            scored.filter { it.t.duration >= lo && it.t.duration <= hi }
        } else emptyList()

        return (inWindow.ifEmpty { scored })
            .sortedWith(
                compareByDescending<Scored> { it.titleMatch }
                    .thenByDescending { it.t.likesCount }
                    .thenBy { it.durationCloseness }
            )
            .firstOrNull()?.t
    }

    /**
     * Musixmatch and LRCLIB race in parallel. Musixmatch gets a short window
     * to claim with synced lyrics; LRCLIB (fast, keyless) covers everything
     * else, so the lyrics pane is never stuck waiting on a blocked source.
     */
    suspend fun getLyrics(track: Track): List<LyricLine> = coroutineScope {
        val lrcDeferred = async { runCatching { fetchFromLrcLib(track) }.getOrDefault(emptyList()) }
        val mxDeferred = async { MusixmatchClient.getLyrics(track.title, track.artist) }

        // Small head start for Musixmatch's synced lyrics.
        val mxEarly = withTimeoutOrNull(2_500) { mxDeferred.await() }
        val lrc = lrcDeferred.await()

        when {
            mxEarly?.synced == true && mxEarly.lines.isNotEmpty() -> mxEarly.lines
            lrc.isNotEmpty() -> lrc
            mxEarly?.lines?.isNotEmpty() == true -> mxEarly.lines
            else -> {
                // Nothing yet — give Musixmatch the remainder of its timeouts.
                val mxLate = runCatching { mxDeferred.await() }.getOrNull()
                mxLate?.lines?.takeIf { it.isNotEmpty() } ?: lrc
            }
        }
    }

    private suspend fun fetchFromLrcLib(track: Track): List<LyricLine> {
        val fromSearch = lyricsApi.searchLyrics(
            trackName = track.title,
            artistName = track.artist
        ).firstNotNullOfOrNull { pickLyrics(it, track) }
        if (!fromSearch.isNullOrEmpty()) return fromSearch

        return lyricsApi.searchLyricsFuzzy(q = "${track.title} ${track.artist}")
            .firstNotNullOfOrNull { pickLyrics(it, track) }
            ?: emptyList()
    }

    private fun pickLyrics(r: com.aesthetic.spotify.data.network.LrcLibResult, track: Track): List<LyricLine>? {
        return r.syncedLyrics?.let { parseLrc(it) }
            ?: r.plainLyrics?.takeIf { it.isNotBlank() }?.let { parsePlain(it, track.duration) }
    }

    fun getExplorePlaylists(): Flow<List<Playlist>> = flow { emit(emptyList()) }

    private fun com.aesthetic.spotify.data.network.ITunesResult.toTrack(audio: String, index: Int) = Track(
        id = trackId.toString(),
        title = trackName,
        artist = artistName,
        album = collectionName ?: "Single",
        albumArt = hiRes(artworkUrl100),
        audioUrl = audio,
        duration = ((trackTimeMillis ?: 0) / 1000).toInt(),
        accentColor = ACCENTS[index % ACCENTS.size]
    )

    private fun com.aesthetic.spotify.data.network.ITunesRssEntry.toTrack(audio: String, index: Int) = Track(
        id = audio.substringAfterLast('/').take(24).ifBlank { name?.label ?: "rss$index" },
        title = name?.label ?: "Unknown",
        artist = artist?.label ?: "Unknown Artist",
        album = collection?.name?.label ?: "Top Charts",
        albumArt = hiRes(images?.lastOrNull()?.label),
        audioUrl = audio,
        duration = 0, // real song length unknown from RSS; resolver uses title matching
        accentColor = ACCENTS[index % ACCENTS.size]
    )

    /** Parse "[mm:ss.xx] line" LRC text into timed lines (supports repeated timestamps). */
    private fun parseLrc(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""").let { ts ->
            lrc.lines().forEach { raw ->
                val stamps = ts.findAll(raw).toList()
                if (stamps.isEmpty()) return@forEach
                val text = raw.substring(stamps.last().range.last + 1).trim()
                    .removePrefix("]").trim()
                stamps.forEach { m ->
                    val min = m.groupValues[1].toIntOrNull() ?: 0
                    val sec = m.groupValues[2].toIntOrNull() ?: 0
                    val frac = m.groupValues[3].padLength(3).toIntOrNull() ?: 0
                    val time = min * 60f + sec + frac / 1000f
                    if (text.isNotBlank()) {
                        out += LyricLine(id = "$time", time = time, text = text)
                    }
                }
            }
        }
        return out.sortedBy { it.time }
    }

    /** No sync data available: spread plain lines evenly across the track duration. */
    private fun parsePlain(plain: String, durationSec: Int): List<LyricLine> {
        val lines = plain.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val total = if (durationSec > 0) durationSec.toFloat() else lines.size * 3f
        val step = total / lines.size
        return lines.mapIndexed { i, text ->
            LyricLine(id = "$i", time = i * step, text = text)
        }
    }
}

private fun String.padLength(len: Int): String =
    if (length >= len) substring(0, len) else padEnd(len, '0')
