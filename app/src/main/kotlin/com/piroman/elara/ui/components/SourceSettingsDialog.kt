package com.aesthetic.spotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.spotify.data.diagnostics.PlaybackDiagnostics
import com.aesthetic.spotify.data.source.SourceSetting
import com.aesthetic.spotify.ui.theme.AuraPurple
import com.aesthetic.spotify.ui.theme.AuraTextSecondary
import com.aesthetic.spotify.ui.theme.AuraWarning
import kotlinx.coroutines.launch
import com.aesthetic.spotify.ui.theme.AuraMenu
import com.aesthetic.spotify.ui.theme.AuraError
import com.aesthetic.spotify.ui.theme.AuraDownload

/** Music Source picker: Auto / JioSaavn / YouTube Music. */
@Composable
fun SourceSettingsDialog(
    current: SourceSetting,
    onSelect: (SourceSetting) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music Source", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Where ELARA searches and plays music from.",
                    color = AuraTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                SourceSetting.entries.forEach { setting ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(setting) }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = setting == current,
                            onClick = { onSelect(setting) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AuraPurple,
                                unselectedColor = AuraTextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(setting.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (setting) {
                                    SourceSetting.AUTO -> "Search both, tagged by source"
                                    SourceSetting.JIOSAAVN -> "JioSaavn only"
                                    SourceSetting.YOUTUBE_MUSIC -> "YouTube Music only"
                                },
                                color = AuraTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (setting == current) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = AuraDownload
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Diagnostics (download/playback failures) ────────────
                val context = androidx.compose.ui.platform.LocalContext.current
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val scope = rememberCoroutineScope()
                var diagLines by remember { mutableStateOf(listOf<String>()) }

                TextButton(onClick = {
                    // Off the main thread. This used to `readText()` the whole
                    // log — which grows on every playback failure — just to keep
                    // the last 30 lines, blocking the UI thread while it did.
                    scope.launch {
                        diagLines = PlaybackDiagnostics.tail(context, 30)
                    }
                }) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = AuraWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load diagnostics log", color = AuraWarning, fontSize = 13.sp)
                }

                if (diagLines.isNotEmpty()) {
                    Text(
                        diagLines.joinToString("\n"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    TextButton(onClick = {
                        clipboard.setText(
                            androidx.compose.ui.text.AnnotatedString(diagLines.joinToString("\n"))
                        )
                    }) {
                        Text("Copy to clipboard", color = AuraPurple, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = AuraPurple, fontWeight = FontWeight.Bold) }
        },
        containerColor = AuraMenu
    )
}
