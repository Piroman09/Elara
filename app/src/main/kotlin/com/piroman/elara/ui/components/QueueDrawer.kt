package com.piroman.elara.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.piroman.elara.data.model.Track
import com.piroman.elara.playback.RepeatMode
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCardStrong
import com.piroman.elara.ui.theme.AuraCardSubtle
import com.piroman.elara.ui.theme.AuraContainer
import com.piroman.elara.ui.theme.AuraViolet
import com.piroman.elara.ui.theme.AuraError
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.Motion
import kotlin.math.roundToInt

private val RowHeight = 60.dp
private val RowSpacing = 8.dp
private val RowStep = RowHeight + RowSpacing
private val CardColor = AuraContainer

/**
 * Slide-over queue panel: what's playing, what's next (drag to reorder), and
 * suggestions. The queue list and the suggestions share one LazyColumn so both
 * actually get laid out — two fillMaxSize lists inside a Column collapse the
 * second one to zero height.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueDrawer(
    viewModel: MainViewModel,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val similarTracks by viewModel.similarTracks.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val nowPlaying = queue.getOrNull(queueIndex) ?: currentTrack
    val upcomingStart = if (queueIndex >= 0) queueIndex + 1 else 0
    val upcoming = queue.drop(upcomingStart)
    val upcomingSeconds = upcoming.sumOf { it.duration }
    val stepPx = with(LocalDensity.current) { RowStep.toPx() }

    // Stable per-item keys, so reordering can actually animate.
    //
    // The row key used to be `"${track.id}-$i"`, which bakes the *index* into
    // the identity. Moving a song changed every key in the list, so LazyColumn
    // saw a brand-new set of items and had nothing to animate between — the
    // queue snapped on drop. Keying on the track instead (with an occurrence
    // suffix, because the same song can legitimately sit in the queue twice and
    // duplicate keys would crash) lets `animateItemPlacement` slide the rows.
    val upcomingKeyed = remember(upcoming) { LazyKeys.withOccurrence(upcoming) { it.id } }

    // Drag-to-reorder state (index is relative to `upcoming`)
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(Motion.Standard, easing = Motion.Decelerate)),
            exit = fadeOut(tween(Motion.Quick, easing = Motion.Accelerate))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDismiss() }
            )
        }

        // Explicitly a tween, not the default spring: the panel's fade was
        // always a tween, so the two halves of the same motion ran on different
        // clocks and the panel arrived after its own fade had finished.
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(
                animationSpec = tween(Motion.Expressive, easing = Motion.Decelerate)
            ) { it } + fadeIn(tween(Motion.Standard, easing = Motion.Decelerate)),
            exit = slideOutHorizontally(
                animationSpec = tween(Motion.Standard, easing = Motion.Accelerate)
            ) { it } + fadeOut(tween(Motion.Quick, easing = Motion.Accelerate))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight(0.88f)
                    .width(320.dp)
                    .padding(12.dp),
                color = AuraBg.copy(alpha = 0.98f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // ── Header ────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = AuraViolet,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Queue",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (upcoming.isEmpty()) {
                                        "Nothing up next"
                                    } else {
                                        "${upcoming.size} songs · ${formatTotal(upcomingSeconds)}"
                                    },
                                    color = AuraTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Shuffle / repeat / clear ──────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (isShuffle) "Shuffle on" else "Shuffle off",
                                    tint = if (isShuffle) AuraViolet else AuraTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.cycleRepeat() }) {
                                Icon(
                                    imageVector = if (repeatMode == RepeatMode.ONE) {
                                        Icons.Default.RepeatOne
                                    } else {
                                        Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat ${repeatMode.name.lowercase()}",
                                    tint = if (repeatMode == RepeatMode.OFF) AuraTextSecondary else AuraViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (upcoming.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearQueue() }) {
                                Text("Clear upcoming", color = AuraError, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Single scrollable list: now playing + up next + suggestions ──
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(RowSpacing)
                    ) {
                        if (nowPlaying != null) {
                            item(key = "now-playing-label") {
                                SectionLabel("Now Playing")
                            }
                            item(key = "now-playing-${nowPlaying.id}") {
                                NowPlayingCard(nowPlaying)
                            }
                        }

                        item(key = "up-next-label") {
                            SectionLabel("Next Up")
                        }

                        if (upcoming.isEmpty()) {
                            item(key = "up-next-empty") {
                                EmptyRow()
                            }
                        } else {
                            itemsIndexed(
                                items = upcomingKeyed,
                                key = { _, item -> item.second },
                                contentType = { _, _ -> "queue" }
                            ) { i, item ->
                                val track = item.first
                                val absoluteIndex = upcomingStart + i
                                val isDragging = dragIndex == i
                                QueueTrackRow(
                                    track = track,
                                    position = i + 1,
                                    isDragging = isDragging,
                                    modifier = Modifier
                                        // Rows slide to their new slot on drop
                                        // and on removal instead of jumping.
                                        .animateItemPlacement(
                                            animationSpec = tween(
                                                Motion.Standard,
                                                easing = Motion.StandardEasing
                                            )
                                        )
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffsetY else 0f
                                            alpha = if (isDragging) 0.92f else 1f
                                        }
                                        .pointerInput(absoluteIndex, upcoming.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragIndex = i
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffsetY += amount.y
                                                },
                                                onDragEnd = {
                                                    val from = dragIndex
                                                    if (from != null && stepPx > 0f) {
                                                        val shift = (dragOffsetY / stepPx).roundToInt()
                                                        val to = (from + shift)
                                                            .coerceIn(0, upcoming.lastIndex)
                                                        if (to != from) {
                                                            viewModel.moveInQueue(
                                                                upcomingStart + from,
                                                                upcomingStart + to
                                                            )
                                                        }
                                                    }
                                                    dragIndex = null
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    dragIndex = null
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        },
                                    onPlay = { viewModel.jumpToQueueIndex(absoluteIndex) },
                                    onPlayNext = { viewModel.moveInQueue(absoluteIndex, upcomingStart) },
                                    onRemove = { viewModel.removeFromQueue(absoluteIndex) }
                                )
                            }
                        }

                        if (similarTracks.isNotEmpty()) {
                            item(key = "similar-label") {
                                SectionLabel("Similar Songs", accent = true)
                            }
                            itemsIndexed(
                                items = similarTracks,
                                key = { i, track -> "similar-${track.id}-$i" },
                                contentType = { _, _ -> "similar" }
                            ) { _, track ->
                                SimilarTrackRow(
                                    track = track,
                                    onPlayNext = { viewModel.playNextInQueue(track) },
                                    onAddToQueue = { viewModel.addToQueue(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, accent: Boolean = false) {
    Text(
        text = text.uppercase(),
        color = if (accent) AuraViolet else Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun NowPlayingCard(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AuraCardStrong)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(url = track.albumArt, size = 40.dp, cornerRadius = 8.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = AuraTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatClock(track.duration),
            color = AuraTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun EmptyRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(AuraCardSubtle),
        contentAlignment = Alignment.Center
    ) {
        Text("Nothing up next", color = AuraTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun QueueTrackRow(
    track: Track,
    position: Int,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor.copy(alpha = if (isDragging) 0.95f else 0.6f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Tap target: artwork + text (kept separate from the drag gesture so
        // the two never fight over the same pointer events).
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onPlay() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(url = track.albumArt, size = 40.dp, cornerRadius = 8.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$position · ${track.artist}",
                    color = AuraTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onPlayNext, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Move to top",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove from queue",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SimilarTrackRow(
    track: Track,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(url = track.albumArt, size = 40.dp, cornerRadius = 8.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = AuraTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onPlayNext, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play next",
                tint = AuraViolet,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onAddToQueue, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to queue",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** "3:07" style duration for a single track. */
private fun formatClock(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${if (seconds < 10) "0" else ""}$seconds"
}

/** "1h 12m" / "48m" style duration for a list. */
private fun formatTotal(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
