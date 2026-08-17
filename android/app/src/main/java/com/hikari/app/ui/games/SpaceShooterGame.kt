package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ————— Modi & Screens —————

private enum class SkyMode(val key: String, val label: String, val desc: String) {
    CLASSIC("classic", "Klassisch", "Endlose Wellen — alle 5 Wellen wartet ein Boss"),
    BOSS_RUSH("rush", "Boss-Rush", "Nur Bosse in Folge. Wie viele schaffst du?"),
    CAMPAIGN("campaign", "Kampagne", "5 Sektoren mit eigenen Gefahren und Sternen"),
}

private enum class SkyScreen { MENU, GAME, SECTORS, HANGAR, STATS, ACHIEVEMENTS }

private val SkyAccent = Color(0xFF22D3EE)

// ————— Erfolge —————

private class SkyAch(val id: String, val emoji: String, val title: String, val desc: String)

private val SkyAchList = listOf(
    SkyAch("boss1", "👑", "Boss-Jäger", "Besiege deinen ersten Boss"),
    SkyAch("wave10", "🌊", "Wellenreiter", "Erreiche Welle 10 im Klassik-Modus"),
    SkyAch("combo5", "🔥", "Heißgelaufen", "Erreiche eine ×5-Kill-Combo"),
    SkyAch("coins500", "🪙", "Sammler", "Sammle insgesamt 500 Münzen"),
    SkyAch("rush3", "⚔️", "Boss-Schreck", "Besiege 3 Bosse in einem Boss-Rush"),
    SkyAch("stars3", "⭐", "Perfektionist", "Schließe einen Sektor mit 3 Sternen ab"),
    SkyAch("kills1000", "💥", "Veteran", "1000 Abschüsse insgesamt"),
    SkyAch("nodmg5", "🛡️", "Unberührbar", "Erreiche Welle 5 ohne Schaden"),
    SkyAch("bomb1", "💣", "Kammerjäger", "Zünde deine erste Bombe"),
    SkyAch("shop1", "🔧", "Schrauber", "Kaufe dein erstes Upgrade im Hangar"),
)

// ————— Kampagnen-Sektoren —————

private class SkySectorDef(
    val name: String,
    val mech: String,
    val tint: Color,
    val starColor: Color,
    val speedMul: Float,
    val countMul: Float,
    val asteroids: Boolean,
)

private val SkySectors = listOf(
    SkySectorDef("Nebel von Kyra", "Ruhiger Einstieg im lila Nebel", Color(0xFFA78BFA), Color(0xFFC4B5FD), 1f, 1f, false),
    SkySectorDef("Asteroidengürtel", "Driftende Brocken — nur ausweichen!", Color(0xFF9CA3AF), Color(0xFFD1D5DB), 1f, 0.9f, true),
    SkySectorDef("Eisfeld Vora", "Träge Gegner, dafür viel mehr davon", Color(0xFF22D3EE), Color(0xFF67E8F9), 0.75f, 1.5f, false),
    SkySectorDef("Sonnennähe", "Gleißendes Licht, schnelle Gegner", Color(0xFFFBBF24), Color(0xFFFDE68A), 1.25f, 1f, false),
    SkySectorDef("Kernwelt", "Das Finale — mit Doppel-Boss", Color(0xFFF87171), Color(0xFFFCA5A5), 1.1f, 1.1f, false),
)

// ————— Meta: Persistenz (Münzen, Upgrades, Stats, Erfolge) —————

private class SkyMeta(private val prefs: SharedPreferences) {
    var highscore by mutableIntStateOf(prefs.getInt("spaceshooter_highscore", 0))
    var rushBest by mutableIntStateOf(prefs.getInt("spaceshooter_rush_best", 0))
    var rushBestBosses by mutableIntStateOf(prefs.getInt("spaceshooter_rush_best_bosses", 0))
    var coins by mutableIntStateOf(prefs.getInt("spaceshooter_coins", 0))
    var upFire by mutableIntStateOf(prefs.getInt("spaceshooter_up_fire", 0))
    var upLives by mutableIntStateOf(prefs.getInt("spaceshooter_up_lives", 0))
    var upMagnet by mutableIntStateOf(prefs.getInt("spaceshooter_up_magnet", 0))
    var skin by mutableIntStateOf(prefs.getInt("spaceshooter_skin", 0))
    val skinUnlocked = mutableStateListOf(
        true,
        prefs.getBoolean("spaceshooter_skin1", false),
        prefs.getBoolean("spaceshooter_skin2", false),
    )
    var haptics by mutableStateOf(prefs.getBoolean("spaceshooter_haptics", true))
    var reducedFx by mutableStateOf(prefs.getBoolean("spaceshooter_fx_reduced", false))
    var sensitivity by mutableIntStateOf(prefs.getInt("spaceshooter_sensitivity", 1))
    var lastMode by mutableStateOf(prefs.getString("spaceshooter_last_mode", "classic") ?: "classic")
    var helpSeen by mutableStateOf(prefs.getBoolean("spaceshooter_help_seen", false))
    val sectorStars = mutableStateListOf(
        prefs.getInt("spaceshooter_sector_stars_0", 0),
        prefs.getInt("spaceshooter_sector_stars_1", 0),
        prefs.getInt("spaceshooter_sector_stars_2", 0),
        prefs.getInt("spaceshooter_sector_stars_3", 0),
        prefs.getInt("spaceshooter_sector_stars_4", 0),
    )
    var statKills by mutableIntStateOf(prefs.getInt("spaceshooter_stat_kills", 0))
    var statBosses by mutableIntStateOf(prefs.getInt("spaceshooter_stat_bosses", 0))
    var statShots by mutableIntStateOf(prefs.getInt("spaceshooter_stat_shots", 0))
    var statHits by mutableIntStateOf(prefs.getInt("spaceshooter_stat_hits", 0))
    var statCoinsTotal by mutableIntStateOf(prefs.getInt("spaceshooter_stat_coins_total", 0))
    var statPlaytime by mutableLongStateOf(prefs.getLong("spaceshooter_stat_playtime", 0L))
    var statBestWave by mutableIntStateOf(prefs.getInt("spaceshooter_stat_best_wave", 0))
    var statGames by mutableIntStateOf(prefs.getInt("spaceshooter_stat_games", 0))
    val unlockedAchs = mutableStateMapOf<String, Boolean>().apply {
        SkyAchList.forEach { put(it.id, prefs.getBoolean("spaceshooter_ach_${it.id}", false)) }
    }

    private fun putInt(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
    private fun putBool(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    fun addCoins(n: Int) { coins += n; putInt("spaceshooter_coins", coins) }
    fun spendCoins(n: Int): Boolean {
        if (coins < n) return false
        coins -= n
        putInt("spaceshooter_coins", coins)
        return true
    }

    fun buyFire() { upFire += 1; putInt("spaceshooter_up_fire", upFire) }
    fun buyLives() { upLives += 1; putInt("spaceshooter_up_lives", upLives) }
    fun buyMagnet() { upMagnet += 1; putInt("spaceshooter_up_magnet", upMagnet) }
    fun selectSkin(i: Int) { skin = i; putInt("spaceshooter_skin", skin) }
    fun unlockSkin(i: Int) {
        skinUnlocked[i] = true
        putBool("spaceshooter_skin$i", true)
    }

    fun saveHaptics(v: Boolean) { haptics = v; putBool("spaceshooter_haptics", v) }
    fun saveReducedFx(v: Boolean) { reducedFx = v; putBool("spaceshooter_fx_reduced", v) }
    fun saveSensitivity(v: Int) { sensitivity = v; putInt("spaceshooter_sensitivity", v) }
    fun saveLastMode(v: String) { lastMode = v; prefs.edit().putString("spaceshooter_last_mode", v).apply() }
    fun setHelpSeen() { helpSeen = true; putBool("spaceshooter_help_seen", true) }
    fun sensFactor() = when (sensitivity) { 0 -> 0.7f; 2 -> 1.4f; else -> 1f }

    fun saveHighscore(v: Int) { highscore = v; putInt("spaceshooter_highscore", v) }
    fun setRushBest(score: Int, bosses: Int) {
        if (score > rushBest) { rushBest = score; putInt("spaceshooter_rush_best", score) }
        if (bosses > rushBestBosses) { rushBestBosses = bosses; putInt("spaceshooter_rush_best_bosses", bosses) }
    }

    fun setStars(sector: Int, stars: Int) {
        if (stars > sectorStars[sector]) {
            sectorStars[sector] = stars
            putInt("spaceshooter_sector_stars_$sector", stars)
        }
    }

    fun tryUnlock(id: String): Boolean {
        if (unlockedAchs[id] == true) return false
        unlockedAchs[id] = true
        putBool("spaceshooter_ach_$id", true)
        return true
    }

    fun commitRun(kills: Int, shots: Int, hits: Int, coinsRun: Int, seconds: Long, bosses: Int, bestWave: Int) {
        statKills += kills
        statShots += shots
        statHits += hits
        statCoinsTotal += coinsRun
        statPlaytime += seconds
        statBosses += bosses
        statGames += 1
        if (bestWave > statBestWave) statBestWave = bestWave
        prefs.edit()
            .putInt("spaceshooter_stat_kills", statKills)
            .putInt("spaceshooter_stat_shots", statShots)
            .putInt("spaceshooter_stat_hits", statHits)
            .putInt("spaceshooter_stat_coins_total", statCoinsTotal)
            .putLong("spaceshooter_stat_playtime", statPlaytime)
            .putInt("spaceshooter_stat_bosses", statBosses)
            .putInt("spaceshooter_stat_games", statGames)
            .putInt("spaceshooter_stat_best_wave", statBestWave)
            .apply()
    }
}

// ————— Entities —————

private class SkyStar(var x: Float, var y: Float, val radius: Float, val speed: Float, val alpha: Float)
private class SkyBullet(var x: Float, var y: Float, var vx: Float = 0f)
private class SkyEnemyBullet(var x: Float, var y: Float, var vx: Float, var vy: Float)
private class SkyPowerUp(var x: Float, var y: Float, val kind: Int) // 0 Double, 1 Leben, 2 Schild, 3 Triple, 4 Bombe
private class SkyCoin(var x: Float, var y: Float, var vx: Float, var vy: Float)
private class SkyShockwave(var x: Float, var y: Float, var r: Float, val maxR: Float, var life: Float, val maxLife: Float)
private class SkyBreach(val x: Float, var life: Float)

private class SkyAsteroid(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val radius: Float,
    var rot: Float, val rotSpeed: Float,
)

private class SkyEnemy(
    val type: Int, // 0 klein, 1 mittel, 2 Brocken, 3 Schütze, 4 Splitter, 5 Mini
    val baseX: Float,
    var x: Float,
    var y: Float,
    val speed: Float,
    val radius: Float,
    var hp: Int,
    val maxHp: Int,
    val points: Int,
    val sinAmp: Float,
    val sinFreq: Float,
    val phase: Float,
    var hitFlash: Float = 0f,
    var fireTimer: Float = 1.6f,
)

private class SkyBoss(
    var x: Float,
    var y: Float,
    val targetY: Float,
    val radius: Float,
    var hp: Int,
    val maxHp: Int,
    var dir: Float,
    var fireTimer: Float,
    val tier: Int,
    var entering: Boolean = true,
    var hitFlash: Float = 0f,
)

private class SkyParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Color, val radius: Float,
)

// ————— Spielzustand —————

private class SkyState(val den: Float, val meta: SkyMeta) {
    var w = 0f
    var h = 0f
    var shipX = 0f
    val shipY get() = h - 76f * den

