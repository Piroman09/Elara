package com.piroman.elara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.data.model.Track
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.AddToPlaylistDialog
import com.piroman.elara.ui.components.PlayShuffleRow
import com.piroman.elara.ui.components.TrackArtwork
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraSuccess

/** Browsed editorial playlist (JioSaavn featured) — browsing only, not in the user's library. */
@Composable
fun BrowsePlaylistScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val browse by viewModel.browsePlaylist.collectAsState()
    val isLoading by viewModel.isBrowseLoading.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var addTrack by remember { mutableStateOf<Track?>(null) }

    // Hoisted out of the rows — see SearchTrackItem.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()
    val downloadedIds = remember(downloadedEntries) {
        downloadedEntries.mapTo(HashSet()) { it.track.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Playlist", color = AuraTextSecondary, fontSize = 14.sp)
        }

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color.White) }

            browse == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("Couldn't load this playlist.", color = AuraTextSecondary) }

            else -> {
                val playlist = browse!!
                // Derived from the *collected* `playlists` state rather than
                // `PlaylistManager.findByName`: this has to recompose when a save
                // lands, or the label would keep saying "Save to Library" until
                // the screen is reopened. Same case-insensitive rule as
                // findByName, so the two cannot disagree — and there is no
                // ViewModel helper to duplicate it (see MainViewModel).
                val saved = playlists.any { it.name.equals(playlist.title, ignoreCase = true) }

                // Lazy keys: identity **plus an occurrence suffix**, never the
                // bare id. These tracks come straight from the JioSaavn playlist
                // response with a plain `mapNotNull` and no de-duplication, and
                // this same list is what "Save to Library" copies verbatim into a
                // playlist, so nothing guarantees the ids are distinct. A
                // duplicate key crashes the list rather than degrading
                // gracefully. See LazyKeys.
                val tracksKeyed = remember(playlist) {
                    LazyKeys.withOccurrence(playlist.tracks) { it.id }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackArtwork(
                                url = playlist.image,
                                size = 110.dp,
                                cornerRadius = 12.dp,
                                contentDescription = playlist.title
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    playlist.title,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(playlist.subtitle, color = AuraTextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (saved) "Saved in Library ✓" else "Save to Library",
                                    color = if (saved) AuraSuccess else AuraPurple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !saved) {
                                            viewModel.saveBrowsePlaylistToLibrary()
                                        }
                                        .background(AuraPurple.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        PlayShuffleRow(
                            onPlay = {
                                if (playlist.tracks.isNotEmpty()) {
                                    viewModel.playTrack(playlist.tracks.first(), playlist.tracks)
                                }
                            },
                            onShuffle = {
                                if (playlist.tracks.isNotEmpty()) {
                                    viewModel.playTrack(playlist.tracks.random(), playlist.tracks)
                                }
                            },
                            enabled = playlist.tracks.isNotEmpty()
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    items(
                        items = tracksKeyed,
                        key = { it.second },
                        contentType = { "track" }
                    ) { item ->
                        val track = item.first
                        SearchTrackItem(
                            track = track,
                            onClick = { viewModel.playTrack(track, playlist.tracks) },
                            viewModel = viewModel,
                            onAddToPlaylist = { addTrack = track },
                            downloadState = downloadStates[track.id],
                            isDownloaded = track.id in downloadedIds
                        )
                    }
                }
            }
        }
    }

    addTrack?.let { track ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { addTrack = null }
        )
    }
}
