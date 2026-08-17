package com.hikari.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlin.math.cos
import kotlin.math.sin

// ————— Gemeinsames UI-Kit für alle Spiele —————
// Ziel: Menüs/Overlays/Controls fühlen sich wie ein poliertes Spiel an,
// nicht wie eine Einstellungs-App. Drei Grundzutaten, überall gleich:
// Press-Scale-Feedback, gestaffelte Einblendungen, Akzent-Glow pro Spiel.

// Press-Feedback: skaliert beim Drücken federnd auf 96 % + Haptik-Tick.
@Composable
internal fun Modifier.gxPressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f),
        label = "gxPress",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

// Gestaffelte Einblendung für Menü-Listen (fade + slide, 50 ms Versatz pro Index).
@Composable
internal fun GxAppear(index: Int, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(260, delayMillis = index * 50)) +
            slideInVertically(tween(300, delayMillis = index * 50, easing = FastOutSlowInEasing)) { it / 5 },
    ) { content() }
}

// Animierter Menü-Hintergrund: driftender Akzent-Glow + schwebende Punkte.
@Composable
internal fun GxMenuBackground(accent: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "gxBg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "gxBgT",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(HikariBg)
        val a = t * 2f * Math.PI.toFloat()
        val c1 = Offset(size.width * (0.5f + 0.28f * sin(a)), size.height * (0.18f + 0.06f * cos(a * 1.3f)))
        val c2 = Offset(size.width * (0.5f - 0.3f * cos(a * 0.8f)), size.height * (0.85f + 0.05f * sin(a)))
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent), c1, size.width * 0.75f),
            radius = size.width * 0.75f, center = c1,
        )
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.06f), Color.Transparent), c2, size.width * 0.6f),
            radius = size.width * 0.6f, center = c2,
        )
        // Schwebende Glanzpunkte (deterministisch, kein Random → ruhig)
        for (i in 0 until 14) {
            val fx = (i * 61 % 100) / 100f
            val fy = ((i * 37 + 13) % 100) / 100f
            val drift = sin(a * (1f + i % 3 * 0.3f) + i) * 14f
            val alpha = 0.05f + 0.05f * ((i * 17 % 10) / 10f) * (0.6f + 0.4f * sin(a * 2 + i))
            drawCircle(
                accent.copy(alpha = alpha.coerceIn(0.02f, 0.12f)),
                radius = 2.dp.toPx() + (i % 3),
                center = Offset(size.width * fx + drift, size.height * fy - drift * 0.6f),
            )
        }
    }
}

// Kopfzeile im Menü: runder Zurück-Chip, Gradient-Titel, rechter Slot.
@Composable
internal fun GxHeader(
    title: String,
    accent: Color,
    onBack: () -> Unit,
    right: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        GxIconChip("←", onClick = onBack)
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(brush = Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.45f)))),
        )
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { right?.invoke() }
    }
}

// Runder Icon-Chip (Zurück, Hilfe, Schließen …) mit Press-Feedback.
@Composable
internal fun GxIconChip(glyph: String, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
            .gxPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 16.sp, color = HikariTextMuted, fontWeight = FontWeight.Bold)
    }
}

// Große Modus-Karte: Emoji-Badge mit Glow, Titel, Untertitel, Best-Wert,
// optional Badge ("NEU"), Schloss und Hervorhebung des zuletzt gespielten Modus.
@Composable
internal fun GxModeCard(
    emoji: String,
    title: String,
    subtitle: String,
    accent: Color,
    highlighted: Boolean = false,
    locked: Boolean = false,
    badge: String? = null,
    best: String? = null,
    onClick: () -> Unit,
) {
    val borderBrush = if (highlighted) {
        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.25f)))
    } else {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        lerp(HikariCardBg, accent, if (highlighted) 0.10f else 0.04f),
                        HikariCardBg,
                    )
                )
            )
            .border(if (highlighted) 1.5.dp else 1.dp, borderBrush, RoundedCornerShape(20.dp))
            .gxPressable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.10f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (locked) "🔒" else emoji, fontSize = 25.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        badge,
                        fontSize = 9.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.sp, color = HikariTextMuted, lineHeight = 16.sp)
            if (best != null) {
                Spacer(Modifier.height(3.dp))
                Text(best, fontSize = 11.sp, color = accent.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("›", fontSize = 22.sp, color = if (highlighted) accent else HikariTextFaint)
    }
}

// Sekundäre Menü-Aktion (Statistik/Erfolge/Optionen) als kompakte Kachel.
@Composable
internal fun GxSmallAction(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .gxPressable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = HikariTextMuted, fontWeight = FontWeight.Medium)
    }
}

// Bottom-Sheet-Overlay: Scrim + hochsliden, Drag-Handle-Optik, runde obere Ecken.
@Composable
internal fun GxSheet(
    title: String,
    accent: Color,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val scrim by animateFloatAsState(if (visible.targetState) 0.72f else 0f, tween(220), label = "gxScrim")
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
                    .padding(top = 10.dp, bottom = 28.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(title, fontSize = 19.sp, color = HikariText, fontWeight = FontWeight.Black)
                    GxIconChip("✕", size = 34.dp, onClick = onClose)
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    content()
                }
            }
        }
    }
}

