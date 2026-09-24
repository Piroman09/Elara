package com.piroman.elara.data.repository

import com.piroman.elara.data.model.Track

/**
 * The rules that decide which YouTube Music search results are fit to put in a
 * Home row.
 *
 * Separate from [MusicRepository] and pure, for the same reason
 * `HomeRefreshPolicy` is: every way of getting these wrong is silent. Nothing
 * throws when a 50-minute DJ set is rendered as a song, or when the same track
 * appears in two rows on one screen — the user just sees something wrong.
 */
internal object YouTubeRowPolicy {

    /**
     * Longest entry allowed in a row, in seconds.
     *
     * YouTube's song search returns continuous mixes as songs. Measured on real
     * queries: "Bollywood Dj Non Stop Remix" at 50:23 and "Party Mashup" at
     * 30:17 in the row that shipped, and — when scouting replacement queries —
     * "The Gym Beats Vol.4-NONSTOP-MEGAMIX" at 58:42 and a 47:17 gym mix. Fifteen
     * minutes leaves room for a long album track while excluding anything that is
     * obviously one continuous mix.
     */
    const val MAX_TRACK_SECONDS = 15 * 60

    /**
     * Whether a search result is a plausible song.
     *
     * A duration of `0` means YouTube's subtitle could not be parsed, and those
     * are **kept**. Dropping them would be stricter, but it would turn any change
     * in the response shape into a silently *empty* row, and an empty row is
     * indistinguishable from a broken one. Keeping them degrades to "no duration
     * known" instead — and Home's cards do not display a duration anyway.
     */
    fun isPresentable(track: Track): Boolean = track.duration <= MAX_TRACK_SECONDS

    /** [tracks] without the entries that are not plausible songs. */
    fun presentable(tracks: List<Track>): List<Track> = tracks.filter(::isPresentable)

    /**
     * [candidates] minus anything already shown in [alreadyShown].
     *
     * The two rows are separate live searches and YouTube's own results overlap —
     * the pair this replaced shared 5 songs out of 25. The same song in two rows
     * on one screen reads as a bug, so the second row drops what the first
     * already shows. Identity is [Track.id], which for a YouTube track is its
     * `videoId`, and `search` has already de-duplicated within a single call.
     */
    fun withoutDuplicates(alreadyShown: List<Track>, candidates: List<Track>): List<Track> {
        val seen = alreadyShown.mapTo(HashSet(alreadyShown.size)) { it.id }
        return candidates.filterNot { it.id in seen }
    }
}
