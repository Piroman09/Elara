package com.aesthetic.spotify.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.spotify.data.model.Track
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.theme.AuraPurple

@Composable
fun TextInputDialog(
    title: String,
    initialText: String = "",
    confirmLabel: String = "Create",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Playlist name", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AuraPurple,
                    focusedBorderColor = AuraPurple,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f)
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text(confirmLabel, color = AuraPurple, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1A1A1C)
    )
}

/** Pick a playlist for [track], or create one and add it straight away. */
@Composable
fun AddToPlaylistDialog(
    viewModel: MainViewModel,
    track: Track,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        TextInputDialog(
            title = "New playlist",
            confirmLabel = "Create & add",
            onConfirm = { name ->
                val playlist = viewModel.createPlaylist(name)
                viewModel.addTrackToPlaylist(playlist.id, track)
                onDismiss()
            },
            onDismiss = { creating = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AuraPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Create new playlist", color = AuraPurple, fontWeight = FontWeight.SemiBold)
                }

                if (playlists.isEmpty()) {
                    Text(
                        "No playlists yet — create your first one.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            val already = playlist.tracks.any { it.id == track.id }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!already) {
                                            viewModel.addTrackToPlaylist(playlist.id, track)
                                            onDismiss()
                                        }
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${playlist.tracks.size} songs",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                if (already) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Already in playlist",
                                        tint = Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = Color(0xFF1A1A1C)
    )
}
