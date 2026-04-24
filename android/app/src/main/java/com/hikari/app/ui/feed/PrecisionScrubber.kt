package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Precision scrubber with:
 * - Thin 2dp track at rest, expands to 4dp + thumb on touch
 * - Horizontal drag = seek proportionally (full width = full duration)
 * - Vertical drag UP while scrubbing = precision mode (slower seek speed)
 *   speed = 1 / (1 + dy_up_px / 100), so 100px up = 0.5×, 300px up = 0.25×
 * - Time label with speed badge appears above while scrubbing, fades on release
 */
@Composable
fun PrecisionScrubber(
    positionMs: Long,
    durationMs: Long,
    onScrubStart: () -> Unit,
    onScrubUpdate: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    var dragStartPositionMs by remember { mutableLongStateOf(0L) }
    var dragDeltaX by remember { mutableStateOf(0f) }
    var dragDeltaY by remember { mutableStateOf(0f) }  // downward positive

    // Effective position during drag (accounts for precision mode)
    val previewPositionMs: Long = if (!scrubbing) {
        positionMs
    } else {
        val dyUp = (-dragDeltaY).coerceAtLeast(0f)          // upward distance in px
        val speed = 1f / (1f + dyUp / 100f)                  // 1× → 0.5× → …
        val rawDelta = (dragDeltaX / trackWidthPx) * durationMs.toFloat()
        val scaledDelta = (rawDelta * speed).toLong()
        (dragStartPositionMs + scaledDelta).coerceIn(0L, durationMs)
    }

    val fraction = (previewPositionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    val barHeight by animateDpAsState(if (scrubbing) 4.dp else 2.dp, label = "scrubberBarHeight")
    val thumbDp by animateDpAsState(if (scrubbing) 14.dp else 0.dp, label = "scrubberThumbSize")

    // Emit live preview positions while dragging
    LaunchedEffect(previewPositionMs, scrubbing) {
        if (scrubbing) onScrubUpdate(previewPositionMs)
    }

    // Outer Box = touch target (40dp tall), content aligned bottom so bar sits at very bottom
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onGloballyPositioned { coords ->
                trackWidthPx = coords.size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        scrubbing = true
                        dragStartPositionMs = positionMs
                        dragDeltaX = 0f
                        dragDeltaY = 0f
                        onScrubStart()
                    },
                    onDrag = { _, drag ->
                        dragDeltaX += drag.x
                        dragDeltaY += drag.y
                    },
                    onDragEnd = {
                        onScrubEnd(previewPositionMs)
                        scrubbing = false
                    },
                    onDragCancel = {
                        onScrubEnd(previewPositionMs)
                        scrubbing = false
                    },
                )
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        // Progress fill
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.92f)),
        )
        // Thumb circle (only visible while scrubbing)
        if (thumbDp > 0.dp) {
            val thumbOffsetDp = with(density) { (fraction * trackWidthPx).toDp() } - thumbDp / 2
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(thumbDp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .align(Alignment.BottomStart),
            )
        }

        // Time tooltip + speed badge (fades in on scrub, disappears on release)
        AnimatedVisibility(
            visible = scrubbing,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val dyUp = (-dragDeltaY).coerceAtLeast(0f)
            val speed = 1f / (1f + dyUp / 100f)
            val speedLabel = if (speed < 0.92f) " · %.1f×".format(speed) else ""

            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${formatTime(previewPositionMs)} / ${formatTime(durationMs)}$speedLabel",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
