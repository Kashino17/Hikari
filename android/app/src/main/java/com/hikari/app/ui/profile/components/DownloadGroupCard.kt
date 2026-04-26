package com.hikari.app.ui.profile.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

enum class GroupCoverShape { SQUARE, CIRCLE, POSTER }

/** Top row of an expandable group: cover + title + meta + chevron with count badge. */
@Composable
fun DownloadGroupCard(
    title: String,
    meta: String,
    coverShape: GroupCoverShape,
    coverUrl: String?,
    fallbackInitial: String?,
    expanded: Boolean,
    itemCount: Int,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onCheckChange: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) HikariAmber.copy(alpha = 0.06f) else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (selectionMode && onCheckChange != null) onCheckChange() else onToggle()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(coverShape, coverUrl, fallbackInitial)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta,
                    color = HikariTextFaint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (selectionMode && onCheckChange != null) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) HikariAmber else Color.Transparent)
                        .border(1.dp, if (selected) HikariAmber else HikariBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (itemCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(HikariSurfaceHigh)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                itemCount.toString(),
                                color = HikariAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    val rot = if (expanded) 90f else 0f
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = if (expanded) "Zuklappen" else "Aufklappen",
                        tint = HikariBorder.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp).rotate(rot),
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded && expandedContent != null) {
            expandedContent?.invoke()
        }
    }
}

@Composable
private fun CoverThumb(shape: GroupCoverShape, url: String?, fallbackInitial: String?) {
    when (shape) {
        GroupCoverShape.SQUARE -> Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurfaceHigh),
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    url, null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        GroupCoverShape.CIRCLE -> Box(
            modifier = Modifier.size(54.dp).clip(CircleShape).background(HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    url, null, modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    fallbackInitial ?: "?",
                    color = HikariText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        GroupCoverShape.POSTER -> Box(
            modifier = Modifier
                .width(42.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(HikariSurfaceHigh),
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    url, null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
fun EpisodeRow(
    title: String,
    meta: String,
    duration: String,
    thumbnailUrl: String?,
    onPlay: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onCheckChange: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) HikariAmber.copy(alpha = 0.06f) else Color.Transparent)
            .clickable {
                if (selectionMode && onCheckChange != null) onCheckChange() else onPlay()
            }
            .padding(start = 80.dp, top = 6.dp, bottom = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(74.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(HikariSurfaceHigh),
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    thumbnailUrl, null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                duration,
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                color = HikariTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) HikariAmber else Color.Transparent)
                    .border(1.dp, if (selected) HikariAmber else HikariBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(HikariAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Abspielen",
                    tint = HikariAmber,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
