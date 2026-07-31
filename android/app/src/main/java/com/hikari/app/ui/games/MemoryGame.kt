package com.hikari.app.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import kotlin.random.Random

@Composable
fun MemoryGame(onBack: () -> Unit) {
    val symbols = remember { listOf("🌟", "🌙", "🔥", "🌊", "🎮", "🎵", "⚡", "🌸") }
    var cards by remember { mutableStateOf(shuffleDeck(symbols)) }
    var flipped by remember { mutableStateOf<List<Int>>(emptyList()) } // Indizes aufgedeckt
    var matched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var moves by remember { mutableStateOf(0) }
    var lock by remember { mutableStateOf(false) }

    val pairs = matched.size / 2
    val won = pairs == symbols.size

    fun reset() {
        cards = shuffleDeck(symbols); flipped = emptyList(); matched = emptySet(); moves = 0; lock = false
    }

    fun tap(idx: Int) {
        if (lock || won) return
        if (idx in matched || idx in flipped) return
        val nf = flipped + idx
        flipped = nf
        if (nf.size == 2) {
            moves++
            lock = true
        }
    }

    // Paar-Prüfung, sobald zwei Karten aufgedeckt sind
    LaunchedEffect(flipped) {
        if (flipped.size == 2) {
            delay(650)
            val (a, b) = flipped
            if (cards[a] == cards[b]) matched = matched + setOf(a, b)
            flipped = emptyList()
            lock = false
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Memory", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Text("$pairs/${symbols.size}", fontSize = 13.sp, color = HikariTextMuted)
        }

        Spacer(Modifier.height(6.dp))
        Text("Züge: $moves", fontSize = 14.sp, color = HikariText)

        if (won) {
            Spacer(Modifier.height(12.dp))
            Text("Geschafft! 🎉", fontSize = 26.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::reset, colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))

        Column {
            for (r in 0 until 4) {
                Row {
                    for (c in 0 until 4) {
                        val i = r * 4 + c
                        val faceUp = i in flipped || i in matched
                        Box(
                            Modifier.padding(5.dp).size(74.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (faceUp) HikariPrimary.copy(alpha = if (i in matched) 0.25f else 0.85f) else HikariCardBg)
                                .clickable { tap(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = faceUp,
                                transitionSpec = { (scaleIn() togetherWith scaleOut()).let { fadeIn() togetherWith fadeOut() } },
                                label = "flip",
                            ) { up ->
                                if (up) Text(cards[i], fontSize = 34.sp)
                                else Text("?", fontSize = 26.sp, color = HikariTextMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::reset, colors = ButtonDefaults.buttonColors(containerColor = HikariCardBg)) {
            Text("Neu mischen", color = HikariText)
        }
    }
}

private fun shuffleDeck(symbols: List<String>): List<String> {
    val deck = (symbols + symbols).toMutableList()
    deck.shuffle(Random)
    return deck
}
