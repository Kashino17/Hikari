package com.hikari.app.ui.games

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Game 4: Wortkette — verbinde Buchstaben zum Bilden von Wörtern.
 * Swipe through letters to form words. Minimum 2 letters.
 */
@Composable
fun WordChainGame(onBack: () -> Unit) {
    val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L")
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var score by remember { mutableStateOf(0) }
    var foundWords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentWord by remember { mutableStateOf("") }
    var gameStarted by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(60) }
    var lastTap by remember { mutableStateOf(0L) }

    fun resetRound() {
        selectedIndices = emptySet()
        currentWord = ""
    }

    fun onLetterTap(idx: Int) {
        val now = System.currentTimeMillis()
        if (now - lastTap > 300) selectedIndices = emptySet()
        lastTap = now

        val newSelected = if (selectedIndices.contains(idx)) {
            selectedIndices - idx
        } else {
            selectedIndices + idx
        }
        selectedIndices = newSelected

        if (newSelected.isNotEmpty()) {
            currentWord = newSelected.sorted().joinToString("")
        } else {
            currentWord = ""
        }
    }

    fun submitWord() {
        if (currentWord.length >= 2 && !foundWords.contains(currentWord)) {
            foundWords = foundWords + currentWord
            score += currentWord.length * 10
            resetRound()
        }
    }

    // Timer
    LaunchedEffect(gameStarted && !gameOver) {
        while (gameStarted && !gameOver) {
            delay(1000)
            if (!gameOver) {
                timeLeft--
                if (timeLeft <= 0) {
                    timeLeft = 0
                    gameOver = true
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        if (!gameStarted) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(100.dp))
                Text("Wortkette", fontSize = 36.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Tippe Buchstaben um Wörter zu bilden!", fontSize = 16.sp, color = HikariTextMuted,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Mindestens 2 Buchstaben", fontSize = 14.sp, color = HikariTextMuted)
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
            Column(Modifier.fillMaxSize().background(HikariBg), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(80.dp))
                Text("Zeit abgelaufen!", fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$score Punkte", fontSize = 56.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${foundWords.size} Wörter gefunden", fontSize = 18.sp, color = HikariTextMuted)
                Spacer(Modifier.height(48.dp))
                Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            score = 0; timeLeft = 60; gameOver = false; gameStarted = true
                            foundWords = emptySet(); resetRound()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                    ) { Text("Nochmal", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onBack, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HikariText),
                    ) { Text("Zurück", fontSize = 16.sp) }
                }
            }
        } else {
            // HUD
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
                Text("⏱ ${timeLeft}s", fontSize = 18.sp, color = if (timeLeft < 10) Color(0xFFFF5252) else HikariPrimary,
                    fontWeight = FontWeight.Bold)
                Text("⭐ $score", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            }

            // Current word
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Alignment.Center) {
                Text(
                    text = if (currentWord.isNotEmpty()) "$currentWord ✓" else "Tippe Buchstaben...",
                    fontSize = 28.sp,
                    color = if (currentWord.isNotEmpty()) HikariPrimary else HikariTextMuted,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Letter grid
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(4) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { col ->
                            val idx = row * 3 + col
                            val isSelected = selectedIndices.contains(idx)
                            LetterButton(
                                letter = letters[idx],
                                isSelected = isSelected,
                                onClick = { onLetterTap(idx) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Submit button
            Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                Button(
                    onClick = ::submitWord,
                    enabled = currentWord.length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentWord.length >= 2) HikariPrimary else HikariTextMuted,
                    ),
                ) {
                    Text("Wort einreichen", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Found words
            Text("Gefundene Wörter", fontSize = 14.sp, color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(foundWords.size) { i ->
                    val word = foundWords.toList()[i]
                    Text("• $word", fontSize = 14.sp, color = HikariText)
                }
            }
        }
    }
}

@Composable
private fun LetterButton(letter: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) HikariPrimary else HikariCardBg)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, fontSize = 24.sp, color = if (isSelected) Color.Black else HikariText,
            fontWeight = FontWeight.Bold)
    }
}