    val stars = ArrayList<SkyStar>()
    val bullets = ArrayList<SkyBullet>()
    val enemyBullets = ArrayList<SkyEnemyBullet>()
    val enemies = ArrayList<SkyEnemy>()
    val bosses = ArrayList<SkyBoss>()
    val particles = ArrayList<SkyParticle>()
    val powerUps = ArrayList<SkyPowerUp>()
    val coins = ArrayList<SkyCoin>()
    val asteroids = ArrayList<SkyAsteroid>()
    val shockwaves = ArrayList<SkyShockwave>()
    val breaches = ArrayList<SkyBreach>()
    val achQueue = ArrayDeque<SkyAch>()

    var mode = SkyMode.CLASSIC
    var sector = 0
    var time = 0f
    var fireTimer = 0f
    var invuln = 0f
    var toSpawn = 0
    var spawnTimer = 0f
    var interWave = 0.6f
    var interBoss = 0f
    var bossElapsed = 0f
    var asteroidTimer = 0f
    var runShots = 0
    var runHits = 0
    var runKills = 0
    var runCoins = 0
    var runBosses = 0
    var livesLost = 0
    var runTime = 0f
    var comboKills = 0
    var bestComboRun = 1
    var finished = false
    var recordShown = false
    var maxLives = 5

    var frame by mutableLongStateOf(0L)
    var runId by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var wave by mutableIntStateOf(0)
    var rushBosses by mutableIntStateOf(0)
    var waveInSector by mutableIntStateOf(0)
    var doubleTimer by mutableFloatStateOf(0f)
    var tripleTimer by mutableFloatStateOf(0f)
    var shield by mutableStateOf(false)
    var comboMult by mutableIntStateOf(1)
    var comboTimer by mutableFloatStateOf(0f)
    var waveBanner by mutableFloatStateOf(0f)
    var bannerText by mutableStateOf("")
    var recordBanner by mutableFloatStateOf(0f)
    var hitVignette by mutableFloatStateOf(0f)
    var countdown by mutableFloatStateOf(0f)
    var paused by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var victory by mutableStateOf(false)
    var victoryStars by mutableIntStateOf(0)
    var newRecord by mutableStateOf(false)
    var achBanner by mutableStateOf<SkyAch?>(null)
    var achBannerT by mutableFloatStateOf(0f)
    var hapticHeavy by mutableIntStateOf(0)
    var hapticLight by mutableIntStateOf(0)

