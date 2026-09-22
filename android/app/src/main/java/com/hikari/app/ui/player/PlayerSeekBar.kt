package com.hikari.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariAmber
import kotlin.math.abs

/**
 * Netflix-artige Fortschrittsleiste für Serien und Filme.
 *
 * Bewusst *unempfindlich*:
 *  - Ein Tipp auf die Leiste springt NICHT — nur ein echtes Ziehen spult.
 *  - Ziehen beginnt erst nach 12 dp Bewegung (Touch-Slop); bis dahin läuft
 *    das Video weiter, nichts pausiert.
 *  - Finger deutlich oberhalb/unterhalb der Leiste (> 56 dp) → Feinmodus mit
 *    einem Viertel der Geschwindigkeit, im Tooltip als „fein" markiert.
 *  - Tooltip zeigt Zielzeit und Versatz (+1:23 / −0:40).
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onScrubStart: () -> Unit,
    onScrubUpdate: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    var startMs by remember { mutableLongStateOf(0L) }
    var deltaX by remember { mutableFloatStateOf(0f) }
    var fine by remember { mutableStateOf(false) }

    val positionState = rememberUpdatedState(positionMs)
    val durationState = rememberUpdatedState(durationMs)
    val onStart = rememberUpdatedState(onScrubStart)
    val onUpdate = rememberUpdatedState(onScrubUpdate)
    val onEnd = rememberUpdatedState(onScrubEnd)

    val slopPx = with(density) { 12.dp.toPx() }
    val finePx = with(density) { 56.dp.toPx() }

    fun target(): Long = scrubTarget(startMs, durationState.value, deltaX, trackWidthPx, fine)

    val shownMs = if (scrubbing) target() else positionMs
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (shownMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val barHeight by animateDpAsState(if (scrubbing) 5.dp else 3.dp, label = "seekBarHeight")
    val thumb by animateDpAsState(if (scrubbing) 18.dp else 12.dp, label = "seekThumb")

    LaunchedEffect(shownMs, scrubbing) {
        if (scrubbing) onUpdate.value(shownMs)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip über der Leiste
        Box(modifier = Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.BottomStart) {
            androidx.compose.animation.AnimatedVisibility(
                visible = scrubbing,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(100)),
            ) {
                val offsetMs = shownMs - startMs
                val sign = if (offsetMs < 0) "−" else "+"
                val bubbleWidth = 132.dp
                val x = with(density) { (fraction * trackWidthPx).toDp() } - bubbleWidth / 2
                val maxX = with(density) { trackWidthPx.toDp() } - bubbleWidth
                Row(
                    modifier = Modifier
                        .offset(x = x.coerceIn(0.dp, maxX.coerceAtLeast(0.dp)))
                        .width(bubbleWidth)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatClock(shownMs),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$sign${formatClock(abs(offsetMs))}" + if (fine) " · fein" else "",
                        color = if (fine) HikariAmber else Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Leiste (Touch-Ziel 44 dp hoch)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .onGloballyPositioned { trackWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var dx = 0f
                        var dy = 0f
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val moved = change.positionChange()
                            dx += moved.x
                            dy += moved.y
                            change.consume()
                            if (!started && abs(dx) > slopPx) {
                                started = true
                                scrubbing = true
                                startMs = positionState.value
                                deltaX = 0f
                                fine = false
                                onStart.value()
                            }
                            if (started) {
                                deltaX = dx
                                fine = abs(dy) > finePx
                            }
                            if (!change.pressed) break
                        }
                        if (started) {
                            val finalMs = target()
                            scrubbing = false
                            onEnd.value(finalMs)
                        }
                        // Ein bloßer Tipp auf die Leiste tut nichts — kein Sprung.
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Box(
                Modifier.fillMaxWidth(bufferedFraction).height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f)),
            )
            Box(
                Modifier.fillMaxWidth(fraction).height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
            )
            val thumbOffset = with(density) { (fraction * trackWidthPx).toDp() } - thumb / 2
            Box(
                Modifier
                    .offset(x = thumbOffset.coerceAtLeast(0.dp))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }

        // Zeiten
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatClock(shownMs),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatRemaining(shownMs, durationMs),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}
