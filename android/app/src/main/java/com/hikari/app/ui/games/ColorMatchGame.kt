package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun ColorMatchGame(onBack: () -> Unit) {
    val allColors = listOf(
        Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFFBBF24),
        Color(0xFFA78BFA), Color(0xFFFF8A65), Color(0xFFF472B6),
        Color(0xFF2DD4BF), Color(0xFFFB923C),
    )

    var tileColors by remember { mutableStateOf(listOf<Color>()) }
    var targetColor by remember { mutableStateOf(Color.White) }
    var score by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(3) }
    var gameStarted by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(30) }
    var streak by remember { mutableStateOf(0) }
    var bestStreak by remember { mutableStateOf(0) }
    var correctIdx by remember { mutableStateOf(-1) }

    fun newRound() {
        val picked = allColors.shuffled().take(level)
        tileColors = picked
        correctIdx = Random.nextInt(picked.size)
        // Make target slightly different from one of the colors
        val base = picked[correctIdx]
        val offset = if (Random.nextBoolean()) 20 else -20
        val r = (base.red * 255 + offset).toInt().coerceIn(0, 255)
        val g = (base.green * 255 + offset).toInt().coerceIn(0, 255)
        val b = (base.blue * 255 + (if (Random.nextBoolean()) offset else -offset)).toInt().coerceIn(0, 255)
        targetColor = Color(r, g, b)
    }

    LaunchedEffect(gameStarted, gameOver) {
        if (!gameStarted || gameOver) return@LaunchedEffect
        while (!gameOver) {
            delay(1000)
            timeLeft--
            if (timeLeft <= 0) { timeLeft = 0; gameOver = true }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("🌟 $score", fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("⏱ $timeLeft", fontSize = 16.sp, color = if (timeLeft < 5) Color(0xFFFF5252) else HikariTextMuted)
        }

        if (streak > 1) {
            Text("$streak× Streak! 🔥", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 16.sp, color = Color(0xFFFF8A65), fontWeight = FontWeight.Bold)
        }

        if (!gameStarted) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(100.dp))
                Text("Color Match", fontSize = 36.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Finde die Farbe, die NICHT zum Ziel passt!", fontSize = 14.sp, color = HikariTextMuted)
                Text("Der Farbton des Targets weicht leicht ab.", fontSize = 13.sp, color = HikariTextMuted)
                Spacer(Modifier.height(48.dp))
                Button(onClick = {
                    score = 0; level = 3; timeLeft = 30; streak = 0; bestStreak = 0
                    gameStarted = true; gameOver = false; newRound()
                }, modifier = Modifier.fillMaxWidth(0.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                    Text("Start", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else if (gameOver) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(80.dp))
                Text("Zeit abgelaufen!", fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$score Punkte", fontSize = 48.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Text("Beste Streak: $bestStreak×", fontSize = 16.sp, color = HikariTextMuted)
                Spacer(Modifier.height(48.dp))
                Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = {
                        score = 0; level = 3; timeLeft = 30; streak = 0; bestStreak = 0
                        gameStarted = true; gameOver = false; newRound()
                    }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                        Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Zurück") }
                }
            }
        } else {
            // Target display
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ziel-Farbe", fontSize = 12.sp, color = HikariTextMuted)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(targetColor)
                        .border(2.dp, HikariPrimary, RoundedCornerShape(16.dp)))
                }
            }

            // Color tiles
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                repeat((tileColors.size + 1) / 2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(2) { col ->
                            val idx = row * 2 + col
                            if (idx < tileColors.size) {
                                val colr = tileColors[idx]
                                Box(Modifier.size(72.dp).padding(4.dp)
                                    .clip(RoundedCornerShape(16.dp)).background(colr)
                                    .border(2.dp, Color(0xFF444444), RoundedCornerShape(16.dp))
                                    .pointerInput(idx) { detectTapGestures { onTap ->
                                        if (gameOver || !gameStarted) return@detectTapGestures
                                        if (idx == correctIdx) {
                                            score += 10 * (1 + streak)
                                            streak++
                                            if (streak > bestStreak) bestStreak = streak
                                            timeLeft += 3
                                            if (level < 8) level++
                                            newRound()
                                        } else {
                                            streak = 0
                                            timeLeft -= 3
                                            if (timeLeft <= 0) gameOver = true
                                        }
                                    } })
                            }
                        }
                    }
                }
            }
        }
    }
}
