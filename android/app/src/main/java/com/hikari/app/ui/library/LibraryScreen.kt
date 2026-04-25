package com.hikari.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder

@Composable
fun LibraryScreen(
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is LibraryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is LibraryUiState.Error -> {
                Text(
                    text = s.message,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp)
                )
            }
            is LibraryUiState.Success -> {
                LibraryContent(
                    data = s.data,
                    onOpenSeries = onOpenSeries,
                    onOpenChannel = onOpenChannel,
                    onPlayVideo = onPlayVideo
                )
            }
        }
    }
}

@Composable
fun LibraryContent(
    data: com.hikari.app.data.api.dto.LibraryResponse,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Hero Section
        item {
            HeroSection(data.recentlyAdded.firstOrNull(), onPlayVideo)
        }

        // Continue Watching
        val continueWatching = data.recentlyAdded.filter { (it.progress_seconds ?: 0f) > 0f }
        if (continueWatching.isNotEmpty()) {
            item {
                SectionRow("Weitersehen") {
                    items(continueWatching) { video ->
                        VideoCard(video) { onPlayVideo(video.id) }
                    }
                }
            }
        }

        // Series
        if (data.series.isNotEmpty()) {
            item {
                SectionRow("Serien") {
                    items(data.series) { series ->
                        SeriesCard(series) { onOpenSeries(series.id) }
                    }
                }
            }
        }

        // Channels
        if (data.channels.isNotEmpty()) {
            item {
                SectionRow("Kanäle") {
                    items(data.channels) { channel ->
                        ChannelCircle(channel) { onOpenChannel(channel.id) }
                    }
                }
            }
        }

        // Recently Added
        item {
            SectionRow("Neu hinzugefügt") {
                items(data.recentlyAdded) { video ->
                    VideoCard(video) { onPlayVideo(video.id) }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun HeroSection(video: LibraryVideoDto?, onPlay: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        if (video != null) {
            AsyncImage(
                model = video.thumbnail_url,
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
                            startY = 400f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = video.channelTitle ?: "",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 28.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onPlay(video.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Abspielen", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Infos", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionRow(title: String, content: LazyListScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun VideoCard(video: LibraryVideoDto, onClick: () -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray)) {
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
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SeriesCard(series: SeriesDto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(2/3f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.DarkGray)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = series.thumbnail_url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Text(
            text = series.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
    }
}

@Composable
fun ChannelCircle(channel: ChannelDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(80.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = channel.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(70.dp).clip(CircleShape).background(HikariBorder),
            contentScale = ContentScale.Crop
        )
        Text(
            text = channel.title,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
