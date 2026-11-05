package com.hikari.app.ui.russian

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.russian.RuPodcastPlayback
import com.hikari.app.player.PodcastPlayback
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay

/** Alles, was die Player-Oberfläche auslösen kann — als Lambdas, damit Vorschau/Tests ohne Player auskommen. */
internal class PodcastActions(
    val toggle: () -> Unit = {},
    val seekTo: (Long) -> Unit = {},
    val skipBy: (Long) -> Unit = {},
    val cycleSpeed: () -> Unit = {},
    val restart: () -> Unit = {},
    val dismissResume: () -> Unit = {},
)

/** Höhe der Steuerung unter dem Cover (Zeitleiste + Zeiten + Knopfreihe). */
internal val PodcastControlsHeight = 126.dp

private val time = RuPodcastPlayback::formatTime

@Composable
internal fun PodcastPlayerFull(
    state: PodcastPlayback,
    actions: PodcastActions,
    onPhrases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        PodcastCover(state, actions)
        Spacer(Modifier.height(6.dp))
        PodcastScrubber(state, actions.seekTo)
        var remaining by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().offset(y = (-4).dp)) {
            Text(time(state.positionMs), color = HikariTextMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (remaining) "−" + time(state.durationMs - state.positionMs) else time(state.durationMs),
                color = HikariTextMuted, fontSize = 12.sp,
                modifier = Modifier.clickable { remaining = !remaining },
            )
        }
        Row(
            Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val alt = kotlin.math.abs(state.speed - 1f) > 0.001f
            Box(
                Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (alt) HikariAmber.copy(alpha = 0.5f) else HikariBorderStrong, RoundedCornerShape(16.dp))
                    .clickable(onClick = actions.cycleSpeed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    RuPodcastPlayback.speedLabel(state.speed),
                    color = if (alt) HikariAmber else HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            RoundIcon(Icons.Outlined.Replay5, "5 Sekunden zurück") { actions.skipBy(-5_000) }
            Box(
                Modifier.size(62.dp).clip(CircleShape).background(Color.White).clickable(onClick = actions.toggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.playing) "Pause" else "Abspielen",
                    tint = Color(0xFF111111), modifier = Modifier.size(36.dp),
                )
            }
            RoundIcon(Icons.Outlined.Forward10, "10 Sekunden vor") { actions.skipBy(10_000) }
            RoundIcon(Icons.Outlined.Translate, "Zu den Lernphrasen", onClick = onPhrases)
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = HikariText, modifier = Modifier.size(30.dp))
    }
}

