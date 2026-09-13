package com.aesthetic.spotify.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aesthetic.spotify.data.download.DownloadManager
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.theme.AuraDownload
import com.aesthetic.spotify.ui.theme.AuraError
import com.aesthetic.spotify.ui.theme.AuraMenu
import com.aesthetic.spotify.ui.theme.AuraTextPrimary
import com.aesthetic.spotify.ui.theme.AuraTextSecondary

/**
 * The one track action menu, opened by long-pressing a song or tapping its
 * overflow button.
 *
 * Extracted because four different row shapes need it — the list row
 * ([SearchTrackItem]) and Home's two card shapes — and the download-state
 * branching had already drifted between its two previous copies. Anything added
 * here appears everywhere at once.
 *
 * Render it inside a [androidx.compose.foundation.layout.Box] at the call site:
 * a `DropdownMenu` anchors to its parent, so as a direct child of a full-width
 * row it opens at the row's left edge instead of under the row's own corner.
 *
 * The menu is a pure view. Every action goes through [MainViewModel]; nothing
 * here reaches into `PlaybackManager` or `DownloadManager` directly.
 *
 * @param onAddToPlaylist omit to hide the entry (e.g. inside a playlist, where
 *   "add" is meaningless but "remove" is not).
 * @param onRemoveFromPlaylist omit outside a playlist context.
 * @param downloadState in-flight state for this track, or null if idle.
 * @param isDownloaded resolved by the caller — see [SearchTrackItem]'s note on
 *   why rows must not each scan the download list themselves.
 */
@Composable
fun TrackContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    track: Track,
    viewModel: MainViewModel,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    downloadState: DownloadManager.State? = null,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.background(AuraMenu)
    ) {
        if (onAddToPlaylist != null) {
            DropdownMenuItem(
                text = { Text("Add to playlist", color = AuraTextPrimary) },
                leadingIcon = {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = AuraTextPrimary)
                },
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )
        }

        DropdownMenuItem(
            text = { Text("Add to queue", color = AuraTextPrimary) },
            leadingIcon = {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = AuraTextPrimary)
            },
            onClick = {
                onDismiss()
                // `addToQueue` reports whether it had to start playback because
                // nothing was playing. Saying "Added to queue" in that case would
                // be a lie — the song is playing right now, not waiting.
                val startedPlaying = viewModel.addToQueue(track)
                Toast.makeText(
                    context,
                    if (startedPlaying) "Playing now" else "Added to queue",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        when {
            downloadState is DownloadManager.State.Downloading -> {
                DropdownMenuItem(
                    text = { Text("Downloading…", color = AuraTextSecondary) },
                    leadingIcon = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AuraTextPrimary
                        )
                    },
                    // Deliberately inert: there is nothing to cancel yet, and
                    // dismissing would only hide the progress it exists to show.
                    onClick = {}
                )
            }

            isDownloaded -> {
                DropdownMenuItem(
                    text = { Text("Remove download", color = AuraTextPrimary) },
                    leadingIcon = {
                        // AuraDownload is the downloads emerald; AuraSuccess is the
                        // Spotify-import green and means something else entirely.
                        Icon(Icons.Default.DownloadDone, contentDescription = null, tint = AuraDownload)
                    },
                    onClick = {
                        onDismiss()
                        viewModel.deleteDownload(track.id)
                    }
                )
            }

            downloadState is DownloadManager.State.Error -> {
                DropdownMenuItem(
                    text = { Text("Download failed — retry", color = AuraError) },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null, tint = AuraError)
                    },
                    onClick = {
                        onDismiss()
                        viewModel.downloadTrack(track)
                    }
                )
            }

            else -> {
                DropdownMenuItem(
                    text = { Text("Download", color = AuraTextPrimary) },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null, tint = AuraTextPrimary)
                    },
                    onClick = {
                        onDismiss()
                        viewModel.downloadTrack(track)
                    }
                )
            }
        }

        if (onRemoveFromPlaylist != null) {
            DropdownMenuItem(
                text = { Text("Remove from playlist", color = AuraError) },
                leadingIcon = {
                    Icon(Icons.Default.Close, contentDescription = null, tint = AuraError)
                },
                onClick = {
                    onDismiss()
                    onRemoveFromPlaylist()
                }
            )
        }
    }
}
