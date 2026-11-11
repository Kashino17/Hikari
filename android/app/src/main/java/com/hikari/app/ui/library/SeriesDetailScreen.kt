package com.hikari.app.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.domain.library.SeriesResume
import com.hikari.app.ui.components.ResumeArtwork
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.components.resumeAwareThumbnail
import com.hikari.app.ui.profile.components.LocalDownloadIcon
import com.hikari.app.ui.profile.components.LocalState
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.util.Calendar

@Composable
fun SeriesDetailScreen(
    seriesId: String?,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.seriesState.collectAsState()
    val allSeries by viewModel.allSeries.collectAsState()
    val localIds by viewModel.localIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val bulkDownloading by viewModel.seriesBulkDownloading.collectAsState()
    val downloadError by viewModel.downloadActionError.collectAsState()

    var showMergePicker by remember { mutableStateOf(false) }
    var mergeTarget by remember { mutableStateOf<SeriesItemDto?>(null) }
    var mergeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seriesId) {
        seriesId?.let { viewModel.loadSeries(it) }
    }

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is SeriesUiState.Loading ->
                CircularProgressIndicator(
                    color = HikariAmber,
                    modifier = Modifier.align(Alignment.Center),
                )
            is SeriesUiState.Error ->
                Text(
                    text = s.message,
                    color = HikariTextMuted,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
            is SeriesUiState.Success ->
                SeriesDetailContent(
                    data = s.data,
                    localIds = localIds,
                    downloadProgress = downloadProgress,
                    bulkDownloading = bulkDownloading,
                    downloadError = downloadError,
                    onBack = onBack,
                    onPlayVideo = onPlayVideo,
                    onDownloadEpisode = { viewModel.downloadEpisode(s.data, it) },
                    onRemoveLocal = viewModel::removeLocalDownload,
                    onBulkDownload = { viewModel.downloadEpisodes(s.data, it) },
                    onDismissDownloadError = viewModel::clearDownloadActionError,
                    onMergeClick = {
                        viewModel.loadAllSeries()
                        mergeError = null
                        showMergePicker = true
                    },
                )
        }
    }

    // ── Merge: Ziel-Serie wählen ─────────────────────────────────────────
    if (showMergePicker) {
        val currentId = (state as? SeriesUiState.Success)?.data?.id
        AlertDialog(
            onDismissRequest = { showMergePicker = false },
            title = { Text("Mit anderer Serie zusammenführen") },
            text = {
                if (allSeries.isEmpty()) {
                    Text("Keine weiteren Serien vorhanden", color = HikariTextMuted)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(allSeries, key = { it.id }) { series ->
                            val isCurrent = series.id == currentId
                            Text(
                                text = series.title,
                                color = if (isCurrent) HikariTextFaint else HikariText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCurrent) {
                                        showMergePicker = false
                                        mergeTarget = series
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMergePicker = false }) { Text("Abbrechen") }
            },
        )
    }

    // ── Merge: Bestätigung ───────────────────────────────────────────────
    mergeTarget?.let { target ->
        val data = (state as? SeriesUiState.Success)?.data
        if (data != null) {
            AlertDialog(
                onDismissRequest = { mergeTarget = null; mergeError = null },
                title = { Text("Serien zusammenführen?") },
                text = {
                    Column {
                        Text("${data.videos.size} Folgen wandern zu ‚${target.title}'")
                        mergeError?.let {
                            Text(
                                text = it,
                                color = HikariDanger,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.mergeSeries(
                            sourceId = data.id,
                            targetId = target.id,
                            onSuccess = {
                                mergeTarget = null
                                mergeError = null
                                onBack()
                            },
                            onError = { mergeError = it },
                        )
                    }) { Text("Bestätigen") }
                },
                dismissButton = {
                    TextButton(onClick = { mergeTarget = null; mergeError = null }) {
                        Text("Abbrechen")
                    }
                },
            )
        }
    }
}

@Composable
private fun SeriesDetailContent(
    data: SeriesDetailResponse,
    localIds: Set<String>,
    downloadProgress: Map<String, Float>,
    bulkDownloading: Boolean,
    downloadError: String?,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onDownloadEpisode: (LibraryVideoDto) -> Unit,
    onRemoveLocal: (String) -> Unit,
    onBulkDownload: (List<LibraryVideoDto>) -> Unit,
    onDismissDownloadError: () -> Unit,
    onMergeClick: () -> Unit,
) {
    val seasons = remember(data.videos) {
        data.videos.mapNotNull { it.season }.distinct().sorted()
    }
    // Die Folge, bei der es weitergeht: die zuletzt geschaute, solange sie nicht
    // zu Ende ist — sonst die danach, auch über die Staffelgrenze.
    val resumeVideo = remember(data.videos) { SeriesResume.target(data.videos) }
    val resumeSeason = resumeVideo?.season ?: seasons.firstOrNull() ?: 1

    // Die Seite öffnet in der Staffel, in der man stehen geblieben ist — nicht
    // stur in Staffel 1 — aber am Anfang der Seite: wohin es geht, entscheidet
    // man selbst. Die Folge, bei der es weitergeht, ist nur markiert.
    var selectedSeason by remember(seasons, resumeVideo?.id) { mutableStateOf(resumeSeason) }

    val episodesForSeason = remember(data.videos, selectedSeason, seasons) {
        if (seasons.isEmpty()) data.videos
        else data.videos.filter { (it.season ?: 1) == selectedSeason }
            .sortedBy { it.episode ?: 0 }
    }

    val missingForSeason = remember(episodesForSeason, localIds) {
        episodesForSeason.filter { it.downloaded != 0 && !localIds.contains(it.id) }
    }
    val allLocal = episodesForSeason.isNotEmpty() &&
        episodesForSeason.all { localIds.contains(it.id) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            HeroSection(
                data = data,
                seasonCount = seasons.size,
                resumeVideo = resumeVideo,
                onBack = onBack,
                onPlay = { resumeVideo?.let { onPlayVideo(it.id) } },
                onMergeClick = onMergeClick,
            )
        }

        if (!data.description.isNullOrBlank()) {
            item {
                Text(
                    text = data.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        if (downloadError != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HikariDanger.copy(alpha = 0.12f))
                        .border(0.5.dp, HikariDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismissDownloadError)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(downloadError, color = HikariDanger, fontSize = 12.sp)
                }
            }
        }

        item {
            if (seasons.size >= 2) {
                SeasonChips(
                    seasons = seasons,
                    selected = selectedSeason,
                    resumeSeason = resumeSeason.takeIf { resumeVideo != null && SeriesResume.isStarted(resumeVideo) },
                    onChange = { selectedSeason = it },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = selectedSeason,
                    transitionSpec = {
                        // Höhere Staffel kommt von unten, niedrigere von oben.
                        val up = targetState > initialState
                        (slideInVertically(tween(220)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(160)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(120)))
                    },
                    label = "seasonTitle",
                ) { season ->
                    Text(
                        text = if (seasons.size >= 2) "Staffel $season" else "Folgen",
                        color = HikariText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (episodesForSeason.isNotEmpty()) {
                    LibrarySeasonDownloadButton(
                        missingCount = missingForSeason.size,
                        allLocal = allLocal,
                        downloading = bulkDownloading,
                        onClick = { onBulkDownload(missingForSeason) },
                    )
                }
            }
        }

        items(episodesForSeason, key = { it.id }) { video ->
            val isLocal = localIds.contains(video.id)
            val progress = downloadProgress[video.id]
            EpisodeRow(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(260),
                    fadeOutSpec = tween(140),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
                video = video,
                isNext = video.id == resumeVideo?.id,
                canPlay = video.downloaded != 0 || isLocal,
                downloadable = video.downloaded != 0 || isLocal,
                isLocal = isLocal,
                downloadProgress = progress,
                onDownloadClick = {
                    if (isLocal) onRemoveLocal(video.id) else onDownloadEpisode(video)
                },
                onClick = { if (video.downloaded != 0 || isLocal) onPlayVideo(video.id) },
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    data: SeriesDetailResponse,
    seasonCount: Int,
    resumeVideo: LibraryVideoDto?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onMergeClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        // Titel steht im Hero bereits groß unten — hier nur das Artwork.
        CoverArtwork(title = data.title, coverUrl = data.backdrop_url ?: data.thumbnail_url, showTitleFallback = false)
        // Top scrim
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
        // Bottom scrim
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

        // Serien-Aktionen (z. B. Merge) — gleiche Optik wie der Zurück-Button.
        var menuOpen by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 10.dp),
        ) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Default.MoreVert, "Serien-Aktionen", tint = Color.White)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Mit anderer Serie zusammenführen…") },
                    onClick = {
                        menuOpen = false
                        onMergeClick()
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Text(
                text = data.title,
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
                    text = buildString {
                        if (seasonCount > 1) append("$seasonCount Staffeln · ")
                        append(if (data.videos.size == 1) "1 Folge" else "${data.videos.size} Folgen")
                    },
                    color = HikariTextMuted,
                    fontSize = 13.sp,
                )
            }
            HeroPlayButton(
                resumeVideo = resumeVideo,
                onClick = onPlay,
            )
        }
    }
}

