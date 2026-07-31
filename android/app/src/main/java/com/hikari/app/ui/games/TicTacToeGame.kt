package com.hikari.app.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay

private enum class TttDifficulty(val label: String) {
    LEICHT("Leicht"),
    MITTEL("Mittel"),
    SCHWER("Schwer"),
}

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

/** Mittel: Gewinnen, sonst blocken, sonst Zufall. */
private fun tttMediumMove(board: Array<String>): Int {
    val b = board.copyOf()
    val empty = b.indices.filter { b[it].isEmpty() }
    for (i in empty) {
        b[i] = "O"
        val win = tttEvaluate(b)?.first == "O"
        b[i] = ""
        if (win) return i
    }
    for (i in empty) {
        b[i] = "X"
        val win = tttEvaluate(b)?.first == "X"
        b[i] = ""
        if (win) return i
    }
    return empty.random()
}

/** Schwer: Minimax, unschlagbar. */
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
    var move = -1
    for (i in b.indices) {
        if (b[i].isNotEmpty()) continue
        b[i] = "O"
        val s = tttMinimax(b, false, 1)
        b[i] = ""
        if (s > best) {
            best = s
            move = i
        }
    }
    return move
}

@Composable
fun TicTacToeGame(onBack: () -> Unit) {
    var board by remember { mutableStateOf(Array(9) { "" }) }
    var turn by remember { mutableStateOf("X") }
    var winner by remember { mutableStateOf<String?>(null) }
    var winLine by remember { mutableStateOf<List<Int>?>(null) }
    var difficulty by remember { mutableStateOf(TttDifficulty.MITTEL) }
    var humanStarts by remember { mutableStateOf(true) }
    var roundId by remember { mutableStateOf(0) }
    var playerWins by remember { mutableStateOf(0) }
    var ties by remember { mutableStateOf(0) }
    var aiWins by remember { mutableStateOf(0) }

    val aiThinking = winner == null && turn == "O"

    fun applyMove(idx: Int, symbol: String) {
        val nb = board.copyOf()
        nb[idx] = symbol
        board = nb
        val res = tttEvaluate(nb)
        if (res != null) {
            winner = res.first
            winLine = res.second
            when (res.first) {
                "X" -> playerWins++
                "O" -> aiWins++
                else -> ties++
            }
        } else {
            turn = if (symbol == "X") "O" else "X"
        }
    }

    fun resetBoard(switchStarter: Boolean) {
        if (switchStarter) humanStarts = !humanStarts
        board = Array(9) { "" }
        winner = null
        winLine = null
        turn = if (humanStarts) "X" else "O"
        roundId++
    }

    fun tap(idx: Int) {
        if (board[idx].isNotEmpty() || winner != null || turn != "X") return
        applyMove(idx, "X")
    }

    // KI-Zug mit natürlichem Delay (nicht blockierend)
    LaunchedEffect(turn, winner, roundId, difficulty) {
        if (winner != null || turn != "O") return@LaunchedEffect
        delay(400)
        val empty = board.indices.filter { board[it].isEmpty() }
        if (empty.isEmpty()) return@LaunchedEffect
        val choice = when (difficulty) {
            TttDifficulty.LEICHT -> empty.random()
            TttDifficulty.MITTEL -> tttMediumMove(board)
            TttDifficulty.SCHWER -> tttBestMove(board)
        }
        applyMove(choice, "O")
    }

    Column(Modifier.fillMaxSize().background(HikariBg), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Tic-Tac-Toe", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text(if (winner == null) "Du bist X" else "", fontSize = 12.sp, color = HikariTextMuted)
                if (aiThinking) Text("KI denkt...", fontSize = 12.sp, color = HikariTextMuted)
            }
        }

        // Session-Punktestand
        Text(
            "Du $playerWins · Remis $ties · KI $aiWins",
            fontSize = 13.sp,
            color = HikariTextMuted,
        )

        Spacer(Modifier.height(14.dp))

        // Schwierigkeitsgrade
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TttDifficulty.entries.forEach { d ->
                val selected = difficulty == d
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) HikariPrimary else HikariCardBg)
                        .clickable {
                            if (difficulty != d) {
                                difficulty = d
                                resetBoard(switchStarter = false)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        d.label,
                        fontSize = 13.sp,
                        color = if (selected) Color.Black else HikariTextMuted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // Ergebnis-Bereich (feste Höhe, damit das Brett nicht springt)
        Box(Modifier.height(110.dp), contentAlignment = Alignment.Center) {
            if (winner != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val msg = when (winner) {
                        "X" -> "Du gewinnst!"
                        "O" -> "KI gewinnt!"
                        else -> "Unentschieden!"
                    }
                    val col = when (winner) {
                        "X" -> Color(0xFF4ADE80)
                        "O" -> Color(0xFFFF5252)
                        else -> HikariText
                    }
                    Text(msg, fontSize = 26.sp, color = col, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { resetBoard(switchStarter = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                    ) {
                        Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (board.all { it.isEmpty() } && !humanStarts) {
                Text("KI beginnt diese Runde", fontSize = 13.sp, color = HikariTextFaint)
            }
        }

        // Brett
        Column {
            repeat(3) { r ->
                Row {
                    repeat(3) { c ->
                        val i = r * 3 + c
                        val cell = board[i]
                        val isWinCell = winLine?.contains(i) == true
                        Box(
                            Modifier
                                .size(96.dp)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isWinCell) HikariPrimary.copy(alpha = 0.25f) else HikariCardBg)
                                .pointerInput(i) { detectTapGestures { tap(i) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            TttCellSymbol(cell = cell, highlight = isWinCell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TttCellSymbol(cell: String, highlight: Boolean) {
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
        cell,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = when {
            cell == "X" && highlight -> HikariPrimary
            cell == "O" && highlight -> HikariPrimary
            cell == "X" -> Color(0xFF60A5FA)
            cell == "O" -> Color(0xFFFF8A65)
            else -> Color.Transparent
        },
        modifier = Modifier.scale(anim.value),
    )
}
