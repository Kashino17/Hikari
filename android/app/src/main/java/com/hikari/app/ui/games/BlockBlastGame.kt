package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ————— Daten —————

private val BbColors = listOf(
    Color(0xFFFBBF24), // Amber
    Color(0xFF60A5FA), // Blau
    Color(0xFFEC4899), // Pink
    Color(0xFF4ADE80), // Grün
    Color(0xFF22D3EE), // Cyan
    Color(0xFFA78BFA), // Lila
)

// Grid-Zellwerte: -1 leer, 0..5 Steinfarben, 6 Hindernis (Abenteuer), 7 Juwel-Block
private const val BbObstacle = 6
private const val BbJewel = 7

private fun bbCellColor(v: Int): Color = when {
    v in BbColors.indices -> BbColors[v]
    v == BbObstacle -> Color(0xFF6B7280)
    else -> Color(0xFF34D399)
}

// Zellen als (Zeile, Spalte) + Gewicht: sperrige Formen kommen seltener
private class BbShapeDef(val cells: List<Pair<Int, Int>>, val weight: Float)

private val BbShapeDefs: List<BbShapeDef> = listOf(
    BbShapeDef(listOf(0 to 0), 2.2f),
    // Linien horizontal
    BbShapeDef(listOf(0 to 0, 0 to 1), 3f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2), 3f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3), 2f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4), 0.8f),
    // Linien vertikal
    BbShapeDef(listOf(0 to 0, 1 to 0), 3f),
    BbShapeDef(listOf(0 to 0, 1 to 0, 2 to 0), 3f),
    BbShapeDef(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), 2f),
    BbShapeDef(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0), 0.8f),
    // Quadrate + Rechtecke
    BbShapeDef(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), 2.5f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1, 2 to 2), 0.7f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2), 0.9f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1), 0.9f),
    // L-Formen (4 Rotationen)
    BbShapeDef(listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1), 1.8f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0), 1.8f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), 1.8f),
    BbShapeDef(listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2), 1.8f),
    // T-Form
    BbShapeDef(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1), 1.8f),
    // S / Z
    BbShapeDef(listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1), 1.6f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2), 1.6f),
    // Plus / Kreuz
    BbShapeDef(listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2, 2 to 1), 1.0f),
    // 3er-Ecken (4 Rotationen)
    BbShapeDef(listOf(0 to 0, 0 to 1, 1 to 0), 1.8f),
    BbShapeDef(listOf(0 to 0, 0 to 1, 1 to 1), 1.8f),
    BbShapeDef(listOf(0 to 0, 1 to 0, 1 to 1), 1.8f),
    BbShapeDef(listOf(0 to 1, 1 to 0, 1 to 1), 1.8f),
)

private val BbTotalWeight = BbShapeDefs.map { it.weight }.sum()

private fun bbRandomShapeIndex(rng: Random): Int {
    var t = rng.nextFloat() * BbTotalWeight
    for (i in BbShapeDefs.indices) {
        t -= BbShapeDefs[i].weight
        if (t <= 0f) return i
    }
    return 0
}

private class BbPiece(val shapeIndex: Int, val colorIndex: Int, val id: Int) {
    val cells: List<Pair<Int, Int>> = BbShapeDefs[shapeIndex].cells
    val rows = cells.maxOf { it.first } + 1
    val cols = cells.maxOf { it.second } + 1
}

private class BbClearFx(val cells: List<Triple<Int, Int, Int>>, val key: Int)
private class BbReturnFx(val piece: BbPiece, val slot: Int, val from: Offset)
private class BbParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Color, val r: Float,
)

private class BbFxHolder {
    var t = 0f
    val parts = ArrayList<BbParticle>()
}

private var bbNextId = 0

private fun bbRandomPiece(rng: Random): BbPiece =
    BbPiece(bbRandomShapeIndex(rng), rng.nextInt(BbColors.size), bbNextId++)

private fun bbCanPlace(grid: IntArray, piece: BbPiece, row: Int, col: Int): Boolean {
    for ((r, c) in piece.cells) {
        val rr = row + r
        val cc = col + c
        if (rr !in 0..7 || cc !in 0..7) return false
        if (grid[rr * 8 + cc] >= 0) return false
    }
    return true
}

private fun bbFitsAnywhere(grid: IntArray, piece: BbPiece): Boolean {
    for (r in 0..7) for (c in 0..7) if (bbCanPlace(grid, piece, r, c)) return true
    return false
}

private fun bbFullLines(grid: IntArray): Pair<List<Int>, List<Int>> {
    val rows = (0..7).filter { r -> (0..7).all { c -> grid[r * 8 + c] >= 0 } }
    val cols = (0..7).filter { c -> (0..7).all { r -> grid[r * 8 + c] >= 0 } }
    return rows to cols
}

// ————— Modi / Screens —————

private enum class BbMode(val key: String, val label: String) {
    CLASSIC("classic", "Klassisch"),
    DAILY("daily", "Daily-Challenge"),
    ADVENTURE("adventure", "Abenteuer"),
    TIME("time", "Zeitrausch"),
}

private enum class BbScreen { MENU, GAME, LEVELS, STATS, ACH }

// ————— Abenteuer-Level —————

private class BbLevelDef(val layout: List<String>, val goalLines: Int, val moves: Int) {
    val hasJewels = layout.any { it.contains('J') }
}

private val BbLevels: List<BbLevelDef> = listOf(
    BbLevelDef(listOf("........", "........", "........", "........", "........", "........", "........", "........"), 2, 10),
    BbLevelDef(listOf("........", "........", "........", "........", "........", "........", "###..###", "###..###"), 2, 8),
    BbLevelDef(listOf("........", "........", "........", "........", "........", "..J..J..", "##.##.##", "#..##..#"), 0, 9),
    BbLevelDef(listOf("########", "#......#", "#......#", "#......#", "#......#", "#......#", "#......#", "########"), 6, 10),
    BbLevelDef(listOf("........", "........", "..#.#...", ".J.#.J..", "..#.#...", ".#.#.#..", "........", "........"), 0, 10),
    BbLevelDef(listOf("#..#..#.", "#..#..#.", "#..#..#.", "#..#..#.", "#..#..#.", "#..#..#.", "........", "........"), 5, 12),
    BbLevelDef(listOf("#.......", ".#......", "..#.....", "...#....", "....#...", ".....#..", "......#.", ".......#"), 4, 12),
    BbLevelDef(listOf("J......J", "........", "........", "........", "........", "........", "........", "J......J"), 0, 14),
    BbLevelDef(listOf("........", "........", "...##...", "..####..", "..####..", "...##...", "........", "........"), 4, 11),
    BbLevelDef(listOf("##....##", "##....##", "##.....#", "#.....##", "##....##", "##....##", "#......#", "##....##"), 6, 12),
    BbLevelDef(listOf("........", "........", "........", "J.J.J.J.", "........", "........", "........", "........"), 0, 12),
    BbLevelDef(listOf("........", ".######.", ".#....#.", ".#.JJ.#.", ".#.JJ.#.", ".#....#.", ".######.", "........"), 0, 14),
    BbLevelDef(listOf("####....", "....####", "####....", "....####", "####....", "....####", "........", "........"), 6, 14),
    BbLevelDef(listOf("J.......", ".J......", "..J.....", "...J....", "....J...", ".....J..", "......J.", ".......J"), 0, 18),
    BbLevelDef(listOf("...##...", "...##...", "J..##..J", "...##...", "...##...", "J..##..J", "...##...", "...##..."), 0, 16),
    BbLevelDef(listOf("#.......", "##......", "###.....", "####....", "#####...", "######..", "#######.", "........"), 7, 16),
    BbLevelDef(listOf("........", "..JJJJ..", "..J..J..", "..J..J..", "..JJJJ..", "........", "........", "........"), 0, 18),
    BbLevelDef(listOf("#.#.#.#.", ".#.#.#.#", "#.#.#.#.", ".#.#.#.#", "........", "........", "........", "........"), 8, 18),
    BbLevelDef(listOf(".#.##.#.", "J......J", ".#....#.", "........", ".#....#.", "J......J", ".#.##.#.", "........"), 0, 16),
    BbLevelDef(listOf("#.J..J.#", ".#....#.", "..#..#..", "...##...", "...##...", "..#..#..", ".#....#.", "#.J..J.#"), 0, 20),
)

