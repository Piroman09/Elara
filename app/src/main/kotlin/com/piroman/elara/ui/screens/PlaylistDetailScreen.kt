package com.piroman.elara.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.piroman.elara.data.model.SongCountFormat
import com.piroman.elara.data.model.Track
import com.piroman.elara.data.playlist.Playlist
import com.piroman.elara.data.playlist.PlaylistOrdering
import com.piroman.elara.data.playlist.PlaylistSort
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.AddToPlaylistDialog
import com.piroman.elara.ui.components.PlayShuffleRow
import com.piroman.elara.ui.components.TextInputDialog
import com.piroman.elara.ui.components.TrackArtwork
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCardStrong
import com.piroman.elara.ui.theme.AuraDownload
import com.piroman.elara.ui.theme.AuraError
import com.piroman.elara.ui.theme.AuraField
import com.piroman.elara.ui.theme.AuraMenu
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraTextTertiary
import com.piroman.elara.ui.theme.AuraViolet
import com.piroman.elara.ui.theme.LikedSongsBrush
import com.piroman.elara.ui.theme.Motion
import com.piroman.elara.ui.theme.PlaylistBrush
import kotlin.math.roundToInt

private val SearchFieldShape = RoundedCornerShape(14.dp)

/**
 * Fixed geometry of a reorder row. Drag distance is divided by this to decide
 * how many slots to move, so the row height and the spacing both have to be
 * constants — a wrapping row would make the drop land in the wrong place.
 */
private val ReorderRowHeight = 64.dp
private val ReorderRowSpacing = 4.dp

/** Below this many songs a search box is more clutter than help. */
private const val SEARCH_MIN_TRACKS = 8

