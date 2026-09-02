package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// ————— Snake —————
// Der Klassiker mit Wischsteuerung, Bonus-Früchten und drei Modi. Läuft als
// simple Tick-Schleife in einem LaunchedEffect — die Tickdauer sinkt mit der
// Länge der Schlange, dadurch entsteht die Spannungskurve.

private enum class SkScreen { MENU, GAME }

private enum class SkMode(val id: String, val label: String, val emoji: String, val desc: String) {
    CLASSIC("classic", "Klassisch", "🐍", "Wände sind tödlich. Früchte sammeln, länger und schneller werden."),
    PORTAL("portal", "Portal", "🌀", "Kein Rand — wer rausläuft, kommt gegenüber wieder rein."),
    BLITZ("blitz", "Blitz", "⚡", "Von Anfang an flott, doppelt so viele Bonus-Früchte. Adrenalin pur."),
}

private enum class SkPhase { READY, RUNNING, PAUSED, OVER }

private val SkAccent = Color(0xFFA3E635)
private const val SkCols = 15
private const val SkRows = 24
private val SkSpeedLabels = listOf("Gemütlich", "Normal", "Schnell")
private val SkSpeedBase = listOf(200, 165, 130)

private class SkStore(val p: SharedPreferences) {
    fun getBool(k: String, d: Boolean) = p.getBoolean("snake_$k", d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean("snake_$k", v).apply()
    fun getInt(k: String, d: Int) = p.getInt("snake_$k", d)
    fun setInt(k: String, v: Int) = p.edit().putInt("snake_$k", v).apply()
    fun getStr(k: String): String? = p.getString("snake_$k", null)
    fun setStr(k: String, v: String?) = p.edit().putString("snake_$k", v).apply()
    fun bump(k: String, by: Int = 1) = setInt(k, getInt(k, 0) + by)
}

private data class SkCell(val x: Int, val y: Int)

private fun skRandomFree(rnd: Random, taken: Collection<SkCell>): SkCell {
    val set = taken.toHashSet()
    while (true) {
        val c = SkCell(rnd.nextInt(SkCols), rnd.nextInt(SkRows))
        if (c !in set) return c
    }
}

// ————— Root —————

@Composable
fun SnakeGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { SkStore(context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)) }

    var screen by remember { mutableStateOf(SkScreen.MENU) }
    var mode by remember {
        mutableStateOf(SkMode.entries.firstOrNull { it.id == store.getStr("last_mode") } ?: SkMode.CLASSIC)
    }
    var speedIdx by remember { mutableIntStateOf(store.getInt("speed", 1).coerceIn(0, 2)) }
    var hapticsOn by remember { mutableStateOf(store.getBool("haptics", true)) }
    var gridOn by remember { mutableStateOf(store.getBool("grid", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var gameKey by remember { mutableIntStateOf(0) }

    fun buzz(t: HapticFeedbackType) { if (hapticsOn) haptic.performHapticFeedback(t) }

    BackHandler(enabled = screen == SkScreen.MENU) {
        when {
            showSettings -> showSettings = false
            showStats -> showStats = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Crossfade(targetState = screen, animationSpec = tween(220), label = "skScreen") { s ->
            when (s) {
                SkScreen.MENU -> SkMenuScreen(
                    store = store,
                    mode = mode, onMode = { mode = it },
                    speedIdx = speedIdx, onSpeed = { speedIdx = it; store.setInt("speed", it) },
                    onStart = {
                        store.setStr("last_mode", mode.id)
                        gameKey++
                        screen = SkScreen.GAME
                    },
                    onStats = { showStats = true },
                    onSettings = { showSettings = true },
                    onBack = onBack,
                )
                SkScreen.GAME -> key(gameKey) {
                    SkPlayScreen(
                        mode = mode,
                        speedIdx = speedIdx,
                        gridOn = gridOn,
                        store = store,
                        buzz = { buzz(it) },
                        onExit = { screen = SkScreen.MENU },
                    )
                }
            }
        }

        if (showSettings) {
            GxSheet("Optionen", SkAccent, onClose = { showSettings = false }) {
                GxToggle("Haptik", "Vibration beim Fressen und Crash.", SkAccent, hapticsOn) {
                    hapticsOn = it; store.setBool("haptics", it)
                }
                GxToggle("Gitter anzeigen", "Dezentes Schachbrett im Spielfeld.", SkAccent, gridOn) {
                    gridOn = it; store.setBool("grid", it)
                }
            }
        }
        if (showStats) {
            GxSheet("Statistik", SkAccent, onClose = { showStats = false }) {
                SkMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically,
                    ) {
                        Text("${m.emoji}  ${m.label}", fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold)
                        Text(
                            "Best ${store.getInt("best_${m.id}", 0)} · ${store.getInt("games_${m.id}", 0)} Runden",
                            fontSize = 12.sp, color = HikariTextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    GxStatTile("${store.getInt("apples_total", 0)}", "Früchte", SkAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("longest", 0)}", "Längste", SkAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("bonus_total", 0)}", "Bonus", SkAccent, Modifier.weight(1f))
                }
            }
        }
    }
}

// ————— Menü —————

@Composable
private fun SkMenuScreen(
    store: SkStore,
    mode: SkMode, onMode: (SkMode) -> Unit,
    speedIdx: Int, onSpeed: (Int) -> Unit,
    onStart: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    BackHandler(enabled = showRules) { showRules = false }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Snake", SkAccent, onBack = onBack, right = { GxIconChip("?") { showRules = true } })
            Column(Modifier.padding(horizontal = 16.dp)) {
                GxSectionTitle("Modus")
                SkMode.entries.forEachIndexed { i, m ->
                    val best = store.getInt("best_${m.id}", 0)
                    GxAppear(i) {
                        GxModeCard(
                            emoji = m.emoji,
                            title = m.label,
                            subtitle = m.desc,
                            accent = SkAccent,
                            highlighted = mode == m,
                            best = if (best > 0) "Best: $best" else null,
                            onClick = { onMode(m) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                GxAppear(3) {
                    Column {
                        GxSectionTitle("Tempo")
                        GxSegmented(SkSpeedLabels, speedIdx, SkAccent) { onSpeed(it) }
                    }
                }
                Spacer(Modifier.height(22.dp))
                GxAppear(4) {
                    GxPrimaryButton("Spielen", SkAccent, Modifier.fillMaxWidth(), onClick = onStart)
                }
                Spacer(Modifier.height(12.dp))
                GxAppear(5) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f), onSettings)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (showRules) {
            GxSheet("So geht's", SkAccent, onClose = { showRules = false }) {
                Text(
                    "Wische in die Richtung, in die die Schlange laufen soll. Jede rote Frucht macht dich " +
                        "einen Punkt länger — und ein bisschen schneller. Die goldene Bonus-Frucht bringt 5 Punkte, " +
                        "verschwindet aber nach kurzer Zeit wieder.\n\nBerührst du dich selbst (oder im Klassik-Modus " +
                        "die Wand), ist die Runde vorbei. Du kannst zwei Richtungswechsel vorausplanen — schnelle " +
                        "Doppelwischer werden sauber nacheinander ausgeführt.",
                    fontSize = 13.sp, color = HikariTextMuted, lineHeight = 19.sp,
                )
            }
        }
    }
}

// ————— Spiel —————

@Composable
private fun SkPlayScreen(
    mode: SkMode,
    speedIdx: Int,
    gridOn: Boolean,
    store: SkStore,
    buzz: (HapticFeedbackType) -> Unit,
    onExit: () -> Unit,
) {
    val rnd = remember { Random(System.currentTimeMillis()) }
    val startSnake = remember {
        listOf(SkCell(SkCols / 2, SkRows / 2), SkCell(SkCols / 2, SkRows / 2 + 1), SkCell(SkCols / 2, SkRows / 2 + 2))
    }
    var snake by remember { mutableStateOf(startSnake) }
    var dir by remember { mutableStateOf(SkCell(0, -1)) }
    val pending = remember { ArrayDeque<SkCell>() }
    var food by remember { mutableStateOf(skRandomFree(rnd, startSnake)) }
    var bonus by remember { mutableStateOf<SkCell?>(null) }
    var bonusTtl by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(SkPhase.READY) }
    var best by remember { mutableIntStateOf(store.getInt("best_${mode.id}", 0)) }
    var newRecord by remember { mutableStateOf(false) }

    val base = SkSpeedBase[speedIdx] - if (mode == SkMode.BLITZ) 40 else 0
    fun tickMs(): Long = (base - (snake.size - 3) * 2.2f).coerceAtLeast(base * 0.45f).toLong()

    fun reset() {
        snake = startSnake
        dir = SkCell(0, -1)
        pending.clear()
        food = skRandomFree(rnd, startSnake)
        bonus = null
        bonusTtl = 0
        score = 0
        newRecord = false
        phase = SkPhase.READY
    }

    fun gameOver() {
        phase = SkPhase.OVER
        buzz(HapticFeedbackType.LongPress)
        store.bump("games_${mode.id}")
        if (score > best) {
            best = score
            newRecord = score > 0
            store.setInt("best_${mode.id}", score)
        }
        if (snake.size > store.getInt("longest", 0)) store.setInt("longest", snake.size)
    }

    fun step() {
        val nd = pending.removeFirstOrNull()?.takeIf { !(it.x == -dir.x && it.y == -dir.y) } ?: dir
        dir = nd
        val head = snake.first()
        var nx = head.x + nd.x
        var ny = head.y + nd.y
        if (mode == SkMode.PORTAL) {
            nx = (nx + SkCols) % SkCols
            ny = (ny + SkRows) % SkRows
        } else if (nx < 0 || ny < 0 || nx >= SkCols || ny >= SkRows) {
            gameOver(); return
        }
        val newHead = SkCell(nx, ny)
        val ateFood = newHead == food
        val ateBonus = bonus != null && newHead == bonus
        val body = if (ateFood || ateBonus) snake else snake.dropLast(1)
        if (newHead in body) { gameOver(); return }
        snake = listOf(newHead) + body
        if (ateFood) {
            score += 1
            store.bump("apples_total")
            buzz(HapticFeedbackType.TextHandleMove)
            food = skRandomFree(rnd, snake + listOfNotNull(bonus))
            val bonusChance = if (mode == SkMode.BLITZ) 0.30f else 0.15f
            if (bonus == null && rnd.nextFloat() < bonusChance) {
                bonus = skRandomFree(rnd, snake + food)
                bonusTtl = 45
            }
        }
        if (ateBonus) {
            score += 5
            store.bump("bonus_total")
            buzz(HapticFeedbackType.LongPress)
            bonus = null
        } else if (bonus != null) {
            bonusTtl -= 1
            if (bonusTtl <= 0) bonus = null
        }
    }

    LaunchedEffect(phase) {
        if (phase != SkPhase.RUNNING) return@LaunchedEffect
        while (phase == SkPhase.RUNNING) {
            delay(tickMs())
            if (phase == SkPhase.RUNNING) step()
        }
    }

    fun swipe(d: SkCell) {
        if (phase == SkPhase.OVER || phase == SkPhase.PAUSED) return
        val ref = pending.lastOrNull() ?: dir
        val reverse = d.x == -ref.x && d.y == -ref.y
        if (phase == SkPhase.READY) {
            // Jeder Wisch startet — auch geradeaus; nur rückwärts wird ignoriert.
            if (!reverse) dir = d
            phase = SkPhase.RUNNING
            return
        }
        if (d == ref || reverse) return
        if (pending.size < 2) pending.addLast(d)
    }

    BackHandler {
        when (phase) {
            SkPhase.RUNNING -> phase = SkPhase.PAUSED
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkAccent)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                GxIconChip("←", onClick = onExit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GxHudPill("Punkte", "$score", SkAccent)
                    GxHudPill("Best", "$best")
                }
                GxIconChip("II") { if (phase == SkPhase.RUNNING) phase = SkPhase.PAUSED }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                SkBoard(
                    snake = snake,
                    dir = dir,
                    food = food,
                    bonus = bonus,
                    bonusFrac = bonusTtl / 45f,
                    gridOn = gridOn,
                    onSwipe = { swipe(it) },
                )
                if (phase == SkPhase.READY) {
                    val pulse by rememberInfiniteTransition(label = "skReady").animateFloat(
                        0.5f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "skReadyT",
                    )
                    Column(
                        Modifier
                            .graphicsLayer { alpha = pulse }
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Wische, um zu starten", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(mode.label + " · " + SkSpeedLabels[speedIdx], fontSize = 12.sp, color = HikariTextMuted)
                    }
                }
            }
            Text(
                "Länge ${snake.size} · ${mode.label}",
                fontSize = 12.sp, color = HikariTextFaint, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        if (phase == SkPhase.PAUSED) {
            GxResultOverlay(
                title = "Pause",
                subtitle = "$score Punkte · Länge ${snake.size}",
                accent = SkAccent,
                stats = emptyList(),
                primaryLabel = "Weiter",
                onPrimary = { phase = SkPhase.RUNNING },
                secondaryLabel = "Zum Menü",
                onSecondary = onExit,
            )
        }
        if (phase == SkPhase.OVER) {
            GxResultOverlay(
                title = if (newRecord) "Neuer Rekord!" else "Crash!",
                subtitle = if (newRecord) "Bestwert auf ${mode.label} geknackt." else "Länge ${snake.size} erreicht.",
                accent = SkAccent,
                stats = listOf("Punkte" to "$score", "Best" to "$best", "Länge" to "${snake.size}"),
                primaryLabel = "Nochmal",
                onPrimary = { reset() },
                secondaryLabel = "Zum Menü",
                onSecondary = onExit,
                badge = if (newRecord) "REKORD" else null,
            )
        }
    }
}

@Composable
private fun SkBoard(
    snake: List<SkCell>,
    dir: SkCell,
    food: SkCell,
    bonus: SkCell?,
    bonusFrac: Float,
    gridOn: Boolean,
    onSwipe: (SkCell) -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 22.dp.toPx() }
    val pulse by rememberInfiniteTransition(label = "skPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "skPulseT",
    )
    // Kein fillMaxSize davor: mit festen Constraints würde aspectRatio eine
    // überlaufende Größe liefern und das Feld über den Rand hinaus zeichnen.
    Canvas(
        Modifier
            .aspectRatio(SkCols / SkRows.toFloat(), matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = { acc = Offset.Zero },
                    onDrag = { change, drag ->
                        change.consume()
                        acc += drag
                        val ax = abs(acc.x)
                        val ay = abs(acc.y)
                        if (ax > threshold || ay > threshold) {
                            onSwipe(
                                if (ax > ay) SkCell(if (acc.x > 0) 1 else -1, 0)
                                else SkCell(0, if (acc.y > 0) 1 else -1)
                            )
                            acc = Offset.Zero
                        }
                    },
                )
            },
    ) {
        val cell = size.width / SkCols
        if (gridOn) {
            for (y in 0 until SkRows) for (x in 0 until SkCols) {
                if ((x + y) % 2 == 0) {
                    drawRect(Color.White.copy(alpha = 0.025f), Offset(x * cell, y * cell), Size(cell, cell))
                }
            }
        }
        // Frucht
        val fc = Offset(food.x * cell + cell / 2, food.y * cell + cell / 2)
        drawCircle(Color(0xFFF87171), radius = cell * 0.36f, center = fc)
        drawCircle(Color.White.copy(alpha = 0.35f), radius = cell * 0.10f, center = fc + Offset(-cell * 0.12f, -cell * 0.12f))
        drawRoundRect(
            Color(0xFF4ADE80), Offset(fc.x - cell * 0.05f, fc.y - cell * 0.48f),
            Size(cell * 0.22f, cell * 0.12f), CornerRadius(cell * 0.06f),
        )
        // Bonus mit Rest-Ring
        if (bonus != null) {
            val bc = Offset(bonus.x * cell + cell / 2, bonus.y * cell + cell / 2)
            val r = cell * (0.34f + 0.06f * pulse)
            drawCircle(Color(0xFFFBBF24).copy(alpha = 0.25f), radius = r * 1.6f, center = bc)
            drawCircle(Color(0xFFFBBF24), radius = r, center = bc)
            drawArc(
                Color.White.copy(alpha = 0.8f), -90f, 360f * bonusFrac.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(bc.x - r * 1.35f, bc.y - r * 1.35f), size = Size(r * 2.7f, r * 2.7f),
                style = Stroke(2.dp.toPx()),
            )
        }
        // Schlange: Kopf hell, Schwanz dunkler
        val n = snake.size
        val tail = lerp(SkAccent, Color(0xFF365314), 0.75f)
        snake.forEachIndexed { i, s ->
            val t = if (n <= 1) 0f else i / (n - 1).toFloat()
            val col = lerp(SkAccent, tail, t)
            val inset = cell * 0.08f
            drawRoundRect(
                col,
                Offset(s.x * cell + inset, s.y * cell + inset),
                Size(cell - inset * 2, cell - inset * 2),
                CornerRadius(cell * 0.32f),
            )
        }
        // Kopf: Augen in Blickrichtung
        val head = snake.first()
        val hc = Offset(head.x * cell + cell / 2, head.y * cell + cell / 2)
        val ex = if (dir.x != 0) cell * 0.18f * dir.x else 0f
        val ey = if (dir.y != 0) cell * 0.18f * dir.y else 0f
        val side = if (dir.x != 0) Offset(0f, cell * 0.2f) else Offset(cell * 0.2f, 0f)
        val eyeR = cell * 0.09f
        drawCircle(Color(0xFF1A2E05), radius = eyeR, center = hc + Offset(ex, ey) + side)
        drawCircle(Color(0xFF1A2E05), radius = eyeR, center = hc + Offset(ex, ey) - side)
    }
}
