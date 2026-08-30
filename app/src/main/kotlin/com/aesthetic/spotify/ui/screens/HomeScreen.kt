package com.aesthetic.spotify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.theme.AuraBg
import com.aesthetic.spotify.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenBrowse: () -> Unit = {}
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val featured by viewModel.featuredPlaylists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    // Greeting follows the device clock, re-evaluated per composition.
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Good Night"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF16101F), AuraBg),
                    startY = 0f,
                    endY = 900f
                )
            )
            .padding(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Featured playlists from JioSaavn (browse content) ───────────
        if (featured.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended playlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(featured, key = { it.id }) { fp ->
                        Card(
                            onClick = {
                                viewModel.loadBrowsePlaylist(fp)
                                onOpenBrowse()
                            },
                            modifier = Modifier.size(150.dp, 190.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.06f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                AsyncImage(
                                    model = fp.image,
                                    contentDescription = fp.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    fp.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    fp.subtitle,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── Recently played (falls back to quick picks until history exists) ──
        val quickPicks = recentlyPlayed.ifEmpty { trendingTracks }.take(6)
        if (quickPicks.isNotEmpty()) {
            item {
                Text(
                    text = if (recentlyPlayed.isEmpty()) "Quick picks" else "Recently played",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(quickPicks) { track ->
                        QuickPickItem(track = track, onClick = { viewModel.playTrack(track, quickPicks) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── Trending Songs (per Music Source setting) ───────────────────
        if (trendingTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Trending Songs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(trendingTracks) { track ->
                        TrendingCard(track = track, onClick = { viewModel.playTrack(track, trendingTracks) })
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPickItem(track: Track, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp),
                maxLines = 2
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .background(Color.Transparent)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(150.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            AsyncImage(
                model = track.albumArt,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            fontSize = 13.sp
        )
        Text(
            text = track.artist,
            color = Color.Gray,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}
