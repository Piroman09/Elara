package com.piroman.elara.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.data.history.MonthAggregate
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.components.TrackArtwork
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraContainer
import com.piroman.elara.ui.theme.AuraPurple
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraTextTertiary
import com.piroman.elara.ui.theme.AuraViolet

/**
 * Elara Wrapped — this month's listening, as one recap screen.
 *
 * The data is the [MonthAggregate] that playback already records (plays +
 * listening time + per-track counts), joined against the app's track sources
 * by [MainViewModel.monthTopTracks]. Pure presentation, like the stats screen
 * it sits beside in Library.
 *
 * The share action copies a plain-text recap — no bitmap export, no
 * FileProvider plumbing — because the point of Wrapped is the *numbers*:
 * minutes, plays, top songs. Text also survives every messaging app's
 * compression, which a rendered card does not.
 */
@Composable
fun WrappedScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val aggregate by viewModel.monthAggregate.collectAsState()
    val topTracks by viewModel.monthTopTracks.collectAsState()

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
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Wrapped",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val text = buildString {
                    appendLine("My ${viewModel.monthLabel(aggregate)} on Elara:")
                    appendLine("• ${viewModel.monthMinutes(aggregate)} minutes listened")
                    appendLine("• ${aggregate.plays} plays")
                    if (topTracks.isNotEmpty()) {
                        appendLine("Top songs:")
                        topTracks.take(5).forEachIndexed { i, (track, count) ->
                            appendLine("  ${i + 1}. ${track.title} — ${track.artist} ($count×)")
                        }
                    }
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text.toString())
                }
                context.startActivity(Intent.createChooser(intent, "Share Wrapped"))
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = viewModel.monthLabel(aggregate),
                    color = AuraTextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your month in music",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Headline numbers, same card language as the stats screen.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WrappedStatCard(
                        value = "${viewModel.monthMinutes(aggregate)}",
                        unit = "min",
                        label = "Listened",
                        modifier = Modifier.weight(1f)
                    )
                    WrappedStatCard(
                        value = "${aggregate.plays}",
                        unit = "plays",
                        label = "Songs played",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = "Top songs this month",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (topTracks.isEmpty()) {
                item {
                    Text(
                        text = "Nothing yet — play some songs and this fills up over the month.",
                        color = AuraTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            } else {
                itemsIndexed(topTracks, key = { _, item -> item.first.id }) { index, (track, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = AuraViolet,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )
                        TrackArtwork(
                            url = track.albumArt,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = AuraTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
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
                        Text(
                            text = "${count}×",
                            color = AuraTextTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Wrapped counts plays and listening time from the moment this month began. Reinstalling the app resets it.",
                    color = AuraTextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WrappedStatCard(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AuraContainer)
            .padding(16.dp)
    ) {
        Text(
            text = value,
            color = AuraViolet,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            color = AuraTextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = AuraTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
