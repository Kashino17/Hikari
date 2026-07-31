package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun TapNPopGame(onBack: () -> Unit) {
    var score by remember { mutableStateOf(0) }
    var bubbles by remember { mutableStateOf<List<Bubble>>(emptyList()) }
    var missed by remember { mutableStateOf(0) }
    var gameRunning by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var bestCombo by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }

    fun spawn() {
        bubbles = bubbles + Bubble(
            x = Random.nextFloat() * 0.8f + 0.1f,
            y = 1.1f,
            speed = 0.003f + Random.nextFloat() * 0.005f,
            color = listOf(
                Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFFBBF24),
                Color(0xFFA78BFA), Color(0xFFFF8A65), Color(0xFFF472B6)
            ).random(),
            size = 20f + Random.nextFloat() * 25f,
        )
    }

    LaunchedEffect(gameRunning) {
        if (!gameRunning) return@LaunchedEffect
        while (!gameOver && gameRunning) {
            val t = Random.nextLong(400, 900)
            delay(t)
            if (gameRunning && !gameOver) spawn()
        }
    }

    LaunchedEffect(gameRunning, bubbles) {
        if (!gameRunning) return@LaunchedEffect
        while (!gameOver) {
            delay(16)
            var newMissed = missed
            bubbles = bubbles.mapNotNull { b ->
                val ny = b.y - b.speed
                if (ny < -0.1f) { newMissed++; null }
                else b.copy(y = ny)
            }
            missed = newMissed
            if (missed >= 5) { gameOver = true; gameRunning = false }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("🌟 $score", fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("❌ $missed/5", fontSize = 14.sp, color = if (missed >= 3) Color(0xFFFF5252) else HikariTextMuted)
        }

        if (combo > 1) {
            Text("$combo× COMBO!", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 20.sp, color = Color(0xFFFF8A65), fontWeight = FontWeight.Bold)
        }

        if (!gameRunning && !gameOver) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(100.dp))
                Text("Tap 'n Pop", fontSize = 36.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Tippe alle Blasen, bevor sie verschwinden!", fontSize = 14.sp, color = HikariTextMuted)
                Spacer(Modifier.height(48.dp))
                Button(onClick = {
                    score = 0; bubbles = emptyList(); missed = 0; gameRunning = true; gameOver = false; combo = 0; bestCombo = 0
                }, modifier = Modifier.fillMaxWidth(0.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                    Text("Start", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (gameOver) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(80.dp))
                Text("Game Over", fontSize = 28.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("$score Punkte", fontSize = 48.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Text("Beste Combo: $bestCombo×", fontSize = 16.sp, color = HikariTextMuted)
                Spacer(Modifier.height(48.dp))
                Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = {
                        score = 0; bubbles = emptyList(); missed = 0; gameRunning = true; gameOver = false; combo = 0; bestCombo = 0
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                        Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Zurück") }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectTapGestures { tap ->
                    val tx = tap.x / size.width; val ty = tap.y / size.height
                    var hit = false
                    bubbles = bubbles.mapNotNull { b ->
                        val dx = tx - b.x; val dy = ty - b.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist < b.size / size.width) { hit = true; null } else b
                    }
                    if (hit) {
                        combo++; score += 10 * combo
                        if (combo > bestCombo) bestCombo = combo
                    } else {
                        combo = 0
                    }
                }
            }) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    bubbles.forEach { b ->
                        val cx = b.x * size.width
                        val cy = b.y * size.height
                        drawCircle(b.color, radius = b.size, center = androidx.compose.ui.geometry.Offset(cx, cy))
                        drawCircle(Color.White.copy(alpha = 0.3f), radius = b.size * 0.3f,
                            center = androidx.compose.ui.geometry.Offset(cx - b.size * 0.2f, cy - b.size * 0.2f))
                    }
                }
            }
        }
    }
}

data class Bubble(val x: Float, val y: Float, val speed: Float, val color: Color, val size: Float)