package com.piroman.elara.data.download

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Renders a byte count as a short human-readable size — `"512 B"`, `"840 KB"`,
 * `"1.5 MB"`, `"1.24 GB"`.
 *
 * **Binary units (1024), not decimal.** Android's own `Formatter.formatFileSize`
 * has used both conventions across releases, so there is no single right answer
 * to match; 1024 is what most file managers show and it is at least
 * self-consistent, which matters more here than agreeing with Settings to the
 * last digit. The choice is written down so nobody "fixes" it later.
 *
 * **Locale.US is deliberate.** The default locale would render `1.5 MB` as
 * `1,5 MB` in much of Europe, which makes the output untestable and looks like a
 * typo next to the unit. A byte count is not prose.
 *
 * Pure and Android-free so it can be unit-tested — the same reason
 * `QueueOrder`, `VolumeMix` and `ListeningStatsFormat` exist. Nothing here
 * touches the filesystem; the caller supplies the byte count.
 */
internal object DownloadSizeFormat {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    /** Unit thresholds as byte counts, for the branch tests to share. */
    internal const val ONE_KB = 1024L
    internal const val ONE_MB = 1024L * 1024
    internal const val ONE_GB = 1024L * 1024 * 1024

    /**
     * `0` and any negative value render as `"0 B"`: an empty download list and a
     * nonsensical size should both read as "nothing", never as a negative or
     * blank label.
     */
    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return when {
            bytes < ONE_KB -> "$bytes B"
            bytes < ONE_MB -> kilobytes(bytes)
            bytes < ONE_GB -> megabytes(bytes)
            else -> decimal(bytes / GB, 2) + " GB"
        }
    }

    /**
     * Whole KB, rounded — a byte-precise `"839.4 KB"` is noise at this size.
     *
     * Rounding is why this needs its own function: the top of the range rounds
     * *up* to 1024, so a file one byte short of a megabyte came out as
     * `"1024 KB"`. Arithmetically defensible, but it reads as a bug and the whole
     * point of the label is to be glanceable, so the unit promotes instead.
     */
    private fun kilobytes(bytes: Long): String {
        val kb = (bytes / KB).roundToLong()
        return if (kb < 1024) "$kb KB" else decimal(bytes / MB, 1) + " MB"
    }

    /**
     * One decimal of MB. The same promotion applies at the top of the range,
     * where `1023.999 MB` rounds to `1024.0` and would otherwise print as
     * `"1024.0 MB"` rather than `"1.00 GB"`.
     *
     * Comparing in tenths avoids a floating-point equality check against 1024.0.
     */
    private fun megabytes(bytes: Long): String {
        val tenths = (bytes / MB * 10).roundToLong()
        return if (tenths < TENTHS_PER_1024) decimal(tenths / 10.0, 1) + " MB"
        else decimal(bytes / GB, 2) + " GB"
    }

    /** 1024.0 expressed in tenths, for [megabytes]. */
    private const val TENTHS_PER_1024 = 10_240L

    private fun decimal(value: Double, places: Int): String =
        String.format(Locale.US, "%.${places}f", value)
}
