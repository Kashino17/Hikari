package com.hikari.app.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlin.math.sin

// ————— Gemeinsames UI-Kit für die Musik-Oberfläche —————
// Gleiche Philosophie wie das Spiele-Kit: Press-Scale-Feedback überall,
// weiche Einblendungen, Ambient-Look im Player — Musik-App-Gefühl à la
// YouTube Music statt Einstellungs-App.

// Press-Feedback: federndes Scale-Down + Haptik-Tick.
@Composable
internal fun Modifier.muPressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f),
        label = "muPress",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

// Gestaffelte Einblendung — NICHT in LazyColumn-Items verwenden
// (Recycling würde die Animation beim Scrollen erneut abspielen).
@Composable
internal fun MuAppear(index: Int, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(240, delayMillis = index * 45)) +
            slideInVertically(tween(280, delayMillis = index * 45, easing = FastOutSlowInEasing)) { it / 6 },
    ) { content() }
}

// Runder Icon-Button mit garantiertem Touch-Ziel und Press-Feedback.
@Composable
internal fun MuIconButton(
    icon: ImageVector,
    contentDesc: String?,
    modifier: Modifier = Modifier,
    tint: Color = HikariTextMuted,
    iconSize: Dp = 24.dp,
    touchSize: Dp = 44.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(touchSize)
            .clip(CircleShape)
            .background(if (active) HikariPrimary.copy(alpha = 0.16f) else Color.Transparent)
            .muPressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDesc, tint = if (active) HikariPrimary else tint, modifier = Modifier.size(iconSize))
    }
}

// Großer Play/Pause-Button: Amber-Gradient, Icon-Crossfade, Puls beim Umschalten.
@Composable
internal fun MuPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playIcon: ImageVector,
    pauseIcon: ImageVector,
    size: Dp = 68.dp,
    onToggle: () -> Unit,
) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(isPlaying) {
        pulse.snapTo(0.86f)
        pulse.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 480f))
    }
    // YouTube-Music-Stil: weißer Play-Kreis statt Akzentfarbe.
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(CircleShape)
            .background(Color.White)
            .muPressable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 3.dp,
                modifier = Modifier.size(size * 0.42f),
            )
        } else {
            Crossfade(isPlaying, animationSpec = tween(160), label = "muPlay") { playing ->
                Icon(
                    if (playing) pauseIcon else playIcon,
                    if (playing) "Pause" else "Abspielen",
                    tint = Color.Black,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

// Eigener Seekbar im YouTube-Music-Stil: schlanke Linie, Daumen wächst beim
// Ziehen, Tap-to-Seek. Ersetzt den Material-Slider.
@Composable
internal fun MuSeekBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color = HikariPrimary,
    onSeekPreview: (Float) -> Unit = {},
    onSeek: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val frac = (if (dragging) dragFrac else progress).coerceIn(0f, 1f)
    val trackH by animateDpAsState(if (dragging) 6.dp else 3.5.dp, tween(120), label = "muTrack")
    val thumbR by animateDpAsState(if (dragging) 9.dp else 6.dp, tween(120), label = "muThumb")

    Box(
        modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val f = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragFrac = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = {
                        dragging = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek(dragFrac)
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFrac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekPreview(dragFrac)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = trackH.toPx()
            val y = size.height / 2f
            val corner = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(
                HikariSurfaceHigh,
                topLeft = Offset(0f, y - h / 2f),
                size = Size(size.width, h),
                cornerRadius = corner,
            )
            val w = size.width * frac
            if (w > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.75f), accent)),
                    topLeft = Offset(0f, y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = corner,
                )
            }
            drawCircle(accent, radius = thumbR.toPx(), center = Offset(w, y))
            if (dragging) {
                drawCircle(accent.copy(alpha = 0.25f), radius = thumbR.toPx() * 2f, center = Offset(w, y))
            }
        }
    }
}

