package com.piroman.elara.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piroman.elara.data.download.DownloadManager
import com.piroman.elara.data.model.Track
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.theme.AuraError
import com.piroman.elara.ui.theme.AuraDownload

/**
 * Download control with three states: idle (download icon), downloading
 * (progress spinner), done (checkmark — tap to remove the download).
 */
@Composable
fun DownloadButton(
    track: Track,
    viewModel: MainViewModel,
    tint: Color = Color.White,
    iconSize: Dp = 22.dp
) {
    val states by viewModel.downloadStates.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()
    val isDownloaded = downloadedEntries.any { it.track.id == track.id }

    when (val state = states[track.id]) {
        is DownloadManager.State.Downloading -> {
            Box(
                modifier = Modifier.size(iconSize + 14.dp),
                contentAlignment = Alignment.Center
            ) {
                val progress = state.progress
                if (progress in 0f..1f) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(iconSize - 2.dp),
                        strokeWidth = 2.dp,
                        color = tint
                    )
                } else {
                    // A negative progress means the server sent no
                    // `Content-Length`, so there is no fraction to draw. This
                    // must be the **indeterminate** overload: passing
                    // `progress = 0f` to the determinate one (as this did) drew a
                    // permanently empty ring, which reads as a stuck or finished
                    // download rather than a running one — and it is the common
                    // case, because music CDNs often omit the length.
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize - 2.dp),
                        strokeWidth = 2.dp,
                        color = tint
                    )
                }
            }
        }
        DownloadManager.State.Done, null -> if (isDownloaded) {
            IconButton(onClick = { viewModel.deleteDownload(track.id) }) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Downloaded — tap to remove",
                    tint = AuraDownload,
                    modifier = Modifier.size(iconSize + 2.dp)
                )
            }
        } else {
            IconButton(onClick = { viewModel.downloadTrack(track) }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        is DownloadManager.State.Error -> {
            IconButton(onClick = { viewModel.downloadTrack(track) }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = state.message,
                    tint = AuraError,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
