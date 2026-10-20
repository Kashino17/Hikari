package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
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
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
private val TttStarterShort = listOf("Du", "Gegner", "Wechsel", "Verlierer")

private val TttAccent = Color(0xFF60A5FA)
private val TttP1Color = Color(0xFF22D3EE) // Cyan — Spieler 1 / X
private val TttP2Color = Color(0xFFEC4899) // Pink — Spieler 2 / O

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
    TttAchievement("duell5", "👑", "Duell-Champion", "Gewinne ein Best-of-5 zu zweit am Gerät."),
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

// ————— Prozeduraler Mini-Synth via AudioTrack (keine Assets) —————

private class TttSound {
    private val sr = 22050
    @Volatile private var running = false
    @Volatile private var failed = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val queue = ArrayBlockingQueue<ShortArray>(24)

    private fun ensureStarted() {
        if (failed || track != null) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack(
                AudioManager.STREAM_MUSIC, sr,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, sr / 2), AudioTrack.MODE_STREAM,
            )
            t.play()
            track = t
            running = true
            val th = Thread {
                while (running) {
                    val s = try { queue.take() } catch (_: InterruptedException) { null }
                    if (s != null) try { track?.write(s, 0, s.size) } catch (_: Throwable) {}
                }
            }
            th.isDaemon = true
            thread = th
            th.start()
        } catch (t: Throwable) {
            failed = true
        }
    }

    private fun play(samples: ShortArray) {
        if (failed) return
        try {
            ensureStarted()
            if (track != null) queue.offer(samples)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        running = false
        queue.clear()
        try { thread?.interrupt() } catch (_: Throwable) {}
        try {
            track?.let {
                try { it.pause() } catch (_: Throwable) {}
                try { it.flush() } catch (_: Throwable) {}
                try { it.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        }
        track = null
        thread = null
    }

    private fun synth(dur: Float, vol: Float = 0.30f, wave: (Float) -> Double): ShortArray {
        val n = max(1, (sr * dur).toInt())
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sr
            out[i] = (wave(t).coerceIn(-1.0, 1.0) * vol * 32767.0).toInt().toShort()
        }
        return out
    }

    private fun concat(vararg parts: ShortArray): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }

    private fun note(freq: Double, dur: Float, vol: Float = 0.30f): ShortArray =
        synth(dur, vol) { t ->
            val env = exp(-t * 16.0)
            (sin(2 * kotlin.math.PI * freq * t) * 0.72 + sin(2 * kotlin.math.PI * freq * 2 * t) * 0.28) * env
        }

    // Setz-Klick Spieler 1 (X): heller Tick
    fun placeX() = play(synth(0.07f, 0.24f) { t ->
        val f = 540.0 - t * 2200.0
        sin(2 * kotlin.math.PI * f * t) * exp(-t * 42.0)
    })

    // Setz-Klick Spieler 2 (O): tieferer, runderer Tick
    fun placeO() = play(synth(0.08f, 0.24f) { t ->
        val f = 340.0 - t * 1400.0
        sin(2 * kotlin.math.PI * f * t) * exp(-t * 38.0)
    })

    // Ungültiger Zug: kurzer tiefer Blip
    fun invalid() = play(synth(0.10f, 0.18f) { t ->
        sin(2 * kotlin.math.PI * 130.0 * t) * exp(-t * 30.0)
    })

    fun win() = play(concat(note(523.0, 0.11f, 0.24f), note(659.0, 0.11f, 0.24f), note(784.0, 0.11f, 0.24f), note(1046.0, 0.24f, 0.28f)))

    fun tie() = play(concat(note(392.0, 0.14f, 0.20f), note(392.0, 0.20f, 0.16f)))

    fun lose() = play(concat(note(392.0, 0.14f, 0.22f), note(311.0, 0.14f, 0.22f), note(233.0, 0.26f, 0.24f)))
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
    var soundOn by remember { mutableStateOf(store.getBool("sound", true)) }
    var aiFast by remember { mutableStateOf(store.getBool("ai_fast", false)) }
    var symbolSet by remember { mutableIntStateOf(store.getInt("symbols", 0).coerceIn(0, TttSymbolSets.size - 1)) }
    var showMoveNums by remember { mutableStateOf(store.getBool("move_nums", false)) }
    var starterRule by remember { mutableIntStateOf(store.getInt("starter", 2).coerceIn(0, 3)) }

    // Sound-Synth (prozedural, kein Asset); sauberes Release beim Verlassen
    val tttSound = remember { TttSound() }
    DisposableEffect(Unit) {
        onDispose { tttSound.release() }
    }

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
        Crossfade(targetState = screen, animationSpec = tween(220), label = "tttScreen") { s ->
            when (s) {
                TttScreen.MENU -> TttMenuScreen(
                    store = store,
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
                        sound = tttSound,
                        soundOn = soundOn,
                        onSoundToggle = { soundOn = it; store.setBool("sound", it) },
                        hapticsOn = hapticsOn,
                        onHapticsToggle = { hapticsOn = it; store.setBool("haptics", it) },
                        buzz = { t -> buzz(t) },
                        onRoundFinished = { r, fm -> roundFinished(r, fm) },
                        onMatchFinished = { won, comeback ->
                            if (won && comeback) unlock("comeback")
                            if (!vsAI && bestOf == 5) unlock("duell5")
                        },
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
        }

        if (showSettings) {
            TttSettingsOverlay(
                hapticsOn = hapticsOn, onHaptics = { hapticsOn = it; store.setBool("haptics", it) },
                soundOn = soundOn, onSound = { soundOn = it; store.setBool("sound", it) },
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

// ————— Menü (max. Content-Breite, zentriert — Fold-tauglich) —————

@Composable
private fun TttMenuScreen(
    store: TttStore,
    mode: TttMode, onMode: (TttMode) -> Unit,
    vsAI: Boolean, onVsAI: (Boolean) -> Unit,
    diff: TttDifficulty, onDiff: (TttDifficulty) -> Unit,
    bestOf: Int, onBestOf: (Int) -> Unit,
    streakCur: Int, streakBest: Int,
    onStart: () -> Unit, onStats: () -> Unit, onAch: () -> Unit,
    onSettings: () -> Unit, onBack: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    BackHandler(enabled = showRules) { showRules = false }

    // Menü-Hero: gezeichnetes X und O über pulsierendem Glow
    val heroInf = rememberInfiniteTransition(label = "tttHero")
    val heroGlow by heroInf.animateFloat(
        0.10f, 0.22f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tttHeroGlow",
    )
    val heroFloat by heroInf.animateFloat(
        -5f, 5f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tttHeroY",
    )

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(TttAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Tic-Tac-Toe", TttAccent, onBack = onBack, right = {
                GxIconChip("?") { showRules = true }
            })

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(84.dp)) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(TttAccent.copy(alpha = heroGlow), Color.Transparent),
                                    center = center,
                                    radius = size.minDimension / 2f,
                                )
                            )
                            val sw = size.minDimension * 0.09f
                            val s = size.minDimension * 0.16f
                            val cx1 = center.x - size.minDimension * 0.16f
                            val cy = center.y + heroFloat * density * 0.4f
                            // X (Cyan)
                            drawLine(TttP1Color.copy(alpha = 0.25f), Offset(cx1 - s, cy - s), Offset(cx1 + s, cy + s), strokeWidth = sw * 1.8f, cap = StrokeCap.Round)
                            drawLine(TttP1Color.copy(alpha = 0.25f), Offset(cx1 + s, cy - s), Offset(cx1 - s, cy + s), strokeWidth = sw * 1.8f, cap = StrokeCap.Round)
                            drawLine(TttP1Color, Offset(cx1 - s, cy - s), Offset(cx1 + s, cy + s), strokeWidth = sw, cap = StrokeCap.Round)
                            drawLine(TttP1Color, Offset(cx1 + s, cy - s), Offset(cx1 - s, cy + s), strokeWidth = sw, cap = StrokeCap.Round)
                            // O (Pink)
                            val cx2 = center.x + size.minDimension * 0.20f
                            drawCircle(TttP2Color.copy(alpha = 0.25f), s * 1.05f, Offset(cx2, cy), style = Stroke(sw * 1.8f))
                            drawCircle(TttP2Color, s * 1.05f, Offset(cx2, cy), style = Stroke(sw))
                        }
                    }

                    if (streakCur >= 3 || streakBest >= 3) {
                        GxAppear(0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                GxHudPill("🔥 Serie", "$streakCur · Best $streakBest", TttAccent)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    TttSectionTitle("Modus")
                    TttMode.entries.forEachIndexed { i, m ->
                        val w = store.getInt("stat_${m.id}_w", 0)
                        val t = store.getInt("stat_${m.id}_t", 0)
                        val l = store.getInt("stat_${m.id}_l", 0)
                        GxAppear(i + 1) {
                            GxModeCard(
                                emoji = m.emoji,
                                title = m.label,
                                subtitle = m.desc,
                                accent = TttAccent,
                                highlighted = mode == m,
                                badge = if (m != TttMode.CLASSIC && w + t + l == 0) "NEU" else null,
                                best = if (w + t + l > 0) "Bilanz: $w S · $t U · $l N" else null,
                                onClick = { onMode(m) },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    GxAppear(4) {
                        Column {
                            TttSectionTitle("Gegner")
                            GxSegmented(listOf("🤖 KI", "👥 Zu zweit"), if (vsAI) 0 else 1, TttAccent) { onVsAI(it == 0) }
                            if (vsAI) {
                                Spacer(Modifier.height(14.dp))
                                TttSectionTitle("Schwierigkeit")
                                GxSegmented(
                                    listOf("Leicht", "Mittel", "Schwer", "Adaptiv"),
                                    diff.ordinal, TttAccent,
                                ) { onDiff(TttDifficulty.entries[it]) }
                                Spacer(Modifier.height(6.dp))
                                Text(diff.desc, fontSize = 12.sp, color = HikariTextFaint, modifier = Modifier.padding(horizontal = 4.dp))
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Abwechselnd am selben Gerät — Punktestand über die Serie, Rematch inklusive.",
                                    fontSize = 12.sp, color = HikariTextFaint, modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            TttSectionTitle("Match")
                            GxSegmented(
                                listOf("Einzel", "Best of 3", "Best of 5"),
                                when (bestOf) { 3 -> 1; 5 -> 2; else -> 0 },
                                TttAccent,
                            ) { onBestOf(when (it) { 1 -> 3; 2 -> 5; else -> 1 }) }
                        }
                    }

                    Spacer(Modifier.height(22.dp))
                    GxAppear(5) {
                        GxPrimaryButton("Spielen", TttAccent, Modifier.fillMaxWidth(), onClick = onStart)
                    }
                    Spacer(Modifier.height(12.dp))
                    GxAppear(6) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                            GxSmallAction("🏅", "Erfolge", Modifier.weight(1f), onAch)
                            GxSmallAction("⚙️", "Optionen", Modifier.weight(1f), onSettings)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        if (showRules) {
            GxSheet("Spielregeln", TttAccent, onClose = { showRules = false }) {
                TttMode.entries.forEach { m ->
                    Text("${m.emoji} ${m.label}", fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(tttHelpText(m), fontSize = 12.sp, color = HikariTextMuted, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                }
                Text("👥 Zu zweit", fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ihr spielt abwechselnd am selben Gerät. Die Farben zeigen, wer dran ist; " +
                        "der Punktestand läuft über die ganze Serie (Einzel, Bo3, Bo5). " +
                        "Wer beginnt, stellt ihr in den Optionen ein.",
                    fontSize = 12.sp, color = HikariTextMuted, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun TttSectionTitle(title: String) {
    Text(
        title.uppercase(),
        fontSize = 11.sp,
        color = HikariTextFaint,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

// ————— Statistik (max. Breite, zentriert) —————

@Composable
private fun TttStatsScreen(store: TttStore, onResetStreaks: () -> Unit, onBack: () -> Unit) {
    var resetConfirm by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(TttAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            refresh // nach Reset neu lesen
            GxHeader("Statistik", TttAccent, onBack = onBack)

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    val total = store.getInt("games_total", 0)
                    val wins = store.getInt("wins_total", 0)
                    GxAppear(0) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GxStatTile("$total", "Partien", TttAccent, Modifier.weight(1f))
                                GxStatTile("$wins", "Siege", TttAccent, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GxStatTile("${store.getInt("streak_cur", 0)} 🔥", "Aktuelle Serie", TttAccent, Modifier.weight(1f))
                                GxStatTile("${store.getInt("streak_best", 0)}", "Beste Serie", TttAccent, Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    GxAppear(1) {
                        Column {
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
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    GxAppear(2) {
                        Column {
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
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    GxAppear(3) {
                        Column {
                            TttSectionTitle("Deine Eröffnung")
                            Text(
                                "Wo du am liebsten deinen ersten Zug setzt:",
                                fontSize = 12.sp,
                                color = HikariTextMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            val fm = IntArray(9) { store.getInt("fm_$it", 0) }
                            val maxFm = (fm.maxOrNull() ?: 0).coerceAtLeast(1)
                            Column(Modifier.align(Alignment.CenterHorizontally)) {
                                repeat(3) { r ->
                                    Row {
                                        repeat(3) { c ->
                                            val i = r * 3 + c
                                            val strength = fm[i].toFloat() / maxFm
                                            Box(
                                                Modifier
                                                    .size(56.dp)
                                                    .padding(2.5.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (fm[i] == 0) HikariCardBg
                                                        else TttAccent.copy(alpha = 0.10f + 0.55f * strength)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (fm[i] == maxFm && fm[i] > 0) TttAccent.copy(alpha = 0.6f)
                                                        else Color.White.copy(alpha = 0.05f),
                                                        RoundedCornerShape(12.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (fm[i] > 0) Text("${fm[i]}", fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))
                    GxAppear(4) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(HikariDanger.copy(alpha = 0.10f))
                                .border(1.dp, HikariDanger.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                                .gxPressable { resetConfirm = true }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Statistik zurücksetzen", color = HikariDanger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        if (resetConfirm) {
            GxConfirmDialog(
                title = "Statistik zurücksetzen?",
                text = "Bilanzen, Serien und Eröffnungs-Daten werden gelöscht. Erfolge und Einstellungen bleiben.",
                confirmLabel = "Löschen",
                accent = TttAccent,
                danger = true,
                onConfirm = {
                    store.resetStats()
                    onResetStreaks()
                    resetConfirm = false
                    refresh++
                },
                onDismiss = { resetConfirm = false },
            )
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

// ————— Erfolge (max. Breite, zentriert) —————

@Composable
private fun TttAchievementsScreen(store: TttStore, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(TttAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Erfolge", TttAccent, onBack = onBack)

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    val unlocked = TttAchievements.count { store.getBool("ach_${it.id}", false) }
                    GxAppear(0) {
                        Column {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Freigeschaltet", fontSize = 13.sp, color = HikariTextMuted)
                                Text(
                                    "$unlocked / ${TttAchievements.size}",
                                    fontSize = 13.sp, color = TttAccent, fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)).background(HikariSurfaceHigh)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(unlocked.toFloat() / TttAchievements.size.coerceAtLeast(1))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(TttAccent),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    TttAchievements.forEachIndexed { i, a ->
                        val u = store.getBool("ach_${a.id}", false)
                        GxAppear(i + 1) {
                            GxAchRow(a.emoji, a.title, a.desc, TttAccent, unlocked = u)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ————— Einstellungen + geteilte Overlays —————

@Composable
private fun TttSettingsOverlay(
    hapticsOn: Boolean, onHaptics: (Boolean) -> Unit,
    soundOn: Boolean, onSound: (Boolean) -> Unit,
    aiFast: Boolean, onAiFast: (Boolean) -> Unit,
    symbolSet: Int, onSymbolSet: (Int) -> Unit,
    showMoveNums: Boolean, onMoveNums: (Boolean) -> Unit,
    starterRule: Int, onStarterRule: (Int) -> Unit,
    onClose: () -> Unit,
) {
    GxSheet("Einstellungen", TttAccent, onClose = onClose) {
        GxToggle("Vibration", "Haptisches Feedback bei Zügen", TttAccent, hapticsOn, onHaptics)
        GxToggle("Sound", "Prozedural erzeugte Effektgeräusche", TttAccent, soundOn, onSound)
        GxToggle("Schnelle KI-Züge", "KI zieht ohne Denkpause", TttAccent, aiFast, onAiFast)
        GxToggle("Zug-Nummern", "Zeigt die Zug-Reihenfolge in den Zellen", TttAccent, showMoveNums, onMoveNums)

        Spacer(Modifier.height(10.dp))
        TttSectionTitle("Symbole")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TttSymbolSets.forEachIndexed { i, s ->
                val sel = symbolSet == i
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (sel) TttAccent.copy(alpha = 0.16f) else HikariSurfaceHigh)
                        .border(
                            if (sel) 1.5.dp else 1.dp,
                            if (sel) TttAccent else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(13.dp),
                        )
                        .gxPressable { onSymbolSet(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${s.first} ${s.second}", fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        TttSectionTitle("Wer beginnt?")
        GxSegmented(TttStarterShort, starterRule, TttAccent) { onStarterRule(it) }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TttOverlayCard(onDismiss: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val appear = remember { Animatable(0.90f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 600f))
    }
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
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                    alpha = ((appear.value - 0.90f) / 0.10f).coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF232326))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
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
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)) {
        GxAppear(0) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(HikariSurfaceHigh)
                    .border(1.dp, TttAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Erfolg freigeschaltet!", fontSize = 11.sp, color = TttAccent, fontWeight = FontWeight.Bold)
                    Text(a.title, fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TttActionChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HikariCardBg)
            .border(
                1.dp,
                if (enabled) TttAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(999.dp),
            )
            .gxPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (enabled) HikariText else HikariTextFaint,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ————— Spiel-Screen (alle Modi, Fold-tauglich) —————

@Composable
private fun TttPlayScreen(
    mode: TttMode, vsAI: Boolean, diff: TttDifficulty, bestOf: Int,
    symbols: Pair<String, String>, showMoveNums: Boolean, starterRule: Int,
    adaptRate: Float, aiFast: Boolean, store: TttStore, streakCur: Int,
    sessionResults: List<Char>,
    sound: TttSound, soundOn: Boolean, onSoundToggle: (Boolean) -> Unit,
    hapticsOn: Boolean, onHapticsToggle: (Boolean) -> Unit,
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
            if (soundOn) sound.invalid()
            return
        }
        if (vsAI) undoSnap = game
        val mover = game.turn
        if (firstMove < 0 && mover == "X") firstMove = c
        hintCell = null
        game = tttApply(game, b, c)
        buzz(HapticFeedbackType.TextHandleMove)
        if (soundOn) {
            if (mover == "X") sound.placeX() else sound.placeO()
        }
    }

    fun useUndo() {
        val snap = undoSnap ?: return
        if (undoUsed || !vsAI || game.winner != null || game.turn != "X" || game.moveCount < 2) return
        game = snap
        undoSnap = null
        undoUsed = true
        hintCell = null
        if (soundOn) sound.placeO()
    }

    fun useHint() {
        if (hintUsed || !vsAI || game.winner != null || game.turn != "X") return
        hintCell = tttAiPick(game, TttDifficulty.SCHWER, 1f)
        hintUsed = true
        if (soundOn) sound.placeX()
    }

    // KI-Zug mit natürlichem Delay (nicht blockierend)
    LaunchedEffect(game, paused, matchOver, showHelp) {
        if (!vsAI || game.winner != null || game.turn != "O" || paused || matchOver || showHelp) return@LaunchedEffect
        delay(if (aiFast) 240L else 620L)
        val mv = tttAiPick(game, diff, adaptRate)
        game = tttApply(game, mv.first, mv.second)
        if (soundOn) sound.placeO()
    }

    // Runden-Ende: Serie, Stats, Haptik, Sound, Konfetti — genau einmal
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
        if (soundOn) {
            when {
                wnr == "Tie" -> sound.tie()
                vsAI && wnr == "O" -> sound.lose()
                else -> sound.win()
            }
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

    BoxWithConstraints(Modifier.fillMaxSize().background(HikariBg)) {
        TttBackdrop()

        val wide = maxWidth >= 600.dp
        val boardSize = if (wide) {
            // Tatsächlich verfügbare Breite der linken Spalte beachten
            // (330 dp Steuer-Spalte + 2×18 dp Padding + Luft), sonst quetscht
            // das Brett auf fast quadratischen Fold-Screens.
            minOf(560.dp, maxWidth - 330.dp - 60.dp, maxHeight - 72.dp)
        } else {
            minOf(maxWidth - 40.dp, 430.dp)
        }

        if (wide) {
            // Fold aufgeklappt: links großes Brett, rechts Steuerung/Info
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TttBoardArea(
                        mode = mode, game = game, symbols = symbols, showMoveNums = showMoveNums,
                        hintCell = hintCell, invalidAt = invalidAt, roundKey = roundKey,
                        boardSize = boardSize,
                        onTap = { b, c -> tap(b, c) },
                    )
                }
                Column(
                    Modifier
                        .width(330.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TttPlayHeader(mode, vsAI, streakCur, bestOf, roundIndex) { paused = true }
                    Spacer(Modifier.height(14.dp))
                    TttPlayerPanel(vsAI, diff, symbols, p1Score, p2Score, target, bestOf, game.turn, game.winner)
                    Spacer(Modifier.height(10.dp))
                    TttTurnPill(vsAI, aiThinking, game.turn, game.winner, symbols)
                    TttSessionDots(sessionResults)
                    Spacer(Modifier.height(10.dp))
                    TttResultArea(
                        game = game, vsAI = vsAI, bestOf = bestOf, roundIndex = roundIndex,
                        matchOver = matchOver, symbols = symbols, starter = starter,
                        onNext = {
                            val ns = nextStarter(game.winner)
                            if (bestOf > 1) roundIndex++
                            beginRound(ns)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    TttActionRow(
                        vsAI = vsAI,
                        undoEnabled = undoSnap != null && !undoUsed && game.winner == null && game.turn == "X" && game.moveCount >= 2,
                        hintEnabled = !hintUsed && game.winner == null && game.turn == "X",
                        onUndo = { useUndo() },
                        onHint = { useHint() },
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        } else {
            // Kompakt: klassische einspaltige Ansicht, adaptives Brett
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TttPlayHeader(mode, vsAI, streakCur, bestOf, roundIndex) { paused = true }

                Spacer(Modifier.height(2.dp))
                TttPlayerPanel(
                    vsAI, diff, symbols, p1Score, p2Score, target, bestOf, game.turn, game.winner,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                Spacer(Modifier.height(8.dp))
                TttTurnPill(vsAI, aiThinking, game.turn, game.winner, symbols)
                TttSessionDots(sessionResults)

                Spacer(Modifier.weight(1f))

                // Ergebnis-Bereich (feste Höhe, damit das Brett nicht springt)
                Box(Modifier.height(104.dp), contentAlignment = Alignment.Center) {
                    TttResultArea(
                        game = game, vsAI = vsAI, bestOf = bestOf, roundIndex = roundIndex,
                        matchOver = matchOver, symbols = symbols, starter = starter,
                        onNext = {
                            val ns = nextStarter(game.winner)
                            if (bestOf > 1) roundIndex++
                            beginRound(ns)
                        },
                    )
                }

                TttBoardArea(
                    mode = mode, game = game, symbols = symbols, showMoveNums = showMoveNums,
                    hintCell = hintCell, invalidAt = invalidAt, roundKey = roundKey,
                    boardSize = boardSize,
                    onTap = { b, c -> tap(b, c) },
                )

                Spacer(Modifier.height(12.dp))
                TttActionRow(
                    vsAI = vsAI,
                    undoEnabled = undoSnap != null && !undoUsed && game.winner == null && game.turn == "X" && game.moveCount >= 2,
                    hintEnabled = !hintUsed && game.winner == null && game.turn == "X",
                    onUndo = { useUndo() },
                    onHint = { useHint() },
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // Konfetti bei Sieg
        TttConfetti(trigger = confetti, modifier = Modifier.fillMaxSize())

        // Pause-Overlay
        if (paused && !matchOver) {
            TttOverlayCard(onDismiss = { paused = false }) {
                Text("Pause", fontSize = 22.sp, color = HikariText, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(18.dp))
                GxPrimaryButton("Weiter", TttAccent, Modifier.fillMaxWidth()) { paused = false }
                Spacer(Modifier.height(10.dp))
                GxGhostButton("Regeln ansehen", Modifier.fillMaxWidth()) {
                    paused = false
                    showHelp = true
                }
                Spacer(Modifier.height(10.dp))
                GxGhostButton("Neustart", Modifier.fillMaxWidth()) {
                    if (p1Score > 0 || p2Score > 0 || game.moveCount > 0) {
                        restartConfirm = true
                    } else {
                        resetMatch()
                        paused = false
                    }
                }
                Spacer(Modifier.height(10.dp))
                GxGhostButton("Zum Menü", Modifier.fillMaxWidth(), onClick = onExit)
                Spacer(Modifier.height(12.dp))
                GxToggle("Vibration", null, TttAccent, hapticsOn, onHapticsToggle)
                GxToggle("Sound", null, TttAccent, soundOn, onSoundToggle)
            }
        }

        // Neustart-Bestätigung (laufende Serie schützen)
        if (restartConfirm) {
            GxConfirmDialog(
                title = "Match neu starten?",
                text = "Der aktuelle Spielstand geht verloren.",
                confirmLabel = "Neustart",
                accent = TttAccent,
                onConfirm = {
                    restartConfirm = false
                    paused = false
                    resetMatch()
                },
                onDismiss = { restartConfirm = false },
            )
        }

        // Regel-/Hilfe-Sheet beim ersten Start eines Modus (ausführlich, inkl. KI-Stärken)
        if (showHelp) {
            GxSheet(
                "${mode.emoji} ${mode.label} — Regeln",
                TttAccent,
                onClose = {
                    showHelp = false
                    store.setBool("help_${mode.id}", true)
                },
            ) {
                Text(
                    tttHelpText(mode),
                    fontSize = 13.sp,
                    color = HikariText,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                if (vsAI) {
                    TttSectionTitle("KI-Stärken")
                    TttDifficulty.entries.forEach { d ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("${d.label}: ", fontSize = 12.sp, color = TttAccent, fontWeight = FontWeight.Bold)
                            Text(d.desc, fontSize = 12.sp, color = HikariTextMuted, lineHeight = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Die KI braucht einen Moment zum Nachdenken — in den Optionen geht es schneller.",
                        fontSize = 11.sp, color = HikariTextFaint, lineHeight = 15.sp,
                    )
                } else {
                    TttSectionTitle("Zu zweit spielen")
                    Text(
                        "Ihr setzt abwechselnd am selben Gerät — Cyan (${symbols.first}) ist Spieler 1, " +
                            "Pink (${symbols.second}) ist Spieler 2. Die Karten oben zeigen, wer dran ist, " +
                            "und der Punktestand läuft über die ganze Serie. Wer beginnt, wechselt nach der " +
                            "Regel aus den Optionen (Standard: abwechselnd).",
                        fontSize = 12.sp, color = HikariTextMuted, lineHeight = 17.sp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                GxPrimaryButton("Los geht's", TttAccent, Modifier.fillMaxWidth()) {
                    showHelp = false
                    store.setBool("help_${mode.id}", true)
                }
            }
        }

        // Match-Ende (mit Rematch)
        if (matchOver) {
            TttOverlayCard(onDismiss = null) {
                Text(if (matchWon || !vsAI) "🏆" else "🤖", fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                val title = when {
                    vsAI && matchWon -> "Du gewinnst das Match!"
                    vsAI -> "Die KI gewinnt das Match!"
                    matchWon -> "Spieler 1 gewinnt das Match!"
                    else -> "Spieler 2 gewinnt das Match!"
                }
                Text(title, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("$p1Score : $p2Score", fontSize = 30.sp, color = TttAccent, fontWeight = FontWeight.Black)
                if (matchWon && wasDown02) {
                    Spacer(Modifier.height(4.dp))
                    Text("Comeback nach 0:2! 🦅", fontSize = 13.sp, color = TttAccent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                GxPrimaryButton("🔁 Rematch", TttAccent, Modifier.fillMaxWidth()) { resetMatch() }
                Spacer(Modifier.height(10.dp))
                GxGhostButton("Zum Menü", Modifier.fillMaxWidth(), onClick = onExit)
            }
        }
    }
}

// ————— Spiel-Screen-Bausteine —————

@Composable
private fun TttPlayHeader(mode: TttMode, vsAI: Boolean, streakCur: Int, bestOf: Int, roundIndex: Int, onPause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        GxIconChip("☰", size = 38.dp) { onPause() }
        Text("${mode.emoji} ${mode.label}", fontSize = 17.sp, color = TttAccent, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vsAI && streakCur >= 3) {
                GxHudPill("🔥", "$streakCur", TttAccent)
            }
            GxHudPill("Runde", if (bestOf > 1) "$roundIndex · Bo$bestOf" else "Einzel")
        }
    }
}

@Composable
private fun TttPlayerPanel(
    vsAI: Boolean, diff: TttDifficulty, symbols: Pair<String, String>,
    p1Score: Int, p2Score: Int, target: Int, bestOf: Int,
    turn: String, winner: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TttPlayerCard(
            name = if (vsAI) "Du" else "Spieler 1",
            symbol = symbols.first,
            color = TttP1Color,
            score = p1Score,
            target = target,
            showScore = bestOf > 1,
            active = winner == null && turn == "X",
            won = winner == "X",
            modifier = Modifier.weight(1f),
        )
        TttPlayerCard(
            name = if (vsAI) "KI · ${diff.label}" else "Spieler 2",
            symbol = symbols.second,
            color = TttP2Color,
            score = p2Score,
            target = target,
            showScore = bestOf > 1,
            active = winner == null && turn == "O",
            won = winner == "O",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TttPlayerCard(
    name: String, symbol: String, color: Color,
    score: Int, target: Int, showScore: Boolean,
    active: Boolean, won: Boolean,
    modifier: Modifier = Modifier,
) {
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(280), label = "tttCard")
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(lerpColor(HikariCardBg, color, 0.07f * glow + if (won) 0.12f else 0f))
            .border(
                if (active || won) 1.5.dp else 1.dp,
                color.copy(alpha = if (won) 0.8f else 0.15f + 0.55f * glow),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TttMiniSymbol(symbol, color, 16.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, fontSize = 12.sp, color = if (active) color else HikariTextMuted, fontWeight = FontWeight.Bold)
            if (showScore) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(target) { i -> TttScoreDot(filled = i < score, color = color) }
                }
            }
        }
    }
}

@Composable
private fun TttMiniSymbol(symbol: String, color: Color, dim: Dp) {
    if (symbol == "X" || symbol == "O") {
        Canvas(Modifier.size(dim)) {
            val w = size.minDimension * 0.16f
            if (symbol == "X") {
                val m = size.minDimension * 0.20f
                drawLine(color, Offset(m, m), Offset(size.width - m, size.height - m), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width - m, m), Offset(m, size.height - m), strokeWidth = w, cap = StrokeCap.Round)
            } else {
                val r = size.minDimension * 0.32f
                drawCircle(color, r, center, style = Stroke(w))
            }
        }
    } else {
        Text(symbol, fontSize = (dim.value * 0.8f).sp)
    }
}

@Composable
private fun TttTurnPill(vsAI: Boolean, aiThinking: Boolean, turn: String, winner: String?, symbols: Pair<String, String>) {
    if (winner != null) {
        Spacer(Modifier.height(26.dp))
        return
    }
    val turnIsP1 = turn == "X"
    val pillColor = if (turnIsP1) TttP1Color else TttP2Color
    val label = when {
        aiThinking -> "KI denkt" + tttThinkingDots()
        vsAI -> "Du bist dran (${symbols.first})"
        turnIsP1 -> "Spieler 1 ist dran (${symbols.first})"
        else -> "Spieler 2 ist dran (${symbols.second})"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(pillColor.copy(alpha = 0.15f))
            .border(1.dp, pillColor.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 13.sp, color = pillColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TttSessionDots(sessionResults: List<Char>) {
    if (sessionResults.isEmpty()) return
    Spacer(Modifier.height(5.dp))
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

@Composable
private fun TttResultArea(
    game: TttGame, vsAI: Boolean, bestOf: Int, roundIndex: Int, matchOver: Boolean,
    symbols: Pair<String, String>, starter: String,
    onNext: () -> Unit,
) {
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
                "X" -> TttP1Color
                "O" -> TttP2Color
                else -> HikariText
            }
            Text(msg, fontSize = 24.sp, color = col, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            GxPrimaryButton(if (bestOf > 1) "Weiter" else "Nochmal", TttAccent, onClick = onNext)
        }
    } else if (game.moveCount == 0 && wnr == null) {
        Text(
            when {
                vsAI && starter == "O" -> "KI beginnt diese Runde"
                vsAI -> "Du beginnst (${symbols.first})"
                starter == "X" -> "Runde $roundIndex · Spieler 1 beginnt (${symbols.first})"
                else -> "Runde $roundIndex · Spieler 2 beginnt (${symbols.second})"
            },
            fontSize = 13.sp,
            color = HikariTextFaint,
        )
    }
}

@Composable
private fun TttActionRow(vsAI: Boolean, undoEnabled: Boolean, hintEnabled: Boolean, onUndo: () -> Unit, onHint: () -> Unit) {
    if (!vsAI) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TttActionChip("↶ Undo", enabled = undoEnabled) { onUndo() }
        TttActionChip("💡 Hinweis", enabled = hintEnabled) { onHint() }
    }
}

// Edler Hintergrund: Verlauf + driftende Glow-Blobs + aufsteigendes Bokeh
@Composable
private fun TttBackdrop() {
    val inf = rememberInfiniteTransition(label = "tttBg")
    val t by inf.animateFloat(
        0f, 6.2832f,
        infiniteRepeatable(tween(36000, easing = LinearEasing)),
        label = "tttBgT",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF14161C), Color(0xFF0D0F14))))
        val cols = arrayOf(TttP1Color, TttAccent, TttP2Color)
        val fx = floatArrayOf(0.18f, 0.85f, 0.5f)
        val fy = floatArrayOf(0.2f, 0.45f, 0.85f)
        for (i in 0 until 3) {
            val bx = size.width * fx[i] + sin(t + i * 2.1f) * size.width * 0.06f
            val by = size.height * fy[i] + kotlin.math.cos(t * 0.8f + i * 1.7f) * size.height * 0.04f
            drawRect(
                Brush.radialGradient(
                    listOf(cols[i].copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(bx, by),
                    radius = size.minDimension * 0.5f,
                )
            )
        }
        for (i in 0 until 12) {
            val s1 = (i * 0.618f) % 1f
            val s2 = (i * 0.317f + 0.13f) % 1f
            val yy = ((s2 - t * (0.02f + s1 * 0.02f)) % 1f + 1f) % 1f
            drawCircle(
                Color.White.copy(alpha = 0.03f + s1 * 0.05f),
                2f + s1 * 5f,
                Offset(s1 * size.width, yy * size.height),
            )
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
    // Gefüllte Dots bekommen einen weichen Glow-Ring
    val fill by animateFloatAsState(if (filled) 1f else 0f, tween(320), label = "tttDot")
    Box(
        Modifier
            .size(14.dp)
            .background(color.copy(alpha = 0.30f * fill), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp + 1.5.dp * fill)
                .background(if (filled) color else color.copy(alpha = 0.20f), CircleShape)
        )
    }
}

// ————— Bretter —————

@Composable
private fun TttBoardArea(
    mode: TttMode, game: TttGame, symbols: Pair<String, String>, showMoveNums: Boolean,
    hintCell: Pair<Int, Int>?, invalidAt: Triple<Int, Int, Int>, roundKey: Int,
    boardSize: Dp,
    onTap: (Int, Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Premium-Panel: Verlauf, Kontur, Glow
        Box(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF27272E), Color(0xFF17171C))))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
                .border(3.dp, TttAccent.copy(alpha = 0.05f), RoundedCornerShape(22.dp))
                .padding(10.dp)
        ) {
            if (mode == TttMode.ULTIMATE) {
                TttUltimateBoard(
                    game = game, symbols = symbols, showMoveNums = showMoveNums,
                    hintCell = hintCell, invalidAt = invalidAt, roundKey = roundKey,
                    boardSize = boardSize - 20.dp,
                    onTap = onTap,
                )
            } else {
                TttBigBoard(
                    game = game, symbols = symbols, showMoveNums = showMoveNums,
                    hintCell = hintCell, invalidAt = invalidAt, roundKey = roundKey,
                    boardSize = boardSize - 20.dp,
                    onTap = { c -> onTap(0, c) },
                )
            }
        }
        if (mode == TttMode.ULTIMATE && game.winner == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (game.active == -1) "Freie Brett-Wahl" else "Spiel im markierten Brett",
                fontSize = 11.sp, color = HikariTextFaint,
            )
        }
    }
}

@Composable
private fun TttBigBoard(
    game: TttGame, symbols: Pair<String, String>, showMoveNums: Boolean,
    hintCell: Pair<Int, Int>?, invalidAt: Triple<Int, Int, Int>, roundKey: Int,
    boardSize: Dp,
    onTap: (Int) -> Unit,
) {
    val cellSize = boardSize / 3
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
                        val cellVal = game.boards[0][i]
                        TttBoardCell(
                            symbol = cellVal,
                            symbols = symbols,
                            highlight = game.winLine?.contains(i) == true,
                            lastMoveColor = if (game.lastMove == (0 to i) && cellVal.isNotEmpty())
                                (if (cellVal == "X") TttP1Color else TttP2Color) else null,
                            hint = hintCell == (0 to i),
                            fading = i == fadingIdx,
                            moveNum = if (showMoveNums) game.moveNumbers[0][i] else 0,
                            invalidTick = if (invalidAt.first == 0 && invalidAt.second == i) invalidAt.third else 0,
                            cellSize = cellSize,
                            fontSize = (cellSize.value * 0.38f).sp,
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
    symbol: String, symbols: Pair<String, String>, highlight: Boolean, lastMoveColor: Color?,
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
    // Gewinner-Zellen pulsieren sanft in Spielerfarbe
    var winMod: Modifier = Modifier
    if (highlight) {
        val t = rememberInfiniteTransition(label = "winPulse")
        val a by t.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "wp",
        )
        val wc = if (symbol == "X") TttP1Color else TttP2Color
        winMod = Modifier.border(2.dp, wc.copy(alpha = a), RoundedCornerShape(12.dp))
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
            .background(
                when {
                    highlight -> (if (symbol == "X") TttP1Color else TttP2Color).copy(alpha = 0.22f)
                    else -> lerpColor(Color(0xFF1E1E24), Color.White, (1f - press.value) * 0.10f)
                }
            )
            .then(
                if (lastMoveColor != null) Modifier.border(1.5.dp, lastMoveColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .then(winMod)
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
            extraAlpha = symbolAlpha,
            fontSize = fontSize,
            dim = cellSize * 0.60f,
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
    boardSize: Dp,
    onTap: (Int, Int) -> Unit,
) {
    val cell = (boardSize - 20.dp) / 9
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
                                .background(if (playable) HikariSurfaceHigh else Color(0xFF1B1B20))
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
                                            val cellVal = game.boards[b][i]
                                            TttUltCell(
                                                symbol = cellVal,
                                                symbols = symbols,
                                                dimmed = game.macro[b].isNotEmpty(),
                                                lastMoveColor = if (game.lastMove == (b to i) && cellVal.isNotEmpty())
                                                    (if (cellVal == "X") TttP1Color else TttP2Color) else null,
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
                                    TttCellSymbol(
                                        cell = m,
                                        display = if (m == "X") symbols.first else symbols.second,
                                        extraAlpha = 1f,
                                        fontSize = (cell.value * 1.2f).sp,
                                        dim = cell * 1.9f,
                                    )
                                }
                                "T" -> Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(HikariBg.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("–", fontSize = (cell.value).sp, color = HikariTextFaint)
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
    symbol: String, symbols: Pair<String, String>, dimmed: Boolean, lastMoveColor: Color?,
    hint: Boolean, moveNum: Int, invalidTick: Int, size: Dp, onTap: () -> Unit,
) {
    val shake = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
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
                scaleX = press.value
                scaleY = press.value
                if (dimmed) alpha = 0.35f
            }
            .clip(RoundedCornerShape(6.dp))
            .background(HikariBg.copy(alpha = 0.55f))
            .then(
                if (lastMoveColor != null) Modifier.border(1.dp, lastMoveColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .then(hintMod)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        press.snapTo(0.88f)
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
            extraAlpha = 1f,
            fontSize = (size.value * 0.44f).sp,
            dim = size * 0.58f,
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

// X als zwei sich zeichnende Striche, O als sich zeichnender Kreis — mit Glow.
// Emoji-Symbol-Sets fallen weiter auf Text zurück.
@Composable
private fun TttCellSymbol(cell: String, display: String, extraAlpha: Float, fontSize: TextUnit, dim: Dp) {
    val drawn = display == "X" || display == "O"
    val pop = remember { Animatable(1f) }
    val draw = remember { Animatable(1f) }
    LaunchedEffect(cell) {
        if (cell.isEmpty()) {
            pop.snapTo(1f)
            draw.snapTo(1f)
        } else {
            pop.snapTo(0.55f)
            draw.snapTo(0f)
            launch { draw.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    if (cell.isEmpty()) return
    val col = if (cell == "X") TttP1Color else TttP2Color
    if (drawn) {
        Canvas(
            Modifier
                .size(dim)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = extraAlpha
                }
        ) {
            val w = size.minDimension * 0.15f
            if (display == "X") {
                val m = size.minDimension * 0.16f
                val pA = (draw.value * 2f).coerceIn(0f, 1f)
                val pB = (draw.value * 2f - 1f).coerceIn(0f, 1f)
                // Glow
                if (pA > 0f) drawLine(
                    col.copy(alpha = 0.22f),
                    Offset(m, m),
                    Offset(m + (size.width - 2 * m) * pA, m + (size.height - 2 * m) * pA),
                    strokeWidth = w * 2.1f, cap = StrokeCap.Round,
                )
                if (pB > 0f) drawLine(
                    col.copy(alpha = 0.22f),
                    Offset(size.width - m, m),
                    Offset(size.width - m - (size.width - 2 * m) * pB, m + (size.height - 2 * m) * pB),
                    strokeWidth = w * 2.1f, cap = StrokeCap.Round,
                )
                // Haupt-Striche
                if (pA > 0f) drawLine(
                    col,
                    Offset(m, m),
                    Offset(m + (size.width - 2 * m) * pA, m + (size.height - 2 * m) * pA),
                    strokeWidth = w, cap = StrokeCap.Round,
                )
                if (pB > 0f) drawLine(
                    col,
                    Offset(size.width - m, m),
                    Offset(size.width - m - (size.width - 2 * m) * pB, m + (size.height - 2 * m) * pB),
                    strokeWidth = w, cap = StrokeCap.Round,
                )
            } else {
                val r = size.minDimension * 0.33f
                val sweep = 360f * draw.value
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2f, r * 2f)
                drawArc(
                    col.copy(alpha = 0.22f),
                    startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = tl, size = sz,
                    style = Stroke(w * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    col,
                    startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = tl, size = sz,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
    } else {
        Text(
            display,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = col,
            modifier = Modifier
                .scale(pop.value)
                .graphicsLayer { alpha = extraAlpha },
        )
    }
}

// ————— Sieg-Effekte —————

@Composable
private fun TttWinLineOverlay(winner: String?, winLine: List<Int>?, roundKey: Int, modifier: Modifier) {
    val progress = remember(roundKey) { Animatable(0f) }
    val ring = remember(roundKey) { Animatable(0f) }
    LaunchedEffect(winner, roundKey) {
        if (winner != null && winLine != null) {
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            ring.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }
    if (winLine == null) return
    val col = if (winner == "O") TttP2Color else TttP1Color
    Canvas(modifier) {
        val cell = size.width / 3f
        val cellH = size.height / 3f
        val a = Offset((winLine[0] % 3 + 0.5f) * cell, (winLine[0] / 3 + 0.5f) * cellH)
        val b = Offset((winLine[2] % 3 + 0.5f) * cell, (winLine[2] / 3 + 0.5f) * cellH)
        val p = progress.value
        if (p > 0f) {
            val bp = Offset(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p)
            // Glow-Unterlage + Kernlinie in Spielerfarbe
            drawLine(col.copy(alpha = 0.28f), a, bp, strokeWidth = cell * 0.22f, cap = StrokeCap.Round)
            drawLine(col, a, bp, strokeWidth = cell * 0.09f, cap = StrokeCap.Round)
        }
        // Partikel-Ringe an beiden Enden
        if (ring.value > 0f) {
            val rr = ring.value
            drawCircle(col.copy(alpha = 0.5f * (1f - rr)), cell * 0.38f * rr, a)
            drawCircle(col.copy(alpha = 0.5f * (1f - rr)), cell * 0.38f * rr, b)
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
        val colors = listOf(HikariAmber, TttP1Color, Color(0xFF4ADE80), TttP2Color, Color(0xFFFF8A65))
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
