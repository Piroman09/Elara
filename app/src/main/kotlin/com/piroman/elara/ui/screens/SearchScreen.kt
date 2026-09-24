package com.piroman.elara.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import com.piroman.elara.data.download.DownloadManager
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.source.SourceSetting
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.AddToPlaylistDialog
import com.piroman.elara.ui.components.TrackArtwork
import com.piroman.elara.ui.components.TrackContextMenu
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCardStrong
import com.piroman.elara.ui.theme.AuraField
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraTextTertiary

private val SearchFieldShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
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

    // Lazy keys: identity **plus an occurrence suffix**, never the bare id.
    //
    // Results are not guaranteed unique by id. With Music Source on Auto the
    // repository concatenates the JioSaavn and YouTube lists and then collapses
    // them with `MusicRepository.dedupe`, which matches on normalized
    // title+artist — *not* on id. So it can drop an entry that only differs by
    // source while leaving two entries that share an id, and a single source can
    // simply return the same song twice under two title spellings. A duplicate
    // key does not degrade gracefully, it crashes the list. See LazyKeys.
    val resultsKeyed = remember(results) { LazyKeys.withOccurrence(results) { it.id } }

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

        // ── Recent searches ────────────────────────────────────────────
        // Shown only while the field is empty, so the chips never compete with
        // results for space. Tapping one re-runs the search by setting the query
        // — the same path typing takes, debounce included.
        if (query.isEmpty() && searchHistory.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent searches",
                    color = AuraTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { viewModel.clearSearchHistory() }) {
                    Text("Clear", color = AuraTextTertiary, fontSize = 13.sp)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = searchHistory,
                    // Query strings are unique by construction — `recordSearch`
                    // de-duplicates case-insensitively before storing — so the
                    // text itself is a safe key here.
                    key = { it },
                    contentType = { "search-history" }
                ) { term ->
                    AssistChip(
                        onClick = { viewModel.setSearchQuery(term) },
                        label = { Text(term, color = AuraTextPrimary, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = AuraTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (results.isEmpty() && query.isNotEmpty() && !isConnecting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found. Try another search.", color = AuraTextSecondary)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = resultsKeyed,
                key = { it.second },
                contentType = { "track" }
            ) { item ->
                val track = item.first
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
