package com.hikari.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
                onRemoveHistory = { viewModel.removeFromHistory(it) },
                onClearHistory = { viewModel.clearHistory() },
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
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var allView by remember { mutableStateOf<AllViewState?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }

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
            // ── Kopf: Schriftzug + Suche ─────────────────────────────────────
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
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Suchen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            LibraryHome(
                data = data,
                onOpenSeries = onOpenSeries,
                onOpenChannel = onOpenChannel,
                onPlay = ::play,
                onLongPressSeries = onLongPressSeries,
                onShowAll = { title, videos ->
                    allView = AllViewState(
                        title = title,
                        items = videos.map { v ->
                            AllViewItem(
                                id = v.id,
                                title = v.title,
                                channel = v.channelTitle,
                                durationSeconds = v.duration_seconds,
                                thumbnailUrl = v.thumbnail_url,
                                onClick = { play(v) },
                            )
                        },
                    )
                },
                onRemoveHistory = onRemoveHistory,
                onClearHistory = { confirmClearHistory = true },
            )
        }
    }

    if (confirmClearHistory) {
        com.hikari.app.ui.components.ConfirmDeleteDialog(
            title = "Verlauf leeren?",
            message = "Alle ${data.watchHistory.size} Einträge verschwinden aus dem Verlauf. Die Videos selbst " +
                "und dein Fortschritt (Weiterschauen) bleiben erhalten.",
            confirmLabel = "Leeren",
            onConfirm = onClearHistory,
            onDismiss = { confirmClearHistory = false },
        )
    }

    // „Alle"-Vollbildansicht über dem gesamten Bibliotheks-Content.
    AnimatedVisibility(
        visible = allView != null,
        enter = fadeIn() + slideInVertically { it / 24 },
        exit = fadeOut(),
    ) {
        allView?.let { state ->
            AllVideosOverlay(state = state, onDismiss = { allView = null })
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onShowAll: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
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

// ─── „Alle"-Vollbildansicht (YouTube-Stil) ───────────────────────────────────

private data class AllViewItem(
    val id: String,
    val title: String,
    val channel: String?,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val onClick: () -> Unit,
)

private data class AllViewState(val title: String, val items: List<AllViewItem>)

@Composable
private fun AllVideosOverlay(state: AllViewState, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Column(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = Color.White,
                )
            }
            Column {
                Text(
                    text = state.title,
                    color = HikariText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${state.items.size} Videos",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                AllVideosRow(
                    item = item,
                    onClick = {
                        item.onClick()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun AllVideosRow(item: AllViewItem, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        // Thumbnail Edge-to-Edge, fast eckig — wie im YouTube-Feed.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(2.dp))
                .background(HikariSurface),
        ) {
            FallbackArtwork(title = item.title)
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                "${item.durationSeconds / 60}:${"%02d".format(item.durationSeconds % 60)}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = item.title,
                color = HikariText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                item.channel?.takeIf { it.isNotBlank() },
                "${item.durationSeconds / 60}:${"%02d".format(item.durationSeconds % 60)}",
            ).joinToString(" · ")
            Text(
                text = subtitle,
                color = HikariTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
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

/**
 * YouTube-Look für Verlauf und Kanal-Uploads: Bild oben mit Dauer und
 * Fortschrittsbalken, Titel und Kanal darunter. [onRemove] blendet ein
 * sichtbares X ein — Entfernen per Langdruck fand niemand.
 */
@Composable
private fun LibraryVideoCard(
    video: LibraryVideoDto,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val fraction = video.progress_seconds?.let { p ->
        if (video.duration_seconds > 0) (p / video.duration_seconds).coerceIn(0f, 1f) else 0f
    } ?: 0f
    Column(modifier = Modifier.width(260.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(HikariSurface),
        ) {
            FallbackArtwork(title = video.title)
            AsyncImage(
                model = video.thumbnail_url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                "${video.duration_seconds / 60}:${"%02d".format(video.duration_seconds % 60)}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Aus dem Verlauf entfernen",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (fraction > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(HikariAmber),
                    )
                }
            }
        }
        Text(
            text = video.title,
            color = HikariText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = video.channelTitle?.takeIf { it.isNotBlank() } ?: "Video",
            color = HikariTextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
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
                (s.description?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val matchingVideos = remember(data.recentlyAdded, searchQuery) {
        if (isBlank) emptyList()
        else data.recentlyAdded.filter { v ->
            v.title.contains(searchQuery, ignoreCase = true) ||
                (v.channelTitle?.contains(searchQuery, ignoreCase = true) == true) ||
                (v.description?.contains(searchQuery, ignoreCase = true) == true)
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
                                    text = "Serien, Filme, Kanäle …",
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
                                    LibraryVideoCard(video = v, onClick = { play(v) })
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

