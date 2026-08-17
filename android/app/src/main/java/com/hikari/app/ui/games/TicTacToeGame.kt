package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

// ————— Modi, Schwierigkeiten, Symbole —————

private enum class TttScreen { MENU, GAME, STATS, ACHIEVEMENTS }

private enum class TttMode(val id: String, val label: String, val emoji: String, val desc: String) {
    CLASSIC("classic", "Klassisch", "⭕", "Das Original — drei in einer Reihe."),
    ULTIMATE("ultimate", "Ultimate", "🧩", "9 Bretter, ein Meta-Spiel. Dein Zug lenkt den Gegner."),
    BOLT("bolt", "Bolt", "⚡", "Nur 3 Steine — der älteste verschwindet. Kein Remis."),
}

private enum class TttDifficulty(val label: String, val desc: String) {
    LEICHT("Leicht", "Zufällige Züge — zum Warmwerden."),
    MITTEL("Mittel", "Gewinnt und blockt, macht aber Fehler."),
    SCHWER("Schwer", "Minimax — unschlagbar."),
    ADAPTIV("Ausgeglichen", "Passt sich deiner Siegquote an."),
}

private val TttSymbolSets = listOf("X" to "O", "⚡" to "🔥", "🌙" to "⭐", "🍣" to "🍜")
private val TttStarterLabels = listOf("Du / Spieler 1", "KI / Spieler 2", "Abwechselnd", "Verlierer beginnt")

private val TttP1Color = Color(0xFF60A5FA)
private val TttP2Color = Color(0xFFFF8A65)

private class TttAchievement(val id: String, val emoji: String, val title: String, val desc: String)

private val TttAchievements = listOf(
    TttAchievement("first_win", "🌱", "Erster Sieg", "Gewinne deine erste Partie."),
    TttAchievement("win_hard", "🏆", "Meisterschlag", "Schlage die KI auf Schwer."),
    TttAchievement("streak5", "🔥", "Lauf!", "5 Siege in Serie gegen die KI."),
    TttAchievement("wins10", "⚔️", "Zehnkämpfer", "Gewinne 10 Partien insgesamt."),
    TttAchievement("comeback", "🦅", "Comeback", "Gewinne ein Bo5 nach 0:2-Rückstand."),
    TttAchievement("ultimate_win", "🧩", "Großmeister", "Gewinne eine Ultimate-Partie gegen die KI."),
    TttAchievement("bolt_hard", "⚡", "Blitzdenker", "Gewinne Bolt gegen die KI auf Schwer."),
    TttAchievement("hotseat", "🤝", "Duell", "Beende eine Partie zu zweit am Gerät."),
    TttAchievement("games50", "🎖️", "Stammgast", "Spiele 50 Partien."),
)

// ————— Persistenz —————

