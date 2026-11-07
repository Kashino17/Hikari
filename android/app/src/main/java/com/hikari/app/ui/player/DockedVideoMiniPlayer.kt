package com.hikari.app.ui.player

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hikari.app.player.VideoDockManager
import com.hikari.app.ui.theme.HikariAmber
import kotlinx.coroutines.delay

private val DockBg = Color(0xFF141417)

/**
 * Angedockter Video-Player im "Ambilight"-Stil: das Bild läuft rechts weich
 * in die Leiste aus, und sein Licht strahlt als unscharfer Schein in die
 * Fläche. Nach dem Zurückgehen steht dort das zuletzt gesehene Bild (auch
 * pausiert), nicht Schwarz und nicht das Vorschaubild.
 *
 * Sparsam: die Unschärfe entsteht, indem ein 24×14-Pixel-Abbild des Bildes
 * hochskaliert wird (kein Blur-Shader); es wird alle 4 s aus dem laufenden
 * Bild erneuert, und nur solange das Video läuft.
 */
@Composable
fun DockedVideoMiniPlayer(
    dockManager: VideoDockManager,
    onExpand: (videoId: String, title: String, channel: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeVideo by dockManager.activeVideo.collectAsState()
    val isDocked by dockManager.isDocked.collectAsState()
    val isPlaying by dockManager.isPlaying.collectAsState()
    val lastFrame by dockManager.lastFrame.collectAsState()
    val video = activeVideo

    AnimatedVisibility(
        visible = isDocked && video != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        if (video == null) return@AnimatedVisibility
        val player = dockManager.getPlayer()
        val ctx = LocalContext.current
        val textureView = remember(player) { TextureView(ctx).apply { isOpaque = false } }

        // Ambilight-Quelle: winziges Abbild des aktuellen Bildes.
        var ambient by remember(video.videoId) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(lastFrame) {
            lastFrame?.let { ambient = Bitmap.createScaledBitmap(it, 24, 14, true) }
        }
        LaunchedEffect(isPlaying, player) {
            while (isPlaying && player != null) {
                delay(4000)
                if (textureView.isAvailable) {
                    textureView.getBitmap(24, 14)?.let { ambient = it }
                }
            }
        }
        // Beim Ausklappen/Schließen das Live-Bild als neues "letztes Bild" merken.
        DisposableEffect(textureView) {
            onDispose {
                if (textureView.isAvailable) {
                    textureView.getBitmap(320, 180)?.let { dockManager.setLastFrame(video.videoId, it) }
                }
            }
        }
        val ambientAlpha = remember { Animatable(0.45f) }
        LaunchedEffect(isPlaying) {
            while (isPlaying) {
                ambientAlpha.animateTo(0.75f, tween(2600, easing = FastOutSlowInEasing))
                ambientAlpha.animateTo(0.45f, tween(2600, easing = FastOutSlowInEasing))
            }
        }

        // Fortschritt und Restzeit: einmal pro Sekunde reicht.
        var positionMs by remember { mutableLongStateOf(0L) }
        var durationMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(player) {
            while (player != null) {
                positionMs = player.currentPosition
                durationMs = player.duration.coerceAtLeast(0L)
                delay(1000)
            }
        }
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val remainingMin = if (durationMs > 0) ((durationMs - positionMs) / 60_000L).toInt() else -1
        val subtitle = listOfNotNull(
            video.subtitle ?: video.channel.takeIf { it.isNotBlank() },
            when {
                remainingMin > 0 -> "noch $remainingMin Min"
                remainingMin == 0 -> "gleich vorbei"
                else -> null
            },
        ).joinToString(" · ")

        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(78.dp)
                .shadow(14.dp, shape)
                .clip(shape)
                .background(DockBg)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                .clickable {
                    dockManager.expand()
                    onExpand(video.videoId, video.title, video.channel)
                },
        ) {
            // 1) Lichtschein: hochskaliertes Mini-Abbild = weiche Unschärfe.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(190.dp)
                    .graphicsLayer {
                        alpha = ambientAlpha.value
                        scaleX = 1.5f
                        scaleY = 1.5f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
            ) {
                val amb = ambient
                if (amb != null) {
                    Image(
                        bitmap = amb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(video.thumbnailUrl).size(24, 14).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to DockBg.copy(alpha = 0f),
                            0.45f to DockBg.copy(alpha = 0.55f),
                            0.8f to DockBg,
                        ),
                    ),
            )

            // 2) Das Bild selbst, rechts weich ausgeblendet.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(139.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.7f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                val frame = lastFrame
                if (frame != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (player != null) {
                    // Durchsichtig, bis ein Bild ankommt — darunter bleibt das letzte Bild stehen.
                    AndroidView(factory = { textureView }, modifier = Modifier.fillMaxSize())
                    DisposableEffect(player, textureView) {
                        player.setVideoTextureView(textureView)
                        onDispose { player.clearVideoTextureView(textureView) }
                    }
                }
            }

            // 3) Text und Steuerung.
            Row(
                Modifier.fillMaxSize().padding(start = 128.dp, end = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        video.title.ifBlank { "Video" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { dockManager.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(isPlaying, animationSpec = tween(160), label = "dockPlay") { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "Pause" else "Abspielen",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { dockManager.closeAndRelease() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Schließen",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 4) Leuchtende Fortschrittslinie an der Unterkante.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.10f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(5.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to HikariAmber.copy(alpha = 0f),
                            0.6f to HikariAmber.copy(alpha = 0.35f),
                            0.6f to HikariAmber,
                            1f to HikariAmber,
                        ),
                    ),
            )
        }
    }
}