private fun bbParseLayout(rows: List<String>): IntArray {
    val g = IntArray(64) { -1 }
    for (r in 0..7) for (c in 0..7) {
        g[r * 8 + c] = when (rows[r][c]) {
            '#' -> BbObstacle
            'J' -> BbJewel
            else -> -1
        }
    }
    return g
}

// ————— Achievements —————

private class BbAchievement(val id: String, val icon: String, val title: String, val desc: String)

private val BbAchievements = listOf(
    BbAchievement("lines3", "🎯", "Dreifach!", "Lösche 3 Linien mit einem Zug"),
    BbAchievement("combo5", "🔥", "Combo-Meister", "Erreiche Combo ×5"),
    BbAchievement("fever", "⚡", "Fieber!", "Löse den Fieber-Modus aus"),
    BbAchievement("perfect", "✨", "Blitzblank", "Räume das Feld komplett leer"),
    BbAchievement("score5k", "🏆", "Punktejäger", "5000 Punkte in einer Runde"),
    BbAchievement("games10", "🎮", "Stammspieler", "Spiele 10 Runden"),
    BbAchievement("hammer", "🔨", "Handwerker", "Setze den Hammer ein"),
    BbAchievement("wirbel", "🌀", "Frischer Wind", "Setze den Wirbel ein"),
    BbAchievement("level5", "🌟", "Aufsteiger", "Erreiche Spielerlevel 5"),
    BbAchievement("rush1500", "⏱️", "Zeitrausch-Profi", "1500 Punkte im Zeitrausch"),
    BbAchievement("adv5", "🗺️", "Abenteurer", "Schaffe 5 Abenteuer-Level"),
    BbAchievement("adv20", "👑", "Weltenbummler", "Schaffe alle 20 Abenteuer-Level"),
)

// ————— XP / Spielerlevel —————

private fun bbXpForLevel(lvl: Int): Int = 50 * lvl * (lvl - 1)

private fun bbLevelFromXp(xp: Int): Int {
    var lvl = 1
    while (bbXpForLevel(lvl + 1) <= xp) lvl++
    return lvl
}

// ————— Spielstand (Autosave + Undo-Snapshot) —————

private class BbSave(
    val grid: IntArray,
    val tray: List<BbPiece?>,
    val score: Int,
    val combo: Int,
    val hammer: Int,
    val wirbel: Int,
    val hProg: Int,
    val wProg: Int,
    val lines: Int,
    val bestCombo: Int,
    val moves: Int,
    val playSec: Int,
    val undoUsed: Boolean,
)

private fun bbSerialize(s: BbSave): String {
    val g = StringBuilder(64)
    for (v in s.grid) g.append(if (v < 0) '.' else ('0' + v))
    val t = s.tray.joinToString("~") { p -> if (p == null) "-" else "${p.shapeIndex}.${p.colorIndex}" }
    return listOf(
        g.toString(), t, s.score, s.combo, s.hammer, s.wirbel, s.hProg, s.wProg,
        s.lines, s.bestCombo, s.moves, s.playSec, if (s.undoUsed) 1 else 0,
    ).joinToString("|")
}

private fun bbParseSave(str: String?): BbSave? {
    if (str == null) return null
    return try {
        val p = str.split("|")
        if (p.size != 13 || p[0].length != 64) return null
        val grid = IntArray(64) { i -> if (p[0][i] == '.') -1 else p[0][i] - '0' }
        val tray = p[1].split("~").map { t ->
            if (t == "-") null else {
                val (si, ci) = t.split(".").map { it.toInt() }
                if (si !in BbShapeDefs.indices || ci !in BbColors.indices) return null
                BbPiece(si, ci, bbNextId++)
            }
        }
        if (tray.size != 3) return null
        BbSave(
            grid, tray, p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(),
            p[6].toInt(), p[7].toInt(), p[8].toInt(), p[9].toInt(), p[10].toInt(),
            p[11].toInt(), p[12] == "1",
        )
    } catch (e: Exception) {
        null
    }
}

private fun bbFmtTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

private fun bbFmtDuration(sec: Int): String =
    if (sec >= 3600) "${sec / 3600}h ${(sec % 3600) / 60}m" else "${sec / 60}m"

private fun bbNewRng(mode: BbMode): Random =
    if (mode == BbMode.DAILY) Random(LocalDate.now().toEpochDay()) else Random(System.nanoTime())

// ————— Root —————

@Composable
fun BlockBlastGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }

    var screen by remember { mutableStateOf(BbScreen.MENU) }
    var mode by remember {
        mutableStateOf(
            BbMode.entries.firstOrNull { it.key == prefs.getString("blockblast_last_mode", "classic") }
                ?: BbMode.CLASSIC
        )
    }
    var advLevel by remember { mutableIntStateOf(0) }
    var resume by remember { mutableStateOf<BbSave?>(null) }
    var runToken by remember { mutableIntStateOf(0) }

    var haptics by remember { mutableStateOf(prefs.getBoolean("blockblast_haptics", true)) }
    var particlesOn by remember { mutableStateOf(prefs.getBoolean("blockblast_particles", true)) }
    val setHaptics: (Boolean) -> Unit = { haptics = it; prefs.edit().putBoolean("blockblast_haptics", it).apply() }
    val setParticles: (Boolean) -> Unit = { particlesOn = it; prefs.edit().putBoolean("blockblast_particles", it).apply() }

    fun startGame(m: BbMode, res: BbSave? = null, level: Int = 0) {
        mode = m
        advLevel = level
        resume = res
        runToken++
        prefs.edit().putString("blockblast_last_mode", m.key).apply()
        screen = BbScreen.GAME
    }

    when (screen) {
        BbScreen.MENU -> BbMenu(
            prefs = prefs,
            haptics = haptics,
            particlesOn = particlesOn,
            onHaptics = setHaptics,
            onParticles = setParticles,
            lastMode = mode,
            onBack = onBack,
            onStart = { m, res -> startGame(m, res) },
            onLevels = { screen = BbScreen.LEVELS },
            onStats = { screen = BbScreen.STATS },
            onAch = { screen = BbScreen.ACH },
        )
        BbScreen.LEVELS -> BbLevelSelect(
            prefs = prefs,
            onPlay = { i -> startGame(BbMode.ADVENTURE, level = i) },
            onBack = { screen = BbScreen.MENU },
        )
        BbScreen.STATS -> BbStatsScreen(prefs, onBack = { screen = BbScreen.MENU })
        BbScreen.ACH -> BbAchScreen(prefs, onBack = { screen = BbScreen.MENU })
        BbScreen.GAME -> key(mode, advLevel, runToken) {
            BbPlay(
                prefs = prefs,
                mode = mode,
                levelIndex = advLevel,
                resume = resume,
                haptics = haptics,
                particlesOn = particlesOn,
                onHaptics = setHaptics,
                onParticles = setParticles,
                onExit = { screen = if (mode == BbMode.ADVENTURE) BbScreen.LEVELS else BbScreen.MENU },
                onPlayLevel = { i -> startGame(BbMode.ADVENTURE, level = i) },
            )
        }
    }
}

// ————— Menü —————

