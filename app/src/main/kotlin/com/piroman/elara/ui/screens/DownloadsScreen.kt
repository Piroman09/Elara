package com.piroman.elara.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.data.download.DownloadSizeFormat
import com.piroman.elara.data.model.SongCountFormat
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.PlayShuffleRow
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraTextTertiary
import com.piroman.elara.ui.theme.DownloadsBrush

/**
 * Everything saved for offline listening: how many songs, how much space they
 * take, and the list itself.
 *
 * This replaced viewing downloads as a special case of `PlaylistDetailScreen`
 * (`playlist/downloads`). That reuse looked free but was not: it gave the screen
 * a playlist header whose **"Download all" button was meaningless** — every song
 * in the list is already downloaded — plus a search box, a sort menu and a
 * reorder mode, none of which mean anything for a folder the app manages itself.
 * And it could not answer the two questions a downloads screen exists to answer,
 * because a `Playlist` carries no size and the header only ever printed a song
 * count.
 *
 * Rows are the shared [SearchTrackItem], so a download is removed from the same
 * menu as everywhere else (`TrackContextMenu`'s "Remove download") rather than
 * through a second, downloads-only control that would drift.
 */
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.downloadedTracks.collectAsState()
    val totalBytes by viewModel.downloadedBytes.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    val tracks = remember(entries) { entries.map { it.track } }
    val downloadedIds = remember(entries) {
        entries.mapTo(HashSet()) { it.track.id }
    }
    // Identity plus occurrence, never the bare id or the index — a lazy list
    // crashes outright on duplicate keys. Same rule as every other list.
    val keyed = remember(tracks) { LazyKeys.withOccurrence(tracks) { it.id } }

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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Downloads",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)
        ) {
            item(key = "summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DownloadsBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Downloads",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summaryLabel(tracks.size, totalBytes),
                            color = AuraTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = AuraTextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nothing saved offline yet",
                            color = AuraTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the download icon on any song to keep it " +
                                "available without a connection.",
                            color = AuraTextSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                item(key = "actions") {
                    PlayShuffleRow(
                        onPlay = { viewModel.playTrack(tracks.first(), tracks) },
                        onShuffle = { viewModel.playTrack(tracks.random(), tracks) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(
                    items = keyed,
                    key = { it.second },
                    contentType = { "download" }
                ) { item ->
                    val track = item.first
                    SearchTrackItem(
                        track = track,
                        onClick = { viewModel.playTrack(track, tracks) },
                        viewModel = viewModel,
                        showSource = true,
                        downloadState = downloadStates[track.id],
                        isDownloaded = track.id in downloadedIds
                    )
                }
            }
        }
    }
}

/**
 * `"130 songs · 520.0 MB"`, or just `"130 songs"` while the size is still being
 * summed on a background thread.
 *
 * The size is **omitted rather than shown as `0 B`** in that window: the entry
 * list loads synchronously from the index but the byte total does not, so the
 * alternative is a header that briefly claims the library occupies no space.
 * Singular/plural comes from [SongCountFormat] — this label used to be able to
 * print "1 songs".
 */
internal fun summaryLabel(songCount: Int, totalBytes: Long?): String {
    val songs = SongCountFormat.songs(songCount)
    return if (totalBytes == null) songs
    else "$songs · ${DownloadSizeFormat.format(totalBytes)}"
}
