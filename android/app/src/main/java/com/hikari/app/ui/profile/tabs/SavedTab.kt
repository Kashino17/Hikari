package com.hikari.app.ui.profile.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.ui.profile.ProfileViewModel
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

@Composable
fun SavedTab(
    onPlay: (FeedItem) -> Unit,
    onPlayLibraryVideo: (LibraryVideoDto) -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val saved by vm.saved.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()

    if (saved.isEmpty() && continueWatching.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Noch nichts gespeichert.",
                color = HikariTextFaint,
                fontSize = 13.sp,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (continueWatching.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Column {
                    SectionLabel("WEITERSCHAUEN")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(continueWatching, key = { it.id }) { v ->
                            ContinueCard(video = v, onClick = { onPlayLibraryVideo(v) })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("GESPEICHERT · ${saved.size}")
                }
            }
        }
        items(saved, key = { it.videoId }) { item ->
            SavedCell(item = item, onClick = { onPlay(item) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = HikariTextFaint,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun ContinueCard(video: LibraryVideoDto, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurfaceHigh),
        ) {
            if (!video.thumbnail_url.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnail_url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Progress bar at bottom
            val progress = (video.progress_seconds ?: 0f) / video.duration_seconds.toFloat()
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(HikariAmber),
                )
            }
        }
        Text(
            text = video.title,
            color = HikariText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (!video.channelTitle.isNullOrBlank()) {
            Text(
                text = video.channelTitle,
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

@Composable
private fun SavedCell(item: FeedItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(HikariSurface)
            .clickable(onClick = onClick),
    ) {
        if (!item.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.thumbnailUrl,
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        startY = 80f,
                    ),
                ),
        )
        Text(
            formatDuration(item.durationSeconds),
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(
            item.title,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, end = 6.dp, bottom = 5.dp),
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
