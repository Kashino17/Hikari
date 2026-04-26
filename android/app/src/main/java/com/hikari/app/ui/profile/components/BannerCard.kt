package com.hikari.app.ui.profile.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import kotlin.math.absoluteValue

/**
 * YouTube-style channel card: 21:9 banner on top with avatar circle overlapping
 * bottom-left, name + meta below. The avatar is z-index'd above the banner's
 * darkening gradient so it stays bright.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerCard(
    title: String,
    subtitle: String?,
    seed: String,
    avatarText: String?,
    bannerUrl: String?,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HikariSurface)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Banner band — 21:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(21f / 9f),
        ) {
            // Background: image if provided, else stable gradient from seed
            if (!bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(bannerGradient(seed)))
            }
            // Bottom darkening so text below feels rooted (cosmetic)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    )
                    .zIndex(1f),
            )
            // Avatar overlapping the banner-edge, z-index above the gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp)
                    .zIndex(2f)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(avatarFallbackGradient(seed))
                    .border(2.5.dp, HikariSurface, CircleShape)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp - 5.dp)
                        .clip(CircleShape)
                        .background(avatarFallbackGradient(seed)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            avatarText ?: title.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Spacer(Modifier.height(0.dp))
        }
        // Body — name + meta. Top padding accounts for avatar overlap (~24dp).
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 10.dp)) {
            Text(
                title,
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Stable banner gradient derived from a seed string — used when no banner image. */
private fun bannerGradient(seed: String): Brush {
    val palette = listOf(
        Triple(Color(0xFF7c2d12), Color(0xFFFBBF24), Color(0xFFfde68a)),
        Triple(Color(0xFF0f172a), Color(0xFF1e40af), Color(0xFF60a5fa)),
        Triple(Color(0xFF166534), Color(0xFF22d3ee), Color(0xFF34d399)),
        Triple(Color(0xFF3b0764), Color(0xFF7e22ce), Color(0xFFc084fc)),
        Triple(Color(0xFF831843), Color(0xFFbe185d), Color(0xFFf472b6)),
        Triple(Color(0xFF0f172a), Color(0xFF1e293b), Color(0xFF475569)),
    )
    val (a, b, c) = palette[(seed.hashCode().absoluteValue) % palette.size]
    return Brush.linearGradient(listOf(a, b, c))
}

private fun avatarFallbackGradient(seed: String): Brush {
    val palette = listOf(
        Color(0xFFB45309) to Color(0xFFFBBF24),
        Color(0xFF1E40AF) to Color(0xFF60A5FA),
        Color(0xFF166534) to Color(0xFF34D399),
        Color(0xFF7E22CE) to Color(0xFFC084FC),
        Color(0xFFBE185D) to Color(0xFFF472B6),
        Color(0xFF374151) to Color(0xFF6B7280),
    )
    val (a, b) = palette[(seed.hashCode().absoluteValue) % palette.size]
    return Brush.linearGradient(listOf(a, b))
}
