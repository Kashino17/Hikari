package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlin.random.Random

@Composable
fun TicTacToeGame(onBack: () -> Unit) {
    var board by remember { mutableStateOf(Array(9) { "" }) }
    var turn by remember { mutableStateOf("X") }
    var winner by remember { mutableStateOf<String?>(null) }
    var aiThinking by remember { mutableStateOf(false) }

    val wins = listOf(
        listOf(0,1,2), listOf(3,4,5), listOf(6,7,8),
        listOf(0,3,6), listOf(1,4,7), listOf(2,5,8),
        listOf(0,4,8), listOf(2,4,6),
    )

    fun checkWinner(): String? {
        for (w in wins) {
            val a = board[w[0]]; val b = board[w[1]]; val c = board[w[2]]
            if (a == b && b == c && a.isNotEmpty()) return a
        }
        return if (board.all { it.isNotEmpty() }) "Tie" else null
    }

    fun aiMove() {
        aiThinking = true
        val empty = board.indices.filter { board[it].isEmpty() }
        if (empty.isNotEmpty()) {
            // Try win, then block, then random
            var choice: Int? = null
            for (i in empty) {
                board[i] = "O"; if (checkWinner() == "O") { choice = i; board[i] = ""; break }; board[i] = ""
            }
            if (choice == null) for (i in empty) {
                board[i] = "X"; if (checkWinner() == "X") { choice = i; board[i] = ""; break }; board[i] = ""
            }
            if (choice == null) choice = empty.random()
            board = board.copyOf()
            board[choice!!] = "O"
            winner = checkWinner()
            turn = "X"
        }
        aiThinking = false
    }

    fun tap(idx: Int) {
        if (board[idx].isNotEmpty() || winner != null || aiThinking || turn != "X") return
        board = board.copyOf()
        board[idx] = "X"
        turn = "O"
        winner = checkWinner()
        if (winner == null) aiMove()
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

        Spacer(Modifier.height(24.dp))

        if (winner != null) {
            val msg = when (winner) { "X" -> "Du gewinnst!"; "O" -> "KI gewinnt!"; else -> "Unentschieden!" }
            val col = when (winner) { "X" -> Color(0xFF4ADE80); "O" -> Color(0xFFFF5252); else -> HikariText }
            Text(msg, fontSize = 28.sp, color = col, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { board = Array(9) { "" }; turn = "X"; winner = null },
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Board
        Column {
            repeat(3) { r ->
                Row {
                    repeat(3) { c ->
                        val i = r * 3 + c
                        val cell = board[i]
                        val isWinCell = winner != null && wins.any { w -> w.contains(i) && cell.isNotEmpty()
                            && board[w[0]] == board[w[1]] && board[w[1]] == board[w[2]] }
                        Box(Modifier.size(96.dp).padding(4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWinCell) HikariPrimary.copy(alpha = 0.2f) else HikariCardBg)
                            .pointerInput(i) { detectTapGestures { tap(i) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cell, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                                color = when (cell) { "X" -> Color(0xFF60A5FA); "O" -> Color(0xFFFF8A65); else -> Color.Transparent })
                        }
                    }
                }
            }
        }
    }
}
