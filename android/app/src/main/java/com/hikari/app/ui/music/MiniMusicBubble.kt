package com.hikari.app.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MiniMusicBubbleViewModel @Inject constructor(
    val player: MusicPlayerController,
) : ViewModel()

/**
 * Schwebender Schnellzugriff auf die Wiedergabe, sichtbar außerhalb des
 * Musikbereichs.
 *
 * Erscheint nur, wenn beim Verlassen der Musik tatsächlich etwas lief —
 * pausierte Wiedergabe soll einen nicht durch die ganze App verfolgen.
 * Einmal sichtbar, bleibt die Blase auch beim Pausieren stehen, damit man
 * weiterhören kann; erst das Schließen-Kreuz oder ein Besuch im Musikbereich
 * lässt sie verschwinden.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniMusicBubble(
    /** true, solange eine Musik-Route offen ist — dort ist die Blase überflüssig. */
    inMusicSection: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniMusicBubbleViewModel = hiltViewModel(),
) {
    val controller = viewModel.player
    val song by controller.currentSong.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()

    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, inMusicSection, song) {
        when {
            song == null -> shown = false
            inMusicSection -> shown = false
            isPlaying -> shown = true // erst laufende Musik holt die Blase hervor
        }
    }

    AnimatedVisibility(
        visible = shown && song != null && !inMusicSection,
        enter = fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.55f), initialScale = 0.7f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.8f),
        modifier = modifier,
    ) {
        val current = song ?: return@AnimatedVisibility

        // Ruhiger Atem, solange gespielt wird — Stillstand signalisiert Pause.
        val transition = rememberInfiniteTransition(label = "bubble")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = if (isPlaying) 1.045f else 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "pulse",
        )
        val sweep by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
            label = "sweep",
        )

        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(54.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(HikariSurfaceHigh)
                    .combinedClickable(
                        onClick = { controller.toggle() },
                        onLongClick = onOpen,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = current.thumbnailUrl.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier.size(54.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                )
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "Musik pausieren" else "Musik fortsetzen",
                    tint = HikariText,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Umlaufender Bogen zeigt laufende Wiedergabe; im Ruhezustand hält
            // ein geschlossener Ring den Kreis auf hellen Bildern sichtbar.
            Canvas(Modifier.size(62.dp).scale(pulse)) {
                val inset = 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                if (isPlaying) {
                    drawArc(
                        color = HikariPrimary,
                        startAngle = sweep,
                        sweepAngle = 84f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                } else {
                    drawArc(
                        color = HikariPrimary.copy(alpha = 0.4f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            // Schließen erst im pausierten Zustand: währenddessen wäre der
            // Knopf nur eine Stolperfalle neben dem Pause-Ziel.
            if (!isPlaying) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(HikariBg)
                        .clickable { shown = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Schnellzugriff schließen",
                        tint = HikariText,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
