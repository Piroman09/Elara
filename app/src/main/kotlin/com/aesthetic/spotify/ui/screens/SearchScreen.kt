package com.aesthetic.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.components.DownloadButton
import com.aesthetic.spotify.ui.theme.AuraBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val sourceSetting by viewModel.sourceSetting.collectAsState()
    val focusManager = LocalFocusManager.current
    var addPlaylistTrack by remember { mutableStateOf<com.aesthetic.spotify.data.model.Track?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AuraBg).padding(16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp)),
                placeholder = { Text("What do you want to listen to?", color = Color(0xFF8A8A96)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8A8A96)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = com.aesthetic.spotify.ui.theme.AuraPurple,
                    focusedIndicatorColor = com.aesthetic.spotify.ui.theme.AuraPurple,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            
            if (isConnecting) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty() && query.isNotEmpty() && !isConnecting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found. Try another search.", color = Color.Gray)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { track ->
                SearchTrackItem(
                    track = track,
                    onClick = { viewModel.playTrack(track, results) },
                    viewModel = viewModel,
                    onAddToPlaylist = { addPlaylistTrack = track },
                    showSource = sourceSetting == com.aesthetic.spotify.data.source.SourceSetting.AUTO
                )
            }
        }
    }

    addPlaylistTrack?.let { track ->
        com.aesthetic.spotify.ui.components.AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { addPlaylistTrack = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTrackItem(
    track: com.aesthetic.spotify.data.model.Track,
    onClick: () -> Unit,
    viewModel: MainViewModel? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    showSource: Boolean = false
) {
    val menuOpen = remember { mutableStateOf(false) }
    val downloadStates = viewModel?.downloadStates?.collectAsState()?.value
    val downloadedEntries = viewModel?.downloadedTracks?.collectAsState()?.value
    val isDownloaded = downloadedEntries?.any { it.track.id == track.id } == true

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.albumArt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = track.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(text = track.artist, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    if (showSource) {
                        Text(
                            text = track.source.label,
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuOpen.value = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen.value,
                        onDismissRequest = { menuOpen.value = false },
                        modifier = Modifier.background(Color(0xFF1A1A1C))
                    ) {
                        if (onAddToPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text("Add to playlist", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.White)
                                },
                                onClick = {
                                    menuOpen.value = false
                                    onAddToPlaylist()
                                }
                            )
                        }
                        if (viewModel != null) {
                            when {
                                downloadStates?.get(track.id) is com.aesthetic.spotify.data.download.DownloadManager.State.Downloading -> {
                                    DropdownMenuItem(
                                        text = { Text("Downloading…", color = Color.Gray) },
                                        leadingIcon = {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        },
                                        onClick = {}
                                    )
                                }
                                isDownloaded -> {
                                    DropdownMenuItem(
                                        text = { Text("Remove download", color = Color.White) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DownloadDone,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399)
                                            )
                                        },
                                        onClick = {
                                            menuOpen.value = false
                                            viewModel.deleteDownload(track.id)
                                        }
                                    )
                                }
                                downloadStates?.get(track.id) is com.aesthetic.spotify.data.download.DownloadManager.State.Error -> {
                                    DropdownMenuItem(
                                        text = { Text("Download failed — retry", color = Color(0xFFF87171)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFF87171))
                                        },
                                        onClick = {
                                            menuOpen.value = false
                                            viewModel.downloadTrack(track)
                                        }
                                    )
                                }
                                else -> {
                                    DropdownMenuItem(
                                        text = { Text("Download", color = Color.White) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                        },
                                        onClick = {
                                            menuOpen.value = false
                                            viewModel.downloadTrack(track)
                                        }
                                    )
                                }
                            }
                        }
                        if (onRemoveFromPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from playlist", color = Color(0xFFF87171)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFF87171))
                                },
                                onClick = {
                                    menuOpen.value = false
                                    onRemoveFromPlaylist()
                                }
                            )
                        }
                    }
                }
            }
    }
}
