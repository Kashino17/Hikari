package com.hikari.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.ui.library.components.CoverEditSheet
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun LibraryScreen(
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val coverEdit by viewModel.coverEditState.collectAsState()
    val savedItems by viewModel.savedItems.collectAsState()
    val today by viewModel.today.collectAsState()
    var editingSeries by remember { mutableStateOf<SeriesDto?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is LibraryUiState.Loading ->
                CircularProgressIndicator(
                    color = HikariAmber,
                    modifier = Modifier.align(Alignment.Center),
                )
            is LibraryUiState.Error ->
                Text(
                    text = s.message,
                    color = HikariTextMuted,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            is LibraryUiState.Success ->
                LibraryContent(
                    s.data,
                    savedItems,
                    today,
                    onOpenSeries,
                    onOpenChannel,
                    onPlayVideo,
                    onLongPressSeries = { editingSeries = it },
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
                viewModel.setSeriesCoverUrl(series.id, url) {
                    editingSeries = null
                }
            },
            onPickGallery = { bytes, mime ->
                viewModel.uploadSeriesCover(series.id, bytes, mime) {
                    editingSeries = null
                }
            },
        )
    }
}

@Composable
private fun LibraryContent(
    data: LibraryResponse,
    savedItems: List<FeedItem>,
    today: TodayCountResponse?,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
) {
    fun play(v: LibraryVideoDto) = onPlayVideo(v.id, v.title, v.channelTitle ?: "")
    fun playSaved(v: FeedItem) = onPlayVideo(v.videoId, v.title, v.channelTitle)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { HeroSection(data.recentlyAdded.firstOrNull()) { play(it) } }

        item {
            DailyLightSection(
                data = data,
                savedItems = savedItems,
                today = today,
                onPlayVideo = { play(it) },
                onPlaySaved = { playSaved(it) },
            )
        }

        val continueWatching = data.recentlyAdded.filter { (it.progress_seconds ?: 0f) > 0f }
        if (continueWatching.isNotEmpty()) {
            item {
                Section(title = "Weitersehen") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { it.id }) { video ->
                            VideoCard(video) { play(video) }
                        }
                    }
                }
            }
        }

        if (savedItems.isNotEmpty()) {
            savedCollections(savedItems).forEach { collection ->
                item {
                    Section(title = collection.title) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(collection.videos, key = { "${collection.title}-${it.videoId}" }) { video ->
                                SavedVideoCard(video) { playSaved(video) }
                            }
                        }
                    }
                }
            }
        }

        if (data.series.isNotEmpty()) {
            item {
                Section(title = "Serien") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(data.series, key = { it.id }) { series ->
                            SeriesCard(
                                series = series,
                                onClick = { onOpenSeries(series.id) },
                                onLongClick = { onLongPressSeries(series) },
                            )
                        }
                    }
                }
            }
        }

        if (data.channels.isNotEmpty()) {
            item {
                Section(title = "Kanäle") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(data.channels, key = { it.id }) { channel ->
                            ChannelAvatar(channel) { onOpenChannel(channel.id) }
                        }
                    }
                }
            }
        }

        item {
            Section(title = "Neu hinzugefügt") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.recentlyAdded, key = { it.id }) { video ->
                        VideoCard(video) { play(video) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ─── Daily Light ─────────────────────────────────────────────────────────────

private data class DailyLightCard(
    val label: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val onClick: () -> Unit,
)

@Composable
private fun DailyLightSection(
    data: LibraryResponse,
    savedItems: List<FeedItem>,
    today: TodayCountResponse?,
    onPlayVideo: (LibraryVideoDto) -> Unit,
    onPlaySaved: (FeedItem) -> Unit,
) {
    val continueVideo = data.recentlyAdded.firstOrNull { (it.progress_seconds ?: 0f) > 0f }
    val quickVideo = data.recentlyAdded.firstOrNull { it.duration_seconds <= 5 * 60 }
    val savedPick = savedItems.firstOrNull()
    val newest = data.recentlyAdded.firstOrNull()

    val cards = listOfNotNull(
        newest?.let { video ->
            DailyLightCard(
                label = "Neu",
                title = video.title,
                subtitle = video.channelTitle ?: "Hikari",
                thumbnailUrl = video.thumbnail_url,
                onClick = { onPlayVideo(video) },
            )
        },
        continueVideo?.takeIf { it.id != newest?.id }?.let { video ->
            DailyLightCard(
                label = "Weiter",
                title = video.title,
                subtitle = progressLabel(video),
                thumbnailUrl = video.thumbnail_url,
                onClick = { onPlayVideo(video) },
            )
        },
        quickVideo?.takeIf { it.id != newest?.id && it.id != continueVideo?.id }?.let { video ->
            DailyLightCard(
                label = "Kurz",
                title = video.title,
                subtitle = durationLabel(video.duration_seconds),
                thumbnailUrl = video.thumbnail_url,
                onClick = { onPlayVideo(video) },
            )
        },
        savedPick?.let { video ->
            DailyLightCard(
                label = "Saved",
                title = video.title,
                subtitle = video.category.replaceFirstChar { it.uppercase() },
                thumbnailUrl = video.thumbnailUrl,
                onClick = { onPlaySaved(video) },
            )
        },
    ).distinctBy { it.title }.take(4)

    if (cards.isEmpty()) return

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Daily Light",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = today?.let { "${it.unseenCount} neue Videos im heutigen Licht" }
                        ?: "Dein kompakter Einstieg fuer heute",
                    color = HikariTextFaint,
                    fontSize = 12.sp,
                )
            }
            today?.let {
                Text(
                    text = "${it.unseenCount}/${it.dailyBudget}",
                    color = if (it.capped) HikariAmber else HikariTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(cards, key = { "${it.label}-${it.title}" }) { card ->
                DailyLightCardView(card)
            }
        }
    }
}

