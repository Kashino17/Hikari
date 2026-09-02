package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// ————— 2048 —————
// Wisch-Puzzle: gleiche Kacheln verschmelzen, Ziel ist die 2048 (und darüber
// hinaus). Drei Brettgrößen als Modi, Rückgängig, Spielstand wird automatisch
// gesichert und im Menü als "Fortsetzen" angeboten.

private enum class G2Screen { MENU, GAME }

private enum class G2Mode(val id: String, val label: String, val emoji: String, val desc: String, val size: Int) {
    CLASSIC("classic", "Klassisch", "🔢", "4×4 — das Original. Schieben, verschmelzen, die 2048 erreichen.", 4),
    BIG("big", "Groß", "🧱", "5×5 — mehr Luft, längere Partien, riesige Kacheln möglich.", 5),
    RELAXED("relaxed", "Relaxed", "🌿", "8×8 — riesiges Brett, kein Druck. Zum Abschalten und Zahlen stapeln.", 8),
    TINY("tiny", "Winzig", "🔥", "3×3 — brutal eng. Jeder Wisch will überlegt sein.", 3),
}

// Abstand und Eckenradius schrumpfen mit der Brettgröße, sonst bleibt bei 8×8 kaum Kachel übrig.
private fun g2Gap(n: Int): Dp = when {
    n >= 8 -> 3.dp
    n >= 5 -> 6.dp
    else -> 8.dp
}

private fun g2Corner(n: Int): Dp = when {
    n >= 8 -> 5.dp
    n >= 5 -> 8.dp
    else -> 10.dp
}

private val G2Accent = Color(0xFFFB923C)

