package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ————— Sudoku —————
// Klassisches 9×9 mit eigenem Generator: volles Gitter per randomisiertem
// Backtracking, danach Felder entfernen, solange die Lösung eindeutig bleibt.
// Notizen, Fehlerlimit, Hinweise, Timer, automatisches Speichern/Fortsetzen.

private enum class SdScreen { MENU, GAME }

internal enum class SdDiff(val id: String, val label: String, val emoji: String, val desc: String, val givens: Int) {
    EASY("easy", "Leicht", "🌱", "Viele Vorgaben — entspannt reinkommen.", 42),
    MEDIUM("medium", "Mittel", "🧠", "Braucht Notizen und etwas Geduld.", 36),
    HARD("hard", "Schwer", "🔥", "Wenige Vorgaben — echte Logik gefragt.", 30),
    EXPERT("expert", "Experte", "💀", "Für Profis. Nichts für nebenbei.", 26),
}

private val SdAccent = Color(0xFF2DD4BF)
private val SdUserColor = Color(0xFF7DD3FC)

private class SdStore(val p: SharedPreferences) {
    fun getBool(k: String, d: Boolean) = p.getBoolean("sudoku_$k", d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean("sudoku_$k", v).apply()
    fun getInt(k: String, d: Int) = p.getInt("sudoku_$k", d)
    fun setInt(k: String, v: Int) = p.edit().putInt("sudoku_$k", v).apply()
    fun getStr(k: String): String? = p.getString("sudoku_$k", null)
    fun setStr(k: String, v: String?) = p.edit().putString("sudoku_$k", v).apply()
    fun bump(k: String, by: Int = 1) = setInt(k, getInt(k, 0) + by)
}

// ————— Generator / Löser —————

internal fun sdValid(g: IntArray, idx: Int, v: Int): Boolean {
    val r = idx / 9
    val c = idx % 9
    for (i in 0 until 9) {
        if (g[r * 9 + i] == v) return false
        if (g[i * 9 + c] == v) return false
    }
    val br = r / 3 * 3
    val bc = c / 3 * 3
    for (i in 0 until 3) for (j in 0 until 3) if (g[(br + i) * 9 + bc + j] == v) return false
    return true
}

private fun sdFill(g: IntArray, rnd: Random): Boolean {
    val idx = g.indexOfFirst { it == 0 }
    if (idx < 0) return true
    for (d in (1..9).shuffled(rnd)) {
        if (sdValid(g, idx, d)) {
            g[idx] = d
            if (sdFill(g, rnd)) return true
            g[idx] = 0
        }
    }
    return false
}

/** Zählt Lösungen bis [limit] — mit "wenigste Kandidaten zuerst", damit es schnell bleibt. */
internal fun sdCountSolutions(g: IntArray, limit: Int): Int {
    var bestIdx = -1
    var bestCands: List<Int>? = null
    for (i in 0 until 81) {
        if (g[i] != 0) continue
        val cands = (1..9).filter { sdValid(g, i, it) }
        if (cands.isEmpty()) return 0
        if (bestCands == null || cands.size < bestCands.size) {
            bestIdx = i
            bestCands = cands
            if (cands.size == 1) break
        }
    }
    if (bestIdx < 0) return 1
    var count = 0
    for (d in bestCands!!) {
        g[bestIdx] = d
        count += sdCountSolutions(g, limit - count)
        g[bestIdx] = 0
        if (count >= limit) break
    }
    return count
}

internal fun sdGenerate(diff: SdDiff, rnd: Random): Pair<IntArray, IntArray> {
    val sol = IntArray(81)
    sdFill(sol, rnd)
    val puzzle = sol.copyOf()
    var givens = 81
    for (i in (0 until 81).shuffled(rnd)) {
        if (givens <= diff.givens) break
        val backup = puzzle[i]
        puzzle[i] = 0
        if (sdCountSolutions(puzzle.copyOf(), 2) != 1) puzzle[i] = backup else givens--
    }
    return puzzle to sol
}

internal fun sdPeers(idx: Int): List<Int> {
    val r = idx / 9
    val c = idx % 9
    val br = r / 3 * 3
    val bc = c / 3 * 3
    val out = HashSet<Int>()
    for (i in 0 until 9) { out += r * 9 + i; out += i * 9 + c }
    for (i in 0 until 3) for (j in 0 until 3) out += (br + i) * 9 + bc + j
    out -= idx
    return out.toList()
}

private fun sdFormatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

// Spielstand: diff|puzzle|solution|cells|notes-csv|elapsed|mistakes|hints
internal fun sdSerialize(
    diff: SdDiff, puzzle: IntArray, solution: IntArray, cells: IntArray, notes: IntArray,
    elapsed: Int, mistakes: Int, hints: Int,
): String = listOf(
    diff.id,
    puzzle.joinToString(""),
    solution.joinToString(""),
    cells.joinToString(""),
    notes.joinToString(","),
    "$elapsed", "$mistakes", "$hints",
).joinToString("|")

internal class SdSaved(
    val diff: SdDiff, val puzzle: IntArray, val solution: IntArray, val cells: IntArray,
    val notes: IntArray, val elapsed: Int, val mistakes: Int, val hints: Int,
)

internal fun sdDeserialize(s: String): SdSaved? = runCatching {
    val p = s.split("|")
    val diff = SdDiff.entries.first { it.id == p[0] }
    fun grid(str: String) = IntArray(81) { str[it] - '0' }
    SdSaved(
        diff, grid(p[1]), grid(p[2]), grid(p[3]),
        p[4].split(",").map { it.toInt() }.toIntArray(),
        p[5].toInt(), p[6].toInt(), p[7].toInt(),
    )
}.getOrNull()

// ————— Root —————

@Composable
fun SudokuGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { SdStore(context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)) }

    var screen by remember { mutableStateOf(SdScreen.MENU) }
    var diff by remember {
        mutableStateOf(SdDiff.entries.firstOrNull { it.id == store.getStr("last_diff") } ?: SdDiff.EASY)
    }
    var hapticsOn by remember { mutableStateOf(store.getBool("haptics", true)) }
    var limitOn by remember { mutableStateOf(store.getBool("limit", true)) }
    var showErrors by remember { mutableStateOf(store.getBool("show_errors", true)) }
    var highlightSame by remember { mutableStateOf(store.getBool("hl_same", true)) }
    var showTimer by remember { mutableStateOf(store.getBool("timer", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var gameKey by remember { mutableIntStateOf(0) }
    var resume by remember { mutableStateOf(false) }
    var hasSave by remember { mutableStateOf(store.getStr("save") != null) }

    fun buzz(t: HapticFeedbackType) { if (hapticsOn) haptic.performHapticFeedback(t) }

    BackHandler(enabled = screen == SdScreen.MENU) {
        when {
            showSettings -> showSettings = false
            showStats -> showStats = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Crossfade(targetState = screen, animationSpec = tween(220), label = "sdScreen") { s ->
            when (s) {
                SdScreen.MENU -> SdMenuScreen(
                    store = store,
                    diff = diff, onDiff = { diff = it },
                    hasSave = hasSave,
                    savedLabel = store.getStr("save")?.let { sdDeserialize(it) }?.let {
                        "${it.diff.label} · ${sdFormatTime(it.elapsed)}"
                    },
                    onStart = { cont ->
                        store.setStr("last_diff", diff.id)
                        resume = cont
                        gameKey++
                        screen = SdScreen.GAME
                    },
                    onStats = { showStats = true },
                    onSettings = { showSettings = true },
                    onBack = onBack,
                )
                SdScreen.GAME -> key(gameKey) {
                    SdPlayScreen(
                        diff = diff,
                        store = store,
                        resume = resume,
                        limitOn = limitOn,
                        showErrors = showErrors,
                        highlightSame = highlightSame,
                        showTimer = showTimer,
                        buzz = { buzz(it) },
                        onSaveChanged = { hasSave = it },
                        onExit = { screen = SdScreen.MENU },
                    )
                }
            }
        }

        if (showSettings) {
            GxSheet("Optionen", SdAccent, onClose = { showSettings = false }) {
                GxToggle("Haptik", "Vibration beim Setzen und bei Fehlern.", SdAccent, hapticsOn) {
                    hapticsOn = it; store.setBool("haptics", it)
                }
                GxToggle("Fehlerlimit", "Nach 3 Fehlern ist die Partie vorbei.", SdAccent, limitOn) {
                    limitOn = it; store.setBool("limit", it)
                }
                GxToggle("Fehler markieren", "Falsche Zahlen sofort rot zeigen.", SdAccent, showErrors) {
                    showErrors = it; store.setBool("show_errors", it)
                }
                GxToggle("Gleiche Zahlen hervorheben", "Alle Felder mit der gewählten Zahl leuchten.", SdAccent, highlightSame) {
                    highlightSame = it; store.setBool("hl_same", it)
                }
                GxToggle("Timer anzeigen", "Zeit läuft im Hintergrund trotzdem mit.", SdAccent, showTimer) {
                    showTimer = it; store.setBool("timer", it)
                }
            }
        }
        if (showStats) {
            GxSheet("Statistik", SdAccent, onClose = { showStats = false }) {
                SdDiff.entries.forEach { d ->
                    val solved = store.getInt("solved_${d.id}", 0)
                    val best = store.getInt("best_${d.id}", 0)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically,
                    ) {
                        Text("${d.emoji}  ${d.label}", fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold)
                        Text(
                            "$solved gelöst · Best ${if (best > 0) sdFormatTime(best) else "–"}",
                            fontSize = 12.sp, color = HikariTextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    GxStatTile("${store.getInt("solved_total", 0)}", "Gelöst", SdAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("started_total", 0)}", "Gestartet", SdAccent, Modifier.weight(1f))
                    GxStatTile("${store.getInt("hints_total", 0)}", "Hinweise", SdAccent, Modifier.weight(1f))
                }
            }
        }
    }
}

// ————— Menü —————

@Composable
private fun SdMenuScreen(
    store: SdStore,
    diff: SdDiff, onDiff: (SdDiff) -> Unit,
    hasSave: Boolean,
    savedLabel: String?,
    onStart: (resume: Boolean) -> Unit,
    onStats: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    BackHandler(enabled = showRules) { showRules = false }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SdAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Sudoku", SdAccent, onBack = onBack, right = { GxIconChip("?") { showRules = true } })
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (hasSave && savedLabel != null) {
                    GxAppear(0) {
                        Column {
                            GxSectionTitle("Angefangen")
                            GxModeCard(
                                emoji = "⏯️",
                                title = "Fortsetzen",
                                subtitle = savedLabel,
                                accent = SdAccent,
                                highlighted = true,
                                onClick = { onStart(true) },
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                GxSectionTitle("Schwierigkeit")
                SdDiff.entries.forEachIndexed { i, d ->
                    val solved = store.getInt("solved_${d.id}", 0)
                    val best = store.getInt("best_${d.id}", 0)
                    GxAppear(i + 1) {
                        GxModeCard(
                            emoji = d.emoji,
                            title = d.label,
                            subtitle = d.desc,
                            accent = SdAccent,
                            highlighted = diff == d,
                            best = if (solved > 0) "$solved gelöst · Best ${sdFormatTime(best)}" else null,
                            onClick = { onDiff(d) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(14.dp))
                GxAppear(5) {
                    GxPrimaryButton("Neues Sudoku", SdAccent, Modifier.fillMaxWidth()) { onStart(false) }
                }
                Spacer(Modifier.height(12.dp))
                GxAppear(6) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f), onSettings)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (showRules) {
            GxSheet("So geht's", SdAccent, onClose = { showRules = false }) {
                Text(
                    "Fülle das Gitter so, dass jede Zeile, jede Spalte und jeder 3×3-Block die Zahlen 1 bis 9 " +
                        "genau einmal enthält.\n\nTippe ein Feld an und wähle unten eine Zahl. Mit „Notizen“ " +
                        "trägst du Kandidaten klein ein — sie verschwinden automatisch, sobald die Zahl in der " +
                        "Zeile, Spalte oder im Block gesetzt wird. „Hinweis“ füllt ein Feld korrekt aus.",
                    fontSize = 13.sp, color = HikariTextMuted, lineHeight = 19.sp,
                )
            }
        }
    }
}

// ————— Spiel —————

@Composable
private fun SdPlayScreen(
    diff: SdDiff,
    store: SdStore,
    resume: Boolean,
    limitOn: Boolean,
    showErrors: Boolean,
    highlightSame: Boolean,
    showTimer: Boolean,
    buzz: (HapticFeedbackType) -> Unit,
    onSaveChanged: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val saved = remember { if (resume) store.getStr("save")?.let { sdDeserialize(it) } else null }
    val activeDiff = saved?.diff ?: diff
    var puzzle by remember { mutableStateOf(saved?.puzzle) }
    var solution by remember { mutableStateOf(saved?.solution) }
    var cells by remember { mutableStateOf(saved?.cells ?: IntArray(81)) }
    var notes by remember { mutableStateOf(saved?.notes ?: IntArray(81)) }
    var elapsed by remember { mutableIntStateOf(saved?.elapsed ?: 0) }
    var mistakes by remember { mutableIntStateOf(saved?.mistakes ?: 0) }
    var hints by remember { mutableIntStateOf(saved?.hints ?: 0) }
    var selected by remember { mutableIntStateOf(-1) }
    var notesMode by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    val undo = remember { ArrayList<Pair<IntArray, IntArray>>() }
    var undoSize by remember { mutableIntStateOf(0) }
    var genKey by remember { mutableIntStateOf(0) }

    val generating = puzzle == null

    LaunchedEffect(genKey) {
        if (puzzle != null) return@LaunchedEffect
        val (p, s) = withContext(Dispatchers.Default) { sdGenerate(activeDiff, Random(System.nanoTime())) }
        puzzle = p
        solution = s
        cells = p.copyOf()
        notes = IntArray(81)
        store.bump("started_total")
    }

    fun persist() {
        val p = puzzle ?: return
        val s = solution ?: return
        if (solved || failed) {
            store.setStr("save", null)
            onSaveChanged(false)
        } else {
            store.setStr("save", sdSerialize(activeDiff, p, s, cells, notes, elapsed, mistakes, hints))
            onSaveChanged(true)
        }
    }

    // Timer
    val running = !generating && !paused && !solved && !failed
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsed += 1
            if (elapsed % 10 == 0) persist()
        }
    }

    fun snapshot() {
        undo += cells.copyOf() to notes.copyOf()
        if (undo.size > 60) undo.removeAt(0)
        undoSize = undo.size
    }

    fun checkSolved() {
        val s = solution ?: return
        if (cells.contentEquals(s)) {
            solved = true
            store.bump("solved_total")
            store.bump("solved_${activeDiff.id}")
            val best = store.getInt("best_${activeDiff.id}", 0)
            if (best == 0 || elapsed < best) {
                store.setInt("best_${activeDiff.id}", elapsed)
                newBest = true
            }
            buzz(HapticFeedbackType.LongPress)
            persist()
        }
    }

    fun place(v: Int) {
        val p = puzzle ?: return
        val s = solution ?: return
        val i = selected
        if (i < 0 || p[i] != 0 || solved || failed || paused) return
        if (notesMode) {
            if (cells[i] != 0) return
            snapshot()
            notes = notes.copyOf().also { it[i] = it[i] xor (1 shl v) }
            buzz(HapticFeedbackType.TextHandleMove)
            persist()
            return
        }
        if (cells[i] == v) return
        snapshot()
        cells = cells.copyOf().also { it[i] = v }
        notes = notes.copyOf().also { n ->
            n[i] = 0
            for (pi in sdPeers(i)) n[pi] = n[pi] and (1 shl v).inv()
        }
        if (v != s[i]) {
            mistakes += 1
            buzz(HapticFeedbackType.LongPress)
            if (limitOn && mistakes >= 3) {
                failed = true
                persist()
                return
            }
        } else {
            buzz(HapticFeedbackType.TextHandleMove)
        }
        persist()
        checkSolved()
    }

    fun erase() {
        val p = puzzle ?: return
        val i = selected
        if (i < 0 || p[i] != 0 || solved || failed) return
        if (cells[i] == 0 && notes[i] == 0) return
        snapshot()
        cells = cells.copyOf().also { it[i] = 0 }
        notes = notes.copyOf().also { it[i] = 0 }
        persist()
    }

    fun undoMove() {
        val last = undo.removeLastOrNull() ?: return
        undoSize = undo.size
        cells = last.first
        notes = last.second
        persist()
    }

    fun hint() {
        val p = puzzle ?: return
        val s = solution ?: return
        if (solved || failed) return
        val target = if (selected >= 0 && p[selected] == 0 && cells[selected] != s[selected]) selected
        else (0 until 81).filter { p[it] == 0 && cells[it] != s[it] }.randomOrNull() ?: return
        snapshot()
        selected = target
        cells = cells.copyOf().also { it[target] = s[target] }
        notes = notes.copyOf().also { n ->
            n[target] = 0
            for (pi in sdPeers(target)) n[pi] = n[pi] and (1 shl s[target]).inv()
        }
        hints += 1
        store.bump("hints_total")
        buzz(HapticFeedbackType.TextHandleMove)
        persist()
        checkSolved()
    }

    fun newGame() {
        store.setStr("save", null)
        onSaveChanged(false)
        puzzle = null
        solution = null
        cells = IntArray(81)
        notes = IntArray(81)
        elapsed = 0
        mistakes = 0
        hints = 0
        selected = -1
        solved = false
        failed = false
        newBest = false
        undo.clear()
        undoSize = 0
        genKey++
    }

    BackHandler {
        when {
            confirmNew -> confirmNew = false
            paused -> paused = false
            else -> { persist(); onExit() }
        }
    }

    val counts = remember(cells) { IntArray(10).also { for (v in cells) it[v]++ } }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SdAccent)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                GxIconChip("←") { persist(); onExit() }
                Text(activeDiff.label, fontSize = 15.sp, color = HikariTextMuted, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GxIconChip("II") { if (!generating && !solved && !failed) paused = true }
                    GxIconChip("✚") { confirmNew = true }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (showTimer) GxHudPill("Zeit", sdFormatTime(elapsed), SdAccent)
                GxHudPill("Fehler", if (limitOn) "$mistakes/3" else "$mistakes", if (mistakes > 0) HikariDanger else null)
                GxHudPill("Hinweise", "$hints")
            }
            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                SdBoard(
                    puzzle = puzzle,
                    solution = solution,
                    cells = cells,
                    notes = notes,
                    selected = selected,
                    hidden = paused,
                    showErrors = showErrors,
                    highlightSame = highlightSame,
                    onTap = { if (!paused && !solved && !failed) selected = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (generating) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GxProgressRing(0.75f, SdAccent, size = 48.dp) {}
                        Spacer(Modifier.height(12.dp))
                        Text("Rätsel wird erstellt …", fontSize = 13.sp, color = HikariTextMuted)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                Arrangement.spacedBy(6.dp),
            ) {
                for (v in 1..9) {
                    val left = 9 - counts[v]
                    val done = left <= 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (notesMode) SdAccent.copy(alpha = 0.10f) else HikariCardBg)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .gxPressable(enabled = !done && !generating) { place(v) }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$v", fontSize = 20.sp, color = if (notesMode) SdAccent else HikariText, fontWeight = FontWeight.Black)
                        Text(if (done) "✓" else "$left", fontSize = 9.sp, color = HikariTextFaint)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f).graphicsLayer { alpha = if (undoSize == 0) 0.4f else 1f }) {
                    GxSmallAction("↶", "Rückgängig", Modifier.fillMaxWidth()) { undoMove() }
                }
                GxSmallAction("⌫", "Löschen", Modifier.weight(1f)) { erase() }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (notesMode) Modifier.border(1.5.dp, SdAccent, RoundedCornerShape(16.dp)) else Modifier),
                ) {
                    GxSmallAction("✎", if (notesMode) "Notizen an" else "Notizen", Modifier.fillMaxWidth()) {
                        notesMode = !notesMode
                    }
                }
                GxSmallAction("💡", "Hinweis", Modifier.weight(1f)) { hint() }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (paused) {
            GxResultOverlay(
                title = "Pause",
                subtitle = "${activeDiff.label} · ${sdFormatTime(elapsed)}",
                accent = SdAccent,
                stats = emptyList(),
                primaryLabel = "Weiter",
                onPrimary = { paused = false },
                secondaryLabel = "Zum Menü",
                onSecondary = { persist(); onExit() },
            )
        }
        if (solved) {
            GxResultOverlay(
                title = "Gelöst!",
                subtitle = if (newBest) "Neue Bestzeit auf ${activeDiff.label}." else "Sauber. ${activeDiff.label} geschafft.",
                accent = SdAccent,
                stats = listOf(
                    "Zeit" to sdFormatTime(elapsed),
                    "Best" to sdFormatTime(store.getInt("best_${activeDiff.id}", elapsed)),
                    "Fehler" to "$mistakes",
                    "Hinweise" to "$hints",
                ),
                primaryLabel = "Neues Sudoku",
                onPrimary = { newGame() },
                secondaryLabel = "Zum Menü",
                onSecondary = onExit,
                badge = if (newBest) "BESTZEIT" else "GELÖST",
            )
        }
        if (failed) {
            GxResultOverlay(
                title = "3 Fehler",
                subtitle = "Das war's für diese Runde. Nächstes Mal mit Notizen arbeiten?",
                accent = SdAccent,
                stats = listOf("Zeit" to sdFormatTime(elapsed), "Gefüllt" to "${cells.count { it != 0 }}/81"),
                primaryLabel = "Neues Sudoku",
                onPrimary = { newGame() },
                secondaryLabel = "Zum Menü",
                onSecondary = onExit,
            )
        }
        if (confirmNew) {
            GxConfirmDialog(
                title = "Neues Sudoku?",
                text = "Das aktuelle Rätsel wird verworfen.",
                confirmLabel = "Neu",
                accent = SdAccent,
                danger = true,
                onConfirm = { confirmNew = false; newGame() },
                onDismiss = { confirmNew = false },
            )
        }
    }
}

@Composable
private fun SdBoard(
    puzzle: IntArray?,
    solution: IntArray?,
    cells: IntArray,
    notes: IntArray,
    selected: Int,
    hidden: Boolean,
    showErrors: Boolean,
    highlightSame: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bigPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    }
    val notePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }
    val selValue = if (selected >= 0) cells[selected] else 0
    val selPeers = remember(selected) { if (selected >= 0) sdPeers(selected).toHashSet() else emptySet() }

    Canvas(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val cell = size.width / 9f
                    val c = (pos.x / cell).toInt().coerceIn(0, 8)
                    val r = (pos.y / cell).toInt().coerceIn(0, 8)
                    onTap(r * 9 + c)
                }
            },
    ) {
        val cell = size.width / 9f
        if (puzzle == null || hidden) {
            // Gitter ohne Inhalt (Laden oder Pause)
            for (i in 0..9) {
                val thick = i % 3 == 0
                val col = Color.White.copy(alpha = if (thick) 0.25f else 0.07f)
                val sw = if (thick) 2.dp.toPx() else 1f
                drawLine(col, Offset(i * cell, 0f), Offset(i * cell, size.height), sw)
                drawLine(col, Offset(0f, i * cell), Offset(size.width, i * cell), sw)
            }
            return@Canvas
        }
        // Zellhintergründe
        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            val v = cells[i]
            val wrong = showErrors && v != 0 && solution != null && v != solution[i]
            val bg = when {
                i == selected -> SdAccent.copy(alpha = 0.32f)
                wrong -> HikariDanger.copy(alpha = 0.22f)
                highlightSame && selValue != 0 && v == selValue -> SdAccent.copy(alpha = 0.16f)
                i in selPeers -> Color.White.copy(alpha = 0.045f)
                else -> Color.Transparent
            }
            if (bg != Color.Transparent) drawRect(bg, Offset(c * cell, r * cell), Size(cell, cell))
        }
        // Linien
        for (i in 0..9) {
            val thick = i % 3 == 0
            val col = Color.White.copy(alpha = if (thick) 0.28f else 0.08f)
            val sw = if (thick) 2.dp.toPx() else 1f
            drawLine(col, Offset(i * cell, 0f), Offset(i * cell, size.height), sw)
            drawLine(col, Offset(0f, i * cell), Offset(size.width, i * cell), sw)
        }
        // Zahlen + Notizen
        bigPaint.textSize = cell * 0.55f
        notePaint.textSize = cell * 0.24f
        drawIntoCanvas { canvas ->
            for (i in 0 until 81) {
                val r = i / 9
                val c = i % 9
                val cx = c * cell + cell / 2
                val v = cells[i]
                if (v != 0) {
                    val given = puzzle[i] != 0
                    val wrong = showErrors && solution != null && v != solution[i]
                    bigPaint.color = when {
                        wrong -> HikariDanger.toArgb()
                        given -> HikariText.toArgb()
                        else -> SdUserColor.toArgb()
                    }
                    val cy = r * cell + cell / 2 - (bigPaint.descent() + bigPaint.ascent()) / 2
                    canvas.nativeCanvas.drawText("$v", cx, cy, bigPaint)
                } else if (notes[i] != 0) {
                    notePaint.color = HikariTextMuted.toArgb()
                    for (d in 1..9) {
                        if (notes[i] and (1 shl d) == 0) continue
                        val nc = (d - 1) % 3
                        val nr = (d - 1) / 3
                        val nx = c * cell + cell * (0.5f + nc) / 3f
                        val ny = r * cell + cell * (0.5f + nr) / 3f - (notePaint.descent() + notePaint.ascent()) / 2
                        canvas.nativeCanvas.drawText("$d", nx, ny, notePaint)
                    }
                }
            }
        }
    }
}
