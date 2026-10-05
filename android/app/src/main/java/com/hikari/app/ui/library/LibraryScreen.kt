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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.hikari.app.domain.genre.Genre
import com.hikari.app.domain.genre.detectGenres
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.ui.library.components.CoverEditSheet
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
                onOpenSeries = onOpenSeries,
                onOpenChannel = onOpenChannel,
                onPlayVideo = onPlayVideo,
                onLongPressSeries = { editingSeries = it },
                watchLater = watchLater,
                saved = savedItems,
                history = history,
                onRemoveWatchLater = { viewModel.removeWatchLater(it) },
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
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    watchLater: List<FeedItem> = emptyList(),
    saved: List<FeedItem> = emptyList(),
    history: List<FeedItem> = emptyList(),
    onRemoveWatchLater: (String) -> Unit = {},
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf(Genre.ALL) }

    val continueWatching = data.recentlyAdded.filter {
        val p = it.progress_seconds ?: 0f
        p > 0f && p < it.duration_seconds.toFloat() * 0.95f
    }
    // "Empfohlen für dich" = top-scored Videos (immer Inhalt sobald
    // recentlyAdded nicht leer). Sortiert by overall_score desc, gefiltert
    // gegen Items die schon im Continue-Watching sind damit's nicht doppelt.
    val cwIds = continueWatching.map { it.id }.toSet()
    val recommended = data.recentlyAdded
        .filter { it.id !in cwIds }
        .sortedByDescending { it.overall_score ?: 0 }
        .take(10)

    val heroVideo = continueWatching.firstOrNull() ?: data.recentlyAdded.firstOrNull()
    val isFiltering = searchQuery.isNotBlank() || selectedGenre != Genre.ALL

    val genreCounts = remember(data) {
        Genre.entries.associateWith { g ->
            if (g == Genre.ALL) {
                data.series.size + data.recentlyAdded.size
            } else {
                data.series.count { g in it.detectGenres() } +
                    data.recentlyAdded.count { g in it.detectGenres() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Suchleiste ────────────────────────────────────────────────────────
        LibrarySearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = { searchQuery = "" },
        )

        // ── Netflix-Style Genre Filter ────────────────────────────────────────
        LibraryGenreFilterRow(
            selectedGenre = selectedGenre,
            onSelectGenre = { selectedGenre = it },
            genreCounts = genreCounts,
        )

        Spacer(Modifier.height(4.dp))

        if (isFiltering) {
            FilteredLibraryContent(
                data = data,
                searchQuery = searchQuery,
                selectedGenre = selectedGenre,
                onOpenSeries = onOpenSeries,
                onOpenChannel = onOpenChannel,
                onPlayVideo = onPlayVideo,
                onLongPressSeries = onLongPressSeries,
                onResetFilter = {
                    searchQuery = ""
                    selectedGenre = Genre.ALL
                },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { HeroSection(video = heroVideo, onPlay = ::play) }

        // ── Deine Sammlung (Etappe 5) ────────────────────────────────────────
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
                Spacer(Modifier.height(28.dp))
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
                Spacer(Modifier.height(28.dp))
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
                Spacer(Modifier.height(28.dp))
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
                Spacer(Modifier.height(28.dp))
            }
        }

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
            }
        }

        if (recommended.isNotEmpty()) {
            item {
                SectionHeader("Empfohlen für dich", count = recommended.size)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recommended, key = { "rec-${it.id}" }) { v ->
                        RecommendedCard(video = v, onClick = { play(v) })
                    }
                }
            }
        }

        if (data.series.isNotEmpty()) {
            item {
                SectionHeader("Serien", count = data.series.size)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.series, key = { it.id }) { s ->
                        SeriesPosterCard(
                            series = s,
                            onClick = { onOpenSeries(s.id) },
                            onLongClick = { onLongPressSeries(s) },
                        )
                    }
                }
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
            Text(
                text = if (isResume) "▶ WEITERSCHAUEN" else "★ NEU IN DER BIBLIOTHEK",
                color = HikariAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
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
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isResume) "Weiterschauen" else "Abspielen",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x66202020))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Info",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariSurfaceHigh)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Suchen",
                tint = if (query.isNotBlank()) HikariAmber else HikariTextFaint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = HikariText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(HikariAmber),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Serien, Filme, Kanäle durchsuchen…",
                            color = HikariTextFaint,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Löschen",
                    tint = HikariTextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClear() },
                )
            }
        }
    }
}