@Composable
private fun BbMenu(
    prefs: SharedPreferences,
    haptics: Boolean,
    particlesOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    onParticles: (Boolean) -> Unit,
    lastMode: BbMode,
    onBack: () -> Unit,
    onStart: (BbMode, BbSave?) -> Unit,
    onLevels: () -> Unit,
    onStats: () -> Unit,
    onAch: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val save = remember { bbParseSave(prefs.getString("blockblast_save", null)) }
    val xp = remember { prefs.getInt("blockblast_xp", 0) }
    val lvl = bbLevelFromXp(xp)
    val lvlPrev = bbXpForLevel(lvl)
    val lvlNext = bbXpForLevel(lvl + 1)
    val lvlFrac = ((xp - lvlPrev).toFloat() / (lvlNext - lvlPrev).coerceAtLeast(1)).coerceIn(0f, 1f)
    val classicBest = remember { prefs.getInt("blockblast_highscore", 0) }
    val timeBest = remember { prefs.getInt("blockblast_time_highscore", 0) }
    val dailyBest = remember { prefs.getInt("blockblast_daily_high_${LocalDate.now().toEpochDay()}", 0) }
    val advStars = remember { (BbLevels.indices).sumOf { prefs.getInt("blockblast_adv_stars_$it", 0) } }
    val advDone = remember { (BbLevels.indices).count { prefs.getInt("blockblast_adv_stars_$it", 0) > 0 } }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
                Text("Block Blast", fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showHelp = true }) { Text("?", color = HikariTextMuted, fontSize = 16.sp) }
            }

            // Spielerlevel-Karte
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HikariCardBg).padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Spielerlevel $lvl", fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Text("$xp / $lvlNext XP", fontSize = 11.sp, color = HikariTextMuted)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(HikariSurfaceHigh)) {
                    Box(
                        Modifier.fillMaxWidth(lvlFrac).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp)).background(HikariPrimary)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (save != null) {
                BbModeCard(
                    emoji = "▶️", title = "Weiterspielen",
                    subtitle = "Klassische Runde · ${save.score} Punkte",
                    highlighted = true,
                    onClick = { onStart(BbMode.CLASSIC, save) },
                )
                Spacer(Modifier.height(10.dp))
            }

            BbModeCard(
                emoji = "🧩", title = "Klassisch",
                subtitle = if (classicBest > 0) "Endlos-Puzzle · Best: $classicBest" else "Endlos-Puzzle — der Klassiker",
                highlighted = lastMode == BbMode.CLASSIC && save == null,
                onClick = { onStart(BbMode.CLASSIC, null) },
            )
            Spacer(Modifier.height(10.dp))
            BbModeCard(
                emoji = "📅", title = "Daily-Challenge",
                subtitle = if (dailyBest > 0) "Heutige Steinfolge · Best heute: $dailyBest" else "Jeden Tag dieselbe Steinfolge für alle Versuche",
                highlighted = lastMode == BbMode.DAILY,
                onClick = { onStart(BbMode.DAILY, null) },
            )
            Spacer(Modifier.height(10.dp))
            BbModeCard(
                emoji = "🗺️", title = "Abenteuer",
                subtitle = "$advDone/${BbLevels.size} Level · $advStars ⭐ gesammelt",
                highlighted = lastMode == BbMode.ADVENTURE,
                onClick = onLevels,
            )
            Spacer(Modifier.height(10.dp))
            BbModeCard(
                emoji = "⏱️", title = "Zeitrausch",
                subtitle = if (timeBest > 0) "120 Sekunden, Linien geben Zeit · Best: $timeBest" else "120 Sekunden — Linien geben Zeit zurück",
                highlighted = lastMode == BbMode.TIME,
                onClick = { onStart(BbMode.TIME, null) },
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                BbSmallButton("📊", "Statistik", Modifier.weight(1f), onStats)
                BbSmallButton("🏅", "Erfolge", Modifier.weight(1f), onAch)
                BbSmallButton("⚙️", "Optionen", Modifier.weight(1f)) { showSettings = true }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (showSettings) {
            BbSettingsOverlay(haptics, particlesOn, onHaptics, onParticles) { showSettings = false }
        }
        if (showHelp) {
            BbHelpOverlay { showHelp = false }
        }
    }
}

@Composable
private fun BbModeCard(emoji: String, title: String, subtitle: String, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .then(if (highlighted) Modifier.border(1.dp, HikariPrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = HikariTextMuted)
        }
        Text("›", fontSize = 20.sp, color = HikariTextFaint)
    }
}

@Composable
private fun BbSmallButton(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = HikariTextMuted)
    }
}

// ————— Overlays (Settings / Hilfe) —————

