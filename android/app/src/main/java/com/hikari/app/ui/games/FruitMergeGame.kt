package com.hikari.app.ui.games

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Fruchtkette Stufe 0..9
private val MergeEmoji = listOf("🍒", "🍓", "🍇", "🍊", "🍎", "🍐", "🍑", "🍍", "🍈", "🍉")
private val MergeColors = listOf(
    Color(0xFFB71C1C), // Kirsche
    Color(0xFFEF5350), // Erdbeere
    Color(0xFF9B59B6), // Traube
    Color(0xFFF39C12), // Orange
    Color(0xFFE53935), // Apfel
    Color(0xFF9CCC65), // Birne
    Color(0xFFFFAB91), // Pfirsich
    Color(0xFFFDD835), // Ananas
    Color(0xFFAED581), // Melone
    Color(0xFF43A047), // Wassermelone
)

private class MergeFruit(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var level: Int,
    var r: Float,
    var pop: Float = 1f,      // 0→1 Pop-in beim Merge
    var overTime: Float = 0f, // Zeit über der Limit-Linie
)

private class MergeSpark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
)

private class MergeTextPop(var x: Float, var y: Float, val text: String, var life: Float)

private class MergeWorld {
    var w = 0f
    var h = 0f
    var initialized = false
    var scale = 1f
    val radii = FloatArray(10)
    val fruits = ArrayList<MergeFruit>()
    val sparks = ArrayList<MergeSpark>()
    val pops = ArrayList<MergeTextPop>()
    var left = 0f
    var right = 0f
    var bottom = 0f
    var top = 0f
    var limitY = 0f
    var hangY = 0f
    var aimX = 0f
    var currentLevel = -1 // -1 = Cooldown, keine Frucht in der Hand
    var cooldown = 0f
    var warn = 0f
    var time = 0f
}