// Eigener Spiel-Toggle statt Material-Switch: animierte Akzent-Pille.
@Composable
internal fun GxToggle(
    label: String,
    desc: String?,
    accent: Color,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val thumbX by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "gxToggle",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(!checked)
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Medium)
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = HikariTextFaint, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(50.dp).height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(lerp(HikariSurfaceHigh, accent, thumbX)),
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(22.dp)
                    .graphicsLayer { translationX = thumbX * 22.dp.toPx() }
                    .clip(CircleShape)
                    .background(lerp(Color.White.copy(alpha = 0.85f), Color.Black, thumbX * 0.9f)),
            )
        }
    }
}

// Segment-Auswahl (z. B. Empfindlichkeit, Bo1/Bo3/Bo5) mit Akzent-Pille.
@Composable
internal fun GxSegmented(
    options: List<String>,
    selected: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(HikariSurfaceHigh)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "gxSeg$i")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = bg))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (active) Color.Black else HikariTextMuted,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                )
            }
        }
    }
}

// Primärer Spiel-Button: Akzent-Gradient, groß, federndes Press-Feedback.
@Composable
internal fun GxPrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.22f))))
            .gxPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Black)
    }
}

// Sekundärer Button (Geist-Stil) für "Zum Menü", "Abbrechen" etc.
@Composable
internal fun GxGhostButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .gxPressable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
    }
}

// Bestätigungs-Dialog (Neustart, Reset) — zentrierte Karte mit Scale-In.
@Composable
internal fun GxConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    accent: Color,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appear = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 600f))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF232326))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(text, fontSize = 13.sp, color = HikariTextMuted, textAlign = TextAlign.Center, lineHeight = 18.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                GxGhostButton("Abbrechen", Modifier.weight(1f), onClick = onDismiss)
                GxPrimaryButton(confirmLabel, if (danger) HikariDanger else accent, Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

// Statistik-Kachel fürs 2er-Raster.
@Composable
internal fun GxStatTile(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, color = accent, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.sp, color = HikariTextMuted)
    }
}

// Erfolgs-Zeile mit Freischalt-Zustand.
@Composable
internal fun GxAchRow(emoji: String, title: String, desc: String, accent: Color, unlocked: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (unlocked) lerp(HikariCardBg, accent, 0.07f) else HikariCardBg)
            .border(
                1.dp,
                if (unlocked) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (unlocked) accent.copy(alpha = 0.18f) else HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (unlocked) emoji else "🔒", fontSize = 19.sp, modifier = Modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.5f })
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 14.sp,
                color = if (unlocked) HikariText else HikariTextMuted,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 11.sp, color = HikariTextFaint, lineHeight = 14.sp)
        }
        if (unlocked) {
            Spacer(Modifier.width(8.dp))
            Text("✓", fontSize = 16.sp, color = accent, fontWeight = FontWeight.Black)
        }
    }
}

// Sterne-Reihe für Level-/Sektor-Bewertungen.
@Composable
internal fun GxStarRow(stars: Int, max: Int = 3, size: Dp = 14.dp) {
    Row {
        repeat(max) { i ->
            Text(
                "★",
                fontSize = (size.value).sp,
                color = if (i < stars) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.15f),
            )
        }
    }
}

// Fortschritts-Ring (Booster-Ladung, Restzeit) als Canvas-Arc.
@Composable
internal fun GxProgressRing(
    frac: Float,
    accent: Color,
    size: Dp = 44.dp,
    stroke: Dp = 3.5.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            drawArc(
                HikariSurfaceHigh, -90f, 360f, useCenter = false,
                style = Stroke(sw), topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
            )
            drawArc(
                accent, -90f, 360f * frac.coerceIn(0f, 1f), useCenter = false,
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
            )
        }
        content()
    }
}

// HUD-Pille für Score/Combo/Zeit im laufenden Spiel.
@Composable
internal fun GxHudPill(label: String, value: String, accent: Color? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HikariCardBg.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, color = HikariTextFaint, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 13.sp, color = accent ?: HikariText, fontWeight = FontWeight.Black)
    }
}

// Animierter Zahlen-Count-up (Score im Game-Over etc.).
@Composable
internal fun gxAnimatedCount(target: Int, durationMs: Int = 700): Int {
    val v by animateIntAsState(target, tween(durationMs, easing = FastOutSlowInEasing), label = "gxCount")
    return v
}

// XP-/Levelkarte mit animiertem Fortschrittsbalken.
@Composable
internal fun GxLevelCard(level: Int, xpText: String, frac: Float, accent: Color) {
    val animFrac by animateFloatAsState(frac.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "gxLvl")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.10f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("$level", fontSize = 17.sp, color = accent, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Level $level", fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Text(xpText, fontSize = 11.sp, color = HikariTextFaint)
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier.fillMaxWidth().height(7.dp)
                    .clip(RoundedCornerShape(4.dp)).background(HikariSurfaceHigh)
            ) {
                Box(
                    Modifier.fillMaxWidth(animFrac).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.3f)))),
                )
            }
        }
    }
}
