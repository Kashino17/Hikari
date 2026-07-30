package com.hikari.app.ui.games

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
private fun GameOverScreen(title: String, score: Int, onRestart: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HikariBg), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(80.dp))
        Text(title, fontSize = 28.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Game Over!", fontSize = 22.sp, color = HikariText)
        Spacer(Modifier.height(8.dp))
        Text("Score: $score", fontSize = 48.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))
        Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onRestart, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
            ) { Text("Nochmal", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBack, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HikariText),
            ) { Text("Zurück", fontSize = 16.sp) }
        }
    }
}

@Composable
fun LaneRunnerGame(onBack: () -> Unit) {
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var gameOver by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var playerLane by remember { mutableStateOf(1) }
    var obstacles by remember { mutableStateOf<List<Obstacle>>(emptyList()) }
    var speed by remember { mutableStateOf(3f) }

    fun spawnObstacle() {
        val blocked = when (Random.nextInt(3)) {
            0 -> listOf(0)
            1 -> listOf(2)
            else -> listOf(Random.nextInt(3))
        }
        blocked.forEach { lane ->
            obstacles = obstacles + Obstacle(lane = lane, y = -0.1f)
        }
    }

    LaunchedEffect(gameStarted, gameOver) {
        while (gameStarted && !gameOver) {
            delay((1000f / speed * 3).toLong())
            if (!gameOver) spawnObstacle()
        }
    }

    LaunchedEffect(playerLane, obstacles, gameStarted, gameOver) {
        while (gameStarted && !gameOver) {
            delay(16)
            val updated = obstacles.map { obs ->
                obs.copy(y = obs.y + speed * 0.012f)
            }.filter { obs ->
                if (obs.y > 0.75f && obs.y < 0.95f && obs.lane == playerLane) {
                    lives--
                    if (lives <= 0) gameOver = true
                    false
                } else if (obs.y > 1.1f) {
                    score++
                    false
                } else {
                    true
                }
            }
            obstacles = updated
            speed = 3f + score * 0.05f
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        if (!gameStarted) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(100.dp))
                Text("Lane Runner", fontSize = 32.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Swipe links/rechts zum Wechseln!", fontSize = 16.sp, color = HikariTextMuted,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(64.dp))
                Button(
                    onClick = { gameStarted = true },
                    modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) {
                    Text("Start", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        } else if (gameOver) {
            GameOverScreen(title = "Lane Runner", score = score,
                onRestart = {
                    score = 0; lives = 3; gameOver = false; gameStarted = true
                    playerLane = 1; obstacles = emptyList(); speed = 3f
                },
                onBack = onBack,
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
                Text("⭐ $score", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Text("❤️ ".repeat(lives), fontSize = 18.sp)
            }

            Box(Modifier.weight(1f)) {
                Canvas(Modifier.fillMaxSize()) {
                    val laneWidth = size.width / 3f
                    for (i in 1..2) {
                        drawLine(
                            color = Color(0xFF222222),
                            start = Offset(i * laneWidth, 0f),
                            end = Offset(i * laneWidth, size.height),
                            strokeWidth = 2f,
                        )
                    }

                    val px = playerLane * laneWidth + laneWidth / 2f
                    val py = size.height * 0.85f
                    drawCircle(
                        color = HikariPrimary.copy(alpha = 0.3f),
                        radius = 28f,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = HikariPrimary,
                        radius = 20f,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 7f,
                        center = Offset(px - 4f, py - 4f),
                    )

                    obstacles.forEach { obs ->
                        val ox = obs.lane * laneWidth + laneWidth / 2f
                        val oy = obs.y * size.height
                        drawCircle(
                            color = Color(0xFFFF5252),
                            radius = 18f,
                            center = Offset(ox, oy),
                        )
                        drawCircle(
                            color = Color(0xFF880000),
                            radius = 10f,
                            center = Offset(ox, oy),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().weight(0.3f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(onHorizontalDrag = { _, dragAmount ->
                                if (abs(dragAmount) > 30f) {
                                    if (dragAmount < 0f && playerLane > 0) playerLane--
                                    if (dragAmount > 0f && playerLane < 2) playerLane++
                                }
                            })
                        }
                        .background(HikariCardBg.copy(alpha = 0.2f)),
                ) {}
            }
        }
    }
}

data class Obstacle(val lane: Int, val y: Float)
