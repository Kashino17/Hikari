package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.ui.theme.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

// ————— Modi / Screens / Objekt-Arten —————

private enum class HoleMode { KLASSIK, RUSH, WELTEN }
private enum class HoleScreen { MENU, PLAY, WELTWAHL, STATS, ERFOLGE }
private enum class HoleKind { FRUCHT, BOMBE, GOLD, HERZ, UHR, MAGNET, REGENBOGEN }

// Akzentfarbe des Spiels (lila) — konsistent mit der Karte im GamesScreen
private val HoleAccent = Color(0xFFA78BFA)

// ————— Themen (Klassik wechselt alle 3 Level) —————

private class HoleTheme(val name: String, val fruits: List<String>, val floor: Color, val glow: Color)

private val HoleThemes = listOf(
    HoleTheme("Beerenhain", listOf("🍓", "🍒", "🍇", "🍎"), Color(0xFF140A10), Color(0xFFF472B6)),
    HoleTheme("Tropenbucht", listOf("🍌", "🍍", "🥥", "🍉"), Color(0xFF141003), Color(0xFFFBBF24)),
    HoleTheme("Zitrusgarten", listOf("🍊", "🍋", "🍐", "🍏"), Color(0xFF121403), Color(0xFFD9F99D)),
)

// ————— Welten-Reise: 4 Welten mit eigener Mechanik + Missionen —————

private class HoleMissionDef(val desc: String, val kind: Int, val target: Int) // 0=Früchte 1=Combo 2=Entschärft 3=Powerups 4=Punkte 5=Gold

private class HoleWorldDef(
    val name: String,
    val emoji: String,
    val mechanik: String,
    val fruits: List<String>,
    val floor: Color,
    val glow: Color,
    val missions: List<HoleMissionDef>,
)

private val HoleWorlds = listOf(
    HoleWorldDef(
        "Wiese", "🌿", "Sanfter Einstieg",
        listOf("🍎", "🍓", "🍇", "🍉"), Color(0xFF0E1408), Color(0xFF86EFAC),
        listOf(
            HoleMissionDef("30 Früchte in einer Runde", 0, 30),
            HoleMissionDef("Combo 8 erreichen", 1, 8),
            HoleMissionDef("300 Punkte in einer Runde", 4, 300),
        ),
    ),
    HoleWorldDef(
        "Wüste", "🏜️", "Seitenwind treibt alles ab",
        listOf("🍊", "🍋", "🥥", "🍌"), Color(0xFF171004), Color(0xFFFBBF24),
        listOf(
            HoleMissionDef("40 Früchte in einer Runde", 0, 40),
            HoleMissionDef("2 Bomben entschärfen", 2, 2),
            HoleMissionDef("Combo 10 erreichen", 1, 10),
        ),
    ),
    HoleWorldDef(
        "Schnee", "❄️", "Das Loch rutscht nach",
        listOf("🍇", "🍏", "🍐", "🍒"), Color(0xFF0D1420), Color(0xFF7DD3FC),
        listOf(
            HoleMissionDef("500 Punkte in einer Runde", 4, 500),
            HoleMissionDef("3 Power-ups einsammeln", 3, 3),
            HoleMissionDef("50 Früchte in einer Runde", 0, 50),
        ),
    ),
    HoleWorldDef(
        "Weltraum", "🌌", "Wenig Schwerkraft, Früchte prallen ab",
        listOf("🍒", "🍑", "🍍", "🍉"), Color(0xFF0A0A18), Color(0xFFA78BFA),
        listOf(
            HoleMissionDef("Combo 12 erreichen", 1, 12),
            HoleMissionDef("2 Gold-Früchte fangen", 5, 2),
            HoleMissionDef("800 Punkte in einer Runde", 4, 800),
        ),
    ),
)

// ————— Erfolge —————

private class HoleAchDef(val id: String, val emoji: String, val title: String, val desc: String)

private val HoleAchs = listOf(
    HoleAchDef("combo20", "🔥", "Combo-König", "Erreiche Combo 20"),
    HoleAchDef("level10", "🚀", "Marathon", "Erreiche Level 10 im Klassik-Modus"),
    HoleAchDef("entschaerft", "✂️", "Entschärfer", "Entschärfe eine Bombe per Fingertipp"),
    HoleAchDef("sammler", "🎒", "Sammler", "5 Power-ups in einer Runde"),
    HoleAchDef("rush500", "⏱️", "Rush-Meister", "500 Punkte in Rush Hour"),
    HoleAchDef("weltmeister", "🌍", "Weltenbummler", "Alle Missionen einer Welt geschafft"),
    HoleAchDef("vielfrass", "🍽️", "Vielfraß", "100 Früchte in einer Runde"),
    HoleAchDef("fieber", "🌡️", "Fieber!", "Fieber-Modus aktiviert (Combo 12)"),
    HoleAchDef("gold10", "🌟", "Goldgräber", "10 goldene Früchte insgesamt"),
    HoleAchDef("punkte1000", "🏆", "Punktejäger", "1000 Punkte in einer Runde"),
)

private val HoleSens = floatArrayOf(0.8f, 1.15f, 1.6f)

// ————— Persistenz-Helfer —————

private fun holeLevelFromXp(xp: Int): Pair<Int, Float> {
    var lvl = 1
    var rem = xp
    while (rem >= lvl * 150) {
        rem -= lvl * 150
        lvl++
    }
    return lvl to rem / (lvl * 150f)
}

private fun holeAddTopScore(prefs: SharedPreferences, score: Int, label: String) {
    if (score <= 0) return
    val raw = prefs.getString("fruithole_top5", "") ?: ""
    val entries = raw.split(";").filter { it.isNotBlank() }.toMutableList()
    entries.add("$score|$label")
    val sorted = entries.sortedByDescending { it.substringBefore("|").toIntOrNull() ?: 0 }.take(5)
    prefs.edit().putString("fruithole_top5", sorted.joinToString(";")).apply()
}

private fun holeTop5(prefs: SharedPreferences): List<Pair<Int, String>> {
    val raw = prefs.getString("fruithole_top5", "") ?: ""
    return raw.split(";").filter { it.isNotBlank() }.map {
        (it.substringBefore("|").toIntOrNull() ?: 0) to it.substringAfter("|", "")
    }
}

private fun holeZeitFmt(sek: Int): String {
    val h = sek / 3600
    val m = (sek % 3600) / 60
    return if (h > 0) "${h}h ${m}min" else if (m > 0) "${m}min" else "${sek}s"
}

// ————— Entities —————

private class HoleItem(
    var x: Float,
    var y: Float,
    var vy: Float,
    var vx: Float,
    var rot: Float,
    var rotSpeed: Float,
    val emoji: String,
    val kind: HoleKind,
    var glow: Float = 0f,
) {
    var swallowing = false
    var swallow = 0f
    var sx = 0f
    var sy = 0f
    var bounced = false
}

private class HolePop(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float,
    val bad: Boolean,
    val big: Boolean = false,
)

private class HoleSpark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
)

private class HoleWorld {
    var w = 0f
    var h = 0f
    var initialized = false
    val items = ArrayList<HoleItem>()
    val pops = ArrayList<HolePop>()
    val sparks = ArrayList<HoleSpark>()
    val bgStars = ArrayList<Triple<Float, Float, Float>>()
    var holeX = 0f
    var holeTargetX = 0f
    var holeVx = 0f
    var holeR = 0f
    var spawnTimer = 0.8f
    var flash = 0f
    var shake = 0f
    var time = 0f
    var runTime = 0f
    var wind = 0f
    var feverFx = 0f
    var sogFx = 0f
    var rng: Random = Random.Default

