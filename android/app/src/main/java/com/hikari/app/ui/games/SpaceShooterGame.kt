package com.hikari.app.ui.games

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ————— Entities —————

private class SkyStar(var x: Float, var y: Float, val radius: Float, val speed: Float, val alpha: Float)
private class SkyBullet(var x: Float, var y: Float)
private class SkyPowerUp(var x: Float, var y: Float, val kind: Int) // 0 = Double-Shot, 1 = Extra-Leben

private class SkyEnemy(
    val type: Int, // 0 = klein, 1 = mittel, 2 = Brocken
    val baseX: Float,
    var x: Float,
    var y: Float,
    val speed: Float, // Anteil der Bildhöhe pro Sekunde
    val radius: Float,
    var hp: Int,
    val maxHp: Int,
    val points: Int,
    val sinAmp: Float,
    val sinFreq: Float,
    val phase: Float,
    var hitFlash: Float = 0f,
)

private class SkyParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Color, val radius: Float,
)

// ————— Spielzustand —————

private class SkyState(val den: Float) {
    var w = 0f
    var h = 0f
    var shipX = 0f
    val shipY get() = h - 76f * den

    val stars = ArrayList<SkyStar>()
    val bullets = ArrayList<SkyBullet>()
    val enemies = ArrayList<SkyEnemy>()
    val particles = ArrayList<SkyParticle>()
    val powerUps = ArrayList<SkyPowerUp>()

    var time = 0f
    var fireTimer = 0f
    var invuln = 0f
    var toSpawn = 0
    var spawnTimer = 0f
    var interWave = 0.6f

    var frame by mutableLongStateOf(0L)
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var wave by mutableIntStateOf(0)
    var doubleTimer by mutableFloatStateOf(0f)
    var waveBanner by mutableFloatStateOf(0f)
    var gameOver by mutableStateOf(false)

    fun enemyColor(type: Int): Color = when (type) {
        0 -> Color(0xFF60A5FA)
        1 -> Color(0xFFA78BFA)
        else -> Color(0xFFF87171)
    }

    fun resize(nw: Float, nh: Float) {
        val first = w == 0f
        w = nw
        h = nh
        if (first) {
            shipX = w / 2f
            repeat(48) { i ->
                val fast = i % 3 == 0
                stars.add(
                    SkyStar(
                        Random.nextFloat() * w,
                        Random.nextFloat() * h,
                        if (fast) 2.2f * den else 1.2f * den,
                        if (fast) 0.055f else 0.022f,
                        if (fast) 0.5f else 0.25f,
                    )
                )
            }
        }
    }

    fun reset() {
        bullets.clear()
        enemies.clear()
        particles.clear()
        powerUps.clear()
        score = 0
        lives = 3
        wave = 0
        doubleTimer = 0f
        waveBanner = 0f
        time = 0f
        fireTimer = 0f
        invuln = 1f
        toSpawn = 0
        spawnTimer = 0f
        interWave = 0.6f
        shipX = w / 2f
        gameOver = false
    }

    private fun speedMul() = 1f + (wave - 1) * 0.05f + (wave / 5) * 0.2f

    private fun spawnEnemy() {
        val roll = Random.nextFloat()
        val pBig = if (wave >= 4) min(0.22f, 0.04f + wave * 0.015f) else 0f
        val pMed = if (wave >= 2) min(0.4f, 0.08f + wave * 0.03f) else 0f
        val type = when {
            roll < pBig -> 2
            roll < pBig + pMed -> 1
            else -> 0
        }
        val radius = when (type) { 0 -> 12f; 1 -> 17f; else -> 24f } * den
        val speed = when (type) { 0 -> 0.16f; 1 -> 0.11f; else -> 0.07f } * speedMul()
        val hp = when (type) { 0 -> 1; 1 -> 2; else -> 4 }
        val pts = when (type) { 0 -> 10; 1 -> 25; else -> 60 }
        val margin = radius + 8f * den
        val baseX = margin + Random.nextFloat() * (w - margin * 2).coerceAtLeast(1f)
        val sine = type != 2 && Random.nextFloat() < 0.45f
        enemies.add(
            SkyEnemy(
                type = type, baseX = baseX, x = baseX, y = -radius * 2,
                speed = speed, radius = radius, hp = hp, maxHp = hp, points = pts,
                sinAmp = if (sine) (0.06f + Random.nextFloat() * 0.09f) * w else 0f,
                sinFreq = 1.5f + Random.nextFloat() * 1.5f,
                phase = Random.nextFloat() * 6.2832f,
            )
        )
    }

