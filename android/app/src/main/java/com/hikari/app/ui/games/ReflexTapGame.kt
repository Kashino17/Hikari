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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Game 3: Reflex Tap — tappe so schnell wie möglich die Kreise.
 * Circles appear randomly; tap them before they disappear. Speed decreases over time.
 */
@Composable
fun ReflexTapGame(onBack: () -> Unit) {
    var score by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(30f) }
    var circles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var gameStarted by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var combo by remember { mutableStateOf(0) }
    var maxCombo by remember { mutableStateOf(0) }

    fun spawnCircle() {
        circles = circles + Circle(
            x = Random.nextFloat() * 0.7f + 0.15f,
            y = Random.nextFloat() * 0.6f + 0.15f,
            radius = 0.06f + Random.nextFloat() * 0.04f,
            life = 1f,
            decay = 0.008f + (30f - timeLeft) * 0.0002f, // gets faster
        )
    }

    // Timer
    LaunchedEffect(gameStarted && !gameOver) {
        while (gameStarted && !gameOver) {
            delay(1000)
            if (!gameOver) {
                timeLeft = timeLeft - 1f
                if (timeLeft <= 0) {
                    timeLeft = 0f
                    gameOver = true
                }
            }
        }
    }

    // Spawn loop
    LaunchedEffect(gameStarted, gameOver) {
        while (gameStarted && !gameOver) {
            delay(800 - minOf(score * 5, 400).toLong())
            if (!gameOver) spawnCircle()
        }
    }

    // Circle decay
    LaunchedEffect(circles) {
        while (gameStarted && !gameOver) {
            delay(16)
            circles = circles.map { it.copy(life = it.life - it.decay) }
                .filter { it.life > 0 }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        if (!gameStarted) {
            // Start screen
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(120.dp))
                Text("Reflex Tap", fontSize = 36.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Tippe die Kreise so schnell wie möglich!", fontSize = 16.sp, color = HikariTextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Je schneller, desto mehr Combo!", fontSize = 14.sp, color = HikariTextMuted)
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
            // Results
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(80.dp))
                Text("Zeit abgelaufen!", fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$score Punkte", fontSize = 56.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Max Combo: $maxCombo", fontSize = 18.sp, color = HikariTextMuted)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = {
                        score = 0; timeLeft = 30f; circles = emptyList(); gameOver = false
                        combo = 0; maxCombo = 0; gameStarted = true
                    },
                    modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) {
                    Text("Nochmal", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth()) {
                    Text("Zurück", fontSize = 16.sp)
                }
            }
        } else {
            // HUD
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
                Text("⏱ $timeLeft", fontSize = 18.sp, color = if (timeLeft < 5) Color(0xFFFF5252) else HikariPrimary,
                    fontWeight = FontWeight.Bold)
                Text("⭐ $score", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                if (combo > 1) Text("🔥 x$combo", fontSize = 18.sp, color = Color(0xFFFF8A65), fontWeight = FontWeight.Bold)
            }

            // Play area
            Canvas(Modifier.fillMaxSize()) {
                circles.forEach { circle ->
                    val cx = circle.x * size.width
                    val cy = circle.y * size.height
                    val r = circle.radius * size.width
                    val alpha = circle.life

                    // Outer glow
                    drawCircle(
                        HikariPrimary.copy(alpha = alpha * 0.3f),
                        radius = r * 1.8f,
                        center = Offset(cx, cy),
                    )
                    // Main circle
                    drawCircle(
                        HikariPrimary.copy(alpha = alpha),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                    // Inner highlight
                    drawCircle(
                        Color.White,
                        radius = r * 0.3f,
                        center = Offset(cx - r * 0.2f, cy - r * 0.2f),
                    )
                }
            }

            // Tap layer
            Canvas(Modifier.fillMaxSize()) {
                // Invisible — tap layer handled by pointerInput
            }

            // Tap detection overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            val tapX = tapOffset.x / size.width
                            val tapY = tapOffset.y / size.height
                            var hitIdx = -1
                            for (i in circles.indices.reversed()) {
                                val dx = tapX - circles[i].x
                                val dy = tapY - circles[i].y
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < circles[i].radius + 0.03f) {
                                    hitIdx = i
                                    break
                                }
                            }
                            if (hitIdx >= 0) {
                                combo++
                                if (combo > maxCombo) maxCombo = combo
                                val points = 10 * combo
                                score += points
                                circles = circles.toMutableList().apply { removeAt(hitIdx) }
                            } else {
                                combo = 0
                            }
                        }
                    }
                    .background(Color.Transparent),
            ) {}
        }
    }
}

data class Circle(val x: Float, val y: Float, val radius: Float, val life: Float, val decay: Float)