@Composable
private fun HeroPlayButton(
    resumeVideo: LibraryVideoDto?,
    onClick: () -> Unit,
) {
    // Der Knopf nennt die Folge beim Namen — man sieht vor dem Tippen, wo man landet.
    val tag = resumeVideo?.let { episodeTag(it.season, it.episode) }.orEmpty()
    val verb = if (resumeVideo != null && SeriesResume.isStarted(resumeVideo) && !SeriesResume.isFinished(resumeVideo)) {
        "Weiterschauen"
    } else {
        "Abspielen"
    }
    val label = if (tag.isBlank()) verb else "$verb · $tag"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(enabled = resumeVideo != null, onClick = onClick)
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

// ─── Season dropdown ─────────────────────────────────────────────────────────

/**
 * Staffeln als antippbare Reihe statt Aufklappliste: ein Tipp wechselt, und
 * ein Punkt zeigt, in welcher Staffel man stehen geblieben ist.
 */
@Composable
private fun SeasonChips(
    seasons: List<Int>,
    selected: Int,
    resumeSeason: Int?,
    onChange: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        items(seasons, key = { it }) { season ->
            val active = season == selected
            val fill by animateColorAsState(
                if (active) Color.White else Color.White.copy(alpha = 0.08f),
                tween(220),
                label = "seasonChip",
            )
            val textColor by animateColorAsState(if (active) Color.Black else HikariText, tween(220), label = "seasonChipText")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .clickable { onChange(season) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Staffel $season",
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
                if (season == resumeSeason) {
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(HikariAmber))
                }
            }
        }
    }
}

