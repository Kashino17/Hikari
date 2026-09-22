package com.hikari.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurface

// ── Helligkeit / Lautstärke ──────────────────────────────────────────────────

enum class LevelKind { Brightness, Volume }

/** Vertikaler Pegel mit Icon — erscheint beim Wischen, verschwindet von selbst. */
@Composable
fun LevelIndicator(kind: LevelKind?, level: Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = kind != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(260)),
        modifier = modifier,
    ) {
        val k = kind ?: LevelKind.Volume
        val icon = when (k) {
            LevelKind.Brightness -> Icons.Outlined.BrightnessMedium
            LevelKind.Volume -> when {
                level <= 0.001f -> Icons.Outlined.VolumeOff
                level < 0.5f -> Icons.Outlined.VolumeDown
                else -> Icons.Outlined.VolumeUp
            }
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .background(Color.White),
                )
            }
            Spacer(Modifier.height(10.dp))
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "${(level * 100).toInt()}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Doppeltipp-Badge (gestapelt: +10, +20, +30) ─────────────────────────────

@Composable
fun StackedSeekBadge(forward: Boolean, totalMs: Long, modifier: Modifier = Modifier) {
    val secs = (kotlin.math.abs(totalMs) / 1000L)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (forward) Icons.Default.Forward10 else Icons.Default.Replay10,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            (if (forward) "+" else "−") + "${secs}s",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Kurzer Hinweis (Zoom, Geschwindigkeit, Sperre) ──────────────────────────

@Composable
fun PlayerToast(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Text(
            text = text.orEmpty(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ── Bildschirmsperre ─────────────────────────────────────────────────────────

/**
 * Im gesperrten Zustand ist die Fläche taub. Ein Tipp zeigt kurz die
 * Entsperr-Pille; nur ein Tipp auf die Pille selbst entsperrt.
 */
@Composable
fun LockedHint(visible: Boolean, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, HikariBorderStrong, CircleShape)
                .clickable { onUnlock() }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = HikariAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Bildschirm gesperrt · zum Entsperren tippen", color = Color.White, fontSize = 13.sp)
        }
    }
}

// ── Untere Aktionsleiste (Netflix: Sperren · Folgen · Tempo · Audio · Nächste) ──

@Composable
fun BottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ── Geschwindigkeit ──────────────────────────────────────────────────────────

val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

fun speedLabel(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×".replace(".0×", "×")

@Composable
fun SpeedChooser(current: Float, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Wiedergabegeschwindigkeit", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PLAYBACK_SPEEDS.forEach { s ->
                val active = s == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color.White else HikariSurface)
                        .border(1.dp, if (active) Color.White else HikariBorderStrong, RoundedCornerShape(10.dp))
                        .clickable { onPick(s) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        speedLabel(s),
                        color = if (active) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Audio & Untertitel ───────────────────────────────────────────────────────

data class TrackChoice(val id: String, val label: String, val selected: Boolean)

@Composable
fun TracksChooser(
    audio: List<TrackChoice>,
    subtitles: List<TrackChoice>,
    onPickAudio: (TrackChoice) -> Unit,
    onPickSubtitle: (TrackChoice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Audio", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (audio.isEmpty()) {
                Text("Nur eine Tonspur", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
            audio.forEach { t -> ChoiceRow(t.label, t.selected) { onPickAudio(t) } }
        }
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Untertitel", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ChoiceRow("Aus", subtitles.none { it.selected }) { onPickSubtitle(null) }
            subtitles.forEach { t -> ChoiceRow(t.label, t.selected) { onPickSubtitle(t) } }
            if (subtitles.isEmpty()) {
                Text("Keine Untertitel in der Datei", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = HikariAmber, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Folgenliste ──────────────────────────────────────────────────────────────

@Composable
fun EpisodeList(
    episodes: List<LibraryVideoDto>,
    currentId: String,
    onPick: (LibraryVideoDto) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentId, episodes.size) {
        val idx = episodes.indexOfFirst { it.id == currentId }
        if (idx >= 0) listState.scrollToItem((idx - 1).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = modifier) {
        items(episodes, key = { it.id }) { ep ->
            val active = ep.id == currentId
            val playable = ep.downloaded != 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = playable && !active) { onPick(ep) }
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(63.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HikariSurface)
                        .border(
                            if (active) 1.5.dp else 0.dp,
                            if (active) HikariAmber else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        ),
                ) {
                    if (ep.thumbnail_url != null) {
                        AsyncImage(
                            model = ep.thumbnail_url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        )
                    }
                    val progress = ep.progress_seconds?.let { p ->
                        if (ep.duration_seconds > 0) (p / ep.duration_seconds).coerceIn(0f, 1f) else 0f
                    } ?: 0f
                    if (progress > 0.02f) {
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                                .background(Color.White.copy(alpha = 0.25f)),
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp)
                                .background(HikariAmber),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episodeLabel(ep.season, ep.episode).ifBlank { " " },
                        color = if (active) HikariAmber else Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = com.hikari.app.ui.library.EpisodeTitles.listTitle(null, ep.title),
                        color = if (playable) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ep.duration_seconds > 0) {
                        Text(
                            text = formatClock(ep.duration_seconds * 1000L) + if (!playable) " · nicht geladen" else "",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Icon-Helfer für die Sperre in der Aktionsleiste. */
val LockIcon = Icons.Outlined.Lock
val UnlockIcon = Icons.Outlined.LockOpen
