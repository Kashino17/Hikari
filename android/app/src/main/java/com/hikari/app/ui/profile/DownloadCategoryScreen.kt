package com.hikari.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.hikari.app.data.api.dto.ChannelGroupDto
import com.hikari.app.data.api.dto.MovieEntryDto
import com.hikari.app.data.api.dto.SeriesGroupDto
import com.hikari.app.ui.profile.components.DownloadGroupCard
import com.hikari.app.ui.profile.components.EpisodeRow
import com.hikari.app.ui.profile.components.GroupCoverShape
import com.hikari.app.ui.profile.components.IconPill
import com.hikari.app.ui.profile.tabs.DownloadCategory
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun DownloadCategoryScreen(
    onBack: () -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    vm: DownloadCategoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = when (state.category) {
                    DownloadCategory.SERIES -> "SERIEN"
                    DownloadCategory.CHANNELS -> "KANÄLE"
                    DownloadCategory.MOVIES -> "FILME"
                },
                editMode = state.editMode,
                selectedCount = state.selectedVideoIds.size,
                onBack = onBack,
                onToggleEdit = { vm.setEditMode(!state.editMode) },
                onCancelEdit = { vm.setEditMode(false) },
                onDeleteSelected = { vm.deleteSelected() },
            )

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = HikariAmber) }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error!!, color = HikariTextMuted, fontSize = 13.sp) }

                state.data != null -> {
                    val data = state.data!!
                    val localIds by vm.localIds.collectAsState()
                    val progress by vm.downloadProgress.collectAsState()
                    when (state.category) {
                        DownloadCategory.SERIES -> SeriesPanel(
                            groups = data.series,
                            state = state,
                            localIds = localIds,
                            downloadProgress = progress,
                            onToggle = { vm.toggleExpand(it) },
                            onSelect = { vm.toggleSelection(it) },
                            onPlay = { onPlayVideo(it.id, it.title, "") },
                            onDownload = { vm.downloadLocally(it.id, it.duration_seconds) },
                            onRemoveLocal = { vm.removeLocal(it.id) },
                        )
                        DownloadCategory.CHANNELS -> ChannelsPanel(
                            groups = data.channels,
                            state = state,
                            localIds = localIds,
                            downloadProgress = progress,
                            onToggle = { vm.toggleExpand(it) },
                            onSelect = { vm.toggleSelection(it) },
                            onPlay = { v, channelTitle ->
                                onPlayVideo(v.id, v.title, channelTitle)
                            },
                            onDownload = { v -> vm.downloadLocally(v.id, v.duration_seconds) },
                            onRemoveLocal = { v -> vm.removeLocal(v.id) },
                        )
                        DownloadCategory.MOVIES -> MoviesPanel(
                            movies = data.movies,
                            state = state,
                            localIds = localIds,
                            downloadProgress = progress,
                            onSelect = { vm.toggleSelection(it) },
                            onPlay = { onPlayVideo(it.id, it.title, "") },
                            onDownload = { m -> vm.downloadLocally(m.id, m.duration_seconds) },
                            onRemoveLocal = { m -> vm.removeLocal(m.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    editMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onToggleEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode) {
            IconPill(
                icon = Icons.Default.Close,
                contentDescription = "Auswahl aufheben",
                onClick = onCancelEdit,
                label = "Abbrechen",
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$selectedCount AUSGEWÄHLT",
                color = HikariTextFaint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(HikariDanger.copy(alpha = 0.16f))
                    .border(0.5.dp, HikariDanger.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable(enabled = selectedCount > 0, onClick = onDeleteSelected)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = HikariDanger,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Löschen", color = HikariDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            IconPill(
                icon = Icons.Default.Close,
                contentDescription = "Zurück",
                onClick = onBack,
                label = "Downloads",
            )
            Text(
                title,
                color = HikariTextFaint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(18.dp))
                    .clickable(onClick = onToggleEdit)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bearbeiten", color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Hero(title: String, meta: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            title,
            color = HikariText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.sp,
        )
        Text(
            meta,
            color = HikariTextFaint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SeriesPanel(
    groups: List<SeriesGroupDto>,
    state: DownloadCategoryUiState,
    localIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onPlay: (com.hikari.app.data.api.dto.SeriesEpisodeDto) -> Unit,
    onDownload: (com.hikari.app.data.api.dto.SeriesEpisodeDto) -> Unit,
    onRemoveLocal: (com.hikari.app.data.api.dto.SeriesEpisodeDto) -> Unit,
) {
    val totalEps = groups.sumOf { it.episode_count }
    val totalSize = groups.sumOf { it.total_bytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { Hero("Serien", "${groups.size} Serien · $totalEps Folgen · ${formatBytes(totalSize)}") }
        item { SimpleFilterStrip("ALLE", groups.size) }
        items(groups) { group ->
            DownloadGroupCard(
                title = group.title,
                meta = "${group.episode_count} Folgen · ${formatBytes(group.total_bytes)}",
                coverShape = GroupCoverShape.SQUARE,
                coverUrl = group.thumbnail_url,
                fallbackInitial = group.title.firstOrNull()?.uppercaseChar()?.toString(),
                expanded = state.expandedGroupId == group.id,
                itemCount = group.episode_count,
                selectionMode = state.editMode,
                selected = false,
                onToggle = { onToggle(group.id) },
                onCheckChange = null,
                expandedContent = {
                    androidx.compose.foundation.layout.Column {
                        group.episodes.forEach { ep ->
                            val isLocal = localIds.contains(ep.id)
                            val progress = downloadProgress[ep.id]
                            EpisodeRow(
                                title = "F${ep.episode ?: "-"} · ${ep.title}",
                                meta = "${formatBytes(ep.size_bytes)} · ${formatDuration(ep.duration_seconds)}",
                                duration = formatDuration(ep.duration_seconds),
                                thumbnailUrl = ep.thumbnail_url,
                                selectionMode = state.editMode,
                                selected = state.selectedVideoIds.contains(ep.id),
                                onPlay = { onPlay(ep) },
                                onCheckChange = { onSelect(ep.id) },
                                localState = when {
                                    isLocal -> com.hikari.app.ui.profile.components.LocalState.Local
                                    progress != null -> com.hikari.app.ui.profile.components.LocalState.Downloading
                                    else -> com.hikari.app.ui.profile.components.LocalState.NotLocal
                                },
                                downloadProgress = progress,
                                onDownloadClick = {
                                    if (isLocal) onRemoveLocal(ep) else onDownload(ep)
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ChannelsPanel(
    groups: List<ChannelGroupDto>,
    state: DownloadCategoryUiState,
    localIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onPlay: (com.hikari.app.data.api.dto.ChannelVideoEntryDto, String) -> Unit,
    onDownload: (com.hikari.app.data.api.dto.ChannelVideoEntryDto) -> Unit,
    onRemoveLocal: (com.hikari.app.data.api.dto.ChannelVideoEntryDto) -> Unit,
) {
    val totalVids = groups.sumOf { it.video_count }
    val totalSize = groups.sumOf { it.total_bytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { Hero("Kanäle", "${groups.size} Kanäle · $totalVids Videos · ${formatBytes(totalSize)}") }
        item { SimpleFilterStrip("ALLE", groups.size) }
        items(groups) { group ->
            DownloadGroupCard(
                title = group.title,
                meta = "${group.video_count} Videos · ${formatBytes(group.total_bytes)}",
                coverShape = GroupCoverShape.CIRCLE,
                coverUrl = group.thumbnail_url,
                fallbackInitial = group.title.firstOrNull()?.uppercaseChar()?.toString(),
                expanded = state.expandedGroupId == group.id,
                itemCount = group.video_count,
                selectionMode = state.editMode,
                selected = false,
                onToggle = { onToggle(group.id) },
                onCheckChange = null,
                expandedContent = {
                    androidx.compose.foundation.layout.Column {
                        group.videos.forEach { v ->
                            val isLocal = localIds.contains(v.id)
                            val progress = downloadProgress[v.id]
                            EpisodeRow(
                                title = v.title,
                                meta = "${formatBytes(v.size_bytes)} · ${formatDuration(v.duration_seconds)}",
                                duration = formatDuration(v.duration_seconds),
                                thumbnailUrl = v.thumbnail_url,
                                selectionMode = state.editMode,
                                selected = state.selectedVideoIds.contains(v.id),
                                onPlay = { onPlay(v, group.title) },
                                onCheckChange = { onSelect(v.id) },
                                localState = when {
                                    isLocal -> com.hikari.app.ui.profile.components.LocalState.Local
                                    progress != null -> com.hikari.app.ui.profile.components.LocalState.Downloading
                                    else -> com.hikari.app.ui.profile.components.LocalState.NotLocal
                                },
                                downloadProgress = progress,
                                onDownloadClick = {
                                    if (isLocal) onRemoveLocal(v) else onDownload(v)
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MoviesPanel(
    movies: List<MovieEntryDto>,
    state: DownloadCategoryUiState,
    localIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onSelect: (String) -> Unit,
    onPlay: (MovieEntryDto) -> Unit,
    onDownload: (MovieEntryDto) -> Unit,
    onRemoveLocal: (MovieEntryDto) -> Unit,
) {
    val totalSize = movies.sumOf { it.size_bytes }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Hero("Filme", "${movies.size} Filme · ${formatBytes(totalSize)}")
        }
        item(span = { GridItemSpan(2) }) {
            SimpleFilterStrip("ALLE", movies.size)
        }
        gridItems(movies, key = { it.id }) { movie ->
            val isLocal = localIds.contains(movie.id)
            val progress = downloadProgress[movie.id]
            MoviePoster(
                movie = movie,
                selected = state.selectedVideoIds.contains(movie.id),
                selectionMode = state.editMode,
                isLocal = isLocal,
                downloadProgress = progress,
                onClick = {
                    if (state.editMode) onSelect(movie.id) else onPlay(movie)
                },
                onDownloadToggle = {
                    if (isLocal) onRemoveLocal(movie) else onDownload(movie)
                },
            )
        }
    }
}

@Composable
private fun MoviePoster(
    movie: MovieEntryDto,
    selected: Boolean,
    selectionMode: Boolean,
    isLocal: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownloadToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(HikariSurfaceHigh)
            .clickable(onClick = onClick),
    ) {
        if (!movie.thumbnail_url.isNullOrBlank()) {
            AsyncImage(
                model = movie.thumbnail_url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                ),
        )
        Text(
            "↓ ${formatBytes(movie.size_bytes)}",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(HikariAmber, RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                movie.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatDuration(movie.duration_seconds)}",
                color = HikariAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (selected) HikariAmber else Color.Black.copy(alpha = 0.4f))
                    .border(
                        1.dp,
                        if (selected) HikariAmber else HikariBorder,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                com.hikari.app.ui.profile.components.LocalDownloadIcon(
                    state = when {
                        isLocal -> com.hikari.app.ui.profile.components.LocalState.Local
                        downloadProgress != null -> com.hikari.app.ui.profile.components.LocalState.Downloading
                        else -> com.hikari.app.ui.profile.components.LocalState.NotLocal
                    },
                    progress = downloadProgress,
                    onClick = onDownloadToggle,
                )
            }
        }
    }
}

@Composable
private fun SimpleFilterStrip(activeLabel: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(HikariAmber)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                "$activeLabel · $count",
                color = HikariBg,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