// ─── Episode row ─────────────────────────────────────────────────────────────

@Composable
private fun EpisodeRow(
    video: LibraryVideoDto,
    isNext: Boolean,
    canPlay: Boolean,
    downloadable: Boolean,
    isLocal: Boolean,
    downloadProgress: Float?,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            // Die Folge, bei der es weitergeht, liegt auf einer leicht getönten Fläche.
            .background(if (isNext) HikariAmber.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
        ) {
            // Angefangene Folge: Bild der Stelle, an der man aufgehört hat.
            ResumeArtwork(video = video, modifier = Modifier.fillMaxSize())
            val progress = video.progress_seconds?.let {
                it / video.duration_seconds.toFloat()
            }
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                text = com.hikari.app.ui.library.EpisodeTitles.listTitle(video.episode, video.title),
                color = if (canPlay) HikariText else HikariTextFaint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Text(
                // Eine Folge ohne Datei als solche ausweisen, statt den Nutzer
                // ins Leere tippen zu lassen.
                text = when {
                    !canPlay -> "Nicht verfügbar"
                    isNext && SeriesResume.isStarted(video) && !SeriesResume.isFinished(video) ->
                        "Hier weiter · noch ${remainingLabel(video)}"
                    isNext -> "Als Nächstes · ${video.duration_seconds / 60} min"
                    isLocal -> "Auf dem Gerät · ${video.duration_seconds / 60} min"
                    SeriesResume.isFinished(video) -> "Gesehen · ${video.duration_seconds / 60} min"
                    else -> "${video.duration_seconds / 60} min"
                },
                // Amber nur für die eine Folge, bei der es weitergeht. »Auf dem Gerät« steht bei
                // geladenen Staffeln in jeder Zeile — in der Akzentfarbe wurde daraus eine gelbe Wand.
                color = if (isNext) HikariAmber else HikariTextFaint,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!video.description.isNullOrBlank()) {
                Text(
                    text = video.description,
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
                itemTitle = video.title,
            )
        }
    }
}

/**
 * „Staffel laden“ im Bibliotheks-Serien-Detail — lädt alle fehlenden Folgen
 * der aktuellen Staffel nacheinander, genau wie auf der Download-Detailseite.
 */
@Composable
private fun LibrarySeasonDownloadButton(
    missingCount: Int,
    allLocal: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
) {
    val active = !downloading && missingCount > 0
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
            else -> Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = if (active) HikariAmber else HikariTextFaint,
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
            color = if (active || downloading) HikariAmber else HikariTextFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