@Composable
private fun LibraryGenreFilterRow(
    selectedGenre: Genre,
    onSelectGenre: (Genre) -> Unit,
    genreCounts: Map<Genre, Int>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Genre.entries.forEach { genre ->
            val isSelected = genre == selectedGenre
            val count = genreCounts[genre] ?: 0
            val bg = if (isSelected) genre.accentColor.copy(alpha = 0.25f) else HikariSurfaceHigh
            val border = if (isSelected) genre.accentColor else HikariBorder

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(0.75.dp, border, RoundedCornerShape(10.dp))
                    .clickable { onSelectGenre(genre) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(genre.emoji, fontSize = 12.sp)
                    Text(
                        text = genre.title,
                        color = if (isSelected) Color.White else HikariText,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (count > 0 && genre != Genre.ALL) {
                        Text(
                            text = "$count",
                            color = if (isSelected) genre.accentColor else HikariTextFaint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilteredLibraryContent(
    data: LibraryResponse,
    searchQuery: String,
    selectedGenre: Genre,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    onResetFilter: () -> Unit,
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    val matchingSeries = remember(data.series, searchQuery, selectedGenre) {
        data.series.filter { s ->
            val matchesGenre = selectedGenre == Genre.ALL || selectedGenre in s.detectGenres()
            val matchesQuery = searchQuery.isBlank() ||
                s.title.contains(searchQuery, ignoreCase = true) ||
                (s.description?.contains(searchQuery, ignoreCase = true) == true)
            matchesGenre && matchesQuery
        }
    }

    val matchingVideos = remember(data.recentlyAdded, searchQuery, selectedGenre) {
        data.recentlyAdded.filter { v ->
            val matchesGenre = selectedGenre == Genre.ALL || selectedGenre in v.detectGenres()
            val matchesQuery = searchQuery.isBlank() ||
                v.title.contains(searchQuery, ignoreCase = true) ||
                (v.channelTitle?.contains(searchQuery, ignoreCase = true) == true) ||
                (v.description?.contains(searchQuery, ignoreCase = true) == true)
            matchesGenre && matchesQuery
        }
    }

    val matchingChannels = remember(data.channels, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else data.channels.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val isEmpty = matchingSeries.isEmpty() && matchingVideos.isEmpty() && matchingChannels.isEmpty()

    if (isEmpty) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 42.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Keine Treffer gefunden",
                    color = HikariText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Keine Serien oder Videos passen zu deiner Auswahl.",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onResetFilter,
                    colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Filter zurücksetzen", color = HikariAmber, fontSize = 12.sp)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            // Genre-Banner if selected
            if (selectedGenre != Genre.ALL) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(selectedGenre.accentColor.copy(alpha = 0.15f))
                            .border(0.5.dp, selectedGenre.accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedGenre.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    selectedGenre.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${matchingSeries.size + matchingVideos.size} Inhalte verfügbar",
                                    color = selectedGenre.accentColor,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (matchingSeries.isNotEmpty()) {
                item {
                    SectionHeader("Serien", count = matchingSeries.size)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(matchingSeries, key = { "match-s-${it.id}" }) { s ->
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

            if (matchingChannels.isNotEmpty()) {
                item {
                    SectionHeader("Kanäle", count = matchingChannels.size)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(matchingChannels, key = { "match-c-${it.id}" }) { c ->
                            ChannelCircle(channel = c, onClick = { onOpenChannel(c.id) })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (matchingVideos.isNotEmpty()) {
                item {
                    SectionHeader("Videos & Filme", count = matchingVideos.size)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(matchingVideos, key = { "match-v-${it.id}" }) { v ->
                            RecommendedCard(video = v, onClick = { play(v) })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

