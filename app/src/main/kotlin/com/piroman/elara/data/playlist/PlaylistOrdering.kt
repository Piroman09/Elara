package com.piroman.elara.data.playlist

import com.piroman.elara.data.model.Track

/**
 * How a playlist's tracks are displayed.
 *
 * Sorting is deliberately **display-only**: nothing here writes back to
 * [PlaylistManager] unless the user explicitly saves the order. Applying a sort
 * straight to storage would silently destroy a hand-arranged playlist the first
 * time someone glanced at "Title A–Z".
 */
enum class PlaylistSort(val label: String) {
    CUSTOM("Custom order"),
    TITLE("Title A–Z"),
    ARTIST("Artist A–Z"),
    DURATION("Longest first")
}

/**
 * Pure filter + ordering for the playlist screen.
 *
 * Holds no Android or Compose types so the behaviour — especially the tie-break
 * order and the case-insensitive match — is exercised directly by JVM tests.
 */
internal object PlaylistOrdering {

    /**
     * Orders [tracks] for display. Every `sortedBy` here is stable, so tracks
     * that compare equal (same artist, same duration) keep the playlist's own
     * order instead of shuffling on every recomposition.
     */
    fun apply(tracks: List<Track>, sort: PlaylistSort): List<Track> = when (sort) {
        PlaylistSort.CUSTOM -> tracks
        PlaylistSort.TITLE -> tracks.sortedBy { it.title.orEmpty().lowercase() }
        PlaylistSort.ARTIST -> tracks.sortedWith(
            compareBy({ it.artist.orEmpty().lowercase() }, { it.title.orEmpty().lowercase() })
        )
        PlaylistSort.DURATION -> tracks.sortedByDescending { it.duration }
    }

    /**
     * Case-insensitive substring match over title, artist and album. A blank
     * query matches everything rather than nothing, so an empty search box
     * shows the whole playlist.
     *
     * `orEmpty()` throughout because Gson can leave nulls in these non-null
     * fields — see `SafeGson`.
     */
    fun filter(tracks: List<Track>, query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return tracks
        return tracks.filter { track ->
            track.title.orEmpty().contains(q, ignoreCase = true) ||
                track.artist.orEmpty().contains(q, ignoreCase = true) ||
                track.album.orEmpty().contains(q, ignoreCase = true)
        }
    }
}