    private fun explode(x: Float, y: Float, color: Color, big: Boolean) {
        val n = if (big) 18 else 10
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val sp = (60f + Random.nextFloat() * 200f) * den * (if (big) 1.4f else 1f)
            particles.add(
                SkyParticle(
                    x, y, cos(a) * sp, sin(a) * sp,
                    0.55f, 0.55f,
                    if (Random.nextFloat() < 0.4f) HikariAmber else color,
                    (2f + Random.nextFloat() * 3f) * den,
                )
            )
        }
    }

    fun update(dt: Float) {
        frame++
        if (w <= 0f || gameOver) return
        time += dt

        // Sternen-Parallax
        for (s in stars) {
            s.y += s.speed * h * dt
            if (s.y > h + 4f) {
                s.y = -4f
                s.x = Random.nextFloat() * w
            }
        }

        // Wellen-Logik
        if (toSpawn == 0 && enemies.isEmpty()) {
            interWave -= dt
            if (interWave <= 0f) {
                wave += 1
                toSpawn = 5 + wave * 2 + (wave / 5) * 4
                spawnTimer = 0.4f
                waveBanner = 2f
                interWave = 1.1f
            }
        }
        if (toSpawn > 0) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnEnemy()
                toSpawn -= 1
                spawnTimer = (0.85f - wave * 0.035f).coerceAtLeast(0.28f)
            }
        }
        if (waveBanner > 0f) waveBanner = (waveBanner - dt).coerceAtLeast(0f)
        if (doubleTimer > 0f) doubleTimer = (doubleTimer - dt).coerceAtLeast(0f)
        if (invuln > 0f) invuln -= dt

        // Auto-Feuer
        fireTimer -= dt
        if (fireTimer <= 0f) {
            fireTimer = 0.28f
            val by = shipY - 26f * den
            if (doubleTimer > 0f) {
                bullets.add(SkyBullet(shipX - 10f * den, by))
                bullets.add(SkyBullet(shipX + 10f * den, by))
            } else {
                bullets.add(SkyBullet(shipX, by))
            }
        }

        // Triebwerks-Partikel
        if (frame % 3L == 0L) {
            particles.add(
                SkyParticle(
                    shipX + (Random.nextFloat() - 0.5f) * 8f * den,
                    shipY + 20f * den,
                    0f, 90f * den, 0.3f, 0.3f, HikariAmber, 2.5f * den,
                )
            )
        }

        // Projektile
        val bulletSpeed = h * 1.15f
        val bIt = bullets.iterator()
        while (bIt.hasNext()) {
            val b = bIt.next()
            b.y -= bulletSpeed * dt
            if (b.y < -20f) bIt.remove()
        }

        // Gegner
        val eIt = enemies.iterator()
        while (eIt.hasNext()) {
            val e = eIt.next()
            e.y += e.speed * h * dt
            if (e.sinAmp > 0f) {
                e.x = (e.baseX + sin(e.y / h * 6.2832f * e.sinFreq + e.phase) * e.sinAmp)
                    .coerceIn(e.radius, (w - e.radius).coerceAtLeast(e.radius))
            }
            if (e.hitFlash > 0f) e.hitFlash -= dt * 6f

            // Unterer Rand erreicht
            if (e.y - e.radius > h) {
                eIt.remove()
                lives -= 1
                if (lives <= 0) gameOver = true
                continue
            }

            // Kollision mit dem Schiff
            if (invuln <= 0f &&
                abs(e.x - shipX) < e.radius + 16f * den &&
                abs(e.y - shipY) < e.radius + 14f * den
            ) {
                explode(e.x, e.y, enemyColor(e.type), e.type == 2)
                eIt.remove()
                lives -= 1
                invuln = 1.5f
                if (lives <= 0) gameOver = true
                continue
            }

            // Projektil-Treffer
            var killed = false
            val hitIt = bullets.iterator()
            while (hitIt.hasNext()) {
                val b = hitIt.next()
                if (abs(b.x - e.x) < e.radius + 4f * den && abs(b.y - e.y) < e.radius + 8f * den) {
                    hitIt.remove()
                    e.hp -= 1
                    e.hitFlash = 1f
                    repeat(3) {
                        particles.add(
                            SkyParticle(
                                b.x, b.y,
                                (Random.nextFloat() - 0.5f) * 160f * den,
                                -Random.nextFloat() * 120f * den,
                                0.25f, 0.25f, Color.White, 1.8f * den,
                            )
                        )
                    }
                    if (e.hp <= 0) {
                        killed = true
                        break
                    }
                }
            }
            if (killed) {
                explode(e.x, e.y, enemyColor(e.type), e.type == 2)
                score += e.points
                if (Random.nextFloat() < 0.10f) {
                    powerUps.add(SkyPowerUp(e.x, e.y, if (Random.nextFloat() < 0.3f) 1 else 0))
                }
                eIt.remove()
            }
        }

        // Power-ups
        val pIt = powerUps.iterator()
        while (pIt.hasNext()) {
            val p = pIt.next()
            p.y += h * 0.085f * dt
            if (p.y > h + 20f) {
                pIt.remove()
                continue
            }
            if (abs(p.x - shipX) < 26f * den && abs(p.y - shipY) < 26f * den) {
                val pc = if (p.kind == 0) Color(0xFF22D3EE) else Color(0xFF4ADE80)
                if (p.kind == 0) doubleTimer = 12f else lives = min(5, lives + 1)
                repeat(8) {
                    val a = Random.nextFloat() * 6.2832f
                    particles.add(
                        SkyParticle(p.x, p.y, cos(a) * 140f * den, sin(a) * 140f * den, 0.4f, 0.4f, pc, 2f * den)
                    )
                }
                pIt.remove()
            }
        }

        // Partikel
        val prIt = particles.iterator()
        while (prIt.hasNext()) {
            val p = prIt.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) prIt.remove()
        }

        shipX = shipX.coerceIn(20f * den, (w - 20f * den).coerceAtLeast(20f * den))
    }
}

