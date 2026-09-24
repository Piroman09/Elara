package com.aesthetic.spotify.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.spotify.data.download.DownloadManager
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.components.AddToPlaylistDialog
import com.aesthetic.spotify.ui.components.TrackArtwork
import com.aesthetic.spotify.ui.components.TrackContextMenu
import com.aesthetic.spotify.ui.theme.AuraBg
import com.aesthetic.spotify.ui.theme.AuraHomeGlow
import com.aesthetic.spotify.ui.theme.AuraCard
import com.aesthetic.spotify.ui.theme.AuraCardBorder
import com.aesthetic.spotify.ui.theme.AuraTextSecondary
import com.aesthetic.spotify.data.model.Track

/**
 * Height of the purple glow at the top of Home, in dp.
 *
 * This was a hardcoded `endY = 900f`, which is **raw pixels**, not dp — so the
 * glow faded over roughly 300 dp on a 3x phone but over the entire screen on a
 * low-density device, and then sat flat at [AuraBg] for the rest. Expressed in
 * dp and converted with the real density so it looks the same everywhere.
 */
private val HomeGlowHeight = 320.dp

private val QuickPickShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenBrowse: () -> Unit = {}
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val featured by viewModel.featuredPlaylists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    // Home renders songs too, so the long-press menu's "Add to playlist" needs
    // the same screen-level dialog state the other track lists already keep.
    var addPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    // Collected once for the whole screen, not once per card — same rule as
    // SearchScreen. Reading these inside QuickPickItem/TrendingCard would give
    // every visible card its own subscription to the download flow and
    // re-compose it on every progress tick.
    //
    // These are also what let the menu show "Remove download" instead of
    // "Download" for a track that is already on disk. Without them the menu
    // silently offered to download a downloaded song, which then called
    // downloadTrack instead of deleteDownload.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()
    val downloadedIds = remember(downloadedEntries) {
        downloadedEntries.mapTo(HashSet()) { it.track.id }
    }

    // `remember` without a key caches this for the life of the screen, so it is
    // evaluated once per visit rather than on every recomposition.
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Good Night"
        }
    }

    val density = LocalDensity.current
    val homeGradient = remember(density) {
        Brush.verticalGradient(
            colors = listOf(AuraHomeGlow, AuraBg),
            startY = 0f,
            endY = with(density) { HomeGlowHeight.toPx() }
        )
    }

    val quickPicks = remember(recentlyPlayed, trendingTracks) {
        recentlyPlayed.ifEmpty { trendingTracks }.take(6)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(homeGradient)
            .padding(16.dp)
    ) {
        item(key = "greeting") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Featured playlists from JioSaavn (browse content) ───────────
        if (featured.isNotEmpty()) {
            item(key = "featured") {
                SectionHeading("Recommended playlists")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(
                        items = featured,
                        key = { it.id },
                        contentType = { "featured" }
                    ) { fp ->
                        Card(
                            onClick = {
                                viewModel.loadBrowsePlaylist(fp)
                                onOpenBrowse()
                            },
                            modifier = Modifier.size(150.dp, 190.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AuraCard)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                TrackArtwork(
                                    url = fp.image,
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    cornerRadius = 10.dp,
                                    contentDescription = fp.title
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    fp.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    fp.subtitle,
                                    color = AuraTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── Recently played (falls back to quick picks until history exists) ──
        if (quickPicks.isNotEmpty()) {
            item(key = "quick-picks") {
                SectionHeading(if (recentlyPlayed.isEmpty()) "Quick picks" else "Recently played")

                // A plain Column of Rows, not a LazyVerticalGrid: the grid used
                // to be nested inside this LazyColumn item behind a hardcoded
                // 200 dp height, which meant a second scroll container, a
                // fixed-height guess that clipped long titles, and no natural
                // measurement. Six items in two columns don't need recycling.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickPicks.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { track ->
                                QuickPickItem(
                                    track = track,
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.playTrack(track, quickPicks) },
                                    onAddToPlaylist = { addPlaylistTrack = track },
                                    downloadState = downloadStates[track.id],
                                    isDownloaded = track.id in downloadedIds
                                )
                            }
                            // Odd item out keeps half width instead of
                            // stretching across the whole row.
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── Trending Songs (per Music Source setting) ───────────────────
        if (trendingTracks.isNotEmpty()) {
            item(key = "trending-heading") {
                Spacer(modifier = Modifier.height(32.dp))
                SectionHeading("Trending Songs")
            }

            item(key = "trending-row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(
                        items = trendingTracks,
                        key = { it.id },
                        contentType = { "trending" }
                    ) { track ->
                        TrendingCard(
                            track = track,
                            viewModel = viewModel,
                            onClick = { viewModel.playTrack(track, trendingTracks) },
                            onAddToPlaylist = { addPlaylistTrack = track },
                            downloadState = downloadStates[track.id],
                            isDownloaded = track.id in downloadedIds
                        )
                    }
                }
            }
        }

        item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(100.dp)) }
    }

    addPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { addPlaylistTrack = null }
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuickPickItem(
    track: Track,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    downloadState: DownloadManager.State? = null,
    isDownloaded: Boolean = false
) {
    val menuOpen = remember { mutableStateOf(false) }

    // The menu is a sibling of the Card, not a child, so it anchors to the whole
    // tile. (It would not be *clipped* by the Card either way — a DropdownMenu
    // renders in its own popup window — but anchoring to the tile reads better
    // than anchoring to whatever happens to be first inside it.)
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = { menuOpen.value = true }
                ),
            shape = QuickPickShape,
            colors = CardDefaults.cardColors(containerColor = AuraCard),
            border = BorderStroke(1.dp, AuraCardBorder)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Square corners: the Card already clips its content to
                // QuickPickShape, so rounding here would cut the artwork's inner edge.
                TrackArtwork(url = track.albumArt, size = 64.dp, cornerRadius = 0.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = AuraTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        TrackContextMenu(
            expanded = menuOpen.value,
            onDismiss = { menuOpen.value = false },
            track = track,
            viewModel = viewModel,
            onAddToPlaylist = onAddToPlaylist,
            downloadState = downloadState,
            isDownloaded = isDownloaded
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrendingCard(
    track: Track,
    viewModel: MainViewModel,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    downloadState: DownloadManager.State? = null,
    isDownloaded: Boolean = false
) {
    val menuOpen = remember { mutableStateOf(false) }

    Column(modifier = Modifier.width(150.dp)) {
        Box {
            Card(
                modifier = Modifier
                    .size(150.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = { menuOpen.value = true }
                    ),
                shape = RoundedCornerShape(10.dp)
            ) {
                TrackArtwork(
                    url = track.albumArt,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    contentDescription = track.title
                )
            }

            TrackContextMenu(
                expanded = menuOpen.value,
                onDismiss = { menuOpen.value = false },
                track = track,
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist,
                downloadState = downloadState,
                isDownloaded = isDownloaded
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
        Text(
            text = track.artist,
            color = AuraTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp
        )
    }
}
