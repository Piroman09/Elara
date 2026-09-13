package com.aesthetic.spotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aesthetic.spotify.ui.theme.AuraViolet
import com.aesthetic.spotify.ui.theme.AuraPurple

/**
 * Placeholder shown wherever a track has no usable cover — YouTube Music rows
 * that failed to resolve art, Spotify-imported tracks, anything with a blank
 * `albumArt`. A flat grey rectangle reads as a rendering bug; this reads as a
 * deliberate "no artwork" state.
 */
private val FallbackBrush = Brush.linearGradient(
    listOf(AuraPurple.copy(alpha = 0.35f), AuraViolet.copy(alpha = 0.25f))
)

/**
 * The single artwork primitive for the whole app.
 *
 * Every list row, player and card used to repeat the same
 * `AsyncImage(...).size(n).clip(RoundedCornerShape(r))` block, which meant
 * (a) five different corner radii and sizes for the same visual role and
 * (b) blank covers rendering as nothing at all. This centralises the shape, the
 * fallback and the clipping so artwork looks identical everywhere.
 *
 * @param url cover URL; blank or null renders the fallback instead of nothing.
 * @param size square edge length. Artwork is square by convention here.
 * @param cornerRadius defaults to a radius proportional to [size] so a 40dp row
 *   thumbnail and a 300dp hero image both look right without per-call tuning.
 */
@Composable
fun TrackArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    cornerRadius: Dp? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val radius = cornerRadius ?: (size?.let { it / 5 } ?: 8.dp)
    val shape = RoundedCornerShape(radius)
    val sized = if (size != null) modifier.size(size) else modifier

    Box(
        modifier = sized.clip(shape).background(FallbackBrush),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.55f),
                // Sized relative to the box either way: a fixed dp icon would be
                // a speck in the full-screen player's 300 dp hero image and
                // oversized in a 40 dp queue row.
                modifier = if (size != null) {
                    Modifier.size(size * 0.45f)
                } else {
                    Modifier.fillMaxSize(0.3f)
                }
            )
        }
    }
}