// ————— UI —————

@Composable
fun SpaceShooterGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    var highscore by remember { mutableIntStateOf(prefs.getInt("spaceshooter_highscore", 0)) }
    var newRecord by remember { mutableStateOf(false) }
    val den = LocalDensity.current.density
    val state = remember { SkyState(den) }
    val textMeasurer = rememberTextMeasurer()

    // Game-Loop — pausiert bei Game Over
    LaunchedEffect(state.gameOver) {
        if (state.gameOver) {
            if (state.score > highscore) {
                newRecord = true
                highscore = state.score
                prefs.edit().putInt("spaceshooter_highscore", state.score).apply()
            }
            return@LaunchedEffect
        }
        var last = 0L
        while (!state.gameOver) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.032f)
                    state.update(dt)
                }
                last = now
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
            Text("Sky Strike", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("${state.score}", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                Text("Best: $highscore", fontSize = 11.sp, color = HikariTextMuted)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            Arrangement.SpaceBetween,
        ) {
            Text("♥ ".repeat(state.lives.coerceAtLeast(0)).trim(), fontSize = 14.sp, color = HikariDanger)
            Row {
                if (state.doubleTimer > 0f) {
                    Text(
                        "2x Schuss ${state.doubleTimer.toInt() + 1}s",
                        fontSize = 12.sp,
                        color = Color(0xFF22D3EE),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text("Welle ${state.wave}", fontSize = 12.sp, color = HikariTextMuted)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { state.resize(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            if (!state.gameOver) {
                                state.shipX = (state.shipX + amount.x)
                                    .coerceIn(20f * den, (state.w - 20f * den).coerceAtLeast(20f * den))
                            }
                        }
                    }
            ) {
                state.frame // Snapshot-Read: löst Neuzeichnen pro Frame aus

                // Sterne (2 Parallax-Ebenen)
                for (s in state.stars) {
                    drawCircle(Color.White.copy(alpha = s.alpha), s.radius, Offset(s.x, s.y))
                }

                // Power-ups
                for (p in state.powerUps) {
                    val col = if (p.kind == 0) Color(0xFF22D3EE) else Color(0xFF4ADE80)
                    drawCircle(HikariSurfaceHigh, 13f * den, Offset(p.x, p.y))
                    drawCircle(col, 13f * den, Offset(p.x, p.y), style = Stroke(2f * den))
                    val res = textMeasurer.measure(
                        AnnotatedString(if (p.kind == 0) "D" else "+"),
                        TextStyle(color = col, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                }

                // Gegner
                for (e in state.enemies) {
                    val base = state.enemyColor(e.type)
                    val dmg = 1f - e.hp.toFloat() / e.maxHp
                    var col = lerpColor(base, Color.Black, dmg * 0.45f)
                    if (e.hitFlash > 0f) col = lerpColor(col, Color.White, e.hitFlash.coerceIn(0f, 1f) * 0.8f)
                    when (e.type) {
                        0 -> { // klein: Dreieck, Spitze nach unten
                            val path = Path().apply {
                                moveTo(e.x, e.y + e.radius)
                                lineTo(e.x - e.radius, e.y - e.radius)
                                lineTo(e.x + e.radius, e.y - e.radius)
                                close()
                            }
                            drawPath(path, col)
                        }
                        1 -> { // mittel: Raute
                            val path = Path().apply {
                                moveTo(e.x, e.y - e.radius)
                                lineTo(e.x + e.radius, e.y)
                                lineTo(e.x, e.y + e.radius)
                                lineTo(e.x - e.radius, e.y)
                                close()
                            }
                            drawPath(path, col)
                            drawCircle(Color.Black.copy(alpha = 0.35f), e.radius * 0.35f, Offset(e.x, e.y))
                        }
                        else -> { // Brocken: Sechseck
                            val path = Path()
                            for (i in 0..5) {
                                val a = i * 1.0472f + 0.5236f
                                val px = e.x + cos(a) * e.radius
                                val py = e.y + sin(a) * e.radius
                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            path.close()
                            drawPath(path, col)
                            drawPath(path, lerpColor(base, Color.Black, 0.6f), style = Stroke(2.5f * den))
                        }
                    }
                }

                // Projektile
                for (b in state.bullets) {
                    drawCircle(Color(0xFFFDE047).copy(alpha = 0.25f), 7f * den, Offset(b.x, b.y))
                    drawRoundRect(
                        Color(0xFFFDE047),
                        Offset(b.x - 2f * den, b.y - 9f * den),
                        Size(4f * den, 18f * den),
                        CornerRadius(2f * den, 2f * den),
                    )
                }

                // Partikel (Explosionen, Funken, Triebwerk)
                for (p in state.particles) {
                    val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(p.color.copy(alpha = a), p.radius * (0.5f + a * 0.5f), Offset(p.x, p.y))
                }

                // Spieler-Schiff
                if (!state.gameOver && state.w > 0f) {
                    val blink = state.invuln > 0f && (state.time * 12f).toInt() % 2 == 0
                    val shipAlpha = if (blink) 0.25f else 1f
                    val sx = state.shipX
                    val sy = state.shipY
                    val flicker = 0.7f + 0.3f * sin(state.time * 30f)
                    drawCircle(
                        HikariAmber.copy(alpha = 0.25f * flicker * shipAlpha),
                        (10f + 4f * flicker) * den,
                        Offset(sx, sy + 18f * den),
                    )
                    val ship = Path().apply {
                        moveTo(sx, sy - 22f * den)
                        lineTo(sx - 16f * den, sy + 14f * den)
                        lineTo(sx, sy + 7f * den)
                        lineTo(sx + 16f * den, sy + 14f * den)
                        close()
                    }
                    drawPath(ship, HikariAmber.copy(alpha = shipAlpha))
                    drawCircle(Color.White.copy(alpha = 0.85f * shipAlpha), 3.5f * den, Offset(sx, sy - 6f * den))
                }
            }

            // "Welle N"-Einblendung
            if (state.waveBanner > 0f && !state.gameOver) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Welle ${state.wave}",
                        fontSize = 30.sp,
                        color = HikariPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { alpha = state.waveBanner.coerceAtMost(1f) },
                    )
                }
            }

            // Game-Over-Overlay
            if (state.gameOver) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xE60A0A0A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Game Over", fontSize = 28.sp, color = HikariText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Welle ${state.wave} erreicht", fontSize = 13.sp, color = HikariTextMuted)
                        Spacer(Modifier.height(12.dp))
                        Text("Punkte: ${state.score}", fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        if (newRecord) {
                            Text("Neuer Rekord!", fontSize = 16.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Highscore: $highscore", fontSize = 14.sp, color = HikariTextMuted)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                newRecord = false
                                state.reset()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
