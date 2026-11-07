package com.hikari.app.ui.music

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import coil.request.ImageRequest
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
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
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
    val isEnded by controller.isEnded.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val current = song ?: return

    // Wiedergabefehler auch außerhalb des Now-Playing-Screens sichtbar machen —
    // sonst wirkt ein fehlgeschlagener Songstart wie „nichts passiert".
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        controller.error.collect { msg ->
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipeX = remember { Animatable(0f) }
    val maxShift = with(LocalDensity.current) { 60.dp.toPx() }
    val shimmer = muShimmerAlpha()

    // Schallplatte: dreht sich nur, solange Musik läuft — Pause hält sie an Ort und Stelle an.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            spin.animateTo(spin.value + 360f, tween(12_000, easing = LinearEasing))
        }
    }
    // Der unscharfe Cover-Hintergrund treibt langsam hin und her.
    val drift = remember { Animatable(-1f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            drift.animateTo(if (drift.value < 0f) 1f else -1f, tween(9_000, easing = FastOutSlowInEasing))
        }
    }
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(32.dp)
    val ringTrack = Color.White.copy(alpha = 0.14f)

    MuAppear(0) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .graphicsLayer { translationX = swipeX.value }
                .muPressable(onClick = onOpen)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(Color(0xFF2A2420))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                // Wisch-Geste wie bei YouTube Music: links = nächster Song,
                // rechts = vorheriger. Die Insel folgt gedämpft dem Finger.
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
            // Hintergrund: das Cover als winziges Bild hochskaliert = weiche
            // Unschärfe ohne Blur-Shader, dazu eine dunkle Lasur für Lesbarkeit.
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(current.thumbnailUrl.ifEmpty { null })
                    .size(28, 16)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                        translationX = drift.value * size.width * 0.08f
                    },
            )
            Box(Modifier.matchParentSize().background(Color(0xFF121216).copy(alpha = 0.52f)))

            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 5.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover als drehende Platte, Fortschritt als Ring drumherum.
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        val stroke = 2.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(ringTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        drawArc(
                            Color.White, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                            alpha = if (isBuffering) shimmer else 1f,
                        )
                    }
                    AsyncImage(
                        model = current.thumbnailUrl.ifEmpty { null },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer { rotationZ = spin.value }
                            .clip(CircleShape)
                            .drawWithContent {
                                drawContent()
                                // Rillen und Mittelloch: aus dem Cover wird eine Platte.
                                val c = center
                                var r = size.minDimension / 2f - 3.dp.toPx()
                                while (r > 8.dp.toPx()) {
                                    drawCircle(Color.Black.copy(alpha = 0.13f), r, c, style = Stroke(0.6.dp.toPx()))
                                    r -= 3.dp.toPx()
                                }
                                drawCircle(Color.White.copy(alpha = 0.25f), 4.5.dp.toPx(), c)
                                drawCircle(Color(0xFF1F1F22), 3.dp.toPx(), c)
                            },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    val time = if (duration > 0) " · ${formatDurationMs(position)} / ${formatDurationMs(duration)}" else ""
                    Text(
                        current.uploader + time,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .muPressable { controller.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Crossfade(isPlaying, animationSpec = tween(160), label = "miniPlay") { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else if (isEnded) Icons.Outlined.Replay else Icons.Default.PlayArrow,
                                if (playing) "Pause" else if (isEnded) "Wiederholen" else "Abspielen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                MuIconButton(Icons.Default.SkipNext, "Weiter", tint = Color.White.copy(alpha = 0.9f)) { controller.next() }
            }
        }
    }
}
