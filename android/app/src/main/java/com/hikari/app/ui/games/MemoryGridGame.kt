package com.hikari.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import kotlin.random.Random
import kotlin.math.sqrt

/**
 * Game 2: Memory Grid — finde die passenden Paare.
 * Tap cards to flip. Classic memory matching with Hikari neon aesthetic.
 */
@Composable
fun MemoryGridGame(onBack: () -> Unit) {
    val colors = listOf(
        Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFF60A5FA),
        Color(0xFFF472B6), Color(0xFFA78BFA), Color(0xFFFF8A65),
        Color(0xFF2DD4BF), Color(0xFFFB923C),
    )
    val pairCount = 6 // 12 cards (4x3)

    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var flippedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var matchedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var moves by remember { mutableStateOf(0) }
    var gameWon by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }

    fun initGame() {
        val deck = (0 until pairCount).flatMap { i -> listOf(i, i) }
        cards = deck.shuffled().map { colorIdx ->
            Card(colorIdx = colorIdx, matched = false, flipped = false)
        }
        flippedIndices = emptySet()
        matchedIndices = emptySet()
        moves = 0
        gameWon = false
        locked = false
    }

    LaunchedEffect(Unit) { initGame() }

    LaunchedEffect(flippedIndices, matchedIndices, cards) {
        if (flippedIndices.size == 2 && !locked) {
            locked = true
            moves++
            val (a, b) = flippedIndices.toList()
            if (cards[a].colorIdx == cards[b].colorIdx) {
                matchedIndices = matchedIndices + a + b
                if (matchedIndices.size == cards.size) {
                    gameWon = true
                }
            }
            delay(600)
            flippedIndices = emptySet()
            locked = false
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        // HUD
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            Text("Züge: $moves", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
            Text("Paare: ${matchedIndices.size / 2}/$pairCount", fontSize = 16.sp, color = HikariPrimary)
        }

        // Grid
        if (gameWon) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(80.dp))
                Text("🎉 Gewonnen!", fontSize = 28.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$moves Züge", fontSize = 20.sp, color = HikariText)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { initGame() },
                    modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) {
                    Text("Neu", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth()) {
                    Text("Zurück", fontSize = 16.sp)
                }
            }
        } else {
            // 4x3 grid
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) { row ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        repeat(4) { col ->
                            val idx = row * 4 + col
                            val card = cards.getOrNull(idx) ?: return@repeat
                            val isFlipped = flippedIndices.contains(idx) || matchedIndices.contains(idx)
                            MemoryCard(
                                card = card,
                                isFlipped = isFlipped,
                                onClick = {
                                    if (!locked && !isFlipped && idx < cards.size) {
                                        flippedIndices = flippedIndices + idx
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

data class Card(val colorIdx: Int, val matched: Boolean, val flipped: Boolean)

@Composable
private fun MemoryCard(card: Card, isFlipped: Boolean, onClick: () -> Unit) {
    val color = if (isFlipped) colors[card.colorIdx] else Color(0xFF1A1A1A)
    val borderColor = if (card.matched) Color(0xFF4ADE80) else Color.Transparent

    Box(
        modifier = Modifier
            .aspectRatio(1.4f)
            .background(color, RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        if (isFlipped) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.width * 0.25f
                drawCircle(colors[card.colorIdx], radius = r * 1.5f)
                drawCircle(Color.White.copy(alpha = 0.3f), radius = r * 0.6f,
                    center = Offset(cx - r * 0.3f, cy - r * 0.3f))
            }
        } else {
            Text("✦", fontSize = 24.sp, color = Color(0xFF333333))
        }
    }
}

private val colors = listOf(
    Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFF60A5FA),
    Color(0xFFF472B6), Color(0xFFA78BFA), Color(0xFFFF8A65),
    Color(0xFF2DD4BF), Color(0xFFFB923C),
)
