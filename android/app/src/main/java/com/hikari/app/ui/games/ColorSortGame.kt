package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

// ————— Color Sort —————
// Das Water-Sort-Prinzip: Farbschichten zwischen Röhren umschütten, bis jede
// Röhre einfarbig ist. Level werden deterministisch aus dem Level-Seed
// erzeugt und vor dem Start von einem Löser auf Lösbarkeit geprüft — der
// gleiche Löser liefert später die Hinweise.

private enum class CsScreen { MENU, GAME }

private enum class CsMode(val id: String, val label: String, val emoji: String, val desc: String) {
    LEVELS("levels", "Level-Reise", "🧪", "Von 3 Farben bis zum Farbchaos — jedes Level ist garantiert lösbar."),
    RANDOM("random", "Zufall", "🎲", "Farbanzahl wählen und ein frisches Zufallsrätsel sortieren."),
}

private val CsAccent = Color(0xFFF472B6)
internal const val CsCap = 4
private const val CsMaxColors = 11
private val CsRandomChoices = listOf(4, 6, 8, 10)

private val CsPalette = listOf(
    Color(0xFFF87171), Color(0xFF60A5FA), Color(0xFF4ADE80), Color(0xFFFBBF24),
    Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF22D3EE), Color(0xFFFB923C),
    Color(0xFFBEF264), Color(0xFF94A3B8), Color(0xFFB45309), Color(0xFFE879F9),
)

