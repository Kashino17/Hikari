package com.hikari.app.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariSurfaceHigh
import kotlin.random.Random

sealed interface ShuffleCandidate {
    val id: String
    val title: String
    val description: String?
    val thumbnailUrl: String?

    data class Series(val series: SeriesDto) : ShuffleCandidate {
        override val id: String get() = series.id
        override val title: String get() = series.title
        override val description: String? get() = series.description
        override val thumbnailUrl: String? get() = series.thumbnail_url
    }

    data class Video(val video: LibraryVideoDto) : ShuffleCandidate {
        override val id: String get() = video.id
        override val title: String get() = video.title
        override val description: String? get() = video.description
        override val thumbnailUrl: String? get() = video.thumbnail_url
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartShuffleSheet(
    data: LibraryResponse,
    onOpenSeries: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pool = remember(data) {
        val list = mutableListOf<ShuffleCandidate>()
        data.series.forEach { list.add(ShuffleCandidate.Series(it)) }
        data.recentlyAdded.forEach { list.add(ShuffleCandidate.Video(it)) }
        list
    }

    var seedIndex by remember { mutableIntStateOf(if (pool.isNotEmpty()) Random.nextInt(pool.size) else 0) }

    if (pool.isEmpty()) {
        onDismiss()
        return
    }

    val selected = pool[seedIndex.coerceIn(pool.indices)]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141316),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "🎲",
                        fontSize = 22.sp,
                    )
                    Text(
                        text = "Zufalls-Banger",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = Color(0xFFA1A1AA),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1D22)),
            ) {
                FallbackArtwork(title = selected.title)
                if (!selected.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = selected.thumbnailUrl,
                        contentDescription = selected.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Surface(
                    color = HikariAmber,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = if (selected is ShuffleCandidate.Series) "SERIE" else "FILM & VIDEO",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Titel & Details
            Text(
                text = selected.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!selected.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = selected.description ?: "",
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Reroll Button
                OutlinedButton(
                    onClick = {
                        if (pool.size > 1) {
                            var next = Random.nextInt(pool.size)
                            while (next == seedIndex) next = Random.nextInt(pool.size)
                            seedIndex = next
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Würfeln", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                // Play / Open Button
                Button(
                    onClick = {
                        onDismiss()
                        when (selected) {
                            is ShuffleCandidate.Series -> onOpenSeries(selected.id)
                            is ShuffleCandidate.Video -> onPlayVideo(
                                selected.video.id,
                                selected.video.title,
                                selected.video.channelTitle ?: "",
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HikariAmber,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Jetzt ansehen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
