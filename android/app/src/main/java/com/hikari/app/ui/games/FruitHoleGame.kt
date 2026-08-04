package com.hikari.app.ui.games

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val HoleFruits = listOf("🍎", "🍌", "🍇", "🍉", "🍓", "🍊")

private class HoleItem(
    var x: Float,
    var y: Float,
    var vy: Float,
    var rot: Float,
    var rotSpeed: Float,
    val emoji: String,
    val isBomb: Boolean,
) {
    var swallowing = false
    var swallow = 0f
    var sx = 0f
    var sy = 0f
}

private class HolePop(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float,
    val bad: Boolean,
    val big: Boolean = false,
)

private class HoleWorld {
    var w = 0f
    var h = 0f
    var initialized = false
    val items = ArrayList<HoleItem>()
    val pops = ArrayList<HolePop>()
    var holeX = 0f
    var holeTargetX = 0f
    var holeR = 0f
    var spawnTimer = 0.8f
    var eaten = 0
    var flash = 0f
    var shake = 0f
}

@Composable
fun FruitHoleGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }

    var score by remember { mutableStateOf(0) }
    var highscore by remember { mutableStateOf(prefs.getInt("fruithole_highscore", 0)) }
    var lives by remember { mutableStateOf(3) }
    var level by remember { mutableStateOf(1) }
    var combo by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var restartKey by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0L) }

    val world = remember { HoleWorld() }
    val emojiPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true } }
    val popPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true } }

    val multiplier = if (combo >= 8) 3 else if (combo >= 4) 2 else 1

    fun finishGame() {
        if (score > highscore) {
            highscore = score
            newRecord = true
            prefs.edit().putInt("fruithole_highscore", score).apply()
        }
        gameOver = true
    }

    fun restart() {
        world.items.clear()
        world.pops.clear()
        world.eaten = 0
        world.spawnTimer = 0.8f
        world.flash = 0f
        world.shake = 0f
        if (world.initialized) {
            world.holeR = world.w * 0.13f
            world.holeX = world.w / 2f
            world.holeTargetX = world.w / 2f
        }
        score = 0
        lives = 3
        level = 1
        combo = 0
        newRecord = false
        gameOver = false
        restartKey++
    }

    // Game-Loop
    LaunchedEffect(restartKey, gameOver) {
        if (gameOver) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.032f)
                last = now
                if (dt > 0f && world.initialized && !gameOver) {
                    val w = world.w
                    val h = world.h
                    val floorY = h - w * 0.10f

                    // Loch smooth zur Zielposition
                    world.holeTargetX = world.holeTargetX.coerceIn(world.holeR, w - world.holeR)
                    world.holeX += (world.holeTargetX - world.holeX) * min(1f, dt * 14f)

                    // Effekte abklingen lassen
                    world.flash = max(0f, world.flash - dt * 2.4f)
                    world.shake = max(0f, world.shake - dt * 3.2f)

                    // Spawnen
                    world.spawnTimer -= dt
                    if (world.spawnTimer <= 0f) {
                        val interval = max(0.34f, 0.95f - (level - 1) * 0.07f)
                        world.spawnTimer = interval * (0.75f + Random.nextFloat() * 0.5f)
                        val bombChance = min(0.16f + (level - 1) * 0.02f, 0.30f)
                        val isBomb = Random.nextFloat() < bombChance
                        val margin = w * 0.08f
                        world.items.add(
                            HoleItem(
                                x = margin + Random.nextFloat() * (w - 2f * margin),
                                y = -w * 0.10f,
                                vy = h * (0.30f + Random.nextFloat() * 0.14f) * (1f + (level - 1) * 0.07f),
                                rot = Random.nextFloat() * 360f,
                                rotSpeed = (Random.nextFloat() * 2f - 1f) * 120f,
                                emoji = if (isBomb) "💣" else HoleFruits.random(),
                                isBomb = isBomb,
                            )
                        )
                    }

                    // Entitäten updaten
                    val catchBand = world.holeR * 0.5f
                    val iter = world.items.iterator()
                    while (iter.hasNext()) {
                        val item = iter.next()
                        if (!item.swallowing) {
                            val prevY = item.y
                            item.y += item.vy * dt
                            item.rot += item.rotSpeed * dt
                            // Segment- statt Punkt-Test: verhindert, dass schnelle
                            // Früchte auf hohem Level durch die Fang-Zone tunneln
                            val crossedZone = prevY <= floorY + catchBand && item.y >= floorY - catchBand
                            if (crossedZone && abs(item.x - world.holeX) < world.holeR * 0.9f) {
                                item.swallowing = true
                                item.sx = item.x
                                item.sy = item.y
                            } else if (item.y > h + w * 0.10f) {
                                iter.remove()
                                if (!item.isBomb) {
                                    if (combo >= 4) {
                                        world.pops.add(
                                            HolePop(item.x, h - w * 0.16f, "Kombo verloren", 1.0f, bad = true)
                                        )
                                    }
                                    combo = 0 // verpasste Frucht: Kombo weg
                                }
                            }
                        } else {
                            item.swallow += dt * 4.2f
                            val t = min(1f, item.swallow)
                            item.x = item.sx + (world.holeX - item.sx) * t
                            item.y = item.sy + (floorY - item.sy) * t
                            if (item.swallow >= 1f) {
                                iter.remove()
                                if (item.isBomb) {
                                    lives -= 1
                                    combo = 0
                                    world.flash = 1f
                                    world.shake = 1f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    world.pops.add(HolePop(world.holeX, floorY - world.holeR * 1.5f, "-1 ♥", 1.1f, bad = true))
                                    if (lives <= 0) finishGame()
                                } else {
                                    combo += 1
                                    val mult = if (combo >= 8) 3 else if (combo >= 4) 2 else 1
                                    val pts = 10 * mult
                                    score += pts
                                    world.eaten += 1
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    world.pops.add(HolePop(item.sx, floorY - world.holeR * 1.5f, "+$pts", 0.9f, bad = false))
                                    if (world.eaten % 12 == 0) {
                                        level += 1
                                        world.pops.add(HolePop(w * 0.5f, h * 0.32f, "Level $level", 1.5f, bad = false, big = true))
                                    }
                                    if (world.eaten % 8 == 0) {
                                        world.holeR = min(world.holeR + w * 0.010f, w * 0.19f)
                                    }
                                }
                            }
                        }
                    }

                    // Punkte-Popups
                    val pit = world.pops.iterator()
                    while (pit.hasNext()) {
                        val p = pit.next()
                        p.life -= dt
                        p.y -= dt * h * 0.05f
                        if (p.life <= 0f) pit.remove()
                    }
                }
                tick++
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Hungry Hole", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("$score", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Text("Rekord: $highscore", fontSize = 11.sp, color = HikariTextMuted)
            }
        }

        // Leben / Kombo / Level
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Row {
                repeat(3) { i ->
                    Text(
                        "♥",
                        fontSize = 16.sp,
                        color = if (i < lives) HikariDanger else HikariTextFaint,
                        modifier = Modifier.padding(end = 3.dp),
                    )
                }
            }
            Text(
                if (multiplier > 1) "Kombo x$multiplier" else "",
                fontSize = 14.sp,
                color = HikariPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text("Level $level", fontSize = 13.sp, color = HikariTextMuted)
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(restartKey) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            world.holeTargetX += dragAmount * 1.15f
                        }
                    }
            ) {
                if (tick < 0) return@Canvas // liest tick → Canvas wird pro Frame neu gezeichnet
                val w = size.width
                val h = size.height
                if (!world.initialized || world.w != w || world.h != h) {
                    world.w = w
                    world.h = h
                    world.holeR = w * 0.13f
                    world.holeX = w / 2f
                    world.holeTargetX = w / 2f
                    world.initialized = true
                }
                val floorY = h - w * 0.10f
                val shakeX = if (world.shake > 0f) (Random.nextFloat() * 2f - 1f) * world.shake * 14f else 0f
                val shakeY = if (world.shake > 0f) (Random.nextFloat() * 2f - 1f) * world.shake * 10f else 0f

                translate(shakeX, shakeY) {
                    // Boden-Schimmer
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF141003)),
                            startY = floorY - h * 0.12f,
                            endY = h,
                        ),
                        topLeft = Offset(0f, floorY - h * 0.12f),
                        size = Size(w, h - floorY + h * 0.12f),
                    )

                    // Schwarzes Loch (Ellipse via Y-Scale)
                    val holeCenter = Offset(world.holeX, floorY)
                    scale(1f, 0.40f, pivot = holeCenter) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(HikariAmber.copy(alpha = 0.50f), Color.Transparent),
                                center = holeCenter,
                                radius = world.holeR * 1.7f,
                            ),
                            radius = world.holeR * 1.7f,
                            center = holeCenter,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF2A1E06)),
                                center = holeCenter,
                                radius = world.holeR,
                            ),
                            radius = world.holeR,
                            center = holeCenter,
                        )
                        drawCircle(
                            color = HikariAmber.copy(alpha = 0.85f),
                            radius = world.holeR,
                            center = holeCenter,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }

                    // Fallende Objekte
                    for (item in world.items) {
                        val s = if (item.swallowing) 1f - min(1f, item.swallow) else 1f
                        if (s <= 0.02f) continue
                        emojiPaint.textSize = w * 0.10f * s
                        val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                        rotate(item.rot, pivot = Offset(item.x, item.y)) {
                            drawIntoCanvas { c ->
                                c.nativeCanvas.drawText(item.emoji, item.x, item.y - yOff, emojiPaint)
                            }
                        }
                    }

                    // Punkte-Popups
                    for (p in world.pops) {
                        popPaint.textSize = if (p.big) w * 0.075f else w * 0.045f
                        val col = if (p.bad) HikariDanger else HikariAmber
                        popPaint.color = col.copy(alpha = p.life.coerceIn(0f, 1f)).toArgb()
                        drawIntoCanvas { c ->
                            c.nativeCanvas.drawText(p.text, p.x, p.y, popPaint)
                        }
                    }
                }

                // Roter Flash bei Bombe
                if (world.flash > 0f) {
                    drawRect(HikariDanger.copy(alpha = world.flash * 0.30f))
                }
            }

            // Game-Over-Overlay
            if (gameOver) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xCC000000)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(HikariCardBg)
                            .padding(horizontal = 32.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Game Over", fontSize = 26.sp, color = HikariDanger, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("Punkte: $score", fontSize = 18.sp, color = HikariText)
                        Spacer(Modifier.height(6.dp))
                        if (newRecord) {
                            Text("Neuer Rekord!", fontSize = 16.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Rekord: $highscore", fontSize = 14.sp, color = HikariTextMuted)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { restart() },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                        ) {
                            Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