private class CsStore(val p: SharedPreferences) {
    fun getBool(k: String, d: Boolean) = p.getBoolean("csort_$k", d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean("csort_$k", v).apply()
    fun getInt(k: String, d: Int) = p.getInt("csort_$k", d)
    fun setInt(k: String, v: Int) = p.edit().putInt("csort_$k", v).apply()
    fun getStr(k: String): String? = p.getString("csort_$k", null)
    fun setStr(k: String, v: String?) = p.edit().putString("csort_$k", v).apply()
    fun bump(k: String, by: Int = 1) = setInt(k, getInt(k, 0) + by)
}

// ————— Logik —————

internal typealias CsTubes = List<List<Int>>

internal fun csUniform(t: List<Int>) = t.isNotEmpty() && t.all { it == t[0] }
internal fun csComplete(t: List<Int>) = t.size == CsCap && csUniform(t)
internal fun csIsSolved(t: CsTubes) = t.all { it.isEmpty() || csComplete(it) }

/** Wie viele Schichten würden von [from] nach [to] fließen? 0 = ungültiger Zug. */
internal fun csCanPour(t: CsTubes, from: Int, to: Int): Int {
    if (from == to) return 0
    val a = t[from]
    val b = t[to]
    if (a.isEmpty() || b.size >= CsCap) return 0
    val top = a.last()
    if (b.isNotEmpty() && b.last() != top) return 0
    var cnt = 0
    var i = a.lastIndex
    while (i >= 0 && a[i] == top) { cnt++; i-- }
    return minOf(cnt, CsCap - b.size)
}

internal fun csPour(t: CsTubes, from: Int, to: Int, n: Int): CsTubes {
    val out = t.map { it.toMutableList() }
    val moved = out[from].takeLast(n)
    repeat(n) { out[from].removeAt(out[from].lastIndex) }
    out[to].addAll(moved)
    return out
}

private fun csKey(t: CsTubes): String = t.map { it.joinToString(",") }.sorted().joinToString("|")

/**
 * Tiefensuche mit Memo. Bevorzugt Züge, die Röhren vervollständigen, und
 * überspringt sinnlose (einfarbig ins Leere, zweite leere Röhre).
 * Gibt die Zugfolge zurück oder null (unlösbar oder Knoten-Limit erreicht).
 */
internal fun csSolve(start: CsTubes, nodeLimit: Int = 120_000): List<Pair<Int, Int>>? {
    val visited = HashSet<String>()
    var nodes = 0
    val path = ArrayList<Pair<Int, Int>>()

    fun dfs(t: CsTubes): Boolean {
        if (csIsSolved(t)) return true
        if (++nodes > nodeLimit) return false
        if (!visited.add(csKey(t))) return false
        val firstEmpty = t.indexOfFirst { it.isEmpty() }
        val moves = ArrayList<Pair<Int, Int>>()
        val prio = HashMap<Pair<Int, Int>, Int>()
        for (from in t.indices) {
            val a = t[from]
            if (a.isEmpty() || csComplete(a)) continue
            for (to in t.indices) {
                if (from == to) continue
                val n = csCanPour(t, from, to)
                if (n == 0) continue
                val b = t[to]
                if (b.isEmpty() && (csUniform(a) || to != firstEmpty)) continue
                var p = 0
                if (b.isNotEmpty()) p += 2
                if (b.isNotEmpty() && b.size + n == CsCap) p += 4
                if (n == a.size) p += 1
                val m = from to to
                moves += m
                prio[m] = p
            }
        }
        moves.sortByDescending { prio[it] ?: 0 }
        for (m in moves) {
            path += m
            if (dfs(csPour(t, m.first, m.second, csCanPour(t, m.first, m.second)))) return true
            path.removeAt(path.lastIndex)
            if (nodes > nodeLimit) return false
        }
        return false
    }
    return if (dfs(start)) path.toList() else null
}

internal fun csColorsForLevel(level: Int) = minOf(3 + (level - 1) / 2, CsMaxColors)

internal fun csGenerate(colors: Int, seed: Long): CsTubes {
    val rnd = Random(seed)
    var attempt = 0
    while (true) {
        val units = ArrayList<Int>(colors * CsCap)
        for (c in 0 until colors) repeat(CsCap) { units += c }
        units.shuffle(rnd)
        val tubes: CsTubes = units.chunked(CsCap) + List(2) { emptyList<Int>() }
        val ok = tubes.none { csComplete(it) } && csSolve(tubes, 60_000) != null
        if (ok || attempt++ >= 25) return tubes
    }
}

// ————— Root —————

@Composable
fun ColorSortGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { CsStore(context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)) }

    var screen by remember { mutableStateOf(CsScreen.MENU) }
    var mode by remember {
        mutableStateOf(CsMode.entries.firstOrNull { it.id == store.getStr("last_mode") } ?: CsMode.LEVELS)
    }
    var level by remember { mutableIntStateOf(store.getInt("level", 1)) }
    var maxLevel by remember { mutableIntStateOf(store.getInt("max_level", 1)) }
    var randomIdx by remember { mutableIntStateOf(store.getInt("rand_idx", 1).coerceIn(0, CsRandomChoices.lastIndex)) }
    var hapticsOn by remember { mutableStateOf(store.getBool("haptics", true)) }
    var markDone by remember { mutableStateOf(store.getBool("mark_done", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var gameKey by remember { mutableIntStateOf(0) }
    var randomSeed by remember { mutableStateOf(System.currentTimeMillis()) }

    fun buzz(t: HapticFeedbackType) { if (hapticsOn) haptic.performHapticFeedback(t) }

    BackHandler(enabled = screen == CsScreen.MENU) {
        when {
            showSettings -> showSettings = false
            showStats -> showStats = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Crossfade(targetState = screen, animationSpec = tween(220), label = "csScreen") { s ->
            when (s) {
                CsScreen.MENU -> CsMenuScreen(
                    store = store,
                    mode = mode, onMode = { mode = it },
                    level = level, maxLevel = maxLevel, onLevel = { level = it; store.setInt("level", it) },
                    randomIdx = randomIdx, onRandomIdx = { randomIdx = it; store.setInt("rand_idx", it) },
                    onStart = {
                        store.setStr("last_mode", mode.id)
                        if (mode == CsMode.RANDOM) randomSeed = System.currentTimeMillis()
                        gameKey++
                        screen = CsScreen.GAME
                    },
                    onStats = { showStats = true },
                    onSettings = { showSettings = true },
                    onBack = onBack,
                )
                CsScreen.GAME -> key(gameKey) {
                    CsPlayScreen(
                        mode = mode,
                        level = level,
                        colors = if (mode == CsMode.LEVELS) csColorsForLevel(level) else CsRandomChoices[randomIdx],
                        seed = if (mode == CsMode.LEVELS) level * 7919L + 17 else randomSeed,
                        store = store,
                        markDone = markDone,
                        buzz = { buzz(it) },
                        onSolved = {
                            store.bump("solved_total")
                            if (mode == CsMode.LEVELS && level >= maxLevel) {
                                maxLevel = level + 1
                                store.setInt("max_level", maxLevel)
                            }
                        },
                        onNext = {
                            if (mode == CsMode.LEVELS) {
                                level += 1
                                store.setInt("level", level)
                            } else {
                                randomSeed = System.currentTimeMillis()
                            }
                            gameKey++
                        },
                        onExit = { screen = CsScreen.MENU },
                    )
                }
            }
        }

        if (showSettings) {
            GxSheet("Optionen", CsAccent, onClose = { showSettings = false }) {
                GxToggle("Haptik", "Vibration beim Umschütten.", CsAccent, hapticsOn) {
                    hapticsOn = it; store.setBool("haptics", it)
                }
                GxToggle("Fertige Röhren markieren", "Einfarbig volle Röhren bekommen einen Haken.", CsAccent, markDone) {
                    markDone = it; store.setBool("mark_done", it)
                }
            }
        }
        if (showStats) {
            GxSheet("Statistik", CsAccent, onClose = { showStats = false }) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    GxStatTile("${maxLevel - 1}", "Level geschafft", CsAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("solved_total", 0)}", "Rätsel gelöst", CsAccent, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    GxStatTile("${store.getInt("moves_total", 0)}", "Züge gesamt", CsAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("hints_total", 0)}", "Hinweise", CsAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("undos_total", 0)}", "Rückgängig", CsAccent, Modifier.weight(1f))
                }
            }
        }
    }
}

