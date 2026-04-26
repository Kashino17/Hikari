package com.hikari.app.ui.profile.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

enum class CategoryStyle { POSTERS, AVATARS }

/**
 * Big tappable card on the Downloads main view. Three "stacked" preview tiles
 * left (fanned posters for Series/Movies, overlapping avatar circles for
 * Channels), title + meta + amber "ÖFFNEN"-pill in the middle, chevron right.
 */
@Composable
fun CategoryCard(
    title: String,
    meta: String,
    style: CategoryStyle,
    coverUrls: List<String?>,
    glowColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seedFallbacks: List<String> = emptyList(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurface)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        // Subtle radial glow background using Canvas brush
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.18f), Color.Transparent),
                        radius = 480f,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (style) {
                CategoryStyle.POSTERS -> PosterStack(coverUrls.take(3), seedFallbacks.take(3))
                CategoryStyle.AVATARS -> AvatarStack(coverUrls.take(3), seedFallbacks.take(3))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = HikariText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    color = HikariTextFaint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(HikariAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        "ÖFFNEN",
                        color = HikariAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = HikariBorder.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PosterStack(urls: List<String?>, seeds: List<String>) {
    Box(modifier = Modifier.size(width = 96.dp, height = 84.dp)) {
        // 3 fanned posters: left (rotated -8°), center (no rotation, on top), right (rotated +8°)
        val tiles = listOf(
            PosterTile(0, 6, -8f),
            PosterTile(24, 2, 0f),
            PosterTile(48, 6, 8f),
        )
        urls.forEachIndexed { idx, url ->
            val tile = tiles.getOrNull(idx) ?: return@forEachIndexed
            val seed = seeds.getOrNull(idx) ?: idx.toString()
            Box(
                modifier = Modifier
                    .padding(start = tile.startDp.dp, top = tile.topDp.dp)
                    .size(width = 50.dp, height = 75.dp)
                    .rotate(tile.rotation)
                    .clip(RoundedCornerShape(6.dp))
                    .background(seededGradient(seed)),
            ) {
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarStack(urls: List<String?>, seeds: List<String>) {
    Box(modifier = Modifier.size(width = 96.dp, height = 64.dp)) {
        val tiles = listOf(0, 28, 56)
        urls.forEachIndexed { idx, url ->
            val left = tiles.getOrNull(idx) ?: return@forEachIndexed
            val seed = seeds.getOrNull(idx) ?: idx.toString()
            Box(
                modifier = Modifier
                    .padding(start = left.dp, top = 9.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(seededGradient(seed))
                    .border(2.5.dp, HikariSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!url.isNullOrBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        seed.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

private data class PosterTile(val startDp: Int, val topDp: Int, val rotation: Float)

private fun seededGradient(seed: String): Brush {
    val palette = listOf(
        Color(0xFF7c2d12) to Color(0xFFFBBF24),
        Color(0xFF0f172a) to Color(0xFF3b82f6),
        Color(0xFF166534) to Color(0xFF22d3ee),
        Color(0xFF3b0764) to Color(0xFFc084fc),
        Color(0xFF831843) to Color(0xFFf472b6),
        Color(0xFF374151) to Color(0xFF6b7280),
    )
    val (a, b) = palette[(seed.hashCode() and 0x7fffffff) % palette.size]
    return Brush.linearGradient(listOf(a, b))
}
