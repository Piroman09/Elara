package com.piroman.elara.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import com.piroman.elara.data.model.Track
import com.piroman.elara.ui.theme.AuraPlayerSurface
import com.piroman.elara.ui.theme.AuraViolet
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import kotlinx.coroutines.flow.StateFlow

private val PlayerSurface = AuraPlayerSurface
private val PlayerBorder = Brush.horizontalGradient(
    listOf(AuraPurple.copy(alpha = 0.5f), AuraViolet.copy(alpha = 0.5f))
)
private val ProgressBrush = Brush.horizontalGradient(listOf(AuraPurple, AuraViolet))
private val PlayerShape = RoundedCornerShape(18.dp)

/**
 * The mini player docked above the navigation bar.
 *
 * [positionMs] / [durationMs] are passed as flows rather than plain values on
 * purpose. Resolving them here — inside the leaf that actually draws the
 * progress bar — keeps the 400 ms position tick from recomposing the scaffold
 * and every screen behind it. See [MainScaffold] for the full explanation.
 */
@Composable
fun FloatingPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    Box(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(PlayerShape)
            .background(PlayerSurface.copy(alpha = 0.95f))
            .border(1.dp, PlayerBorder, PlayerShape)
            .clickable { onExpand() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            TrackArtwork(
                url = currentTrack.albumArt,
                size = 48.dp,
                cornerRadius = 10.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.title,
                    color = AuraTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack.artist,
                    color = AuraTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AuraTextPrimary
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = AuraTextPrimary
                )
            }

            IconButton(onClick = onQueue) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = AuraTextPrimary
                )
            }
        }

        ProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

/**
 * The 3 dp progress hairline.
 *
 * Split into its own composable so that reading the position only invalidates
 * this function, and so the bar itself is drawn — not laid out — as it fills:
 * `animated.value` is read inside `drawBehind`, which subscribes the *draw*
 * scope, so a frame of the fill animation costs a redraw and no recomposition.
 */
@Composable
private fun ProgressBar(
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    modifier: Modifier = Modifier
) {
    val position by positionMs.collectAsState()
    val duration by durationMs.collectAsState()
    val target = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val animated = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(400),
        label = "fpProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind {
                val filled = size.width * animated.value.coerceIn(0f, 1f)
                if (filled > 0f) {
                    drawRect(brush = ProgressBrush, size = Size(filled, size.height))
                }
            }
    )
}
