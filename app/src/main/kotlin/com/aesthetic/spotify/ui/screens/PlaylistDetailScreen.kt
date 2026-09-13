package com.aesthetic.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.data.playlist.Playlist
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.components.AddToPlaylistDialog
import com.aesthetic.spotify.ui.components.TextInputDialog
import com.aesthetic.spotify.ui.theme.AuraBg
import com.aesthetic.spotify.ui.theme.AuraMenu
import com.aesthetic.spotify.ui.theme.AuraError
import com.aesthetic.spotify.ui.theme.AuraDownload
import com.aesthetic.spotify.ui.theme.DownloadsBrush
import com.aesthetic.spotify.ui.theme.LikedSongsBrush
import com.aesthetic.spotify.ui.theme.PlaylistBrush
import com.aesthetic.spotify.ui.theme.AuraTextSecondary

/**
 * Playlist detail for both user playlists and the pinned specials
 * ("liked" / "downloads"), which resolve from their own state flows.
 */
@Composable
fun PlaylistDetailScreen(
    viewModel: MainViewModel,
    playlistId: String,
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()
    val sourceSetting by viewModel.sourceSetting.collectAsState()

    // Resolved once for the whole list instead of once per row — see
    // SearchTrackItem for why the per-row version was a problem.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedIds = remember(downloadedEntries) {
        downloadedEntries.mapTo(HashSet()) { it.track.id }
    }

    val isSpecial = playlistId == "liked" || playlistId == "downloads"

    val playlist: Playlist? = when (playlistId) {
        "liked" -> Playlist(id = "liked", name = "Liked Songs", tracks = likedTracks)
        "downloads" -> Playlist(
            id = "downloads",
            name = "Downloads",
            tracks = downloadedEntries.map { it.track }
        )
        else -> playlists.firstOrNull { it.id == playlistId }
    }

    val headerIcon: ImageVector = when (playlistId) {
        "liked" -> Icons.Default.Favorite
        "downloads" -> Icons.Default.DownloadDone
        else -> Icons.Default.QueueMusic
    }
    val headerBrush = when (playlistId) {
        "liked" -> LikedSongsBrush
        "downloads" -> DownloadsBrush
        else -> PlaylistBrush
    }

    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var addPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    if (playlist == null) {
        // Deleted while open — leave the screen.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBg)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                if (isSpecial) "Playlist" else "Playlist",
                color = AuraTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (!isSpecial) {
                IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.White)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete playlist",
                        tint = AuraError
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(headerBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            headerIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            playlist.name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (playlistId == "downloads")
                                "${playlist.tracks.size} songs saved offline"
                            else "${playlist.tracks.size} songs",
                            color = AuraTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (playlist.tracks.isNotEmpty()) {
                                viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                            }
                        },
                        enabled = playlist.tracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            if (playlist.tracks.isNotEmpty()) {
                                viewModel.playTrack(playlist.tracks.random(), playlist.tracks)
                            }
                        },
                        enabled = playlist.tracks.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (playlist.tracks.isEmpty()) {
                item {
                    Text(
                        when (playlistId) {
                            "liked" -> "Songs you like will appear here — tap the heart on any song."
                            "downloads" -> "Tap the download icon on any song to save it offline."
                            else -> "No songs yet — use the add-to-playlist button on any song."
                        },
                        color = AuraTextSecondary
                    )
                }
            } else {
                items(
                    items = playlist.tracks,
                    key = { it.id },
                    contentType = { "track" }
                ) { track ->
                    SearchTrackItem(
                        track = track,
                        onClick = { viewModel.playTrack(track, playlist.tracks) },
                        viewModel = viewModel,
                        // Always offered, including inside a user playlist. It
                        // used to be hidden there on the reasoning that the song
                        // is already in *a* playlist — but the useful action is
                        // adding it to a *different* one, and hiding it made the
                        // long-press menu inconsistent between screens.
                        onAddToPlaylist = { addPlaylistTrack = track },
                        showSource = sourceSetting == com.aesthetic.spotify.data.source.SourceSetting.AUTO,
                        downloadState = downloadStates[track.id],
                        isDownloaded = track.id in downloadedIds,
                        onRemoveFromPlaylist = {
                            when (playlistId) {
                                "liked" -> viewModel.toggleLikeTrack(track)
                                "downloads" -> viewModel.deleteDownload(track.id)
                                else -> viewModel.removeTrackFromPlaylist(playlist.id, track.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showRename && !isSpecial) {
        TextInputDialog(
            title = "Rename playlist",
            initialText = playlist.name,
            confirmLabel = "Rename",
            onConfirm = { name ->
                viewModel.renamePlaylist(playlist.id, name)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    if (showDeleteConfirm && !isSpecial) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete playlist?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${playlist.name}\" and its ${playlist.tracks.size} songs will be removed. Your downloaded files are not deleted.",
                    color = AuraTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.id)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Delete", color = AuraError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = AuraTextSecondary)
                }
            },
            containerColor = AuraMenu
        )
    }

    addPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { addPlaylistTrack = null }
        )
    }
}
