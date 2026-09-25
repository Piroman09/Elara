package com.piroman.elara.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.BuildConfig
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCardStrong
import com.piroman.elara.ui.theme.AuraContainer
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraTextTertiary
import com.piroman.elara.ui.theme.AuraViolet

/**
 * Player Settings screen matching the glass-card design from the reference.
 *
 * Sections: PLAYBACK (autoplay, smart shuffle, fade out, equalizer) and
 * NETWORK (data saver).
 * Each card has rounded corners, a dark tinted background, and rows with an
 * icon pill, title, description and a control.
 */
@Composable
fun PlayerSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val autoplay by viewModel.autoplayEnabled.collectAsState()
    val crossfade by viewModel.crossfadeSeconds.collectAsState()
    val smartShuffle by viewModel.smartShuffleEnabled.collectAsState()
    val dataSaver by viewModel.dataSaverEnabled.collectAsState()

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
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
            }
            Text(
                text = "Player Settings",
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
            item { SectionHeader("PLAYBACK") }

            item {
                SettingsCard {
                    ToggleRow(
                        icon = Icons.Default.PlaylistAdd,
                        title = "Auto Play",
                        description = "Enqueue similar songs when the queue ends.",
                        checked = autoplay,
                        onCheckedChange = { viewModel.setAutoplay(it) }
                    )

                    DividerLine()

                    ToggleRow(
                        icon = Icons.Default.Shuffle,
                        title = "Smart Shuffle",
                        description = "Prioritise similar songs when shuffling.",
                        checked = smartShuffle,
                        onCheckedChange = { viewModel.setSmartShuffle(it) }
                    )

                    DividerLine()

                    SliderRow(
                        icon = Icons.Default.Timeline,
                        title = "Fade Out",
                        description = if (crossfade == 0) "Off" else "${crossfade}s fade at the end of a track",
                        value = crossfade.toFloat(),
                        valueRange = 0f..12f,
                        steps = 5, // 2s increments: 0,2,4,6,8,10,12
                        onValueChange = { viewModel.setCrossfade(it.toInt()) },
                        valueLabel = if (crossfade == 0) "Off" else "${crossfade}s"
                    )

                    DividerLine()

                    ChevronRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Equalizer",
                        description = "System audio effects panel.",
                        onClick = {
                            val intent = Intent(
                                AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL
                            ).apply {
                                // Without the session id the panel opens unscoped —
                                // and on some devices shows nothing at all — so
                                // point it at our own playback.
                                val session = viewModel.audioSessionId
                                if (session > 0) {
                                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                                }
                                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                            runCatching {
                                context.startActivity(intent)
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "No equalizer available on this device",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item { SectionHeader("NETWORK") }

            item {
                SettingsCard {
                    ToggleRow(
                        icon = Icons.Default.NetworkCheck,
                        title = "Data Saver",
                        description = "Avoid background network use for autoplay and batch downloads.",
                        checked = dataSaver,
                        onCheckedChange = { viewModel.setDataSaver(it) }
                    )
                }
            }

            // Which build is actually installed. This app is sideloaded, so
            // there is no store to tell you — and a fix that "isn't there" is
            // far more often a stale APK than a broken fix. The *presence* of
            // this line is itself the answer: builds before 2026-09-18 have no
            // footer at all, so if you cannot see it, you are running an old
            // build.
            item { Spacer(modifier = Modifier.height(28.dp)) }

            item {
                Text(
                    text = "Elara ${BuildConfig.VERSION_NAME} " +
                        "(build ${BuildConfig.VERSION_CODE})",
                    color = AuraTextTertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                // The maker's mark: one step below the version line in an even
                // smaller size, so it reads as a quiet signature rather than a
                // feature of the screen.
                Text(
                    text = "made with ❤️ by piroman",
                    color = AuraTextTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = AuraTextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AuraContainer)
            .padding(vertical = 8.dp),
        content = content
    )
}

@Composable
private fun IconPill(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AuraPurple.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AuraPurple,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPill(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AuraTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = AuraTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AuraPurple,
                checkedTrackColor = AuraPurple.copy(alpha = 0.5f),
                uncheckedThumbColor = AuraTextSecondary,
                uncheckedTrackColor = AuraCardStrong
            )
        )
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconPill(icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AuraTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = AuraTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Text(
                text = valueLabel,
                color = AuraViolet,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Snap to even values (0, 2, 4, 6, 8, 10, 12)
        val snappedValue = (value / 2).toInt() * 2f

        Slider(
            value = snappedValue,
            onValueChange = { onValueChange((it / 2).toInt() * 2f) },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = AuraPurple,
                activeTrackColor = AuraPurple,
                inactiveTrackColor = AuraCardStrong
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Tick labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labels = listOf("Off", "2s", "4s", "6s", "8s", "10s", "12s")
            labels.forEach { label ->
                Text(
                    text = label,
                    color = if (
                        (label == "Off" && snappedValue == 0f) ||
                        (label != "Off" && label.removeSuffix("s").toIntOrNull() == snappedValue.toInt())
                    ) AuraViolet else AuraTextTertiary,
                    fontSize = 10.sp,
                    fontWeight = if (
                        (label == "Off" && snappedValue == 0f) ||
                        (label != "Off" && label.removeSuffix("s").toIntOrNull() == snappedValue.toInt())
                    ) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ChevronRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPill(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AuraTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = AuraTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AuraTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DividerLine() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}