/**
 * Playlist detail for user playlists and the "liked" pinned special, which
 * resolves from its own state flow.
 *
 * **Downloads is deliberately not handled here.** It used to be a second pinned
 * special, but a playlist header gave it a "Download all" button that could only
 * ever be a no-op (every song in the list is already downloaded) alongside a
 * search box, sort menu and reorder mode that mean nothing for a folder the app
 * manages itself — and a `Playlist` carries no size, so the header could not
 * answer how much space the downloads occupy. It now has its own screen.
 *
 * Three orderings are in play and they are not interchangeable:
 * the *stored* order ([Playlist.tracks]), the *display* order (sort + filter,
 * see [PlaylistOrdering]) and the *drag* order (only meaningful while the
 * display order is the stored one). Reordering is therefore offered only when
 * nothing is filtered or sorted — dragging row 4 when the list is sorted by
 * title would otherwise move an arbitrary song.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    val context = LocalContext.current

    // Resolved once for the whole list instead of once per row — see
    // SearchTrackItem for why the per-row version was a problem.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedIds = remember(downloadedEntries) {
        downloadedEntries.mapTo(HashSet()) { it.track.id }
    }

    val isSpecial = playlistId == "liked"

    val playlist: Playlist? = when (playlistId) {
        "liked" -> Playlist(id = "liked", name = "Liked Songs", tracks = likedTracks)
        else -> playlists.firstOrNull { it.id == playlistId }
    }

    val headerIcon: ImageVector = when (playlistId) {
        "liked" -> Icons.Default.Favorite
        else -> Icons.Default.QueueMusic
    }
    val headerBrush = when (playlistId) {
        "liked" -> LikedSongsBrush
        else -> PlaylistBrush
    }

    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var addPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    // ── View state for search / sort / reorder ────────────────────────────
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(PlaylistSort.CUSTOM) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val storedTracks = playlist?.tracks.orEmpty()
    val displayed = remember(storedTracks, sort, query) {
        PlaylistOrdering.filter(PlaylistOrdering.apply(storedTracks, sort), query)
    }

    val canReorder = !isSpecial && query.isBlank() && sort == PlaylistSort.CUSTOM

    // Lazy keys: identity **plus an occurrence suffix**, never the bare id.
    //
    // A playlist can legitimately hold the same track twice — a Spotify import
    // and a restored backup both store their track list as-is, with no
    // de-duplication — and duplicate keys crash a lazy list outright. The bare
    // index is equally wrong here: reorder mode animates rows with
    // `animateItemPlacement`, and baking the index into the key makes every row
    // look brand new after a drop, so nothing has anything to animate between.
    // Same rule and same reason as QueueDrawer; the loop lives in LazyKeys.
    val displayedKeyed = remember(displayed) { LazyKeys.withOccurrence(displayed) { it.id } }

    val hasUndownloaded = remember(displayed, downloadedIds) {
        displayed.any { it.id !in downloadedIds }
    }

    // Sorting or filtering invalidates the drag indices, so leave the mode
    // rather than let a drag commit against the wrong rows.
    LaunchedEffect(canReorder) {
        if (!canReorder) reorderMode = false
    }

    // Drag state, hoisted here because the row that starts a drag is not the
    // row that finishes it.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val stepPx = with(LocalDensity.current) { (ReorderRowHeight + ReorderRowSpacing).toPx() }

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
                // A static breadcrumb — the playlist's own name is the title
                // below. This used to read `if (isSpecial) "Playlist" else
                // "Playlist"`, which is the same string either way.
                "Playlist",
                color = AuraTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )

            if (reorderMode) {
                // One exit that is impossible to miss, instead of leaving the
                // user to guess that the toggle turns itself off.
                TextButton(onClick = { reorderMode = false }) {
                    Text("Done", color = AuraViolet, fontWeight = FontWeight.Bold)
                }
            } else {
                if (canReorder) {
                    IconButton(onClick = { reorderMode = true }) {
                        Icon(
                            Icons.Default.Reorder,
                            contentDescription = "Reorder songs",
                            tint = Color.White
                        )
                    }
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                    }
                    SortMenu(
                        expanded = sortMenuOpen,
                        current = sort,
                        // Sorting is display-only; committing is an explicit,
                        // separate action so a stray tap can't flatten a
                        // hand-arranged playlist.
                        onSaveOrder = if (!isSpecial) {
                            {
                                viewModel.savePlaylistOrder(playlist.id, displayed)
                                sortMenuOpen = false
                            }
                        } else null,
                        onSelect = { sort = it },
                        onDismiss = { sortMenuOpen = false }
                    )
                }
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
                            songCountLabel(displayed.size, playlist.tracks.size),
                            color = AuraTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Play/Shuffle plus this screen's "Download all", which is passed
                // as the row's trailing slot. The wrapping that keeps "Download
                // all" from breaking mid-word now lives in `PlayShuffleRow`.
                PlayShuffleRow(
                    onPlay = {
                        if (displayed.isNotEmpty()) {
                            viewModel.playTrack(displayed.first(), displayed)
                        }
                    },
                    onShuffle = {
                        if (displayed.isNotEmpty()) {
                            viewModel.playTrack(displayed.random(), displayed)
                        }
                    },
                    enabled = displayed.isNotEmpty(),
                    trailing = {
                        OutlinedButton(
                            onClick = {
                                // Data Saver blocks a bulk fetch; say so rather than
                                // letting the button look broken.
                                if (!viewModel.downloadPlaylistTracks(displayed)) {
                                    Toast.makeText(
                                        context,
                                        "Data Saver is on — turn it off to download a whole playlist",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = hasUndownloaded
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Download all",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                )

                // Fixed rather than scrolled away: the query is the thing the
                // user needs to be able to see and clear.
                if (playlist.tracks.size >= SEARCH_MIN_TRACKS || query.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SearchFieldShape),
                        placeholder = {
                            Text("Find in this playlist", color = AuraTextSecondary)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = AuraTextSecondary
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = AuraTextSecondary
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        ),
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
                }

                if (reorderMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Drag the handle on the right to change the order.",
                        color = AuraTextTertiary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (displayed.isEmpty()) {
                item {
                    Text(
                        if (query.isNotBlank()) {
                            "No songs in this playlist match \"$query\"."
                        } else {
                            when (playlistId) {
                                "liked" -> "Songs you like will appear here — tap the heart on any song."
                                else -> "No songs yet — use the add-to-playlist button on any song."
                            }
                        },
                        color = AuraTextSecondary
                    )
                }
            } else if (reorderMode) {
                itemsIndexed(
                    items = displayedKeyed,
                    key = { _, item -> item.second },
                    contentType = { _, _ -> "reorder" }
                ) { index, item ->
                    val track = item.first
                    val isDragging = dragIndex == index
                    ReorderTrackRow(
                        track = track,
                        position = index,
                        isDragging = isDragging,
                        onDragStart = {
                            dragIndex = index
                            dragOffsetY = 0f
                        },
                        onDragBy = { dragOffsetY += it },
                        onDragFinish = {
                            val from = dragIndex
                            if (from != null && stepPx > 0f) {
                                val shift = (dragOffsetY / stepPx).roundToInt()
                                val to = (from + shift).coerceIn(0, displayed.lastIndex)
                                if (to != from) {
                                    viewModel.moveTrackInPlaylist(playlistId, from, to)
                                }
                            }
                            dragIndex = null
                            dragOffsetY = 0f
                        },
                        modifier = Modifier
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
                    )
                }
            } else {
                items(
                    items = displayedKeyed,
                    key = { it.second },
                    contentType = { "track" }
                ) { item ->
                    val track = item.first
                    SearchTrackItem(
                        track = track,
                        onClick = { viewModel.playTrack(track, displayed) },
                        viewModel = viewModel,
                        // Always offered, including inside a user playlist. It
                        // used to be hidden there on the reasoning that the song
                        // is already in *a* playlist — but the useful action is
                        // adding it to a *different* one, and hiding it made the
                        // long-press menu inconsistent between screens.
                        onAddToPlaylist = { addPlaylistTrack = track },
                        showSource = sourceSetting == com.piroman.elara.data.source.SourceSetting.AUTO,
                        downloadState = downloadStates[track.id],
                        isDownloaded = track.id in downloadedIds,
                        onRemoveFromPlaylist = {
                            when (playlistId) {
                                "liked" -> viewModel.toggleLikeTrack(track)
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

/**
 * "12 songs" normally, but "3 of 12 songs" once a filter is hiding some — a
 * bare "3 songs" on a 12-song playlist reads as data loss.
 */
