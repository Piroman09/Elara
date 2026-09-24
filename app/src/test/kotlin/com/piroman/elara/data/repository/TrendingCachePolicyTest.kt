package com.piroman.elara.data.repository

import com.piroman.elara.data.model.MusicSource
import com.piroman.elara.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule deciding what Home's trending row shows after a fetch, tested
 * directly because its failure is invisible: an empty fetch written into state
 * does not throw anywhere — the row just quietly disappears until the next
 * successful fetch.
 */
class TrendingCachePolicyTest {

    private fun track(id: String) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumArt = "",
        audioUrl = "",
        duration = 200,
        source = MusicSource.JIOSAAVN
    )

    private val fresh = listOf(track("fresh"))
    private val cached = listOf(track("cached"))

    @Test
    fun `a non-empty fetch wins over the cache`() {
        // The cache covers the fetch's failure, never outranks it — otherwise
        // the row would freeze on day-old data while the network works.
        assertEquals(
            TrendingCachePolicy.Outcome.Fetched(fresh),
            TrendingCachePolicy.resolve(fetched = fresh, cached = cached)
        )
    }

    @Test
    fun `an empty fetch falls back to the cache`() {
        assertEquals(
            TrendingCachePolicy.Outcome.Stale(cached),
            TrendingCachePolicy.resolve(fetched = emptyList(), cached = cached)
        )
    }

    @Test
    fun `an empty fetch with no cache leaves the row empty`() {
        assertEquals(
            TrendingCachePolicy.Outcome.None,
            TrendingCachePolicy.resolve(fetched = emptyList(), cached = null)
        )
    }

    @Test
    fun `an empty cached list is not a fallback`() {
        // Gson can hand back an empty (or null) list from a corrupt file; that
        // must read as "no cache", not as "serve zero songs".
        assertEquals(
            TrendingCachePolicy.Outcome.None,
            TrendingCachePolicy.resolve(fetched = emptyList(), cached = emptyList())
        )
    }
}