private class G2Store(val p: SharedPreferences) {
    fun getBool(k: String, d: Boolean) = p.getBoolean("g2048_$k", d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean("g2048_$k", v).apply()
    fun getInt(k: String, d: Int) = p.getInt("g2048_$k", d)
    fun setInt(k: String, v: Int) = p.edit().putInt("g2048_$k", v).apply()
    fun getStr(k: String): String? = p.getString("g2048_$k", null)
    fun setStr(k: String, v: String?) = p.edit().putString("g2048_$k", v).apply()
    fun bump(k: String, by: Int = 1) = setInt(k, getInt(k, 0) + by)
}

// ————— Spiellogik —————

internal data class G2Tile(
    val id: Int,
    val r: Int,
    val c: Int,
    val v: Int,
    val merged: Boolean = false,
    val spawned: Boolean = false,
    // Geist: eine bereits verschmolzene Kachel, die nur noch zur Zielposition
    // gleitet und dann entfernt wird — sorgt für die flüssige Merge-Animation.
    val ghost: Boolean = false,
)

internal data class G2Board(
    val size: Int,
    val tiles: List<G2Tile>,
    val score: Int,
    val nextId: Int,
    val moves: Int = 0,
) {
    val live: List<G2Tile> get() = tiles.filter { !it.ghost }
    val highest: Int get() = live.maxOfOrNull { it.v } ?: 0
}

internal enum class G2Dir { LEFT, RIGHT, UP, DOWN }

internal fun g2Spawn(b: G2Board, rnd: Random): G2Board {
    val occupied = b.live.map { it.r * b.size + it.c }.toHashSet()
    val free = (0 until b.size * b.size).filter { it !in occupied }
    if (free.isEmpty()) return b
    val cell = free[rnd.nextInt(free.size)]
    val v = if (rnd.nextFloat() < 0.9f) 2 else 4
    return b.copy(
        tiles = b.tiles + G2Tile(b.nextId, cell / b.size, cell % b.size, v, spawned = true),
        nextId = b.nextId + 1,
    )
}

internal fun g2NewBoard(size: Int, rnd: Random): G2Board {
    var b = G2Board(size, emptyList(), 0, 1)
    b = g2Spawn(b, rnd)
    b = g2Spawn(b, rnd)
    return b
}

/** Schiebt alle Kacheln in Richtung [dir]. Null, wenn sich nichts bewegt hat. */
internal fun g2Move(b: G2Board, dir: G2Dir): G2Board? {
    val n = b.size
    val grid = arrayOfNulls<G2Tile>(n * n)
    for (t in b.live) grid[t.r * n + t.c] = t
    val real = ArrayList<G2Tile>(n * n)
    val ghosts = ArrayList<G2Tile>(n)
    var nextId = b.nextId
    var gained = 0
    var moved = false
    for (line in 0 until n) {
        // Zellen dieser Linie in Schub-Reihenfolge (vorderste zuerst)
        val cells = (0 until n).map { i ->
            when (dir) {
                G2Dir.LEFT -> line to i
                G2Dir.RIGHT -> line to (n - 1 - i)
                G2Dir.UP -> i to line
                G2Dir.DOWN -> (n - 1 - i) to line
            }
        }
        var pos = 0
        var last: G2Tile? = null // zuletzt platzierte, noch nicht verschmolzene Kachel
        for ((r, c) in cells) {
            val t = grid[r * n + c] ?: continue
            if (last != null && last.v == t.v) {
                // Verschmelzen: neue Kachel ersetzt 'last', beide Quellen werden Geister.
                val mergedTile = G2Tile(nextId++, last.r, last.c, t.v * 2, merged = true)
                real[real.lastIndex] = mergedTile
                ghosts += last.copy(ghost = true)
                ghosts += t.copy(r = last.r, c = last.c, ghost = true)
                gained += t.v * 2
                moved = true
                last = null
            } else {
                val (tr, tc) = cells[pos]
                if (tr != t.r || tc != t.c) moved = true
                val placed = t.copy(r = tr, c = tc, merged = false, spawned = false)
                real += placed
                last = placed
                pos++
            }
        }
    }
    if (!moved) return null
    return b.copy(tiles = ghosts + real, score = b.score + gained, nextId = nextId, moves = b.moves + 1)
}

internal fun g2CanMove(b: G2Board): Boolean {
    val n = b.size
    val grid = IntArray(n * n)
    for (t in b.live) grid[t.r * n + t.c] = t.v
    for (i in 0 until n * n) if (grid[i] == 0) return true
    for (r in 0 until n) for (c in 0 until n) {
        val v = grid[r * n + c]
        if (c + 1 < n && grid[r * n + c + 1] == v) return true
        if (r + 1 < n && grid[(r + 1) * n + c] == v) return true
    }
    return false
}

// Spielstand als kompakter String: "score|moves|r,c,v;r,c,v;…"
internal fun g2Serialize(b: G2Board): String =
    "${b.score}|${b.moves}|" + b.live.joinToString(";") { "${it.r},${it.c},${it.v}" }

internal fun g2Deserialize(size: Int, s: String): G2Board? = runCatching {
    val parts = s.split("|")
    val score = parts[0].toInt()
    val moves = parts[1].toInt()
    var id = 1
    val tiles = parts[2].split(";").filter { it.isNotBlank() }.map { seg ->
        val (r, c, v) = seg.split(",").map { it.toInt() }
        G2Tile(id++, r, c, v)
    }
    if (tiles.isEmpty()) null else G2Board(size, tiles, score, id, moves)
}.getOrNull()

private fun g2TileColor(v: Int): Color = when (v) {
    2 -> Color(0xFF3F3F46)
    4 -> Color(0xFF52525B)
    8 -> Color(0xFFF59E0B)
    16 -> Color(0xFFF97316)
    32 -> Color(0xFFEF4444)
    64 -> Color(0xFFDC2626)
    128 -> Color(0xFFEAB308)
    256 -> Color(0xFFFACC15)
    512 -> Color(0xFFFDE047)
    1024 -> Color(0xFFA3E635)
    2048 -> Color(0xFF22D3EE)
    else -> Color(0xFFA78BFA)
}

private fun g2TextColor(v: Int): Color = if (v <= 4) HikariText else Color(0xFF17171A)

// ————— Root —————

@Composable
fun TwentyFortyEightGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { G2Store(context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)) }
    val rnd = remember { Random(System.currentTimeMillis()) }

    var screen by remember { mutableStateOf(G2Screen.MENU) }
    var mode by remember {
        mutableStateOf(G2Mode.entries.firstOrNull { it.id == store.getStr("last_mode") } ?: G2Mode.CLASSIC)
    }
    var hapticsOn by remember { mutableStateOf(store.getBool("haptics", true)) }
    var undoOn by remember { mutableStateOf(store.getBool("undo", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var gameKey by remember { mutableIntStateOf(0) }
    var resume by remember { mutableStateOf(false) }

    fun buzz(t: HapticFeedbackType) { if (hapticsOn) haptic.performHapticFeedback(t) }

    BackHandler(enabled = screen == G2Screen.MENU) {
        when {
            showSettings -> showSettings = false
            showStats -> showStats = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Crossfade(targetState = screen, animationSpec = tween(220), label = "g2Screen") { s ->
            when (s) {
                G2Screen.MENU -> G2MenuScreen(
                    store = store,
                    mode = mode,
                    onMode = { mode = it },
                    onStart = { cont ->
                        store.setStr("last_mode", mode.id)
                        resume = cont
                        gameKey++
                        screen = G2Screen.GAME
                    },
                    onStats = { showStats = true },
                    onSettings = { showSettings = true },
                    onBack = onBack,
                )
                G2Screen.GAME -> key(gameKey) {
                    G2PlayScreen(
                        mode = mode,
                        store = store,
                        rnd = rnd,
                        resume = resume,
                        undoOn = undoOn,
                        buzz = { buzz(it) },
                        onExit = { screen = G2Screen.MENU },
                    )
                }
            }
        }

        if (showSettings) {
            GxSheet("Optionen", G2Accent, onClose = { showSettings = false }) {
                GxToggle("Haptik", "Vibration bei Wisch und Verschmelzen.", G2Accent, hapticsOn) {
                    hapticsOn = it; store.setBool("haptics", it)
                }
                GxToggle("Rückgängig erlauben", "Den letzten Zug zurücknehmen. Für Puristen abschaltbar.", G2Accent, undoOn) {
                    undoOn = it; store.setBool("undo", it)
                }
            }
        }
        if (showStats) {
            GxSheet("Statistik", G2Accent, onClose = { showStats = false }) {
                G2Mode.entries.forEach { m ->
                    Text(m.label, fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        GxStatTile("${store.getInt("best_${m.id}", 0)}", "Bestscore", G2Accent, Modifier.weight(1f))
                        GxStatTile("${store.getInt("hi_${m.id}", 0)}", "Höchste Kachel", G2Accent, Modifier.weight(1f))
                        GxStatTile("${store.getInt("games_${m.id}", 0)}", "Partien", G2Accent, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    GxStatTile("${store.getInt("wins", 0)}", "2048 erreicht", G2Accent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("moves_total", 0)}", "Züge gesamt", G2Accent, Modifier.weight(1f))
                }
            }
        }
    }
}

// ————— Menü —————

@Composable
private fun G2MenuScreen(
    store: G2Store,
    mode: G2Mode,
    onMode: (G2Mode) -> Unit,
    onStart: (resume: Boolean) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    BackHandler(enabled = showRules) { showRules = false }
    val hasSave = store.getStr("save_${mode.id}") != null

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(G2Accent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("2048", G2Accent, onBack = onBack, right = { GxIconChip("?") { showRules = true } })
            Column(Modifier.padding(horizontal = 16.dp)) {
                GxSectionTitle("Brett")
                G2Mode.entries.forEachIndexed { i, m ->
                    val best = store.getInt("best_${m.id}", 0)
                    val hi = store.getInt("hi_${m.id}", 0)
                    GxAppear(i) {
                        GxModeCard(
                            emoji = m.emoji,
                            title = m.label,
                            subtitle = m.desc,
                            accent = G2Accent,
                            highlighted = mode == m,
                            badge = if (store.getStr("save_${m.id}") != null) "LÄUFT" else null,
                            best = if (best > 0) "Best: $best · Kachel $hi" else null,
                            onClick = { onMode(m) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(14.dp))
                GxAppear(3) {
                    Column {
                        if (hasSave) {
                            GxPrimaryButton("Fortsetzen", G2Accent, Modifier.fillMaxWidth()) { onStart(true) }
                            Spacer(Modifier.height(10.dp))
                            GxGhostButton("Neue Partie", Modifier.fillMaxWidth()) { onStart(false) }
                        } else {
                            GxPrimaryButton("Spielen", G2Accent, Modifier.fillMaxWidth()) { onStart(false) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GxAppear(4) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f), onSettings)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (showRules) {
            GxSheet("So geht's", G2Accent, onClose = { showRules = false }) {
                Text(
                    "Wische in eine Richtung — alle Kacheln rutschen bis zum Anschlag. Treffen zwei gleiche " +
                        "Zahlen aufeinander, verschmelzen sie zu ihrer Summe. Nach jedem Zug erscheint eine neue 2 " +
                        "(selten eine 4).\n\nZiel ist die 2048 — danach geht es weiter, so lange noch ein Zug möglich ist. " +
                        "Tipp: Halte die größte Kachel in einer Ecke und fülle die Reihe daneben von groß nach klein.",
                    fontSize = 13.sp, color = HikariTextMuted, lineHeight = 19.sp,
                )
            }
        }
    }
}

// ————— Spiel —————

@Composable
private fun G2PlayScreen(
    mode: G2Mode,
    store: G2Store,
    rnd: Random,
    resume: Boolean,
    undoOn: Boolean,
    buzz: (HapticFeedbackType) -> Unit,
    onExit: () -> Unit,
) {
    var board by remember {
        val saved = if (resume) store.getStr("save_${mode.id}")?.let { g2Deserialize(mode.size, it) } else null
        if (saved == null) store.bump("games_${mode.id}")
        mutableStateOf(saved ?: g2NewBoard(mode.size, rnd))
    }
    var prev by remember { mutableStateOf<G2Board?>(null) }
    var best by remember { mutableIntStateOf(store.getInt("best_${mode.id}", 0)) }
    var startBest by remember { mutableIntStateOf(best) }
    var gameOver by remember { mutableStateOf(false) }
    var wonShown by remember { mutableStateOf(board.highest >= 2048) }
    var showWin by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }

    fun persist(b: G2Board) {
        store.setStr("save_${mode.id}", g2Serialize(b))
        if (b.score > best) {
            best = b.score
            store.setInt("best_${mode.id}", best)
        }
        val hi = b.highest
        if (hi > store.getInt("hi_${mode.id}", 0)) store.setInt("hi_${mode.id}", hi)
    }

    fun restart() {
        store.setStr("save_${mode.id}", null)
        store.bump("games_${mode.id}")
        board = g2NewBoard(mode.size, rnd)
        startBest = best
        prev = null
        gameOver = false
        showWin = false
        wonShown = false
        newRecord = false
    }

    fun swipe(dir: G2Dir) {
        if (gameOver || showWin) return
        val moved = g2Move(board, dir) ?: return
        val before = board
        val next = g2Spawn(moved, rnd)
        val mergedNow = next.tiles.any { it.ghost }
        buzz(if (mergedNow) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        prev = before
        board = next
        store.bump("moves_total")
        persist(next)
        if (!wonShown && next.highest >= 2048) {
            wonShown = true
            showWin = true
            store.bump("wins")
        }
        if (!g2CanMove(next)) {
            gameOver = true
            newRecord = next.score > startBest
            store.setStr("save_${mode.id}", null)
        }
    }

    fun undo() {
        val p = prev ?: return
        board = p
        prev = null
        gameOver = false
        persist(p)
    }

    // Geister nach der Gleit-Animation entsorgen
    LaunchedEffect(board.moves) {
        if (board.tiles.any { it.ghost }) {
            delay(140)
            board = board.copy(tiles = board.live)
        }
    }

    BackHandler {
        when {
            confirmRestart -> confirmRestart = false
            showWin -> showWin = false
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(G2Accent)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                GxIconChip("←", onClick = onExit)
                Text(mode.label, fontSize = 15.sp, color = HikariTextMuted, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (undoOn) {
                        Box(Modifier.graphicsLayer { alpha = if (prev != null) 1f else 0.35f }) {
                            GxIconChip("↶") { undo() }
                        }
                    }
                    GxIconChip("↻") { confirmRestart = true }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Arrangement.spacedBy(8.dp),
                Alignment.CenterVertically,
            ) {
                G2ScoreCard("Punkte", gxAnimatedCount(board.score, 350), G2Accent, Modifier.weight(1f))
                G2ScoreCard("Best", best, HikariText, Modifier.weight(1f))
                G2ScoreCard("Kachel", board.highest, g2TileColor(board.highest), Modifier.weight(1f))
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    G2BoardView(
                        board = board,
                        onSwipe = { swipe(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Wischen zum Schieben · ${board.moves} Züge",
                        fontSize = 12.sp, color = HikariTextFaint,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showWin) {
            GxResultOverlay(
                title = "2048 erreicht!",
                subtitle = "Stark. Das Brett bleibt offen — wie weit kommst du?",
                accent = G2Accent,
                stats = listOf("Punkte" to "${board.score}", "Züge" to "${board.moves}"),
                primaryLabel = "Weiter spielen",
                onPrimary = { showWin = false },
                secondaryLabel = "Zum Menü",
                onSecondary = onExit,
                badge = "GESCHAFFT",
            )
        }
        if (gameOver) {
            GxResultOverlay(
                title = "Kein Zug mehr",
                subtitle = if (newRecord) "Neuer Bestwert auf ${mode.label}!" else "Höchste Kachel: ${board.highest}",
                accent = G2Accent,
                stats = listOf("Punkte" to "${board.score}", "Best" to "$best", "Züge" to "${board.moves}"),
                primaryLabel = "Nochmal",
                onPrimary = { restart() },
                secondaryLabel = if (undoOn && prev != null) "Zug zurück" else "Zum Menü",
                onSecondary = { if (undoOn && prev != null) undo() else onExit() },
                badge = if (newRecord) "REKORD" else null,
            )
        }
        if (confirmRestart) {
            GxConfirmDialog(
                title = "Neu starten?",
                text = "Der aktuelle Spielstand (${board.score} Punkte) geht verloren.",
                confirmLabel = "Neu starten",
                accent = G2Accent,
                danger = true,
                onConfirm = { confirmRestart = false; restart() },
                onDismiss = { confirmRestart = false },
            )
        }
    }
}

@Composable
private fun G2ScoreCard(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), fontSize = 9.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text("$value", fontSize = 18.sp, color = accent, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun G2BoardView(board: G2Board, onSwipe: (G2Dir) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var px by remember { mutableIntStateOf(0) }
    val n = board.size
    val gapPx = with(density) { g2Gap(n).toPx() }
    val cellPx = if (px == 0) 0f else (px - gapPx * (n + 1)) / n
    val threshold = with(density) { 36.dp.toPx() }

    Box(
        modifier
            .aspectRatio(1f)
            .onSizeChanged { px = it.width }
            .clip(RoundedCornerShape(if (n >= 8) 14.dp else 18.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(if (n >= 8) 14.dp else 18.dp))
            .pointerInput(Unit) {
                var acc = Offset.Zero
                var fired = false
                detectDragGestures(
                    onDragStart = { acc = Offset.Zero; fired = false },
                    onDrag = { change, drag ->
                        change.consume()
                        if (fired) return@detectDragGestures
                        acc += drag
                        val ax = abs(acc.x)
                        val ay = abs(acc.y)
                        if (ax > threshold || ay > threshold) {
                            fired = true
                            onSwipe(
                                if (ax > ay) (if (acc.x > 0) G2Dir.RIGHT else G2Dir.LEFT)
                                else (if (acc.y > 0) G2Dir.DOWN else G2Dir.UP)
                            )
                        }
                    },
                )
            },
    ) {
        if (px > 0) {
            val cellDp = with(density) { cellPx.toDp() }
            for (i in 0 until n * n) {
                val r = i / n
                val c = i % n
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (gapPx + c * (cellPx + gapPx)).roundToInt(),
                                (gapPx + r * (cellPx + gapPx)).roundToInt(),
                            )
                        }
                        .size(cellDp)
                        .clip(RoundedCornerShape(g2Corner(n)))
                        .background(HikariSurfaceHigh.copy(alpha = 0.55f)),
                )
            }
            // Geister zuerst (liegen unter den echten Kacheln)
            for (t in board.tiles.sortedBy { if (it.ghost) 0 else 1 }) {
                key(t.id) { G2TileView(t, cellPx, gapPx, n) }
            }
        }
    }
}

@Composable
private fun G2TileView(t: G2Tile, cellPx: Float, gapPx: Float, n: Int) {
    val density = LocalDensity.current
    val target = IntOffset(
        (gapPx + t.c * (cellPx + gapPx)).roundToInt(),
        (gapPx + t.r * (cellPx + gapPx)).roundToInt(),
    )
    val pos by animateIntOffsetAsState(target, tween(110, easing = FastOutSlowInEasing), label = "g2pos")
    val scale = remember { Animatable(if (t.spawned || t.merged) 0f else 1f) }
    LaunchedEffect(t.id) {
        when {
            t.merged -> {
                delay(100)
                scale.snapTo(1.22f)
                scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f))
            }
            t.spawned -> {
                delay(90)
                scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 800f))
            }
        }
    }
    val cellDp = with(density) { cellPx.toDp() }
    val digits = t.v.toString().length
    val fontFrac = when (digits) {
        1, 2 -> 0.42f
        3 -> 0.36f
        4 -> 0.30f
        else -> 0.25f
    }
    val color = g2TileColor(t.v)
    Box(
        Modifier
            .offset { pos }
            .size(cellDp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(g2Corner(n)))
            .background(
                Brush.verticalGradient(listOf(lerp(color, Color.White, 0.10f), color))
            )
            .then(
                if (t.v >= 2048) Modifier.border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(g2Corner(n)))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${t.v}",
            fontSize = (cellDp.value * fontFrac).sp,
            color = g2TextColor(t.v),
            fontWeight = FontWeight.Black,
        )
    }
}
