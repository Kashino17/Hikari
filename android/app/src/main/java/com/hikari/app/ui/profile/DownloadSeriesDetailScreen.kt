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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hikari.app.data.db.PlaybackPositionEntity
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.library.EpisodeTitles
import com.hikari.app.ui.profile.components.LocalDownloadIcon
import com.hikari.app.ui.profile.components.LocalState
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Eigenständige Detailseite einer heruntergeladenen Serie.
 *
 * Aufbau wie im Bibliotheks-Serien-Detail: großes Artwork, Beschreibung,
 * Staffelwahl und Folgen mit großen Thumbnails. Der Unterschied ist der
 * Download-Status: jede Folge lässt sich hier direkt auf das Gerät holen
 * oder wieder entfernen, ganze Staffeln auf einmal inklusive.
 */
@Composable
fun DownloadSeriesDetailScreen(
    onBack: () -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    viewModel: DownloadDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localIds by viewModel.localIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val positions by viewModel.playbackPositions.collectAsState()
    val bulkDownloading by viewModel.bulkDownloading.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is DownloadDetailUiState.Loading -> CircularProgressIndicator(
                color = HikariAmber,
                modifier = Modifier.align(Alignment.Center),
            )
            is DownloadDetailUiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = HikariTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Zurück",
                    color = HikariAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            is DownloadDetailUiState.Movie -> Unit
            is DownloadDetailUiState.Series -> SeriesDetailContent(
                detail = s.data,
                localIds = localIds,
                downloadProgress = downloadProgress,
                positions = positions,
                bulkDownloading = bulkDownloading,
                actionError = actionError,
                onBack = onBack,
                onPlay = { ep -> onPlayVideo(ep.id, ep.title, "") },
                onDownload = viewModel::downloadEpisode,
                onRemoveLocal = viewModel::removeLocal,
                onBulkDownload = viewModel::downloadEpisodes,
                onDismissError = viewModel::clearActionError,
            )
        }
    }
}