// ————— Menü —————

@Composable
private fun CsMenuScreen(
    store: CsStore,
    mode: CsMode, onMode: (CsMode) -> Unit,
    level: Int, maxLevel: Int, onLevel: (Int) -> Unit,
    randomIdx: Int, onRandomIdx: (Int) -> Unit,
    onStart: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    var showLevels by remember { mutableStateOf(false) }
    BackHandler(enabled = showRules || showLevels) { showRules = false; showLevels = false }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(CsAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Color Sort", CsAccent, onBack = onBack, right = { GxIconChip("?") { showRules = true } })
            Column(Modifier.padding(horizontal = 16.dp)) {
                GxSectionTitle("Modus")
                CsMode.entries.forEachIndexed { i, m ->
                    GxAppear(i) {
                        GxModeCard(
                            emoji = m.emoji,
                            title = m.label,
                            subtitle = m.desc,
                            accent = CsAccent,
                            highlighted = mode == m,
                            best = when (m) {
                                CsMode.LEVELS -> "Level $level · ${csColorsForLevel(level)} Farben"
                                CsMode.RANDOM -> "${CsRandomChoices[randomIdx]} Farben"
                            },
                            onClick = { onMode(m) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                GxAppear(2) {
                    Column {
                        if (mode == CsMode.RANDOM) {
                            GxSectionTitle("Farben")
                            GxSegmented(CsRandomChoices.map { "$it" }, randomIdx, CsAccent) { onRandomIdx(it) }
                        } else if (maxLevel > 1) {
                            GxSectionTitle("Level")
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                                GxGhostButton("Level wählen", Modifier.weight(1f)) { showLevels = true }
                                if (level != maxLevel) {
                                    GxGhostButton("Zum neuesten", Modifier.weight(1f)) { onLevel(maxLevel) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                GxAppear(3) {
                    GxPrimaryButton(
                        if (mode == CsMode.LEVELS) "Level $level spielen" else "Rätsel starten",
                        CsAccent, Modifier.fillMaxWidth(), onClick = onStart,
                    )
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
            GxSheet("So geht's", CsAccent, onClose = { showRules = false }) {
                Text(
                    "Tippe eine Röhre an, um sie anzuheben, und dann eine zweite, um die obere Farbe " +
                        "hinüberzugießen. Gießen geht nur in leere Röhren oder auf dieselbe Farbe — und nur " +
                        "so viel, wie hineinpasst.\n\nFertig bist du, wenn jede Röhre komplett einfarbig ist. " +
                        "Zwei leere Röhren sind dein Spielraum: Nutze sie, um Farben zwischenzuparken, " +
                        "statt sie sofort zu füllen.",
                    fontSize = 13.sp, color = HikariTextMuted, lineHeight = 19.sp,
                )
            }
        }
        if (showLevels) {
            GxSheet("Level wählen", CsAccent, onClose = { showLevels = false }) {
                (1..maxLevel).chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        row.forEach { lv ->
                            val best = store.getInt("best_$lv", 0)
                            val current = lv == level
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (current) CsAccent.copy(alpha = 0.18f) else HikariCardBg)
                                    .border(
                                        1.dp,
                                        if (current) CsAccent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .gxPressable { onLevel(lv); showLevels = false }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$lv", fontSize = 15.sp, color = if (current) CsAccent else HikariText, fontWeight = FontWeight.Black)
                                Text(
                                    if (lv == maxLevel) "neu" else if (best > 0) "$best Z." else "–",
                                    fontSize = 10.sp, color = HikariTextFaint,
                                )
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ————— Spiel —————

@Composable
private fun CsPlayScreen(
    mode: CsMode,
    level: Int,
    colors: Int,
    seed: Long,
    store: CsStore,
    markDone: Boolean,
    buzz: (HapticFeedbackType) -> Unit,
    onSolved: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    var initial by remember { mutableStateOf<CsTubes?>(null) }
    var tubes by remember { mutableStateOf<CsTubes>(emptyList()) }
    var history by remember { mutableStateOf<List<CsTubes>>(emptyList()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var moves by remember { mutableIntStateOf(0) }
    var hintsLeft by remember { mutableIntStateOf(3) }
    var hintMove by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hintBusy by remember { mutableStateOf(false) }
    var hintFailed by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var bumpTube by remember { mutableStateOf(-1 to 0) }
    val bestKey = if (mode == CsMode.LEVELS) "best_$level" else null
    var best by remember { mutableIntStateOf(bestKey?.let { store.getInt(it, 0) } ?: 0) }

    LaunchedEffect(seed, colors) {
        val gen = withContext(Dispatchers.Default) { csGenerate(colors, seed) }
        initial = gen
        tubes = gen
    }

    LaunchedEffect(hintMove) {
        if (hintMove != null) {
            kotlinx.coroutines.delay(2600)
            hintMove = null
        }
    }

    fun restart() {
        val g = initial ?: return
        tubes = g
        history = emptyList()
        selected = null
        moves = 0
        hintMove = null
        solved = false
    }

    fun finish() {
        solved = true
        store.bump("moves_total", moves)
        if (bestKey != null && (best == 0 || moves < best)) {
            best = moves
            store.setInt(bestKey, moves)
        }
        buzz(HapticFeedbackType.LongPress)
        onSolved()
    }

    fun tap(i: Int) {
        if (solved || tubes.isEmpty()) return
        val sel = selected
        when {
            sel == null -> {
                if (tubes[i].isNotEmpty() && !(markDone && csComplete(tubes[i]))) {
                    selected = i
                    buzz(HapticFeedbackType.TextHandleMove)
                }
            }
            sel == i -> selected = null
            else -> {
                val n = csCanPour(tubes, sel, i)
                if (n == 0) {
                    // Auswahl wechseln, falls die neue Röhre gießbar ist
                    selected = if (tubes[i].isNotEmpty() && !(markDone && csComplete(tubes[i]))) i else null
                    return
                }
                history = (history + listOf(tubes)).takeLast(60)
                tubes = csPour(tubes, sel, i, n)
                selected = null
                moves += 1
                hintMove = null
                bumpTube = i to (bumpTube.second + 1)
                buzz(HapticFeedbackType.TextHandleMove)
                if (csIsSolved(tubes)) finish()
            }
        }
    }

    fun undo() {
        val last = history.lastOrNull() ?: return
        tubes = last
        history = history.dropLast(1)
        selected = null
        moves += 1
        hintMove = null
        store.bump("undos_total")
    }

    LaunchedEffect(hintBusy) {
        if (!hintBusy) return@LaunchedEffect
        val snapshot = tubes
        val sol = withContext(Dispatchers.Default) { csSolve(snapshot) }
        hintBusy = false
        if (sol.isNullOrEmpty()) {
            hintFailed = true
        } else {
            hintMove = sol.first()
            hintsLeft -= 1
            selected = null
            store.bump("hints_total")
        }
    }
    LaunchedEffect(hintFailed) {
        if (hintFailed) { kotlinx.coroutines.delay(2200); hintFailed = false }
    }

    BackHandler {
        when {
            confirmRestart -> confirmRestart = false
            else -> onExit()
        }
    }

    val title = if (mode == CsMode.LEVELS) "Level $level" else "Zufall · $colors Farben"

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(CsAccent)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                GxIconChip("←", onClick = onExit)
                Text(title, fontSize = 15.sp, color = HikariTextMuted, fontWeight = FontWeight.Bold)
                GxIconChip("↻") { confirmRestart = true }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                GxHudPill("Züge", "$moves", CsAccent)
                if (bestKey != null) GxHudPill("Best", if (best > 0) "$best" else "–")
                GxHudPill("Röhren", "${tubes.count { csComplete(it) }}/$colors")
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (tubes.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GxProgressRing(0.75f, CsAccent, size = 48.dp) {}
                        Spacer(Modifier.height(12.dp))
                        Text("Rätsel wird gemischt …", fontSize = 13.sp, color = HikariTextMuted)
                    }
                } else {
                    val count = tubes.size
                    val perRow = when {
                        count <= 6 -> 3
                        count <= 8 -> 4
                        else -> 5
                    }
                    val tubeW: Dp = when (perRow) { 3 -> 54.dp; 4 -> 48.dp; else -> 42.dp }
                    val tubeH: Dp = tubeW * 3.3f
                    Column(
                        Modifier.padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        tubes.indices.chunked(perRow).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (perRow == 5) 14.dp else 20.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                row.forEach { i ->
                                    CsTube(
                                        layers = tubes[i],
                                        selected = selected == i,
                                        hintFrom = hintMove?.first == i,
                                        hintTo = hintMove?.second == i,
                                        done = markDone && csComplete(tubes[i]),
                                        bumpKey = if (bumpTube.first == i) bumpTube.second else 0,
                                        width = tubeW,
                                        height = tubeH,
                                        onClick = { tap(i) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (hintFailed) {
                Text(
                    "Von hier aus finde ich keinen Weg — nimm Züge zurück.",
                    fontSize = 12.sp, color = CsAccent,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f).graphicsLayer { alpha = if (history.isEmpty()) 0.4f else 1f }) {
                    GxSmallAction("↶", "Rückgängig", Modifier.fillMaxWidth()) { undo() }
                }
                Box(Modifier.weight(1f).graphicsLayer { alpha = if (hintsLeft <= 0 || hintBusy) 0.4f else 1f }) {
                    GxSmallAction("💡", if (hintBusy) "Suche …" else "Hinweis · $hintsLeft", Modifier.fillMaxWidth()) {
                        if (hintsLeft > 0 && !hintBusy && !solved) hintBusy = true
                    }
                }
            }
        }

        if (solved) {
            val perfect = bestKey != null && moves == best
            GxResultOverlay(
                title = if (mode == CsMode.LEVELS) "Level $level geschafft!" else "Sortiert!",
                subtitle = when {
                    mode == CsMode.LEVELS && perfect && history.isNotEmpty() -> "Neue Bestmarke für dieses Level."
                    mode == CsMode.LEVELS -> "Als Nächstes: ${csColorsForLevel(level + 1)} Farben."
                    else -> "Alle $colors Farben sauber getrennt."
                },
                accent = CsAccent,
                stats = buildList {
                    add("Züge" to "$moves")
                    if (bestKey != null) add("Best" to "$best")
                    add("Hinweise" to "${3 - hintsLeft}")
                },
                primaryLabel = if (mode == CsMode.LEVELS) "Nächstes Level" else "Neues Rätsel",
                onPrimary = onNext,
                secondaryLabel = "Zum Menü",
                onSecondary = onExit,
                badge = if (perfect && mode == CsMode.LEVELS) "BESTZEIT" else "GELÖST",
            )
        }
        if (confirmRestart) {
            GxConfirmDialog(
                title = "Neu anfangen?",
                text = "Alle Züge dieses Rätsels werden zurückgesetzt.",
                confirmLabel = "Neu starten",
                accent = CsAccent,
                onConfirm = { confirmRestart = false; restart() },
                onDismiss = { confirmRestart = false },
            )
        }
    }
}

@Composable
private fun CsTube(
    layers: List<Int>,
    selected: Boolean,
    hintFrom: Boolean,
    hintTo: Boolean,
    done: Boolean,
    bumpKey: Int,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "csLift")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(bumpKey) {
        if (bumpKey > 0) {
            bump.snapTo(1.06f)
            bump.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f))
        }
    }
    val pulse by rememberInfiniteTransition(label = "csHint").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "csHintT",
    )
    val hinted = hintFrom || hintTo

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(width, height)
                .graphicsLayer {
                    translationY = -lift * 16.dp.toPx()
                    scaleX = bump.value
                    scaleY = bump.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            val w = size.width
            val h = size.height
            val rTop = 3.dp.toPx()
            val rBot = w * 0.42f
            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        0f, 0f, w, h,
                        topLeftCornerRadius = CornerRadius(rTop),
                        topRightCornerRadius = CornerRadius(rTop),
                        bottomLeftCornerRadius = CornerRadius(rBot),
                        bottomRightCornerRadius = CornerRadius(rBot),
                    )
                )
            }
            // Glaskörper
            drawPath(body, Color.White.copy(alpha = 0.05f))
            clipPath(body) {
                val inset = 2.dp.toPx()
                val segH = (h - inset * 2 - 4.dp.toPx()) / CsCap
                layers.forEachIndexed { i, c ->
                    val color = CsPalette[c % CsPalette.size]
                    val top = h - inset - (i + 1) * segH
                    drawRect(color, Offset(inset, top), Size(w - inset * 2, segH + 0.5f))
                    // Glanzkante oben auf jeder Schicht
                    drawRect(Color.White.copy(alpha = 0.16f), Offset(inset, top), Size(w - inset * 2, 1.5.dp.toPx()))
                }
                // Reflex links
                drawRect(Color.White.copy(alpha = 0.08f), Offset(inset + 2.dp.toPx(), inset), Size(w * 0.14f, h))
            }
            val outline = when {
                hinted -> lerp(Color.White.copy(alpha = 0.18f), CsAccent, 0.4f + 0.6f * pulse)
                selected -> CsAccent.copy(alpha = 0.8f)
                done -> Color.White.copy(alpha = 0.30f)
                else -> Color.White.copy(alpha = 0.18f)
            }
            drawPath(body, outline, style = Stroke(if (hinted || selected) 2.dp.toPx() else 1.5.dp.toPx()))
            if (hintTo) {
                // Pfeil-Punkt über der Zielröhre
                drawCircle(CsAccent.copy(alpha = 0.5f + 0.5f * pulse), radius = 3.dp.toPx(), center = Offset(w / 2, -8.dp.toPx() + sin(pulse * 3.14f) * 3f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (done) "✓" else " ",
            fontSize = 12.sp,
            color = CsAccent,
            fontWeight = FontWeight.Black,
        )
    }
}
