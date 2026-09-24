package com.aesthetic.spotify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.spotify.data.playlist.Playlist
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.theme.AuraContainer
import com.aesthetic.spotify.ui.theme.AuraOutline
import com.aesthetic.spotify.ui.theme.AuraSuccess
import com.aesthetic.spotify.ui.theme.AuraSurface
import com.aesthetic.spotify.ui.theme.AuraError
import com.aesthetic.spotify.ui.theme.AuraTextSecondary
import com.aesthetic.spotify.ui.theme.SpotifyBrand
import kotlinx.coroutines.launch


sealed class ImportDialogState {
    object Input : ImportDialogState()
    data class Progress(
        val playlistName: String = "",
        val coverArt: String = "",
        val imported: Int = 0,
        val total: Int = 0
    ) : ImportDialogState()
    data class Success(
        val summary: MainViewModel.SpotifyImportSummary
    ) : ImportDialogState()
    data class Error(
        val message: String
    ) : ImportDialogState()
}

@Composable
fun ImportSpotifyDialog(
    viewModel: MainViewModel,
    onSuccess: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var dialogState by remember { mutableStateOf<ImportDialogState>(ImportDialogState.Input) }

    AlertDialog(
        onDismissRequest = {
            if (dialogState !is ImportDialogState.Progress) {
                onDismiss()
            }
        },
        title = {
            when (dialogState) {
                is ImportDialogState.Input -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SpotifyBrand.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = SpotifyBrand,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import from Spotify",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }
                is ImportDialogState.Progress -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = SpotifyBrand,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Importing Playlist...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }
                is ImportDialogState.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AuraSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import Complete!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }
                is ImportDialogState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = AuraError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import Failed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (val state = dialogState) {
                    is ImportDialogState.Input -> {
                        Text(
                            text = "Paste any public Spotify playlist link. We'll import all songs from the playlist directly into your library with no 100-song limit.",
                            color = Color(0xFFB0B0BA),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "https://open.spotify.com/playlist/...",
                                    color = AuraTextSecondary,
                                    fontSize = 13.sp
                                )
                            },
                            trailingIcon = {
                                if (urlText.isNotEmpty()) {
                                    IconButton(onClick = { urlText = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = AuraTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            urlText = clip.trim()
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.ContentPaste,
                                            contentDescription = "Paste from clipboard",
                                            tint = SpotifyBrand,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = SpotifyBrand,
                                focusedBorderColor = SpotifyBrand,
                                unfocusedBorderColor = AuraOutline
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    is ImportDialogState.Progress -> {
                        // Playlist Info preview if name is available
                        if (state.playlistName.isNotBlank()) {
                            Surface(
                                color = AuraContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                                    .border(1.dp, AuraOutline, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackArtwork(
                                        url = state.coverArt,
                                        size = 44.dp,
                                        cornerRadius = 8.dp,
                                        contentDescription = state.playlistName
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = state.playlistName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (state.total > 0) "${state.total} songs in playlist" else "Reading playlist...",
                                            color = AuraTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Progress bar
                        val progressFraction = if (state.total > 0) {
                            (state.imported.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        if (state.total > 0) {
                            LinearProgressIndicator(
                                progress = progressFraction,
                                color = SpotifyBrand,
                                trackColor = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                color = SpotifyBrand,
                                trackColor = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats counters row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.total > 0) {
                                    "Imported ${state.imported} / ${state.total} songs"
                                } else if (state.imported > 0) {
                                    "Imported ${state.imported} songs..."
                                } else {
                                    "Connecting to Spotify..."
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (state.total > 0) {
                                val percent = (progressFraction * 100).toInt()
                                Text(
                                    text = "$percent%",
                                    color = SpotifyBrand,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Paginating through entire playlist. Please keep this screen open.",
                            color = Color(0xFF8E8E98),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    is ImportDialogState.Success -> {
                        val summary = state.summary

                        // Playlist Header Card
                        Surface(
                            color = AuraContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AuraOutline, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackArtwork(
                                    url = summary.coverArt,
                                    size = 52.dp,
                                    cornerRadius = 10.dp,
                                    contentDescription = summary.playlist.name
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = summary.playlist.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Saved in your Library",
                                        color = AuraSuccess,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Breakdown Screen
                        Text(
                            text = "IMPORT SUMMARY",
                            color = Color(0xFF8E8E98),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Spotify Total Card
                            Surface(
                                color = AuraContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, AuraOutline, RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Spotify Playlist",
                                        color = AuraTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${summary.totalInSpotify}",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "total songs",
                                        color = Color(0xFF71717A),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // App Imported Card
                            Surface(
                                color = AuraContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        SpotifyBrand.copy(alpha = 0.5f),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Imported to App",
                                        color = SpotifyBrand,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${summary.importedCount}",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "playable songs",
                                        color = Color(0xFF71717A),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (summary.importedCount == summary.totalInSpotify) {
                            Text(
                                text = "✨ All ${summary.importedCount} songs were successfully imported into your app library.",
                                color = Color(0xFFD4D4D8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        } else {
                            val skipped = summary.totalInSpotify - summary.importedCount
                            Text(
                                text = "Note: $skipped track(s) were unavailable or restricted on Spotify and were omitted.",
                                color = Color(0xFFE4E4E7),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    is ImportDialogState.Error -> {
                        Surface(
                            color = Color(0xFF2A1515),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x66FF5252), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = AuraError,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (val state = dialogState) {
                is ImportDialogState.Input -> {
                    Button(
                        onClick = {
                            val input = urlText.trim()
                            if (input.isNotBlank()) {
                                dialogState = ImportDialogState.Progress(
                                    imported = 0,
                                    total = 0
                                )
                                scope.launch {
                                    val result = viewModel.importSpotifyPlaylist(
                                        urlOrId = input,
                                        onProgress = { imported, total, name, coverArt ->
                                            dialogState = ImportDialogState.Progress(
                                                playlistName = name,
                                                coverArt = coverArt,
                                                imported = imported,
                                                total = total
                                            )
                                        }
                                    )
                                    result.onSuccess { summary ->
                                        dialogState = ImportDialogState.Success(summary)
                                    }.onFailure { err ->
                                        dialogState = ImportDialogState.Error(
                                            err.message ?: "Failed to import playlist"
                                        )
                                    }
                                }
                            }
                        },
                        enabled = urlText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyBrand,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Import All Songs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                is ImportDialogState.Progress -> {
                    // Do not show confirm button during active download
                }

                is ImportDialogState.Success -> {
                    Button(
                        onClick = {
                            onSuccess(state.summary.playlist)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyBrand,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Playlist", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                is ImportDialogState.Error -> {
                    Button(
                        onClick = { dialogState = ImportDialogState.Input },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyBrand,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Try Again", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            when (dialogState) {
                is ImportDialogState.Input -> {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = AuraTextSecondary, fontSize = 13.sp)
                    }
                }
                is ImportDialogState.Progress -> {
                    // Prevent accidental dismissal while actively importing
                }
                is ImportDialogState.Success -> {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = AuraTextSecondary, fontSize = 13.sp)
                    }
                }
                is ImportDialogState.Error -> {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = AuraTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        },
        containerColor = AuraSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
