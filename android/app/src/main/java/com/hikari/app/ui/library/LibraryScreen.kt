package com.hikari.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.isFilm
import com.hikari.app.domain.genre.Genre
import com.hikari.app.domain.genre.detectGenres
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.ui.library.components.CinemaPosterCard
import com.hikari.app.ui.library.components.CoverEditSheet
import com.hikari.app.ui.library.components.LibraryTab
import com.hikari.app.ui.library.components.LibraryTabNav
import com.hikari.app.ui.library.components.MoodDiscoverySection
import com.hikari.app.ui.library.components.NetflixTopTenSection
import com.hikari.app.ui.library.components.QuickLookItem
import com.hikari.app.ui.library.components.QuickLookSheet
import com.hikari.app.ui.library.components.SmartShuffleSheet
import com.hikari.app.ui.library.components.TopTenCharts
import com.hikari.app.ui.library.components.TopTenItem
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.components.resumeAwareThumbnail
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.util.Calendar

@Composable
fun LibraryScreen(
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val topCharts by viewModel.topCharts.collectAsState()
    val watchLater by viewModel.watchLater.collectAsState()
    val savedItems by viewModel.savedItems.collectAsState()
    val history by viewModel.history.collectAsState()
    val coverEdit by viewModel.coverEditState.collectAsState()
    var editingSeries by remember { mutableStateOf<SeriesDto?>(null) }

    // Reload on resume so Continue-Watching reflects the user's latest
    // playback position right after they return from the player.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.loadLibrary()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is LibraryUiState.Loading -> CircularProgressIndicator(
                color = HikariAmber,
                modifier = Modifier.align(Alignment.Center),
            )
            // Kein Netz oder Backend nicht erreichbar → lokale Downloads statt
            // technischem Fehlertext.
            is LibraryUiState.Offline -> OfflineLibrary(
                state = s,
                onPlayVideo = onPlayVideo,
                onPlaySong = viewModel::playSong,
            )
            is LibraryUiState.Success -> LibraryContent(
                data = s.data,
                topCharts = topCharts,
                onOpenSeries = onOpenSeries,
                onOpenChannel = onOpenChannel,
                onPlayVideo = onPlayVideo,
                onLongPressSeries = { editingSeries = it },
                watchLater = watchLater,
                saved = savedItems,
                history = history,
                onRemoveWatchLater = { viewModel.removeWatchLater(it) },
                onToggleWatchLater = { id, saved -> viewModel.toggleWatchLater(id, saved) },
            )
        }
    }

    editingSeries?.let { series ->
        CoverEditSheet(
            seriesTitle = series.title,
            state = coverEdit,
            onDismiss = {
                editingSeries = null
                viewModel.resetCoverEdit()
            },
            onSaveUrl = { url ->
                viewModel.setSeriesCoverUrl(series.id, url) { editingSeries = null }
            },
            onPickGallery = { bytes, mime ->
                viewModel.uploadSeriesCover(series.id, bytes, mime) { editingSeries = null }
            },
        )
    }
}

