package com.hikari.app.ui.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    controller: MusicPlayerController,
    onOpen: () -> Unit,
) {
    val song by controller.currentSong.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val isBuffering by controller.isBuffering.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val current = song ?: return

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipeX = remember { Animatable(0f) }
    val maxShift = with(LocalDensity.current) { 60.dp.toPx() }
    val shimmer = muShimmerAlpha()

    MuAppear(0) {
        Column(
            Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .graphicsLayer { translationX = swipeX.value }
                .muPressable(onClick = onOpen)
                .clip(RoundedCornerShape(14.dp))
                .background(HikariSurfaceHigh.copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                // Wisch-Geste wie bei YouTube Music: links = nächster Song,
                // rechts = vorheriger. Die Bar folgt gedämpft dem Finger.
                .pointerInput(Unit) {
                    var total = 0f
                    var lastDelta = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f; lastDelta = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            total += amount
                            lastDelta = amount
                            val shifted = (total * 0.5f).coerceIn(-maxShift, maxShift)
                            scope.launch { swipeX.snapTo(shifted) }
                        },
                        onDragEnd = {
                            val threshold = size.width * 0.4f
                            val fling = abs(lastDelta) > 40f
                            when {
                                total < -threshold || (fling && lastDelta < 0f) -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.next()
                                }
                                total > threshold || (fling && lastDelta > 0f) -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.previous()
                                }
                            }
                            scope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 520f)) }
                        },
                        onDragCancel = {
                            scope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 520f)) }
                        },
                    )
                },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = current.thumbnailUrl.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HikariText,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(current.uploader, fontSize = 11.sp, color = HikariTextMuted, maxLines = 1)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .muPressable { controller.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = HikariPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Crossfade(isPlaying, animationSpec = tween(160), label = "miniPlay") { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "Pause" else "Abspielen",
                                tint = HikariText,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
                MuIconButton(Icons.Default.SkipNext, "Weiter") { controller.next() }
            }

            // Schlanke Fortschrittslinie an der Unterkante — pulsiert beim Puffern.
            val progress = if (duration > 0) position.toFloat() / duration else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.07f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.horizontalGradient(listOf(HikariPrimary.copy(alpha = 0.75f), HikariPrimary)))
                        .graphicsLayer { alpha = if (isBuffering) shimmer else 1f },
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