@Composable
private fun DailyLightCardView(card: DailyLightCard) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(128.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HikariSurface)
            .clickable(onClick = card.onClick),
    ) {
        AsyncImage(
            model = card.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.86f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        ) {
            Text(
                text = card.label.uppercase(),
                color = HikariAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.subtitle,
                color = HikariTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Smart Saved Collections ────────────────────────────────────────────────

private data class SavedCollection(val title: String, val videos: List<FeedItem>)

private fun savedCollections(savedItems: List<FeedItem>): List<SavedCollection> {
    val collections = mutableListOf<SavedCollection>()
    val quick = savedItems.filter { it.durationSeconds <= 5 * 60 }
    val deep = savedItems.filter { it.durationSeconds >= 12 * 60 }
    val highLearning = savedItems.filter {
        (it.educationalValue ?: 0) >= 8 || (it.overallScore ?: 0) >= 85
    }

    if (highLearning.isNotEmpty()) collections += SavedCollection("Gespeichert · Hoher Lernwert", highLearning)
    if (quick.isNotEmpty()) collections += SavedCollection("Gespeichert · Kurz ansehen", quick)
    if (deep.isNotEmpty()) collections += SavedCollection("Gespeichert · Deep Dives", deep)

    savedItems
        .groupBy { it.category.ifBlank { "Thema" } }
        .entries
        .sortedByDescending { it.value.size }
        .take(3)
        .forEach { (category, videos) ->
            collections += SavedCollection("Thema · ${category.replaceFirstChar { it.uppercase() }}", videos)
        }

    savedItems
        .groupBy { it.channelTitle }
        .entries
        .sortedByDescending { it.value.size }
        .take(2)
        .forEach { (channel, videos) ->
            collections += SavedCollection("Kanal · $channel", videos)
        }

    return collections
        .filter { it.videos.isNotEmpty() }
        .distinctBy { it.title }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(video: LibraryVideoDto?, onPlay: (LibraryVideoDto) -> Unit) {
    if (video == null) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(HikariSurface))
        return
    }
    Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
        AsyncImage(
            model = video.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Top scrim — readable status bar
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
        // Bottom scrim — heavy fade into page background, plus mid-band so
        // text reads even on a busy/storyboard thumbnail
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            HikariBg.copy(alpha = 0.55f),
                            HikariBg,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = (video.channelTitle ?: "Hikari").uppercase(),
                color = HikariAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            HeroButton(
                label = "Abspielen",
                icon = Icons.Default.PlayArrow,
                background = Color.White,
                contentColor = Color.Black,
                onClick = { onPlay(video) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain Box-as-button so HikariTypography's hardcoded color doesn't override
    // the button's contentColor (the bug that left the white "Abspielen" empty).
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Section header ──────────────────────────────────────────────────────────

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 28.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
        content()
    }
}

// ─── Cards ───────────────────────────────────────────────────────────────────

@Composable
private fun VideoCard(video: LibraryVideoDto, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            AsyncImage(
                model = video.thumbnail_url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            val progress = video.progress_seconds?.let { it / video.duration_seconds.toFloat() }
            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
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
        Text(
            text = video.title,
            color = HikariText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SavedVideoCard(video: FeedItem, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = durationLabel(video.durationSeconds),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Text(
            text = video.title,
            color = HikariText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = video.channelTitle,
            color = HikariTextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun durationLabel(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}

private fun progressLabel(video: LibraryVideoDto): String {
    val progress = video.progress_seconds ?: return video.channelTitle ?: "Weitersehen"
    val percent = (progress / video.duration_seconds.toFloat()).coerceIn(0f, 1f)
    return "${(percent * 100).toInt()}% gesehen"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesCard(
    series: SeriesDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(HikariSurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = series.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        )
        Text(
            text = series.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

@Composable
private fun ChannelAvatar(channel: ChannelDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(80.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
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
                    text = channel.title.firstOrNull()?.uppercase() ?: "•",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Text(
            text = channel.title,
            color = HikariTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Stable per-channel gradient derived from the title — Spotify-style avatar fallback. */
private fun channelGradient(title: String): Brush {
    val palette = listOf(
        Color(0xFFB45309) to Color(0xFFF59E0B), // amber/copper
        Color(0xFF1E40AF) to Color(0xFF60A5FA), // indigo/azure
        Color(0xFF166534) to Color(0xFF34D399), // emerald
        Color(0xFF7E22CE) to Color(0xFFC084FC), // violet
        Color(0xFFBE185D) to Color(0xFFF472B6), // rose
        Color(0xFF374151) to Color(0xFF6B7280), // slate
    )
    val (start, end) = palette[(title.hashCode() and 0x7fffffff) % palette.size]
    return Brush.linearGradient(listOf(start, end))
}