private class TttStore(val p: SharedPreferences) {
    fun getBool(k: String, d: Boolean) = p.getBoolean("tictactoe_$k", d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean("tictactoe_$k", v).apply()
    fun getInt(k: String, d: Int) = p.getInt("tictactoe_$k", d)
    fun setInt(k: String, v: Int) = p.edit().putInt("tictactoe_$k", v).apply()
    fun getStr(k: String, d: String) = p.getString("tictactoe_$k", d) ?: d
    fun setStr(k: String, v: String) = p.edit().putString("tictactoe_$k", v).apply()
    fun bump(k: String) = setInt(k, getInt(k, 0) + 1)

    /** Nur Statistiken löschen — Einstellungen und Erfolge bleiben. */
    fun resetStats() {
        val e = p.edit()
        for (k in p.all.keys) {
            if (!k.startsWith("tictactoe_")) continue
            val s = k.removePrefix("tictactoe_")
            if (s.startsWith("stat_") || s.startsWith("fm_") || s.startsWith("streak_") ||
                s == "games_total" || s == "wins_total" || s == "adapt_hist"
            ) e.remove(k)
        }
        e.apply()
    }
}

// ————— Klassik-Logik (Minimax bleibt unangetastet) —————

private val TttWins = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

/** Gibt Gewinner ("X"/"O"/"Tie") + Gewinn-Linie zurück, sonst null. */
private fun tttEvaluate(b: Array<String>): Pair<String, List<Int>?>? {
    for (w in TttWins) {
        val a = b[w[0]]
        if (a.isNotEmpty() && a == b[w[1]] && a == b[w[2]]) return a to w
    }
    return if (b.all { it.isNotEmpty() }) "Tie" to null else null
}

/** Mittel: Gewinnen, sonst blocken, sonst Zufall — für beliebige Seite. */
private fun tttMediumMove(board: Array<String>, me: String): Int {
    val opp = if (me == "X") "O" else "X"
    val b = board.copyOf()
    val empty = b.indices.filter { b[it].isEmpty() }
    for (i in empty) {
        b[i] = me
        val win = tttEvaluate(b)?.first == me
        b[i] = ""
        if (win) return i
    }
    for (i in empty) {
        b[i] = opp
        val win = tttEvaluate(b)?.first == opp
        b[i] = ""
        if (win) return i
    }
    return empty.random()
}

/** Schwer: Minimax, unschlagbar (O maximiert). */
private fun tttMinimax(b: Array<String>, aiTurn: Boolean, depth: Int): Int {
    val res = tttEvaluate(b)
    if (res != null) return when (res.first) {
        "O" -> 10 - depth
        "X" -> depth - 10
        else -> 0
    }
    val empty = b.indices.filter { b[it].isEmpty() }
    return if (aiTurn) {
        empty.maxOf { i ->
            b[i] = "O"
            val s = tttMinimax(b, false, depth + 1)
            b[i] = ""
            s
        }
    } else {
        empty.minOf { i ->
            b[i] = "X"
            val s = tttMinimax(b, true, depth + 1)
            b[i] = ""
            s
        }
    }
}

private fun tttBestMove(board: Array<String>): Int {
    val b = board.copyOf()
    var best = Int.MIN_VALUE
    val moves = ArrayList<Int>()
    for (i in b.indices) {
        if (b[i].isNotEmpty()) continue
        b[i] = "O"
        val s = tttMinimax(b, false, 1)
        b[i] = ""
        if (s > best) {
            best = s
            moves.clear()
            moves.add(i)
        } else if (s == best) {
            moves.add(i)
        }
    }
    // Zufall unter gleichwertigen Best-Moves: bleibt unschlagbar,
    // spielt aber nicht jede Partie identisch.
    return moves.random()
}

/** Bester Zug aus Sicht von X (für den Hinweis-Button). */
private fun tttBestMoveX(board: Array<String>): Int {
    val b = board.copyOf()
    var best = Int.MAX_VALUE
    val moves = ArrayList<Int>()
    for (i in b.indices) {
        if (b[i].isNotEmpty()) continue
        b[i] = "X"
        val s = tttMinimax(b, true, 1)
        b[i] = ""
        if (s < best) {
            best = s
            moves.clear()
            moves.add(i)
        } else if (s == best) {
            moves.add(i)
        }
    }
    return moves.random()
}

// ————— Einheitlicher Spielzustand für alle drei Modi —————

private class TttGame(
    val mode: TttMode,
    val boards: Array<Array<String>>, // Klassisch/Bolt nutzen nur boards[0]
    val macro: Array<String>,         // Ultimate: "X"/"O"/"T"/""
    val active: Int,                  // Ultimate: Ziel-Brett, -1 = freie Wahl
    val turn: String,
    val winner: String?,              // "X"/"O"/"Tie"
    val winLine: List<Int>?,          // Zellen bzw. Macro-Zellen
    val xq: List<Int>,                // Bolt: Setz-Reihenfolge X
    val oq: List<Int>,                // Bolt: Setz-Reihenfolge O
    val lastMove: Pair<Int, Int>?,    // (Brett, Zelle)
    val moveNumbers: Array<IntArray>,
    val moveCount: Int,
)

private fun tttNewGame(mode: TttMode, starter: String): TttGame = TttGame(
    mode = mode,
    boards = Array(9) { Array(9) { "" } },
    macro = Array(9) { "" },
    active = -1,
    turn = starter,
    winner = null,
    winLine = null,
    xq = emptyList(),
    oq = emptyList(),
    lastMove = null,
    moveNumbers = Array(9) { IntArray(9) },
    moveCount = 0,
)

private fun tttWithTurn(g: TttGame, t: String): TttGame = TttGame(
    mode = g.mode, boards = g.boards, macro = g.macro, active = g.active, turn = t,
    winner = g.winner, winLine = g.winLine, xq = g.xq, oq = g.oq,
    lastMove = g.lastMove, moveNumbers = g.moveNumbers, moveCount = g.moveCount,
)

private fun tttLegal(g: TttGame, b: Int, c: Int): Boolean {
    if (g.winner != null) return false
    return when (g.mode) {
        TttMode.CLASSIC, TttMode.BOLT -> b == 0 && g.boards[0][c].isEmpty()
        TttMode.ULTIMATE -> g.macro[b].isEmpty() && g.boards[b][c].isEmpty() &&
            (g.active == -1 || g.active == b)
    }
}

private fun tttMoves(g: TttGame): List<Pair<Int, Int>> = when (g.mode) {
    TttMode.CLASSIC, TttMode.BOLT -> (0..8).filter { g.boards[0][it].isEmpty() }.map { 0 to it }
    TttMode.ULTIMATE -> {
        val bs = if (g.active >= 0) listOf(g.active) else (0..8).filter { g.macro[it].isEmpty() }
        bs.flatMap { b -> (0..8).filter { g.boards[b][it].isEmpty() }.map { b to it } }
    }
}

private fun tttApply(g: TttGame, b: Int, c: Int): TttGame {
    val boards = Array(9) { g.boards[it].copyOf() }
    val macro = g.macro.copyOf()
    val moveNumbers = Array(9) { g.moveNumbers[it].copyOf() }
    val sym = g.turn
    var xq = g.xq
    var oq = g.oq
    boards[b][c] = sym
    moveNumbers[b][c] = g.moveCount + 1
    var winner: String? = null
    var winLine: List<Int>? = null
    var active = -1
    when (g.mode) {
        TttMode.CLASSIC -> {
            val res = tttEvaluate(boards[0])
            if (res != null) {
                winner = res.first
                winLine = res.second
            }
        }
        TttMode.BOLT -> {
            // Decay: der 4. eigene Stein entfernt den ältesten — erst danach werten.
            val q = (if (sym == "X") xq else oq).toMutableList()
            q.add(c)
            if (q.size > 3) boards[0][q.removeAt(0)] = ""
            if (sym == "X") xq = q else oq = q
            val res = tttEvaluate(boards[0])
            if (res != null && res.first != "Tie") {
                winner = res.first
                winLine = res.second
            }
        }
        TttMode.ULTIMATE -> {
            if (macro[b].isEmpty()) {
                val res = tttEvaluate(boards[b])
                if (res != null) macro[b] = if (res.first == "Tie") "T" else res.first
            }
            for (w in TttWins) {
                val a = macro[w[0]]
                if ((a == "X" || a == "O") && a == macro[w[1]] && a == macro[w[2]]) {
                    winner = a
                    winLine = w
                }
            }
            if (winner == null && macro.none { it.isEmpty() }) {
                val xs = macro.count { it == "X" }
                val os = macro.count { it == "O" }
                winner = if (xs > os) "X" else if (os > xs) "O" else "Tie"
            }
            active = if (winner == null && macro[c].isEmpty() && boards[c].any { it.isEmpty() }) c else -1
        }
    }
    return TttGame(
        mode = g.mode, boards = boards, macro = macro, active = active,
        turn = if (winner == null) (if (sym == "X") "O" else "X") else sym,
        winner = winner, winLine = winLine, xq = xq, oq = oq,
        lastMove = b to c, moveNumbers = moveNumbers, moveCount = g.moveCount + 1,
    )
}

// ————— KI für alle Modi —————

private fun tttBoltPick(g: TttGame, eff: TttDifficulty): Pair<Int, Int> {
    val me = g.turn
    val moves = tttMoves(g)
    if (eff == TttDifficulty.LEICHT) return moves.random()
    for (m in moves) if (tttApply(g, m.first, m.second).winner == me) return m
    // Zellen besetzen, die dem Gegner (inkl. seines Decays) den Sofortsieg gäben
    val oppView = tttWithTurn(g, if (me == "X") "O" else "X")
    val oppWinCells = tttMoves(oppView)
        .filter { tttApply(oppView, it.first, it.second).winner == oppView.turn }
        .map { it.second }
    val blocking = moves.filter { it.second in oppWinCells }
    val pool: List<Pair<Int, Int>> = if (eff == TttDifficulty.SCHWER) {
        // 2-Ply: Züge meiden, nach denen der Gegner sofort gewinnen kann
        val safe = moves.filter { m ->
            val after = tttApply(g, m.first, m.second)
            after.winner == me ||
                tttMoves(after).none { r -> tttApply(after, r.first, r.second).winner == after.turn }
        }
        when {
            blocking.any { it in safe } -> blocking.filter { it in safe }
            safe.isNotEmpty() -> safe
            blocking.isNotEmpty() -> blocking
            else -> moves
        }
    } else {
        blocking.ifEmpty { moves }
    }
    val center = pool.filter { it.second == 4 }
    val corners = pool.filter { it.second in listOf(0, 2, 6, 8) }
    return center.ifEmpty { corners.ifEmpty { pool } }.random()
}

private fun tttUltPick(g: TttGame, eff: TttDifficulty): Pair<Int, Int> {
    val me = g.turn
    val opp = if (me == "X") "O" else "X"
    val moves = tttMoves(g)
    if (eff == TttDifficulty.LEICHT) return moves.random()
    if (eff == TttDifficulty.MITTEL) {
        val winSmall = moves.filter { m ->
            val a = tttApply(g, m.first, m.second)
            a.winner == me || (a.macro[m.first] == me && g.macro[m.first].isEmpty())
        }
        if (winSmall.isNotEmpty()) return winSmall.random()
        val ov = tttWithTurn(g, opp)
        val block = moves.filter { m ->
            val a = tttApply(ov, m.first, m.second)
            a.macro[m.first] == opp && g.macro[m.first].isEmpty()
        }
        if (block.isNotEmpty()) return block.random()
        val center = moves.filter { it.second == 4 }
        return center.ifEmpty { moves }.random()
    }
    // Schwer: Heuristik — gewinnen > blocken > Gegner nicht füttern
    var best = Int.MIN_VALUE
    val picks = ArrayList<Pair<Int, Int>>()
    val ov = tttWithTurn(g, opp)
    for (m in moves) {
        var s = Random.nextInt(3)
        val after = tttApply(g, m.first, m.second)
        if (after.winner == me) s += 10000
        if (after.macro[m.first] == me && g.macro[m.first].isEmpty()) s += 120
        val oppSim = tttApply(ov, m.first, m.second)
        if (oppSim.macro[m.first] == opp && g.macro[m.first].isEmpty()) s += 90
        if (after.winner == null) {
            if (after.active == -1) {
                s -= 45
            } else {
                val oppMoves = tttMoves(after)
                if (oppMoves.any { r -> tttApply(after, r.first, r.second).winner == opp }) s -= 5000
                else if (oppMoves.any { r ->
                        r.first == after.active && after.macro[after.active].isEmpty() &&
                            tttApply(after, r.first, r.second).macro[after.active] == opp
                    }
                ) s -= 80
            }
        }
        if (m.second == 4) s += 4
        if (m.first == 4 && g.macro[4].isEmpty()) s += 6
        if (s > best) {
            best = s
            picks.clear()
            picks.add(m)
        } else if (s == best) {
            picks.add(m)
        }
    }
    return picks.random()
}

/**
 * Zug-Wahl für den Spieler am Zug. ADAPTIV gleitet anhand der
 * Winrate der letzten 10 KI-Partien zwischen den drei Stufen.
 */
private fun tttAiPick(g: TttGame, diff: TttDifficulty, adaptRate: Float): Pair<Int, Int> {
    val eff = if (diff == TttDifficulty.ADAPTIV) {
        when {
            adaptRate > 0.62f -> TttDifficulty.SCHWER
            adaptRate > 0.38f -> TttDifficulty.MITTEL
            else -> TttDifficulty.LEICHT
        }
    } else diff
    return when (g.mode) {
        TttMode.CLASSIC -> 0 to when (eff) {
            TttDifficulty.LEICHT -> (0..8).filter { g.boards[0][it].isEmpty() }.random()
            TttDifficulty.MITTEL -> tttMediumMove(g.boards[0], g.turn)
            else -> if (g.turn == "O") tttBestMove(g.boards[0]) else tttBestMoveX(g.boards[0])
        }
        TttMode.BOLT -> tttBoltPick(g, eff)
        TttMode.ULTIMATE -> tttUltPick(g, eff)
    }
}

private fun tttHelpText(mode: TttMode): String = when (mode) {
    TttMode.CLASSIC ->
        "Drei gleiche Symbole in einer Reihe gewinnen.\n\n" +
            "• Undo nimmt gegen die KI deinen letzten Zug zurück (1× pro Runde).\n" +
            "• Der Hinweis zeigt dir den stärksten Zug (1× pro Runde)."
    TttMode.ULTIMATE ->
        "9 kleine Bretter bilden ein großes Meta-Brett.\n\n" +
            "• Die Zelle deines Zugs bestimmt, in welchem Brett dein Gegner spielen muss.\n" +
            "• Ist das Ziel-Brett voll oder entschieden, hat er freie Wahl.\n" +
            "• Gewinne 3 kleine Bretter in einer Reihe."
    TttMode.BOLT ->
        "Nur 3 Steine pro Spieler!\n\n" +
            "• Beim 4. Stein verschwindet dein ältester — er pulsiert vorher als Warnung.\n" +
            "• Kein Remis möglich. Bleib wachsam: Blockaden lösen sich wieder auf."
}

private class TttConfettiP(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var rot: Float, val vr: Float, val color: Color, val size: Float, var life: Float,
)

// ————— Haupt-Composable —————

@Composable
fun TicTacToeGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { TttStore(context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)) }

    var screen by remember { mutableStateOf(TttScreen.MENU) }
    var gameKey by remember { mutableIntStateOf(0) }

    // Einstellungen (persistiert)
    var hapticsOn by remember { mutableStateOf(store.getBool("haptics", true)) }
    var aiFast by remember { mutableStateOf(store.getBool("ai_fast", false)) }
    var symbolSet by remember { mutableIntStateOf(store.getInt("symbols", 0).coerceIn(0, TttSymbolSets.size - 1)) }
    var showMoveNums by remember { mutableStateOf(store.getBool("move_nums", false)) }
    var starterRule by remember { mutableIntStateOf(store.getInt("starter", 2).coerceIn(0, 3)) }

    // Match-Auswahl — zuletzt gespielte Kombination vorgewählt
    var mode by remember {
        mutableStateOf(TttMode.entries.firstOrNull { it.id == store.getStr("last_mode", "classic") } ?: TttMode.CLASSIC)
    }
    var vsAI by remember { mutableStateOf(store.getStr("last_opp", "ai") == "ai") }
    var diff by remember {
        mutableStateOf(TttDifficulty.entries.getOrElse(store.getInt("last_diff", 1)) { TttDifficulty.MITTEL })
    }
    var bestOf by remember { mutableIntStateOf(store.getInt("last_bestof", 1)) }

    var streakCur by remember { mutableIntStateOf(store.getInt("streak_cur", 0)) }
    var streakBest by remember { mutableIntStateOf(store.getInt("streak_best", 0)) }
    var adaptHist by remember { mutableStateOf(store.getStr("adapt_hist", "")) }
    var showSettings by remember { mutableStateOf(false) }
    val sessionResults = remember { mutableStateListOf<Char>() }
    val achToasts = remember { mutableStateListOf<TttAchievement>() }

    val adaptRate = if (adaptHist.isEmpty()) 0.5f
    else adaptHist.count { it == 'W' }.toFloat() / adaptHist.length

    fun buzz(t: HapticFeedbackType) {
        if (hapticsOn) haptic.performHapticFeedback(t)
    }

    fun unlock(id: String) {
        if (!store.getBool("ach_$id", false)) {
            store.setBool("ach_$id", true)
            TttAchievements.firstOrNull { it.id == id }?.let { achToasts.add(it) }
        }
    }

    fun roundFinished(res: Char, firstMove: Int) {
        store.bump("games_total")
        store.bump("stat_${mode.id}_${res.lowercaseChar()}")
        if (firstMove in 0..8) store.bump("fm_$firstMove")
        if (res == 'W') store.bump("wins_total")
        if (vsAI) {
            store.bump("stat_diff_${diff.ordinal}_${res.lowercaseChar()}")
            when (res) {
                'W' -> {
                    streakCur += 1
                    if (streakCur > streakBest) {
                        streakBest = streakCur
                        store.setInt("streak_best", streakBest)
                    }
                }
                'L' -> streakCur = 0
                else -> {}
            }
            store.setInt("streak_cur", streakCur)
            adaptHist = (adaptHist + res).takeLast(10)
            store.setStr("adapt_hist", adaptHist)
        } else {
            unlock("hotseat")
        }
        sessionResults.add(res)
        if (sessionResults.size > 10) sessionResults.removeAt(0)
        if (res == 'W') {
            unlock("first_win")
            if (vsAI && diff == TttDifficulty.SCHWER) unlock("win_hard")
            if (vsAI && mode == TttMode.ULTIMATE) unlock("ultimate_win")
            if (vsAI && mode == TttMode.BOLT && diff == TttDifficulty.SCHWER) unlock("bolt_hard")
            if (streakCur >= 5) unlock("streak5")
            if (store.getInt("wins_total", 0) >= 10) unlock("wins10")
        }
        if (store.getInt("games_total", 0) >= 50) unlock("games50")
    }

    // Erfolgs-Toast automatisch ausblenden
    LaunchedEffect(achToasts.size) {
        if (achToasts.isNotEmpty()) {
            delay(2600)
            if (achToasts.isNotEmpty()) achToasts.removeAt(0)
        }
    }

    BackHandler(enabled = screen != TttScreen.GAME) {
        when {
            showSettings -> showSettings = false
            screen == TttScreen.STATS || screen == TttScreen.ACHIEVEMENTS -> screen = TttScreen.MENU
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        when (screen) {
            TttScreen.MENU -> TttMenuScreen(
                mode = mode, onMode = { mode = it },
                vsAI = vsAI, onVsAI = { vsAI = it },
                diff = diff, onDiff = { diff = it },
                bestOf = bestOf, onBestOf = { bestOf = it },
                streakCur = streakCur, streakBest = streakBest,
                onStart = {
                    store.setStr("last_mode", mode.id)
                    store.setStr("last_opp", if (vsAI) "ai" else "human")
                    store.setInt("last_diff", diff.ordinal)
                    store.setInt("last_bestof", bestOf)
                    gameKey++
                    screen = TttScreen.GAME
                },
                onStats = { screen = TttScreen.STATS },
                onAch = { screen = TttScreen.ACHIEVEMENTS },
                onSettings = { showSettings = true },
                onBack = onBack,
            )
            TttScreen.GAME -> key(gameKey) {
                TttPlayScreen(
                    mode = mode, vsAI = vsAI, diff = diff, bestOf = bestOf,
                    symbols = TttSymbolSets[symbolSet],
                    showMoveNums = showMoveNums,
                    starterRule = starterRule,
                    adaptRate = adaptRate,
                    aiFast = aiFast,
                    store = store,
                    streakCur = streakCur,
                    sessionResults = sessionResults,
                    buzz = { t -> buzz(t) },
                    onRoundFinished = { r, fm -> roundFinished(r, fm) },
                    onMatchFinished = { won, comeback -> if (won && comeback) unlock("comeback") },
                    onExit = { screen = TttScreen.MENU },
                )
            }
            TttScreen.STATS -> TttStatsScreen(
                store = store,
                onResetStreaks = {
                    streakCur = 0
                    streakBest = 0
                    adaptHist = ""
                },
                onBack = { screen = TttScreen.MENU },
            )
            TttScreen.ACHIEVEMENTS -> TttAchievementsScreen(
                store = store,
                onBack = { screen = TttScreen.MENU },
            )
        }

        if (showSettings) {
            TttSettingsOverlay(
                hapticsOn = hapticsOn, onHaptics = { hapticsOn = it; store.setBool("haptics", it) },
                aiFast = aiFast, onAiFast = { aiFast = it; store.setBool("ai_fast", it) },
                symbolSet = symbolSet, onSymbolSet = { symbolSet = it; store.setInt("symbols", it) },
                showMoveNums = showMoveNums, onMoveNums = { showMoveNums = it; store.setBool("move_nums", it) },
                starterRule = starterRule, onStarterRule = { starterRule = it; store.setInt("starter", it) },
                onClose = { showSettings = false },
            )
        }

        achToasts.firstOrNull()?.let { TttAchToast(it) }
    }
}