// Ambient-Backdrop für den Player: aufgeblasenes, weichgezeichnetes Artwork
// hinter dem Inhalt + Scrim nach unten ins HikariBg (blur ab API 31,
// darunter greift das kräftige Scrim allein — deshalb niedrige Alpha).
@Composable
internal fun MuArtworkBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(HikariBg)) {
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .graphicsLayer { scaleX = 1.35f; scaleY = 1.35f; alpha = 0.45f },
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        HikariBg.copy(alpha = 0.30f),
                        HikariBg.copy(alpha = 0.72f),
                        HikariBg,
                    )
                )
            )
        )
    }
}

// Bottom-Sheet im Musik-Look: Scrim, Slide-up, Drag-Handle, runde obere Ecken.
@Composable
internal fun MuSheet(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val scrim by animateFloatAsState(if (visible.targetState) 0.72f else 0f, tween(220), label = "muScrim")
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF232326))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Spacer(Modifier.height(14.dp))
                Text(title, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    content()
                }
            }
        }
    }
}

// Pill-Chip (Filter, Modi, Aktionen) mit Aktiv-Zustand und Press-Feedback.
@Composable
internal fun MuChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) HikariPrimary else HikariCardBg)
            .border(
                1.dp,
                if (active) Color.Transparent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(999.dp),
            )
            .muPressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (active) Color.Black else HikariTextMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) Color.Black else HikariTextMuted,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// Beschriftete Aktions-Pille (Favorit/Download/Playlist) für den Player.
@Composable
internal fun MuActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = HikariPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) activeColor.copy(alpha = 0.16f) else HikariCardBg.copy(alpha = 0.8f))
            .border(
                1.dp,
                if (active) activeColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(999.dp),
            )
            .muPressable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = if (active) activeColor else HikariTextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (active) activeColor else HikariTextMuted,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Equalizer-Indikator: drei tanzende Balken für den gerade spielenden Song.
@Composable
internal fun MuEqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = HikariPrimary,
    barWidth: Dp = 3.dp,
) {
    val t by rememberInfiniteTransition(label = "muEq").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "muEqT",
    )
    Canvas(modifier.size(18.dp)) {
        val bw = barWidth.toPx()
        val gap = (size.width - bw * 3) / 2f
        for (i in 0 until 3) {
            val phase = t * 2f * Math.PI.toFloat() + i * 2.1f
            val f = if (playing) 0.35f + 0.65f * (0.5f + 0.5f * sin(phase)) else 0.25f
            val h = size.height * f
            drawRoundRect(
                color,
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}

// Abschnitts-Überschrift mit optionaler Aktion rechts.
@Composable
internal fun MuSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Black)
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                fontSize = 12.sp,
                color = HikariTextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .muPressable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// Primär-/Ghost-Buttons im Musik-Look (Abspielen, Shuffle, Sheet-Aktionen).
@Composable
internal fun MuPrimaryButton(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // YouTube-Music-Stil: weiße Fläche mit schwarzem Inhalt statt Akzentfarbe.
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .muPressable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun MuGhostButton(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .muPressable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = HikariText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
    }
}

// Shimmer-Puls für Lade-Skeletons.
@Composable
internal fun muShimmerAlpha(): Float {
    val a by rememberInfiniteTransition(label = "muShimmer").animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "muShimmerA",
    )
    return a
}

// Hero-Header für Detail-Seiten: Artwork + Scrim, Titel unten, dann Buttons.
@Composable
internal fun MuHeroHeader(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    fallbackIcon: ImageVector,
    height: Dp = 240.dp,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(HikariSurfaceHigh), contentAlignment = Alignment.Center) {
                Icon(fallbackIcon, null, tint = HikariTextFaint, modifier = Modifier.size(72.dp))
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, HikariBg.copy(alpha = 0.55f), HikariBg),
                    startY = 0f,
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Black, maxLines = 2)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = HikariTextMuted, maxLines = 1)
            }
        }
    }
}
