package com.piroman.elara.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.data.download.DownloadManager
import com.piroman.elara.data.repository.MusicRepository
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.LazyKeys
import com.piroman.elara.ui.components.AddToPlaylistDialog
import com.piroman.elara.ui.components.SectionHeading
import com.piroman.elara.ui.components.TrackArtwork
import com.piroman.elara.ui.components.TrackContextMenu
import com.piroman.elara.ui.theme.AuraBg
import com.piroman.elara.ui.theme.AuraCardStrong
import com.piroman.elara.ui.theme.AuraHomeGlow
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.AuraViolet
import com.piroman.elara.data.model.Track

/**
 * Height of the purple glow at the top of Home, in dp.
 *
 * This was a hardcoded `endY = 900f`, which is **raw pixels**, not dp — so the
 * glow faded over roughly 300 dp on a 3x phone but over the entire screen on a
 * low-density device, and then sat flat at [AuraBg] for the rest. Expressed in
 * dp and converted with the real density so it looks the same everywhere.
 */
private val HomeGlowHeight = 320.dp

/**
 * The card metrics every shelf on Home shares.
 *
 * These were four separate literals (150 dp card, 150 dp artwork, 16 dp gap,
 * 10 dp radius) sprinkled across the featured row and [HomeTrackCard]. Naming
 * them is what lets the featured row and the song shelves be the *same* card
 * shape — which is the point of the redesign: the reference screen makes no
 * visual distinction between "an editorial playlist" and "a song", it is one
 * tile repeated down the page.
 *
 * [HomeCardWidth] is 136 dp rather than 150 dp so a little over two cards are
 * visible at 360 dp of screen width. That third sliver is doing real work — it
 * is what tells the eye the row scrolls sideways. At 150 dp only two fit
 * exactly and the row reads as a static grid.
 */