    fun enemyColor(type: Int): Color = when (type) {
        0 -> Color(0xFF60A5FA)
        1 -> Color(0xFFA78BFA)
        2 -> Color(0xFFF87171)
        3 -> Color(0xFFFB923C)
        4 -> Color(0xFF4ADE80)
        else -> Color(0xFF22D3EE)
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

    fun startRun(m: SkyMode, sec: Int = 0) {
        mode = m
        sector = sec
        bullets.clear()
        enemyBullets.clear()
        enemies.clear()
        bosses.clear()
        particles.clear()
        powerUps.clear()
        coins.clear()
        asteroids.clear()
        shockwaves.clear()
        breaches.clear()
        achQueue.clear()
        score = 0
        lives = 3 + meta.upLives
        maxLives = lives + 2
        wave = 0
        waveInSector = 0
        rushBosses = 0
        doubleTimer = 0f
        tripleTimer = 0f
        shield = false
        comboMult = 1
        comboTimer = 0f
        comboKills = 0
        bestComboRun = 1
        runShots = 0
        runHits = 0
        runKills = 0
        runCoins = 0
        runBosses = 0
        livesLost = 0
        runTime = 0f
        time = 0f
        fireTimer = 0f
        invuln = 1f
        toSpawn = 0
        spawnTimer = 0f
        interWave = 0.9f
        interBoss = 1.2f
        bossElapsed = 0f
        asteroidTimer = 1f
        waveBanner = 0f
        bannerText = ""
        recordBanner = 0f
        recordShown = false
        hitVignette = 0f
        achBanner = null
        achBannerT = 0f
        finished = false
        newRecord = false
        gameOver = false
        victory = false
        victoryStars = 0
        paused = false
        countdown = 3f
        if (w > 0f) shipX = w / 2f
        meta.saveLastMode(m.key)
        runId++
    }

    fun pauseGame() {
        if (!gameOver && !victory) paused = true
    }

    fun resumeGame() {
        paused = false
        countdown = 3f
    }

    fun award(id: String) {
        if (meta.tryUnlock(id)) {
            SkyAchList.firstOrNull { it.id == id }?.let { achQueue.add(it) }
        }
    }

    fun finishRun() {
        if (finished) return
        finished = true
        meta.commitRun(
            runKills, runShots, runHits, runCoins,
            runTime.toLong(), runBosses,
            if (mode == SkyMode.CLASSIC) wave else 0,
        )
        if (mode == SkyMode.CLASSIC && score > meta.highscore) {
            meta.saveHighscore(score)
            newRecord = true
        }
        if (mode == SkyMode.BOSS_RUSH) meta.setRushBest(score, rushBosses)
    }

    private fun speedMul() = 1f + (wave - 1) * 0.05f + (wave / 5) * 0.2f

    private fun spawnEnemy() {
        val sectorMul = if (mode == SkyMode.CAMPAIGN) SkySectors[sector].speedMul else 1f
        val roll = Random.nextFloat()
        val pBig = if (wave >= 4) min(0.20f, 0.04f + wave * 0.014f) else 0f
        val pSplit = if (wave >= 4 || mode == SkyMode.CAMPAIGN) min(0.15f, 0.04f + wave * 0.01f) else 0f
        val pShoot = if (wave >= 3 || mode == SkyMode.CAMPAIGN) min(0.18f, 0.05f + wave * 0.012f) else 0f
        val pMed = if (wave >= 2) min(0.35f, 0.08f + wave * 0.03f) else 0f
        val type = when {
            roll < pBig -> 2
            roll < pBig + pSplit -> 4
            roll < pBig + pSplit + pShoot -> 3
            roll < pBig + pSplit + pShoot + pMed -> 1
            else -> 0
        }
        val radius = when (type) { 0 -> 12f; 1 -> 17f; 2 -> 24f; 3 -> 16f; else -> 19f } * den
        val speed = when (type) { 0 -> 0.16f; 1 -> 0.11f; 2 -> 0.07f; 3 -> 0.09f; else -> 0.10f } * speedMul() * sectorMul
        val hp = when (type) { 0 -> 1; 1 -> 2; 2 -> 4; 3 -> 2; else -> 3 }
        val pts = when (type) { 0 -> 10; 1 -> 25; 2 -> 60; 3 -> 35; else -> 40 }
        val margin = radius + 8f * den
        val baseX = margin + Random.nextFloat() * (w - margin * 2).coerceAtLeast(1f)
        val sine = type == 0 && Random.nextFloat() < 0.45f
        enemies.add(
            SkyEnemy(
                type = type, baseX = baseX, x = baseX, y = -radius * 2,
                speed = speed, radius = radius, hp = hp, maxHp = hp, points = pts,
                sinAmp = if (sine) (0.06f + Random.nextFloat() * 0.09f) * w else 0f,
                sinFreq = 1.5f + Random.nextFloat() * 1.5f,
                phase = Random.nextFloat() * 6.2832f,
                fireTimer = 1.2f + Random.nextFloat() * 1.5f,
            )
        )
    }

    private fun spawnBoss(tier: Int, xFrac: Float) {
        val r = (44f + tier * 2f) * den
        bosses.add(
            SkyBoss(
                x = w * xFrac, y = -r * 2f, targetY = h * 0.17f, radius = r,
                hp = 30 + tier * 15, maxHp = 30 + tier * 15,
                dir = if (Random.nextBoolean()) 1f else -1f,
                fireTimer = 1.4f, tier = tier,
            )
        )
    }

    private fun spawnAsteroid() {
        val r = (16f + Random.nextFloat() * 16f) * den
        asteroids.add(
            SkyAsteroid(
                x = Random.nextFloat() * w, y = -r * 2f,
                vx = (Random.nextFloat() - 0.5f) * 0.08f * w,
                vy = h * (0.08f + Random.nextFloat() * 0.06f),
                radius = r,
                rot = Random.nextFloat() * 360f,
                rotSpeed = (Random.nextFloat() - 0.5f) * 90f,
            )
        )
    }

    private fun explode(x: Float, y: Float, color: Color, big: Boolean) {
        val n = (if (big) 18 else 10) / (if (meta.reducedFx) 2 else 1)
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

    private fun dropCoins(x: Float, y: Float, n: Int) {
        repeat(n) {
            coins.add(
                SkyCoin(
                    x, y,
                    (Random.nextFloat() - 0.5f) * 160f * den,
                    -Random.nextFloat() * 120f * den,
                )
            )
        }
    }

    private fun registerKill() {
        comboTimer = 1.5f
        comboKills += 1
        comboMult = min(5, comboKills)
        if (comboMult > bestComboRun) bestComboRun = comboMult
        if (comboMult >= 5) award("combo5")
        runKills += 1
        if (meta.statKills + runKills >= 1000) award("kills1000")
    }

    fun damagePlayer(fromBreach: Boolean = false) {
        if (gameOver || victory) return
        if (!fromBreach && invuln > 0f) return
        if (shield) {
            shield = false
            shockwaves.add(SkyShockwave(shipX, shipY, 10f * den, 80f * den, 0.4f, 0.4f))
            hapticLight++
            return
        }
        lives -= 1
        livesLost += 1
        hitVignette = 1f
        invuln = 1.5f
        hapticHeavy++
        if (lives <= 0) {
            gameOver = true
            finishRun()
        }
    }

    private fun bombNow() {
        award("bomb1")
        shockwaves.add(SkyShockwave(shipX, shipY, 20f * den, h * 0.8f, 0.55f, 0.55f))
        for (e in enemies) {
            explode(e.x, e.y, enemyColor(e.type), e.type == 2)
            score += e.points * comboMult
            registerKill()
        }
        enemies.clear()
        enemyBullets.clear()
        for (boss in bosses) {
            boss.hp -= 5
            boss.hitFlash = 1f
        }
        hapticHeavy++
    }

    private fun completeSector() {
        victory = true
        victoryStars = when (livesLost) { 0 -> 3; 1, 2 -> 2; else -> 1 }
        if (victoryStars == 3) award("stars3")
        meta.setStars(sector, victoryStars)
        finishRun()
        hapticHeavy++
    }

    fun update(dt: Float) {
        if (w <= 0f || gameOver || victory) return
        time += dt
        runTime += dt

        // Sternen-Parallax
        for (s in stars) {
            s.y += s.speed * h * dt
            if (s.y > h + 4f) {
                s.y = -4f
                s.x = Random.nextFloat() * w
            }
        }

        // Timer abklingen
        if (waveBanner > 0f) waveBanner = (waveBanner - dt).coerceAtLeast(0f)
        if (doubleTimer > 0f) doubleTimer = (doubleTimer - dt).coerceAtLeast(0f)
        if (tripleTimer > 0f) tripleTimer = (tripleTimer - dt).coerceAtLeast(0f)
        if (recordBanner > 0f) recordBanner = (recordBanner - dt).coerceAtLeast(0f)
        if (hitVignette > 0f) hitVignette = (hitVignette - dt * 2f).coerceAtLeast(0f)
        if (invuln > 0f) invuln -= dt
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) {
                comboKills = 0
                comboMult = 1
            }
        }

        // Erfolgs-Banner nachschieben
        if (achBanner == null && achQueue.isNotEmpty()) {
            achBanner = achQueue.removeFirst()
            achBannerT = 3f
        }
        if (achBannerT > 0f) {
            achBannerT -= dt
            if (achBannerT <= 0f) achBanner = null
        }

        // Wellen-/Boss-Logik pro Modus
        when (mode) {
            SkyMode.CLASSIC -> {
                if (bosses.isEmpty() && toSpawn == 0 && enemies.isEmpty()) {
                    interWave -= dt
                    if (interWave <= 0f) {
                        wave += 1
                        if (wave == 5 && livesLost == 0) award("nodmg5")
                        if (wave == 10) award("wave10")
                        if (wave % 5 == 0) {
                            spawnBoss(tier = wave / 5, xFrac = 0.5f)
                            bannerText = "Boss!"
                        } else {
                            toSpawn = 5 + wave * 2 + (wave / 5) * 4
                            spawnTimer = 0.4f
                            bannerText = "Welle $wave"
                        }
                        waveBanner = 2f
                        interWave = 1.1f
                    }
                }
            }
            SkyMode.CAMPAIGN -> {
                val def = SkySectors[sector]
                if (bosses.isEmpty() && toSpawn == 0 && enemies.isEmpty()) {
                    interWave -= dt
                    if (interWave <= 0f) {
                        if (waveInSector >= 5) {
                            completeSector()
                            return
                        }
                        waveInSector += 1
                        wave = waveInSector + sector * 2 // steuert Tempo-/Typkurve
                        if (waveInSector == 5) {
                            if (sector == 4) {
                                spawnBoss(tier = sector + 2, xFrac = 0.3f)
                                spawnBoss(tier = sector + 1, xFrac = 0.7f)
                            } else {
                                spawnBoss(tier = sector + 1, xFrac = 0.5f)
                            }
                            bannerText = "Boss!"
                        } else {
                            toSpawn = ((6 + sector * 2 + waveInSector * 2) * def.countMul).toInt()
                            spawnTimer = 0.4f
                            bannerText = "Welle $waveInSector/5"
                        }
                        waveBanner = 2f
                        interWave = 1.1f
                    }
                }
                if (def.asteroids) {
                    asteroidTimer -= dt
                    if (asteroidTimer <= 0f) {
                        spawnAsteroid()
                        asteroidTimer = 1.4f + Random.nextFloat()
                    }
                }
            }
            SkyMode.BOSS_RUSH -> {
                if (bosses.isEmpty()) {
                    interBoss -= dt
                    if (interBoss <= 0f) {
                        wave = rushBosses + 1
                        spawnBoss(tier = wave, xFrac = 0.5f)
                        bossElapsed = 0f
                        bannerText = "Boss $wave"
                        waveBanner = 2f
                    }
                } else {
                    bossElapsed += dt
                }
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

        // Auto-Feuer (Feuerrate durch Hangar-Upgrade verbessert)
        fireTimer -= dt
        if (fireTimer <= 0f) {
            fireTimer = 0.28f - meta.upFire * 0.03f
            val by = shipY - 26f * den
            val side = h * 0.25f
            when {
                tripleTimer > 0f -> {
                    bullets.add(SkyBullet(shipX, by))
                    bullets.add(SkyBullet(shipX - 8f * den, by, -side))
                    bullets.add(SkyBullet(shipX + 8f * den, by, side))
                    runShots += 3
                }
                doubleTimer > 0f -> {
                    bullets.add(SkyBullet(shipX - 10f * den, by))
                    bullets.add(SkyBullet(shipX + 10f * den, by))
                    runShots += 2
                }
                else -> {
                    bullets.add(SkyBullet(shipX, by))
                    runShots += 1
                }
            }
        }

        // Triebwerks-Partikel
        if (frame % 3L == 0L && !meta.reducedFx) {
            particles.add(
                SkyParticle(
                    shipX + (Random.nextFloat() - 0.5f) * 8f * den,
                    shipY + 20f * den,
                    0f, 90f * den, 0.3f, 0.3f, HikariAmber, 2.5f * den,
                )
            )
        }

        // Eigene Projektile
        val bulletSpeed = h * 1.15f
        val bIt = bullets.iterator()
        while (bIt.hasNext()) {
            val b = bIt.next()
            b.y -= bulletSpeed * dt
            b.x += b.vx * dt
            if (b.y < -20f || b.x < -20f || b.x > w + 20f) bIt.remove()
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
                val pc = when (p.kind) {
                    0 -> Color(0xFF22D3EE)
                    1 -> Color(0xFF4ADE80)
                    2 -> Color(0xFF4ADE80)
                    3 -> HikariAmber
                    else -> HikariDanger
                }
                when (p.kind) {
                    0 -> doubleTimer = 12f
                    1 -> lives = min(maxLives, lives + 1)
                    2 -> shield = true
                    3 -> tripleTimer = 12f
                    4 -> bombNow()
                }
                if (p.kind != 4) hapticLight++
                repeat(if (meta.reducedFx) 4 else 8) {
                    val a = Random.nextFloat() * 6.2832f
                    particles.add(
                        SkyParticle(p.x, p.y, cos(a) * 140f * den, sin(a) * 140f * den, 0.4f, 0.4f, pc, 2f * den)
                    )
                }
                pIt.remove()
            }
        }

        // Asteroiden (Kampagne, Sektor 2): unzerstörbar, nur ausweichen
        val aIt = asteroids.iterator()
        while (aIt.hasNext()) {
            val a = aIt.next()
            a.x += a.vx * dt
            a.y += a.vy * dt
            a.rot += a.rotSpeed * dt
            if (a.y - a.radius > h || a.x < -a.radius * 2 || a.x > w + a.radius * 2) {
                aIt.remove()
                continue
            }
            if (abs(a.x - shipX) < a.radius + 14f * den && abs(a.y - shipY) < a.radius + 12f * den) {
                damagePlayer()
            }
            // Projektile prallen wirkungslos ab
            val abIt = bullets.iterator()
            while (abIt.hasNext()) {
                val b = abIt.next()
                val dx = b.x - a.x
                val dy = b.y - a.y
                if (dx * dx + dy * dy < a.radius * a.radius) {
                    abIt.remove()
                    particles.add(
                        SkyParticle(b.x, b.y, 0f, -60f * den, 0.2f, 0.2f, Color(0xFF9CA3AF), 1.8f * den)
                    )
                }
            }
        }

        // Gegner-Projektile
        val ebIt = enemyBullets.iterator()
        while (ebIt.hasNext()) {
            val b = ebIt.next()
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.y > h + 20f || b.y < -40f || b.x < -20f || b.x > w + 20f) {
                ebIt.remove()
                continue
            }
            if (abs(b.x - shipX) < 12f * den && abs(b.y - shipY) < 14f * den) {
                ebIt.remove()
                damagePlayer()
            }
        }

        // Gegner
        val spawnKids = ArrayList<SkyEnemy>()
        val eIt = enemies.iterator()
        while (eIt.hasNext()) {
            val e = eIt.next()
            e.y += e.speed * h * dt
            if (e.sinAmp > 0f) {
                e.x = (e.baseX + sin(e.y / h * 6.2832f * e.sinFreq + e.phase) * e.sinAmp)
                    .coerceIn(e.radius, (w - e.radius).coerceAtLeast(e.radius))
            }
            if (e.hitFlash > 0f) e.hitFlash -= dt * 6f

            // Schütze feuert gezielt
            if (e.type == 3 && e.y > 0f) {
                e.fireTimer -= dt
                if (e.fireTimer <= 0f) {
                    e.fireTimer = 2.2f + Random.nextFloat() * 1.2f
                    val ang = atan2(shipY - e.y, shipX - e.x)
                    val sp = h * 0.33f
                    enemyBullets.add(SkyEnemyBullet(e.x, e.y + e.radius * 0.6f, cos(ang) * sp, sin(ang) * sp))
                }
            }

            // Durchbruch am unteren Rand
            if (e.y - e.radius > h) {
                eIt.remove()
                breaches.add(SkyBreach(e.x, 1.2f))
                damagePlayer(fromBreach = true)
                continue
            }

            // Kollision mit dem Schiff
            if (invuln <= 0f &&
                abs(e.x - shipX) < e.radius + 16f * den &&
                abs(e.y - shipY) < e.radius + 14f * den
            ) {
                explode(e.x, e.y, enemyColor(e.type), e.type == 2)
                eIt.remove()
                damagePlayer()
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
                    runHits += 1
                    repeat(if (meta.reducedFx) 1 else 3) {
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
                registerKill()
                score += e.points * comboMult
                // Splitter zerfällt in 2 Minis
                if (e.type == 4) {
                    repeat(2) { k ->
                        val off = if (k == 0) -1f else 1f
                        spawnKids.add(
                            SkyEnemy(
                                type = 5, baseX = e.x, x = e.x + off * e.radius * 0.6f, y = e.y,
                                speed = 0.22f * speedMul(), radius = 9f * den,
                                hp = 1, maxHp = 1, points = 15,
                                sinAmp = 0.04f * w, sinFreq = 2.5f,
                                phase = Random.nextFloat() * 6.2832f,
                            )
                        )
                    }
                }
                if (Random.nextFloat() < 0.30f) dropCoins(e.x, e.y, if (e.type == 2) 3 else 1)
                if (Random.nextFloat() < 0.10f) {
                    val roll = Random.nextFloat()
                    val kind = when {
                        roll < 0.30f -> 0
                        roll < 0.45f -> 1
                        roll < 0.65f -> 2
                        roll < 0.85f -> 3
                        else -> 4
                    }
                    powerUps.add(SkyPowerUp(e.x, e.y, kind))
                }
                eIt.remove()
            }
        }
        enemies.addAll(spawnKids)

        // Bosse
        val bossIt = bosses.iterator()
        while (bossIt.hasNext()) {
            val boss = bossIt.next()
            if (boss.hitFlash > 0f) boss.hitFlash -= dt * 5f
            if (boss.entering) {
                boss.y += h * 0.12f * dt
                if (boss.y >= boss.targetY) {
                    boss.y = boss.targetY
                    boss.entering = false
                }
            } else {
                val phase2 = boss.hp <= boss.maxHp / 2
                val spd = w * (0.13f + boss.tier * 0.012f) * (if (phase2) 1.5f else 1f)
                boss.x += boss.dir * spd * dt
                if (boss.x < boss.radius) { boss.x = boss.radius; boss.dir = 1f }
                if (boss.x > w - boss.radius) { boss.x = w - boss.radius; boss.dir = -1f }
                boss.fireTimer -= dt
                if (boss.fireTimer <= 0f) {
                    boss.fireTimer = ((if (phase2) 1.0f else 1.7f) - min(0.5f, boss.tier * 0.05f)).coerceAtLeast(0.5f)
                    val n = if (phase2) 7 else 5
                    val base = atan2(shipY - boss.y, shipX - boss.x)
                    val sp = h * (0.28f + boss.tier * 0.015f)
                    for (k in 0 until n) {
                        val ang = base + (k - (n - 1) / 2f) * 0.22f
                        enemyBullets.add(
                            SkyEnemyBullet(boss.x, boss.y + boss.radius * 0.5f, cos(ang) * sp, sin(ang) * sp)
                        )
                    }
                }
            }

            if (invuln <= 0f &&
                abs(boss.x - shipX) < boss.radius + 16f * den &&
                abs(boss.y - shipY) < boss.radius + 14f * den
            ) {
                damagePlayer()
            }

            val bhIt = bullets.iterator()
            while (bhIt.hasNext()) {
                val b = bhIt.next()
                if (abs(b.x - boss.x) < boss.radius + 4f * den && abs(b.y - boss.y) < boss.radius + 6f * den) {
                    bhIt.remove()
                    boss.hp -= 1
                    boss.hitFlash = 1f
                    runHits += 1
                }
            }

            if (boss.hp <= 0) {
                bossIt.remove()
                explode(boss.x, boss.y, HikariDanger, big = true)
                explode(boss.x - boss.radius * 0.5f, boss.y, HikariAmber, big = true)
                explode(boss.x + boss.radius * 0.5f, boss.y, HikariDanger, big = true)
                shockwaves.add(SkyShockwave(boss.x, boss.y, 20f * den, boss.radius * 4f, 0.5f, 0.5f))
                runBosses += 1
                award("boss1")
                dropCoins(boss.x, boss.y, 8 + boss.tier * 2)
                score += 500 * boss.tier
                hapticHeavy++
                if (mode == SkyMode.BOSS_RUSH) {
                    rushBosses += 1
                    val bonus = max(0f, 35f - bossElapsed)
                    score += 100 + (bonus * 10).toInt()
                    lives = min(maxLives, lives + 1)
                    interBoss = 2.5f
                    if (rushBosses >= 3) award("rush3")
                }
            }
        }

        // Münzen: fallen, Magnet zieht sie ab gewisser Distanz zum Schiff
        val cIt = coins.iterator()
        while (cIt.hasNext()) {
            val c = cIt.next()
            val dx = shipX - c.x
            val dy = shipY - c.y
            val d2 = dx * dx + dy * dy
            val magnetR = (70f + meta.upMagnet * 45f) * den
            if (d2 < magnetR * magnetR) {
                val d = sqrt(d2).coerceAtLeast(1f)
                c.vx += dx / d * 2400f * den * dt
                c.vy += dy / d * 2400f * den * dt
            } else {
                c.vy = min(c.vy + 500f * den * dt, h * 0.25f)
                c.vx *= 0.98f
            }
            c.x += c.vx * dt
            c.y += c.vy * dt
            if (c.y > h + 20f) {
                cIt.remove()
                continue
            }
            if (d2 < 22f * den * 22f * den) {
                cIt.remove()
                runCoins += 1
                meta.addCoins(1)
                if (meta.statCoinsTotal + runCoins >= 500) award("coins500")
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

        // Schockwellen
        val swIt = shockwaves.iterator()
        while (swIt.hasNext()) {
            val s = swIt.next()
            s.r += (s.maxR / s.maxLife) * dt
            s.life -= dt
            if (s.life <= 0f) swIt.remove()
        }

        // Durchbruch-Hinweise
        val brIt = breaches.iterator()
        while (brIt.hasNext()) {
            val b = brIt.next()
            b.life -= dt
            if (b.life <= 0f) brIt.remove()
        }

        // Live-Rekord-Banner
        if (mode == SkyMode.CLASSIC && !recordShown && meta.highscore > 0 && score > meta.highscore) {
            recordShown = true
            recordBanner = 2.5f
            hapticLight++
        }

        shipX = shipX.coerceIn(20f * den, (w - 20f * den).coerceAtLeast(20f * den))
    }
}

// ————— Haupt-Composable (Router) —————

@Composable
fun SpaceShooterGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    val meta = remember { SkyMeta(prefs) }
    val den = LocalDensity.current.density
    val state = remember { SkyState(den, meta) }
    var screen by remember { mutableStateOf(SkyScreen.MENU) }
    val haptic = LocalHapticFeedback.current

    // Zentrale Haptik (Toggle in den Einstellungen)
    LaunchedEffect(state.hapticHeavy) {
        if (state.hapticHeavy > 0 && meta.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.hapticLight) {
        if (state.hapticLight > 0 && meta.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Auto-Pause bei App-Wechsel
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.pauseGame()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun startRun(mode: SkyMode, sector: Int = 0) {
        state.startRun(mode, sector)
        screen = SkyScreen.GAME
    }

    fun exitToMenu() {
        state.finishRun()
        screen = SkyScreen.MENU
    }

    // System-Back: im Spiel pausieren statt verlassen
    BackHandler {
        when (screen) {
            SkyScreen.MENU -> onBack()
            SkyScreen.GAME -> {
                if (state.gameOver || state.victory) exitToMenu()
                else if (state.paused) exitToMenu()
                else state.pauseGame()
            }
            else -> screen = SkyScreen.MENU
        }
    }

    Crossfade(targetState = screen, animationSpec = tween(220), label = "skyScreen") { s ->
        when (s) {
            SkyScreen.MENU -> SkyMenuScreen(
                meta = meta,
                onBack = onBack,
                onPlay = { m -> if (m == SkyMode.CAMPAIGN) screen = SkyScreen.SECTORS else startRun(m) },
                onNav = { screen = it },
            )
            SkyScreen.SECTORS -> SkySectorSelect(
                meta = meta,
                onBack = { screen = SkyScreen.MENU },
                onPick = { startRun(SkyMode.CAMPAIGN, it) },
            )
            SkyScreen.HANGAR -> SkyHangarScreen(meta = meta, state = state, onBack = { screen = SkyScreen.MENU })
            SkyScreen.STATS -> SkyStatsScreen(meta = meta, onBack = { screen = SkyScreen.MENU })
            SkyScreen.ACHIEVEMENTS -> SkyAchievementsScreenUi(meta = meta, onBack = { screen = SkyScreen.MENU })
            SkyScreen.GAME -> SkyGameView(state = state, meta = meta, onMenu = { exitToMenu() })
        }
    }
}

// ————— Menü —————

@Composable
private fun SkyMenuScreen(
    meta: SkyMeta,
    onBack: () -> Unit,
    onPlay: (SkyMode) -> Unit,
    onNav: (SkyScreen) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkyAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Sky Strike", SkyAccent, onBack, right = {
                GxIconChip("?", onClick = { showHelp = true })
            })

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                Text("Wähle deinen Modus", fontSize = 13.sp, color = HikariTextMuted)
                SkyCoinPill(meta.coins) { onNav(SkyScreen.HANGAR) }
            }
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkyMode.entries.forEachIndexed { i, m ->
                    val best = when (m) {
                        SkyMode.CLASSIC -> if (meta.highscore > 0) "Rekord: ${meta.highscore}" else null
                        SkyMode.BOSS_RUSH -> if (meta.rushBestBosses > 0) "Beste Serie: ${meta.rushBestBosses} Bosse" else null
                        SkyMode.CAMPAIGN -> "★ ${meta.sectorStars.sum()}/15 gesammelt"
                    }
                    val emoji = when (m) {
                        SkyMode.CLASSIC -> "🚀"
                        SkyMode.BOSS_RUSH -> "👑"
                        SkyMode.CAMPAIGN -> "🗺️"
                    }
                    GxAppear(i) {
                        GxModeCard(
                            emoji = emoji,
                            title = m.label,
                            subtitle = m.desc,
                            accent = SkyAccent,
                            highlighted = meta.lastMode == m.key,
                            best = best,
                            onClick = { onPlay(m) },
                        )
                    }
                }

                GxAppear(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("🛠️", "Hangar", Modifier.weight(1f)) { onNav(SkyScreen.HANGAR) }
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f)) { onNav(SkyScreen.STATS) }
                        GxSmallAction("🏅", "Erfolge", Modifier.weight(1f)) { onNav(SkyScreen.ACHIEVEMENTS) }
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f)) { showSettings = true }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (showSettings) SkySettingsSheet(meta) { showSettings = false }
        if (showHelp) SkyHelpOverlay(onDismiss = { showHelp = false })
    }
}

