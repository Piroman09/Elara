package com.aesthetic.spotify.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.theme.AuraBg
import kotlinx.coroutines.flow.distinctUntilChanged
@Composable
fun FullScreenPlayer(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val lyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    if (track == null) return

    val accent = runCatching {
        Color(android.graphics.Color.parseColor(track!!.accentColor ?: "#8B5CF6"))
    }.getOrDefault(Color(0xFF8B5CF6))

    val sliderMax = if (durationMs > 0) durationMs.toFloat() else 1f
    val sliderValue = (scrubPosition ?: positionMs.toFloat()).coerceIn(0f, sliderMax)

    // Active lyric line for the current playback position.
    val activeLine = remember(lyrics, positionMs) {
        if (lyrics.isEmpty()) -1
        else lyrics.indexOfLast { it.time * 1000f <= positionMs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.5f), AuraBg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White)
                }
                Text(
                    text = track!!.album,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                IconButton(onClick = { /* More options */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { showAddToPlaylist = true }) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = Color.White
                    )
                }
                DownloadButton(track = track!!, viewModel = viewModel, tint = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showLyrics) {
                LyricsPane(
                    lyrics = lyrics,
                    activeLine = activeLine,
                    isLoading = lyricsLoading
                )
            } else {
                AsyncImage(
                    model = track!!.albumArt,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = track!!.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = track!!.artist, color = Color.LightGray, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = sliderValue,
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = {
                    scrubPosition?.let { viewModel.seekTo(it.toLong()) }
                    scrubPosition = null
                },
                valueRange = 0f..sliderMax,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF8B5CF6),
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(if (scrubPosition != null) scrubPosition!!.toLong() else positionMs), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }

            playbackError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val liked = viewModel.isLiked(track!!)
                IconButton(onClick = { viewModel.toggleLikeTrack(track!!) }) {
                    Icon(
                        imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        tint = if (liked) Color.Red else Color.White
                    )
                }
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                FloatingActionButton(
                    onClick = { viewModel.togglePlay() },
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(100)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) Color.Cyan else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track!!,
            onDismiss = { showAddToPlaylist = false }
        )
    }
}

@Composable
private fun LyricsPane(
    lyrics: List<com.aesthetic.spotify.data.model.LyricLine>,
    activeLine: Int,
    isLoading: Boolean
) {
    val listState = rememberLazyListState()

    // Keep the active line in view.
    LaunchedEffect(listState) {
        snapshotFlow { activeLine }
            .distinctUntilChanged()
            .collect { idx ->
                if (idx >= 0) {
                    runCatching {
                        listState.animateScrollToItem(
                            index = (idx - 2).coerceAtLeast(0),
                            scrollOffset = 0
                        )
                    }
                }
            }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }
    } else if (lyrics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No lyrics found for this track.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(400.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeLine
                val color by animateColorAsState(
                    targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                    label = "lyricColor"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1f,
                    label = "lyricScale"
                )
                Text(
                    text = line.text,
                    color = color,
                    fontSize = 22.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .scale(scale)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
