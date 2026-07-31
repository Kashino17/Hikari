package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun ConnectFourGame(onBack: () -> Unit) {
    val cols = 7
    val rows = 6
    var board by remember { mutableStateOf(CharArray(cols * rows) { ' ' }) }
    var turn by remember { mutableStateOf('P') }   // P = Spieler, C = KI
    var winner by remember { mutableStateOf<Char?>(null) } // P, C, 'T'
    var lastDrop by remember { mutableStateOf(-1) }
    val playerColor = Color(0xFFFBBF24)
    val cpuColor = Color(0xFF60A5FA)
    val empty = ' '

    fun dropIndex(b: CharArray, col: Int): Int {
        if (col < 0 || col >= cols) return -1
        for (r in (rows - 1) downTo 0) if (b[r * cols + col] == empty) return r * cols + col
        return -1
    }

    fun checkWin(b: CharArray, idx: Int): Char? {
        if (idx < 0) return null
        val p = b[idx]; if (p == empty) return null
        val c = idx % cols; val r = idx / cols
        for ((dc, dr) in listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)) {
            var count = 1
            var cc = c + dc; var rr = r + dr
            while (cc in 0 until cols && rr in 0 until rows && b[rr * cols + cc] == p) { count++; cc += dc; rr += dr }
            cc = c - dc; rr = r - dr
            while (cc in 0 until cols && rr in 0 until rows && b[rr * cols + cc] == p) { count++; cc -= dc; rr -= dr }
            if (count >= 4) return p
        }
        return null
    }

    fun isFull(b: CharArray) = b.all { it != empty }

    fun play(col: Int) {
        if (turn != 'P' || winner != null) return
        val di = dropIndex(board, col); if (di < 0) return
        board = board.copyOf().also { it[di] = 'P' }
        lastDrop = di
        winner = checkWin(board, di)
        if (winner == null && isFull(board)) { winner = 'T'; return }
        if (winner == null) turn = 'C'
    }

    LaunchedEffect(turn) {
        if (turn == 'C' && winner == null) {
            delay(450)
            val valid = (0 until cols).filter { dropIndex(board, it) >= 0 }
            if (valid.isEmpty()) { winner = 'T'; return@LaunchedEffect }
            var choice: Int? = null
            // 1) gewinnen
            for (col in valid) { val t = board.copyOf(); val di = dropIndex(t, col); t[di] = 'C'; if (checkWin(t, di) == 'C') { choice = col; break } }
            // 2) blocken
            if (choice == null) for (col in valid) { val t = board.copyOf(); val di = dropIndex(t, col); t[di] = 'P'; if (checkWin(t, di) == 'P') { choice = col; break } }
            // 3) mittig bevorzugen, sonst zufällig
            if (choice == null) choice = valid.minByOrNull { abs(it - 3) } ?: valid.random()
            val di = dropIndex(board, choice!!)
            board = board.copyOf().also { it[di] = 'C' }
            lastDrop = di
            winner = checkWin(board, di)
            if (winner == null && isFull(board)) winner = 'T'
            turn = 'P'
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Vier Gewinnt", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text(if (winner == null && turn == 'C') "KI…" else "", fontSize = 12.sp, color = HikariTextMuted)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(playerColor))
            Spacer(Modifier.width(6.dp)); Text("Du", fontSize = 13.sp, color = HikariText)
            Spacer(Modifier.width(18.dp))
            Box(Modifier.size(14.dp).clip(CircleShape).background(cpuColor))
            Spacer(Modifier.width(6.dp)); Text("KI", fontSize = 13.sp, color = HikariText)
        }

        if (winner != null) {
            Spacer(Modifier.height(14.dp))
            val (msg, col) = when (winner) { 'P' -> "Du gewinnst! 🎉" to Color(0xFF4ADE80); 'C' -> "KI gewinnt" to Color(0xFFFF5252); else -> "Unentschieden!" to HikariText }
            Text(msg, fontSize = 26.sp, color = col, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { board = CharArray(cols * rows) { ' ' }; turn = 'P'; winner = null; lastDrop = -1 },
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Spielfeld
        Surface(color = HikariCardBg, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(8.dp)) {
                for (r in 0 until rows) {
                    Row {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            val v = board[i]
                            Box(
                                Modifier
                                    .padding(3.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (v == empty) HikariBg.copy(alpha = 0.55f)
                                        else if (v == 'P') playerColor else cpuColor)
                                    .clickable { play(c) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (i == lastDrop && winner != null) Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Tippe auf eine Spalte, um den Stein fallen zu lassen", fontSize = 12.sp, color = HikariTextMuted)
    }
}
