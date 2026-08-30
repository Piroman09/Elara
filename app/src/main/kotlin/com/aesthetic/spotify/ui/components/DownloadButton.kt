package com.aesthetic.spotify.ui.components

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
import com.aesthetic.spotify.data.download.DownloadManager
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.ui.MainViewModel

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

    when (val state = states[track.id]) {
        is DownloadManager.State.Downloading -> {
            Box(
                modifier = Modifier.size(iconSize + 14.dp),
                contentAlignment = Alignment.Center
            ) {
                val progress = state.progress
                CircularProgressIndicator(
                    progress = if (progress in 0f..1f) progress else 0f,
                    modifier = Modifier.size(iconSize - 2.dp),
                    strokeWidth = 2.dp,
                    color = tint
                )
            }
        }
        is DownloadManager.State.Done -> {
            IconButton(onClick = { viewModel.deleteDownload(track.id) }) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Downloaded — tap to remove",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(iconSize + 2.dp)
                )
            }
        }
        is DownloadManager.State.Error -> {
            IconButton(onClick = { viewModel.downloadTrack(track) }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = state.message,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        else -> {
            IconButton(onClick = { viewModel.downloadTrack(track) }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
