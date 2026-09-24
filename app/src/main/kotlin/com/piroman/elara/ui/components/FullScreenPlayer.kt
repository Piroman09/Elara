package com.piroman.elara.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCardStrong
import com.piroman.elara.ui.theme.AuraError
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraViolet
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.Motion
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun FullScreenPlayer(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSettings: () -> Unit = {}
) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val lyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    // Deliberately *not* unwrapped with `by`. `by` reads `.value` in this
    // composable's scope, so the playback tick re-composed the whole player —
    // top bar, dropdown, artwork, lyrics, controls and the queue drawer — 2.5
    // times a second. The State object itself is stable, so passing it down
    // costs nothing here and the leaves that need the time read it themselves.
    val positionState = viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val isQueueOpen by viewModel.isQueueOpen.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val queueIndex by viewModel.queueIndex.collectAsState()

    // Collected rather than read via `viewModel.isLiked(track)`: that helper reads
    // `_likedTracks.value` once and does **not** subscribe this composable to the
    // flow, so tapping the heart changed the stored list but never repainted the
    // icon — it looked like the button did nothing. `likedTracks` is not per-frame
    // state (it changes only when the user taps), so collecting it here is cheap.
    val likedTracks by viewModel.likedTracks.collectAsState()

    // Upcoming tracks for the preview row.
    val upcoming = remember(queue, queueIndex) {
        if (queueIndex < 0 || queueIndex >= queue.lastIndex) emptyList()
        else queue.subList(queueIndex + 1, (queueIndex + 4).coerceAtMost(queue.size))
    }

    // Lazy keys for the preview row: identity **plus an occurrence suffix**,
    // never the bare id — the same rule as QueueDrawer, and for the same reason.
    //
    // This slice is taken straight out of the queue, and the queue deliberately
    // allows the same song to appear more than once: `playNextInQueue` inserts at
    // `queueIndex + 1` with no de-duplication, so tapping "Play next" on a song
    // that is already one of the next few tracks puts two entries with the same
    // id inside this three-item window. Duplicate keys crash a lazy list outright.
    val upcomingKeyed = remember(upcoming) { LazyKeys.withOccurrence(upcoming) { it.id } }

    // Same rule as `positionState` above, for the same reason: the sleep timer
    // ticks once a second while it runs, so it is handed to a leaf as a State
    // rather than unwrapped with `by` here. Unwrapping would re-compose the whole
    // player — artwork, lyrics, controls, queue drawer — every second.
    val sleepTimerState = viewModel.sleepTimerRemainingMs.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()

    if (track == null) return

    // The player is an overlay, not a navigation destination, so the system
    // back gesture would otherwise pop the screen *behind* it and leave the
    // player stranded on top of the previous destination.
    //
    // The queue is a layer *inside* the player, so back has to unwind it first —
    // a single handler would throw away the queue and the player together. The
    // two are mutually exclusive rather than stacked, because only the
    // last-registered *enabled* handler ever receives the event.
    BackHandler(enabled = isQueueOpen) { viewModel.setQueueOpen(false) }
    BackHandler(enabled = !isQueueOpen) { onDismiss() }

    val accent = runCatching {
        Color(android.graphics.Color.parseColor(track!!.accentColor ?: "#8B5CF6"))
    }.getOrDefault(AuraPurple)

    // Derived, not remembered-on-position. `derivedStateOf` recomputes lazily
    // and — the important part — only notifies when the *computed value*
    // actually changes. The position ticks 2.5 times a second but the active
    // lyric line changes once every few seconds, so the ticks are swallowed
    // here instead of reaching the UI. It is read inside the lyric rows, so
    // even a genuine change repaints two rows rather than the whole screen.
    val activeLineState = remember(lyrics, positionState) {
        derivedStateOf {
            val pos = positionState.value
            if (lyrics.isEmpty()) -1
            else lyrics.indexOfLast { it.time * 1000f <= pos }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    // Blended against AuraBg instead of left translucent: the
                    // full-screen player is now overlaid on the live UI rather
                    // than swapped in, so a 50 %-alpha top stop would let the
                    // list underneath show through it.
                    colors = listOf(accent.copy(alpha = 0.5f).compositeOver(AuraBg), AuraBg)
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White)
                }
                // `weight(1f)` rather than padding a centred label clear of the
                // buttons: the right side now carries two of them, so symmetric
                // insets would leave the album name only ~120 dp on a 360 dp
                // phone. Taking the leftover space gives it ~168 dp and costs
                // only a 24 dp offset from true centre.
                Text(
                    text = track!!.album,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                SleepTimerButton(
                    remaining = sleepTimerState,
                    onSelect = { viewModel.setSleepTimer(it) }
                )
                IconButton(onClick = { viewModel.setQueueOpen(true) }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Player settings",
                        tint = Color.White
                    )
                }
                // A dropdown anchors to its parent, so it gets its own Box around
                // the button. As a direct child of the full-width row it opened
                // at the far left edge rather than under the button.
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }
                    TrackContextMenu(
                        expanded = showMoreMenu,
                        onDismiss = { showMoreMenu = false },
                        track = track!!,
                        viewModel = viewModel,
                        onAddToPlaylist = { showAddToPlaylist = true },
                        downloadState = downloadStates[track!!.id],
                        isDownloaded = downloadedEntries.any { it.track.id == track!!.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Spacer(modifier = Modifier.weight(1f))

            // Cross-faded rather than swapped instantly. Both panes are the
            // same size (a full-width square), and that is what makes this
            // clean — a cross-fade between children of different sizes
            // re-lays-out mid-transition and reads as a jump, not a fade.
            Crossfade(
                targetState = showLyrics,
                animationSpec = tween(Motion.Standard, easing = Motion.StandardEasing),
                label = "playerPane"
            ) { lyricsMode ->
                if (lyricsMode) {
                    LyricsPane(
                        lyrics = lyrics,
                        activeLineState = activeLineState,
                        isLoading = lyricsLoading
                    )
                } else {
                    TrackArtwork(
                        url = track!!.albumArt,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        cornerRadius = 28.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Track title and artist
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = track!!.title, 
                    color = Color.White, 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track!!.artist, 
                    color = AuraTextSecondary, 
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // The seek bar and the elapsed-time label are the only things on
            // this screen that change on a playback tick, so they live in their
            // own composable with the scrub state inside it. Inlined here, every
            // tick invalidated the whole player — including the always-composed
            // queue drawer — just to move a thumb a few pixels.
            SeekSection(
                positionState = positionState,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onSeek = { viewModel.seekTo(it) }
            )

            playbackError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    color = AuraError,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Up Next preview
            if (upcoming.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Up next",
                    color = AuraTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(upcomingKeyed, key = { it.second }) { item ->
                        // `queueTrack`, not `track`: the outer `track` is the song
                        // playing right now, and shadowing it made the same name mean
                        // two different things a few lines apart.
                        val queueTrack = item.first
                        UpNextChip(
                            track = queueTrack,
                            onClick = { viewModel.playTrack(queueTrack, queue) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val liked = likedTracks.any { it.id == track!!.id }
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

                // Pixel Material 3 Squircle Play/Pause FAB
                FloatingActionButton(
                    onClick = { viewModel.togglePlay() },
                    containerColor = AuraViolet,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(34.dp),
                        tint = Color.Black
                    )
                }

                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) AuraViolet else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Rendered *inside* the player's layer. The shell owns its own copy, but
        // that one sits below the full-screen player in the z-order, so reusing
        // it here would open the panel behind the player and look like nothing
        // happened. MainScaffold suppresses its copy while the player is
        // expanded, so only one of the two is ever composed.
        QueueDrawer(
            viewModel = viewModel,
            isOpen = isQueueOpen,
            onDismiss = { viewModel.setQueueOpen(false) },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track!!,
            onDismiss = { showAddToPlaylist = false }
        )
    }
}

/** Minute choices offered by the sleep timer. `null` is the "off" entry. */
private val SleepTimerOptions = listOf<Int?>(null, 5, 15, 30, 45, 60)

/**
 * Sleep-timer control. Owns the only read of [remaining], so the once-a-second
 * countdown re-composes this button and nothing else.
 *
 * [onSelect] receives minutes, or null to cancel.
 */
@Composable
private fun SleepTimerButton(
    remaining: State<Long?>,
    onSelect: (Int?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val ms = remaining.value
    val active = ms != null

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = if (active) {
                    "Sleep timer, ${formatTime(ms!!)} remaining"
                } else {
                    "Sleep timer"
                },
                tint = if (active) AuraViolet else Color.White
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SleepTimerOptions.forEach { minutes ->
                // The active duration is shown by tint rather than a checkmark,
                // so "Off" still reads correctly when nothing is running.
                val isCurrent = if (minutes == null) {
                    !active
                } else {
                    active && ms!! > (minutes - 1) * 60_000L
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = minutes?.let { "$it minutes" } ?: "Off",
                            color = if (isCurrent) AuraViolet else AuraTextPrimary
                        )
                    },
                    onClick = {
                        onSelect(minutes)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LyricsPane(
    lyrics: List<com.piroman.elara.data.model.LyricLine>,
    activeLineState: State<Int>,
    isLoading: Boolean
) {
    val listState = rememberLazyListState()

    // Lazy keys: identity **plus an occurrence suffix**, never the bare id.
    //
    // The id here is the line's timestamp (see the LRC parsers), and two lines
    // can share one: bilingual LRC files carry the original and the translation
    // under the same `[mm:ss.xx]` marker, and repeated markers appear in
    // hand-edited files. A duplicate key crashes the list.
    //
    // Safe to re-key because `line.id` is used for nothing else — the active
    // line is tracked by *index* (`activeLineState`), not looked up by id.
    val lyricsKeyed = remember(lyrics) { LazyKeys.withOccurrence(lyrics) { it.id } }

    // Keep the active line in view. Reads the State rather than a plain Int, so
    // the playback tick arrives here as a snapshot-flow emission instead of a
    // recomposition of the pane. `snapshotFlow` already de-duplicates, so
    // `distinctUntilChanged` is now redundant and has been dropped with it.
    LaunchedEffect(listState, activeLineState) {
        snapshotFlow { activeLineState.value }
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
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }
    } else if (lyrics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
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
            // Square, matching the artwork exactly. It used to be a fixed
            // 400 dp, which on a 360 dp-wide phone left the two panes 88 dp
            // apart in height and made the cross-fade above impossible to do
            // without the layout jumping.
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            itemsIndexed(
                items = lyricsKeyed,
                key = { _, item -> item.second },
                contentType = { _, _ -> "lyric" }
            ) { index, item ->
                val line = item.first
                // A plain swap instead of animateColorAsState + animateFloatAsState
                // + Modifier.scale(). The scale was the expensive part: it wraps a
                // full-width 22 sp Text in its own graphics layer, and two rows
                // rebuilt that layer on every active-line change. Bold + full
                // opacity is just as legible at zero per-frame cost.
                val isActive = index == activeLineState.value
                Text(
                    text = line.text,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 22.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
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

/**
 * The seek bar plus its elapsed-time label, and the only two things on this
 * screen that change on a playback tick.
 *
 * They are isolated here so that a tick invalidates this composable and nothing
 * else. Before, the position was read at the top of [FullScreenPlayer], so every
 * tick re-composed the entire screen — top bar, dropdown, artwork, lyric pane,
 * control row and the always-composed queue drawer — just to move a thumb a few
 * pixels. The scrub state lives here too, which is what keeps it out of the
 * parent's scope.
 */
@Composable
private fun SeekSection(
    positionState: State<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    val sliderMax = if (durationMs > 0) durationMs.toFloat() else 1f
    val displayMs = scrubPosition?.toLong() ?: positionState.value

    PixelSquigglySlider(
        value = displayMs.toFloat().coerceIn(0f, sliderMax),
        onValueChange = { scrubPosition = it },
        onValueChangeFinished = {
            scrubPosition?.let { onSeek(it.toLong()) }
            scrubPosition = null
        },
        valueRange = 0f..sliderMax,
        isPlaying = isPlaying,
        activeColor = AuraViolet
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Text compares its String argument, so when the tick lands inside the
        // same second the label is skipped rather than re-measured.
        Text(formatTime(displayMs), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
fun PixelSquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = AuraViolet,
    inactiveColor: Color = Color.White.copy(alpha = 0.2f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    // Deliberately held as a State instead of read with `by`. The value is only
    // consumed inside the Canvas draw lambda below, so the 60 fps wave
    // animation invalidates the draw phase alone. Reading it here in
    // composition re-composed this slider on every frame of the wave.
    val phaseState: State<Float> = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phaseAnim"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Reused across frames instead of reallocated. The wave is redrawn up to 60
    // times a second while playing, so `Path()` per frame was 60 short-lived
    // objects a second for no reason.
    val wavePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
            val progressFraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
            val progressX = progressFraction * size.width
            val midY = size.height / 2f
            val stroke = 4.dp.toPx()
            val thumbRadius = 6.dp.toPx()
            val amp = if (isPlaying) 3.5.dp.toPx() else 0f
            val wavelength = 24.dp.toPx()
            val pi2 = (Math.PI * 2.0).toFloat()
            val phase = phaseState.value

            // Inactive straight track
            if (progressX < size.width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(progressX, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            if (progressX > 0f) {
                if (amp == 0f) {
                    // Paused: zero amplitude means the "wave" is a straight
                    // line, so skip the path walk entirely.
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, midY),
                        end = Offset(progressX, midY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(progressX, midY)
                    )
                } else {
                    // Six samples per wavelength instead of one every 4 px. At a
                    // 24 dp wavelength the old step worked out at roughly 17
                    // points per period — about 3x more line segments than a
                    // 4 dp stroke can resolve. That detail was invisible and
                    // paid for on every frame of the animation.
                    wavePath.reset()
                    wavePath.moveTo(0f, midY)
                    val step = (wavelength / 6f).coerceAtLeast(1f)
                    var x = 0f
                    while (x <= progressX) {
                        wavePath.lineTo(
                            x,
                            midY + kotlin.math.sin((x / wavelength) * pi2 - phase) * amp
                        )
                        x += step
                    }
                    wavePath.lineTo(
                        progressX,
                        midY + kotlin.math.sin((progressX / wavelength) * pi2 - phase) * amp
                    )
                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // Pixel Slider Thumb
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(
                            progressX,
                            midY + kotlin.math.sin((progressX / wavelength) * pi2 - phase) * amp
                        )
                    )
                }
            }
        }

        // Invisible native Slider overlay for silky touch scrub gestures
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0f)
        )
    }
}

/**
 * Small horizontal chip showing the next track in the queue.
 *
 * Kept tiny so several fit in the row without pushing the controls off-screen.
 */
@Composable
private fun UpNextChip(track: com.piroman.elara.data.model.Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AuraCardStrong)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            url = track.albumArt,
            size = 32.dp,
            cornerRadius = 4.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = track.title,
                color = AuraTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Text(
                text = track.artist,
                color = AuraTextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
    }
}