@Composable
private fun LibraryContent(
    data: LibraryResponse,
    topCharts: TopTenCharts,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    watchLater: List<FeedItem> = emptyList(),
    saved: List<FeedItem> = emptyList(),
    history: List<FeedItem> = emptyList(),
    onRemoveWatchLater: (String) -> Unit = {},
    onToggleWatchLater: (String, Boolean) -> Unit = { _, _ -> },
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(LibraryTab.FOR_YOU) }
    var selectedGenre by remember { mutableStateOf(Genre.ALL) }
    var showShuffleSheet by remember { mutableStateOf(false) }
    var quickLookItem by remember { mutableStateOf<QuickLookItem?>(null) }

    fun isMovie(v: LibraryVideoDto): Boolean = v.isFilm()

    fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        return if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
    }

    val allMovies = remember(data.recentlyAdded) {
        data.recentlyAdded.filter { isMovie(it) }
    }
    val weekendMovies = remember(allMovies) {
        allMovies.filter { it.duration_seconds >= 4500 }
    }

    val genreCounts = remember(data) {
        val map = mutableMapOf<Genre, Int>()
        data.series.forEach { s ->
            s.detectGenres().forEach { g -> map[g] = (map[g] ?: 0) + 1 }
        }
        data.recentlyAdded.forEach { v ->
            v.detectGenres().forEach { g -> map[g] = (map[g] ?: 0) + 1 }
        }
        map
    }

    val continueWatching = remember(data.recentlyAdded) {
        data.recentlyAdded.filter {
            val p = it.progress_seconds ?: 0f
            p > 0f && p < it.duration_seconds.toFloat() * 0.95f
        }
    }
    val heroVideo = continueWatching.firstOrNull() ?: data.recentlyAdded.firstOrNull()

    fun openQuickLookForSeries(s: SeriesDto) {
        val isWl = watchLater.any { it.videoId == s.id }
        quickLookItem = QuickLookItem(
            id = s.id,
            title = s.title,
            subtitle = "Serie",
            description = s.description,
            thumbnailUrl = s.thumbnail_url,
            isSeries = true,
            seriesId = s.id,
            isWatchLater = isWl,
        )
    }

    fun openQuickLookForVideo(v: LibraryVideoDto) {
        val isWl = watchLater.any { it.videoId == v.id }
        quickLookItem = QuickLookItem(
            id = v.id,
            title = v.title,
            subtitle = "${formatDuration(v.duration_seconds)} · ${v.channelTitle ?: "Video"}",
            description = v.description,
            thumbnailUrl = v.thumbnail_url,
            isSeries = v.series_id != null,
            seriesId = v.series_id,
            channelTitle = v.channelTitle ?: "",
            durationSeconds = v.duration_seconds,
            matchScore = v.overall_score,
            isWatchLater = isWl,
        )
    }

    fun openQuickLookForTopTen(item: TopTenItem) {
        val isWl = watchLater.any { it.videoId == item.id }
        quickLookItem = QuickLookItem(
            id = item.id,
            title = item.title,
            subtitle = item.subtitle,
            description = null,
            thumbnailUrl = item.thumbnailUrl,
            isSeries = item.isSeries,
            seriesId = item.seriesId,
            channelTitle = item.channelTitle,
            durationSeconds = item.durationSeconds,
            matchScore = item.matchScore,
            isWatchLater = isWl,
        )
    }

    if (isSearchActive) {
        NetflixSearchView(
            data = data,
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
            onCloseSearch = {
                searchQuery = ""
                isSearchActive = false
            },
            onOpenSeries = onOpenSeries,
            onOpenChannel = onOpenChannel,
            onPlayVideo = onPlayVideo,
            onLongPressSeries = onLongPressSeries,
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar: HIKARI Brand + Shuffle + Search ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HIKARI",
                    color = HikariAmber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showShuffleSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Shuffle,
                            contentDescription = "Zufallsauswahl",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Suchen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // ── Apple-Glass Segment Switcher ──────────────────────────────────
            LibraryTabNav(
                selectedTab = selectedTab,
                onSelectTab = {
                    selectedTab = it
                    selectedGenre = Genre.ALL
                },
            )

            Spacer(Modifier.height(4.dp))

            if (selectedGenre != Genre.ALL) {
                FilteredNetflixLibraryContent(
                    data = data,
                    selectedType = when (selectedTab) {
                        LibraryTab.SERIES -> NetflixContentType.SERIES
                        LibraryTab.MOVIES -> NetflixContentType.VIDEOS
                        else -> NetflixContentType.ALL
                    },
                    selectedGenre = selectedGenre,
                    onOpenSeries = onOpenSeries,
                    onOpenChannel = onOpenChannel,
                    onPlayVideo = onPlayVideo,
                    onLongPressSeries = onLongPressSeries,
                    onResetFilter = {
                        selectedGenre = Genre.ALL
                    },
                )
            } else {
                when (selectedTab) {
                    LibraryTab.FOR_YOU -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item { HeroSection(video = heroVideo, onPlay = ::play) }

                            if (continueWatching.isNotEmpty()) {
                                item {
                                    SectionHeader("Weiterschauen", count = continueWatching.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(continueWatching, key = { it.id }) { v ->
                                            ContinueCard(video = v, onClick = { play(v) })
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // ── TOP 10 SERIEN (Letzte 30 Tage) ─────────────────────────
                            if (topCharts.topSeries.isNotEmpty()) {
                                item {
                                    NetflixTopTenSection(
                                        title = "TOP 10 Serien",
                                        items = topCharts.topSeries,
                                        onItemClick = { item ->
                                            if (item.isSeries) onOpenSeries(item.id)
                                            else onPlayVideo(item.id, item.title, item.channelTitle)
                                        },
                                        onItemLongClick = { openQuickLookForTopTen(it) },
                                    )
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // ── TOP 10 FILME (Letzte 30 Tage) ──────────────────────────
                            if (topCharts.topMovies.isNotEmpty()) {
                                item {
                                    NetflixTopTenSection(
                                        title = "TOP 10 Filme",
                                        items = topCharts.topMovies,
                                        onItemClick = { item ->
                                            onPlayVideo(item.id, item.title, item.channelTitle)
                                        },
                                        onItemLongClick = { openQuickLookForTopTen(it) },
                                    )
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // ── THEMEN & GENRES ENTDECKEN ──────────────────────────────
                            item {
                                MoodDiscoverySection(
                                    selectedGenre = selectedGenre,
                                    onSelectGenre = { selectedGenre = it },
                                    genreCounts = genreCounts,
                                )
                                Spacer(Modifier.height(20.dp))
                            }

                            // ── Deine Sammlung ──────────────────────────────
                            if (watchLater.isNotEmpty()) {
                                item {
                                    SectionHeader("Später ansehen", count = watchLater.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(watchLater, key = { it.videoId }) { fi ->
                                            CollectionCard(
                                                item = fi,
                                                onPlay = {
                                                    onRemoveWatchLater(fi.videoId)
                                                    onPlayVideo(fi.videoId, fi.title, fi.channelTitle)
                                                },
                                                onRemove = { onRemoveWatchLater(fi.videoId) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // Wochenend-Kino (> 75 Min)
                            if (weekendMovies.isNotEmpty()) {
                                item {
                                    SectionHeader("Wochenend-Kino (> 75 Min)", count = weekendMovies.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(weekendMovies, key = { "wk-${it.id}" }) { m ->
                                            CinemaPosterCard(
                                                title = m.title,
                                                subtitle = formatDuration(m.duration_seconds),
                                                thumbnailUrl = m.thumbnail_url,
                                                tag = "KINO",
                                                matchScore = m.overall_score,
                                                onClick = { play(m) },
                                                onLongClick = { openQuickLookForVideo(m) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            if (saved.isNotEmpty()) {
                                item {
                                    SectionHeader("Gespeichert", count = saved.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(saved, key = { it.videoId }) { fi ->
                                            CollectionCard(
                                                item = fi,
                                                onPlay = { onPlayVideo(fi.videoId, fi.title, fi.channelTitle) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            if (data.suggestions.isNotEmpty()) {
                                item {
                                    SectionHeader("Vorschläge für dich", count = data.suggestions.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(data.suggestions, key = { it.videoId }) { dto ->
                                            val fi = dto.toFeedItem()
                                            CollectionCard(
                                                item = fi,
                                                onPlay = { onPlayVideo(fi.videoId, fi.title, fi.channelTitle) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            if (data.channels.isNotEmpty()) {
                                item {
                                    SectionHeader("Deine Kanäle", count = data.channels.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(data.channels, key = { it.id }) { c ->
                                            ChannelCircle(channel = c, onClick = { onOpenChannel(c.id) })
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            if (history.isNotEmpty()) {
                                item {
                                    SectionHeader("Verlauf", count = history.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(history, key = { it.videoId }) { fi ->
                                            CollectionCard(
                                                item = fi,
                                                onPlay = { onPlayVideo(fi.videoId, fi.title, fi.channelTitle) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            item {
                                SectionHeader("Neu hinzugefügt", count = data.recentlyAdded.size)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(data.recentlyAdded, key = { it.id }) { v ->
                                        RecentVideoCard(video = v, onClick = { play(v) })
                                    }
                                }
                            }

                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }

                    LibraryTab.SERIES -> {
                        val continueSeries = continueWatching.filter { it.series_id != null }
                        val heroSeriesVideo = continueSeries.firstOrNull() ?: data.recentlyAdded.firstOrNull { it.series_id != null }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (heroSeriesVideo != null) {
                                item { HeroSection(video = heroSeriesVideo, onPlay = ::play) }
                            }

                            // ── TOP 10 SERIEN (Meistgesehen) ──────────────────────────
                            if (topCharts.topSeries.isNotEmpty()) {
                                item {
                                    NetflixTopTenSection(
                                        title = "TOP 10 Serien (Meistgesehen)",
                                        items = topCharts.topSeries,
                                        onItemClick = { item -> onOpenSeries(item.id) },
                                        onItemLongClick = { openQuickLookForTopTen(it) },
                                    )
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            if (continueSeries.isNotEmpty()) {
                                item {
                                    SectionHeader("Weiter bingen", count = continueSeries.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(continueSeries, key = { "cs-${it.id}" }) { v ->
                                            ContinueCard(video = v, onClick = { play(v) })
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // Alle Serien in 2:3 Postern
                            item {
                                SectionHeader("Alle Serien", count = data.series.size)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(data.series, key = { "all-s-${it.id}" }) { s ->
                                        CinemaPosterCard(
                                            title = s.title,
                                            subtitle = s.description ?: "Serie",
                                            thumbnailUrl = s.thumbnail_url,
                                            tag = "SERIE",
                                            onClick = { onOpenSeries(s.id) },
                                            onLongClick = { openQuickLookForSeries(s) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }

                            // Serien nach Genre (Anime, SciFi, Action etc.)
                            val animeSeries = data.series.filter { Genre.ANIME in it.detectGenres() }
                            if (animeSeries.isNotEmpty()) {
                                item {
                                    SectionHeader("Anime-Serien", count = animeSeries.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(animeSeries, key = { "anime-${it.id}" }) { s ->
                                            CinemaPosterCard(
                                                title = s.title,
                                                subtitle = s.description ?: "Anime",
                                                thumbnailUrl = s.thumbnail_url,
                                                tag = "ANIME",
                                                onClick = { onOpenSeries(s.id) },
                                                onLongClick = { openQuickLookForSeries(s) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }

                    LibraryTab.MOVIES -> {
                        val heroMovie = allMovies.firstOrNull() ?: heroVideo

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (heroMovie != null) {
                                item { HeroSection(video = heroMovie, onPlay = ::play) }
                            }

                            // ── TOP 10 FILME (Meistgesehen) ───────────────────────────
                            if (topCharts.topMovies.isNotEmpty()) {
                                item {
                                    NetflixTopTenSection(
                                        title = "TOP 10 Filme (Meistgesehen)",
                                        items = topCharts.topMovies,
                                        onItemClick = { item -> onPlayVideo(item.id, item.title, item.channelTitle) },
                                        onItemLongClick = { openQuickLookForTopTen(it) },
                                    )
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // Spielfilme & Blockbuster (> 75 Min)
                            if (weekendMovies.isNotEmpty()) {
                                item {
                                    SectionHeader("Spielfilme & Blockbuster (> 75 Min)", count = weekendMovies.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(weekendMovies, key = { "m-block-${it.id}" }) { m ->
                                            CinemaPosterCard(
                                                title = m.title,
                                                subtitle = "${formatDuration(m.duration_seconds)} · ${m.channelTitle ?: ""}",
                                                thumbnailUrl = m.thumbnail_url,
                                                tag = "KINO",
                                                matchScore = m.overall_score,
                                                onClick = { play(m) },
                                                onLongClick = { openQuickLookForVideo(m) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            // Alle Filme & Dokus
                            item {
                                SectionHeader("Alle Filme & Dokus", count = allMovies.size)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(allMovies, key = { "all-m-${it.id}" }) { m ->
                                        CinemaPosterCard(
                                            title = m.title,
                                            subtitle = "${formatDuration(m.duration_seconds)} · ${m.channelTitle ?: ""}",
                                            thumbnailUrl = m.thumbnail_url,
                                            tag = "FILM",
                                            matchScore = m.overall_score,
                                            onClick = { play(m) },
                                            onLongClick = { openQuickLookForVideo(m) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }

                            // Dokus
                            val dokuMovies = allMovies.filter { Genre.DOCS in it.detectGenres() }
                            if (dokuMovies.isNotEmpty()) {
                                item {
                                    SectionHeader("Doku & Wissen", count = dokuMovies.size)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(dokuMovies, key = { "doku-${it.id}" }) { m ->
                                            CinemaPosterCard(
                                                title = m.title,
                                                subtitle = formatDuration(m.duration_seconds),
                                                thumbnailUrl = m.thumbnail_url,
                                                tag = "DOKU",
                                                matchScore = m.overall_score,
                                                onClick = { play(m) },
                                                onLongClick = { openQuickLookForVideo(m) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }
                            }

                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }

                    LibraryTab.DISCOVER -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                MoodDiscoverySection(
                                    selectedGenre = selectedGenre,
                                    onSelectGenre = { selectedGenre = it },
                                    genreCounts = genreCounts,
                                )
                                Spacer(Modifier.height(20.dp))
                            }

                            // Curated shelves for each genre
                            val genres = Genre.entries.filter { it != Genre.ALL }
                            genres.forEach { genre ->
                                val genreSeries = data.series.filter { genre in it.detectGenres() }
                                val genreVideos = data.recentlyAdded.filter { genre in it.detectGenres() }
                                val total = genreSeries.size + genreVideos.size
                                if (total > 0) {
                                    item {
                                        SectionHeader(genre.title, count = total)
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            items(genreSeries, key = { "shelf-s-${it.id}" }) { s ->
                                                CinemaPosterCard(
                                                    title = s.title,
                                                    subtitle = "Serie",
                                                    thumbnailUrl = s.thumbnail_url,
                                                    tag = "SERIE",
                                                    onClick = { onOpenSeries(s.id) },
                                                    onLongClick = { openQuickLookForSeries(s) },
                                                )
                                            }
                                            items(genreVideos, key = { "shelf-v-${it.id}" }) { v ->
                                                CinemaPosterCard(
                                                    title = v.title,
                                                    subtitle = formatDuration(v.duration_seconds),
                                                    thumbnailUrl = v.thumbnail_url,
                                                    tag = if (isMovie(v)) "FILM" else null,
                                                    matchScore = v.overall_score,
                                                    onClick = { play(v) },
                                                    onLongClick = { openQuickLookForVideo(v) },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(20.dp))
                                    }
                                }
                            }

                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showShuffleSheet) {
        SmartShuffleSheet(
            data = data,
            onOpenSeries = onOpenSeries,
            onPlayVideo = onPlayVideo,
            onDismiss = { showShuffleSheet = false },
        )
    }

    quickLookItem?.let { item ->
        QuickLookSheet(
            item = item,
            onPlay = {
                if (item.isSeries && item.seriesId != null) {
                    onOpenSeries(item.seriesId)
                } else {
                    onPlayVideo(item.id, item.title, item.channelTitle)
                }
            },
            onOpenSeries = if (item.isSeries && item.seriesId != null) onOpenSeries else null,
            onToggleWatchLater = {
                onToggleWatchLater(item.id, item.isWatchLater)
            },
            onDismiss = { quickLookItem = null },
        )
    }
}

/**
 * Netflix-Look für Karten mit geladenem Bild: dunkler Scrim am unteren Rand,
 * darauf der Titel weiß (1–2 Zeilen). Nur einblenden, wenn das Bild wirklich
 * geladen ist — ohne Bild zeigt das FallbackArtwork den Titel bereits groß,
 * ein Overlay würde ihn doppeln. [bottomPadding] lässt Raum für eine
 * Fortschrittsleiste am unteren Kartenrand (z. B. ContinueCard).
 */
@Composable
private fun BoxScope.ImageTitleOverlay(title: String, bottomPadding: Dp = 6.dp) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                ),
            ),
    )
    Text(
        text = title,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 14.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 8.dp, end = 8.dp, bottom = bottomPadding),
    )
}

@Composable
private fun RecommendedCard(video: LibraryVideoDto, onClick: () -> Unit) {
    // Erst wenn das Bild wirklich da ist, wandert der Titel als Overlay aufs
    // Bild — sonst zeigt das FallbackArtwork den Titel (nicht doppelt).
    var imageLoaded by remember(video.thumbnail_url) { mutableStateOf(false) }
    Column(modifier = Modifier.width(200.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            // Fallback-Artwork als Unterlage: fehlendes oder fehlerhaftes
            // Thumbnail wirkt gewollt statt leer (und dient als Lade-Hintergrund).
            FallbackArtwork(title = video.title)
            AsyncImage(
                model = video.resumeAwareThumbnail(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
            )
            if (imageLoaded) ImageTitleOverlay(video.title)
            // Match% badge top-left
            video.overall_score?.let { score ->
                Text(
                    "$score%",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(HikariAmber, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Text(
                "${video.duration_seconds / 60}:${"%02d".format(video.duration_seconds % 60)}",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Der Titel liegt bei geladenem Bild als Overlay auf dem Bild; ohne
        // Bild trägt ihn das FallbackArtwork — hier nur noch der Kanal.
        if (!video.channelTitle.isNullOrBlank()) {
            Text(
                text = video.channelTitle.uppercase(),
                color = Color(0xFF4ADE80),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(video: LibraryVideoDto?, onPlay: (LibraryVideoDto) -> Unit) {
    if (video == null) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(HikariSurface))
        return
    }

    val progress = video.progress_seconds?.let { it / video.duration_seconds.toFloat() } ?: 0f
    val isResume = (video.progress_seconds ?: 0f) > 0f
    val remainingMin = ((video.duration_seconds.toFloat() * (1f - progress)) / 60f).toInt()
    val year = remember(video.published_at) {
        if (video.published_at <= 0L) null
        else {
            val cal = Calendar.getInstance().apply { timeInMillis = video.published_at }
            cal.get(Calendar.YEAR)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(580.dp)) {
        // Der Hero blendet den Titel selbst groß ein — nur Artwork, kein Text.
        FallbackArtwork(title = video.title, showTitle = false)
        AsyncImage(
            model = video.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            HikariBg.copy(alpha = 0.6f),
                            HikariBg,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isResume) Icons.Default.PlayArrow else Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = HikariAmber,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (isResume) "WEITERSCHAUEN" else "NEU IN DER BIBLIOTHEK",
                    color = HikariAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 32.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                video.overall_score?.let { score ->
                    Text(
                        "$score% Match",
                        color = Color(0xFF4ADE80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                year?.let {
                    Text("$it", color = HikariTextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                video.season?.let { s ->
                    Text(
                        "S$s · F${video.episode ?: "-"}",
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    "${video.duration_seconds / 60} min",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (isResume) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(HikariAmber),
                    )
                }
                Spacer(Modifier.height(5.dp))
                val current = ((video.progress_seconds ?: 0f) / 60f).toInt()
                val total = video.duration_seconds / 60
                Text(
                    "$current / $total min · noch $remainingMin min",
                    color = HikariTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { onPlay(video) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isResume) "Weiterschauen" else "Abspielen",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            color = HikariTextFaint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "ALLE ›",
            color = HikariAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ContinueCard(video: LibraryVideoDto, onClick: () -> Unit) {
    // Erst wenn das Bild wirklich da ist, wandert der Titel als Overlay aufs
    // Bild — sonst zeigt das FallbackArtwork den Titel (nicht doppelt).
    var imageLoaded by remember(video.thumbnail_url) { mutableStateOf(false) }
    Column(modifier = Modifier.width(200.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            // Fallback-Artwork als Unterlage: fehlendes oder fehlerhaftes
            // Thumbnail wirkt gewollt statt leer (und dient als Lade-Hintergrund).
            FallbackArtwork(title = video.title)
            // Angefangenes Video: Frame an der Stopp-Position statt Poster.
            AsyncImage(
                model = video.resumeAwareThumbnail(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
            )
            // Titel-Overlay oberhalb der Fortschrittsleiste (daher mehr
            // Abstand zum unteren Rand).
            if (imageLoaded) ImageTitleOverlay(video.title, bottomPadding = 10.dp)
            Text(
                "${video.duration_seconds / 60}:${"%02d".format(video.duration_seconds % 60)}",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
            val progress = video.progress_seconds?.let { it / video.duration_seconds.toFloat() }
            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(HikariAmber),
                    )
                }
            }
        }
        // Titel sitzt bei geladenem Bild im Overlay, sonst im FallbackArtwork —
        // unter der Karte bleibt nur die Restlaufzeit bzw. der Kanal.
        val sub = video.progress_seconds?.let {
            val left = video.duration_seconds - it.toInt()
            "noch ${left / 60} min"
        } ?: video.channelTitle
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesPosterCard(
    series: SeriesDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Erst wenn das Bild wirklich da ist, kommen Scrim + Titel als Overlay
    // aufs Poster — ohne Bild trägt das FallbackArtwork den Titel bereits
    // groß, ein Overlay würde ihn doppeln.
    var imageLoaded by remember(series.thumbnail_url) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(122.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(HikariSurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        FallbackArtwork(title = series.title)
        AsyncImage(
            model = series.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = false },
        )
        if (imageLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 200f,
                        ),
                    ),
            )
            Text(
                text = series.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, end = 7.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun ChannelCircle(channel: ChannelDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(channelGradient(channel.title)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.thumbnail_url.isNullOrBlank()) {
                AsyncImage(
                    model = channel.thumbnail_url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    channel.title.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Text(
            text = channel.title,
            color = HikariText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Bibliotheks-Vorschläge kommen als Feed-DTO — hier auf das Kartenmodell mappen. */
private fun com.hikari.app.data.api.dto.FeedItemDto.toFeedItem() = FeedItem(
    videoId = videoId,
    title = title,
    durationSeconds = durationSeconds,
    aspectRatio = aspectRatio,
    thumbnailUrl = thumbnailUrl,
    channelTitle = channelTitle,
    category = category,
    reasoning = reasoning,
    saved = saved == 1,
    kind = kind,
    summary = summary,
    source = source,
    channelId = channelId,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCard(item: FeedItem, onPlay: () -> Unit, onRemove: (() -> Unit)? = null) {
    // Erst wenn das Bild wirklich da ist, wandert der Titel als Overlay aufs
    // Bild — sonst zeigt das FallbackArtwork den Titel (nicht doppelt).
    var imageLoaded by remember(item.thumbnailUrl) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(200.dp)
            .combinedClickable(onClick = onPlay, onLongClick = onRemove),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            FallbackArtwork(title = item.title)
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
            )
            if (imageLoaded) ImageTitleOverlay(item.title)
            Text(
                "${item.durationSeconds / 60}:${"%02d".format(item.durationSeconds % 60)}",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        // Titel sitzt bei geladenem Bild im Overlay, sonst im FallbackArtwork —
        // unter der Karte bleibt nur der Kanal.
        Text(
            text = item.channelTitle,
            color = HikariTextFaint,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RecentVideoCard(video: LibraryVideoDto, onClick: () -> Unit) {
    // Erst wenn das Bild wirklich da ist, wandert der Titel als Overlay aufs
    // Bild — sonst zeigt das FallbackArtwork den Titel (nicht doppelt).
    var imageLoaded by remember(video.thumbnail_url) { mutableStateOf(false) }
    Column(modifier = Modifier.width(200.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            // Fallback-Artwork als Unterlage: fehlendes oder fehlerhaftes
            // Thumbnail wirkt gewollt statt leer (und dient als Lade-Hintergrund).
            FallbackArtwork(title = video.title)
            // Angefangenes Video: Frame an der Stopp-Position statt Poster.
            AsyncImage(
                model = video.resumeAwareThumbnail(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
            )
            if (imageLoaded) ImageTitleOverlay(video.title)
            Text(
                "${video.duration_seconds / 60}:${"%02d".format(video.duration_seconds % 60)}",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Der Titel liegt bei geladenem Bild als Overlay auf dem Bild; ohne
        // Bild trägt ihn das FallbackArtwork — hier nur noch der Kanal.
        if (!video.channelTitle.isNullOrBlank()) {
            Text(
                text = video.channelTitle.uppercase(),
                color = HikariAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun channelGradient(title: String): Brush {
    val palette = listOf(
        Color(0xFFB45309) to Color(0xFFF59E0B),
        Color(0xFF1E40AF) to Color(0xFF60A5FA),
        Color(0xFF166534) to Color(0xFF34D399),
        Color(0xFF7E22CE) to Color(0xFFC084FC),
        Color(0xFFBE185D) to Color(0xFFF472B6),
        Color(0xFF374151) to Color(0xFF6B7280),
    )
    val (start, end) = palette[(title.hashCode() and 0x7fffffff) % palette.size]
    return Brush.linearGradient(listOf(start, end))
}

private enum class NetflixContentType {
    ALL,
    SERIES,
    VIDEOS,
}

@Composable
private fun NetflixPill(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isActive) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isActive) 1.dp else 0.8.dp,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetflixCategorySheet(
    selectedGenre: Genre,
    onSelectGenre: (Genre) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Kategorien",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(Genre.entries) { genre ->
                    val isSelected = genre == selectedGenre
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                onSelectGenre(genre)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (genre == Genre.ALL) "Alle Kategorien" else genre.title,
                            color = if (isSelected) Color.White else Color(0xFFB3B3B3),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilteredNetflixLibraryContent(
    data: LibraryResponse,
    selectedType: NetflixContentType,
    selectedGenre: Genre,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    onResetFilter: () -> Unit,
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    val matchingSeries = remember(data.series, selectedType, selectedGenre) {
        if (selectedType == NetflixContentType.VIDEOS) emptyList()
        else data.series.filter { s ->
            selectedGenre == Genre.ALL || selectedGenre in s.detectGenres()
        }
    }

    val matchingVideos = remember(data.recentlyAdded, selectedType, selectedGenre) {
        if (selectedType == NetflixContentType.SERIES) emptyList()
        else data.recentlyAdded.filter { v ->
            selectedGenre == Genre.ALL || selectedGenre in v.detectGenres()
        }
    }

    val filterTitle = when {
        selectedGenre != Genre.ALL -> selectedGenre.title
        selectedType == NetflixContentType.SERIES -> "Alle Serien"
        selectedType == NetflixContentType.VIDEOS -> "Alle Filme & Videos"
        else -> "Alle Inhalte"
    }

    val totalCount = matchingSeries.size + matchingVideos.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = filterTitle,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$totalCount Titel verfügbar",
                        color = Color(0xFF8C8C8C),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = "Zurücksetzen",
                    color = HikariAmber,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onResetFilter() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (totalCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Keine Titel in dieser Kategorie",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Wähle eine andere Kategorie oder setze den Filter zurück.",
                            color = HikariTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (matchingSeries.isNotEmpty()) {
            item {
                SectionHeader("Serien", count = matchingSeries.size)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(matchingSeries, key = { "filter-s-${it.id}" }) { s ->
                        SeriesPosterCard(
                            series = s,
                            onClick = { onOpenSeries(s.id) },
                            onLongClick = { onLongPressSeries(s) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (matchingVideos.isNotEmpty()) {
            item {
                SectionHeader("Filme & Videos", count = matchingVideos.size)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(matchingVideos, key = { "filter-v-${it.id}" }) { v ->
                        RecommendedCard(video = v, onClick = { play(v) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NetflixSearchView(
    data: LibraryResponse,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    val isBlank = searchQuery.isBlank()

    val matchingSeries = remember(data.series, searchQuery) {
        if (isBlank) emptyList()
        else data.series.filter { s ->
            s.title.contains(searchQuery, ignoreCase = true) ||
                (s.description?.contains(searchQuery, ignoreCase = true) == true) ||
                s.detectGenres().any { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val matchingVideos = remember(data.recentlyAdded, searchQuery) {
        if (isBlank) emptyList()
        else data.recentlyAdded.filter { v ->
            v.title.contains(searchQuery, ignoreCase = true) ||
                (v.channelTitle?.contains(searchQuery, ignoreCase = true) == true) ||
                (v.description?.contains(searchQuery, ignoreCase = true) == true) ||
                v.detectGenres().any { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val matchingChannels = remember(data.channels, searchQuery) {
        if (isBlank) emptyList()
        else data.channels.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Top trending / recommended for empty search state (Netflix style)
    val topSearches = remember(data) {
        (data.recentlyAdded.take(8).map { TopSearchItem.Video(it) } +
            data.series.take(4).map { TopSearchItem.Series(it) })
            .distinctBy { it.title }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search App Bar ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = Color.White,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF262626))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF8C8C8C),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Serien, Filme, Genres...",
                                    color = Color(0xFF8C8C8C),
                                    fontSize = 14.sp,
                                )
                            }
                            inner()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Löschen",
                            tint = Color(0xFF8C8C8C),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQueryChange("") },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        if (isBlank) {
            // ── Netflix "Top-Suchanfragen" ────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            ) {
                item {
                    Text(
                        text = "Top-Suchanfragen",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                items(topSearches, key = { it.uniqueKey }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (item) {
                                    is TopSearchItem.Video -> play(item.video)
                                    is TopSearchItem.Series -> onOpenSeries(item.series.id)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(HikariSurface),
                        ) {
                            FallbackArtwork(title = item.title)
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                color = Color(0xFF8C8C8C),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Abspielen",
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .padding(4.dp),
                        )
                    }
                }
            }
        } else {
            val totalMatches = matchingSeries.size + matchingVideos.size + matchingChannels.size
            if (totalMatches == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Keine Treffer für „$searchQuery“",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Versuche es mit einem anderen Begriff oder überprüfe die Schreibweise.",
                            color = HikariTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                ) {
                    if (matchingSeries.isNotEmpty()) {
                        item {
                            SectionHeader("Serien", count = matchingSeries.size)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(matchingSeries, key = { "search-s-${it.id}" }) { s ->
                                    SeriesPosterCard(
                                        series = s,
                                        onClick = { onOpenSeries(s.id) },
                                        onLongClick = { onLongPressSeries(s) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    if (matchingVideos.isNotEmpty()) {
                        item {
                            SectionHeader("Videos", count = matchingVideos.size)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(matchingVideos, key = { "search-v-${it.id}" }) { v ->
                                    RecommendedCard(video = v, onClick = { play(v) })
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    if (matchingChannels.isNotEmpty()) {
                        item {
                            SectionHeader("Kanäle", count = matchingChannels.size)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(matchingChannels, key = { "search-c-${it.id}" }) { c ->
                                    ChannelCircle(channel = c, onClick = { onOpenChannel(c.id) })
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private sealed interface TopSearchItem {
    val uniqueKey: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String?

    data class Video(val video: LibraryVideoDto) : TopSearchItem {
        override val uniqueKey: String = "v-${video.id}"
        override val title: String = video.title
        override val subtitle: String = video.channelTitle ?: "${video.duration_seconds / 60} min"
        override val thumbnailUrl: String? = video.thumbnail_url
    }

    data class Series(val series: SeriesDto) : TopSearchItem {
        override val uniqueKey: String = "s-${series.id}"
        override val title: String = series.title
        override val subtitle: String = "Serie"
        override val thumbnailUrl: String? = series.thumbnail_url
    }
}

