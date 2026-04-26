package com.hikari.app.ui.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
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
                SeriesDetailContent(s.data, onBack, onPlayVideo)
        }
    }
}

@Composable
private fun SeriesDetailContent(
    data: SeriesDetailResponse,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    val seasons = remember(data.videos) {
        data.videos.mapNotNull { it.season }.distinct().sorted()
    }
    var selectedSeason by remember(seasons) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }

    // "Weiterschauen" target: first video with progress > 0 but not finished, else first video
    val resumeVideo = remember(data.videos) {
        data.videos.firstOrNull { v ->
            val p = v.progress_seconds ?: 0f
            p > 0f && p < v.duration_seconds.toFloat() * 0.95f
        } ?: data.videos.firstOrNull()
    }

    val episodesForSeason = remember(data.videos, selectedSeason, seasons) {
        if (seasons.isEmpty()) data.videos
        else data.videos.filter { (it.season ?: 1) == selectedSeason }
            .sortedBy { it.episode ?: 0 }
    }

    val year = remember(data.videos) {
        data.videos.firstOrNull()?.published_at?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = it }
            cal.get(Calendar.YEAR)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            HeroSection(
                data = data,
                year = year,
                resumeVideo = resumeVideo,
                onBack = onBack,
                onPlay = { resumeVideo?.let { onPlayVideo(it.id) } },
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

        item {
            if (seasons.size >= 2) {
                SeasonDropdown(
                    seasons = seasons,
                    selected = selectedSeason,
                    onChange = { selectedSeason = it },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    text = "Folgen",
                    color = HikariText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        items(episodesForSeason, key = { it.id }) { video ->
            EpisodeRow(video) { onPlayVideo(video.id) }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    data: SeriesDetailResponse,
    year: Int?,
    resumeVideo: LibraryVideoDto?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        AsyncImage(
            model = data.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
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
                    "98% Match",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (year != null) {
                    Text("$year", color = HikariTextFaint, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, HikariTextFaint, RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text("12+", color = HikariTextFaint, fontSize = 10.sp)
                }
                Text(
                    "${data.videos.size} Folgen",
                    color = HikariTextFaint,
                    fontSize = 12.sp,
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
    val isResume = (resumeVideo?.progress_seconds ?: 0f) > 0f
    val label = when {
        resumeVideo == null -> "Abspielen"
        isResume -> "Weiterschauen: F${resumeVideo.episode ?: "-"}"
        else -> "Abspielen"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
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

@Composable
private fun SeasonDropdown(
    seasons: List<Int>,
    selected: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
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

// ─── Episode row ─────────────────────────────────────────────────────────────

@Composable
private fun EpisodeRow(video: LibraryVideoDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            AsyncImage(
                model = video.thumbnail_url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
                text = video.episode?.let { "$it. ${video.title}" } ?: video.title,
                color = HikariText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Text(
                text = "${video.duration_seconds / 60} min",
                color = HikariTextFaint,
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
    }
}
