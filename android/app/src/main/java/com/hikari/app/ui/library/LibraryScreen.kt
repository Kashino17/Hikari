package com.hikari.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.hikari.app.data.api.dto.isArchive
import com.hikari.app.data.api.dto.isFilm
import com.hikari.app.domain.genre.Genre
import com.hikari.app.domain.genre.detectGenres
import com.hikari.app.domain.library.Shelves
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
                library = s.library,
                onOpenSeries = onOpenSeries,
                onPlayVideo = onPlayVideo,
                onPlaySong = viewModel::playSong,
                onPlayAll = viewModel::playAll,
                onRetry = viewModel::loadLibrary,
            )
            is LibraryUiState.Success -> LibraryContent(
                data = s.data,
                topCharts = topCharts,
                onOpenSeries = onOpenSeries,
                onOpenChannel = onOpenChannel,
                onPlayVideo = onPlayVideo,
                onLongPressSeries = { editingSeries = it },
                onDismissContinue = viewModel::dismissContinue,
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

/** Luft zwischen den Bereichen der Bibliothek. */
private val SECTION_GAP = 40.dp

@Composable
private fun LibraryContent(
    data: LibraryResponse,
    topCharts: TopTenCharts,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    onDismissContinue: (videoId: String) -> Unit,
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(LibraryTab.FOR_YOU) }
    var selectedGenre by remember { mutableStateOf(Genre.ALL) }
    var showShuffleSheet by remember { mutableStateOf(false) }
    var quickLookItem by remember { mutableStateOf<QuickLookItem?>(null) }
    // Vollständige Übersicht einer Reihe (hinter »Alle«): Titel und alle Plakate.
    var gridPage by remember { mutableStateOf<Pair<String, List<PosterItem>>?>(null) }
    var showGenrePicker by remember { mutableStateOf(false) }
    var continueMenu by remember { mutableStateOf<LibraryVideoDto?>(null) }
    var showChannels by rememberSaveable { mutableStateOf(false) }
    // Die Bibliothek zeigt Serien und Filme; alles von YouTube liegt im Kanal-Bereich.
    val seriesById = remember(data.series) { data.series.associateBy { it.id } }

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

    // Je Genre ein Bild aus dem Bestand — möglichst für jedes Genre ein anderes.
    val genreCovers = remember(data) {
        val used = mutableSetOf<String>()
        Genre.entries.filter { it != Genre.ALL }.mapNotNull { genre ->
            val candidates = data.series.filter { genre in it.detectGenres() }
                .mapNotNull { it.backdrop_url ?: it.thumbnail_url } +
                data.recentlyAdded.filter { genre in it.detectGenres() }.mapNotNull { it.thumbnail_url }
            val pick = candidates.firstOrNull { it !in used } ?: candidates.firstOrNull()
            pick?.let { used += it; genre to it }
        }.toMap()
    }

    val continueWatching = remember(data) {
        // Neuer Server: fertige Liste über alle Quellen, auch gestreamte
        // YouTube-Videos. Ältere Server: Rückfall auf die Downloads.
        if (data.continueWatching.isNotEmpty()) {
            return@remember Shelves.continueRow(data.continueWatching.filter { it.isArchive() })
        }
        Shelves.continueRow(
            data.recentlyAdded
                .filter {
                    val p = it.progress_seconds ?: 0f
                    p > 0f && p < it.duration_seconds.toFloat() * 0.95f
                }
                // Zuletzt geschaut zuerst. Ältere Server liefern keinen Zeitstempel
                // (0) — dann bleibt die bisherige Reihenfolge stabil.
                .sortedByDescending { it.progress_updated_at ?: 0L },
        )
    }
    val heroVideo = continueWatching.firstOrNull() ?: data.recentlyAdded.firstOrNull()

    fun openQuickLookForSeries(s: SeriesDto) {
        quickLookItem = QuickLookItem(
            id = s.id,
            title = s.title,
            subtitle = "Serie",
            description = s.description,
            thumbnailUrl = s.thumbnail_url,
            isSeries = true,
            seriesId = s.id,
        )
    }

    fun openQuickLookForVideo(v: LibraryVideoDto) {
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
        )
    }

    fun openQuickLookForTopTen(item: TopTenItem) {
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
        )
    }

    fun seriesPoster(s: SeriesDto, prefix: String, tag: String = "SERIE", subtitle: String = s.description ?: "Serie") =
        PosterItem(
            key = "$prefix-s-${s.id}",
            title = s.title,
            subtitle = subtitle,
            thumbnailUrl = s.thumbnail_url,
            posterUrl = s.poster_url,
            tag = tag,
            onClick = { onOpenSeries(s.id) },
            onLongClick = { openQuickLookForSeries(s) },
        )

    fun videoPoster(v: LibraryVideoDto, prefix: String, tag: String?, subtitle: String = formatDuration(v.duration_seconds)) =
        PosterItem(
            key = "$prefix-v-${v.id}",
            title = v.title,
            subtitle = subtitle,
            thumbnailUrl = v.thumbnail_url,
            posterUrl = v.poster_url,
            tag = tag,
            matchScore = v.overall_score,
            onClick = { play(v) },
            onLongClick = { openQuickLookForVideo(v) },
        )

    val showAll: (String) -> (List<PosterItem>) -> Unit = { title -> { items -> gridPage = title to items } }

    // Was es in einer Kategorie gibt, hängt vom Reiter ab: unter »Serien« nur Serien, unter »Filme« nur Filme.
    fun genreItems(genre: Genre, prefix: String): List<PosterItem> {
        val series = if (selectedTab == LibraryTab.MOVIES) emptyList()
        else data.series.filter { genre in it.detectGenres() }.map { seriesPoster(it, prefix) }
        // Einzelne Folgen stehen nicht neben ihrer Serie — nur Filme und Einzelvideos.
        val films = if (selectedTab == LibraryTab.SERIES) emptyList()
        else data.recentlyAdded.filter { it.series_id == null && genre in it.detectGenres() }
            .map { videoPoster(it, prefix, if (isMovie(it)) "FILM" else null) }
        return series + films
    }
    val availableGenres = Genre.entries.filter { it != Genre.ALL && genreItems(it, "probe").isNotEmpty() }

    @Composable
    fun ContinueShelf(title: String, videos: List<LibraryVideoDto>, keyPrefix: String) {
        // Wie bei Netflix: nur die Reihe, kein »Alle« — sie zeigt ohnehin nur das Jüngste.
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(videos, key = { "$keyPrefix-${it.id}" }) { v ->
                ContinueCard(v, v.series_id?.let(seriesById::get), onClick = { play(v) }, onMenu = { continueMenu = v })
            }
        }
    }

    // Zurück geht innerhalb der Bibliothek einen Schritt zurück, statt die App zu schließen:
    // Suche zu → Kategorie aufheben → zurück zu »Für dich«. Erst von dort verlässt Zurück die App.
    // Steht bewusst vor den Überlagerungen weiter unten — deren eigenes Zurück hat Vorrang.
    BackHandler(enabled = isSearchActive || selectedGenre != Genre.ALL || selectedTab != LibraryTab.FOR_YOU) {
        when {
            isSearchActive -> {
                searchQuery = ""
                isSearchActive = false
            }
            selectedGenre != Genre.ALL -> selectedGenre = Genre.ALL
            else -> selectedTab = LibraryTab.FOR_YOU
        }
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
                Spacer(Modifier.width(12.dp))
                // Kategorie-Umschalter rechts neben dem Namen, vor Zufall und Suche.
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    GenreSwitcher(
                        selected = selectedGenre,
                        onOpen = { showGenrePicker = true },
                        onClear = { selectedGenre = Genre.ALL },
                    )
                }
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
                // Eine Kategorie ist gewählt: alles daraus als Plakat-Raster.
                PosterGrid(
                    items = genreItems(selectedGenre, "genre"),
                    emptyText = "In dieser Kategorie gibt es hier noch nichts.",
                )
            } else {
                when (selectedTab) {
                    LibraryTab.FOR_YOU -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            heroVideo?.let { h ->
                                item(key = "hero") {
                                    LibraryHero(h, h.series_id?.let(seriesById::get), onPlay = { play(h) })
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            if (continueWatching.isNotEmpty()) {
                                item {
                                    ContinueShelf("Weiterschauen", continueWatching, "cw")
                                    Spacer(Modifier.height(SECTION_GAP))
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
                                    Spacer(Modifier.height(SECTION_GAP))
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
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            // ── THEMEN & GENRES ENTDECKEN ──────────────────────────────
                            item {
                                MoodDiscoverySection(
                                    selectedGenre = selectedGenre,
                                    onSelectGenre = { selectedGenre = it },
                                    genreCounts = genreCounts,
                                    genreCovers = genreCovers,
                                )
                                Spacer(Modifier.height(SECTION_GAP))
                            }

                            // Wochenend-Kino (> 75 Min)
                            if (weekendMovies.isNotEmpty()) {
                                item {
                                    PosterShelf(
                                        title = "Wochenend-Kino",
                                        items = weekendMovies.map { videoPoster(it, "wk", "KINO") },
                                        onShowAll = showAll("Wochenend-Kino"),
                                    )
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            // ── Alles von YouTube: eigener Bereich hinter einem Banner ───
                            item(key = "channels-banner") {
                                ChannelsBanner(data = data, onClick = { showChannels = true })
                                Spacer(Modifier.height(SECTION_GAP))
                            }

                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }

                    LibraryTab.SERIES -> {
                        val continueSeries = continueWatching.filter { it.series_id != null }
                        val heroSeriesVideo = continueSeries.firstOrNull() ?: data.recentlyAdded.firstOrNull { it.series_id != null }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (heroSeriesVideo != null) {
                                item(key = "hero") {
                                    LibraryHero(
                                        heroSeriesVideo,
                                        heroSeriesVideo.series_id?.let(seriesById::get),
                                        onPlay = { play(heroSeriesVideo) },
                                    )
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
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
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            if (continueSeries.isNotEmpty()) {
                                item {
                                    ContinueShelf("Weiter bingen", continueSeries, "cs")
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            item {
                                PosterShelf(
                                    title = "Alle Serien",
                                    items = data.series.map { seriesPoster(it, "all") },
                                    onShowAll = showAll("Alle Serien"),
                                )
                                Spacer(Modifier.height(SECTION_GAP))
                            }

                            val animeSeries = data.series.filter { Genre.ANIME in it.detectGenres() }
                            if (animeSeries.isNotEmpty()) {
                                item {
                                    PosterShelf(
                                        title = "Anime-Serien",
                                        items = animeSeries.map { seriesPoster(it, "anime", tag = "ANIME", subtitle = it.description ?: "Anime") },
                                        onShowAll = showAll("Anime-Serien"),
                                    )
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            item { Spacer(Modifier.height(96.dp)) }
                        }
                    }

                    LibraryTab.MOVIES -> {
                        val heroMovie = allMovies.firstOrNull() ?: heroVideo
                        fun withChannel(m: LibraryVideoDto) = "${formatDuration(m.duration_seconds)} · ${m.channelTitle ?: ""}"

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (heroMovie != null) {
                                item(key = "hero") {
                                    LibraryHero(heroMovie, null, onPlay = { play(heroMovie) })
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
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
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            if (weekendMovies.isNotEmpty()) {
                                item {
                                    PosterShelf(
                                        title = "Spielfilme & Blockbuster",
                                        items = weekendMovies.map { videoPoster(it, "block", "KINO", withChannel(it)) },
                                        onShowAll = showAll("Spielfilme & Blockbuster"),
                                    )
                                    Spacer(Modifier.height(SECTION_GAP))
                                }
                            }

                            item {
                                PosterShelf(
                                    title = "Alle Filme & Dokus",
                                    items = allMovies.map { videoPoster(it, "all", "FILM", withChannel(it)) },
                                    onShowAll = showAll("Alle Filme & Dokus"),
                                )
                                Spacer(Modifier.height(SECTION_GAP))
                            }

                            val dokuMovies = allMovies.filter { Genre.DOCS in it.detectGenres() }
                            if (dokuMovies.isNotEmpty()) {
                                item {
                                    PosterShelf(
                                        title = "Doku & Wissen",
                                        items = dokuMovies.map { videoPoster(it, "doku", "DOKU") },
                                        onShowAll = showAll("Doku & Wissen"),
                                    )
                                    Spacer(Modifier.height(SECTION_GAP))
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
                                    genreCovers = genreCovers,
                                )
                                Spacer(Modifier.height(SECTION_GAP))
                            }

                            // Je Kategorie eine Reihe; »Alle« führt in die Kategorie selbst.
                            availableGenres.forEach { genre ->
                                item(key = "shelf-${genre.id}") {
                                    PosterShelf(
                                        title = genre.title,
                                        items = genreItems(genre, "shelf-${genre.id}"),
                                        onShowAll = { selectedGenre = genre },
                                    )
                                    Spacer(Modifier.height(SECTION_GAP))
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
            onDismiss = { quickLookItem = null },
        )
    }

    // Kanal-Bereich: schiebt sich von rechts über die Bibliothek.
    AnimatedVisibility(
        visible = showChannels,
        enter = slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)),
        exit = slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160)),
    ) {
        BackHandler(enabled = gridPage == null) { showChannels = false }
        ChannelsHome(
            data = data,
            onBack = { showChannels = false },
            onOpenChannel = onOpenChannel,
            onPlay = ::play,
        )
    }

    // Vollständige Übersicht einer Reihe, über der ganzen Bibliothek.
    AnimatedVisibility(
        visible = gridPage != null,
        enter = fadeIn() + slideInVertically { it / 24 },
        exit = fadeOut(),
    ) {
        gridPage?.let { (title, items) ->
            PosterGridPage(title = title, items = items, onBack = { gridPage = null })
        }
    }

    // Auswahl der Kategorie über den ganzen Bildschirm.
    AnimatedVisibility(visible = showGenrePicker, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
        GenrePickerOverlay(
            genres = availableGenres,
            selected = selectedGenre,
            onSelect = { selectedGenre = it },
            onDismiss = { showGenrePicker = false },
        )
    }

    continueMenu?.let { video ->
        val series = video.series_id?.let(seriesById::get)
        ContinueMenuSheet(
            title = series?.title ?: video.title,
            onOpenSeries = series?.let { { continueMenu = null; onOpenSeries(it.id) } },
            onRemove = {
                continueMenu = null
                onDismissContinue(video.id)
            },
            onDismiss = { continueMenu = null },
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

@Composable
internal fun SectionHeader(
    title: String,
    /** Zahl hinter dem Titel; `null` lässt sie weg (Weiterschauen zeigt ohnehin nur das Jüngste). */
    count: Int? = null,
    onShowAll: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString(),
                color = HikariTextFaint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel.uppercase(),
                color = HikariTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (onShowAll != null) {
            Text(
                "ALLE ›",
                color = HikariAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
                            Spacer(Modifier.height(SECTION_GAP))
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
                            Spacer(Modifier.height(SECTION_GAP))
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
                            Spacer(Modifier.height(SECTION_GAP))
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

