package com.aesthetic.spotify.data.lyrics

/**
 * Turns catalogue metadata into queries a lyrics database can actually match.
 *
 * YouTube Music search rows come back looking like real songs but carrying the
 * video's packaging rather than the release's metadata:
 *
 *  - a bracketed attribution — `Kesariya (From "Brahmastra")`
 *  - an edit label — `(Lofi Flip)`, `(Official Video)`, `(Dance Mix)`, `(Synthwave)`
 *  - a pipe-separated upload title — `Kesariya Karaoke| Low Scale| Brahmastra| …`
 *  - a **channel** where the artist belongs — `T-Series`, `SET India`
 *  - every credited artist joined into one string —
 *    `Pritam, Shashwat Singh, Antara Mitra & Arijit Singh`
 *
 * LRCLIB matches `track_name` / `artist_name` against its own catalogue, so
 * those rows returned nothing. Measured against the live API, `Kesariya` by
 * `Arijit Singh & Nikhita Gandhi` and `Tum Hi Ho` by `T-Series` both returned
 * **0 results**, while the same songs with normalised metadata returned 20
 * each. That is the whole reason YouTube Music tracks showed an empty lyrics
 * pane while JioSaavn tracks — whose metadata is already release-clean — worked.
 *
 * Every function here is pure, so the matching rules are unit-tested without
 * touching the network.
 */
object LyricQuery {

    /**
     * One query to try. [artist] is empty for the title-only fallback, which
     * LRCLIB accepts (it then matches on title alone).
     */
    data class Candidate(val title: String, val artist: String)

    /** `(…)`, `[…]`, `{…}` — one level, non-nested. */
    private val bracketed = Regex("""[(\[{][^)\]}]*[])}]""")

    /** YouTube separates upload-title segments with a pipe; the song is first. */
    private val pipe = Regex("""\s*[|｜]\s*""")

    /** `Artist - Topic` is YouTube's auto-generated channel for a real artist. */
    private val topicSuffix = Regex("""(?i)\s*-\s*topic$""")

    /**
     * Artist separators, applied only when whitespace-delimited for `&` and
     * `/` so band names such as `AC/DC` and `Simon & Garfunkel` survive. This
     * is why [candidates] tries the raw artist *before* the split one — the
     * split is a fallback for YouTube Music's joined credit strings, not a
     * rewrite of the artist.
     */
    private val artistSeparator = Regex(
        """\s*,\s*|\s+&\s+|\s+/\s+|\s*;\s*|\s+\b(?:feat|ft|with|vs)\b\.?\s+|\s+\bx\b\s+""",
        RegexOption.IGNORE_CASE
    )

    private val whitespace = Regex("""\s+""")
    private val edgeJunk = Regex("""^[\s\-–—·•]+|[\s\-–—·•]+$""")
    private val nonWord = Regex("""[^\p{L}\p{N}\s]+""")

    /** Strips the packaging YouTube wraps around a song title. */
    fun cleanTitle(raw: String): String {
        val firstSegment = pipe.split(raw).firstOrNull() ?: raw
        val withoutBrackets = bracketed.replace(firstSegment, " ")
        val collapsed = whitespace.replace(withoutBrackets, " ")
        return edgeJunk.replace(collapsed, "").trim()
    }

    /** The first credited artist, with YouTube's channel decorations removed. */
    fun primaryArtist(raw: String): String {
        val withoutTopic = topicSuffix.replace(raw, "")
        return artistSeparator.split(withoutTopic)
            .firstOrNull { it.isNotBlank() }
            ?.let { edgeJunk.replace(it, "").trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: cleanTitle(raw)
    }

    /**
     * The cascade the repository walks, most faithful first. Each step is a
     * strictly weaker match than the one before, so the first step that returns
     * anything is also the most precise query available. For already-clean
     * metadata — every JioSaavn track — step 1 *is* the original query, so this
     * is a no-op rather than a behaviour change.
     *
     * Verified against the live LRCLIB API on real YouTube Music rows: the raw
     * artist is tried first so `Simon & Garfunkel` and `AC/DC` are never
     * rewritten, and the title-only step rescues rows whose "artist" is really
     * a channel (`Tum Hi Ho` by `T-Series`: 0 results → 20).
     */
    fun candidates(title: String, artist: String): List<Candidate> {
        val cleanT = cleanTitle(title)
        if (cleanT.isEmpty()) return emptyList()

        val primaryA = primaryArtist(artist)

        return buildList {
            // 1. The artist exactly as the catalogue gave it.
            if (artist.isNotBlank()) add(Candidate(cleanT, artist.trim()))
            // 2. The first credited artist — what fixes YouTube Music's joined
            //    credit strings ("Arijit Singh & Nikhita Gandhi" → 0 results,
            //    "Arijit Singh" → 20).
            if (primaryA.isNotEmpty()) add(Candidate(cleanT, primaryA))
            // 3. Title alone. LRCLIB accepts an empty artist_name and matches
            //    on the title, the only thing left when the "artist" is a
            //    channel that appears in no lyrics catalogue.
            add(Candidate(cleanT, ""))
        }.distinct()
    }

    /**
     * Scores a database hit against the query that produced it, higher is
     * better. Needed because the title-only step can return a cover before the
     * original, and the repository previously took whichever row came first.
     */
    fun relevance(
        resultTrack: String?,
        resultArtist: String?,
        queryTitle: String,
        queryArtist: String
    ): Int {
        var score = 0
        val rt = comparable(resultTrack)
        val qt = comparable(queryTitle)
        if (rt.isNotEmpty() && qt.isNotEmpty()) {
            score += when {
                rt == qt -> 4
                rt.startsWith(qt) || qt.startsWith(rt) -> 2
                else -> 0
            }
        }
        val ra = comparable(resultArtist)
        val qa = comparable(queryArtist)
        if (ra.isNotEmpty() && qa.isNotEmpty()) {
            score += when {
                ra == qa -> 3
                sharesToken(ra, qa) -> 2
                else -> 0
            }
        }
        return score
    }

    /** Casefolded, punctuation-free, single-spaced — for comparison only. */
    private fun comparable(value: String?): String =
        whitespace.replace(nonWord.replace(value.orEmpty().lowercase(), " "), " ").trim()

    private fun sharesToken(a: String, b: String): Boolean {
        val tokens = b.split(' ').filter { it.length > 2 }.toSet()
        return a.split(' ').any { it in tokens }
    }
}
