package com.piroman.elara.data.backup

import androidx.compose.runtime.Immutable
import com.piroman.elara.data.history.PlayStat
import com.piroman.elara.data.model.SafeGson
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.playlist.Playlist
import com.google.gson.JsonSyntaxException

/**
 * The whole user-owned library, in one JSON document.
 *
 * **Persisted via Gson, so its field names are load-bearing** — R8 is on and
 * `-dontobfuscate` alone does not stop shrinking. A stripped field here does not
 * throw: it silently deserialises to `null`, and a restore would then quietly
 * produce an empty library. See `proguard-rules.pro` and the `requiredTypes`
 * list in `verify-release-dex.gradle`.
 *
 * [version] exists so a future format change can be detected rather than
 * mis-parsed. [appPackage] is **informational only** and is deliberately not
 * checked on import: moving a library between installs is the main reason this
 * feature exists, so refusing a file because it came from a different
 * applicationId would defeat it.
 */
@Immutable
data class ElaraBackup(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appPackage: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val playStats: List<PlayStat> = emptyList(),
    val musicSource: String? = null
) {
    companion object {
        const val CURRENT_VERSION = 1

        /** Highest version this build knows how to read. */
        private const val MAX_SUPPORTED_VERSION = 1

        fun encode(backup: ElaraBackup): String = SafeGson.instance.toJson(backup)

        /**
         * Parses a backup document.
         *
         * Returns null only when the text is not a backup at all. Throws
         * [UnsupportedVersionException] for a document from a newer build —
         * those must not be silently half-read, because the fields this build
         * does not know about would be dropped and then written back on the
         * next save.
         */
        fun decode(json: String): ElaraBackup? {
            val parsed = try {
                SafeGson.instance.fromJson(json, ElaraBackup::class.java)
            } catch (e: JsonSyntaxException) {
                return null
            } ?: return null

            if (parsed.version > MAX_SUPPORTED_VERSION) {
                throw UnsupportedVersionException(parsed.version, MAX_SUPPORTED_VERSION)
            }

            // Gson allocates through Unsafe and never runs the constructor, so a
            // key that is *absent* from the document lands as null inside a
            // property the compiler believes is non-null — the same trap
            // SafeGson exists to close for Track. A file written before a field
            // was introduced would otherwise NPE on the first `.size`, so every
            // collection is re-asserted through a nullable view.
            return ElaraBackup(
                version = parsed.version,
                exportedAt = parsed.exportedAt,
                appPackage = parsed.appPackage,
                playlists = lenient(parsed.playlists),
                likedTracks = lenient(parsed.likedTracks),
                recentTracks = lenient(parsed.recentTracks),
                playStats = lenient(parsed.playStats),
                musicSource = parsed.musicSource
            )
        }

        /**
         * Takes a nullable view of a value the compiler thinks is already
         * non-null. Passing a non-null List to a `List<T>?` parameter is legal
         * and warning-free, which `value ?: emptyList()` on the field itself is
         * not — that trips "Elvis operator always returns the left operand".
         */
        private fun <T> lenient(value: List<T>?): List<T> = value ?: emptyList()
    }

    /** True when there is genuinely nothing to restore. */
    val isEmpty: Boolean
        get() = playlists.isEmpty() && likedTracks.isEmpty() &&
            recentTracks.isEmpty() && playStats.isEmpty()
}

class UnsupportedVersionException(val found: Int, val supported: Int) :
    Exception("Backup is version $found, this build reads up to $supported")

/** What a restore actually did, for the confirmation message. */
@Immutable
data class RestoreReport(
    val playlists: Int,
    val likedTracks: Int,
    val recentTracks: Int,
    val playStats: Int,
    val source: String? = null
) {
    val summary: String
        get() = buildList {
            add("$playlists playlist${plural(playlists)}")
            add("$likedTracks liked song${plural(likedTracks)}")
            add("$recentTracks history entr${if (recentTracks == 1) "y" else "ies"}")
            if (playStats > 0) add("$playStats play count${plural(playStats)}")
        }.joinToString(", ")

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