@Composable
private fun SeriesDetailContent(
    detail: DownloadSeriesDetail,
    localIds: Set<String>,
    downloadProgress: Map<String, Float>,
    positions: Map<String, PlaybackPositionEntity>,
    bulkDownloading: Boolean,
    actionError: String?,
    onBack: () -> Unit,
    onPlay: (DownloadDetailEpisode) -> Unit,
    onDownload: (DownloadDetailEpisode) -> Unit,
    onRemoveLocal: (String) -> Unit,
    onBulkDownload: (List<DownloadDetailEpisode>) -> Unit,
    onDismissError: () -> Unit,
) {
    val seasons = remember(detail.episodes) {
        detail.episodes.mapNotNull { it.season }.distinct().sorted()
    }
    var selectedSeason by remember(seasons) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }

    val episodesForSeason = remember(detail.episodes, selectedSeason, seasons) {
        if (seasons.isEmpty()) {
            detail.episodes.sortedBy { it.episode ?: 0 }
        } else {
            detail.episodes.filter { (it.season ?: 1) == selectedSeason }
                .sortedBy { it.episode ?: 0 }
        }
    }

    // „Weiterschauen“ wie in der Bibliothek: zuletzt geschaute unfertige Folge,
    // sonst die erste Folge, sonst die erste lokal vorhandene.
    fun fractionOf(ep: DownloadDetailEpisode): Float {
        val seconds = progressSeconds(positions[ep.id], ep.serverProgressSeconds)
        return if (ep.durationSeconds > 0) {
            (seconds / ep.durationSeconds).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val resumeEpisode = remember(detail.episodes, positions) {
        val unfinished = detail.episodes.filter { ep ->
            val f = fractionOf(ep)
            f > 0f && f < 0.95f
        }
        unfinished.maxByOrNull { positions[it.id]?.updatedAt ?: 0L }
            ?: detail.episodes.firstOrNull { localIds.contains(it.id) }
            ?: detail.episodes.firstOrNull()
    }

    val missingForSeason = episodesForSeason.filter { it.available && !localIds.contains(it.id) }
    val allLocal = episodesForSeason.isNotEmpty() && missingForSeason.isEmpty()
    val resumeFraction = resumeEpisode?.let { fractionOf(it) } ?: 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            SeriesHero(
                detail = detail,
                resumeEpisode = resumeEpisode,
                resumeFraction = resumeFraction,
                localCount = detail.episodes.count { localIds.contains(it.id) },
                onBack = onBack,
                onPlay = { resumeEpisode?.let(onPlay) },
            )
        }

        if (actionError != null) {
            item {
                ErrorBanner(text = actionError, onDismiss = onDismissError)
            }
        }

        if (detail.offlineOnly) {
            item {
                Text(
                    text = "Offline — es werden nur die Folgen auf diesem Gerät angezeigt.",
                    color = HikariAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        if (!detail.description.isNullOrBlank()) {
            item {
                Text(
                    text = detail.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (seasons.size >= 2) {
                    SeasonDropdown(
                        seasons = seasons,
                        selected = selectedSeason,
                        onChange = { selectedSeason = it },
                    )
                } else {
                    Text(
                        text = "Folgen",
                        color = HikariText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (episodesForSeason.isNotEmpty()) {
                    SeasonDownloadButton(
                        missingCount = missingForSeason.size,
                        allLocal = allLocal,
                        downloading = bulkDownloading,
                        enabled = !detail.offlineOnly,
                        onClick = { onBulkDownload(missingForSeason) },
                    )
                }
            }
        }

        items(episodesForSeason, key = { it.id }) { episode ->
            val isLocal = localIds.contains(episode.id)
            val progress = downloadProgress[episode.id]
            val fraction = episodeProgress(
                durationSeconds = episode.durationSeconds,
                local = positions[episode.id],
                serverSeconds = episode.serverProgressSeconds,
            )
            DownloadEpisodeRow(
                episode = episode,
                isLocal = isLocal,
                downloadable = episode.available || isLocal,
                playable = episode.available || isLocal,
                downloadProgress = progress,
                progress = fraction,
                onPlay = { onPlay(episode) },
                onDownloadClick = {
                    if (isLocal) onRemoveLocal(episode.id) else onDownload(episode)
                },
            )
        }

        if (episodesForSeason.isEmpty()) {
            item {
                Text(
                    text = "Keine Folgen in dieser Staffel.",
                    color = HikariTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SeriesHero(
    detail: DownloadSeriesDetail,
    resumeEpisode: DownloadDetailEpisode?,
    resumeFraction: Float,
    localCount: Int,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        FallbackArtwork(title = detail.title, showTitle = false)
        AsyncImage(
            model = detail.thumbnailUrl,
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
                .fillMaxHeight(0.7f)
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
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 40.dp, start = 10.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Text(
                text = detail.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 32.sp,
            )
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Heruntergeladen",
                    color = HikariAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (detail.year != null) {
                    Text("${detail.year}", color = HikariTextFaint, fontSize = 12.sp)
                }
                Text(
                    "${detail.episodes.size} Folgen",
                    color = HikariTextFaint,
                    fontSize = 12.sp,
                )
                Text(
                    "$localCount auf dem Gerät",
                    color = HikariTextFaint,
                    fontSize = 12.sp,
                )
            }
            HeroPlayButton(
                label = when {
                    resumeEpisode == null -> "Abspielen"
                    resumeFraction > 0f && resumeFraction < 0.95f ->
                        "Weiterschauen: F${resumeEpisode.episode ?: "-"}"
                    else -> "Abspielen"
                },
                enabled = resumeEpisode != null,
                onClick = onPlay,
            )
        }
    }
}

@Composable
private fun HeroPlayButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeasonDownloadButton(
    missingCount: Int,
    allLocal: Boolean,
    downloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = enabled && !downloading && missingCount > 0
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (active) HikariAmber.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            downloading -> CircularProgressIndicator(
                color = HikariAmber,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            allLocal -> Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = HikariTextFaint,
                modifier = Modifier.size(15.dp),
            )
            else -> Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = HikariAmber,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when {
                downloading -> "Lädt…"
                allLocal -> "Staffel komplett"
                missingCount > 0 -> "Staffel laden · $missingCount"
                else -> "Staffel laden"
            },
            color = when {
                downloading -> HikariAmber
                allLocal -> HikariTextFaint
                active -> HikariAmber
                else -> HikariTextMuted
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SeasonDropdown(
    seasons: List<Int>,
    selected: Int,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Staffel $selected",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Staffel wählen",
                tint = Color.White,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "Staffel $season",
                            color = if (season == selected) HikariAmber else HikariText,
                        )
                    },
                    onClick = {
                        onChange(season)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadEpisodeRow(
    episode: DownloadDetailEpisode,
    isLocal: Boolean,
    downloadable: Boolean,
    playable: Boolean,
    downloadProgress: Float?,
    progress: Float,
    onPlay: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = playable, onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray),
        ) {
            FallbackArtwork(title = episode.title)
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (progress > 0f) {
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                text = EpisodeTitles.listTitle(episode.episode, episode.title),
                color = if (isLocal) HikariText else HikariTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Text(
                text = episodeMeta(
                    isLocal = isLocal,
                    available = episode.available,
                    sizeBytes = episode.sizeBytes,
                    durationSeconds = episode.durationSeconds,
                ),
                color = if (isLocal) HikariAmber else HikariTextFaint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!episode.description.isNullOrBlank()) {
                Text(
                    text = episode.description,
                    color = HikariTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (downloadable) {
            LocalDownloadIcon(
                state = when {
                    isLocal -> LocalState.Local
                    downloadProgress != null -> LocalState.Downloading
                    else -> LocalState.NotLocal
                },
                progress = downloadProgress,
                onClick = onDownloadClick,
                itemTitle = episode.title,
            )
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(HikariDanger.copy(alpha = 0.12f))
            .border(0.5.dp, HikariDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = HikariDanger, fontSize = 12.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Meldung schließen", tint = HikariDanger, modifier = Modifier.size(16.dp))
        }
    }
}

/** Lokale Position hat Vorrang — sie kann frischer sein als der Server-Stand. */
private fun progressSeconds(local: PlaybackPositionEntity?, serverSeconds: Float?): Float =
    if (local != null && local.positionMs > 0L) local.positionMs / 1000f
    else serverSeconds ?: 0f

private fun episodeProgress(
    durationSeconds: Int,
    local: PlaybackPositionEntity?,
    serverSeconds: Float?,
): Float {
    if (durationSeconds <= 0) return 0f
    return (progressSeconds(local, serverSeconds) / durationSeconds).coerceIn(0f, 1f)
}

private fun episodeMeta(
    isLocal: Boolean,
    available: Boolean,
    sizeBytes: Long,
    durationSeconds: Int,
): String {
    val size = if (sizeBytes > 0) "${formatBytes(sizeBytes)} · " else ""
    val duration = if (durationSeconds > 0) formatDuration(durationSeconds) else ""
    val status = when {
        isLocal -> "✓ Auf dem Gerät"
        available -> "Nicht auf dem Gerät"
        else -> "Nicht verfügbar"
    }
    return "$status · $size$duration".trimEnd(' ', '·')
}
