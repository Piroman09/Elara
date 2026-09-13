package com.aesthetic.spotify.perf

import com.aesthetic.spotify.data.model.SafeGson
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.data.playlist.Playlist
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much of the cold start is Gson, measured rather than assumed?
 *
 * `MainViewModel` builds five singletons as field initializers, and seven
 * operations inside them block the main thread before the first frame. The
 * heaviest is `PlaylistManager.load()` — a `readText()` plus a `SafeGson` parse
 * of every playlist, and each playlist holds full [Track] objects.
 *
 * Deciding whether that is worth restructuring needs a number, and this machine
 * has no device and no working emulator. This supplies a **floor** for it.
 *
 * ## What this is not
 *
 * Optimistic in three ways, so treat the output as a lower bound:
 *  1. **No disk I/O.** `file.readText()` and the `SharedPreferences` reads are
 *     not included — only the parse.
 *  2. **HotSpot, not ART.** Gson is reflection-heavy and reflection is slower on
 *     Android, so the real figure is higher.
 *  3. **Warm JVM.** No class loading or JIT warm-up.
 *
 * Use it for the *shape* — how cost scales with library size, and how much of it
 * is [SafeGson] rather than Gson itself. The authoritative figures come from the
 * `StartupTiming` line the release build writes to the diagnostics log.
 *
 * Asserts correctness of the round trip, never a duration, so it cannot be
 * flaky in CI.
 */
class StartupCostBenchmarkTest {

    /** Fixture builder. A separate instance keeps [SafeGson]'s adapter cache cold. */
    private val fixtureGson = Gson()

    /** Separate again, so the plain-vs-SafeGson comparison is not pre-warmed. */
    private val plainGson = Gson()

    private val safe = SafeGson.instance

    /** Guards against the JIT eliding the timed work. Printed at the end. */
    private var sink = 0

    private fun track(i: Int) = Track(
        id = "track-$i",
        videoId = "vid$i",
        title = "Title $i",
        artist = "Artist ${i % 50}",
        artistId = "artist-${i % 50}",
        album = "Album ${i % 200}",
        albumArt = "https://example.invalid/art/$i.jpg",
        audioUrl = "https://example.invalid/audio/$i.m4a",
        duration = 180 + (i % 120),
        genre = "Genre ${i % 12}",
        energy = i % 100,
        accentColor = "#A0B0C0",
        isLiked = i % 7 == 0,
        tags = listOf("tag${i % 5}", "tag${i % 9}")
    )

    /** Shape of `playlists.json`: a list of playlists, each holding full tracks. */
    private fun libraryJson(trackCount: Int, playlistCount: Int): String {
        val perPlaylist = trackCount / playlistCount
        val data = (0 until playlistCount).map { p ->
            Playlist(
                id = "pl-$p",
                name = "Playlist $p",
                createdAt = 1_700_000_000_000L + p,
                tracks = (0 until perPlaylist).map { track(p * perPlaylist + it) }
            )
        }
        return fixtureGson.toJson(data)
    }

    /** Shape of the persisted queue: a flat list of tracks. */
    private fun queueJson(trackCount: Int): String =
        fixtureGson.toJson((0 until trackCount).map { track(it) })

    /**
     * Returns `first` (includes any one-time reflective adapter build) and the
     * median of [repeats] further runs. The median, not the mean, so a GC pause
     * on one iteration cannot drag the headline figure.
     */
    private inline fun timed(repeats: Int = 5, block: () -> Int): Pair<Long, Long> {
        val t0 = System.nanoTime()
        sink += block()
        val firstMs = (System.nanoTime() - t0) / 1_000_000

        val samples = LongArray(repeats) {
            val start = System.nanoTime()
            sink += block()
            (System.nanoTime() - start) / 1_000_000
        }
        samples.sort()
        return firstMs to samples[repeats / 2]
    }

    private fun row(size: Int, shape: String, impl: String,
                    first: Long, median: Long, kb: Int) {
        println(String.format("%-9d %-11s %-8s %-9d %-9d %-9d", size, shape, impl, first, median, kb))
    }

    @Test
    fun `report parse cost against library size`() {
        val playlistType = object : TypeToken<List<Playlist>>() {}.type
        val trackType = object : TypeToken<List<Track>>() {}.type

        println()
        println("Gson parse cost — LOWER BOUND for ART (no disk I/O, warm JVM)")
        println("'first' includes the one-time reflective adapter build; 'median' is 5 runs")
        println()
        println(String.format("%-9s %-11s %-8s %-9s %-9s %-9s",
            "tracks", "shape", "impl", "first", "median", "json KB"))

        val sizes = listOf(200, 1000, 3000)
        for (trackCount in sizes) {
            val plJson = libraryJson(trackCount, PLAYLIST_COUNT)
            val plKb = plJson.length / 1024

            val (plFirst, plMedian) = timed {
                // Explicit type: Gson's `<T> fromJson(String, Type)` cannot infer
                // T from a TypeToken's `type`.
                val parsed: List<Playlist>? = safe.fromJson(plJson, playlistType)
                parsed?.size ?: 0
            }
            row(trackCount, "playlists", "safe", plFirst, plMedian, plKb)

            val qJson = queueJson(trackCount)
            val (qFirst, qMedian) = timed {
                val parsed: List<Track>? = safe.fromJson(qJson, trackType)
                parsed?.size ?: 0
            }
            row(trackCount, "queue", "safe", qFirst, qMedian, qJson.length / 1024)

            // Save path: serialise the parsed library back out.
            val (sFirst, sMedian) = timed {
                val parsed: List<Playlist>? = safe.fromJson(plJson, playlistType)
                safe.toJson(parsed).length
            }
            row(trackCount, "parse+save", "safe", sFirst, sMedian, plKb)
        }

        // How much of that is SafeGson rather than Gson? Its Track adapter goes
        // JsonElement -> sanitized(), i.e. it parses every track twice.
        println()
        println("SafeGson overhead — same JSON, plain Gson() vs SafeGson.instance")
        println(String.format("%-9s %-11s %-8s %-9s %-9s %-9s",
            "tracks", "shape", "impl", "first", "median", "json KB"))
        val overheadTrackCount = sizes.last()
        val overheadJson = libraryJson(overheadTrackCount, PLAYLIST_COUNT)
        val overheadKb = overheadJson.length / 1024
        val (pFirst, pMedian) = timed {
            val parsed: List<Playlist>? = plainGson.fromJson(overheadJson, playlistType)
            parsed?.size ?: 0
        }
        row(overheadTrackCount, "playlists", "plain", pFirst, pMedian, overheadKb)
        val (sfFirst, sfMedian) = timed {
            val parsed: List<Playlist>? = safe.fromJson(overheadJson, playlistType)
            parsed?.size ?: 0
        }
        row(overheadTrackCount, "playlists", "safe", sfFirst, sfMedian, overheadKb)

        println()
        println("(checksum $sink — non-zero means the work was not optimised away)")

        // Correctness, so this is a real test and not only a printer.
        val small = libraryJson(200, PLAYLIST_COUNT)
        val parsed: List<Playlist> = safe.fromJson(small, playlistType)
        assertEquals(PLAYLIST_COUNT, parsed.size)
        assertEquals(200 / PLAYLIST_COUNT, parsed.first().tracks.size)
        assertEquals("track-0", parsed.first().tracks.first().id)
        assertEquals(listOf("tag0", "tag0"), parsed.first().tracks.first().tags)
        assertTrue(sink > 0)
    }

    private companion object {
        const val PLAYLIST_COUNT = 20
    }
}
