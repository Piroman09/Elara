package com.piroman.elara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.data.stats.ListeningStatsFormat
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.ImportSpotifyDialog
import com.piroman.elara.ui.components.SectionHeading
import com.piroman.elara.ui.components.TextInputDialog
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCard
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraViolet
import com.piroman.elara.ui.theme.AuraDownload
import com.piroman.elara.ui.theme.DownloadsBrush
import com.piroman.elara.ui.theme.LikedSongsBrush
import com.piroman.elara.ui.theme.PlaylistBrush
import com.piroman.elara.ui.theme.SpotifyBrand
import com.piroman.elara.ui.theme.AuraTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenWrapped: () -> Unit = {}
) {
    val likedTracks by viewModel.likedTracks.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadedBytes by viewModel.downloadedBytes.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val sourceSetting by viewModel.sourceSetting.collectAsState()
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var showImportSpotify by remember { mutableStateOf(false) }
    var showSourceSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = { showSourceSettings = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Music source settings",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wrapped entry: sits above the lifetime stats row because its data is
        // the freshest slice of the same story — this month vs. all time.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(AuraPurple.copy(alpha = 0.25f), AuraCard)
                    )
                )
                .clickable(onClick = onOpenWrapped)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuraPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = AuraViolet,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Elara Wrapped",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Your month in music",
                    color = AuraTextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AuraTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stats snapshot card
        val totalMs by viewModel.totalListeningMs.collectAsState()
        val uniqueTracks by viewModel.uniqueTracksListened.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AuraCard)
                .clickable(onClick = onOpenStats)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuraPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AvTimer,
                    contentDescription = null,
                    tint = AuraPurple,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ListeningStatsFormat.listenedLabel(totalMs),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$uniqueTracks unique tracks",
                    color = AuraTextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AuraTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Playlists header ────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Was a hand-written `Color.White / 18.sp / Bold` copy of
                    // Home's private heading. Now the one shared component, so
                    // the two screens cannot drift. `bottomPadding = 0.dp`
                    // because this row's spacing already comes from the
                    // LazyColumn's own `spacedBy(12.dp)`.
                    SectionHeading("Playlists", bottomPadding = 0.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showImportSpotify = true }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = SpotifyBrand,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", color = SpotifyBrand, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showCreatePlaylist = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = AuraPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", color = AuraPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Pinned: Liked Songs & Downloads ─────────────────────────
            item {
                SpecialPlaylistCard(
                    icon = Icons.Default.Favorite,
                    brush = LikedSongsBrush,
                    title = "Liked Songs",
                    subtitle = "${likedTracks.size} songs",
                    onClick = { onOpenPlaylist("liked") }
                )
            }
            item {
                SpecialPlaylistCard(
                    icon = Icons.Default.DownloadDone,
                    brush = DownloadsBrush,
                    title = "Downloads",
                    // Exactly the label the Downloads screen prints in its own
                    // header, so the card and the screen it opens cannot disagree
                    // about how much is saved — the same reason the stats row and
                    // ListeningStatsScreen share ListeningStatsFormat.
                    subtitle = summaryLabel(downloadedTracks.size, downloadedBytes),
                    onClick = onOpenDownloads
                )
            }

            // ── User playlists ──────────────────────────────────────────
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "Create playlists to organise your music.",
                        color = AuraTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            } else {
                items(
                    items = playlists,
                    key = { it.id },
                    contentType = { "playlist" }
                ) { playlist ->
                    Card(
                        onClick = { onOpenPlaylist(playlist.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = AuraCard
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PlaylistBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    "${playlist.tracks.size} songs",
                                    color = AuraTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AuraTextSecondary
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showCreatePlaylist) {
        TextInputDialog(
            title = "New playlist",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false }
        )
    }

    if (showImportSpotify) {
        ImportSpotifyDialog(
            viewModel = viewModel,
            onSuccess = { playlist ->
                showImportSpotify = false
                onOpenPlaylist(playlist.id)
            },
            onDismiss = { showImportSpotify = false }
        )
    }

    if (showSourceSettings) {
        val dataSaver by viewModel.dataSaverEnabled.collectAsState()
        com.piroman.elara.ui.components.SourceSettingsDialog(
            current = sourceSetting,
            onSelect = { viewModel.setSourceSetting(it) },
            onDismiss = { showSourceSettings = false },
            exportJson = { viewModel.exportBackupJson() },
            importJson = { viewModel.importBackupJson(it) },
            dataSaverEnabled = dataSaver,
            onDataSaverChange = { viewModel.setDataSaver(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpecialPlaylistCard(
    icon: ImageVector,
    brush: Brush,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraCard)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, color = AuraTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuraTextSecondary)
        }
    }
}
