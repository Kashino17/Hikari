package com.hikari.app.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariSurface

@Composable
fun NetflixTopTenSection(
    title: String,
    items: List<TopTenItem>,
    onItemClick: (TopTenItem) -> Unit,
    onItemLongClick: (TopTenItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
                Surface(
                    color = HikariAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.7.dp, HikariAmber.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = "TOP 10",
                        color = HikariAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = "Letzte 30 Tage",
                color = Color(0xFF8E8E93),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { "top10-${it.id}-${it.rank}" }) { item ->
                NetflixTopTenCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetflixTopTenCard(
    item: TopTenItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var imageLoaded by remember(item.thumbnailUrl) { mutableStateOf(false) }

    Row(
        modifier = modifier.height(178.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // ── Ikonische Netflix-Ziffer mit Outlined Stroke ─────────────────────
        Box(
            modifier = Modifier
                .width(if (item.rank == 10) 64.dp else 46.dp)
                .height(178.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            // Outline Stroke
            Text(
                text = "${item.rank}",
                color = Color(0xFF595959),
                fontSize = if (item.rank == 10) 80.sp else 94.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(drawStyle = Stroke(width = 7f)),
                modifier = Modifier.offset(x = (-2).dp, y = 10.dp),
            )
            // Inner Fill
            Text(
                text = "${item.rank}",
                color = Color(0xFF0F0E0D),
                fontSize = if (item.rank == 10) 80.sp else 94.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.offset(x = (-2).dp, y = 10.dp),
            )
        }

        // ── 2:3 Kinoplakat-Poster ───────────────────────────────────────────
        Surface(
            modifier = Modifier
                .width(116.dp)
                .height(174.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            color = HikariSurface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Fallback Artwork
                FallbackArtwork(title = item.title)

                // Async Poster Image
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { imageLoaded = true },
                        onError = { imageLoaded = false },
                    )
                }

                // Scrim Overlay am unteren Rand
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                            ),
                        ),
                )

                // Top Badge: "TOP 10" oder Match %
                Surface(
                    color = HikariAmber,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = "TOP 10",
                        color = Color.Black,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                // Bottom Content: Titel & Info
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            color = Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
