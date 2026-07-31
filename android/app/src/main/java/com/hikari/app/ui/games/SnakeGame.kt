package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun SnakeGame(onBack: () -> Unit) {
    val gridSize = 20
    var snake by remember { mutableStateOf(listOf(Pair(10, 10), Pair(10, 9), Pair(10, 8))) }
    var dir by remember { mutableStateOf(Pair(0, 1)) }
    var food by remember { mutableStateOf(Pair(5, 5)) }
    var score by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    fun spawnFood() {
        val body = snake.toSet()
        var f: Pair<Int, Int>
        do { f = Pair(Random.nextInt(gridSize), Random.nextInt(gridSize)) } while (f in body)
        food = f
    }

    LaunchedEffect(started, gameOver) {
        if (!started || gameOver) return@LaunchedEffect
        while (!gameOver) {
            delay(150)
            val head = snake.first()
            val newHead = Pair(
                (head.first + dir.first + gridSize) % gridSize,
                (head.second + dir.second + gridSize) % gridSize
            )
            if (newHead in snake) { gameOver = true; break }
            val newSnake = mutableListOf(newHead)
            newSnake.addAll(snake)
            if (newHead == food) {
                score += 10
                spawnFood()
            } else {
                newSnake.removeAt(newSnake.lastIndex)
            }
            snake = newSnake
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            Text("🐍 $score", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            if (!started || gameOver) {
                Button(
                    onClick = {
                        snake = listOf(Pair(10, 10), Pair(10, 9), Pair(10, 8))
                        dir = Pair(0, 1); score = 0; gameOver = false; started = true
                        spawnFood()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) { Text("Start", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }

        if (gameOver) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Game Over!", fontSize = 28.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    Text("Score: $score", fontSize = 20.sp, color = HikariText)
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x; val dy = dragAmount.y
                        if (abs(dx) > abs(dy)) {
                            if (dx > 0 && dir.second == 0) dir = Pair(0, 1)
                            else if (dx < 0 && dir.second == 0) dir = Pair(0, -1)
                        } else {
                            if (dy > 0 && dir.first == 0) dir = Pair(1, 0)
                            else if (dy < 0 && dir.first == 0) dir = Pair(-1, 0)
                        }
                    }
                }
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val cellW = size.width / gridSize
                val cellH = size.height / gridSize
                // Food
                drawCircle(Color(0xFFFF5252), radius = cellW * 0.4f, center = Offset(food.second * cellW + cellW / 2, food.first * cellH + cellH / 2))
                // Snake
                snake.forEachIndexed { i, (r, c) ->
                    val alpha = 1f - i * 0.03f
                    drawRoundRect(
                        HikariPrimary.copy(alpha = alpha),
                        topLeft = Offset(c * cellW + 1, r * cellH + 1),
                        size = androidx.compose.ui.geometry.Size(cellW - 2, cellH - 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                    )
                }
            }
        }

        // D-Pad
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DPadButton("▲") { if (dir.first == 0) dir = Pair(-1, 0) }
                Row {
                    DPadButton("◀") { if (dir.second == 0) dir = Pair(0, -1) }
                    Spacer(Modifier.width(8.dp))
                    DPadButton("▼") { if (dir.first == 0) dir = Pair(1, 0) }
                    Spacer(Modifier.width(8.dp))
                    DPadButton("▶") { if (dir.second == 0) dir = Pair(0, 1) }
                }
            }
        }
    }
}

@Composable
private fun DPadButton(text: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).padding(2.dp)
        .clip(RoundedCornerShape(8.dp)).background(HikariCardBg)
        .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Bold)
    }
}