private val HomeCardWidth = 136.dp
private val HomeCardGap = 14.dp
private val HomeCardCorner = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenBrowse: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {}
) {
    val trendingTracks by viewModel.trendingTracks.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val featured by viewModel.featuredPlaylists.collectAsState()
    val charts by viewModel.charts.collectAsState()
    val throwback by viewModel.throwback.collectAsState()
    val newReleases by viewModel.newReleases.collectAsState()
    val newSongs by viewModel.newSongs.collectAsState()
    val trendingSearches by viewModel.trendingSearches.collectAsState()
    val youTubeTop by viewModel.youTubeTop.collectAsState()
    val youTubeHipHop by viewModel.youTubeHipHop.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()

    // Home renders songs too, so the long-press menu's "Add to playlist" needs
    // the same screen-level dialog state the other track lists already keep.
    var addPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    // Collected once for the whole screen, not once per card — same rule as
    // SearchScreen. Reading these inside HomeTrackCard would give
    // every visible card its own subscription to the download flow and
    // re-compose it on every progress tick.
    //
    // These are also what let the menu show "Remove download" instead of
    // "Download" for a track that is already on disk. Without them the menu
    // silently offered to download a downloaded song, which then called
    // downloadTrack instead of deleteDownload.
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedEntries by viewModel.downloadedTracks.collectAsState()
    val downloadedIds = remember(downloadedEntries) {
        downloadedEntries.mapTo(HashSet()) { it.track.id }
    }

    // `remember` without a key caches this for the life of the screen, so it is
    // evaluated once per visit rather than on every recomposition.
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Good Night"
        }
    }

    val density = LocalDensity.current
    val homeGradient = remember(density) {
        Brush.verticalGradient(
            colors = listOf(AuraHomeGlow, AuraBg),
            startY = 0f,
            endY = with(density) { HomeGlowHeight.toPx() }
        )
    }

    // Every song the history holds, not the first six. `PlayHistoryManager`
    // already caps itself at MAX_ENTRIES (20), so this ceiling only ever bites
    // the `trendingTracks` fallback below — that is a full API page and we do
    // not want an unbounded shelf.
    val quickPicks = remember(recentlyPlayed, trendingTracks) {
        recentlyPlayed.ifEmpty { trendingTracks }.take(20)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(homeGradient)
            .padding(16.dp)
    ) {
        item(key = "greeting") {
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Featured playlists from JioSaavn (browse content) ───────────
        if (featured.isNotEmpty()) {
            item(key = "featured") {
                SectionHeading("Recommended playlists")
                TileShelf(featured, viewModel, "featured", onOpenBrowse)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── Recently played (falls back to quick picks until history exists) ──
        if (quickPicks.isNotEmpty()) {
            item(key = "quick-picks") {
                SectionHeading(if (recentlyPlayed.isEmpty()) "Quick picks" else "Recently played")

                // A horizontal shelf, matching the rows below.
                //
                // This used to be a 2-column grid of wide 64 dp tiles, which
                // could only ever show six songs — three rows and the page was
                // out of screen — so the remaining history was unreachable, and
                // every extra row pushed Trending further down. A row scrolls
                // sideways instead, so all 20 entries are reachable at a fixed
                // height.
                TrackShelf(
                    tracks = quickPicks,
                    viewModel = viewModel,
                    contentType = "quick-pick",
                    onAddToPlaylist = { addPlaylistTrack = it },
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── Most played ─────────────────────────────────────────────────
        // Only appears once something has been played at least twice, so a fresh
        // install does not show a second shelf that is a duplicate of the first.
        if (mostPlayed.isNotEmpty()) {
            item(key = "most-played-heading") {
                SectionHeading("Most played")
            }

            item(key = "most-played-row") {
                TrackShelf(
                    tracks = mostPlayed,
                    viewModel = viewModel,
                    contentType = "most-played",
                    onAddToPlaylist = { addPlaylistTrack = it },
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds
                )
            }
        }

        // ── Trending Songs (per Music Source setting) ───────────────────
        if (trendingTracks.isNotEmpty()) {
            item(key = "trending-heading") {
                Spacer(modifier = Modifier.height(32.dp))
                SectionHeading("Trending Songs")
            }

            item(key = "trending-row") {
                TrackShelf(
                    tracks = trendingTracks,
                    viewModel = viewModel,
                    contentType = "trending",
                    onAddToPlaylist = { addPlaylistTrack = it },
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds
                )
            }
        }

        // ── The four discovery rows ────────────────────────────────────
        //
        // Appended below everything that was already here rather than slotted in
        // above it. That is deliberate: it means not one existing row moves, so a
        // user who scrolls to their Recently played finds it exactly where it was
        // and the only difference is new content further down.
        //
        // Every row except categories is `if (isNotEmpty())`, so a failed or empty
        // fetch removes the row instead of leaving a heading over nothing — which
        // is also why each row carries its own section key. Categories needs no
        // guard: [HomeCategories] is a constant, so the row always has tiles.

        // Browse categories. No feed backs these on this host (see [HomeCategories]),
        // so a tile is a search for its own name.
        item(key = "categories") {
            Spacer(modifier = Modifier.height(32.dp))
            SectionHeading("Browse categories")
            ChipShelf(HomeCategories, "category", onOpenCategory)
            Spacer(modifier = Modifier.height(28.dp))
        }

        if (charts.isNotEmpty()) {
            item(key = "charts") {
                SectionHeading("Top charts")
                TileShelf(charts, viewModel, "chart", onOpenBrowse)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        if (newReleases.isNotEmpty()) {
            item(key = "new-releases") {
                SectionHeading("New releases")
                TileShelf(newReleases, viewModel, "new-release", onOpenBrowse)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        if (throwback.isNotEmpty()) {
            item(key = "throwback") {
                SectionHeading("Throwback")
                TileShelf(throwback, viewModel, "throwback", onOpenBrowse)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // The other half of the "New releases" feed, which the album filter used
        // to discard. Real songs with real streams — see NewReleaseRows.
        if (newSongs.isNotEmpty()) {
            item(key = "new-songs") {
                SectionHeading("New songs")
                TrackShelf(
                    tracks = newSongs,
                    viewModel = viewModel,
                    contentType = "new-song",
                    onAddToPlaylist = { addPlaylistTrack = it },
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // Search-backed for the same reason the category row is: the feed mixes
        // albums, songs and artists, so there is no single thing to open. Reuses
        // `onOpenCategory`, which sets the query and switches to the search tab.
        if (trendingSearches.isNotEmpty()) {
            item(key = "trending-searches") {
                SectionHeading("Trending searches")
                ChipShelf(trendingSearches, "search-term", onOpenCategory)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // YouTube Music's two rows. The heading names the source because the
        // rows are search-backed rather than editorial (see YouTubeMusicRows):
        // "top songs" is what YouTube Music returns for that query, not a chart
        // it published, and the second row is a genre from [HomeCategories]
        // rather than "new songs" — that query returned 2018–2022 releases.
        // Both are hidden entirely when the Music Source setting is JioSaavn-only
        // — the ViewModel empties them, and the `isNotEmpty` guard drops the rows.
        if (youTubeTop.isNotEmpty()) {
            item(key = "yt-top") {
                SectionHeading("YouTube Music top songs")
                TrackShelf(
                    tracks = youTubeTop,
                    viewModel = viewModel,
                    contentType = "yt-top",
                    onAddToPlaylist = { addPlaylistTrack = it },
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        if (youTubeHipHop.isNotEmpty()) {
            item(key = "yt-hip-hop") {
                SectionHeading("YouTube Music hip-hop")
                TrackShelf(
                    tracks = youTubeHipHop,
                    viewModel = viewModel,
                    contentType = "yt-hip-hop",
                    onAddToPlaylist = { addPlaylistTrack = it },
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(100.dp)) }
    }

    addPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            viewModel = viewModel,
            track = track,
            onDismiss = { addPlaylistTrack = null }
        )
    }
}

/**
 * One horizontal shelf of [HomeTrackCard]s. Home has three of these now
 * (Recently played, Most played, Trending), so the row lives in one place.
 *
 * `downloadStates` and `downloadedIds` are passed down rather than read from the
 * ViewModel inside each card — see the note in [HomeScreen]. Reading them per
 * card gave every visible card its own flow subscription.
 *
 * The key is identity **and** position, never the bare index and not the bare
 * id. Two of the three shelves are fed by unfiltered API/derived lists that can
 * legitimately contain the same id twice, and duplicate keys crash a lazy list.
 */
@Composable
private fun TrackShelf(
    tracks: List<Track>,
    viewModel: MainViewModel,
    contentType: String,
    onAddToPlaylist: (Track) -> Unit,
    downloadStates: Map<String, DownloadManager.State>,
    downloadedIds: Set<String>
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(HomeCardGap)) {
        itemsIndexed(
            items = tracks,
            key = { index, track -> "${track.id}#$index" },
            contentType = { _, _ -> contentType }
        ) { _, track ->
            HomeTrackCard(
                track = track,
                viewModel = viewModel,
                onClick = { viewModel.playTrack(track, tracks) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                downloadState = downloadStates[track.id],
                isDownloaded = track.id in downloadedIds
            )
        }
    }
}

/**
 * Home's browse categories.
 *
 * **There is no category feed behind these.** Probed live 2026-09-21:
 * `content.getBrowseCategories`, `content.getGenres`, `content.getMoods`,
 * `content.getLanguages`, `content.getRadio` and `content.getCities` all answer
 * `{"error":{"code":"INPUT_INVALID","msg":"... is not supported"}}`, so there is
 * nothing to fetch. A tile therefore runs a *search* for its own name — real
 * results and real playback, but the list of names is ours rather than the
 * service's editorial team. That is written here, at the data, because the next
 * person to touch this will otherwise assume a feed was simply missed.
 *
 * Deliberately broad queries. A narrow one ("Arijit Singh") would read as an
 * artist row and belong somewhere else.
 */
private val HomeCategories = listOf(
    "Romance",
    "Party",
    "Workout",
    "Chill",
    "Devotional",
    "Hip-Hop",
    "Retro",
    "Sad Songs"
)

/**
 * One horizontal row of [MusicRepository.BrowseTile]s. Featured playlists,
 * charts and albums all render through this, so the four rows cannot drift.
 *
 * Separate from [TrackShelf] because a tile is not a `Track`: there is no
 * download state, no context menu, and tapping *opens* a collection rather than
 * playing a song.
 *
 * Keys are id **and** position, matching [TrackShelf]. These lists come straight
 * off the network and nothing guarantees an id is unique within a page — and a
 * duplicate key crashes a lazy list, which is a crash caused by a cosmetic
 * problem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileShelf(
    tiles: List<MusicRepository.BrowseTile>,
    viewModel: MainViewModel,
    contentType: String,
    onOpen: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(HomeCardGap)) {
        itemsIndexed(
            items = tiles,
            key = { index, tile -> "${tile.id}#$index" },
            contentType = { _, _ -> contentType }
        ) { _, tile ->
            Column(
                modifier = Modifier.width(HomeCardWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    onClick = {
                        viewModel.loadBrowsePlaylist(tile)
                        onOpen()
                    },
                    modifier = Modifier.size(HomeCardWidth),
                    shape = RoundedCornerShape(HomeCardCorner)
                ) {
                    TrackArtwork(
                        url = tile.image,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp,
                        contentDescription = tile.title
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TileLabels(title = tile.title, subtitle = tile.subtitle)
            }
        }
    }
}

/**
 * A row of square text chips, shared by both Home chip rows — "Browse categories"
 * (a constant list) and "Trending searches" (live titles from the service) — so
 * the two cannot drift apart in size, gap or label treatment.
 *
 * A neutral card fill with the name in the accent — deliberately not a
 * violet-tinted surface. Tinting surfaces violet is the one thing the theme
 * rejects outright, and eight saturated squares in a row would be exactly that.
 *
 * [contentType] is a parameter rather than a constant: the rows recycle different
 * things, and one shared value would let Lazy reuse a category chip for a search chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipShelf(
    names: List<String>,
    contentType: String,
    onOpen: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(HomeCardGap)) {
        items(
            // `withOccurrence` rather than the bare name: the trending feed is live
            // and can legitimately list the same title twice, and duplicate keys
            // crash a LazyRow.
            items = LazyKeys.withOccurrence(names) { it },
            key = { it.second },
            contentType = { contentType }
        ) { (name, _) ->
            Card(
                onClick = { onOpen(name) },
                modifier = Modifier.size(HomeCardWidth),
                shape = RoundedCornerShape(HomeCardCorner),
                colors = CardDefaults.cardColors(containerColor = AuraCardStrong)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        color = AuraViolet,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The two centred lines under a Home tile: the title, then a quieter second line
 * — the artist for a song, the playlist's own subtitle for a featured card.
 *
 * Shared by [HomeTrackCard] and the featured row so the two cannot disagree
 * about size, weight or alignment. The `fillMaxWidth` on each `Text` is what
 * makes `TextAlign.Center` mean anything: centring inside a box that has already
 * shrunk to the text's own width would be a no-op, and the labels would stay
 * left-aligned to the card edge.
 */
@Composable
private fun TileLabels(title: String, subtitle: String) {
    Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = subtitle,
        color = AuraTextSecondary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The [HomeCardWidth] artwork card used by every horizontal shelf on Home —
 * "Recently played", "Most played" and "Trending Songs".
 *
 * Deliberately one composable, not two: the shelves are the same object at
 * different data, and a second copy is how they drift apart. It used to be
 * called `TrendingCard`, which stopped being true once Recently played adopted
 * it. The wide 64 dp tile this replaced is in git history if the list-row shape
 * is ever wanted back.
 *
 * The labels are centred and sit *outside* the artwork box, matching the
 * reference layout: the artwork is the card, and the text is a caption under it.
 * `HomeCardWidth` / `HomeCardGap` / `HomeCardCorner` are shared with the
 * featured row so all four shelves are the same tile.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeTrackCard(
    track: Track,
    viewModel: MainViewModel,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    downloadState: DownloadManager.State? = null,
    isDownloaded: Boolean = false
) {
    val menuOpen = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(HomeCardWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Card(
                modifier = Modifier
                    .size(HomeCardWidth)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = { menuOpen.value = true }
                    ),
                shape = RoundedCornerShape(HomeCardCorner)
            ) {
                TrackArtwork(
                    url = track.albumArt,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    contentDescription = track.title
                )
            }

            TrackContextMenu(
                expanded = menuOpen.value,
                onDismiss = { menuOpen.value = false },
                track = track,
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist,
                downloadState = downloadState,
                isDownloaded = isDownloaded
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TileLabels(title = track.title, subtitle = track.artist)
    }
}
