package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlin.random.Random

@Composable
fun MinesweeperGame(onBack: () -> Unit) {
    val rows = 9
    val cols = 9
    val mines = 10
    val dirs = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

    var grid by remember { mutableStateOf(Array(rows * cols) { Cell() }) }
    var gameOver by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var flags by remember { mutableStateOf(0) }

    fun idx(r: Int, c: Int) = r * cols + c

    fun init(clickedIdx: Int) {
        val mineIdx = mutableSetOf<Int>()
        while (mineIdx.size < mines) {
            val m = Random.nextInt(rows * cols)
            if (m != clickedIdx) mineIdx.add(m)
        }
        grid = Array(rows * cols) { i ->
            val count = if (i in mineIdx) -1 else {
                val r = i / cols; val c = i % cols
                dirs.count { (dr, dc) ->
                    val nr = r + dr; val nc = c + dc
                    nr in 0 until rows && nc in 0 until cols && idx(nr, nc) in mineIdx
                }
            }
            Cell(isMine = i in mineIdx, adjacentMines = count)
        }
        started = true
        gameOver = false
        gameWon = false
        flags = 0
    }

    fun reveal(idx: Int) {
        if (grid[idx].revealed || grid[idx].flagged) return
        val new = grid.copyOf()
        new[idx] = new[idx].copy(revealed = true)
        if (new[idx].isMine) { grid = new; gameOver = true; return }
        if (new[idx].adjacentMines == 0) {
            val r = idx / cols; val c = idx % cols
            dirs.forEach { (dr, dc) ->
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols) reveal(idx(nr, nc))
            }
        }
        grid = new
        if (grid.count { !it.revealed } == mines) gameWon = true
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            Text("💣 $flags/$mines", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
            TextButton(onClick = { started = false; gameOver = false; gameWon = false }) {
                Text("Neu", color = HikariPrimary, fontWeight = FontWeight.Bold)
            }
        }

        if (gameWon) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gewonnen!", fontSize = 32.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { started = false; gameOver = false; gameWon = false },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                        Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBack) { Text("Zurück") }
                }
            }
        } else if (gameOver) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Game Over!", fontSize = 32.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { started = false; gameOver = false; gameWon = false },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                        Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBack) { Text("Zurück") }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                        repeat(cols) { c ->
                            val i = idx(r, c)
                            val cell = grid[i]
                            val bg = when {
                                cell.revealed && cell.isMine -> Color(0xFFFF5252)
                                cell.revealed -> HikariCardBg
                                else -> Color(0xFF2A2A2A)
                            }
                            val txt = when {
                                !cell.revealed && cell.flagged -> "🚩"
                                !cell.revealed -> ""
                                cell.isMine -> "💣"
                                cell.adjacentMines == 0 -> ""
                                else -> cell.adjacentMines.toString()
                            }
                            val txtColor = when (cell.adjacentMines) {
                                1 -> Color(0xFF4ADE80); 2 -> Color(0xFF60A5FA)
                                3 -> Color(0xFFFBBF24); 4 -> Color(0xFFFF8A65)
                                else -> Color(0xFFFF5252)
                            }

                            Box(
                                Modifier.size(36.dp).padding(1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bg)
                                    .border(0.5.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
                                    .pointerInput(i) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (!started) init(i)
                                                if (!grid[i].revealed && !gameOver && !gameWon) {
                                                    grid = grid.copyOf()
                                                    grid[i] = grid[i].copy(flagged = !grid[i].flagged)
                                                    flags += if (grid[i].flagged) 1 else -1
                                                }
                                            },
                                            onTap = {
                                                if (!started) init(i)
                                                if (!grid[i].flagged && !gameOver && !gameWon) reveal(i)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(txt, fontSize = 12.sp, color = txtColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class Cell(val isMine: Boolean = false, val adjacentMines: Int = 0, val revealed: Boolean = false, val flagged: Boolean = false)
