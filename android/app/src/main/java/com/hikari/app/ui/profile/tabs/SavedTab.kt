package com.hikari.app.ui.profile.tabs

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.profile.ProfileViewModel
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun SavedTab(
    onPlay: (FeedItem) -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val saved by vm.saved.collectAsState()

    if (saved.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(HikariSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Keine gespeicherten Videos",
                    color = HikariText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tippe im Feed auf das Lesezeichen, um Videos für deine persönliche Mediathek zu sichern.",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(saved, key = { it.videoId }) { item ->
            SavedVideoCard(item = item, onClick = { onPlay(item) })
        }
    }
}

@Composable
private fun SavedVideoCard(item: FeedItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        // Thumbnail with 16:10 cinematic ratio + duration badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(HikariSurface),
        ) {
            if (!item.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                FallbackArtwork(title = item.title)
            }

            // Subtle gradient overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f),
                            ),
                        ),
                    ),
            )

            // Duration badge top-right
            Text(
                formatDuration(item.durationSeconds),
                color = Color.White,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )

            // Play indicator button in bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(HikariAmber),
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

        // Title and channel info below thumbnail
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Text(
                text = item.title,
                color = HikariText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.channelTitle.ifBlank { "Hikari Video" },
                color = HikariTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
