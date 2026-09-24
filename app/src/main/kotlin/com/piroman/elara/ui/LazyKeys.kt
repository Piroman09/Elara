package com.piroman.elara.ui

/**
 * Stable, collision-free keys for lazy lists whose items may repeat.
 *
 * The rule in this app is that a lazy-list key is an item's **identity plus an
 * occurrence suffix** — never the bare id and never the bare index:
 *
 * - The bare id crashes. A track list here is allowed to hold the same song
 *   more than once: a Spotify playlist can contain a song twice, `playNextInQueue`
 *   inserts a duplicate at `queueIndex + 1` with no de-duplication, and the Home
 *   shelves are fed by unfiltered API lists. Compose throws
 *   "Key ... was already used" and the screen dies.
 * - The bare index breaks animation. Baking the position into the key means
 *   every row looks brand new after a reorder, so `animateItemPlacement` has
 *   nothing to animate between and the list snaps instead of sliding.
 *
 * This used to be hand-rolled in four places and one copy had already drifted
 * back to the bare id, so the loop lives here once and is covered by
 * [com.piroman.elara.ui.LazyKeysTest].
 *
 * Pure Kotlin on purpose: no Android or Compose types, so the behaviour is
 * exercised directly by JVM tests. Same reasoning as `QueueOrder` and
 * `PlaylistOrdering`.
 *
 * Usage — pair each item with its key, then key the lazy item on the second
 * element:
 *
 * ```
 * val keyed = remember(tracks) { LazyKeys.withOccurrence(tracks) { it.id } }
 * LazyRow {
 *     items(keyed, key = { it.second }) { (track, _) -> TrackRow(track) }
 * }
 * ```
 */
internal object LazyKeys {

    /**
     * Pairs every item in [items] with `"<id>#<n>"`, where `n` is how many
     * earlier items in the list returned the same id.
     *
     * The first occurrence of an id keeps the suffix `#0`, so a list with no
     * repeats produces exactly the same keys it would have with a bare id. The
     * suffix only disambiguates once a genuine repeat shows up.
     *
     * Order is preserved and the function is stable: equal ids get increasing
     * suffixes in list order, so reordering the list reorders the keys rather
     * than reshuffling them arbitrarily.
     */
    fun <T> withOccurrence(items: List<T>, id: (T) -> String): List<Pair<T, String>> {
        val seen = HashMap<String, Int>(items.size)
        return items.map { item ->
            val raw = id(item)
            val occurrence = seen.getOrDefault(raw, 0)
            seen[raw] = occurrence + 1
            item to "$raw#$occurrence"
        }
    }
}