@Composable
fun FruitMergeGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }

    var score by remember { mutableStateOf(0) }
    var highscore by remember { mutableStateOf(prefs.getInt("fruitmerge_highscore", 0)) }
    var nextLevel by remember { mutableStateOf(Random.nextInt(5)) }
    var gameOver by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var restartKey by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0L) }

    val world = remember { MergeWorld() }
    val emojiPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true } }
    val popPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true } }

    fun finishGame() {
        if (score > highscore) {
            highscore = score
            newRecord = true
            prefs.edit().putInt("fruitmerge_highscore", score).apply()
        }
        gameOver = true
    }

    fun restart() {
        world.fruits.clear()
        world.sparks.clear()
        world.pops.clear()
        world.currentLevel = Random.nextInt(5)
        world.cooldown = 0f
        world.warn = 0f
        if (world.initialized) world.aimX = world.w / 2f
        nextLevel = Random.nextInt(5)
        score = 0
        newRecord = false
        gameOver = false
        restartKey++
    }

    fun dropFruit() {
        if (gameOver || !world.initialized || world.currentLevel < 0) return
        val lvl = world.currentLevel
        val r = world.radii[lvl]
        world.fruits.add(
            MergeFruit(
                x = world.aimX.coerceIn(world.left + r + 2f, world.right - r - 2f),
                y = world.hangY,
                vx = 0f,
                vy = 180f * world.scale,
                level = lvl,
                r = r,
            )
        )
        world.currentLevel = -1
        world.cooldown = 0.6f
    }

    // Game-Loop mit fixem Substep
    LaunchedEffect(restartKey, gameOver) {
        if (gameOver) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.032f)
                last = now
                if (dt > 0f && world.initialized && !gameOver) {
                    val sc = world.scale
                    val g = 2000f * sc
                    world.time += dt

                    // Nachschub nach Cooldown
                    if (world.currentLevel < 0) {
                        world.cooldown -= dt
                        if (world.cooldown <= 0f) {
                            world.currentLevel = nextLevel
                            nextLevel = Random.nextInt(5)
                        }
                    }

                    // Physik: 4 Substeps pro Frame
                    val fruits = world.fruits
                    val sub = 4
                    val hstep = dt / sub
                    repeat(sub) {
                        // Integration
                        for (f in fruits) {
                            f.vy += g * hstep
                            f.x += f.vx * hstep
                            f.y += f.vy * hstep
                        }
                        // Kollisionen (2 Iterationen)
                        repeat(2) {
                            for (i in 0 until fruits.size) {
                                val a = fruits[i]
                                for (j in i + 1 until fruits.size) {
                                    val b = fruits[j]
                                    val dx = b.x - a.x
                                    val dy = b.y - a.y
                                    val rs = a.r + b.r
                                    val d2 = dx * dx + dy * dy
                                    if (d2 < rs * rs && d2 > 0.0001f) {
                                        val d = sqrt(d2)
                                        val nx = dx / d
                                        val ny = dy / d
                                        val overlap = max(0f, rs - d - 0.5f * sc)
                                        val ma = a.r * a.r
                                        val mb = b.r * b.r
                                        val wa = mb / (ma + mb)
                                        val wb = ma / (ma + mb)
                                        val corr = overlap * 0.8f
                                        a.x -= nx * corr * wa
                                        a.y -= ny * corr * wa
                                        b.x += nx * corr * wb
                                        b.y += ny * corr * wb
                                        // Impuls entlang der Normalen
                                        val rvx = b.vx - a.vx
                                        val rvy = b.vy - a.vy
                                        val vn = rvx * nx + rvy * ny
                                        if (vn < 0f) {
                                            val e = if (-vn < 120f * sc) 0f else 0.15f
                                            val jn = -(1f + e) * vn / (1f / ma + 1f / mb)
                                            a.vx -= nx * jn / ma
                                            a.vy -= ny * jn / ma
                                            b.vx += nx * jn / mb
                                            b.vy += ny * jn / mb
                                            // leichte Reibung tangential
                                            val tx = -ny
                                            val ty = nx
                                            val vt = rvx * tx + rvy * ty
                                            val jt = -vt * 0.2f / (1f / ma + 1f / mb)
                                            a.vx -= tx * jt / ma
                                            a.vy -= ty * jt / ma
                                            b.vx += tx * jt / mb
                                            b.vy += ty * jt / mb
                                        }
                                    }
                                }
                            }
                            // Wände + Boden
                            for (f in fruits) {
                                val minX = world.left + f.r
                                val maxX = world.right - f.r
                                if (f.x < minX) { f.x = minX; if (f.vx < 0f) f.vx = -f.vx * 0.15f }
                                if (f.x > maxX) { f.x = maxX; if (f.vx > 0f) f.vx = -f.vx * 0.15f }
                                val maxY = world.bottom - f.r
                                if (f.y > maxY) {
                                    f.y = maxY
                                    if (f.vy > 0f) f.vy = if (f.vy < 260f * sc) 0f else -f.vy * 0.15f
                                    f.vx *= 0.94f
                                }
                            }
                        }
                    }

                    // Ruhige Stapel beruhigen (kein Zittern)
                    for (f in fruits) {
                        val sp = abs(f.vx) + abs(f.vy)
                        if (sp < 30f * sc) {
                            f.vx *= 0.75f
                            f.vy *= 0.85f
                        }
                        f.pop = min(1f, f.pop + dt * 4.5f)
                    }

                    // Merges (Kettenreaktionen über Folge-Frames)
                    var i = 0
                    outer@ while (i < fruits.size) {
                        val a = fruits[i]
                        var j = i + 1
                        while (j < fruits.size) {
                            val b = fruits[j]
                            if (a.level == b.level) {
                                val dx = b.x - a.x
                                val dy = b.y - a.y
                                val rs = a.r + b.r + 2f * sc
                                if (dx * dx + dy * dy <= rs * rs) {
                                    val mx = (a.x + b.x) / 2f
                                    val my = (a.y + b.y) / 2f
                                    fruits.removeAt(j)
                                    fruits.removeAt(i)
                                    if (a.level >= 9) {
                                        // 🍉 + 🍉 → Feuerwerk + Bonus
                                        score += 500
                                        world.pops.add(MergeTextPop(mx, my - 40f * sc, "+500 Bonus!", 1.6f))
                                        val fwColors = listOf(HikariAmber, Color(0xFFFF7043), Color(0xFF66BB6A), Color.White, Color(0xFFEF5350))
                                        repeat(42) {
                                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                                            val spd = (300f + Random.nextFloat() * 650f) * sc
                                            world.sparks.add(
                                                MergeSpark(
                                                    mx, my,
                                                    cos(ang) * spd, sin(ang) * spd - 150f * sc,
                                                    0.6f + Random.nextFloat() * 0.5f, 1.1f,
                                                    fwColors.random(),
                                                    (4f + Random.nextFloat() * 6f) * sc,
                                                )
                                            )
                                        }
                                    } else {
                                        val nl = a.level + 1
                                        val pts = (nl + 1) * 10
                                        score += pts
                                        fruits.add(
                                            MergeFruit(
                                                mx, my,
                                                (a.vx + b.vx) * 0.25f,
                                                min((a.vy + b.vy) * 0.25f, 0f) - 60f * sc,
                                                nl, world.radii[nl],
                                                pop = 0f,
                                            )
                                        )
                                        world.pops.add(MergeTextPop(mx, my - world.radii[nl] - 18f * sc, "+$pts", 0.8f))
                                        repeat(7) {
                                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                                            val spd = (140f + Random.nextFloat() * 260f) * sc
                                            world.sparks.add(
                                                MergeSpark(
                                                    mx, my,
                                                    cos(ang) * spd, sin(ang) * spd,
                                                    0.3f + Random.nextFloat() * 0.25f, 0.55f,
                                                    MergeColors[nl],
                                                    (3f + Random.nextFloat() * 4f) * sc,
                                                )
                                            )
                                        }
                                    }
                                    continue@outer
                                }
                            }
                            j++
                        }
                        i++
                    }

                    // Game Over: ruhende Frucht ragt >1.2s über die Limit-Linie
                    var anyOver = false
                    for (f in fruits) {
                        val slow = abs(f.vx) + abs(f.vy) < 60f * world.scale
                        if (slow && f.y - f.r < world.limitY) {
                            f.overTime += dt
                            if (f.overTime > 0.1f) anyOver = true
                            if (f.overTime > 1.2f) {
                                finishGame()
                                break
                            }
                        } else {
                            f.overTime = 0f
                        }
                    }
                    world.warn = if (anyOver) min(1f, world.warn + dt * 4f) else max(0f, world.warn - dt * 4f)

                    // Partikel
                    val sit = world.sparks.iterator()
                    while (sit.hasNext()) {
                        val s = sit.next()
                        s.vy += g * 0.35f * dt
                        s.x += s.vx * dt
                        s.y += s.vy * dt
                        s.life -= dt
                        if (s.life <= 0f) sit.remove()
                    }
                    val pit = world.pops.iterator()
                    while (pit.hasNext()) {
                        val p = pit.next()
                        p.life -= dt
                        p.y -= dt * 60f * world.scale
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
            Text("Fruit Merge", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("$score", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Text("Rekord: $highscore", fontSize = 11.sp, color = HikariTextMuted)
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(restartKey, gameOver) {
                        if (gameOver) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            world.aimX = down.position.x
                            val completed = drag(down.id) { change ->
                                world.aimX = change.position.x
                                change.consume()
                            }
                            if (completed) dropFruit()
                        }
                    }
            ) {
                if (tick < 0) return@Canvas // liest tick → Redraw pro Frame
                val w = size.width
                val h = size.height
                if (!world.initialized || world.w != w || world.h != h) {
                    world.w = w
                    world.h = h
                    world.scale = w / 1080f
                    var r = 26f * world.scale
                    for (k in 0 until 10) {
                        world.radii[k] = r
                        r *= 1.35f
                    }
                    val margin = w * 0.035f
                    world.left = margin
                    world.right = w - margin
                    world.bottom = h - margin
                    world.top = h * 0.15f
                    world.limitY = world.top + h * 0.045f
                    world.hangY = world.top * 0.45f
                    if (world.aimX == 0f) world.aimX = w / 2f
                    if (world.currentLevel < 0 && world.cooldown <= 0f) world.currentLevel = Random.nextInt(5)
                    world.initialized = true
                }
                val sc = world.scale

                // Behälter-Innenraum
                drawRect(
                    Color(0xFF111111),
                    topLeft = Offset(world.left, world.top),
                    size = Size(world.right - world.left, world.bottom - world.top),
                )

                // Limit-Linie (gestrichelt, pulsiert bei Gefahr)
                val warnPulse = if (world.warn > 0f) (0.5f + 0.5f * sin(world.time * 10f)) * world.warn else 0f
                val lineColor = lerp(HikariTextFaint, HikariDanger, min(1f, world.warn + warnPulse * 0.3f))
                drawLine(
                    lineColor,
                    Offset(world.left + 8f * sc, world.limitY),
                    Offset(world.right - 8f * sc, world.limitY),
                    strokeWidth = max(2f, 3f * sc),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f * sc, 14f * sc)),
                )

                // Ziel-Hilfslinie + aktuelle Frucht in der Drop-Zone
                if (!gameOver && world.currentLevel >= 0) {
                    val lvl = world.currentLevel
                    val cr = world.radii[lvl]
                    val cx = world.aimX.coerceIn(world.left + cr + 2f, world.right - cr - 2f)
                    drawLine(
                        Color.White.copy(alpha = 0.07f),
                        Offset(cx, world.hangY + cr),
                        Offset(cx, world.bottom - 4f),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * sc, 12f * sc)),
                    )
                    drawFruitBall(cx, world.hangY, cr, lvl, emojiPaint, sc)
                }

                // Früchte
                for (f in world.fruits) {
                    val popT = f.pop
                    val visR = f.r * (0.55f + 0.45f * popT) * (1f + 0.18f * sin(popT * PI.toFloat()))
                    drawFruitBall(f.x, f.y, visR, f.level, emojiPaint, sc)
                }

                // Behälter-Wände
                val corner = 28f * sc
                val wallPath = Path().apply {
                    moveTo(world.left, world.top)
                    lineTo(world.left, world.bottom - corner)
                    quadraticBezierTo(world.left, world.bottom, world.left + corner, world.bottom)
                    lineTo(world.right - corner, world.bottom)
                    quadraticBezierTo(world.right, world.bottom, world.right, world.bottom - corner)
                    lineTo(world.right, world.top)
                }
                drawPath(
                    wallPath,
                    Color(0xFF3A3226),
                    style = Stroke(width = max(6f, 8f * sc), cap = StrokeCap.Round),
                )

                // Funken / Feuerwerk
                for (s in world.sparks) {
                    drawCircle(
                        s.color.copy(alpha = (s.life / s.maxLife).coerceIn(0f, 1f)),
                        radius = s.size,
                        center = Offset(s.x, s.y),
                    )
                }

                // Punkte-Popups
                for (p in world.pops) {
                    popPaint.textSize = 42f * sc
                    popPaint.color = HikariAmber.copy(alpha = p.life.coerceIn(0f, 1f)).toArgb()
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawText(p.text, p.x, p.y, popPaint)
                    }
                }
            }

            // "Nächste:"-Preview
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariCardBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Nächste:", fontSize = 11.sp, color = HikariTextMuted)
                Spacer(Modifier.width(5.dp))
                Text(MergeEmoji[nextLevel], fontSize = 18.sp)
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

// Frucht = satter Farbkreis + Emoji zentriert darüber
private fun DrawScope.drawFruitBall(x: Float, y: Float, r: Float, level: Int, paint: Paint, sc: Float) {
    val col = MergeColors[level]
    drawCircle(col.copy(alpha = 0.92f), radius = r, center = Offset(x, y))
    drawCircle(
        Color(col.red * 0.55f, col.green * 0.55f, col.blue * 0.55f),
        radius = r,
        center = Offset(x, y),
        style = Stroke(width = max(2f, 3f * sc)),
    )
    drawCircle(
        Color.White.copy(alpha = 0.10f),
        radius = r * 0.72f,
        center = Offset(x - r * 0.15f, y - r * 0.20f),
    )
    paint.textSize = r * 1.15f
    val yOff = (paint.ascent() + paint.descent()) / 2f
    drawIntoCanvas { c ->
        c.nativeCanvas.drawText(MergeEmoji[level], x, y - yOff, paint)
    }
}
