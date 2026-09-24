package com.piroman.elara.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.data.backup.RestoreReport
import com.piroman.elara.data.diagnostics.PlaybackDiagnostics
import com.piroman.elara.data.source.SourceSetting
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.piroman.elara.ui.theme.AuraMenu
import com.piroman.elara.ui.theme.AuraError
import com.piroman.elara.ui.theme.AuraDownload

/**
 * Settings: Music Source picker (Auto / JioSaavn / YouTube Music), backup &
 * restore, and the diagnostics log.
 *
 * [exportJson] and [importJson] are injected rather than reaching for the
 * ViewModel, so this stays a presentation component and the file I/O lives with
 * the storage framework here.
 */
@Composable
fun SourceSettingsDialog(
    current: SourceSetting,
    onSelect: (SourceSetting) -> Unit,
    onDismiss: () -> Unit,
    exportJson: () -> String,
    importJson: (String) -> RestoreReport?,
    dataSaverEnabled: Boolean = false,
    onDataSaverChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    // SAF, not a file path: the user picks the destination, the app needs no
    // storage permission, and it works the same on every Android version.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupMessage = runCatching {
                // Both halves on IO: serialising the library and writing it are
                // each proportional to library size, and neither belongs on the
                // main thread.
                val json = withContext(Dispatchers.IO) { exportJson() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    } ?: error("could not open the chosen file for writing")
                }
                "Backup written. Keep the file somewhere safe."
            }.getOrElse { "Backup failed: ${it.message}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupMessage = runCatching {
                val report = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("could not read the chosen file")
                    importJson(text)
                }
                report?.let { "Restored ${it.summary}." }
                    ?: "That file is not an ELARA backup."
            }.getOrElse { it.message ?: "Restore failed." }
        }
    }

    // Restore overwrites the whole library, so it is confirmed *before* the file
    // picker opens — asking afterwards would invite the user to choose a file and
    // only then discover the action was destructive.
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Replace your library?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Restoring replaces every playlist, liked song, history entry and play count on this device with what is in the backup file. This cannot be undone.",
                    color = AuraTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    importLauncher.launch(arrayOf("application/json"))
                }) {
                    Text("Choose file", color = AuraError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel", color = AuraTextSecondary)
                }
            }
        )
    }

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

                // ── Data saver ─────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDataSaverChange(!dataSaverEnabled) }
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Data saver", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Avoid background network use for autoplay and batch downloads.",
                            color = AuraTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = dataSaverEnabled,
                        onCheckedChange = onDataSaverChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AuraPurple,
                            checkedTrackColor = AuraPurple.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Backup & restore ───────────────────────────────────
                Text(
                    "Backup",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Save your playlists, liked songs, history and play counts to a file, or bring them back. Useful before a reinstall.",
                    color = AuraTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    TextButton(onClick = {
                        backupMessage = null
                        exportLauncher.launch(backupFileName())
                    }) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = AuraPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", color = AuraPurple, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        backupMessage = null
                        showRestoreConfirm = true
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = AuraPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", color = AuraPurple, fontSize = 13.sp)
                    }
                }
                backupMessage?.let { message ->
                    Text(
                        message,
                        color = AuraTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Diagnostics (download/playback failures) ────────────
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
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

/**
 * Default name offered to the file picker. Dated so that several backups taken
 * over time do not collide in the same folder — the picker happily overwrites,
 * and a silently overwritten backup is worse than none.
 */
private fun backupFileName(): String {
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())
    return "elara-backup-$stamp.json"
}