@Composable
private fun BbSettingsOverlay(
    haptics: Boolean,
    particlesOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    onParticles: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HikariCardBg)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
        ) {
            Text("Einstellungen", fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.width(260.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Vibration", fontSize = 14.sp, color = HikariText)
                Switch(
                    checked = haptics, onCheckedChange = onHaptics,
                    colors = SwitchDefaults.colors(checkedTrackColor = HikariPrimary, checkedThumbColor = Color.Black),
                )
            }
            Row(Modifier.width(260.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Partikel-Effekte", fontSize = 14.sp, color = HikariText)
                Switch(
                    checked = particlesOn, onCheckedChange = onParticles,
                    colors = SwitchDefaults.colors(checkedTrackColor = HikariPrimary, checkedThumbColor = Color.Black),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Fertig", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BbHelpOverlay(onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6000000))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HikariCardBg)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("So funktioniert's", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val lines = listOf(
                "🧩 Ziehe Steine aufs 8×8-Feld — volle Reihen und Spalten verschwinden.",
                "🔥 Mehrere Linien nacheinander = Combo. Ab Combo ×3 zündet der Fieber-Modus: 10 s doppelte Punkte!",
                "✨ Feld komplett leer geräumt = Perfect Clear (+300).",
                "🔨 Hammer: entfernt eine einzelne Zelle. Lädt sich über gelöschte Linien auf.",
                "🌀 Wirbel: würfelt deine drei Steine neu.",
                "↩️ Undo: einmal pro Runde den letzten Zug zurücknehmen.",
                "🗺️ Abenteuer: 20 Level mit eigenen Karten, Juwelen und Zuglimit.",
                "⏱️ Zeitrausch: 120 s — jede Linie gibt +5 s.",
            )
            for (l in lines) {
                Text(l, fontSize = 13.sp, color = HikariText, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Los geht's", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

// ————— Level-Auswahl —————

@Composable
private fun BbLevelSelect(prefs: SharedPreferences, onPlay: (Int) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val stars = remember { BbLevels.indices.map { prefs.getInt("blockblast_adv_stars_$it", 0) } }
    Column(Modifier.fillMaxSize().background(HikariBg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Abenteuer", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("${stars.sum()} ⭐", fontSize = 13.sp, color = HikariTextMuted)
        }
        Text(
            "Räume die Karte ab: Juwelen einsammeln oder Linien löschen — bevor die Züge ausgehen.",
            fontSize = 12.sp, color = HikariTextMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        for (row in 0 until (BbLevels.size + 3) / 4) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Arrangement.spacedBy(10.dp),
            ) {
                for (col in 0..3) {
                    val i = row * 4 + col
                    if (i >= BbLevels.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val unlocked = i == 0 || stars[i - 1] > 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (unlocked) HikariCardBg else HikariCardBg.copy(alpha = 0.5f))
                            .then(
                                if (unlocked) Modifier.clickable { onPlay(i) } else Modifier
                            )
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (unlocked) {
                            Text("${i + 1}", fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Row {
                                repeat(3) { s ->
                                    Text(
                                        "★", fontSize = 10.sp,
                                        color = if (s < stars[i]) HikariPrimary else HikariTextFaint,
                                    )
                                }
                            }
                        } else {
                            Text("🔒", fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("${i + 1}", fontSize = 10.sp, color = HikariTextFaint)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ————— Statistik —————

@Composable
private fun BbStatsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    BackHandler { onBack() }
    val xp = remember { prefs.getInt("blockblast_xp", 0) }
    val rows = remember {
        listOf(
            "Runden gespielt" to "${prefs.getInt("blockblast_games_played", 0)}",
            "Spielerlevel" to "${bbLevelFromXp(xp)} ($xp XP)",
            "Beste Combo" to "×${prefs.getInt("blockblast_best_combo", 0)}",
            "Linien gesamt" to "${prefs.getInt("blockblast_total_lines", 0)}",
            "Punkte gesamt" to "${prefs.getInt("blockblast_total_score", 0)}",
            "Perfect Clears" to "${prefs.getInt("blockblast_perfect_clears", 0)}",
            "Abenteuer-Sterne" to "${BbLevels.indices.sumOf { prefs.getInt("blockblast_adv_stars_$it", 0) }}",
            "Spielzeit" to bbFmtDuration(prefs.getInt("blockblast_playtime_sec", 0)),
        )
    }
    val history = remember {
        (prefs.getString("blockblast_history", "") ?: "")
            .split(";").filter { it.isNotBlank() }
            .mapNotNull { e ->
                val p = e.split(",")
                if (p.size == 2) p[0].toIntOrNull()?.let { s -> p[1].toLongOrNull()?.let { d -> s to d } } else null
            }
            .sortedByDescending { it.first }
    }
    Column(Modifier.fillMaxSize().background(HikariBg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Statistik", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(HikariCardBg).padding(16.dp),
        ) {
            for ((label, value) in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                    Text(label, fontSize = 13.sp, color = HikariTextMuted)
                    Text(value, fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Top 5 — Klassisch", fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                "Noch keine Runden gespielt.", fontSize = 12.sp, color = HikariTextFaint,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(HikariCardBg).padding(16.dp),
            ) {
                history.forEachIndexed { i, (score, epochDay) ->
                    val d = LocalDate.ofEpochDay(epochDay)
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween) {
                        Text("${i + 1}.", fontSize = 13.sp, color = HikariTextFaint)
                        Text("$score", fontSize = 13.sp, color = if (i == 0) HikariPrimary else HikariText, fontWeight = FontWeight.Bold)
                        Text("%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year), fontSize = 12.sp, color = HikariTextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ————— Erfolge —————

@Composable
private fun BbAchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    BackHandler { onBack() }
    val unlocked = remember { BbAchievements.associate { it.id to prefs.getBoolean("blockblast_ach_${it.id}", false) } }
    val count = unlocked.values.count { it }
    Column(Modifier.fillMaxSize().background(HikariBg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Erfolge", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("$count/${BbAchievements.size}", fontSize = 13.sp, color = HikariTextMuted)
        }
        for (a in BbAchievements) {
            val got = unlocked[a.id] == true
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (got) HikariCardBg else HikariCardBg.copy(alpha = 0.55f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.icon, fontSize = 22.sp, modifier = Modifier.graphicsLayer { alpha = if (got) 1f else 0.35f })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontSize = 14.sp, color = if (got) HikariText else HikariTextMuted, fontWeight = FontWeight.Bold)
                    Text(a.desc, fontSize = 11.sp, color = HikariTextFaint)
                }
                if (got) Text("✓", fontSize = 16.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ————— Das Spiel —————

@Composable
private fun BbPlay(
    prefs: SharedPreferences,
    mode: BbMode,
    levelIndex: Int,
    resume: BbSave?,
    haptics: Boolean,
    particlesOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    onParticles: (Boolean) -> Unit,
    onExit: () -> Unit,
    onPlayLevel: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val levelDef = if (mode == BbMode.ADVENTURE) BbLevels[levelIndex] else null

    fun buzz(t: HapticFeedbackType) {
        if (haptics) haptic.performHapticFeedback(t)
    }

    fun initialGrid(): IntArray =
        if (levelDef != null) bbParseLayout(levelDef.layout) else IntArray(64) { -1 }

    var rng by remember { mutableStateOf(bbNewRng(mode)) }

    var grid by remember { mutableStateOf(resume?.grid?.copyOf() ?: initialGrid()) }
    var tray by remember { mutableStateOf(resume?.tray ?: List(3) { bbRandomPiece(rng) }) }
    var score by remember { mutableIntStateOf(resume?.score ?: 0) }
    var combo by remember { mutableIntStateOf(resume?.combo ?: 0) }
    var gameOver by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var wonStars by remember { mutableIntStateOf(0) }
    var timeUp by remember { mutableStateOf(false) }
    var deadFlash by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(!prefs.getBoolean("blockblast_help_seen", false)) }
    var newRecord by remember { mutableStateOf(false) }
    var recordBanner by remember { mutableStateOf(false) }

    // Booster
    var hammerCharges by remember { mutableIntStateOf(resume?.hammer ?: 0) }
    var wirbelCharges by remember { mutableIntStateOf(resume?.wirbel ?: 0) }
    var hammerProg by remember { mutableIntStateOf(resume?.hProg ?: 0) }
    var wirbelProg by remember { mutableIntStateOf(resume?.wProg ?: 0) }
    var hammerActive by remember { mutableStateOf(false) }

    // Fieber / Zeit
    var feverTime by remember { mutableFloatStateOf(0f) }
    var timeLeft by remember { mutableFloatStateOf(120f) }
    var playSec by remember { mutableFloatStateOf((resume?.playSec ?: 0).toFloat()) }

    // Runden-Statistik
    var linesRound by remember { mutableIntStateOf(resume?.lines ?: 0) }
    var bestComboRound by remember { mutableIntStateOf(resume?.bestCombo ?: 0) }
    var movesLeft by remember { mutableIntStateOf(if (resume != null) resume.moves else levelDef?.moves ?: 0) }

    // Undo (1× pro Runde)
    var undoSnap by remember { mutableStateOf<BbSave?>(null) }
    var undoUsed by remember { mutableStateOf(resume?.undoUsed ?: false) }

    var runId by remember { mutableIntStateOf(0) }

    var xp by remember { mutableIntStateOf(prefs.getInt("blockblast_xp", 0)) }

    val hsKey = when (mode) {
        BbMode.CLASSIC -> "blockblast_highscore"
        BbMode.DAILY -> "blockblast_daily_high_${LocalDate.now().toEpochDay()}"
        BbMode.TIME -> "blockblast_time_highscore"
        BbMode.ADVENTURE -> null
    }
    var highscore by remember { mutableIntStateOf(hsKey?.let { prefs.getInt(it, 0) } ?: 0) }

    // Toast-Banner (Erfolge, Level-Ups)
    val toasts = remember { mutableStateListOf<Pair<Int, String>>() }
    var toastId by remember { mutableIntStateOf(0) }
    fun pushToast(text: String) {
        toasts.add(toastId++ to text)
    }
    LaunchedEffect(toasts.firstOrNull()) {
        if (toasts.isNotEmpty()) {
            delay(2400)
            if (toasts.isNotEmpty()) toasts.removeAt(0)
        }
    }

    fun unlockAch(id: String) {
        if (!prefs.getBoolean("blockblast_ach_$id", false)) {
            prefs.edit().putBoolean("blockblast_ach_$id", true).apply()
            BbAchievements.firstOrNull { it.id == id }?.let { pushToast("🏅 ${it.icon} ${it.title}") }
        }
    }

    fun addStat(key: String, delta: Int) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + delta).apply()
    }

    fun maxStat(key: String, v: Int) {
        if (v > prefs.getInt(key, 0)) prefs.edit().putInt(key, v).apply()
    }

    fun snapshot(): BbSave = BbSave(
        grid.copyOf(), tray.toList(), score, combo, hammerCharges, wirbelCharges,
        hammerProg, wirbelProg, linesRound, bestComboRound, movesLeft, playSec.toInt(), undoUsed,
    )

    fun saveClassic() {
        if (mode != BbMode.CLASSIC) return
        if (gameOver || won || score <= 0) {
            prefs.edit().remove("blockblast_save").apply()
        } else {
            prefs.edit().putString("blockblast_save", bbSerialize(snapshot())).apply()
        }
    }

    fun finishRound(win: Boolean) {
        if (gameOver || won) return
        if (hsKey != null && score > highscore) {
            newRecord = true
            highscore = score
            prefs.edit().putInt(hsKey, score).apply()
        }
        addStat("blockblast_games_played", 1)
        addStat("blockblast_total_score", score)
        addStat("blockblast_total_lines", linesRound)
        addStat("blockblast_playtime_sec", playSec.toInt())
        maxStat("blockblast_best_combo", bestComboRound)
        val oldLvl = bbLevelFromXp(xp)
        xp += score / 10 + if (win) 100 else 0
        prefs.edit().putInt("blockblast_xp", xp).apply()
        val newLvl = bbLevelFromXp(xp)
        if (newLvl > oldLvl) {
            pushToast("🌟 Spielerlevel $newLvl erreicht!")
            if (newLvl >= 5) unlockAch("level5")
        }
        if (score >= 5000) unlockAch("score5k")
        if (mode == BbMode.TIME && score >= 1500) unlockAch("rush1500")
        if (prefs.getInt("blockblast_games_played", 0) >= 10) unlockAch("games10")
        if (mode == BbMode.CLASSIC) {
            val entries = (prefs.getString("blockblast_history", "") ?: "")
                .split(";").filter { it.isNotBlank() }.toMutableList()
            entries.add("$score,${LocalDate.now().toEpochDay()}")
            val top = entries.mapNotNull { e ->
                val p = e.split(",")
                if (p.size == 2) p[0].toIntOrNull()?.let { s -> p[1].toLongOrNull()?.let { d -> s to d } } else null
            }.sortedByDescending { it.first }.take(5)
            prefs.edit().putString("blockblast_history", top.joinToString(";") { "${it.first},${it.second}" }).apply()
            prefs.edit().remove("blockblast_save").apply()
        }
        if (mode == BbMode.ADVENTURE && win && levelDef != null) {
            val frac = movesLeft.toFloat() / levelDef.moves
            wonStars = if (frac >= 0.4f) 3 else if (frac >= 0.15f) 2 else 1
            val key = "blockblast_adv_stars_$levelIndex"
            if (wonStars > prefs.getInt(key, 0)) prefs.edit().putInt(key, wonStars).apply()
            val cleared = BbLevels.indices.count { prefs.getInt("blockblast_adv_stars_$it", 0) > 0 }
            if (cleared >= 5) unlockAch("adv5")
            if (cleared >= BbLevels.size) unlockAch("adv20")
            won = true
        } else {
            gameOver = true
        }
        buzz(HapticFeedbackType.LongPress)
    }

    fun resetRound() {
        rng = bbNewRng(mode)
        grid = initialGrid()
        tray = List(3) { bbRandomPiece(rng) }
        score = 0
        combo = 0
        gameOver = false
        won = false
        wonStars = 0
        timeUp = false
        newRecord = false
        recordBanner = false
        hammerCharges = 0
        wirbelCharges = 0
        hammerProg = 0
        wirbelProg = 0
        hammerActive = false
        feverTime = 0f
        timeLeft = 120f
        playSec = 0f
        linesRound = 0
        bestComboRound = 0
        movesLeft = levelDef?.moves ?: 0
        undoSnap = null
        undoUsed = false
        paused = false
        showRestartConfirm = false
        if (mode == BbMode.CLASSIC) prefs.edit().remove("blockblast_save").apply()
        runId++
    }

    fun undo() {
        val s = undoSnap ?: return
        if (undoUsed || gameOver || won) return
        grid = s.grid.copyOf()
        tray = s.tray
        score = s.score
        combo = s.combo
        hammerCharges = s.hammer
        wirbelCharges = s.wirbel
        hammerProg = s.hProg
        wirbelProg = s.wProg
        linesRound = s.lines
        bestComboRound = s.bestCombo
        movesLeft = s.moves
        undoUsed = true
        undoSnap = null
        buzz(HapticFeedbackType.TextHandleMove)
        saveClassic()
    }

    // Drag / FX
    var dragSlot by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridSizePx by remember { mutableFloatStateOf(0f) }
    val slotCenters = remember { mutableStateMapOf<Int, Offset>() }

    var returnFx by remember { mutableStateOf<BbReturnFx?>(null) }
    val returnAnim = remember { Animatable(0f) }
    LaunchedEffect(returnFx) {
        if (returnFx != null) {
            returnAnim.snapTo(0f)
            returnAnim.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            returnFx = null
        }
    }

    var clearFx by remember { mutableStateOf<BbClearFx?>(null) }
    val clearAnim = remember { Animatable(1f) }
    LaunchedEffect(clearFx?.key) {
        if (clearFx != null) {
            clearAnim.snapTo(0f)
            clearAnim.animateTo(1f, tween(340))
            clearFx = null
        }
    }

    var popup by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val fx = remember { BbFxHolder() }
    var tick by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current
    val liftPx = with(density) { 90.dp.toPx() }

    val fits = remember(grid, tray) { tray.map { it == null || bbFitsAnywhere(grid, it) } }
    val isDead = remember(grid, tray) {
        val alive = tray.filterNotNull()
        alive.isNotEmpty() && alive.none { bbFitsAnywhere(grid, it) }
    }
    LaunchedEffect(isDead) {
        if (isDead && !gameOver && !won) {
            repeat(3) {
                deadFlash = true
                delay(160)
                deadFlash = false
                delay(120)
            }
            finishRound(win = false)
        }
    }

    // Zeitrausch: Ablauf beendet die Runde
    LaunchedEffect(timeUp) {
        if (timeUp && !gameOver && !won) finishRound(win = false)
    }

    // Ticker: Fieber, Zeit, Partikel, Puls-Effekte
    LaunchedEffect(paused, gameOver, won, runId, showHelp) {
        if (paused || gameOver || won || showHelp) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.05f)
                last = now
                if (dt > 0f) {
                    fx.t += dt
                    playSec += dt
                    if (feverTime > 0f) feverTime = max(0f, feverTime - dt)
                    if (mode == BbMode.TIME && !timeUp) {
                        timeLeft = max(0f, timeLeft - dt)
                        if (timeLeft <= 0f) timeUp = true
                    }
                    val it = fx.parts.iterator()
                    while (it.hasNext()) {
                        val p = it.next()
                        p.vy += 1600f * dt
                        p.x += p.vx * dt
                        p.y += p.vy * dt
                        p.life -= dt
                        if (p.life <= 0f) it.remove()
                    }
                }
                tick++
            }
        }
    }

    // Auto-Pause + Autosave beim App-Wechsel
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (!gameOver && !won) paused = true
                saveClassic()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // System-Back: erst Overlays schließen, dann pausieren, aus der Pause zurück ins Menü
    BackHandler {
        when {
            showHelp -> {
                showHelp = false
                prefs.edit().putBoolean("blockblast_help_seen", true).apply()
            }
            showRestartConfirm -> showRestartConfirm = false
            hammerActive -> hammerActive = false
            gameOver || won -> {
                saveClassic()
                onExit()
            }
            paused -> {
                saveClassic()
                onExit()
            }
            else -> paused = true
        }
    }

    fun spawnBurst(cx: Float, cy: Float, colors: List<Color>, n: Int, speed: Float) {
        if (!particlesOn) return
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val sp = speed * (0.4f + Random.nextFloat())
            fx.parts.add(
                BbParticle(
                    cx, cy,
                    kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp - speed * 0.3f,
                    0.5f + Random.nextFloat() * 0.5f, 1f,
                    colors.random(),
                    gridSizePx / 8f * (0.06f + Random.nextFloat() * 0.08f),
                )
            )
        }
    }

    fun dropTarget(piece: BbPiece): Triple<Int, Int, Boolean>? {
        if (gridSizePx <= 0f) return null
        val cellPx = gridSizePx / 8f
        val topLeftX = dragPos.x - piece.cols * cellPx / 2f
        val topLeftY = dragPos.y - liftPx - piece.rows * cellPx / 2f
        val col = ((topLeftX - gridOrigin.x) / cellPx).roundToInt()
        val row = ((topLeftY - gridOrigin.y) / cellPx).roundToInt()
        return Triple(row, col, bbCanPlace(grid, piece, row, col))
    }

    fun place(piece: BbPiece, row: Int, col: Int, slot: Int) {
        undoSnap = snapshot()
        val newGrid = grid.copyOf()
        for ((r, c) in piece.cells) newGrid[(row + r) * 8 + (col + c)] = piece.colorIndex
        var gained = piece.cells.size
        var jewelsGot = 0
        val (fullRows, fullCols) = bbFullLines(newGrid)
        val lineCount = fullRows.size + fullCols.size
        var perfect = false
        if (lineCount > 0) {
            combo += 1
            if (combo > bestComboRound) bestComboRound = combo
            if (combo >= 5) unlockAch("combo5")
            buzz(HapticFeedbackType.LongPress)
            val bonus = lineCount * (lineCount + 1) / 2 * 100 * combo
            gained += bonus
            val fxCells = ArrayList<Triple<Int, Int, Int>>()
            for (r in fullRows) for (c in 0..7) fxCells.add(Triple(r, c, newGrid[r * 8 + c]))
            for (c in fullCols) for (r in 0..7) if (r !in fullRows) fxCells.add(Triple(r, c, newGrid[r * 8 + c]))
            jewelsGot = fxCells.count { it.third == BbJewel }
            for ((r, c, _) in fxCells) newGrid[r * 8 + c] = -1
            clearFx = BbClearFx(fxCells, (clearFx?.key ?: 0) + 1)
            if (lineCount >= 3) unlockAch("lines3")
            linesRound += lineCount
            hammerProg += lineCount
            wirbelProg += lineCount
            while (hammerProg >= 10) {
                hammerProg -= 10
                if (hammerCharges < 3) {
                    hammerCharges++
                    pushToast("🔨 Hammer aufgeladen!")
                }
            }
            while (wirbelProg >= 10) {
                wirbelProg -= 10
                if (wirbelCharges < 3) {
                    wirbelCharges++
                    pushToast("🌀 Wirbel aufgeladen!")
                }
            }
            if (mode == BbMode.TIME) timeLeft = min(180f, timeLeft + 5f * lineCount)
            if (newGrid.all { it < 0 }) {
                perfect = true
                gained += 300
                addStat("blockblast_perfect_clears", 1)
                unlockAch("perfect")
                spawnBurst(gridSizePx / 2f, gridSizePx / 2f, BbColors, 40, gridSizePx * 0.9f)
            }
        } else {
            combo = 0
            buzz(HapticFeedbackType.TextHandleMove)
        }
        val fever = feverTime > 0f
        if (fever) gained *= 2
        if (lineCount > 0 && combo >= 3) {
            if (feverTime <= 0f) pushToast("⚡ Fieber-Modus!")
            feverTime = 10f
            unlockAch("fever")
        }
        score += gained
        if (hsKey != null && highscore > 0 && score > highscore && !recordBanner) {
            recordBanner = true
        }
        val text = buildString {
            if (fever) append("🔥 ")
            append("+$gained")
            if (perfect) append(" · Perfect!")
            else if (lineCount > 0 && combo >= 2) append(" · Combo x$combo")
            if (jewelsGot > 0) append(" · 💎×$jewelsGot")
        }
        popup = ((popup?.first ?: 0) + 1) to text
        grid = newGrid
        tray = tray.toMutableList().also { it[slot] = null }
        if (tray.all { it == null }) tray = List(3) { bbRandomPiece(rng) }

        if (mode == BbMode.ADVENTURE && levelDef != null) {
            movesLeft -= 1
            val done = if (levelDef.hasJewels) newGrid.count { it == BbJewel } == 0
            else linesRound >= levelDef.goalLines
            if (done) {
                finishRound(win = true)
            } else if (movesLeft <= 0) {
                finishRound(win = false)
            }
        }
        saveClassic()
    }

    fun useHammer(r: Int, c: Int) {
        if (!hammerActive || hammerCharges <= 0) return
        val v = grid[r * 8 + c]
        if (v < 0) return
        val newGrid = grid.copyOf()
        newGrid[r * 8 + c] = -1
        grid = newGrid
        hammerCharges -= 1
        hammerActive = false
        unlockAch("hammer")
        buzz(HapticFeedbackType.LongPress)
        val cellPx = gridSizePx / 8f
        spawnBurst(c * cellPx + cellPx / 2f, r * cellPx + cellPx / 2f, listOf(bbCellColor(v), Color.White), 10, cellPx * 5f)
        if (mode == BbMode.ADVENTURE && levelDef != null && levelDef.hasJewels &&
            newGrid.count { it == BbJewel } == 0
        ) {
            finishRound(win = true)
        }
        saveClassic()
    }

    fun useWirbel() {
        if (wirbelCharges <= 0 || gameOver || won) return
        if (tray.all { it == null }) return
        tray = tray.map { if (it == null) null else bbRandomPiece(rng) }
        wirbelCharges -= 1
        unlockAch("wirbel")
        buzz(HapticFeedbackType.TextHandleMove)
        saveClassic()
    }

    val displayScore by animateIntAsState(score, tween(350))

    val comboColor = when {
        combo >= 6 -> Color(0xFFFF7043)
        combo >= 4 -> HikariPrimary
        else -> HikariText
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(HikariBg)
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
    ) {
        val gridDp = if (maxWidth - 32.dp < 340.dp) maxWidth - 32.dp else 340.dp
        val cellDp = gridDp / 8

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                TextButton(onClick = { paused = true }) { Text("❚❚", color = HikariTextMuted, fontSize = 14.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (mode) {
                            BbMode.CLASSIC -> "Block Blast"
                            BbMode.DAILY -> "Heutige Challenge"
                            BbMode.ADVENTURE -> "Level ${levelIndex + 1}"
                            BbMode.TIME -> "Zeitrausch"
                        },
                        fontSize = 17.sp, color = HikariPrimary, fontWeight = FontWeight.Bold,
                    )
                    if (mode == BbMode.TIME) {
                        val tl = timeLeft.toInt()
                        Text(
                            "⏱ ${bbFmtTime(tl)}",
                            fontSize = 15.sp,
                            color = if (timeLeft < 10f) HikariDanger else HikariText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer {
                                alpha = if (timeLeft < 10f) 0.55f + 0.45f * sin(timeLeft * 6f) * sin(timeLeft * 6f) else 1f
                            },
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$displayScore", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    when {
                        mode == BbMode.ADVENTURE -> Text("Züge: $movesLeft", fontSize = 11.sp, color = if (movesLeft <= 3) HikariDanger else HikariTextMuted)
                        highscore > 0 && score < highscore -> Text("Noch ${highscore - score} bis Rekord", fontSize = 10.sp, color = HikariTextMuted)
                        else -> Text("Best: $highscore", fontSize = 11.sp, color = HikariTextMuted)
                    }
                }
            }

            // Statuszeile: Combo / Ziel / Fieber
            if (mode == BbMode.ADVENTURE && levelDef != null) {
                val goalText = if (levelDef.hasJewels) {
                    "💎 ${grid.count { it == BbJewel }} übrig"
                } else {
                    "🎯 $linesRound/${levelDef.goalLines} Linien"
                }
                Text(goalText, fontSize = 13.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    if (combo >= 2) "Combo x$combo" else " ",
                    fontSize = 13.sp,
                    color = comboColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Fieber-Balken
            Box(Modifier.width(gridDp * 0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (feverTime > 0f) HikariSurfaceHigh else Color.Transparent)) {
                if (feverTime > 0f) {
                    Box(
                        Modifier.fillMaxWidth(feverTime / 10f).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp)).background(HikariPrimary)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Canvas(
                Modifier
                    .size(gridDp)
                    .onGloballyPositioned {
                        gridOrigin = it.positionInRoot()
                        gridSizePx = it.size.width.toFloat()
                    }
                    .pointerInput(hammerActive) {
                        if (hammerActive) {
                            detectTapGestures { pos ->
                                val cellPx = size.width / 8f
                                val c = (pos.x / cellPx).toInt().coerceIn(0, 7)
                                val r = (pos.y / cellPx).toInt().coerceIn(0, 7)
                                useHammer(r, c)
                            }
                        }
                    }
            ) {
                if (tick < 0) return@Canvas // liest tick → Redraw pro Frame
                val cell = size.width / 8f
                val pad = cell * 0.06f
                val corner = CornerRadius(cell * 0.18f, cell * 0.18f)

                // Fast-volle Linien subtil schimmern lassen
                val shimmer = 0.05f + 0.04f * sin(fx.t * 3f)
                val nearRows = (0..7).filter { r -> (0..7).count { c -> grid[r * 8 + c] >= 0 } == 7 }
                val nearCols = (0..7).filter { c -> (0..7).count { r -> grid[r * 8 + c] >= 0 } == 7 }

                for (r in 0..7) for (c in 0..7) {
                    val v = grid[r * 8 + c]
                    val topLeft = Offset(c * cell + pad, r * cell + pad)
                    val sz = Size(cell - pad * 2, cell - pad * 2)
                    if (v < 0) {
                        drawRoundRect(HikariSurfaceHigh, topLeft, sz, corner)
                        if (r in nearRows || c in nearCols) {
                            drawRoundRect(HikariAmber.copy(alpha = shimmer), topLeft, sz, corner)
                        }
                    } else {
                        val col = bbCellColor(v)
                        drawRoundRect(col, topLeft, sz, corner)
                        drawRoundRect(
                            lerpColor(col, Color.Black, 0.45f), topLeft, sz, corner,
                            style = Stroke(width = cell * 0.06f),
                        )
                        if (v == BbJewel) {
                            val cx = c * cell + cell / 2f
                            val cy = r * cell + cell / 2f
                            val d = cell * 0.2f
                            val jp = Path().apply {
                                moveTo(cx, cy - d)
                                lineTo(cx + d, cy)
                                lineTo(cx, cy + d)
                                lineTo(cx - d, cy)
                                close()
                            }
                            drawPath(jp, Color.White.copy(alpha = 0.92f))
                        }
                    }
                }

                // Ghost-Preview während des Drags (grün = passt, rot = passt nicht)
                val dPiece = if (dragSlot >= 0) tray.getOrNull(dragSlot) else null
                if (dPiece != null) {
                    val target = dropTarget(dPiece)
                    if (target != null) {
                        val (row, colBase, ok) = target
                        if (ok) {
                            val gcol = BbColors[dPiece.colorIndex]
                            val sim = grid.copyOf()
                            for ((r, c) in dPiece.cells) sim[(row + r) * 8 + (colBase + c)] = dPiece.colorIndex
                            val (fr, fc) = bbFullLines(sim)
                            for (r in fr) for (c in 0..7) drawRoundRect(
                                gcol.copy(alpha = 0.22f),
                                Offset(c * cell + pad, r * cell + pad),
                                Size(cell - pad * 2, cell - pad * 2), corner,
                            )
                            for (c in fc) for (r in 0..7) drawRoundRect(
                                gcol.copy(alpha = 0.22f),
                                Offset(c * cell + pad, r * cell + pad),
                                Size(cell - pad * 2, cell - pad * 2), corner,
                            )
                            for ((r, c) in dPiece.cells) drawRoundRect(
                                gcol.copy(alpha = 0.45f),
                                Offset((colBase + c) * cell + pad, (row + r) * cell + pad),
                                Size(cell - pad * 2, cell - pad * 2), corner,
                            )
                        } else {
                            // ungültige Position: rote Andeutung auf den Zellen im Feld
                            var any = false
                            for ((r, c) in dPiece.cells) {
                                val rr = row + r
                                val cc = colBase + c
                                if (rr in 0..7 && cc in 0..7) any = true
                            }
                            if (any) {
                                for ((r, c) in dPiece.cells) {
                                    val rr = row + r
                                    val cc = colBase + c
                                    if (rr in 0..7 && cc in 0..7) {
                                        drawRoundRect(
                                            HikariDanger.copy(alpha = 0.18f),
                                            Offset(cc * cell + pad, rr * cell + pad),
                                            Size(cell - pad * 2, cell - pad * 2), corner,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Clear-Animation (Blink/Fade)
                val cfx = clearFx
                if (cfx != null) {
                    val p = clearAnim.value
                    for ((r, c, ci) in cfx.cells) {
                        val col = lerpColor(bbCellColor(ci.coerceAtLeast(0)), Color.White, 0.5f)
                        val shrink = cell * 0.5f * p
                        drawRoundRect(
                            col.copy(alpha = 1f - p),
                            Offset(c * cell + pad + shrink / 2, r * cell + pad + shrink / 2),
                            Size(cell - pad * 2 - shrink, cell - pad * 2 - shrink),
                            corner,
                        )
                    }
                }

                // Partikel (Perfect Clear, Hammer)
                for (p in fx.parts) {
                    drawCircle(p.color.copy(alpha = (p.life / p.maxLife).coerceIn(0f, 1f)), p.r, Offset(p.x, p.y))
                }

                // Fieber-Glühen + Hammer-Rahmen
                if (feverTime > 0f) {
                    val glow = 0.10f + 0.06f * sin(fx.t * 6f)
                    drawRoundRect(
                        HikariAmber.copy(alpha = glow),
                        Offset.Zero, Size(size.width, size.height),
                        CornerRadius(cell * 0.2f, cell * 0.2f),
                    )
                }
                if (hammerActive) {
                    drawRoundRect(
                        HikariAmber,
                        Offset.Zero, Size(size.width, size.height),
                        CornerRadius(cell * 0.2f, cell * 0.2f),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                if (mode == BbMode.TIME && timeLeft < 10f && timeLeft > 0f) {
                    val pulse = (0.10f + 0.10f * sin(timeLeft * 8f)).coerceAtLeast(0f)
                    drawRoundRect(
                        HikariDanger.copy(alpha = pulse),
                        Offset.Zero, Size(size.width, size.height),
                        CornerRadius(cell * 0.2f, cell * 0.2f),
                        style = Stroke(width = 5.dp.toPx()),
                    )
                }
            }

            if (hammerActive) {
                Text(
                    "🔨 Zelle antippen — erneut auf den Hammer tippen bricht ab",
                    fontSize = 11.sp, color = HikariPrimary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.weight(0.3f))

            // Booster-Leiste
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                Arrangement.SpaceEvenly,
                Alignment.CenterVertically,
            ) {
                BbBoosterButton(
                    emoji = "🔨", charges = hammerCharges, progress = hammerProg / 10f,
                    active = hammerActive, enabled = hammerCharges > 0 && !gameOver && !won,
                ) { hammerActive = !hammerActive }
                // Undo
                val undoEnabled = undoSnap != null && !undoUsed && !gameOver && !won
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(HikariCardBg)
                        .clickable(enabled = undoEnabled) { undo() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "↩",
                        fontSize = 20.sp,
                        color = if (undoEnabled) HikariText else HikariTextFaint,
                        fontWeight = FontWeight.Bold,
                    )
                }
                BbBoosterButton(
                    emoji = "🌀", charges = wirbelCharges, progress = wirbelProg / 10f,
                    active = false, enabled = wirbelCharges > 0 && !gameOver && !won,
                ) { useWirbel() }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(108.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (slot in 0..2) {
                    val piece = tray[slot]
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned {
                                slotCenters[slot] = it.positionInRoot() +
                                    Offset(it.size.width / 2f, it.size.height / 2f)
                            }
                            .pointerInput(piece?.id, gameOver, won, paused) {
                                detectDragGestures(
                                    onDragStart = { off ->
                                        if (piece != null && !gameOver && !won && !paused && returnFx == null) {
                                            dragSlot = slot
                                            val center = slotCenters[slot] ?: Offset.Zero
                                            dragPos = center - Offset(size.width / 2f, size.height / 2f) + off
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragSlot == slot && piece != null) {
                                            val t = dropTarget(piece)
                                            if (t != null && t.third) {
                                                place(piece, t.first, t.second, slot)
                                            } else {
                                                buzz(HapticFeedbackType.TextHandleMove)
                                                returnFx = BbReturnFx(piece, slot, dragPos)
                                            }
                                        }
                                        dragSlot = -1
                                    },
                                    onDragCancel = {
                                        if (dragSlot == slot && piece != null) {
                                            returnFx = BbReturnFx(piece, slot, dragPos)
                                        }
                                        dragSlot = -1
                                    },
                                    onDrag = { change, amount ->
                                        if (dragSlot == slot) {
                                            change.consume()
                                            dragPos += amount
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val hidden = dragSlot == slot || returnFx?.slot == slot
                        if (piece != null && !hidden) {
                            val appear = remember(piece.id) { Animatable(0.5f) }
                            LaunchedEffect(piece.id) {
                                appear.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                            val fitsHere = fits[slot]
                            BbPieceView(
                                piece = piece,
                                cellSize = 15.dp,
                                alpha = if (fitsHere) 1f else 0.35f,
                                tint = if (deadFlash && !fitsHere) HikariDanger else null,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = appear.value
                                    scaleY = appear.value
                                },
                            )
                        }
                    }
                }
            }
        }

        // Gezogenes Piece — ~90dp über dem Finger, leicht vergrößert
        val dragPiece = if (dragSlot >= 0) tray.getOrNull(dragSlot) else null
        if (dragPiece != null && gridSizePx > 0f) {
            val cellPx = gridSizePx / 8f
            val w = dragPiece.cols * cellPx
            val h = dragPiece.rows * cellPx
            val tl = Offset(dragPos.x - w / 2f, dragPos.y - liftPx - h / 2f) - rootOrigin
            BbPieceView(
                piece = dragPiece,
                cellSize = cellDp,
                modifier = Modifier
                    .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                    .graphicsLayer { scaleX = 1.07f; scaleY = 1.07f },
            )
        }

        // Zurück-in-den-Slot-Animation bei ungültigem Drop
        returnFx?.let { rfx ->
            val target = slotCenters[rfx.slot] ?: Offset.Zero
            val t = returnAnim.value
            val startX = rfx.from.x
            val startY = rfx.from.y - liftPx
            val cx = startX + (target.x - startX) * t
            val cy = startY + (target.y - startY) * t
            val cellNow = cellDp + (15.dp - cellDp) * t
            val wPx = with(density) { (cellNow * rfx.piece.cols).toPx() }
            val hPx = with(density) { (cellNow * rfx.piece.rows).toPx() }
            val tl = Offset(cx - wPx / 2f, cy - hPx / 2f) - rootOrigin
            BbPieceView(
                piece = rfx.piece,
                cellSize = cellNow,
                modifier = Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) },
            )
        }

        // Punkte-Popup
        popup?.let { (pkey, text) ->
            val anim = remember(pkey) { Animatable(0f) }
            LaunchedEffect(pkey) {
                anim.animateTo(1f, tween(800))
                popup = null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text,
                    fontSize = 22.sp,
                    color = HikariPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        translationY = -140f - anim.value * 90f
                        alpha = 1f - anim.value * anim.value
                    },
                )
            }
        }

        // Rekord-Banner (live, sobald der Highscore fällt)
        if (recordBanner && !gameOver && !won) {
            Box(Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "🏆 Neuer Rekord!",
                    fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(HikariPrimary)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // Toast-Banner (Erfolge, Aufladungen, Level-Ups)
        toasts.firstOrNull()?.let { (_, text) ->
            Box(Modifier.fillMaxSize().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    text,
                    fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(HikariSurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Pause-Overlay
        if (paused && !gameOver && !won && !showHelp) {
            Box(
                Modifier.fillMaxSize().background(Color(0xE60A0A0A))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HikariCardBg)
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Pause", fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Punkte: $score · Zeit: ${bbFmtTime(playSec.toInt())}",
                        fontSize = 12.sp, color = HikariTextMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { paused = false },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                        modifier = Modifier.width(200.dp),
                    ) { Text("Fortsetzen", color = Color.Black, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showRestartConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                        modifier = Modifier.width(200.dp),
                    ) { Text("Neustart", color = HikariText) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            saveClassic()
                            onExit()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                        modifier = Modifier.width(200.dp),
                    ) { Text("Zum Menü", color = HikariText) }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.width(200.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Vibration", fontSize = 13.sp, color = HikariTextMuted)
                        Switch(
                            checked = haptics, onCheckedChange = onHaptics,
                            colors = SwitchDefaults.colors(checkedTrackColor = HikariPrimary, checkedThumbColor = Color.Black),
                        )
                    }
                    Row(Modifier.width(200.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Partikel", fontSize = 13.sp, color = HikariTextMuted)
                        Switch(
                            checked = particlesOn, onCheckedChange = onParticles,
                            colors = SwitchDefaults.colors(checkedTrackColor = HikariPrimary, checkedThumbColor = Color.Black),
                        )
                    }
                }
            }
        }

        // Neustart-Bestätigung
        if (showRestartConfirm) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000))
                    .pointerInput(Unit) { detectTapGestures { showRestartConfirm = false } },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HikariCardBg)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Runde neu starten?", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Der aktuelle Fortschritt geht verloren.", fontSize = 12.sp, color = HikariTextMuted)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showRestartConfirm = false },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                        ) { Text("Abbrechen", color = HikariText) }
                        Button(
                            onClick = { resetRound() },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariDanger),
                        ) { Text("Neustart", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Hilfe beim ersten Start
        if (showHelp) {
            BbHelpOverlay {
                showHelp = false
                prefs.edit().putBoolean("blockblast_help_seen", true).apply()
            }
        }

        // Game-Over / Sieg-Overlay
        if (gameOver || won) {
            Box(
                Modifier.fillMaxSize().background(Color(0xE60A0A0A))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            won -> "Level geschafft!"
                            mode == BbMode.TIME && timeUp -> "Zeit um!"
                            mode == BbMode.ADVENTURE -> "Level gescheitert"
                            else -> "Game Over"
                        },
                        fontSize = 28.sp,
                        color = if (won) HikariPrimary else HikariText,
                        fontWeight = FontWeight.Bold,
                    )
                    if (won) {
                        Spacer(Modifier.height(10.dp))
                        Row {
                            repeat(3) { s ->
                                Text(
                                    "★", fontSize = 34.sp,
                                    color = if (s < wonStars) HikariPrimary else HikariTextFaint,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Punkte: $score", fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (newRecord) {
                        Text("Neuer Rekord!", fontSize = 16.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                    } else if (hsKey != null) {
                        Text("Highscore: $highscore", fontSize = 14.sp, color = HikariTextMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Runden-Statistik
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(HikariCardBg)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Dauer ${bbFmtTime(playSec.toInt())} · Beste Combo ×$bestComboRound · $linesRound Linien",
                            fontSize = 12.sp, color = HikariTextMuted)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (won && levelIndex + 1 < BbLevels.size) {
                            Button(
                                onClick = { onPlayLevel(levelIndex + 1) },
                                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Weiter →", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                        Button(
                            onClick = { resetRound() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (won) HikariSurfaceHigh else HikariPrimary),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                "Nochmal",
                                color = if (won) HikariText else Color.Black,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = onExit,
                            colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Menü", color = HikariText) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BbBoosterButton(
    emoji: String,
    charges: Int,
    progress: Float,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (active) HikariAmberSoft else HikariCardBg)
            .then(if (active) Modifier.border(2.dp, HikariPrimary, CircleShape) else Modifier)
            .clickable(enabled = enabled || active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(
                HikariSurfaceHigh, -90f, 360f, useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                HikariPrimary, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false,
                style = Stroke(width = stroke),
            )
        }
        Text(
            emoji, fontSize = 20.sp,
            modifier = Modifier.graphicsLayer { alpha = if (enabled || active) 1f else 0.35f },
        )
        if (charges > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(HikariPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text("$charges", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BbPieceView(
    piece: BbPiece,
    cellSize: Dp,
    alpha: Float = 1f,
    tint: Color? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(cellSize * piece.cols, cellSize * piece.rows)) {
        val cell = size.width / piece.cols
        val pad = cell * 0.07f
        val corner = CornerRadius(cell * 0.2f, cell * 0.2f)
        val base = tint ?: BbColors[piece.colorIndex]
        for ((r, c) in piece.cells) {
            val topLeft = Offset(c * cell + pad, r * cell + pad)
            val sz = Size(cell - pad * 2, cell - pad * 2)
            drawRoundRect(base.copy(alpha = alpha), topLeft, sz, corner)
            drawRoundRect(
                lerpColor(base, Color.Black, 0.45f).copy(alpha = alpha),
                topLeft, sz, corner,
                style = Stroke(width = cell * 0.07f),
            )
        }
    }
}