    // HUD-sichtbare Zustände (Compose-States, damit die Anzeige live folgt)
    var eaten by mutableIntStateOf(0)
    var defusedRun by mutableIntStateOf(0)
    var goldRun by mutableIntStateOf(0)
    var powerupsRun by mutableIntStateOf(0)
    var bestComboRun by mutableIntStateOf(0)
    var slowTimer by mutableFloatStateOf(0f)
    var magnetTimer by mutableFloatStateOf(0f)
    var rainbowTimer by mutableFloatStateOf(0f)
    var fever by mutableStateOf(false)
}

@Composable
fun FruitHoleGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }

    // Navigation innerhalb des Spiels
    var screen by remember { mutableStateOf(HoleScreen.MENU) }
    var mode by remember {
        mutableStateOf(
            runCatching { HoleMode.valueOf(prefs.getString("fruithole_last_mode", "KLASSIK") ?: "KLASSIK") }
                .getOrDefault(HoleMode.KLASSIK)
        )
    }
    var daily by remember { mutableStateOf(false) }
    var worldIdx by remember { mutableIntStateOf(0) }

    // Einstellungen (persistiert)
    var haptikOn by remember { mutableStateOf(prefs.getBoolean("fruithole_set_haptik", true)) }
    var fxReduziert by remember { mutableStateOf(prefs.getBoolean("fruithole_set_fx", false)) }
    var sensIdx by remember { mutableIntStateOf(prefs.getInt("fruithole_set_sens", 1).coerceIn(0, 2)) }
    var direktSteuerung by remember { mutableStateOf(prefs.getBoolean("fruithole_set_direkt", false)) }

    // Runden-Zustand
    var score by remember { mutableIntStateOf(0) }
    var shownScore by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var level by remember { mutableIntStateOf(1) }
    var combo by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var countdown by remember { mutableFloatStateOf(0f) }
    var timeLeft by remember { mutableFloatStateOf(60f) }
    var runRecord by remember { mutableIntStateOf(0) }
    var recordBroken by remember { mutableStateOf(false) }
    var showRecordBanner by remember { mutableStateOf(false) }
    var missionDone by remember { mutableStateOf(BooleanArray(3)) }

    // Overlays
    var showSettings by remember { mutableStateOf(false) }
    var showRestart by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(!prefs.getBoolean("fruithole_help_seen", false)) }
    val achToasts = remember { mutableStateListOf<String>() }

    var xp by remember { mutableIntStateOf(prefs.getInt("fruithole_xp", 0)) }

    val world = remember { HoleWorld() }
    val emojiPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true } }
    val popPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true } }

    val multiplier = if (combo >= 8) 3 else if (combo >= 4) 2 else 1

    fun buzz(type: HapticFeedbackType) {
        if (haptikOn) haptic.performHapticFeedback(type)
    }

    fun unlock(id: String) {
        if (prefs.getBoolean("fruithole_ach_$id", false)) return
        prefs.edit().putBoolean("fruithole_ach_$id", true).apply()
        val a = HoleAchs.firstOrNull { it.id == id } ?: return
        achToasts.add("${a.emoji} Erfolg: ${a.title}")
        buzz(HapticFeedbackType.LongPress)
    }

    fun hsKey(m: HoleMode, isDaily: Boolean): String = when {
        m == HoleMode.RUSH -> "fruithole_rush_highscore"
        m == HoleMode.WELTEN -> "fruithole_world_highscore"
        isDaily -> "fruithole_daily_hs_${LocalDate.now().toEpochDay()}"
        else -> "fruithole_highscore"
    }

    fun modeLabel(): String = when {
        mode == HoleMode.RUSH -> "Rush Hour"
        mode == HoleMode.WELTEN -> HoleWorlds[worldIdx].name
        daily -> "Daily-Challenge"
        else -> "Klassik"
    }

    fun startRun(m: HoleMode, isDaily: Boolean, wIdx: Int) {
        mode = m
        daily = isDaily
        worldIdx = wIdx
        prefs.edit().putString("fruithole_last_mode", m.name).apply()
        world.items.clear()
        world.pops.clear()
        world.sparks.clear()
        world.eaten = 0
        world.defusedRun = 0
        world.goldRun = 0
        world.powerupsRun = 0
        world.bestComboRun = 0
        world.slowTimer = 0f
        world.magnetTimer = 0f
        world.rainbowTimer = 0f
        world.fever = false
        world.spawnTimer = 0.8f
        world.flash = 0f
        world.shake = 0f
        world.runTime = 0f
        world.wind = 0f
        world.holeVx = 0f
        world.feverFx = 0f
        world.sogFx = 0f
        world.rng = if (isDaily) Random(LocalDate.now().toEpochDay()) else Random.Default
        if (world.initialized) {
            world.holeR = world.w * 0.13f
            world.holeX = world.w / 2f
            world.holeTargetX = world.w / 2f
        }
        score = 0
        shownScore = 0
        lives = 3
        level = 1
        combo = 0
        timeLeft = 60f
        missionDone = BooleanArray(3) { prefs.getBoolean("fruithole_w${wIdx}_m$it", false) }
        runRecord = prefs.getInt(hsKey(m, isDaily), 0)
        recordBroken = false
        showRecordBanner = false
        newRecord = false
        gameOver = false
        paused = false
        countdown = 3f
        restartKey++
        screen = HoleScreen.PLAY
    }

    fun finishRun() {
        if (gameOver) return
        val key = hsKey(mode, daily)
        val hs = prefs.getInt(key, 0)
        if (score > hs) {
            newRecord = true
            prefs.edit().putInt(key, score).apply()
        }
        val e = prefs.edit()
        e.putInt("fruithole_stat_runden", prefs.getInt("fruithole_stat_runden", 0) + 1)
        e.putInt("fruithole_stat_fruechte", prefs.getInt("fruithole_stat_fruechte", 0) + world.eaten)
        if (world.bestComboRun > prefs.getInt("fruithole_stat_combo", 0)) e.putInt("fruithole_stat_combo", world.bestComboRun)
        e.putInt("fruithole_stat_entschaerft", prefs.getInt("fruithole_stat_entschaerft", 0) + world.defusedRun)
        e.putInt("fruithole_stat_powerups", prefs.getInt("fruithole_stat_powerups", 0) + world.powerupsRun)
        if (level > prefs.getInt("fruithole_stat_level", 0)) e.putInt("fruithole_stat_level", level)
        e.putInt("fruithole_stat_zeit", prefs.getInt("fruithole_stat_zeit", 0) + world.runTime.toInt())
        e.apply()
        val d = LocalDate.now()
        holeAddTopScore(prefs, score, "${d.dayOfMonth}.${d.monthValue}. · ${modeLabel()}")
        val lvlVorher = holeLevelFromXp(xp).first
        xp += score / 10
        prefs.edit().putInt("fruithole_xp", xp).apply()
        val lvlNachher = holeLevelFromXp(xp).first
        if (lvlNachher > lvlVorher) achToasts.add("⬆️ Spieler-Level $lvlNachher erreicht!")
        if (mode == HoleMode.RUSH && score >= 500) unlock("rush500")
        buzz(HapticFeedbackType.LongPress)
        gameOver = true
    }

    // Frucht/Gold gefressen: Combo, Multiplikatoren, Level-Aufstieg
    fun essen(basis: Int, px: Float) {
        combo += 1
        if (combo > world.bestComboRun) world.bestComboRun = combo
        if (combo >= 12 && !world.fever) {
            world.fever = true
            world.pops.add(HolePop(world.w * 0.5f, world.h * 0.30f, "🔥 Fieber! Alles ×2", 1.4f, bad = false, big = true))
            unlock("fieber")
        }
        if (combo >= 20) unlock("combo20")
        val mult = if (combo >= 8) 3 else if (combo >= 4) 2 else 1
        var pts = basis * mult
        if (world.rainbowTimer > 0f) pts *= 2
        if (world.fever) pts *= 2
        score += pts
        if (score >= 1000) unlock("punkte1000")
        world.eaten += 1
        if (world.eaten >= 100) unlock("vielfrass")
        buzz(HapticFeedbackType.TextHandleMove)
        val floorY = world.h - world.w * 0.10f
        world.pops.add(HolePop(px, floorY - world.holeR * 1.5f, "+$pts", 0.9f, bad = false))
        if (mode != HoleMode.RUSH) {
            if (world.eaten % 12 == 0) {
                level += 1
                world.pops.add(HolePop(world.w * 0.5f, world.h * 0.32f, "Level $level", 1.5f, bad = false, big = true))
                if (mode == HoleMode.KLASSIK && (level - 1) % 3 == 0) {
                    val th = HoleThemes[((level - 1) / 3) % 3]
                    world.pops.add(HolePop(world.w * 0.5f, world.h * 0.40f, "Neues Gebiet: ${th.name}", 1.6f, bad = false))
                }
                if (level >= 10 && mode == HoleMode.KLASSIK) unlock("level10")
            }
            if (world.eaten % 8 == 0) world.holeR = min(world.holeR + world.w * 0.010f, world.w * 0.19f)
        } else {
            if (world.eaten % 10 == 0) world.holeR = min(world.holeR + world.w * 0.008f, world.w * 0.19f)
        }
    }

    fun collectFx(x: Float, y: Float, col: Color) {
        if (fxReduziert) return
        repeat(8) {
            world.sparks.add(
                HoleSpark(
                    x, y,
                    (Random.nextFloat() * 2f - 1f) * world.w * 0.20f,
                    -(Random.nextFloat() * 0.6f + 0.2f) * world.w * 0.25f,
                    0.4f, 0.4f, col, 3f + Random.nextFloat() * 4f,
                )
            )
        }
    }

    // Auto-Pause bei App-Wechsel
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && screen == HoleScreen.PLAY && !gameOver) {
                paused = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // System-Back: erst Overlays, dann Pause, dann zurück
    BackHandler {
        when {
            showSettings -> showSettings = false
            showHelp -> {
                showHelp = false
                prefs.edit().putBoolean("fruithole_help_seen", true).apply()
            }
            screen == HoleScreen.PLAY && showRestart -> showRestart = false
            screen == HoleScreen.PLAY && gameOver -> screen = HoleScreen.MENU
            screen == HoleScreen.PLAY && !paused -> paused = true
            screen == HoleScreen.PLAY -> screen = HoleScreen.MENU
            screen != HoleScreen.MENU -> screen = HoleScreen.MENU
            else -> onBack()
        }
    }

    // Erfolgs-Toasts nacheinander abbauen
    LaunchedEffect(achToasts.firstOrNull()) {
        val cur = achToasts.firstOrNull()
        if (cur != null) {
            delay(2400)
            if (achToasts.isNotEmpty() && achToasts[0] == cur) achToasts.removeAt(0)
        }
    }
    LaunchedEffect(showRecordBanner) {
        if (showRecordBanner) {
            delay(2200)
            showRecordBanner = false
        }
    }

    // Game-Loop
    LaunchedEffect(screen, restartKey, gameOver) {
        if (screen != HoleScreen.PLAY || gameOver) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val rdt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.032f)
                last = now
                if (rdt > 0f && world.initialized && !gameOver && !paused) {
                    if (countdown > 0f) {
                        countdown = max(0f, countdown - rdt)
                    } else {
                        val sdt = rdt * (if (world.slowTimer > 0f) 0.5f else 1f)
                        val w = world.w
                        val h = world.h
                        val floorY = h - w * 0.10f
                        world.time += rdt
                        world.runTime += rdt

                        // Effekt-Timer laufen in Echtzeit ab
                        if (world.slowTimer > 0f) world.slowTimer = max(0f, world.slowTimer - rdt)
                        if (world.magnetTimer > 0f) world.magnetTimer = max(0f, world.magnetTimer - rdt)
                        if (world.rainbowTimer > 0f) world.rainbowTimer = max(0f, world.rainbowTimer - rdt)
                        world.flash = max(0f, world.flash - rdt * 2.4f)
                        world.shake = max(0f, world.shake - rdt * 3.2f)

                        // Rush: Countdown der Rundenzeit
                        if (mode == HoleMode.RUSH) {
                            timeLeft = max(0f, timeLeft - rdt)
                            if (timeLeft <= 0f) finishRun()
                        }

                        // Wüsten-Wind pendelt langsam
                        if (mode == HoleMode.WELTEN && worldIdx == 1) {
                            world.wind = sin(world.time * 0.35f) * w * 0.09f
                        }

                        // Loch zur Zielposition — im Schnee mit Trägheit (rutscht nach)
                        world.holeTargetX = world.holeTargetX.coerceIn(world.holeR, w - world.holeR)
                        if (mode == HoleMode.WELTEN && worldIdx == 2) {
                            world.holeVx += (world.holeTargetX - world.holeX) * 45f * rdt
                            world.holeVx *= max(0f, 1f - 3.5f * rdt)
                            world.holeX = (world.holeX + world.holeVx * rdt).coerceIn(world.holeR, w - world.holeR)
                        } else {
                            world.holeX += (world.holeTargetX - world.holeX) * min(1f, rdt * 14f)
                        }

                        // Score-Anzeige zählt animiert hoch
                        if (shownScore < score) {
                            shownScore = min(score, shownScore + max(1, ((score - shownScore) * 8f * rdt).toInt() + 1))
                        }
                        if (!recordBroken && runRecord > 0 && score > runRecord) {
                            recordBroken = true
                            showRecordBanner = true
                        }

                        // Spawnen (rng: bei Daily deterministischer Tages-Seed)
                        world.spawnTimer -= sdt
                        if (world.spawnTimer <= 0f) {
                            val basisInterval = if (mode == HoleMode.RUSH) 0.26f
                            else max(0.34f, 0.95f - (level - 1) * 0.07f)
                            world.spawnTimer = basisInterval * (0.75f + world.rng.nextFloat() * 0.5f)
                            val bombChance = if (mode == HoleMode.RUSH) 0.18f
                            else min(0.16f + (level - 1) * 0.02f, 0.30f)
                            val roll = world.rng.nextFloat()
                            var kind = when {
                                roll < bombChance -> HoleKind.BOMBE
                                roll < bombChance + 0.040f -> HoleKind.GOLD
                                roll < bombChance + 0.055f -> HoleKind.HERZ
                                roll < bombChance + 0.075f -> HoleKind.UHR
                                roll < bombChance + 0.095f -> HoleKind.MAGNET
                                roll < bombChance + 0.110f -> HoleKind.REGENBOGEN
                                else -> HoleKind.FRUCHT
                            }
                            if (mode == HoleMode.RUSH && kind == HoleKind.HERZ) kind = HoleKind.FRUCHT
                            val fruitSet = if (mode == HoleMode.WELTEN) HoleWorlds[worldIdx].fruits
                            else HoleThemes[((level - 1) / 3) % 3].fruits
                            val emoji = when (kind) {
                                HoleKind.BOMBE -> "💣"
                                HoleKind.GOLD -> "🌟"
                                HoleKind.HERZ -> "❤️"
                                HoleKind.UHR -> "⏰"
                                HoleKind.MAGNET -> "🌀"
                                HoleKind.REGENBOGEN -> "🌈"
                                else -> fruitSet[world.rng.nextInt(fruitSet.size)]
                            }
                            val margin = w * 0.08f
                            val space = mode == HoleMode.WELTEN && worldIdx == 3
                            val fallMul = if (space) 0.62f else 1f
                            world.items.add(
                                HoleItem(
                                    x = margin + world.rng.nextFloat() * (w - 2f * margin),
                                    y = -w * 0.10f,
                                    vy = h * (0.30f + world.rng.nextFloat() * 0.14f) *
                                        (1f + (level - 1) * 0.07f) * fallMul,
                                    vx = if (space) (world.rng.nextFloat() * 2f - 1f) * w * 0.10f else 0f,
                                    rot = world.rng.nextFloat() * 360f,
                                    rotSpeed = (world.rng.nextFloat() * 2f - 1f) * 120f,
                                    emoji = emoji,
                                    kind = kind,
                                    glow = if (kind == HoleKind.BOMBE) 0.9f else 0f,
                                )
                            )
                        }

                        // Entitäten updaten
                        val catchBand = world.holeR * 0.5f
                        val iter = world.items.iterator()
                        while (iter.hasNext()) {
                            val item = iter.next()
                            if (!item.swallowing) {
                                val prevY = item.y
                                if (mode == HoleMode.WELTEN) {
                                    when (worldIdx) {
                                        1 -> item.x = (item.x + world.wind * sdt).coerceIn(w * 0.03f, w * 0.97f)
                                        3 -> {
                                            item.x += item.vx * sdt
                                            val m = w * 0.05f
                                            if (item.x < m && item.vx < 0f) {
                                                if (!item.bounced) { item.vx = -item.vx; item.bounced = true } else item.x = m
                                            }
                                            if (item.x > w - m && item.vx > 0f) {
                                                if (!item.bounced) { item.vx = -item.vx; item.bounced = true } else item.x = w - m
                                            }
                                        }
                                    }
                                }
                                // Magnet zieht alles außer Bomben Richtung Loch
                                if (world.magnetTimer > 0f && item.kind != HoleKind.BOMBE && item.y > h * 0.45f) {
                                    item.x += (world.holeX - item.x) * min(1f, sdt * 3.2f)
                                }
                                item.y += item.vy * sdt
                                item.rot += item.rotSpeed * sdt
                                if (item.glow > 0f) item.glow -= sdt * 1.4f
                                // Segment- statt Punkt-Test: verhindert, dass schnelle
                                // Früchte auf hohem Level durch die Fang-Zone tunneln
                                val crossedZone = prevY <= floorY + catchBand && item.y >= floorY - catchBand
                                if (crossedZone && abs(item.x - world.holeX) < world.holeR * 0.9f) {
                                    item.swallowing = true
                                    item.sx = item.x
                                    item.sy = item.y
                                } else if (item.y > h + w * 0.10f) {
                                    iter.remove()
                                    if (item.kind != HoleKind.BOMBE) {
                                        world.pops.add(HolePop(item.x, floorY - 8f, "✕", 0.5f, bad = true))
                                        if (combo >= 4) {
                                            world.pops.add(HolePop(item.x, h - w * 0.16f, "Kombo verloren", 1.0f, bad = true))
                                        }
                                        combo = 0
                                        world.fever = false
                                    }
                                }
                            } else {
                                item.swallow += sdt * 4.2f
                                val t = min(1f, item.swallow)
                                item.x = item.sx + (world.holeX - item.sx) * t
                                item.y = item.sy + (floorY - item.sy) * t
                                if (item.swallow >= 1f) {
                                    iter.remove()
                                    when (item.kind) {
                                        HoleKind.BOMBE -> {
                                            combo = 0
                                            world.fever = false
                                            world.flash = 1f
                                            world.shake = 1f
                                            buzz(HapticFeedbackType.LongPress)
                                            if (mode == HoleMode.RUSH) {
                                                timeLeft = max(0f, timeLeft - 5f)
                                                world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "-5 s", 1.1f, bad = true))
                                            } else {
                                                lives -= 1
                                                world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "-1 ♥", 1.1f, bad = true))
                                                if (lives <= 0) finishRun()
                                            }
                                        }
                                        HoleKind.GOLD -> {
                                            world.goldRun += 1
                                            val gTotal = prefs.getInt("fruithole_stat_gold", 0) + 1
                                            prefs.edit().putInt("fruithole_stat_gold", gTotal).apply()
                                            if (gTotal >= 10) unlock("gold10")
                                            collectFx(world.holeX, floorY - world.holeR, HikariAmber)
                                            essen(50, item.sx)
                                        }
                                        HoleKind.HERZ -> {
                                            world.powerupsRun += 1
                                            if (world.powerupsRun >= 5) unlock("sammler")
                                            if (lives < 5) {
                                                lives += 1
                                                world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "+1 ♥", 1.0f, bad = false))
                                            } else {
                                                world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "♥ voll", 0.8f, bad = false))
                                            }
                                            collectFx(world.holeX, floorY - world.holeR, HikariDanger)
                                            buzz(HapticFeedbackType.TextHandleMove)
                                        }
                                        HoleKind.UHR -> {
                                            world.powerupsRun += 1
                                            if (world.powerupsRun >= 5) unlock("sammler")
                                            world.slowTimer = 5f
                                            world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "⏰ Slow-Mo", 1.0f, bad = false))
                                            collectFx(world.holeX, floorY - world.holeR, Color(0xFF22D3EE))
                                            buzz(HapticFeedbackType.TextHandleMove)
                                        }
                                        HoleKind.MAGNET -> {
                                            world.powerupsRun += 1
                                            if (world.powerupsRun >= 5) unlock("sammler")
                                            world.magnetTimer = 6f
                                            world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "🌀 Magnet", 1.0f, bad = false))
                                            collectFx(world.holeX, floorY - world.holeR, Color(0xFF60A5FA))
                                            buzz(HapticFeedbackType.TextHandleMove)
                                        }
                                        HoleKind.REGENBOGEN -> {
                                            world.powerupsRun += 1
                                            if (world.powerupsRun >= 5) unlock("sammler")
                                            world.rainbowTimer = 8f
                                            world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "🌈 Punkte ×2", 1.0f, bad = false))
                                            collectFx(world.holeX, floorY - world.holeR, Color(0xFFA78BFA))
                                            buzz(HapticFeedbackType.TextHandleMove)
                                        }
                                        else -> essen(10, item.sx)
                                    }
                                }
                            }
                        }

                        // Missionen der aktuellen Welt live prüfen
                        if (mode == HoleMode.WELTEN && !gameOver) {
                            val defs = HoleWorlds[worldIdx].missions
                            for (mi in defs.indices) {
                                if (missionDone[mi]) continue
                                val m = defs[mi]
                                val valNow = when (m.kind) {
                                    0 -> world.eaten
                                    1 -> world.bestComboRun
                                    2 -> world.defusedRun
                                    3 -> world.powerupsRun
                                    4 -> score
                                    else -> world.goldRun
                                }
                                if (valNow >= m.target) {
                                    missionDone = missionDone.copyOf().also { it[mi] = true }
                                    prefs.edit().putBoolean("fruithole_w${worldIdx}_m$mi", true).apply()
                                    achToasts.add("🎯 Mission erfüllt: ${m.desc}")
                                    if (missionDone.all { it }) unlock("weltmeister")
                                }
                            }
                        }

                        // Fieber-Funken am Loch-Rand
                        if (world.fever && !fxReduziert) {
                            world.feverFx -= rdt
                            if (world.feverFx <= 0f) {
                                world.feverFx = 0.08f
                                world.sparks.add(
                                    HoleSpark(
                                        world.holeX + (Random.nextFloat() * 2f - 1f) * world.holeR,
                                        floorY - 4f,
                                        (Random.nextFloat() * 2f - 1f) * 40f,
                                        -(80f + Random.nextFloat() * 140f),
                                        0.5f, 0.5f, Color(0xFFFFD54F), 3f + Random.nextFloat() * 3f,
                                    )
                                )
                            }
                        }
                        // Magnet-Sog-Partikel Richtung Loch
                        if (world.magnetTimer > 0f && !fxReduziert) {
                            world.sogFx -= rdt
                            if (world.sogFx <= 0f) {
                                world.sogFx = 0.06f
                                val seite = if (Random.nextBoolean()) 1f else -1f
                                val sx = world.holeX + seite * world.holeR * (1.6f + Random.nextFloat() * 1.2f)
                                world.sparks.add(
                                    HoleSpark(
                                        sx, floorY - Random.nextFloat() * h * 0.12f,
                                        (world.holeX - sx) * 2.6f,
                                        30f + Random.nextFloat() * 40f,
                                        0.35f, 0.35f, Color(0xFF60A5FA), 2.5f + Random.nextFloat() * 2f,
                                    )
                                )
                            }
                        }

                        // Partikel
                        val sIt = world.sparks.iterator()
                        while (sIt.hasNext()) {
                            val s = sIt.next()
                            s.x += s.vx * rdt
                            s.y += s.vy * rdt
                            s.life -= rdt
                            if (s.life <= 0f) sIt.remove()
                        }

                        // Punkte-Popups
                        val pit = world.pops.iterator()
                        while (pit.hasNext()) {
                            val p = pit.next()
                            p.life -= rdt
                            p.y -= rdt * h * 0.05f
                            if (p.life <= 0f) pit.remove()
                        }
                    }
                }
                tick++
            }
        }
    }

    // ————— UI —————
    // Eine Wurzel-Box, damit Toast/Einstellungen/Hilfe sauber über dem
    // aktiven Screen liegen statt als lose Geschwister-Nodes.
    Box(Modifier.fillMaxSize()) {

    Crossfade(screen, animationSpec = tween(220), label = "holeScreen") { scr ->
    when (scr) {
        HoleScreen.MENU -> HoleMenuInhalt(
            prefs = prefs,
            xp = xp,
            lastMode = mode,
            onBack = onBack,
            onKlassik = { startRun(HoleMode.KLASSIK, false, 0) },
            onDaily = { startRun(HoleMode.KLASSIK, true, 0) },
            onRush = { startRun(HoleMode.RUSH, false, 0) },
            onWelten = { screen = HoleScreen.WELTWAHL },
            onStats = { screen = HoleScreen.STATS },
            onErfolge = { screen = HoleScreen.ERFOLGE },
            onSettings = { showSettings = true },
            onHilfe = { showHelp = true },
        )

        HoleScreen.WELTWAHL -> HoleWeltwahlInhalt(
            prefs = prefs,
            onZurueck = { screen = HoleScreen.MENU },
            onStart = { idx -> startRun(HoleMode.WELTEN, false, idx) },
        )

        HoleScreen.STATS -> HoleStatsInhalt(prefs = prefs, xp = xp, onZurueck = { screen = HoleScreen.MENU })

        HoleScreen.ERFOLGE -> HoleErfolgeInhalt(prefs = prefs, onZurueck = { screen = HoleScreen.MENU })

        HoleScreen.PLAY -> Column(Modifier.fillMaxSize().background(HikariBg)) {
            // Kopf: Pause-Chip · Modus · Punkte-Pille
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                GxIconChip(if (gameOver) "←" else "⏸", size = 44.dp) {
                    if (!gameOver) paused = true else screen = HoleScreen.MENU
                }
                Text(modeLabel(), fontSize = 18.sp, color = HoleAccent, fontWeight = FontWeight.Black)
                GxHudPill("PKT", "$shownScore", accent = if (recordBroken) HoleAccent else null)
            }
            Text(
                when {
                    recordBroken -> "🏆 Neuer Rekord!"
                    runRecord > 0 && score < runRecord -> "Noch ${runRecord - score} bis Rekord"
                    runRecord > 0 -> "Rekord: $runRecord"
                    else -> "Erste Runde!"
                },
                fontSize = 11.sp,
                color = if (recordBroken) HoleAccent else HikariTextFaint,
                fontWeight = if (recordBroken) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(4.dp))

            // Leben / Timer · Kombo-Pille · Level/Welt
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                if (mode == HoleMode.RUSH) {
                    val restSek = ceil(timeLeft).toInt()
                    GxHudPill("ZEIT", "${restSek}s", accent = if (timeLeft <= 10f) HikariDanger else null)
                } else {
                    Row {
                        repeat(5) { i ->
                            Text(
                                "♥",
                                fontSize = 16.sp,
                                color = if (i < lives) HikariDanger else HikariTextFaint,
                                modifier = Modifier.padding(end = 3.dp),
                            )
                        }
                    }
                }
                if (world.fever || multiplier > 1) {
                    Text(
                        if (world.fever) "🔥 Fieber ×2" else "Kombo x$multiplier",
                        fontSize = 12.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (world.fever) Color(0xFFFFD54F) else HoleAccent)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                when (mode) {
                    HoleMode.WELTEN -> GxHudPill("🌍", HoleWorlds[worldIdx].name)
                    HoleMode.RUSH -> GxHudPill("🔥", "Dauerregen")
                    else -> GxHudPill("LVL", "$level")
                }
            }

            // Kombo-Fortschritt zum nächsten Multiplikator
            val nextAt = if (combo >= 8) 12 else if (combo >= 4) 8 else 4
            val prevAt = if (combo >= 8) 8 else if (combo >= 4) 4 else 0
            val comboFrac = if (combo >= 12) 1f else ((combo - prevAt).toFloat() / (nextAt - prevAt)).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(4.dp).clip(RoundedCornerShape(2.dp)).background(HikariSurfaceHigh)
            ) {
                Box(
                    Modifier.fillMaxWidth(comboFrac).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (world.fever) Color(0xFFFFD54F) else HoleAccent)
                )
            }

            // Aktive Welt-Mission
            if (mode == HoleMode.WELTEN) {
                val defs = HoleWorlds[worldIdx].missions
                val offen = defs.indices.firstOrNull { !missionDone[it] }
                if (offen != null) {
                    val m = defs[offen]
                    val valNow = when (m.kind) {
                        0 -> world.eaten
                        1 -> world.bestComboRun
                        2 -> world.defusedRun
                        3 -> world.powerupsRun
                        4 -> score
                        else -> world.goldRun
                    }
                    Text(
                        "🎯 ${m.desc} · ${min(valNow, m.target)}/${m.target}",
                        fontSize = 12.sp,
                        color = HikariTextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(restartKey) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                if (!paused && !gameOver && countdown <= 0f) {
                                    world.holeTargetX = if (direktSteuerung) change.position.x
                                    else world.holeTargetX + dragAmount * HoleSens[sensIdx]
                                }
                            }
                        }
                        .pointerInput(restartKey) {
                            // Bomben-Entschärfen per Fingertipp
                            detectTapGestures(onTap = { pos ->
                                if (paused || gameOver || countdown > 0f) return@detectTapGestures
                                val ww = size.width.toFloat()
                                val radius = ww * 0.09f
                                val bombe = world.items.firstOrNull {
                                    it.kind == HoleKind.BOMBE && !it.swallowing &&
                                        (it.x - pos.x) * (it.x - pos.x) + (it.y - pos.y) * (it.y - pos.y) < radius * radius
                                }
                                if (bombe != null) {
                                    world.items.remove(bombe)
                                    world.defusedRun += 1
                                    score += 5
                                    world.pops.add(HolePop(bombe.x, bombe.y, "Entschärft +5", 0.9f, bad = false))
                                    if (!fxReduziert) {
                                        repeat(10) {
                                            world.sparks.add(
                                                HoleSpark(
                                                    bombe.x, bombe.y,
                                                    (Random.nextFloat() * 2f - 1f) * ww * 0.22f,
                                                    (Random.nextFloat() * 2f - 1f) * ww * 0.22f,
                                                    0.35f, 0.35f, Color(0xFFB0BEC5), 2.5f + Random.nextFloat() * 3f,
                                                )
                                            )
                                        }
                                    }
                                    unlock("entschaerft")
                                    buzz(HapticFeedbackType.TextHandleMove)
                                }
                            })
                        }
                ) {
                    if (tick < 0) return@Canvas // liest tick → Canvas wird pro Frame neu gezeichnet
                    val w = size.width
                    val h = size.height
                    if (!world.initialized || world.w != w || world.h != h) {
                        world.w = w
                        world.h = h
                        world.holeR = w * 0.13f
                        world.holeX = w / 2f
                        world.holeTargetX = w / 2f
                        world.bgStars.clear()
                        repeat(40) {
                            world.bgStars.add(
                                Triple(Random.nextFloat() * w, Random.nextFloat() * h * 0.8f, 1f + Random.nextFloat() * 2f)
                            )
                        }
                        world.initialized = true
                    }
                    val floorY = h - w * 0.10f
                    val weltDef = if (mode == HoleMode.WELTEN) HoleWorlds[worldIdx] else null
                    val theme = HoleThemes[((level - 1) / 3) % 3]
                    val floorCol = weltDef?.floor ?: theme.floor
                    val glowCol = weltDef?.glow ?: theme.glow
                    val shakeX = if (world.shake > 0f) (Random.nextFloat() * 2f - 1f) * world.shake * 14f else 0f
                    val shakeY = if (world.shake > 0f) (Random.nextFloat() * 2f - 1f) * world.shake * 10f else 0f

                    translate(shakeX, shakeY) {
                        // Weltraum: Sternenhimmel
                        if (mode == HoleMode.WELTEN && worldIdx == 3) {
                            for ((sx, sy, sr) in world.bgStars) {
                                drawCircle(
                                    Color.White.copy(alpha = 0.25f + 0.20f * (0.5f + 0.5f * sin(world.time * 2f + sx))),
                                    sr,
                                    Offset(sx, sy),
                                )
                            }
                        }

                        // Boden-Schimmer im Thema der Welt / des Gebiets
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, floorCol),
                                startY = floorY - h * 0.12f,
                                endY = h,
                            ),
                            topLeft = Offset(0f, floorY - h * 0.12f),
                            size = Size(w, h - floorY + h * 0.12f),
                        )

                        // Rand-Farbe: Regenbogen > Fieber > Amber
                        val rimCol = when {
                            world.rainbowTimer > 0f -> Color.hsv((world.time * 140f) % 360f, 0.65f, 1f)
                            world.fever -> Color(0xFFFFD54F)
                            else -> HikariAmber
                        }

                        // Schwarzes Loch (Ellipse via Y-Scale)
                        val holeCenter = Offset(world.holeX, floorY)
                        scale(1f, 0.40f, pivot = holeCenter) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(glowCol.copy(alpha = 0.50f), Color.Transparent),
                                    center = holeCenter,
                                    radius = world.holeR * 1.7f,
                                ),
                                radius = world.holeR * 1.7f,
                                center = holeCenter,
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF2A1E06)),
                                    center = holeCenter,
                                    radius = world.holeR,
                                ),
                                radius = world.holeR,
                                center = holeCenter,
                            )
                            drawCircle(
                                color = rimCol.copy(alpha = 0.85f),
                                radius = world.holeR,
                                center = holeCenter,
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }

                        // Fallende Objekte
                        for (item in world.items) {
                            val s = if (item.swallowing) 1f - min(1f, item.swallow) else 1f
                            if (s <= 0.02f) continue
                            if (!fxReduziert) {
                                when (item.kind) {
                                    HoleKind.GOLD -> drawCircle(
                                        HikariAmber.copy(alpha = 0.30f * (0.6f + 0.4f * sin(world.time * 8f))),
                                        w * 0.065f * s, Offset(item.x, item.y),
                                    )
                                    HoleKind.BOMBE -> if (item.glow > 0f) drawCircle(
                                        HikariDanger.copy(alpha = 0.35f * item.glow.coerceIn(0f, 1f)),
                                        w * 0.075f, Offset(item.x, item.y),
                                    )
                                    HoleKind.REGENBOGEN -> drawCircle(
                                        Color.hsv((world.time * 200f) % 360f, 0.5f, 1f).copy(alpha = 0.22f),
                                        w * 0.06f * s, Offset(item.x, item.y),
                                    )
                                    else -> {}
                                }
                            }
                            emojiPaint.textSize = w * 0.10f * s
                            val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                            rotate(item.rot, pivot = Offset(item.x, item.y)) {
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawText(item.emoji, item.x, item.y - yOff, emojiPaint)
                                }
                            }
                        }

                        // Partikel (Fieber, Magnet-Sog, Einsammeln, Entschärfen)
                        for (s in world.sparks) {
                            drawCircle(
                                s.color.copy(alpha = (s.life / s.maxLife).coerceIn(0f, 1f)),
                                radius = s.size,
                                center = Offset(s.x, s.y),
                            )
                        }

                        // Wüsten-Wind-Anzeige
                        if (mode == HoleMode.WELTEN && worldIdx == 1 && abs(world.wind) > w * 0.02f) {
                            popPaint.textSize = w * 0.05f
                            popPaint.color = HikariTextMuted.toArgb()
                            drawIntoCanvas { c ->
                                val txt = if (world.wind > 0f) "💨 →" else "← 💨"
                                c.nativeCanvas.drawText(txt, w * 0.5f, h * 0.06f, popPaint)
                            }
                        }

                        // Punkte-Popups
                        for (p in world.pops) {
                            popPaint.textSize = if (p.big) w * 0.075f else w * 0.045f
                            val col = if (p.bad) HikariDanger else HikariAmber
                            popPaint.color = col.copy(alpha = p.life.coerceIn(0f, 1f)).toArgb()
                            drawIntoCanvas { c ->
                                c.nativeCanvas.drawText(p.text, p.x, p.y, popPaint)
                            }
                        }
                    }

                    // Slow-Mo: leichter Blaustich
                    if (world.slowTimer > 0f) {
                        drawRect(Color(0x1422D3EE))
                    }
                    // Rush: rote Puls-Vignette in den letzten 10 Sekunden
                    if (mode == HoleMode.RUSH && timeLeft <= 10f && !gameOver) {
                        drawRect(HikariDanger.copy(alpha = 0.06f + 0.06f * (0.5f + 0.5f * sin(world.time * 8f))))
                    }
                    // Roter Flash bei Bombe
                    if (world.flash > 0f) {
                        drawRect(HikariDanger.copy(alpha = world.flash * 0.30f))
                    }
                }

                // Aktive Power-up-Ringe
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (world.slowTimer > 0f) GxProgressRing(world.slowTimer / 5f, Color(0xFF22D3EE), size = 38.dp) { Text("⏰", fontSize = 13.sp) }
                    if (world.magnetTimer > 0f) GxProgressRing(world.magnetTimer / 6f, Color(0xFF60A5FA), size = 38.dp) { Text("🌀", fontSize = 13.sp) }
                    if (world.rainbowTimer > 0f) GxProgressRing(world.rainbowTimer / 8f, HoleAccent, size = 38.dp) { Text("🌈", fontSize = 13.sp) }
                    if (world.fever) GxProgressRing(1f, Color(0xFFFFD54F), size = 38.dp) { Text("🔥", fontSize = 13.sp) }
                }

                // Rekord-Banner
                if (showRecordBanner) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp)) {
                        Text(
                            "🏆 Neuer Rekord!",
                            fontSize = 16.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                                .background(Brush.horizontalGradient(listOf(HoleAccent, Color(0xFFC4B5FD))))
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                }

                // Countdown nach Start / Pause
                if (countdown > 0f && !paused && !gameOver) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(110.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(2.dp, HoleAccent.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${ceil(countdown).toInt()}",
                                fontSize = 56.sp,
                                color = HoleAccent,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }

                // Pause-Overlay
                if (paused && !gameOver) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
                            .pointerInput(Unit) { detectTapGestures { } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier.padding(28.dp)
                                .widthIn(max = 340.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF232326))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("⏸", fontSize = 30.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Pause", fontSize = 22.sp, color = HikariText, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GxHudPill("PKT", "$score", accent = HoleAccent)
                                GxHudPill("KOMBO", "${world.bestComboRun}")
                                GxHudPill("🍎", "${world.eaten}")
                            }
                            Spacer(Modifier.height(20.dp))
                            GxPrimaryButton("Weiter", HoleAccent, Modifier.fillMaxWidth()) {
                                paused = false
                                countdown = 3f
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GxGhostButton("Neustart", Modifier.weight(1f)) { showRestart = true }
                                GxGhostButton("Menü", Modifier.weight(1f)) {
                                    paused = false
                                    screen = HoleScreen.MENU
                                }
                            }
                        }
                    }
                }

                // Neustart-Bestätigung
                if (showRestart) {
                    GxConfirmDialog(
                        title = "Neu starten?",
                        text = "Der aktuelle Lauf geht verloren.",
                        confirmLabel = "Neu starten",
                        accent = HoleAccent,
                        danger = true,
                        onConfirm = { showRestart = false; startRun(mode, daily, worldIdx) },
                        onDismiss = { showRestart = false },
                    )
                }

                // Game-Over-Overlay mit Runden-Statistik
                if (gameOver) {
                    // Count-up: startet bei 0, LaunchedEffect setzt das Ziel nach dem ersten Frame
                    var countTarget by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) { countTarget = score }
                    val displayScore = gxAnimatedCount(countTarget, 900)
                    Column(
                        Modifier.fillMaxSize().background(Color(0xE0000000)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF232326))
                                .border(1.dp, HoleAccent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                                .padding(horizontal = 22.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (mode == HoleMode.RUSH) "⏱ Zeit um!" else "Game Over",
                                fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("$displayScore", fontSize = 44.sp, color = HoleAccent, fontWeight = FontWeight.Black)
                            if (newRecord) {
                                Text("🏆 Neuer Rekord!", fontSize = 15.sp, color = HoleAccent, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Rekord: ${max(runRecord, score)}", fontSize = 13.sp, color = HikariTextMuted)
                            }
                            Spacer(Modifier.height(16.dp))
                            val dSek = world.runTime.toInt()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GxStatTile("${dSek / 60}:${(dSek % 60).toString().padStart(2, '0')}", "Dauer", HoleAccent, Modifier.weight(1f))
                                GxStatTile("${world.eaten}", "Früchte", HoleAccent, Modifier.weight(1f))
                                GxStatTile("${world.bestComboRun}", "Kombo", HoleAccent, Modifier.weight(1f))
                                GxStatTile("${world.powerupsRun}", "Extras", HoleAccent, Modifier.weight(1f))
                            }
                            if (mode == HoleMode.WELTEN) {
                                Spacer(Modifier.height(12.dp))
                                HoleWorlds[worldIdx].missions.forEachIndexed { i, m ->
                                    Text(
                                        "${if (missionDone[i]) "✅" else "⬜"} ${m.desc}",
                                        fontSize = 12.sp,
                                        color = if (missionDone[i]) HikariText else HikariTextMuted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            GxPrimaryButton("Nochmal", HoleAccent, Modifier.fillMaxWidth()) {
                                startRun(mode, daily, worldIdx)
                            }
                            Spacer(Modifier.height(10.dp))
                            GxGhostButton("Zum Menü", Modifier.fillMaxWidth()) { screen = HoleScreen.MENU }
                        }
                    }
                }
            }
        }
    }
    }

    // Erfolgs-/Missions-Toast (über allen Screens)
    achToasts.firstOrNull()?.let { toast ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            GxAppear(0) {
                Text(
                    toast,
                    fontSize = 14.sp,
                    color = HikariText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 64.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF232326))
                        .border(1.dp, HoleAccent.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    // Einstellungen (Bottom-Sheet, aus dem Menü erreichbar)
    if (showSettings) {
        GxSheet("Einstellungen", HoleAccent, onClose = { showSettings = false }) {
            GxToggle("Vibration", "Haptisches Feedback beim Fangen & bei Treffern", HoleAccent, haptikOn) {
                haptikOn = it
                prefs.edit().putBoolean("fruithole_set_haptik", it).apply()
            }
            GxToggle("Reduzierte Effekte", "Weniger Partikel für flüssigeres Spiel", HoleAccent, fxReduziert) {
                fxReduziert = it
                prefs.edit().putBoolean("fruithole_set_fx", it).apply()
            }
            Spacer(Modifier.height(10.dp))
            Text("Steuerung", fontSize = 12.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            GxSegmented(listOf("Relativ", "Direkt"), if (direktSteuerung) 1 else 0, HoleAccent) { i ->
                direktSteuerung = i == 1
                prefs.edit().putBoolean("fruithole_set_direkt", direktSteuerung).apply()
            }
            Spacer(Modifier.height(14.dp))
            Text("Empfindlichkeit", fontSize = 12.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            GxSegmented(listOf("Sanft", "Normal", "Flink"), sensIdx, HoleAccent) { i ->
                sensIdx = i
                prefs.edit().putInt("fruithole_set_sens", i).apply()
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // Hilfe (Bottom-Sheet, beim ersten Start automatisch)
    if (showHelp) {
        GxSheet(
            "So funktioniert's", HoleAccent,
            onClose = {
                showHelp = false
                prefs.edit().putBoolean("fruithole_help_seen", true).apply()
            },
        ) {
            listOf(
                "🕳️ Zieh das Loch unter fallende Früchte",
                "💣 Bomben kosten ein Leben — oder tippe sie an: Entschärfen gibt +5",
                "🌟 +50 Punkte · ❤️ Extra-Leben · ⏰ Slow-Mo",
                "🌀 Magnet zieht Früchte an · 🌈 Punkte ×2",
                "🔥 Combo 12 zündet den Fieber-Modus: alles ×2",
                "🎯 Daily-Challenge: jeden Tag derselbe Frucht-Regen für alle Versuche",
            ).forEach {
                Text(it, fontSize = 13.sp, color = HikariTextMuted, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            GxPrimaryButton("Los geht's!", HoleAccent, Modifier.fillMaxWidth()) {
                showHelp = false
                prefs.edit().putBoolean("fruithole_help_seen", true).apply()
            }
        }
    }

    } // Ende Wurzel-Box
}

// ————— Menü —————

@Composable
private fun HoleMenuInhalt(
    prefs: SharedPreferences,
    xp: Int,
    lastMode: HoleMode,
    onBack: () -> Unit,
    onKlassik: () -> Unit,
    onDaily: () -> Unit,
    onRush: () -> Unit,
    onWelten: () -> Unit,
    onStats: () -> Unit,
    onErfolge: () -> Unit,
    onSettings: () -> Unit,
    onHilfe: () -> Unit,
) {
    val (lvl, prog) = holeLevelFromXp(xp)
    val hsKlassik = prefs.getInt("fruithole_highscore", 0)
    val hsDaily = prefs.getInt("fruithole_daily_hs_${LocalDate.now().toEpochDay()}", 0)
    val hsRush = prefs.getInt("fruithole_rush_highscore", 0)
    val sterneGesamt = (0..3).sumOf { wi -> (0..2).count { prefs.getBoolean("fruithole_w${wi}_m$it", false) } }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Hungry Hole", HoleAccent, onBack = onBack, right = { GxIconChip("?", onClick = onHilfe) })

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GxAppear(0) { GxLevelCard(lvl, "$xp XP", prog, HoleAccent) }
                GxAppear(1) {
                    GxModeCard(
                        emoji = "🕳️", title = "Klassik",
                        subtitle = "Leben, Level & wechselnde Gebiete",
                        accent = HoleAccent,
                        highlighted = lastMode == HoleMode.KLASSIK,
                        best = if (hsKlassik > 0) "Best: $hsKlassik" else null,
                        onClick = onKlassik,
                    )
                }
                GxAppear(2) {
                    GxModeCard(
                        emoji = "🎯", title = "Daily-Challenge",
                        subtitle = "Heute derselbe Frucht-Regen für alle Versuche",
                        accent = HoleAccent,
                        badge = "HEUTE",
                        best = if (hsDaily > 0) "Best heute: $hsDaily" else null,
                        onClick = onDaily,
                    )
                }
                GxAppear(3) {
                    GxModeCard(
                        emoji = "⏱️", title = "Rush Hour",
                        subtitle = "60 Sekunden Dauerregen — Bomben kosten Zeit",
                        accent = HoleAccent,
                        highlighted = lastMode == HoleMode.RUSH,
                        best = if (hsRush > 0) "Best: $hsRush" else null,
                        onClick = onRush,
                    )
                }
                GxAppear(4) {
                    GxModeCard(
                        emoji = "🌍", title = "Welten-Reise",
                        subtitle = "4 Welten mit eigenen Regeln & Missionen",
                        accent = HoleAccent,
                        highlighted = lastMode == HoleMode.WELTEN,
                        best = "$sterneGesamt/12 ★ gesammelt",
                        onClick = onWelten,
                    )
                }
                GxAppear(5) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                        GxSmallAction("🏅", "Erfolge", Modifier.weight(1f), onErfolge)
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f), onSettings)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ————— Weltwahl —————

@Composable
private fun HoleWeltwahlInhalt(
    prefs: SharedPreferences,
    onZurueck: () -> Unit,
    onStart: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Welten-Reise", HoleAccent, onBack = onZurueck)

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HoleWorlds.forEachIndexed { i, welt ->
                    val sterne = (0..2).count { prefs.getBoolean("fruithole_w${i}_m$it", false) }
                    val vorherSterne = if (i == 0) 3 else (0..2).count { prefs.getBoolean("fruithole_w${i - 1}_m$it", false) }
                    val offen = i == 0 || vorherSterne >= 2
                    GxAppear(i) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(welt.glow.copy(alpha = if (offen) 0.10f else 0.04f), HikariCardBg)
                                    )
                                )
                                .border(
                                    1.dp,
                                    if (offen) welt.glow.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(20.dp),
                                )
                                .gxPressable(enabled = offen) { onStart(i) }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                        .background(welt.glow.copy(alpha = if (offen) 0.20f else 0.08f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (offen) welt.emoji else "🔒", fontSize = 24.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        welt.name,
                                        fontSize = 16.sp,
                                        color = if (offen) HikariText else HikariTextFaint,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (offen) welt.mechanik else "🔒 ${HoleWorlds[i - 1].name}: 2 Missionen nötig",
                                        fontSize = 12.sp,
                                        color = HikariTextMuted,
                                    )
                                }
                                GxStarRow(sterne)
                            }
                            if (offen) {
                                Spacer(Modifier.height(10.dp))
                                welt.missions.forEachIndexed { mi, m ->
                                    val done = prefs.getBoolean("fruithole_w${i}_m$mi", false)
                                    Text(
                                        "${if (done) "✅" else "⬜"} ${m.desc}",
                                        fontSize = 12.sp,
                                        color = if (done) HikariText else HikariTextMuted,
                                        modifier = Modifier.padding(vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ————— Statistik —————

@Composable
private fun HoleStatsInhalt(prefs: SharedPreferences, xp: Int, onZurueck: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Statistik", HoleAccent, onBack = onZurueck)

            val (lvl, prog) = holeLevelFromXp(xp)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GxAppear(0) { GxLevelCard(lvl, "$xp XP", prog, HoleAccent) }
                GxAppear(1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_runden", 0)}", "Runden", HoleAccent, Modifier.weight(1f))
                        GxStatTile("${prefs.getInt("fruithole_stat_fruechte", 0)}", "Früchte", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_combo", 0)}", "Beste Kombo", HoleAccent, Modifier.weight(1f))
                        GxStatTile("${prefs.getInt("fruithole_stat_entschaerft", 0)}", "Entschärft", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_powerups", 0)}", "Power-ups", HoleAccent, Modifier.weight(1f))
                        GxStatTile("${prefs.getInt("fruithole_stat_gold", 0)}", "Gold-Früchte", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_level", 0)}", "Höchstes Level", HoleAccent, Modifier.weight(1f))
                        GxStatTile(holeZeitFmt(prefs.getInt("fruithole_stat_zeit", 0)), "Spielzeit", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(5) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HikariCardBg)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text("Top 5 Runden", fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val top = holeTop5(prefs)
                        if (top.isEmpty()) {
                            Text("Noch keine Runden — spiel eine!", fontSize = 13.sp, color = HikariTextFaint)
                        } else {
                            top.forEachIndexed { i, (pts, label) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    Arrangement.SpaceBetween,
                                    Alignment.CenterVertically,
                                ) {
                                    Text("${i + 1}. $label", fontSize = 13.sp, color = HikariTextMuted)
                                    Text("$pts", fontSize = 13.sp, color = HoleAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ————— Erfolge —————

@Composable
private fun HoleErfolgeInhalt(prefs: SharedPreferences, onZurueck: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Erfolge", HoleAccent, onBack = onZurueck)

            val freigeschaltet = HoleAchs.count { prefs.getBoolean("fruithole_ach_${it.id}", false) }
            Text(
                "$freigeschaltet von ${HoleAchs.size} freigeschaltet",
                fontSize = 13.sp,
                color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HoleAchs.forEachIndexed { i, a ->
                    val done = prefs.getBoolean("fruithole_ach_${a.id}", false)
                    GxAppear(i) { GxAchRow(a.emoji, a.title, a.desc, HoleAccent, done) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

