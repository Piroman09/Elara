package com.aesthetic.spotify.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.spotify.data.download.DownloadManager
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.data.source.SourceSetting
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.components.AddToPlaylistDialog
import com.aesthetic.spotify.ui.components.TrackArtwork
import com.aesthetic.spotify.ui.components.TrackContextMenu
import com.aesthetic.spotify.ui.theme.AuraBg
import com.aesthetic.spotify.ui.theme.AuraCardStrong
import com.aesthetic.spotify.ui.theme.AuraField
import com.aesthetic.spotify.ui.theme.AuraPurple
import com.aesthetic.spotify.ui.theme.AuraTextPrimary
import com.aesthetic.spotify.ui.theme.AuraTextSecondary
import com.aesthetic.spotify.ui.theme.AuraTextTertiary

private val SearchFieldShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val sourceSetting by viewModel.sourceSetting.collectAsState()
    val focusManager = LocalFocusManager.current
    var addPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    // Collected once for the whole list, not once per row. These used to live
    // inside SearchTrackItem, which meant every visible row held its own
    // subscription to the download state flow and re-composed on every
    // download progress tick. See SearchTrackItem for the rest of the story.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()
    val downloadedIds = remember(downloadedEntries) {
        downloadedEntries.mapTo(HashSet()) { it.track.id }
    }

    Column(modifier = Modifier.fillMaxSize().background(AuraBg).padding(16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = AuraTextPrimary,
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
                    .clip(SearchFieldShape),
                placeholder = { Text("What do you want to listen to?", color = AuraTextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AuraTextSecondary)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AuraCardStrong,
                    unfocusedContainerColor = AuraField,
                    focusedTextColor = AuraTextPrimary,
                    unfocusedTextColor = AuraTextPrimary,
                    cursorColor = AuraPurple,
                    focusedIndicatorColor = AuraPurple,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            if (isConnecting) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AuraTextPrimary,
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty() && query.isNotEmpty() && !isConnecting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found. Try another search.", color = AuraTextSecondary)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = results,
                key = { it.id },
                contentType = { "track" }
            ) { track ->
                SearchTrackItem(
                    track = track,
                    onClick = { viewModel.playTrack(track, results) },
                    viewModel = viewModel,
                    onAddToPlaylist = { addPlaylistTrack = track },
                    showSource = sourceSetting == SourceSetting.AUTO,
                    downloadState = downloadStates[track.id],
                    isDownloaded = track.id in downloadedIds
                )
            }
        }
    }

    addPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { addPlaylistTrack = null }
        )
    }
}

/**
 * A single song row, shared by Search, Playlist detail and Browse.
 *
 * [downloadState] and [isDownloaded] are passed in rather than read from the
 * ViewModel here. Each row used to call `collectAsState()` on the download map
 * and the download list, so a 40-row result set held 80 flow subscriptions and
 * every progress tick re-composed all 40 rows — and each one scanned the whole
 * download list to answer "is this one downloaded?" (O(rows × downloads)). The
 * caller now resolves both once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchTrackItem(
    track: Track,
    onClick: () -> Unit,
    viewModel: MainViewModel,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    showSource: Boolean = false,
    downloadState: DownloadManager.State? = null,
    isDownloaded: Boolean = false
) {
    val menuOpen = remember { mutableStateOf(false) }

    Card(
        // Long-press is the shortcut this row was missing. The overflow button
        // stays as well: long-press alone is undiscoverable, so removing the
        // button would strand anyone who never thinks to hold a row down.
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                // The clickable `Card` overload this replaced passed
                // Role.Button. `combinedClickable` does not, so it is restored
                // here or screen readers announce the row as an unlabelled tap
                // target rather than a button.
                role = Role.Button,
                onClick = onClick,
                onLongClick = { menuOpen.value = true }
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(
                url = track.albumArt,
                size = 60.dp,
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = AuraTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = AuraTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showSource) {
                    Text(
                        text = track.source.label,
                        color = AuraTextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen.value = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = AuraTextPrimary
                    )
                }
                TrackContextMenu(
                    expanded = menuOpen.value,
                    onDismiss = { menuOpen.value = false },
                    track = track,
                    viewModel = viewModel,
                    onAddToPlaylist = onAddToPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    downloadState = downloadState,
                    isDownloaded = isDownloaded
                )
            }
        }
    }
}
