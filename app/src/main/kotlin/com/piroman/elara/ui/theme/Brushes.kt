package com.piroman.elara.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The three gradients used for playlist covers.
 *
 * These were spelled out as raw colour pairs in both `LibraryScreen` (the
 * pinned cards) and `PlaylistDetailScreen` (the header), so the "Liked Songs"
 * tile and the "Liked Songs" header were two independent copies of the same
 * gradient — one edit away from drifting apart. Hoisted so a playlist looks the
 * same everywhere it appears.
 *
 * Liked Songs used to run `#450EFF → #ADC0FF`, a blue that belonged to no part
 * of the app. It is now a deep violet into lavender, which keeps it distinct
 * from the generic [PlaylistBrush] while staying in the theme's hue.
 */
val LikedSongsBrush = Brush.linearGradient(
    listOf(Color(0xFF6D28D9), AuraLavender)
)

/**
 * Downloads keeps its emerald on purpose: green is the download affordance
 * everywhere else in the app (the tick on a saved row, [AuraDownload]), so
 * re-tinting this tile violet would break the link between the two.
 */
val DownloadsBrush = Brush.linearGradient(
    listOf(Color(0xFF0E7490), AuraDownload)
)

/** The signature gradient. Was violet → pink; now violet → light violet. */
val PlaylistBrush = Brush.linearGradient(
    listOf(AuraPurple, AuraViolet)
)
