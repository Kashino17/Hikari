package com.hikari.app.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariSurface

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CinemaPosterCard(
    title: String,
    subtitle: String = "",
    thumbnailUrl: String?,
    tag: String? = null,
    matchScore: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    width: Dp = 120.dp,
    height: Dp = 175.dp,
    modifier: Modifier = Modifier,
) {
    var imageLoaded by remember(thumbnailUrl) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        color = HikariSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FallbackArtwork(title = title)

            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { imageLoaded = true },
                    onError = { imageLoaded = false },
                )
            }

            // Scrim
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        ),
                    ),
            )

            // Top-left Tag
            if (!tag.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = tag,
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            } else if (matchScore != null) {
                Surface(
                    color = HikariAmber,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = "$matchScore%",
                        color = Color.Black,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            // Title & Subtitle
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
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
