package com.piroman.elara.data.repository

import com.piroman.elara.data.model.Track

/**
 * What Home's trending row should show once a fetch has returned.
 *
 * Separate from [MainViewModel] and pure, for the same reason `HomeRefreshPolicy`
 * is: the failure it guards against is invisible. When a fetch comes back empty
 * and the ViewModel writes `emptyList()` into the state, nothing throws — Home
 * just loses its trending row until the next successful fetch, and the user has
 * no way to tell "no data" from "broken".
 *
 * The rules:
 *
 *  - A non-empty fetch always wins. The cache exists to cover the fetch's
 *    failure, never to outrank it — otherwise the row could freeze on day-old
 *    data while the network works.
 *  - An empty fetch falls back to the cache, however old. Stale songs the user
 *    can play beat a row that vanished; the cached entries carry full stream
 *    resolution, so they keep working.
 *  - Neither — an empty fetch on a device with no cache — is [None], and the
 *    row legitimately stays empty.
 */
internal object TrendingCachePolicy {

    /** The decision, made once so the caller cannot improvise a fourth option. */
    sealed interface Outcome {
        /** Fresh data arrived; show it and overwrite the cache with it. */
        data class Fetched(val tracks: List<Track>) : Outcome

        /** The fetch failed; serve the last known tracks instead. */
        data class Stale(val tracks: List<Track>) : Outcome

        /** Nothing fetched and nothing cached; the row is legitimately empty. */
        data object None : Outcome
    }

    /**
     * @param fetched what the network returned (already empty on a thrown error).
     * @param cached what [TrendingCache] holds from any previous day, or null.
     */
    fun resolve(fetched: List<Track>, cached: List<Track>?): Outcome = when {
        fetched.isNotEmpty() -> Outcome.Fetched(fetched)
        !cached.isNullOrEmpty() -> Outcome.Stale(cached)
        else -> Outcome.None
    }
}