@Composable
private fun SkyCoinPill(coins: Int, onClick: (() -> Unit)? = null) {
    val shown = gxAnimatedCount(coins, 500)
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HikariCardBg)
            .border(1.dp, HikariAmber.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.gxPressable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🪙", fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text("$shown", fontSize = 13.sp, color = HikariAmber, fontWeight = FontWeight.Black)
    }
}

// ————— Sektor-Auswahl (Kampagne) —————

@Composable
private fun SkySectorSelect(meta: SkyMeta, onBack: () -> Unit, onPick: (Int) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkyAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Kampagne", SkyAccent, onBack, right = {
                Text("${meta.sectorStars.sum()}/15", fontSize = 12.sp, color = HikariAmber, fontWeight = FontWeight.Black)
            })
            Text(
                "Kämpfe dich durch 5 Sektoren — 3 Sterne gibt es für makellose Läufe",
                fontSize = 12.sp, color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkySectors.forEachIndexed { i, def ->
                    val unlocked = i == 0 || meta.sectorStars[i - 1] > 0
                    val stars = meta.sectorStars[i]
                    GxAppear(i) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            lerpColor(HikariCardBg, def.tint, if (unlocked) 0.09f else 0.02f),
                                            HikariCardBg,
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            def.tint.copy(alpha = if (unlocked) 0.45f else 0.10f),
                                            Color.White.copy(alpha = 0.05f),
                                        )
                                    ),
                                    RoundedCornerShape(20.dp),
                                )
                                .gxPressable(enabled = unlocked) { onPick(i) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                def.tint.copy(alpha = if (unlocked) 0.30f else 0.08f),
                                                def.tint.copy(alpha = if (unlocked) 0.10f else 0.03f),
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (unlocked) "${i + 1}" else "🔒",
                                    fontSize = 17.sp,
                                    color = if (unlocked) def.tint else HikariTextFaint,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    def.name,
                                    fontSize = 15.sp,
                                    color = if (unlocked) HikariText else HikariTextFaint,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (unlocked) def.mech else "Schaffe Sektor $i zum Freischalten",
                                    fontSize = 12.sp,
                                    color = if (unlocked) HikariTextMuted else HikariTextFaint,
                                    lineHeight = 16.sp,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            GxStarRow(stars)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ————— Hangar —————

private class SkyUpgradeDef(
    val name: String,
    val desc: String,
    val maxLevel: Int,
    val costs: List<Int>,
    val level: (SkyMeta) -> Int,
    val buy: (SkyMeta) -> Unit,
)

private val SkyUpgrades = listOf(
    SkyUpgradeDef(
        "Feuerrate", "Schneller schießen (3 Stufen)", 3, listOf(120, 240, 480),
        { it.upFire }, { it.buyFire() },
    ),
    SkyUpgradeDef(
        "Panzerung", "+1 Startleben (2 Stufen)", 2, listOf(200, 400),
        { it.upLives }, { it.buyLives() },
    ),
    SkyUpgradeDef(
        "Münz-Magnet", "Größerer Einzugsradius (3 Stufen)", 3, listOf(80, 160, 320),
        { it.upMagnet }, { it.buyMagnet() },
    ),
)

private val SkySkinNames = listOf("Komet", "Falke", "Phantom")
private val SkySkinCosts = listOf(0, 150, 300)
private val SkySkinColors = listOf(HikariAmber, Color(0xFF22D3EE), Color(0xFFA78BFA))

@Composable
private fun SkyHangarScreen(meta: SkyMeta, state: SkyState, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkyAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Hangar", SkyAccent, onBack)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                Text("Rüste dein Schiff dauerhaft auf", fontSize = 12.sp, color = HikariTextMuted)
                SkyCoinPill(meta.coins)
            }
            Spacer(Modifier.height(14.dp))

            Text(
                "UPGRADES", fontSize = 11.sp, color = HikariTextFaint, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkyUpgrades.forEachIndexed { idx, up ->
                    val lvl = up.level(meta)
                    val maxed = lvl >= up.maxLevel
                    val cost = if (maxed) 0 else up.costs[lvl]
                    val affordable = meta.coins >= cost
                    GxAppear(idx) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(HikariCardBg)
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GxProgressRing(frac = lvl.toFloat() / up.maxLevel, accent = SkyAccent, size = 46.dp) {
                                Text(
                                    "$lvl/${up.maxLevel}",
                                    fontSize = 11.sp, color = HikariText, fontWeight = FontWeight.Black,
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(up.name, fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(up.desc, fontSize = 11.sp, color = HikariTextMuted, lineHeight = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            if (maxed) {
                                Text(
                                    "MAX",
                                    fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SkyAccent)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (affordable)
                                                Brush.horizontalGradient(listOf(HikariAmber, Color(0xFFFDE68A)))
                                            else
                                                Brush.horizontalGradient(listOf(HikariSurfaceHigh, HikariSurfaceHigh))
                                        )
                                        .gxPressable(enabled = affordable) {
                                            if (meta.spendCoins(cost)) {
                                                up.buy(meta)
                                                state.award("shop1")
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "🪙 $cost",
                                        color = if (affordable) Color.Black else HikariTextFaint,
                                        fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "SCHIFFE", fontSize = 11.sp, color = HikariTextFaint, fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            GxAppear(3) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(3) { i ->
                        val unlocked = meta.skinUnlocked[i]
                        val selected = meta.skin == i
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) lerpColor(HikariCardBg, SkySkinColors[i], 0.08f)
                                    else HikariCardBg
                                )
                                .border(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) SkySkinColors[i].copy(alpha = 0.8f)
                                    else Color.White.copy(alpha = 0.06f),
                                    RoundedCornerShape(18.dp),
                                )
                                .gxPressable {
                                    if (unlocked) meta.selectSkin(i)
                                    else if (meta.spendCoins(SkySkinCosts[i])) {
                                        meta.unlockSkin(i)
                                        meta.selectSkin(i)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Canvas(Modifier.size(56.dp)) {
                                drawSkyShip(size.width / 2f, size.height / 2f + 6f, size.width / 44f, i, if (unlocked) 1f else 0.35f, 0f, false)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                SkySkinNames[i],
                                fontSize = 12.sp,
                                color = if (unlocked) HikariText else HikariTextMuted,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when {
                                    selected -> "✓ Aktiv"
                                    unlocked -> "Wählen"
                                    else -> "🪙 ${SkySkinCosts[i]}"
                                },
                                fontSize = 11.sp,
                                color = when {
                                    selected -> SkySkinColors[i]
                                    unlocked -> HikariTextMuted
                                    else -> HikariAmber
                                },
                                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ————— Statistik —————

@Composable
private fun SkyStatsScreen(meta: SkyMeta, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkyAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Statistik", SkyAccent, onBack)
            Spacer(Modifier.height(6.dp))

            val acc = if (meta.statShots > 0) (meta.statHits * 100 / meta.statShots) else 0
            val pt = meta.statPlaytime
            val playtime = if (pt >= 3600) "${pt / 3600}h ${(pt % 3600) / 60}m" else "${pt / 60}m ${pt % 60}s"
            val tiles = listOf(
                "${meta.statGames}" to "Runden gespielt",
                "${meta.statBestWave}" to "Höchste Welle",
                "${meta.statKills}" to "Abschüsse gesamt",
                "${meta.statBosses}" to "Bosse besiegt",
                "$acc %" to "Genauigkeit",
                "${meta.statCoinsTotal}" to "Münzen gesammelt",
                playtime to "Spielzeit",
                "${meta.highscore}" to "Klassik-Rekord",
                "${meta.rushBestBosses}" to "Boss-Rush-Rekord",
                "${meta.sectorStars.sum()}/15" to "Kampagnen-Sterne",
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tiles.chunked(2).forEachIndexed { row, pair ->
                    GxAppear(row) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (value, label) ->
                                GxStatTile(value, label, SkyAccent, Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ————— Erfolge —————

@Composable
private fun SkyAchievementsScreenUi(meta: SkyMeta, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(SkyAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val unlockedCount = SkyAchList.count { meta.unlockedAchs[it.id] == true }
            GxHeader("Erfolge", SkyAccent, onBack, right = {
                Text(
                    "$unlockedCount/${SkyAchList.size}",
                    fontSize = 12.sp, color = SkyAccent, fontWeight = FontWeight.Black,
                )
            })
            Spacer(Modifier.height(6.dp))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkyAchList.forEachIndexed { i, ach ->
                    val unlocked = meta.unlockedAchs[ach.id] == true
                    GxAppear(i.coerceAtMost(6)) {
                        GxAchRow(ach.emoji, ach.title, ach.desc, SkyAccent, unlocked)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ————— Einstellungen —————

@Composable
private fun SkySettingsSheet(meta: SkyMeta, onClose: () -> Unit) {
    GxSheet("Optionen", SkyAccent, onClose) {
        GxToggle("Vibration", "Haptisches Feedback bei Treffern", SkyAccent, meta.haptics) { meta.saveHaptics(it) }
        GxToggle("Weniger Effekte", "Reduziert Partikel für flüssigeres Spiel", SkyAccent, meta.reducedFx) { meta.saveReducedFx(it) }
        Spacer(Modifier.height(10.dp))
        Text("Steuerungs-Empfindlichkeit", fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        GxSegmented(
            options = listOf("Präzise", "Normal", "Flott"),
            selected = meta.sensitivity,
            accent = SkyAccent,
            modifier = Modifier.fillMaxWidth(),
        ) { meta.saveSensitivity(it) }
        Spacer(Modifier.height(18.dp))
        GxPrimaryButton("Fertig", SkyAccent, Modifier.fillMaxWidth(), onClick = onClose)
    }
}

// ————— Hilfe-Overlay —————

@Composable
private fun SkyHelpOverlay(onDismiss: () -> Unit) {
    GxSheet("So funktioniert's", SkyAccent, onDismiss) {
        listOf(
            "👆" to "Wische, um dein Schiff zu steuern — es feuert automatisch",
            "💠" to "D = Doppelschuss · T = Dreifach-Fächer",
            "🛡️" to "S = Schild (blockt genau einen Treffer)",
            "💣" to "B = Bombe (räumt den Bildschirm leer)",
            "❤️" to "Herz = Extra-Leben",
            "🪙" to "Münzen sammeln → im Hangar Upgrades kaufen",
            "🔥" to "Schnelle Kills hintereinander = Combo bis ×5",
        ).forEach { (emoji, text) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 17.sp)
                Spacer(Modifier.width(12.dp))
                Text(text, fontSize = 13.sp, color = HikariText, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        GxPrimaryButton("Los geht's!", SkyAccent, Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

// ————— Spiel-Ansicht —————

@Composable
private fun SkyGameView(state: SkyState, meta: SkyMeta, onMenu: () -> Unit) {
    val den = state.den
    val textMeasurer = rememberTextMeasurer()
    var showRestartConfirm by remember(state.runId) { mutableStateOf(false) }
    var showHelp by remember(state.runId) { mutableStateOf(!meta.helpSeen) }

    // Erststart: Hilfe blockiert das Spiel, bis sie weggeklickt ist
    LaunchedEffect(showHelp) {
        if (showHelp) state.paused = true
    }

    // Game-Loop: läuft solange die Ansicht sichtbar ist
    LaunchedEffect(state.runId) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.032f)
                    if (!state.paused) {
                        if (state.countdown > 0f) {
                            state.countdown = (state.countdown - dt).coerceAtLeast(0f)
                        } else {
                            state.update(dt)
                        }
                    }
                }
                last = now
                state.frame++
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        // Header: Pause-Chip, Titel, Score-Pille
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            GxIconChip(
                if (state.gameOver || state.victory) "←" else "❚❚",
                size = 38.dp,
            ) {
                if (state.gameOver || state.victory) onMenu()
                else state.pauseGame()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sky Strike", fontSize = 16.sp, color = SkyAccent, fontWeight = FontWeight.Black)
                Text(
                    when (state.mode) {
                        SkyMode.CLASSIC -> "Klassisch"
                        SkyMode.BOSS_RUSH -> "Boss-Rush"
                        SkyMode.CAMPAIGN -> SkySectors[state.sector].name
                    },
                    fontSize = 10.sp, color = HikariTextFaint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val shownScore by animateIntAsState(targetValue = state.score, animationSpec = tween(250))
                GxHudPill("PKT", "$shownScore")
                Spacer(Modifier.height(2.dp))
                Text(
                    when (state.mode) {
                        SkyMode.CLASSIC ->
                            if (meta.highscore > 0 && state.score < meta.highscore)
                                "Noch ${meta.highscore - state.score} bis Rekord"
                            else "Best: ${meta.highscore}"
                        SkyMode.BOSS_RUSH -> "Best: ${meta.rushBestBosses} Bosse"
                        SkyMode.CAMPAIGN -> "Welle ${state.waveInSector}/5"
                    },
                    fontSize = 10.sp, color = HikariTextMuted,
                )
            }
        }

        // Leben / Combo / Welle
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            SkyHeartsRow(lives = state.lives, maxLives = state.maxLives)
            if (state.comboMult >= 2) {
                val comboColor = when (state.comboMult) {
                    2 -> HikariText
                    3 -> HikariAmber
                    4 -> Color(0xFFFB923C)
                    else -> HikariDanger
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GxHudPill("COMBO", "×${state.comboMult}", accent = comboColor)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(HikariSurfaceHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((state.comboTimer / 1.5f).coerceIn(0f, 1f))
                                .background(comboColor),
                        )
                    }
                }
            }
            GxHudPill(
                when (state.mode) {
                    SkyMode.CLASSIC -> "WELLE"
                    SkyMode.BOSS_RUSH -> "BOSS"
                    SkyMode.CAMPAIGN -> "SEKTOR"
                },
                when (state.mode) {
                    SkyMode.CLASSIC -> "${state.wave}"
                    SkyMode.BOSS_RUSH -> "${state.rushBosses + 1}"
                    SkyMode.CAMPAIGN -> "${state.sector + 1}"
                },
            )
        }

        // Aktive Power-ups mit Restzeit-Ringen
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp).height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.doubleTimer > 0f) {
                GxProgressRing(state.doubleTimer / 12f, Color(0xFF22D3EE), size = 26.dp, stroke = 2.5.dp) {
                    Text("D", fontSize = 10.sp, color = Color(0xFF22D3EE), fontWeight = FontWeight.Black)
                }
            }
            if (state.tripleTimer > 0f) {
                GxProgressRing(state.tripleTimer / 12f, HikariAmber, size = 26.dp, stroke = 2.5.dp) {
                    Text("T", fontSize = 10.sp, color = HikariAmber, fontWeight = FontWeight.Black)
                }
            }
            if (state.shield) {
                GxProgressRing(1f, Color(0xFF4ADE80), size = 26.dp, stroke = 2.5.dp) {
                    Text("S", fontSize = 10.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Black)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { state.resize(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(state.runId) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            if (!state.gameOver && !state.victory && !state.paused) {
                                state.shipX = (state.shipX + amount.x * meta.sensFactor())
                                    .coerceIn(20f * den, (state.w - 20f * den).coerceAtLeast(20f * den))
                            }
                        }
                    }
            ) {
                state.frame // Snapshot-Read: löst Neuzeichnen pro Frame aus

                // Sektor-Färbung (Kampagne)
                val sectorDef = if (state.mode == SkyMode.CAMPAIGN) SkySectors[state.sector] else null
                if (sectorDef != null) {
                    drawRect(sectorDef.tint.copy(alpha = 0.05f))
                }
                val starColor = sectorDef?.starColor ?: Color.White

                // Sterne (2 Parallax-Ebenen)
                for (s in state.stars) {
                    drawCircle(starColor.copy(alpha = s.alpha), s.radius, Offset(s.x, s.y))
                }

                // Münzen
                for (c in state.coins) {
                    drawCircle(HikariAmber, 6f * den, Offset(c.x, c.y))
                    drawCircle(Color(0xFF92650B), 6f * den, Offset(c.x, c.y), style = Stroke(1.5f * den))
                    drawCircle(Color.White.copy(alpha = 0.6f), 1.5f * den, Offset(c.x - 2f * den, c.y - 2f * den))
                }

                // Power-ups
                for (p in state.powerUps) {
                    val col = when (p.kind) {
                        0 -> Color(0xFF22D3EE)
                        1 -> Color(0xFF4ADE80)
                        2 -> Color(0xFF4ADE80)
                        3 -> HikariAmber
                        else -> HikariDanger
                    }
                    val label = when (p.kind) { 0 -> "D"; 1 -> "+"; 2 -> "S"; 3 -> "T"; else -> "B" }
                    drawCircle(HikariSurfaceHigh, 13f * den, Offset(p.x, p.y))
                    drawCircle(col, 13f * den, Offset(p.x, p.y), style = Stroke(2f * den))
                    val res = textMeasurer.measure(
                        AnnotatedString(label),
                        TextStyle(color = col, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                }

                // Asteroiden
                for (a in state.asteroids) {
                    drawCircle(Color(0xFF6B7280), a.radius, Offset(a.x, a.y))
                    drawCircle(Color(0xFF4B5563), a.radius, Offset(a.x, a.y), style = Stroke(2.5f * den))
                    drawCircle(Color(0xFF4B5563), a.radius * 0.28f, Offset(a.x - a.radius * 0.3f, a.y - a.radius * 0.2f))
                    drawCircle(Color(0xFF4B5563), a.radius * 0.18f, Offset(a.x + a.radius * 0.35f, a.y + a.radius * 0.3f))
                }

                // Gegner-Projektile
                for (b in state.enemyBullets) {
                    drawCircle(HikariDanger.copy(alpha = 0.3f), 6f * den, Offset(b.x, b.y))
                    drawCircle(HikariDanger, 3.5f * den, Offset(b.x, b.y))
                }

                // Gegner
                for (e in state.enemies) {
                    val base = state.enemyColor(e.type)
                    val dmg = 1f - e.hp.toFloat() / e.maxHp
                    var col = lerpColor(base, Color.Black, dmg * 0.45f)
                    if (e.hitFlash > 0f) col = lerpColor(col, Color.White, e.hitFlash.coerceIn(0f, 1f) * 0.8f)
                    when (e.type) {
                        0, 5 -> { // klein / Mini: Dreieck, Spitze nach unten
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
                        3 -> { // Schütze: Dreieck mit Kanone
                            val path = Path().apply {
                                moveTo(e.x, e.y + e.radius * 0.4f)
                                lineTo(e.x - e.radius, e.y - e.radius)
                                lineTo(e.x + e.radius, e.y - e.radius)
                                close()
                            }
                            drawPath(path, col)
                            drawRoundRect(
                                lerpColor(col, Color.Black, 0.4f),
                                Offset(e.x - 2.5f * den, e.y + e.radius * 0.2f),
                                Size(5f * den, e.radius * 0.8f),
                                CornerRadius(2f * den, 2f * den),
                            )
                        }
                        4 -> { // Splitter: Doppel-Raute
                            val path = Path().apply {
                                moveTo(e.x, e.y - e.radius)
                                lineTo(e.x + e.radius, e.y)
                                lineTo(e.x, e.y + e.radius)
                                lineTo(e.x - e.radius, e.y)
                                close()
                            }
                            drawPath(path, col)
                            val inner = Path().apply {
                                moveTo(e.x, e.y - e.radius * 0.5f)
                                lineTo(e.x + e.radius * 0.5f, e.y)
                                lineTo(e.x, e.y + e.radius * 0.5f)
                                lineTo(e.x - e.radius * 0.5f, e.y)
                                close()
                            }
                            drawPath(inner, Color.Black.copy(alpha = 0.35f), style = Stroke(2f * den))
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

                // Bosse
                state.bosses.forEachIndexed { idx, boss ->
                    drawSkyBoss(boss, den)
                    // HP-Balken oben
                    val barW = size.width * 0.7f
                    val barH = 8f * den
                    val barX = (size.width - barW) / 2f
                    val barY = 10f * den + idx * 16f * den
                    drawRoundRect(
                        HikariSurfaceHigh,
                        Offset(barX, barY), Size(barW, barH),
                        CornerRadius(barH / 2f, barH / 2f),
                    )
                    val frac = (boss.hp.toFloat() / boss.maxHp).coerceIn(0f, 1f)
                    if (frac > 0f) {
                        drawRoundRect(
                            lerpColor(HikariDanger, HikariAmber, frac),
                            Offset(barX, barY), Size(barW * frac, barH),
                            CornerRadius(barH / 2f, barH / 2f),
                        )
                    }
                }

                // Eigene Projektile
                for (b in state.bullets) {
                    drawCircle(Color(0xFFFDE047).copy(alpha = 0.25f), 7f * den, Offset(b.x, b.y))
                    drawRoundRect(
                        Color(0xFFFDE047),
                        Offset(b.x - 2f * den, b.y - 9f * den),
                        Size(4f * den, 18f * den),
                        CornerRadius(2f * den, 2f * den),
                    )
                }

                // Partikel
                for (p in state.particles) {
                    val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(p.color.copy(alpha = a), p.radius * (0.5f + a * 0.5f), Offset(p.x, p.y))
                }

                // Schockwellen
                for (s in state.shockwaves) {
                    val a = (s.life / s.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        HikariAmber.copy(alpha = a * 0.7f),
                        s.r, Offset(s.x, s.y),
                        style = Stroke(width = 5f * den * a + 1f),
                    )
                }

                // Durchbruch-Hinweise unten
                for (br in state.breaches) {
                    val a = (br.life / 1.2f).coerceIn(0f, 1f)
                    val res = textMeasurer.measure(
                        AnnotatedString("▲"),
                        TextStyle(color = HikariDanger.copy(alpha = a), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    )
                    drawText(res, topLeft = Offset(br.x - res.size.width / 2f, size.height - res.size.height - 4f * den))
                }

                // Spieler-Schiff
                if (!state.gameOver && state.w > 0f) {
                    val blink = state.invuln > 0f && (state.time * 12f).toInt() % 2 == 0
                    val shipAlpha = if (blink) 0.25f else 1f
                    drawSkyShip(state.shipX, state.shipY, den, meta.skin, shipAlpha, state.time, state.shield)
                }

                // Treffer-Vignette
                if (state.hitVignette > 0f) {
                    val v = state.hitVignette
                    drawRect(HikariDanger.copy(alpha = v * 0.10f))
                    drawRect(
                        HikariDanger.copy(alpha = v * 0.45f),
                        style = Stroke(width = 18f * den * v),
                    )
                }
            }

            // Wellen-/Boss-Banner
            if (state.waveBanner > 0f && !state.gameOver && !state.victory) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        state.bannerText,
                        fontSize = 30.sp,
                        color = HikariPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { alpha = state.waveBanner.coerceAtMost(1f) },
                    )
                }
            }

            // Live-Rekord-Banner
            if (state.recordBanner > 0f) {
                Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "🏆 Neuer Rekord!",
                        fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .graphicsLayer { alpha = state.recordBanner.coerceAtMost(1f) }
                            .clip(RoundedCornerShape(999.dp))
                            .background(HikariPrimary)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Erfolgs-Banner
            state.achBanner?.let { ach ->
                Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                    Row(
                        Modifier
                            .graphicsLayer { alpha = state.achBannerT.coerceAtMost(1f) }
                            .clip(RoundedCornerShape(999.dp))
                            .background(HikariCardBg)
                            .border(1.dp, HikariPrimary.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ach.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Erfolg: ${ach.title}", fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Countdown nach Start/Pause
            if (state.countdown > 0f && !state.paused && !state.gameOver && !state.victory) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "${ceil(state.countdown).toInt()}",
                        fontSize = 64.sp, color = HikariPrimary, fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Pause
            if (state.paused && !showHelp) {
                Box(Modifier.fillMaxSize().background(Color(0xD90A0A0A)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .padding(horizontal = 36.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF232326))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("❚❚", fontSize = 20.sp, color = SkyAccent, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(6.dp))
                        Text("Pause", fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(18.dp))
                        GxPrimaryButton("Weiter", SkyAccent, Modifier.fillMaxWidth()) { state.resumeGame() }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GxGhostButton("Neu starten", Modifier.weight(1f)) { showRestartConfirm = true }
                            GxGhostButton("Zum Menü", Modifier.weight(1f), onClick = onMenu)
                        }
                        Spacer(Modifier.height(12.dp))
                        GxToggle("Vibration", null, SkyAccent, meta.haptics) { meta.saveHaptics(it) }
                    }
                }
            }

            // Neustart-Bestätigung
            if (showRestartConfirm) {
                GxConfirmDialog(
                    title = "Neu starten?",
                    text = "Der aktuelle Lauf geht verloren.",
                    confirmLabel = "Neu starten",
                    accent = SkyAccent,
                    onConfirm = {
                        showRestartConfirm = false
                        state.finishRun()
                        state.startRun(state.mode, state.sector)
                    },
                    onDismiss = { showRestartConfirm = false },
                )
            }

            // Hilfe beim ersten Start
            if (showHelp) {
                SkyHelpOverlay(onDismiss = {
                    meta.setHelpSeen()
                    showHelp = false
                    state.paused = false
                })
            }

            // Sektor geschafft (Kampagne)
            if (state.victory) {
                Box(Modifier.fillMaxSize().background(Color(0xE60A0A0A)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF232326))
                            .border(1.dp, SkyAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Sektor geschafft!", fontSize = 24.sp, color = SkyAccent, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(12.dp))
                        GxStarRow(state.victoryStars, size = 34.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${gxAnimatedCount(state.score)}",
                            fontSize = 34.sp, color = HikariText, fontWeight = FontWeight.Black,
                        )
                        Text("Punkte", fontSize = 12.sp, color = HikariTextMuted)
                        Spacer(Modifier.height(20.dp))
                        if (state.sector < 4) {
                            GxPrimaryButton("Nächster Sektor", SkyAccent, Modifier.fillMaxWidth()) {
                                state.startRun(SkyMode.CAMPAIGN, state.sector + 1)
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        GxGhostButton("Zum Menü", Modifier.fillMaxWidth(), onClick = onMenu)
                    }
                }
            }

            // Game Over mit Runden-Statistik
            if (state.gameOver) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xE60A0A0A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .padding(horizontal = 28.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF232326))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Game Over", fontSize = 25.sp, color = HikariText, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${gxAnimatedCount(state.score)}",
                            fontSize = 38.sp, color = SkyAccent, fontWeight = FontWeight.Black,
                        )
                        when (state.mode) {
                            SkyMode.CLASSIC -> {
                                if (state.newRecord) {
                                    Text("🏆 Neuer Rekord!", fontSize = 14.sp, color = HikariAmber, fontWeight = FontWeight.Black)
                                } else {
                                    Text("Rekord: ${meta.highscore}", fontSize = 12.sp, color = HikariTextMuted)
                                }
                            }
                            SkyMode.BOSS_RUSH -> Text(
                                "${state.rushBosses} Bosse besiegt · Best: ${meta.rushBestBosses}",
                                fontSize = 12.sp, color = HikariTextMuted,
                            )
                            SkyMode.CAMPAIGN -> Text(
                                "Sektor ${state.sector + 1} · Welle ${state.waveInSector}/5",
                                fontSize = 12.sp, color = HikariTextMuted,
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        val acc = if (state.runShots > 0) state.runHits * 100 / state.runShots else 0
                        val secs = state.runTime.toInt()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GxStatTile("${state.runKills}", "Abschüsse", SkyAccent, Modifier.weight(1f))
                            GxStatTile("$acc %", "Genauigkeit", SkyAccent, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GxStatTile("×${state.bestComboRun}", "Beste Combo", SkyAccent, Modifier.weight(1f))
                            GxStatTile("+${state.runCoins}", "Münzen", HikariAmber, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Dauer ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}",
                            fontSize = 11.sp, color = HikariTextFaint,
                        )

                        Spacer(Modifier.height(16.dp))
                        GxPrimaryButton("Nochmal", SkyAccent, Modifier.fillMaxWidth()) {
                            state.startRun(state.mode, state.sector)
                        }
                        Spacer(Modifier.height(10.dp))
                        GxGhostButton("Zum Menü", Modifier.fillMaxWidth(), onClick = onMenu)
                    }
                }
            }
        }
    }

    // System-Back-Handling passiert im Router
}

@Composable
private fun SkyHeartsRow(lives: Int, maxLives: Int) {
    // Bei nur 1 Leben pulsieren die Herzen als Warnung
    val pulse = if (lives == 1) {
        val inf = rememberInfiniteTransition()
        inf.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        ).value
    } else 1f
    Row {
        repeat(maxLives.coerceAtMost(6)) { i ->
            Text(
                "♥",
                fontSize = 14.sp,
                color = if (i < lives) HikariDanger else HikariTextFaint,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .scale(if (i < lives) pulse else 1f),
            )
        }
    }
}

// ————— Zeichen-Helfer —————

private fun DrawScope.drawSkyShip(
    x: Float,
    y: Float,
    den: Float,
    skin: Int,
    alpha: Float,
    time: Float,
    shield: Boolean,
) {
    val color = SkySkinColors.getOrElse(skin) { HikariAmber }
    val flicker = 0.7f + 0.3f * sin(time * 30f)
    drawCircle(
        color.copy(alpha = 0.25f * flicker * alpha),
        (10f + 4f * flicker) * den,
        Offset(x, y + 18f * den),
    )
    val ship = when (skin) {
        1 -> Path().apply { // Falke: breite Delta-Schwinge
            moveTo(x, y - 20f * den)
            lineTo(x - 20f * den, y + 12f * den)
            lineTo(x - 6f * den, y + 6f * den)
            lineTo(x, y + 12f * den)
            lineTo(x + 6f * den, y + 6f * den)
            lineTo(x + 20f * den, y + 12f * den)
            close()
        }
        2 -> Path().apply { // Phantom: schlank mit Doppel-Finne
            moveTo(x, y - 26f * den)
            lineTo(x - 9f * den, y + 8f * den)
            lineTo(x - 13f * den, y + 16f * den)
            lineTo(x - 4f * den, y + 10f * den)
            lineTo(x, y + 6f * den)
            lineTo(x + 4f * den, y + 10f * den)
            lineTo(x + 13f * den, y + 16f * den)
            lineTo(x + 9f * den, y + 8f * den)
            close()
        }
        else -> Path().apply { // Komet: Original
            moveTo(x, y - 22f * den)
            lineTo(x - 16f * den, y + 14f * den)
            lineTo(x, y + 7f * den)
            lineTo(x + 16f * den, y + 14f * den)
            close()
        }
    }
    drawPath(ship, color.copy(alpha = alpha))
    drawCircle(Color.White.copy(alpha = 0.85f * alpha), 3.5f * den, Offset(x, y - 6f * den))
    if (shield) {
        val pulse = 0.8f + 0.2f * sin(time * 6f)
        drawCircle(
            Color(0xFF4ADE80).copy(alpha = 0.55f * pulse * alpha),
            26f * den,
            Offset(x, y),
            style = Stroke(width = 2.5f * den),
        )
    }
}

private fun DrawScope.drawSkyBoss(boss: SkyBoss, den: Float) {
    val phase2 = boss.hp <= boss.maxHp / 2
    var hull = lerpColor(Color(0xFF7F1D1D), HikariDanger, if (phase2) 0.55f else 0.25f)
    if (boss.hitFlash > 0f) hull = lerpColor(hull, Color.White, boss.hitFlash.coerceIn(0f, 1f) * 0.7f)
    val r = boss.radius
    // Rumpf
    drawRoundRect(
        hull,
        Offset(boss.x - r * 1.3f, boss.y - r * 0.55f),
        Size(r * 2.6f, r * 1.1f),
        CornerRadius(r * 0.35f, r * 0.35f),
    )
    // Flügel
    val wingL = Path().apply {
        moveTo(boss.x - r * 1.3f, boss.y)
        lineTo(boss.x - r * 1.9f, boss.y - r * 0.5f)
        lineTo(boss.x - r * 1.9f, boss.y + r * 0.5f)
        close()
    }
    val wingR = Path().apply {
        moveTo(boss.x + r * 1.3f, boss.y)
        lineTo(boss.x + r * 1.9f, boss.y - r * 0.5f)
        lineTo(boss.x + r * 1.9f, boss.y + r * 0.5f)
        close()
    }
    drawPath(wingL, lerpColor(hull, Color.Black, 0.3f))
    drawPath(wingR, lerpColor(hull, Color.Black, 0.3f))
    // Kern: glüht in Phase 2
    drawCircle(
        if (phase2) HikariAmber else Color(0xFF450A0A),
        r * 0.35f,
        Offset(boss.x, boss.y),
    )
    drawCircle(
        Color.Black.copy(alpha = 0.4f),
        r * 0.35f,
        Offset(boss.x, boss.y),
        style = Stroke(width = 2f * den),
    )
}
