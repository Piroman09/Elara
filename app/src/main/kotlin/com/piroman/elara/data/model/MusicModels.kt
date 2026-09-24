package com.piroman.elara.data.model

import androidx.compose.runtime.Immutable

/**
 * `@Immutable` is a Compose contract, not a Kotlin one: it promises the value
 * never changes after construction, which lets the compiler let a composable
 * taking a [Track] **skip** recomposition when the reference is equal.
 *
 * Without it Compose infers the opposite. Every field here is a `val`, but
 * [tags] is a `List` — an interface, and therefore unstable — which makes the
 * whole class unstable by inference. That single inference used to defeat
 * skipping for every track row in the app (home, search, library, playlist
 * detail, queue drawer), so a download tick or a queue edit recomposed all of
 * them. Verified before adding: nothing mutates a `Track` or its `tags` in
 * place anywhere in the app, and updates always go through `copy()`.
 */
@Immutable
data class Track(
    val id: String,
    val videoId: String? = null,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String,
    val albumArt: String,
    val audioUrl: String,
    val duration: Int,
    val genre: String = "",
    val energy: Int? = null,
    val accentColor: String? = null,
    val isLiked: Boolean = false,
    val source: MusicSource = MusicSource.JIOSAAVN,
    val tags: List<String> = emptyList()
)

/**
 * Gson bypasses Kotlin's null-safety: it populates fields reflectively instead
 * of through the constructor, so a [Track] parsed from JSON that predates a
 * field — or an API response that simply omits one — ends up holding `null` in
 * a property declared non-null. That `null` escapes its declared type and
 * crashes the first caller that trusts it (`.lowercase()`, `Text(...)`, or
 * iterating `tags`).
 *
 * Every track that comes back out of Gson — the persisted queue, liked songs,
 * play history, the trending cache, playlists — must be passed through this
 * before it reaches the rest of the app.
 */
@Suppress("USELESS_ELVIS")
fun Track.sanitized(): Track = copy(
    id = id.orEmpty(),
    title = title.orEmpty(),
    artist = artist.orEmpty(),
    album = album.orEmpty(),
    albumArt = albumArt.orEmpty(),
    audioUrl = audioUrl.orEmpty(),
    genre = genre.orEmpty(),
    tags = tags.orEmpty(),
    source = source ?: MusicSource.JIOSAAVN
)

data class VibeCoords(val x: Float, val y: Float)

data class LyricLine(
    val id: String,
    val time: Float,
    val text: String
)

/** Same reasoning as [Track]: all `val`, but `tracks` is a `List`, so Compose
 *  needs the explicit promise to be able to skip playlist rows. */
@Immutable
data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val coverArt: String,
    val tracks: List<Track>
)
