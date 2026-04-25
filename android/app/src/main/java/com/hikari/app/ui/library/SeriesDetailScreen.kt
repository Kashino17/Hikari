package com.hikari.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariTextFaint

@Composable
fun SeriesDetailScreen(
    seriesId: String?,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.seriesState.collectAsState()

    LaunchedEffect(seriesId) {
        seriesId?.let { viewModel.loadSeries(it) }
    }

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is SeriesUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is SeriesUiState.Error -> {
                Text(
                    text = s.message,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp)
                )
            }
            is SeriesUiState.Success -> {
                SeriesDetailContent(s.data, onBack, onPlayVideo)
            }
        }
    }
}

@Composable
fun SeriesDetailContent(
    data: SeriesDetailResponse,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SeriesHeader(data, onBack, onPlayVideo)
        }

        item {
            Text(
                text = "Folgen",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp)
            )
        }

        items(data.videos) { video ->
            EpisodeItem(video) { onPlayVideo(video.id) }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
fun SeriesHeader(data: SeriesDetailResponse, onBack: () -> Unit, onPlay: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16/10f)) {
        AsyncImage(
            model = data.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, HikariBg),
                        startY = 200f
                    )
                )
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 40.dp, start = 10.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = data.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("98% Match", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("2024", color = HikariTextFaint, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .border(1.dp, HikariTextFaint, RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("12+", color = HikariTextFaint, fontSize = 10.sp)
                }
                Text("${data.videos.size} Folgen", color = HikariTextFaint, fontSize = 12.sp)
            }
            Button(
                onClick = { data.videos.firstOrNull()?.id?.let { onPlay(it) } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Abspielen", fontWeight = FontWeight.Bold)
            }
            Text(
                text = data.description ?: "",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 15.dp)
            )
        }
    }
}

@Composable
fun EpisodeItem(video: LibraryVideoDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16/9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = video.thumbnail_url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (video.progress_seconds != null) {
                val progress = video.progress_seconds / video.duration_seconds.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                        .align(Alignment.BottomStart)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${video.episode ?: ""}. ${video.title}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${video.duration_seconds / 60} min",
                color = HikariTextFaint,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

import androidx.compose.foundation.border