/**
 * `"130 songs"`, or `"12 of 130 songs"` while a search is filtering the list.
 *
 * Pluralisation comes from [SongCountFormat] because this used to append
 * `" songs"` unconditionally, so a one-song playlist printed **"1 songs"** — the
 * same mistake that previously shipped as "1 hours" and "1 minutes" in two
 * hand-rolled duration labels.
 *
 * The count that decides the plural is the **total**, not the number currently
 * shown: "1 of 5 songs", never "1 of 5 song".
 */
private fun songCountLabel(shown: Int, total: Int): String {
    val label = SongCountFormat.songs(total)
    return if (shown == total) label else "$shown of $label"
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: PlaylistSort,
    onSaveOrder: (() -> Unit)?,
    onSelect: (PlaylistSort) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        PlaylistSort.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        option.label,
                        color = if (option == current) AuraViolet else AuraTextPrimary
                    )
                },
                trailingIcon = {
                    if (option == current) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AuraViolet)
                    }
                },
                onClick = {
                    onSelect(option)
                    onDismiss()
                }
            )
        }

        // Only meaningful once the display order differs from what is stored.
        if (onSaveOrder != null && current != PlaylistSort.CUSTOM) {
            Divider(color = AuraTextTertiary.copy(alpha = 0.3f))
            DropdownMenuItem(
                text = { Text("Save this order", color = AuraDownload) },
                onClick = onSaveOrder
            )
        }
    }
}

/**
 * A reorder-mode row: artwork, title, and a drag handle.
 *
 * Deliberately not [SearchTrackItem]. That row owns a long-press gesture for
 * its context menu, which would fight the drag; and it has no slot for a drag
 * handle. This is a different row for a different job, not a second copy of the
 * same one — it carries no menu, no download state and no source label.
 */
@Composable
private fun ReorderTrackRow(
    track: Track,
    position: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ReorderRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDragging) AuraCardStrong else Color.Transparent)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${position + 1}",
            color = AuraTextTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp)
        )
        TrackArtwork(url = track.albumArt, size = 44.dp, cornerRadius = 6.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = AuraTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
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

        // The gesture lives on the handle, not the row, so the list can still
        // be scrolled by dragging anywhere else.
        Box(
            modifier = Modifier
                .size(44.dp)
                .pointerInput(track.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDragBy(amount.y)
                        },
                        onDragEnd = { onDragFinish() },
                        onDragCancel = { onDragFinish() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder ${track.title}",
                tint = AuraTextSecondary
            )
        }
    }
}
