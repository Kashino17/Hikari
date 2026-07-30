package com.hikari.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Game 5: Orbit — drehe den Ring und weiche den Asteroiden aus!
 * Tippe links/rechts um die Richtung zu wechseln.
 */
@Composable
fun OrbitGame(onBack: () -> Unit) {
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var gameOver by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var playerAngle by remember { mutableStateOf(0f) }
    var asteroids by remember { mutableStateOf<List<Asteroid>>(emptyList()) }
    var rotationSpeed by remember { mutableStateOf(0.5f) }
    var spawnRate by remember { mutableStateOf(1200L) }
    var direction by remember { mutableStateOf(1) }
    var invincibleUntil by remember { mutableStateOf(0L) }

    fun spawnAsteroid() {
        val angle = Random.nextFloat() * 360f
        asteroids = asteroids + Asteroid(
            angle = angle,
            distance = 420f,
            speed = 1.5f + score * 0.02f,
            size = 15f + Random.nextFloat() * 20f,
        )
    }

    // Timer
    LaunchedEffect(gameStarted && !gameOver) {
        while (gameStarted && !gameOver) {
            delay(spawnRate.coerceIn(400, 1200))
            if (!gameOver) spawnAsteroid()
        }
    }

    // Game loop
    LaunchedEffect(playerAngle, asteroids, gameStarted, gameOver) {
        while (gameStarted && !gameOver) {
            delay(16)
            val now = System.currentTimeMillis()

            playerAngle = (playerAngle + direction * rotationSpeed * 3f) % 360f
            if (playerAngle < 0) playerAngle += 360f

            val updated = asteroids.map { a ->
                a.copy(distance = a.distance - a.speed)
            }.filter { a ->
                if (a.distance < 150f && a.distance > 90f) {
                    val diff = ((a.angle - playerAngle + 540) % 360) - 180f
                    if (abs(diff) < 20f && now > invincibleUntil) {
                        lives--
                        invincibleUntil = now + 1000
                        if (lives <= 0) gameOver = true
                        false
                    } else {
                        true
                    }
                } else if (a.distance < 50f) {
                    score++
                    false
                } else {
                    true
                }
            }
            asteroids = updated
            rotationSpeed += 0.02f
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        if (!gameStarted) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(100.dp))
                Text("Orbit", fontSize = 36.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Weiche den Asteroiden aus!", fontSize = 16.sp, color = HikariTextMuted,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Tippe links/rechts zum Drehen", fontSize = 14.sp, color = HikariTextMuted)
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
            GameOverScreen(title = "Orbit", score = score,
                onRestart = {
                    score = 0; lives = 3; gameOver = false; gameStarted = true
                    playerAngle = 0f; asteroids = emptyList()
                    rotationSpeed = 0.5f; spawnRate = 1200L
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
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val orbitR = 120f

                    drawCircle(color = Color(0xFF222222), radius = orbitR,
                        center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                    asteroids.forEach { a ->
                        val rad = a.angle * (Math.PI / 180.0).toFloat()
                        val ax = cx + cos(rad.toDouble()).toFloat() * a.distance
                        val ay = cy + sin(rad.toDouble()).toFloat() * a.distance
                        drawCircle(color = Color(0xFF888888), radius = a.size,
                            center = Offset(ax, ay))
                        drawCircle(color = Color(0xFFAAAAAA), radius = a.size * 0.4f,
                            center = Offset(ax - a.size * 0.2f, ay - a.size * 0.2f))
                    }

                    val playerRad = playerAngle * (Math.PI / 180.0).toFloat()
                    val px = cx + cos(playerRad.toDouble()).toFloat() * orbitR
                    val py = cy + sin(playerRad.toDouble()).toFloat() * orbitR
                    val isInvincible = System.currentTimeMillis() < invincibleUntil
                    if (!isInvincible) {
                        drawCircle(color = HikariPrimary.copy(alpha = 0.3f), radius = 24f, center = Offset(px, py))
                        drawCircle(color = HikariPrimary, radius = 14f, center = Offset(px, py))
                        drawCircle(color = Color.White, radius = 5f, center = Offset(px - 3f, py - 3f))
                    }

                    drawCircle(color = Color(0xFF111111), radius = 40f, center = Offset(cx, cy))
                    drawCircle(color = HikariPrimary.copy(alpha = 0.1f), radius = 30f, center = Offset(cx, cy))
                }
            }

            Row(Modifier.fillMaxWidth().weight(0.3f)) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .pointerInput(Unit) { detectTapGestures { direction = -1 } }
                        .background(HikariCardBg.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text("◀", fontSize = 32.sp, color = HikariTextMuted.copy(alpha = 0.5f)) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .pointerInput(Unit) { detectTapGestures { direction = 1 } }
                        .background(HikariCardBg.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", fontSize = 32.sp, color = HikariTextMuted.copy(alpha = 0.5f)) }
            }
        }
    }
}

data class Asteroid(val angle: Float, val distance: Float, val speed: Float, val size: Float)

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
