package com.hikari.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.absoluteValue
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlin.random.Random

/**
 * Game 1: Neon Catch — fange fallende Kristalle mit dem Korb.
 * Swipe left/right to move. Collect green (+10), avoid red (-1 point, lives -1).
 */
@Composable
fun NeonCatchGame(onBack: () -> Unit) {
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var gameOver by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<CatchItem>>(emptyList()) }
    var basketX by remember { mutableStateOf(0.5f) }
    var speed by remember { mutableStateOf(2f) }
    var spawnTimer by remember { mutableStateOf(0) }

    if (gameOver) {
        GameOverScreen(
            title = "Neon Catch",
            score = score,
            onRestart = {
                score = 0; lives = 3; gameOver = false; items = emptyList()
                basketX = 0.5f; speed = 2f; spawnTimer = 0
            },
            onBack = onBack,
        )
        return
    }

    LaunchedEffect(Unit) {
        while (!gameOver) {
            delay((1000f / speed).toLong())
            if (!gameOver) {
                spawnTimer++
                items = items + CatchItem(
                    x = Random.nextFloat(),
                    y = -0.05f,
                    type = if (Random.nextInt(5) == 0) CatchItem.Type.BAD else CatchItem.Type.GOOD,
                )
            }
        }
    }

    // Gravity
    LaunchedEffect(items) {
        while (!gameOver) {
            delay(16) // ~60fps
            val hitItems = mutableListOf<CatchItem>()
            val removedItems = mutableListOf<CatchItem>()
            items.forEach { item ->
                val newY = item.y + speed * 0.016f
                if (newY > 0.82f && newY < 0.92f &&
                    abs(item.x - basketX) < 0.08f) {
                    if (item.type == CatchItem.Type.GOOD) score += 10
                    else { lives--; if (lives <= 0) gameOver = true }
                    removedItems.add(item)
                } else if (newY > 1f) {
                    if (item.type == CatchItem.Type.GOOD) { lives--; if (lives <= 0) gameOver = true }
                    removedItems.add(item)
                } else {
                    hitItems.add(item.copy(y = newY))
                }
            }
            items = hitItems
            speed = 2f + score / 100f // slowly increase
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        // HUD
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            Text("⭐ $score", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("❤️ ".repeat(lives).ifEmpty { "💀" }, fontSize = 18.sp)
        }

        // Game area + drag input (merged for responsive control)
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        basketX = (basketX + dragAmount.x / size.width)
                            .coerceIn(0.08f, 0.92f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Render items and basket in the same area as input
                items.forEach { item ->
                    val cx = item.x * size.width
                    val cy = item.y * size.height
                    val color = if (item.type == CatchItem.Type.GOOD)
                        Color(0xFF4ADE80) else Color(0xFFFF5252)
                    drawCircle(color, radius = 18f, center = Offset(cx, cy))
                    drawCircle(Color.White.copy(alpha = 0.4f), radius = 8f,
                        center = Offset(cx - 4f, cy - 4f))
                }
                // Basket
                val bx = basketX * size.width
                drawRect(
                    color = HikariPrimary,
                    topLeft = Offset(bx - 40f, size.height * 0.86f),
                    size = androidx.compose.ui.geometry.Size(80f, 16f),
                )
            }
        }
    }
}

data class CatchItem(val x: Float, val y: Float, val type: Type) {
    enum class Type { GOOD, BAD }
}

@Composable
private fun GameOverScreen(
    title: String,
    score: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(HikariBg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Text(title, fontSize = 28.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Game Over!", fontSize = 22.sp, color = HikariText)
        Spacer(Modifier.height(8.dp))
        Text("Score: $score", fontSize = 48.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))
        Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
            ) {
                Text("Nochmal", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HikariText),
            ) {
                Text("Zurück", fontSize = 16.sp)
            }
        }
    }
}
