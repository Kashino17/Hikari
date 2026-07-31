package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlin.random.Random

@Composable
fun Game2048(onBack: () -> Unit) {
    var board by remember { mutableStateOf(startBoard()) }
    var score by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    var won by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    fun spawn(b: IntArray): IntArray {
        val empties = b.indices.filter { b[it] == 0 }
        if (empties.isEmpty()) return b
        val idx = empties.random()
        b[idx] = if (Random.nextFloat() < 0.9f) 2 else 4
        return b
    }

    fun moved(before: IntArray, after: IntArray) = !(before contentEquals after)

    fun apply(dir: Int) {
        if (over) return
        val before = board.copyOf()
        val (nb, gained) = slide(board, dir)
        if (moved(before, nb)) {
            board = spawn(nb)
            score += gained
            if (score > best) best = score
            if (!won && nb.any { it >= 2048 }) won = true
            if (!movesLeft(board)) over = true
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("2048", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Box {}
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.spacedBy(10.dp)) {
            ScoreBox("Punkte", score.toString(), Modifier.weight(1f))
            ScoreBox("Best", best.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (over) "Kein Zug mehr!" else if (won) "2048 erreicht! Weiter geht's ✨" else "Wische, um zu bewegen",
                fontSize = 13.sp, color = if (over) Color(0xFFFF5252) else HikariTextMuted,
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDragEnd = {
                            val t = 45f
                            if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY)) {
                                when { dragX > t -> apply(1); dragX < -t -> apply(3) }
                            } else {
                                when { dragY > t -> apply(2); dragY < -t -> apply(0) }
                            }
                        },
                        onDrag = { _, d -> dragX += d.x; dragY += d.y },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column {
                for (r in 0 until 4) {
                    Row {
                        for (c in 0 until 4) {
                            val v = board[r * 4 + c]
                            Box(
                                Modifier.padding(4.dp).size(74.dp).clip(RoundedCornerShape(10.dp))
                                    .background(tileColor(v)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (v != 0) Text(
                                    v.toString(), color = if (v <= 4) HikariText else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (v < 100) 30.sp else if (v < 1000) 24.sp else 19.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.Center) {
            Button(onClick = { board = spawn(startBoard()); score = 0; won = false; over = false },
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary)) {
                Text("Neues Spiel", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScoreBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = HikariCardBg, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = HikariTextMuted)
            Text(value, fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

private fun startBoard(): IntArray {
    var b = IntArray(16)
    b = b.copyOf().also { it[it.indices.random()] = if (Random.nextFloat() < 0.9f) 2 else 4 }
    val rest = b.indices.filter { it == 0 || b[it] == 0 }.filter { b[it] == 0 }
    if (rest.isNotEmpty()) b[rest.random()] = if (Random.nextFloat() < 0.9f) 2 else 4
    return b
}

// dir: 0=up, 1=right, 2=down, 3=left  → returns (newBoard, gainedScore)
private fun slide(b: IntArray, dir: Int): Pair<IntArray, Int> {
    val out = b.copyOf()
    var gained = 0
    // Für jede der 4 Linien (je nach Richtung Zeile oder Spalte) Werte holen
    for (line in 0 until 4) {
        val cells = IntArray(4) { i ->
            when (dir) {
                3 -> out[line * 4 + i]            // links: Zeile links→rechts
                1 -> out[line * 4 + (3 - i)]      // rechts: Zeile rechts→links
                0 -> out[i * 4 + line]            // hoch: Spalte oben→unten
                2 -> out[(3 - i) * 4 + line]      // runter: Spalte unten→oben
                else -> 0
            }
        }
        val (merged, g) = mergeLine(cells)
        gained += g
        for (i in 0 until 4) {
            val v = merged[i]
            when (dir) {
                3 -> out[line * 4 + i] = v
                1 -> out[line * 4 + (3 - i)] = v
                0 -> out[i * 4 + line] = v
                2 -> out[(3 - i) * 4 + line] = v
            }
        }
    }
    return out to gained
}

private fun mergeLine(cells: IntArray): Pair<IntArray, Int> {
    val noZero = cells.filter { it != 0 }.toMutableList()
    var gained = 0
    var i = 0
    while (i < noZero.size - 1) {
        if (noZero[i] == noZero[i + 1]) {
            noZero[i] *= 2; gained += noZero[i]; noZero.removeAt(i + 1)
        }
        i++
    }
    while (noZero.size < 4) noZero.add(0)
    return noZero.toIntArray() to gained
}

private fun movesLeft(b: IntArray): Boolean {
    if (b.any { it == 0 }) return true
    for (r in 0 until 4) for (c in 0 until 4) {
        val v = b[r * 4 + c]
        if (c < 3 && b[r * 4 + c + 1] == v) return true
        if (r < 3 && b[(r + 1) * 4 + c] == v) return true
    }
    return false
}

private fun tileColor(v: Int): Color = when (v) {
    0 -> HikariCardBg
    2 -> Color(0xFF3C3A4A)
    4 -> Color(0xFF4A4658)
    8 -> Color(0xFFB45309)
    16 -> Color(0xFFD97706)
    32 -> Color(0xFFEA580C)
    64 -> Color(0xFFEF4444)
    128 -> Color(0xFFF59E0B)
    256 -> Color(0xFFFBBF24)
    512 -> Color(0xFFEAB308)
    1024 -> Color(0xFFFACC15)
    else -> Color(0xFFFFD700) // 2048+
}
