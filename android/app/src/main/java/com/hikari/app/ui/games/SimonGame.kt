package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun SimonGame(onBack: () -> Unit) {
    // 0=grün (ol), 1=rot (or), 2=gelb (ul), 3=blau (ur)
    val baseColors = listOf(Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFFFBBF24), Color(0xFF3B82F6))
    val litColors = listOf(Color(0xFF86EFAC), Color(0xFFFCA5A5), Color(0xFFFDE68A), Color(0xFF93C5FD))

    var sequence by remember { mutableStateOf<List<Int>>(emptyList()) }
    var started by remember { mutableStateOf(false) }
    var playingSeq by remember { mutableStateOf(false) }
    var activePad by remember { mutableStateOf(-1) }
    var userInput by remember { mutableStateOf<List<Int>>(emptyList()) }
    var gameOver by remember { mutableStateOf(false) }
    var userFlash by remember { mutableStateOf(-1) }

    val round = sequence.size
    val score = if (gameOver) sequence.size - 1 else sequence.size - 1
    val best = remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun nextRound() {
        sequence = sequence + Random.nextInt(4)
        playingSeq = true
        userInput = emptyList()
        activePad = -1
    }

    // Sequenz abspielen
    LaunchedEffect(sequence) {
        if (!started || sequence.isEmpty()) return@LaunchedEffect
        playingSeq = true
        delay(450)
        for (pad in sequence) {
            activePad = pad
            delay(420)
            activePad = -1
            delay(180)
        }
        playingSeq = false
    }

    fun tap(pad: Int) {
        if (playingSeq || !started || gameOver || sequence.isEmpty()) return
        userFlash = pad
        if (pad == sequence[userInput.size]) {
            userInput = userInput + pad
            if (userInput.size == sequence.size) {
                // Runde geschafft → nächste
                if (sequence.size - 1 > best.value) best.value = sequence.size - 1
                scope.launch { delay(600); if (!gameOver) nextRound() }
            }
        } else {
            gameOver = true
            if (sequence.size - 1 > best.value) best.value = sequence.size - 1
        }
    }

    // User-Flash nach kurzer Zeit zurücksetzen
    LaunchedEffect(userFlash) {
        if (userFlash != -1) { delay(220); userFlash = -1 }
    }

    Column(Modifier.fillMaxSize().background(HikariBg), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Simon", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("Best: ${best.value}", fontSize = 12.sp, color = HikariTextMuted)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                gameOver -> "Game Over — Level $score"
                !started -> "Merke dir die Reihenfolge"
                playingSeq -> "Zuschauen…"
                else -> "Level $score — deine Eingabe"
            },
            fontSize = 18.sp, color = if (gameOver) Color(0xFFFF5252) else HikariPrimary, fontWeight = FontWeight.Bold,
        )

        if (gameOver) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                sequence = emptyList(); started = true; playingSeq = false; gameOver = false
                userInput = emptyList(); activePad = -1
                nextRound()
            }, colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (!started && !gameOver) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = { started = true; nextRound() },
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                Text("Start", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // 2×2 Pads
        Column {
            for (r in 0 until 2) {
                Row {
                    for (c in 0 until 2) {
                        val pad = r * 2 + c
                        val lit = activePad == pad || userFlash == pad
                        Box(
                            Modifier.padding(6.dp).size(124.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (lit) litColors[pad] else baseColors[pad].copy(alpha = 0.55f))
                                .clickable { tap(pad) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Tippe die Pads in derselben Reihenfolge nach", fontSize = 12.sp, color = HikariTextMuted)
    }
}