// ————— Menü —————

@Composable
private fun TttMenuScreen(
    mode: TttMode, onMode: (TttMode) -> Unit,
    vsAI: Boolean, onVsAI: (Boolean) -> Unit,
    diff: TttDifficulty, onDiff: (TttDifficulty) -> Unit,
    bestOf: Int, onBestOf: (Int) -> Unit,
    streakCur: Int, streakBest: Int,
    onStart: () -> Unit, onStats: () -> Unit, onAch: () -> Unit,
    onSettings: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Tic-Tac-Toe", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            TextButton(onClick = onSettings) { Text("⚙️", fontSize = 16.sp) }
        }

        if (streakCur >= 3 || streakBest >= 3) {
            Text(
                "🔥 Serie: $streakCur · Best: $streakBest",
                fontSize = 13.sp,
                color = HikariPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(6.dp))
        }

        TttSectionTitle("Modus")
        TttMode.entries.forEach { m ->
            TttModeCard(m, selected = mode == m) { onMode(m) }
        }

        Spacer(Modifier.height(12.dp))
        TttSectionTitle("Gegner")
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TttPill("🤖 KI", selected = vsAI) { onVsAI(true) }
            TttPill("👥 Zu zweit", selected = !vsAI) { onVsAI(false) }
        }

        if (vsAI) {
            Spacer(Modifier.height(12.dp))
            TttSectionTitle("Schwierigkeit")
            TttDifficulty.entries.toList().chunked(3).forEach { row ->
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { d -> TttPill(d.label, selected = diff == d) { onDiff(d) } }
                }
            }
            Text(
                diff.desc,
                fontSize = 12.sp,
                color = HikariTextFaint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        TttSectionTitle("Match")
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TttPill("Einzelrunde", selected = bestOf == 1) { onBestOf(1) }
            TttPill("Best of 3", selected = bestOf == 3) { onBestOf(3) }
            TttPill("Best of 5", selected = bestOf == 5) { onBestOf(5) }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
        ) {
            Text("Spielen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onStats) { Text("📊 Statistik", color = HikariTextMuted, fontSize = 13.sp) }
            TextButton(onClick = onAch) { Text("🏅 Erfolge", color = HikariTextMuted, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TttSectionTitle(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        color = HikariTextFaint,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun TttModeCard(m: TttMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .then(if (selected) Modifier.border(1.5.dp, HikariPrimary, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(m.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.label, fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
            Text(m.desc, fontSize = 12.sp, color = HikariTextMuted)
        }
        if (selected) Text("✓", color = HikariPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TttPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) HikariPrimary else HikariCardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) Color.Black else HikariTextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ————— Statistik —————

@Composable
private fun TttStatsScreen(store: TttStore, onResetStreaks: () -> Unit, onBack: () -> Unit) {
    var resetConfirm by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        refresh // nach Reset neu lesen
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Statistik", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(64.dp))
        }

        val total = store.getInt("games_total", 0)
        val wins = store.getInt("wins_total", 0)
        TttStatCard {
            TttStatRow("Partien gesamt", "$total")
            TttStatRow("Siege gesamt", "$wins")
            TttStatRow("Aktuelle KI-Serie", "${store.getInt("streak_cur", 0)} 🔥")
            TttStatRow("Beste KI-Serie", "${store.getInt("streak_best", 0)}")
        }

        TttSectionTitle("Pro Modus (S · U · N)")
        TttStatCard {
            TttMode.entries.forEach { m ->
                TttStatRow(
                    "${m.emoji} ${m.label}",
                    "${store.getInt("stat_${m.id}_w", 0)} · " +
                        "${store.getInt("stat_${m.id}_t", 0)} · " +
                        "${store.getInt("stat_${m.id}_l", 0)}",
                )
            }
        }

        TttSectionTitle("Gegen die KI (S · U · N)")
        TttStatCard {
            TttDifficulty.entries.forEach { d ->
                TttStatRow(
                    d.label,
                    "${store.getInt("stat_diff_${d.ordinal}_w", 0)} · " +
                        "${store.getInt("stat_diff_${d.ordinal}_t", 0)} · " +
                        "${store.getInt("stat_diff_${d.ordinal}_l", 0)}",
                )
            }
        }

        TttSectionTitle("Deine Eröffnung")
        Text(
            "Wo du am liebsten deinen ersten Zug setzt:",
            fontSize = 12.sp,
            color = HikariTextMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        val fm = IntArray(9) { store.getInt("fm_$it", 0) }
        val maxFm = (fm.maxOrNull() ?: 0).coerceAtLeast(1)
        Column(Modifier.padding(horizontal = 20.dp)) {
            repeat(3) { r ->
                Row {
                    repeat(3) { c ->
                        val i = r * 3 + c
                        val strength = fm[i].toFloat() / maxFm
                        Box(
                            Modifier
                                .size(52.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (fm[i] == 0) HikariCardBg
                                    else HikariPrimary.copy(alpha = 0.10f + 0.55f * strength)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (fm[i] > 0) Text("${fm[i]}", fontSize = 12.sp, color = HikariText)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = { resetConfirm = true },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text("Statistik zurücksetzen", color = HikariDanger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (resetConfirm) {
        TttOverlayCard(onDismiss = { resetConfirm = false }) {
            Text("Statistik zurücksetzen?", fontSize = 17.sp, color = HikariText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bilanzen, Serien und Eröffnungs-Daten werden gelöscht. Erfolge und Einstellungen bleiben.",
                fontSize = 13.sp, color = HikariTextMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { resetConfirm = false }) { Text("Abbrechen", color = HikariTextMuted) }
                Button(
                    onClick = {
                        store.resetStats()
                        onResetStreaks()
                        resetConfirm = false
                        refresh++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HikariDanger),
                ) {
                    Text("Löschen", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TttStatCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
private fun TttStatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = HikariTextMuted)
        Text(value, fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
    }
}

// ————— Erfolge —————

@Composable
private fun TttAchievementsScreen(store: TttStore, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Erfolge", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(64.dp))
        }

        val unlocked = TttAchievements.count { store.getBool("ach_${it.id}", false) }
        Text(
            "$unlocked / ${TttAchievements.size} freigeschaltet",
            fontSize = 13.sp,
            color = HikariTextMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

        TttAchievements.forEach { a ->
            val u = store.getBool("ach_${a.id}", false)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariCardBg)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (u) a.emoji else "🔒", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        a.title,
                        fontSize = 14.sp,
                        color = if (u) HikariText else HikariTextFaint,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(a.desc, fontSize = 12.sp, color = if (u) HikariTextMuted else HikariTextFaint)
                }
                if (u) Text("✓", color = HikariPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ————— Einstellungen + geteilte Overlays —————

@Composable
private fun TttSettingsOverlay(
    hapticsOn: Boolean, onHaptics: (Boolean) -> Unit,
    aiFast: Boolean, onAiFast: (Boolean) -> Unit,
    symbolSet: Int, onSymbolSet: (Int) -> Unit,
    showMoveNums: Boolean, onMoveNums: (Boolean) -> Unit,
    starterRule: Int, onStarterRule: (Int) -> Unit,
    onClose: () -> Unit,
) {
    TttOverlayCard(onDismiss = onClose) {
        Text("Einstellungen", fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        TttSwitchRow("Vibration", hapticsOn, onHaptics)
        TttSwitchRow("Schnelle KI-Züge", aiFast, onAiFast)
        TttSwitchRow("Zug-Nummern anzeigen", showMoveNums, onMoveNums)

        Spacer(Modifier.height(12.dp))
        Text("Symbole", fontSize = 12.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TttSymbolSets.forEachIndexed { i, s ->
                TttPill("${s.first} ${s.second}", selected = symbolSet == i) { onSymbolSet(i) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Wer beginnt?", fontSize = 12.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        TttStarterLabels.chunked(2).forEachIndexed { rowIdx, row ->
            Row(
                Modifier.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { colIdx, label ->
                    val idx = rowIdx * 2 + colIdx
                    TttPill(label, selected = starterRule == idx) { onStarterRule(idx) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
        ) {
            Text("Fertig", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TttSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = HikariText)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HikariPrimary,
                checkedThumbColor = Color.Black,
            ),
        )
    }
}

@Composable
private fun TttOverlayCard(onDismiss: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onDismiss != null,
            ) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HikariCardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(horizontal = 26.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun BoxScope.TttAchToast(a: TttAchievement) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurfaceHigh)
            .border(1.dp, HikariPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Erfolg freigeschaltet!", fontSize = 11.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text(a.title, fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TttActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HikariCardBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (enabled) HikariText else HikariTextFaint,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ————— Spiel-Screen (alle Modi) —————

@Composable
private fun TttPlayScreen(
    mode: TttMode, vsAI: Boolean, diff: TttDifficulty, bestOf: Int,
    symbols: Pair<String, String>, showMoveNums: Boolean, starterRule: Int,
    adaptRate: Float, aiFast: Boolean, store: TttStore, streakCur: Int,
    sessionResults: List<Char>,
    buzz: (HapticFeedbackType) -> Unit,
    onRoundFinished: (Char, Int) -> Unit,
    onMatchFinished: (Boolean, Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val target = bestOf / 2 + 1
    val firstStarter = if (starterRule == 1) "O" else "X"

    var roundIndex by remember { mutableIntStateOf(1) }
    var p1Score by remember { mutableIntStateOf(0) }
    var p2Score by remember { mutableIntStateOf(0) }
    var starter by remember { mutableStateOf(firstStarter) }
    var game by remember { mutableStateOf(tttNewGame(mode, firstStarter)) }
    var roundKey by remember { mutableIntStateOf(0) }
    var processed by remember { mutableStateOf(false) }
    var matchOver by remember { mutableStateOf(false) }
    var matchWon by remember { mutableStateOf(false) }
    var wasDown02 by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(!store.getBool("help_${mode.id}", false)) }
    var restartConfirm by remember { mutableStateOf(false) }

    var undoSnap by remember { mutableStateOf<TttGame?>(null) }
    var undoUsed by remember { mutableStateOf(false) }
    var hintCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hintUsed by remember { mutableStateOf(false) }
    var firstMove by remember { mutableIntStateOf(-1) }
    var invalidAt by remember { mutableStateOf(Triple(-1, -1, 0)) }
    var confetti by remember { mutableIntStateOf(0) }

    fun beginRound(st: String) {
        starter = st
        game = tttNewGame(mode, st)
        processed = false
        undoSnap = null
        undoUsed = false
        hintCell = null
        hintUsed = false
        firstMove = -1
        roundKey++
    }

    fun nextStarter(lastWinner: String?): String = when (starterRule) {
        0 -> "X"
        1 -> "O"
        2 -> if (starter == "X") "O" else "X"
        else -> when (lastWinner) {
            "X" -> "O"
            "O" -> "X"
            else -> if (starter == "X") "O" else "X"
        }
    }

    fun resetMatch() {
        p1Score = 0
        p2Score = 0
        roundIndex = 1
        matchOver = false
        matchWon = false
        wasDown02 = false
        beginRound(firstStarter)
    }

    fun tap(b: Int, c: Int) {
        if (paused || matchOver || showHelp || game.winner != null) return
        if (vsAI && game.turn != "X") return
        if (!tttLegal(game, b, c)) {
            invalidAt = Triple(b, c, invalidAt.third + 1)
            buzz(HapticFeedbackType.TextHandleMove)
            return
        }
        if (vsAI) undoSnap = game
        if (firstMove < 0 && game.turn == "X") firstMove = c
        hintCell = null
        game = tttApply(game, b, c)
        buzz(HapticFeedbackType.TextHandleMove)
    }

    fun useUndo() {
        val snap = undoSnap ?: return
        if (undoUsed || !vsAI || game.winner != null || game.turn != "X" || game.moveCount < 2) return
        game = snap
        undoSnap = null
        undoUsed = true
        hintCell = null
    }

    fun useHint() {
        if (hintUsed || !vsAI || game.winner != null || game.turn != "X") return
        hintCell = tttAiPick(game, TttDifficulty.SCHWER, 1f)
        hintUsed = true
    }

    // KI-Zug mit natürlichem Delay (nicht blockierend)
    LaunchedEffect(game, paused, matchOver, showHelp) {
        if (!vsAI || game.winner != null || game.turn != "O" || paused || matchOver || showHelp) return@LaunchedEffect
        delay(if (aiFast) 240L else 620L)
        val mv = tttAiPick(game, diff, adaptRate)
        game = tttApply(game, mv.first, mv.second)
    }

    // Runden-Ende: Serie, Stats, Haptik, Konfetti — genau einmal
    LaunchedEffect(game.winner, roundKey) {
        val wnr = game.winner ?: return@LaunchedEffect
        if (processed) return@LaunchedEffect
        processed = true
        val res = if (wnr == "Tie") 'T' else if (wnr == "X") 'W' else 'L'
        if (wnr == "X") p1Score++ else if (wnr == "O") p2Score++
        if (bestOf == 5 && p1Score == 0 && p2Score == 2) wasDown02 = true
        onRoundFinished(res, firstMove)
        if (wnr != "Tie" && (!vsAI || wnr == "X")) confetti++
        when (res) {
            'W' -> buzz(HapticFeedbackType.LongPress)
            'L' -> {
                buzz(HapticFeedbackType.LongPress)
                delay(150)
                buzz(HapticFeedbackType.LongPress)
            }
            else -> buzz(HapticFeedbackType.TextHandleMove)
        }
        if (p1Score >= target || p2Score >= target) {
            matchWon = p1Score >= target
            matchOver = true
            onMatchFinished(matchWon, wasDown02)
        }
    }

    BackHandler {
        when {
            showHelp -> {
                showHelp = false
                store.setBool("help_${mode.id}", true)
            }
            restartConfirm -> restartConfirm = false
            matchOver -> onExit()
            paused -> paused = false
            else -> paused = true
        }
    }

    val aiThinking = vsAI && game.winner == null && game.turn == "O"

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                TextButton(onClick = { paused = true }) { Text("☰ Menü", color = HikariTextMuted) }
                Text("${mode.emoji} ${mode.label}", fontSize = 17.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 10.dp)) {
                    Text(
                        if (bestOf > 1) "Runde $roundIndex · Bo$bestOf" else "Einzelrunde",
                        fontSize = 11.sp, color = HikariTextMuted,
                    )
                    if (vsAI && streakCur >= 3) {
                        Text("🔥 Serie $streakCur", fontSize = 11.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Matchstand als Punkte-Dots (bei Serien immer sichtbar)
            if (bestOf > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(if (vsAI) "Du" else "Spieler 1", fontSize = 12.sp, color = TttP1Color, fontWeight = FontWeight.Bold)
                    repeat(target) { i -> TttScoreDot(filled = i < p1Score, color = TttP1Color) }
                    Text("–", fontSize = 12.sp, color = HikariTextFaint)
                    repeat(target) { i -> TttScoreDot(filled = i < p2Score, color = TttP2Color) }
                    Text(if (vsAI) "KI" else "Spieler 2", fontSize = 12.sp, color = TttP2Color, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
            }

            // Wer ist dran? — inkl. animierter KI-Denk-Punkte
            if (game.winner == null) {
                val turnIsP1 = game.turn == "X"
                val pillColor = if (turnIsP1) TttP1Color else TttP2Color
                val label = when {
                    aiThinking -> "KI denkt" + tttThinkingDots()
                    vsAI -> "Du bist ${symbols.first}"
                    turnIsP1 -> "Spieler 1 ist dran  ${symbols.first}"
                    else -> "Spieler 2 ist dran  ${symbols.second}"
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillColor.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(label, fontSize = 13.sp, color = pillColor, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(28.dp))
            }

            // Session-Historie (letzte 10 Runden)
            if (sessionResults.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    sessionResults.forEach { r ->
                        Text(
                            when (r) {
                                'W' -> "✓"
                                'L' -> "✗"
                                else -> "–"
                            },
                            fontSize = 12.sp,
                            color = when (r) {
                                'W' -> Color(0xFF4ADE80)
                                'L' -> HikariDanger
                                else -> HikariTextFaint
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Ergebnis-Bereich (feste Höhe, damit das Brett nicht springt)
            Box(Modifier.height(112.dp), contentAlignment = Alignment.Center) {
                val wnr = game.winner
                if (wnr != null && !matchOver) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val msg = when {
                            wnr == "Tie" -> "Unentschieden!"
                            wnr == "X" && vsAI -> "Du gewinnst!"
                            wnr == "O" && vsAI -> "KI gewinnt!"
                            wnr == "X" -> "Spieler 1 gewinnt!"
                            else -> "Spieler 2 gewinnt!"
                        }
                        val col = when (wnr) {
                            "X" -> Color(0xFF4ADE80)
                            "O" -> if (vsAI) HikariDanger else Color(0xFF4ADE80)
                            else -> HikariText
                        }
                        Text(msg, fontSize = 24.sp, color = col, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val ns = nextStarter(wnr)
                                if (bestOf > 1) roundIndex++
                                beginRound(ns)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                        ) {
                            Text(if (bestOf > 1) "Weiter" else "Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (game.moveCount == 0 && starter == "O" && vsAI) {
                    Text("KI beginnt diese Runde", fontSize = 13.sp, color = HikariTextFaint)
                }
            }

            // Brett
            if (mode == TttMode.ULTIMATE) {
                TttUltimateBoard(
                    game = game, symbols = symbols, showMoveNums = showMoveNums,
                    hintCell = hintCell, invalidAt = invalidAt, roundKey = roundKey,
                    onTap = { b, c -> tap(b, c) },
                )
                if (game.winner == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (game.active == -1) "Freie Brett-Wahl" else "Spiel im markierten Brett",
                        fontSize = 11.sp, color = HikariTextFaint,
                    )
                }
            } else {
                TttBigBoard(
                    game = game, symbols = symbols, showMoveNums = showMoveNums,
                    hintCell = hintCell, invalidAt = invalidAt, roundKey = roundKey,
                    onTap = { c -> tap(0, c) },
                )
            }

            Spacer(Modifier.height(14.dp))

            // Aktionen (nur gegen die KI sinnvoll)
            if (vsAI) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TttActionChip(
                        "↶ Undo",
                        enabled = undoSnap != null && !undoUsed && game.winner == null &&
                            game.turn == "X" && game.moveCount >= 2,
                    ) { useUndo() }
                    TttActionChip(
                        "💡 Hinweis",
                        enabled = !hintUsed && game.winner == null && game.turn == "X",
                    ) { useHint() }
                }
            }

            Spacer(Modifier.height(36.dp))
        }

        // Konfetti bei Sieg
        TttConfetti(trigger = confetti, modifier = Modifier.fillMaxSize())

        // Pause-Overlay
        if (paused && !matchOver) {
            TttOverlayCard(onDismiss = { paused = false }) {
                Text("Pause", fontSize = 20.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { paused = false },
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Weiter", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        paused = false
                        showHelp = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Regeln ansehen", color = HikariTextMuted) }
                TextButton(
                    onClick = {
                        if (p1Score > 0 || p2Score > 0 || game.moveCount > 0) {
                            restartConfirm = true
                        } else {
                            resetMatch()
                            paused = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Neustart", color = HikariTextMuted) }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("Zum Menü", color = HikariTextMuted)
                }
            }
        }

        // Neustart-Bestätigung (laufende Serie schützen)
        if (restartConfirm) {
            TttOverlayCard(onDismiss = { restartConfirm = false }) {
                Text("Match neu starten?", fontSize = 17.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Der aktuelle Spielstand geht verloren.",
                    fontSize = 13.sp, color = HikariTextMuted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { restartConfirm = false }) { Text("Abbrechen", color = HikariTextMuted) }
                    Button(
                        onClick = {
                            restartConfirm = false
                            paused = false
                            resetMatch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                    ) {
                        Text("Neustart", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Regel-/Hilfe-Overlay beim ersten Start eines Modus
        if (showHelp) {
            TttOverlayCard(onDismiss = null) {
                Text("${mode.emoji} ${mode.label}", fontSize = 19.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    tttHelpText(mode),
                    fontSize = 13.sp,
                    color = HikariTextMuted,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        showHelp = false
                        store.setBool("help_${mode.id}", true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) {
                    Text("Los geht's", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Match-Ende
        if (matchOver) {
            TttOverlayCard(onDismiss = null) {
                Text(if (matchWon) "🏆" else "🤖", fontSize = 42.sp)
                Spacer(Modifier.height(8.dp))
                val title = when {
                    vsAI && matchWon -> "Du gewinnst das Match!"
                    vsAI -> "Die KI gewinnt das Match!"
                    matchWon -> "Spieler 1 gewinnt das Match!"
                    else -> "Spieler 2 gewinnt das Match!"
                }
                Text(title, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("$p1Score : $p2Score", fontSize = 26.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                if (matchWon && wasDown02) {
                    Spacer(Modifier.height(4.dp))
                    Text("Comeback nach 0:2! 🦅", fontSize = 13.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { resetMatch() },
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("Zum Menü", color = HikariTextMuted)
                }
            }
        }
    }
}

@Composable
private fun tttThinkingDots(): String {
    val t = rememberInfiniteTransition(label = "think")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "p",
    )
    return ".".repeat(phase.toInt().coerceIn(0, 2) + 1)
}

@Composable
private fun TttScoreDot(filled: Boolean, color: Color) {
    Box(
        Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) color else color.copy(alpha = 0.2f))
    )
}

// ————— Bretter —————

@Composable
private fun TttBigBoard(
    game: TttGame, symbols: Pair<String, String>, showMoveNums: Boolean,
    hintCell: Pair<Int, Int>?, invalidAt: Triple<Int, Int, Int>, roundKey: Int,
    onTap: (Int) -> Unit,
) {
    val cellSize = 96.dp
    // Bolt: der älteste Stein des Spielers am Zug pulsiert als Decay-Warnung
    val fadingIdx = if (game.mode == TttMode.BOLT && game.winner == null) {
        val q = if (game.turn == "X") game.xq else game.oq
        if (q.size >= 3) q.first() else -1
    } else -1

    Box {
        Column {
            repeat(3) { r ->
                Row {
                    repeat(3) { c ->
                        val i = r * 3 + c
                        TttBoardCell(
                            symbol = game.boards[0][i],
                            symbols = symbols,
                            highlight = game.winLine?.contains(i) == true,
                            lastMove = game.lastMove == (0 to i) && game.boards[0][i].isNotEmpty(),
                            hint = hintCell == (0 to i),
                            fading = i == fadingIdx,
                            moveNum = if (showMoveNums) game.moveNumbers[0][i] else 0,
                            invalidTick = if (invalidAt.first == 0 && invalidAt.second == i) invalidAt.third else 0,
                            cellSize = cellSize,
                            fontSize = 36.sp,
                            onTap = { onTap(i) },
                        )
                    }
                }
            }
        }
        TttWinLineOverlay(game.winner, game.winLine, roundKey, Modifier.size(cellSize * 3))
        TttTieFlash(game.winner, roundKey, Modifier.size(cellSize * 3))
    }
}

@Composable
private fun TttBoardCell(
    symbol: String, symbols: Pair<String, String>, highlight: Boolean, lastMove: Boolean,
    hint: Boolean, fading: Boolean, moveNum: Int, invalidTick: Int,
    cellSize: Dp, fontSize: TextUnit, onTap: () -> Unit,
) {
    val press = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(invalidTick) {
        if (invalidTick > 0) {
            repeat(2) {
                shake.animateTo(9f, tween(40))
                shake.animateTo(-9f, tween(40))
            }
            shake.animateTo(0f, tween(36))
        }
    }

    var hintBorder: Modifier = Modifier
    if (hint) {
        val t = rememberInfiniteTransition(label = "hint")
        val a by t.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "ha",
        )
        hintBorder = Modifier.border(2.dp, HikariPrimary.copy(alpha = a), RoundedCornerShape(12.dp))
    }
    var symbolAlpha = 1f
    if (fading) {
        val t = rememberInfiniteTransition(label = "fade")
        val a by t.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "fa",
        )
        symbolAlpha = a
    }

    Box(
        Modifier
            .size(cellSize)
            .padding(4.dp)
            .graphicsLayer {
                translationX = shake.value
                scaleX = press.value
                scaleY = press.value
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) HikariPrimary.copy(alpha = 0.25f) else HikariCardBg)
            .then(
                if (lastMove) Modifier.border(1.dp, HikariPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .then(hintBorder)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        press.snapTo(0.94f)
                        try {
                            awaitRelease()
                        } finally {
                            press.animateTo(1f, spring(stiffness = Spring.StiffnessHigh))
                        }
                    },
                    onTap = { onTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        TttCellSymbol(
            cell = symbol,
            display = if (symbol == "X") symbols.first else symbols.second,
            highlight = highlight,
            extraAlpha = symbolAlpha,
            fontSize = fontSize,
        )
        if (moveNum > 0 && symbol.isNotEmpty()) {
            Text(
                "$moveNum",
                fontSize = 9.sp,
                color = HikariTextFaint,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 3.dp),
            )
        }
    }
}

@Composable
private fun TttUltimateBoard(
    game: TttGame, symbols: Pair<String, String>, showMoveNums: Boolean,
    hintCell: Pair<Int, Int>?, invalidAt: Triple<Int, Int, Int>, roundKey: Int,
    onTap: (Int, Int) -> Unit,
) {
    val cell = 34.dp
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { br ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { bc ->
                        val b = br * 3 + bc
                        val playable = game.winner == null && game.macro[b].isEmpty() &&
                            (game.active == -1 || game.active == b)
                        val isActive = game.active == b && game.winner == null
                        Box(
                            Modifier
                                .graphicsLayer {
                                    if (isActive) {
                                        scaleX = 1.07f
                                        scaleY = 1.07f
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (playable) HikariSurfaceHigh else HikariCardBg)
                                .then(
                                    when {
                                        isActive -> Modifier.border(2.dp, HikariPrimary, RoundedCornerShape(10.dp))
                                        playable -> Modifier.border(
                                            1.dp, HikariPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp),
                                        )
                                        else -> Modifier
                                    }
                                )
                                .padding(3.dp)
                        ) {
                            Column {
                                repeat(3) { r ->
                                    Row {
                                        repeat(3) { c ->
                                            val i = r * 3 + c
                                            TttUltCell(
                                                symbol = game.boards[b][i],
                                                symbols = symbols,
                                                dimmed = game.macro[b].isNotEmpty(),
                                                lastMove = game.lastMove == (b to i),
                                                hint = hintCell == (b to i),
                                                moveNum = if (showMoveNums) game.moveNumbers[b][i] else 0,
                                                invalidTick = if (invalidAt.first == b && invalidAt.second == i) invalidAt.third else 0,
                                                size = cell,
                                                onTap = { onTap(b, i) },
                                            )
                                        }
                                    }
                                }
                            }
                            // Entschiedenes Brett: großes Symbol darüber
                            when (val m = game.macro[b]) {
                                "X", "O" -> Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(HikariBg.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (m == "X") symbols.first else symbols.second,
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (m == "X") TttP1Color else TttP2Color,
                                    )
                                }
                                "T" -> Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(HikariBg.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("–", fontSize = 30.sp, color = HikariTextFaint)
                                }
                            }
                        }
                    }
                }
            }
        }
        TttWinLineOverlay(game.winner, game.winLine, roundKey, Modifier.matchParentSize())
    }
}

@Composable
private fun TttUltCell(
    symbol: String, symbols: Pair<String, String>, dimmed: Boolean, lastMove: Boolean,
    hint: Boolean, moveNum: Int, invalidTick: Int, size: Dp, onTap: () -> Unit,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(invalidTick) {
        if (invalidTick > 0) {
            repeat(2) {
                shake.animateTo(6f, tween(36))
                shake.animateTo(-6f, tween(36))
            }
            shake.animateTo(0f, tween(30))
        }
    }
    var hintMod: Modifier = Modifier
    if (hint) {
        val t = rememberInfiniteTransition(label = "uh")
        val a by t.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
            label = "ua",
        )
        hintMod = Modifier.border(1.5.dp, HikariPrimary.copy(alpha = a), RoundedCornerShape(6.dp))
    }
    Box(
        Modifier
            .size(size)
            .padding(1.5.dp)
            .graphicsLayer {
                translationX = shake.value
                if (dimmed) alpha = 0.35f
            }
            .clip(RoundedCornerShape(6.dp))
            .background(HikariBg.copy(alpha = 0.55f))
            .then(
                if (lastMove) Modifier.border(1.dp, HikariPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .then(hintMod)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        TttCellSymbol(
            cell = symbol,
            display = if (symbol == "X") symbols.first else symbols.second,
            highlight = false,
            extraAlpha = 1f,
            fontSize = 15.sp,
        )
        if (moveNum > 0 && symbol.isNotEmpty()) {
            Text(
                "$moveNum",
                fontSize = 7.sp,
                color = HikariTextFaint,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun TttCellSymbol(cell: String, display: String, highlight: Boolean, extraAlpha: Float, fontSize: TextUnit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(cell) {
        if (cell.isEmpty()) {
            anim.snapTo(0f)
        } else {
            anim.snapTo(0.3f)
            anim.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    Text(
        if (cell.isEmpty()) "" else display,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = when {
            cell.isEmpty() -> Color.Transparent
            highlight -> HikariPrimary
            cell == "X" -> TttP1Color
            else -> TttP2Color
        },
        modifier = Modifier
            .scale(anim.value)
            .graphicsLayer { alpha = extraAlpha },
    )
}

// ————— Sieg-Effekte —————

@Composable
private fun TttWinLineOverlay(winner: String?, winLine: List<Int>?, roundKey: Int, modifier: Modifier) {
    val progress = remember(roundKey) { Animatable(0f) }
    LaunchedEffect(winner, roundKey) {
        if (winner != null && winLine != null) {
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    if (winLine == null) return
    Canvas(modifier) {
        val cell = size.width / 3f
        val cellH = size.height / 3f
        val a = Offset((winLine[0] % 3 + 0.5f) * cell, (winLine[0] / 3 + 0.5f) * cellH)
        val b = Offset((winLine[2] % 3 + 0.5f) * cell, (winLine[2] / 3 + 0.5f) * cellH)
        val p = progress.value
        if (p > 0f) {
            drawLine(
                HikariPrimary.copy(alpha = 0.85f),
                a,
                Offset(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p),
                strokeWidth = cell * 0.09f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TttTieFlash(winner: String?, roundKey: Int, modifier: Modifier) {
    val a = remember(roundKey) { Animatable(0f) }
    LaunchedEffect(winner, roundKey) {
        if (winner == "Tie") {
            repeat(2) {
                a.animateTo(0.20f, tween(160))
                a.animateTo(0f, tween(210))
            }
        }
    }
    if (a.value > 0f) {
        Box(modifier.background(Color.White.copy(alpha = a.value), RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun TttConfetti(trigger: Int, modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    val parts = remember { ArrayList<TttConfettiP>() }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val colors = listOf(HikariAmber, Color(0xFF60A5FA), Color(0xFF4ADE80), Color(0xFFEC4899), Color(0xFFFF8A65))
        parts.clear()
        repeat(64) {
            parts.add(
                TttConfettiP(
                    x = Random.nextFloat(),
                    y = -0.05f - Random.nextFloat() * 0.12f,
                    vx = (Random.nextFloat() - 0.5f) * 0.35f,
                    vy = 0.25f + Random.nextFloat() * 0.4f,
                    rot = Random.nextFloat() * 360f,
                    vr = (Random.nextFloat() - 0.5f) * 540f,
                    color = colors.random(),
                    size = 6f + Random.nextFloat() * 7f,
                    life = 2.2f,
                )
            )
        }
        var last = 0L
        while (parts.isNotEmpty()) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.03f)
                last = now
                val it2 = parts.iterator()
                while (it2.hasNext()) {
                    val p = it2.next()
                    p.vy += 0.35f * dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rot += p.vr * dt
                    p.life -= dt
                    if (p.life <= 0f || p.y > 1.15f) it2.remove()
                }
                tick++
            }
        }
    }
    Canvas(modifier) {
        if (tick < 0) return@Canvas // liest tick → Redraw pro Frame
        for (p in parts) {
            rotate(p.rot, pivot = Offset(p.x * size.width, p.y * size.height)) {
                drawRect(
                    p.color,
                    topLeft = Offset(p.x * size.width - p.size, p.y * size.height - p.size * 0.6f),
                    size = Size(p.size * 2f, p.size * 1.2f),
                )
            }
        }
    }
}
