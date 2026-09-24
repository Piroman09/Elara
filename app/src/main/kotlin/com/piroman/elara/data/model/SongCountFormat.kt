package com.piroman.elara.data.model

/**
 * `"1 song"` / `"2 songs"` — the plural rule for a song count.
 *
 * This exists because the app has already shipped "1 hours" and "1 minutes"
 * from two hand-rolled copies of the same duration label, and the identical
 * mistake was sitting in the playlist header: `songCountLabel` appended
 * `" songs"` unconditionally, so a one-song playlist read **"1 songs"**. Two
 * screens need this label now (the playlist header and Downloads), which is the
 * point at which the repo's own history says to make it one function rather than
 * two copies.
 *
 * Pure and Android-free so it can be unit-tested. Note that **zero takes the
 * plural** — "0 songs" is correct English, and `count == 1` is the only special
 * case, not `count <= 1`.
 */
internal object SongCountFormat {

    fun songs(count: Int): String = when (count) {
        1 -> "1 song"
        else -> "$count songs"
    }
}