/** Cover statt Video (die Folgen sind ein Standbild). Tippen = Play/Pause, Doppeltippen links/rechts = ±10 s. */
@Composable
private fun PodcastCover(state: PodcastPlayback, actions: PodcastActions) {
    val playing by rememberUpdatedState(state.playing)
    val act by rememberUpdatedState(actions)
    var flashKey by remember { mutableIntStateOf(0) }
    var flashPause by remember { mutableStateOf(false) }
    var rippleKey by remember { mutableIntStateOf(0) }
    var rippleSide by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(flashKey) { if (flashKey > 0) { flash.snapTo(1f); flash.animateTo(0f, tween(600)) } }
    LaunchedEffect(rippleKey) { if (rippleKey > 0) { ripple.snapTo(1f); ripple.animateTo(0f, tween(700)) } }
    LaunchedEffect(state.resumedFromMs) {
        if (state.resumedFromMs != null) { delay(6_000); act.dismissResume() }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        flashPause = playing
                        act.toggle()
                        flashKey++
                    },
                    onDoubleTap = { o ->
                        val side = when {
                            o.x < size.width * 0.38f -> -1
                            o.x > size.width * 0.62f -> 1
                            else -> 0
                        }
                        if (side == 0) {
                            flashPause = playing
                            act.toggle()
                            flashKey++
                        } else {
                            act.skipBy(side * 10_000L)
                            rippleSide = side
                            rippleKey++
                        }
                    },
                )
            },
    ) {
        PodcastCoverImage(state.coverUrl, Modifier.fillMaxSize())
        if (rippleSide != 0) {
            Column(
                Modifier.align(if (rippleSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight().width(maxWidth * 0.42f).alpha(ripple.value)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent))),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (rippleSide < 0) Icons.Outlined.FastRewind else Icons.Outlined.FastForward, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Text("10 s", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (flash.value > 0f) {
            Box(
                Modifier.align(Alignment.Center).size(64.dp).scale(0.85f + 0.3f * (1f - flash.value))
                    .alpha(flash.value).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (flashPause) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        if (state.loading) {
            CircularProgressIndicator(
                color = Color.White, trackColor = Color.White.copy(alpha = 0.18f), strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(38.dp),
            )
        }
        if (state.error) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = act.toggle),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Folge lädt gerade nicht", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Tippen für einen neuen Versuch", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
            }
        }
        val resumed = state.resumedFromMs
        if (resumed != null && !state.error) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).height(36.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xEB141416))
                    .border(1.dp, HikariBorderStrong, RoundedCornerShape(18.dp))
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weiter bei ${time(resumed)}", color = HikariText, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Von vorn", color = HikariText, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = act.restart).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** Zeitleiste: ziehen mit Zeitblase, gesprungen wird erst beim Loslassen (kein Seek-Sturm). */
@Composable
private fun PodcastScrubber(state: PodcastPlayback, onSeek: (Long) -> Unit) {
    val seek by rememberUpdatedState(onSeek)
    val duration by rememberUpdatedState(state.durationMs)
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: state.fraction
    val buffered = if (state.durationMs > 0) (state.bufferedMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val railH by animateDpAsState(if (drag != null) 6.dp else 4.dp, label = "rail")
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (duration > 0) seek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> if (duration > 0) drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        if (drag != null) drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        drag?.let { seek((it * duration).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                )
            },
    ) {
        val w = maxWidth
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().height(railH)
                .clip(RoundedCornerShape(3.dp)).background(HikariBorderStrong),
        ) {
            Box(Modifier.fillMaxWidth(buffered).fillMaxHeight().background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = w * frac - 7.dp)
                .size(14.dp).scale(if (drag != null) 1.25f else 1f).clip(CircleShape).background(Color.White),
        )
        drag?.let { f ->
            val target = (f * state.durationMs).toLong()
            val delta = target - state.positionMs
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * f - 50.dp, y = (-30).dp).width(100.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.wrapContentSize().clip(RoundedCornerShape(8.dp)).background(Color.White)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(time(target), color = Color(0xFF111111), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        (if (delta >= 0) "+" else "−") + time(kotlin.math.abs(delta)),
                        color = Color(0xFF555555), fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Leiste: oben angedockt beim Scrollen, unten als Mini-Player nach "Zurück". */
@Composable
internal fun PodcastPlayerBar(
    state: PodcastPlayback,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.fillMaxWidth().height(64.dp).shadow(14.dp, shape).clip(shape).background(HikariSurface)
            .border(1.dp, HikariBorder, shape).clickable(onClick = onExpand),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(92.dp).height(52.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
                PodcastCoverImage(state.coverUrl, Modifier.fillMaxSize())
                if (state.loading) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.align(Alignment.Center).size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Lektion ${state.day ?: ""} · ${state.title}",
                    color = HikariText, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${time(state.positionMs)} / ${time(state.durationMs)}",
                    color = HikariTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.playing) "Pause" else "Abspielen",
                    tint = HikariText, modifier = Modifier.size(28.dp),
                )
            }
            if (onClose != null) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Schließen", tint = HikariText, modifier = Modifier.size(26.dp))
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp).fillMaxWidth().height(2.dp)
                .clip(RoundedCornerShape(1.dp)).background(HikariBorderStrong),
        ) {
            Box(Modifier.fillMaxWidth(state.fraction).fillMaxHeight().background(Color.White))
        }
    }
}

/** Cover der Folge. In Vorschau/Snapshot-Tests leer — Coil braucht dort einen Main-Dispatcher, den es nicht gibt. */
@Composable
private fun PodcastCoverImage(url: String, modifier: Modifier) {
    if (LocalInspectionMode.current) return
    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
