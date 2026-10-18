package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay

// Akzentfarbe des Spiels (grün — passend zur Wassermelone)
private val MergeAccent = Color(0xFF4ADE80)

// Fruchtkette Stufe 0..9
private val MergeEmoji = listOf("🍒", "🍓", "🍇", "🍊", "🍎", "🍐", "🍑", "🍍", "🍈", "🍉")
private val MergeNames = listOf(
    "Kirsche", "Erdbeere", "Traube", "Orange", "Apfel",
    "Birne", "Pfirsich", "Ananas", "Melone", "Wassermelone",
)
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

// Farb-Helfer fürs Shading (Licht von oben-links, dunkle Rims)
private fun mergeTint(c: Color, f: Float): Color = lerp(c, Color.White, f)
private fun mergeShade(c: Color, f: Float): Color = Color(c.red * f, c.green * f, c.blue * f)

// Hand-Zustände: -1 = leer/Cooldown, -2 = Regenbogen-Frucht, 0..4 = Fruchtstufe
private const val MergeHandNone = -1
private const val MergeHandRainbow = -2

private enum class MergeMode(val label: String) {
    CLASSIC("Klassisch"),
    ZEN("Zen"),
    CHALLENGE("Herausforderung"),
}

private enum class MergeScreenId { MENU, GAME, LEVELS, STATS, ACH }

private enum class MergeGeom { NORMAL, NARROW, WIDE }

private enum class MergeGoalType { FRUIT_DROPS, MERGES_TIME, SCORE_CLEAN, SCORE_DROPS }

private class MergeLevelDef(
    val geom: MergeGeom,
    val pins: List<Triple<Float, Float, Float>>, // fx, fy (Anteil im Behälter), Radius als Anteil der Breite
    val type: MergeGoalType,
    val target: Int,
    val limit: Int,
    val title: String,
)

private val MergeLevels = listOf(
    MergeLevelDef(MergeGeom.NORMAL, emptyList(), MergeGoalType.FRUIT_DROPS, 3, 18, "Aufwärmen"),
    MergeLevelDef(MergeGeom.NORMAL, emptyList(), MergeGoalType.MERGES_TIME, 15, 60, "Im Takt"),
    MergeLevelDef(MergeGeom.NARROW, emptyList(), MergeGoalType.FRUIT_DROPS, 4, 25, "Enge Röhre"),
    MergeLevelDef(MergeGeom.NORMAL, listOf(Triple(0.5f, 0.45f, 0.045f)), MergeGoalType.SCORE_DROPS, 300, 30, "Der Pin"),
    MergeLevelDef(MergeGeom.NORMAL, emptyList(), MergeGoalType.SCORE_CLEAN, 500, 0, "Pur"),
    MergeLevelDef(MergeGeom.WIDE, emptyList(), MergeGoalType.FRUIT_DROPS, 5, 30, "Flachwasser"),
    MergeLevelDef(MergeGeom.NARROW, emptyList(), MergeGoalType.MERGES_TIME, 20, 75, "Schneller Schacht"),
    MergeLevelDef(MergeGeom.NORMAL, listOf(Triple(0.3f, 0.4f, 0.04f), Triple(0.7f, 0.4f, 0.04f)), MergeGoalType.FRUIT_DROPS, 6, 40, "Doppel-Pin"),
    MergeLevelDef(MergeGeom.NORMAL, emptyList(), MergeGoalType.SCORE_DROPS, 800, 45, "Punktejagd"),
    MergeLevelDef(MergeGeom.WIDE, emptyList(), MergeGoalType.MERGES_TIME, 25, 90, "Breite Bühne"),
    MergeLevelDef(MergeGeom.NARROW, emptyList(), MergeGoalType.FRUIT_DROPS, 7, 45, "Ananas-Turm"),
    MergeLevelDef(MergeGeom.NORMAL, listOf(Triple(0.25f, 0.35f, 0.038f), Triple(0.5f, 0.55f, 0.038f), Triple(0.75f, 0.35f, 0.038f)), MergeGoalType.SCORE_CLEAN, 700, 0, "Flipper"),
    MergeLevelDef(MergeGeom.NORMAL, emptyList(), MergeGoalType.FRUIT_DROPS, 8, 55, "Melonenreif"),
    MergeLevelDef(MergeGeom.NARROW, emptyList(), MergeGoalType.SCORE_DROPS, 1200, 60, "Präzision"),
    MergeLevelDef(MergeGeom.NORMAL, listOf(Triple(0.35f, 0.4f, 0.042f), Triple(0.65f, 0.4f, 0.042f)), MergeGoalType.FRUIT_DROPS, 9, 70, "Meisterprüfung"),
)

// Erfolge (Bit-Index im Prefs-Bitmask) — alte Bits 0..9 unverändert, neue ab 10
private const val MergeAchMelone = 0
private const val MergeAchKette = 1
private const val MergeAchTausend = 2
private const val MergeAchRainbow = 3
private const val MergeAchRunden = 4
private const val MergeAchZen = 5
private const val MergeAchChal = 6
private const val MergeAchFeuerwerk = 7
private const val MergeAchPower = 8
private const val MergeAchSammler = 9
private const val MergeAchBombe = 10
private const val MergeAchTausch = 11
private const val MergeAchTurbo = 12

private class MergeAchievement(val bit: Int, val emoji: String, val title: String, val desc: String)

private val MergeAchievements = listOf(
    MergeAchievement(MergeAchMelone, "🍉", "Wassermelone", "Baue deine erste Wassermelone"),
    MergeAchievement(MergeAchKette, "⛓️", "Kettenmeister", "Erreiche eine 5er-Merge-Kette"),
    MergeAchievement(MergeAchTausend, "💯", "Tausender", "1000 Punkte in einer Klassik-Runde"),
    MergeAchievement(MergeAchRainbow, "🌈", "Regenbogen", "Verschmelze eine Regenbogen-Frucht"),
    MergeAchievement(MergeAchRunden, "🎮", "Stammspieler", "Spiele 10 Runden"),
    MergeAchievement(MergeAchZen, "🧘", "Zen-Geist", "10 Minuten im Zen-Modus"),
    MergeAchievement(MergeAchChal, "🗺️", "Wegbereiter", "Schaffe Herausforderungs-Level 5"),
    MergeAchievement(MergeAchFeuerwerk, "🎆", "Feuerwerk", "Zünde 🍉 + 🍉"),
    MergeAchievement(MergeAchPower, "⚡", "Werkzeugkasten", "Nutze Schütteln und Pop in einer Runde"),
    MergeAchievement(MergeAchSammler, "📚", "Sammler", "Entdecke alle 10 Früchte"),
    MergeAchievement(MergeAchBombe, "💣", "Großreinemachen", "Zünde die Bombe"),
    MergeAchievement(MergeAchTausch, "🔀", "Tauschgeschäft", "Nutze den Tausch"),
    MergeAchievement(MergeAchTurbo, "🚀", "Turbo-Kette", "Erreiche eine 8er-Merge-Kette"),
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
    var rainbow: Boolean = false,
    var angle: Float = 0f,    // Rotation (rad)
    var angVel: Float = 0f,   // Winkelgeschwindigkeit
    var sq: Float = 0f,       // Squash-Phase: >0 = breit/flach, federt zurück
    var sqv: Float = 0f,      // Squash-Geschwindigkeit (gedämpfte Feder)
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
    val shape: Int = 0,   // 0 = Kreis, 1 = Streifen, 2 = Stern
    var rot: Float = 0f,
    val rotV: Float = 0f,
    val drag: Float = 0f,
)

private class MergeTextPop(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float,
    val sizeMul: Float = 1f,
    val maxLife: Float = life,
    val vy: Float = 60f,
    val golden: Boolean = false,
)

private class MergePin(val x: Float, val y: Float, val r: Float)

// Shockwave-/Glow-Ring beim Merge
private class MergeRing(
    val x: Float,
    val y: Float,
    var life: Float,
    val maxLife: Float,
    val maxR: Float,
    val color: Color,
    val width: Float,
)

// Langsam aufsteigendes Hintergrund-Bokeh
private class MergeBokeh(var x: Float, var y: Float, val r: Float, val speed: Float, val alpha: Float)

private class MergeWorld {
    var w = 0f
    var h = 0f
    var initialized = false
    var scale = 1f
    val radii = FloatArray(10)
    val fruits = ArrayList<MergeFruit>()
    val sparks = ArrayList<MergeSpark>()
    val pops = ArrayList<MergeTextPop>()
    val rings = ArrayList<MergeRing>()
    val bokeh = ArrayList<MergeBokeh>()
    val pins = ArrayList<MergePin>()
    var left = 0f
    var right = 0f
    var bottom = 0f
    var top = 0f
    var limitY = 0f
    var hangY = 0f
    var aimX = 0f
    var current = MergeHandNone
    var cooldown = 0f
    var warn = 0f
    var time = 0f

    // Juice: weißer Flash, Kamera-Shake, Zeitlupe, Impact-Frames
    var flash = 0f
    var camShake = 0f
    var slowMo = 0f
    var freeze = 0
    var chainPop = 0f

    // Ketten-Combo
    var chain = 0
    var chainTimer = 0f
    var longestChain = 0

    // Power-ups
    var mergesForShake = 0
    var mergesForPop = 0
    var mergesForSwap = 0
    var mergesForBomb = 0
    var shakeCharges = 0
    var popCharges = 0
    var swapCharges = 0
    var bombCharges = 0
    var shakeVis = 0f
    var usedShake = false
    var usedPop = false
    var usedSwap = false
    var usedBomb = false

    // Runden-Zähler
    var mergesRound = 0
    var drops = 0
    var bestFruit = 0
    val milestones = BooleanArray(10)
    var fillLevel = 0f
    var graceTimer = -1f
}

// ————— Prefs-Helfer —————

private fun mergeLevelInfo(totalXp: Int): Triple<Int, Int, Int> {
    var lvl = 1
    var rest = totalXp
    var need = 150
    while (rest >= need) {
        rest -= need
        lvl++
        need = 150 + (lvl - 1) * 100
    }
    return Triple(lvl, rest, need)
}

private fun mergeLoadTop5(p: SharedPreferences): List<Pair<Int, Long>> =
    (p.getString("fruitmerge_top5", "") ?: "").split(";").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size == 2) {
            val s = parts[0].toIntOrNull()
            val d = parts[1].toLongOrNull()
            if (s != null && d != null) s to d else null
        } else null
    }

private fun mergeLoadStars(p: SharedPreferences): IntArray {
    val arr = IntArray(MergeLevels.size)
    (p.getString("fruitmerge_stars", "") ?: "").split(",").forEachIndexed { i, s ->
        if (i < arr.size) arr[i] = s.toIntOrNull() ?: 0
    }
    return arr
}

private fun mergeSaveStars(p: SharedPreferences, stars: IntArray) {
    p.edit().putString("fruitmerge_stars", stars.joinToString(",")).apply()
}

private fun mergeGoalText(d: MergeLevelDef): String = when (d.type) {
    MergeGoalType.FRUIT_DROPS -> "Baue ${MergeEmoji[d.target]} in max. ${d.limit} Drops"
    MergeGoalType.MERGES_TIME -> "${d.target} Merges in ${d.limit} s"
    MergeGoalType.SCORE_CLEAN -> "${d.target} Punkte ohne Power-ups"
    MergeGoalType.SCORE_DROPS -> "${d.target} Punkte in max. ${d.limit} Drops"
}

private fun mergeStarsFor(d: MergeLevelDef, drops: Int, time: Float): Int = when (d.type) {
    MergeGoalType.FRUIT_DROPS, MergeGoalType.SCORE_DROPS -> {
        val ratio = drops.toFloat() / d.limit.coerceAtLeast(1)
        if (ratio <= 0.6f) 3 else if (ratio <= 0.85f) 2 else 1
    }
    MergeGoalType.MERGES_TIME -> {
        val remain = (d.limit - time) / d.limit.coerceAtLeast(1)
        if (remain >= 0.4f) 3 else if (remain >= 0.15f) 2 else 1
    }
    MergeGoalType.SCORE_CLEAN -> if (time <= 90f) 3 else if (time <= 150f) 2 else 1
}

private fun mergeFmtTime(s: Int): String = "%d:%02d".format(s / 60, s % 60)

private fun mergeFmtDuration(secs: Long): String {
    val m = secs / 60
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

private fun mergeRollDrop(): Int =
    if (Random.nextFloat() < 0.04f) MergeHandRainbow else Random.nextInt(5)

// ————— Prozeduraler Mini-Synth via AudioTrack (keine Assets) —————
// Kurze generierte PCM-Samples (Sine/Chirp + Hüllkurve) werden auf einem
// Daemon-Thread in einen MODE_STREAM-AudioTrack geschrieben. Alles abgesichert;
// bei Fehlern bleibt das Spiel stumm, statt zu crashen.

private class MergeSound {
    private val sr = 22050
    @Volatile private var running = false
    @Volatile private var failed = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val queue = ArrayBlockingQueue<ShortArray>(24)

    private fun ensureStarted() {
        if (failed || track != null) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack(
                AudioManager.STREAM_MUSIC, sr,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, sr / 2), AudioTrack.MODE_STREAM,
            )
            t.play()
            track = t
            running = true
            val th = Thread {
                while (running) {
                    val s = try { queue.take() } catch (_: InterruptedException) { null }
                    if (s != null) try { track?.write(s, 0, s.size) } catch (_: Throwable) {}
                }
            }
            th.isDaemon = true
            thread = th
            th.start()
        } catch (t: Throwable) {
            failed = true
        }
    }

    private fun play(samples: ShortArray) {
        if (failed) return
        try {
            ensureStarted()
            if (track != null) queue.offer(samples)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        running = false
        queue.clear()
        try { thread?.interrupt() } catch (_: Throwable) {}
        try {
            track?.let {
                try { it.pause() } catch (_: Throwable) {}
                try { it.flush() } catch (_: Throwable) {}
                try { it.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        }
        track = null
        thread = null
    }

    private fun synth(dur: Float, vol: Float = 0.30f, wave: (Float) -> Double): ShortArray {
        val n = max(1, (sr * dur).toInt())
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sr
            out[i] = (wave(t).coerceIn(-1.0, 1.0) * vol * 32767.0).toInt().toShort()
        }
        return out
    }

    private fun concat(vararg parts: ShortArray): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }

    private fun note(freq: Double, dur: Float, vol: Float = 0.30f): ShortArray =
        synth(dur, vol) { t ->
            val env = exp(-t * 18.0)
            (sin(2 * PI * freq * t) * 0.72 + sin(2 * PI * freq * 2 * t) * 0.28) * env
        }

    // Drop-Thock: tiefer, schnell fallender Sine
    fun drop() = play(synth(0.09f, 0.22f) { t ->
        val f = 170.0 - t * 700.0
        sin(2 * PI * f * t) * exp(-t * 40.0)
    })

    // Merge-Pling: Pitch steigt mit Fruchtstufe
    fun merge(level: Int) = play(note(300.0 + level * 55.0, 0.17f, 0.30f))

    // Combo-Fanfare: Arpeggio, steigt mit der Kette
    fun fanfare(chain: Int) {
        val base = 420.0 + min(chain, 8) * 25.0
        play(concat(note(base, 0.10f, 0.20f), note(base * 1.25, 0.10f, 0.20f), note(base * 1.5, 0.16f, 0.25f)))
    }

    // Feuerwerk-/Bomben-Boom: tiefer Sine-Drop + Rauschen
    fun boom() = play(synth(0.5f, 0.38f) { t ->
        val env = exp(-t * 9.0)
        val noise = (Random.nextDouble() * 2.0 - 1.0) * exp(-t * 16.0) * 0.5
        (sin(2 * PI * (70.0 - t * 60.0) * t) * 0.9 + noise) * env
    })

    // Power-up-Aktivierung: schneller Up-Chirp
    fun power() = play(synth(0.14f, 0.26f) { t ->
        val f = 320.0 + t * 3200.0
        sin(2 * PI * f * t) * exp(-t * 14.0)
    })

    fun warn() = play(concat(note(196.0, 0.09f, 0.22f), note(196.0, 0.09f, 0.22f)))

    fun gameOver() = play(concat(note(392.0, 0.16f, 0.24f), note(311.0, 0.16f, 0.24f), note(233.0, 0.30f, 0.26f)))

    fun win() = play(concat(note(523.0, 0.11f, 0.24f), note(659.0, 0.11f, 0.24f), note(784.0, 0.11f, 0.24f), note(1046.0, 0.24f, 0.28f)))
}

// ————— Einstieg / Router —————

@Composable
fun FruitMergeGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(MergeScreenId.MENU) }
    var mode by remember {
        mutableStateOf(
            runCatching {
                MergeMode.valueOf(prefs.getString("fruitmerge_last_mode", MergeMode.CLASSIC.name) ?: MergeMode.CLASSIC.name)
            }.getOrDefault(MergeMode.CLASSIC)
        )
    }
    var levelIdx by remember { mutableStateOf(0) }
    var playKey by remember { mutableStateOf(0) }

    fun startGame(m: MergeMode, lvl: Int = 0) {
        mode = m
        levelIdx = lvl
        prefs.edit().putString("fruitmerge_last_mode", m.name).apply()
        playKey++
        screen = MergeScreenId.GAME
    }

    Crossfade(targetState = screen, animationSpec = tween(220), label = "mergeScreens") { s ->
        when (s) {
            MergeScreenId.MENU -> MergeMenuScreen(
                prefs = prefs,
                lastMode = mode,
                onBack = onBack,
                onPlay = { m -> if (m == MergeMode.CHALLENGE) screen = MergeScreenId.LEVELS else startGame(m) },
                onStats = { screen = MergeScreenId.STATS },
                onAchievements = { screen = MergeScreenId.ACH },
            )
            MergeScreenId.LEVELS -> MergeLevelSelectScreen(
                prefs = prefs,
                onBack = { screen = MergeScreenId.MENU },
                onPick = { idx -> startGame(MergeMode.CHALLENGE, idx) },
            )
            MergeScreenId.STATS -> MergeStatsScreen(prefs, onBack = { screen = MergeScreenId.MENU })
            MergeScreenId.ACH -> MergeAchievementsScreen(prefs, onBack = { screen = MergeScreenId.MENU })
            MergeScreenId.GAME -> key(playKey) {
                MergePlayScreen(
                    mode = mode,
                    levelIdx = levelIdx,
                    onMenu = { screen = MergeScreenId.MENU },
                    onNextLevel = { startGame(MergeMode.CHALLENGE, levelIdx + 1) },
                )
            }
        }
    }
}

// ————— Menü —————

@Composable
private fun MergeMenuScreen(
    prefs: SharedPreferences,
    lastMode: MergeMode,
    onBack: () -> Unit,
    onPlay: (MergeMode) -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val highscore = remember { prefs.getInt("fruitmerge_highscore", 0) }
    val xp = remember { prefs.getInt("fruitmerge_xp", 0) }
    val zenSecs = remember { prefs.getLong("fruitmerge_zen_secs", 0L) }
    val stars = remember { mergeLoadStars(prefs) }
    val starSum = stars.sum()
    val levelsDone = stars.count { it > 0 }
    val (lvl, xpIn, xpNeed) = mergeLevelInfo(xp)

    // Animierte Titel-Frucht: schwebt sanft über Glow
    val heroInf = rememberInfiniteTransition(label = "mergeHero")
    val heroFloat by heroInf.animateFloat(
        -7f, 7f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mergeHeroY",
    )
    val heroGlow by heroInf.animateFloat(
        0.10f, 0.22f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mergeHeroGlow",
    )

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(MergeAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Fruit Merge", MergeAccent, onBack, right = {
                GxIconChip("?") { showHelp = true }
            })

            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(MergeAccent.copy(alpha = heroGlow), Color.Transparent),
                            center = center,
                            radius = size.minDimension / 2f,
                        )
                    )
                }
                Text(
                    "🍉",
                    fontSize = 50.sp,
                    modifier = Modifier.graphicsLayer { translationY = heroFloat.dp.toPx() },
                )
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                GxAppear(0) {
                    GxLevelCard(lvl, "$xpIn / $xpNeed XP", xpIn.toFloat() / xpNeed.coerceAtLeast(1), MergeAccent)
                }
                Spacer(Modifier.height(14.dp))
                GxAppear(1) {
                    GxModeCard(
                        emoji = "🍉", title = "Klassisch",
                        subtitle = "Stapeln, mergen, überleben — bis zur Wassermelone.",
                        accent = MergeAccent,
                        highlighted = lastMode == MergeMode.CLASSIC,
                        best = if (highscore > 0) "Rekord: $highscore" else null,
                        onClick = { onPlay(MergeMode.CLASSIC) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                GxAppear(2) {
                    GxModeCard(
                        emoji = "🧘", title = "Zen",
                        subtitle = "Kein Game Over, kein Druck. Einfach mergen und entspannen.",
                        accent = MergeAccent,
                        highlighted = lastMode == MergeMode.ZEN,
                        best = if (zenSecs > 0L) "Gespielt: ${mergeFmtDuration(zenSecs)}" else null,
                        onClick = { onPlay(MergeMode.ZEN) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                GxAppear(3) {
                    GxModeCard(
                        emoji = "🗺️", title = "Herausforderung",
                        subtitle = "15 Level mit eigenen Behältern, Pins und Zielen.",
                        accent = MergeAccent,
                        highlighted = lastMode == MergeMode.CHALLENGE,
                        best = "★ $starSum/45 · Level $levelsDone/15",
                        onClick = { onPlay(MergeMode.CHALLENGE) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                GxAppear(4) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                        GxSmallAction("🏆", "Erfolge", Modifier.weight(1f), onAchievements)
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f)) { showSettings = true }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (showSettings) {
            MergeSettingsSheet(prefs = prefs, onClose = { showSettings = false })
        }
        if (showHelp) {
            GxSheet("So geht's", MergeAccent, onClose = { showHelp = false }) {
                for (l in listOf(
                    "🍉 Zwei gleiche Früchte verschmelzen zur nächsten Stufe.",
                    "⛓️ Schnelle Folge-Merges bilden Ketten: ×2 ab Kette 2, ×3 ab Kette 4.",
                    "🌀 schüttelt den Behälter, 💥 entfernt die kleinste Frucht.",
                    "🔀 tauscht die Hand-Frucht mit der nächsten, 💣 sprengt die drei kleinsten.",
                    "🌈 verschmilzt mit der ersten berührten Frucht.",
                    "⚠️ Über der Linie stapeln = Game Over (außer im Zen-Modus).",
                )) {
                    Text(l, fontSize = 13.sp, color = HikariText, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(14.dp))
                GxPrimaryButton("Alles klar", MergeAccent, Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

@Composable
private fun MergeSettingsSheet(prefs: SharedPreferences, onClose: () -> Unit) {
    var haptics by remember { mutableStateOf(prefs.getBoolean("fruitmerge_haptics", true)) }
    var fxReduced by remember { mutableStateOf(prefs.getBoolean("fruitmerge_fx_reduced", false)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("fruitmerge_sound", true)) }
    val playtime = remember { prefs.getLong("fruitmerge_playtime", 0L) }

    GxSheet("Einstellungen", MergeAccent, onClose = onClose) {
        GxToggle("Haptik", "Vibration bei Drops, Merges und Meilensteinen", MergeAccent, haptics) {
            haptics = it
            prefs.edit().putBoolean("fruitmerge_haptics", it).apply()
        }
        GxToggle("Sound", "Prozedural erzeugte Effektgeräusche", MergeAccent, soundOn) {
            soundOn = it
            prefs.edit().putBoolean("fruitmerge_sound", it).apply()
        }
        GxToggle("Reduzierte Effekte", "Weniger Partikel, kein Shake, keine Zeitlupe", MergeAccent, fxReduced) {
            fxReduced = it
            prefs.edit().putBoolean("fruitmerge_fx_reduced", it).apply()
        }
        Spacer(Modifier.height(8.dp))
        Text("Gesamtspielzeit: ${mergeFmtDuration(playtime)}", fontSize = 12.sp, color = HikariTextMuted)
        Spacer(Modifier.height(16.dp))
        GxPrimaryButton("Fertig", MergeAccent, Modifier.fillMaxWidth(), onClick = onClose)
    }
}

// ————— Level-Auswahl —————

@Composable
private fun MergeLevelSelectScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
) {
    BackHandler { onBack() }
    val stars = remember { mergeLoadStars(prefs) }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(MergeAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Herausforderung", MergeAccent, onBack, right = {
                Text("★${stars.sum()}", fontSize = 13.sp, color = MergeAccent, fontWeight = FontWeight.Bold)
            })

            Column(Modifier.padding(horizontal = 16.dp)) {
                for (row in 0 until 5) {
                    GxAppear(row) {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                            for (col in 0 until 3) {
                                val idx = row * 3 + col
                                val def = MergeLevels[idx]
                                val unlocked = idx == 0 || stars[idx - 1] > 0
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (stars[idx] > 0) lerp(HikariCardBg, MergeAccent, 0.08f)
                                            else HikariCardBg
                                        )
                                        .border(
                                            1.dp,
                                            if (stars[idx] > 0) MergeAccent.copy(alpha = 0.35f)
                                            else Color.White.copy(alpha = 0.06f),
                                            RoundedCornerShape(16.dp),
                                        )
                                        .gxPressable(enabled = unlocked) { onPick(idx) }
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        if (unlocked) "${idx + 1}" else "🔒",
                                        fontSize = 19.sp,
                                        color = if (unlocked) HikariText else HikariTextFaint,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(def.title, fontSize = 9.sp, color = HikariTextMuted, maxLines = 1)
                                    Spacer(Modifier.height(4.dp))
                                    GxStarRow(stars[idx], size = 11.dp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "Gewinne mindestens 1 Stern, um das nächste Level freizuschalten.",
                    fontSize = 11.sp,
                    color = HikariTextFaint,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ————— Statistiken —————

@Composable
private fun MergeStatsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    BackHandler { onBack() }
    val xp = remember { prefs.getInt("fruitmerge_xp", 0) }
    val (lvl, _, _) = mergeLevelInfo(xp)
    val top5 = remember { mergeLoadTop5(prefs) }
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yy") }
    val bf = prefs.getInt("fruitmerge_fruit_best", 0)
    val games = prefs.getInt("fruitmerge_games", 0)

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(MergeAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Statistik", MergeAccent, onBack)

            Column(Modifier.padding(horizontal = 16.dp)) {
                val tiles = listOf(
                    "$lvl" to "Spielerstufe",
                    "$games" to "Runden",
                    "${prefs.getInt("fruitmerge_highscore", 0)}" to "Rekord (Klassik)",
                    (if (bf > 0 || games > 0) MergeEmoji[bf] else "—") to "Höchste Frucht",
                    "×${prefs.getInt("fruitmerge_chain_best", 0)}" to "Beste Kette",
                    "${prefs.getInt("fruitmerge_merges_best", 0)}" to "Merges (Runde)",
                    "${prefs.getInt("fruitmerge_merges_total", 0)}" to "Merges gesamt",
                    "★${mergeLoadStars(prefs).sum()}/45" to "Challenge-Sterne",
                    mergeFmtDuration(prefs.getLong("fruitmerge_playtime", 0L)) to "Spielzeit",
                    mergeFmtDuration(prefs.getLong("fruitmerge_zen_secs", 0L)) to "Zen-Zeit",
                )
                tiles.chunked(2).forEachIndexed { row, pair ->
                    GxAppear(row) {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                            for ((v, l) in pair) {
                                GxStatTile(v, l, MergeAccent, Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(10.dp))
                Text("Top 5 (Klassik)", fontSize = 15.sp, color = HikariText, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                if (top5.isEmpty()) {
                    Text("Noch keine Runden gespielt.", fontSize = 12.sp, color = HikariTextFaint)
                } else {
                    top5.forEachIndexed { i, (s, day) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (i == 0) lerp(HikariCardBg, MergeAccent, 0.08f) else HikariCardBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    listOf("🥇", "🥈", "🥉", "4.", "5.")[i],
                                    fontSize = 14.sp, color = HikariTextMuted,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "$s",
                                    fontSize = 15.sp,
                                    color = if (i == 0) MergeAccent else HikariText,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(LocalDate.ofEpochDay(day).format(fmt), fontSize = 12.sp, color = HikariTextMuted)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ————— Erfolge —————

@Composable
private fun MergeAchievementsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    BackHandler { onBack() }
    val mask = remember { prefs.getInt("fruitmerge_ach", 0) }
    val unlockedCount = MergeAchievements.count { mask and (1 shl it.bit) != 0 }

    Box(Modifier.fillMaxSize()) {
        GxMenuBackground(MergeAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Erfolge", MergeAccent, onBack, right = {
                Text(
                    "$unlockedCount/${MergeAchievements.size}",
                    fontSize = 13.sp, color = MergeAccent, fontWeight = FontWeight.Bold,
                )
            })

            Column(Modifier.padding(horizontal = 16.dp)) {
                MergeAchievements.forEachIndexed { i, ach ->
                    GxAppear(i) {
                        GxAchRow(
                            emoji = ach.emoji,
                            title = ach.title,
                            desc = ach.desc,
                            accent = MergeAccent,
                            unlocked = mask and (1 shl ach.bit) != 0,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ————— Konfetti-Spawner (Kreise, Streifen, Sterne; Gravitation + Drag) —————

private fun mergeSpawnConfetti(
    world: MergeWorld,
    x: Float,
    y: Float,
    n: Int,
    sc: Float,
    colors: List<Color> = listOf(HikariAmber, Color(0xFFFF7043), Color(0xFF66BB6A), Color.White, Color(0xFFEF5350), Color(0xFFEC4899)),
) {
    repeat(n) {
        val ang = Random.nextFloat() * 2f * PI.toFloat()
        val spd = (260f + Random.nextFloat() * 620f) * sc
        world.sparks.add(
            MergeSpark(
                x, y,
                cos(ang) * spd, sin(ang) * spd - 160f * sc,
                0.7f + Random.nextFloat() * 0.6f, 1.3f,
                colors.random(),
                (3.5f + Random.nextFloat() * 5.5f) * sc,
                shape = Random.nextInt(3),
                rot = Random.nextFloat() * 360f,
                rotV = (Random.nextFloat() - 0.5f) * 900f,
                drag = 1.4f,
            )
        )
    }
}

// ————— Spiel —————

@Composable
private fun MergePlayScreen(
    mode: MergeMode,
    levelIdx: Int,
    onMenu: () -> Unit,
    onNextLevel: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    val def = if (mode == MergeMode.CHALLENGE) MergeLevels[levelIdx.coerceIn(0, MergeLevels.size - 1)] else null
    val dropLimited = def != null && (def.type == MergeGoalType.FRUIT_DROPS || def.type == MergeGoalType.SCORE_DROPS)
    val powerAllowed = def == null || def.type != MergeGoalType.SCORE_CLEAN

    // Einstellungen
    var hapticsOn by remember { mutableStateOf(prefs.getBoolean("fruitmerge_haptics", true)) }
    var fxReduced by remember { mutableStateOf(prefs.getBoolean("fruitmerge_fx_reduced", false)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("fruitmerge_sound", true)) }

    // Sound-Synth (prozedural, kein Asset); sauberes Release beim Verlassen
    val sound = remember { MergeSound() }
    DisposableEffect(Unit) {
        onDispose { sound.release() }
    }

    // Runden-Zustand
    var score by remember { mutableStateOf(0) }
    var shownScore by remember { mutableStateOf(0) }
    var highscore by remember { mutableStateOf(prefs.getInt("fruitmerge_highscore", 0)) }
    var next1 by remember { mutableStateOf(mergeRollDrop()) }
    var next2 by remember { mutableStateOf(mergeRollDrop()) }
    var over by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var recordShown by remember { mutableStateOf(false) }
    var recordBanner by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var statsFlushed by remember { mutableStateOf(false) }
    var stars by remember { mutableStateOf(0) }
    var failReason by remember { mutableStateOf("") }
    var levelUpTo by remember { mutableStateOf(0) }
    var milestone by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var reachedMask by remember { mutableStateOf(0) }
    var fruitMask by remember { mutableStateOf(prefs.getInt("fruitmerge_fruitmask", 0)) }
    var achMask by remember { mutableStateOf(prefs.getInt("fruitmerge_ach", 0)) }
    val achToasts = remember { mutableStateListOf<MergeAchievement>() }
    var showHelp by remember { mutableStateOf(!prefs.getBoolean("fruitmerge_help_seen", false)) }
    var namesPopup by remember { mutableStateOf<String?>(null) }
    var shakeCh by remember { mutableStateOf(0) }
    var popCh by remember { mutableStateOf(0) }
    var swapCh by remember { mutableStateOf(0) }
    var bombCh by remember { mutableStateOf(0) }
    var shakeFill by remember { mutableStateOf(0f) }
    var popFill by remember { mutableStateOf(0f) }
    var swapFill by remember { mutableStateOf(0f) }
    var bombFill by remember { mutableStateOf(0f) }
    var zenSecs by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(def?.limit ?: 0) }
    var dropsState by remember { mutableStateOf(0) }
    var mergesState by remember { mutableStateOf(0) }
    var restartKey by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0L) }

    val world = remember { MergeWorld() }
    val emojiPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true } }
    val popPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true } }

    // Gecachte Frucht-Shader (Einheitskreis, wird per Canvas-Transform skaliert):
    // radialer Verlauf mit Licht von oben-links + dunkler Rim + Specular
    val bodyPaints = remember {
        Array(10) { lvl ->
            val c = MergeColors[lvl]
            Paint().apply {
                isAntiAlias = true
                shader = RadialGradient(
                    -0.35f, -0.38f, 1.45f,
                    intArrayOf(mergeTint(c, 0.55f).toArgb(), c.toArgb(), mergeShade(c, 0.42f).toArgb()),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
        }
    }
    val rimPaints = remember {
        Array(10) { lvl ->
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = mergeShade(MergeColors[lvl], 0.38f).toArgb()
            }
        }
    }
    val specPaint = remember {
        Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                -0.34f, -0.42f, 0.66f,
                intArrayOf(Color.White.copy(alpha = 0.50f).toArgb(), Color.White.copy(alpha = 0.14f).toArgb(), Color.Transparent.toArgb()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }
    val starPath = remember {
        Path().apply {
            for (i in 0 until 10) {
                val ang = -PI / 2 + i * PI / 5
                val rad = if (i % 2 == 0) 1f else 0.45f
                val px = (cos(ang) * rad).toFloat()
                val py = (sin(ang) * rad).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
    }

    // Pop-Animation der "Nächste Frucht"-Vorschau
    val nextPop = remember { Animatable(1f) }
    LaunchedEffect(next1) {
        nextPop.snapTo(0.3f)
        nextPop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }

    fun buzz(type: HapticFeedbackType) {
        if (hapticsOn) haptic.performHapticFeedback(type)
    }

    fun unlockAch(idx: Int) {
        val bit = 1 shl idx
        if (achMask and bit != 0) return
        achMask = achMask or bit
        prefs.edit().putInt("fruitmerge_ach", achMask).apply()
        achToasts.add(MergeAchievements.first { it.bit == idx })
        buzz(HapticFeedbackType.LongPress)
    }

    fun noteFruit(lvl: Int) {
        reachedMask = reachedMask or (1 shl lvl)
        if (fruitMask and (1 shl lvl) == 0) {
            fruitMask = fruitMask or (1 shl lvl)
            prefs.edit().putInt("fruitmerge_fruitmask", fruitMask).apply()
        }
        if (fruitMask == 0x3FF) unlockAch(MergeAchSammler)
        if (lvl > world.bestFruit) world.bestFruit = lvl
    }

    fun flushRound(starsEarned: Int) {
        if (statsFlushed) return
        statsFlushed = true
        val e = prefs.edit()
        val games = prefs.getInt("fruitmerge_games", 0) + 1
        e.putInt("fruitmerge_games", games)
        e.putInt("fruitmerge_merges_total", prefs.getInt("fruitmerge_merges_total", 0) + world.mergesRound)
        if (world.mergesRound > prefs.getInt("fruitmerge_merges_best", 0)) e.putInt("fruitmerge_merges_best", world.mergesRound)
        if (world.longestChain > prefs.getInt("fruitmerge_chain_best", 0)) e.putInt("fruitmerge_chain_best", world.longestChain)
        if (world.bestFruit > prefs.getInt("fruitmerge_fruit_best", 0)) e.putInt("fruitmerge_fruit_best", world.bestFruit)
        e.putLong("fruitmerge_playtime", prefs.getLong("fruitmerge_playtime", 0L) + world.time.toLong())
        var zenTotal = prefs.getLong("fruitmerge_zen_secs", 0L)
        if (mode == MergeMode.ZEN) {
            zenTotal += world.time.toLong()
            e.putLong("fruitmerge_zen_secs", zenTotal)
        }
        val xpBefore = prefs.getInt("fruitmerge_xp", 0)
        val gain = when (mode) {
            MergeMode.CLASSIC -> score / 10
            MergeMode.ZEN -> score / 20
            MergeMode.CHALLENGE -> 25 * starsEarned
        }
        val xpAfter = xpBefore + gain
        e.putInt("fruitmerge_xp", xpAfter)
        if (mode == MergeMode.CLASSIC && score > 0) {
            val list = mergeLoadTop5(prefs).toMutableList()
            list.add(score to LocalDate.now().toEpochDay())
            list.sortByDescending { it.first }
            while (list.size > 5) list.removeAt(list.size - 1)
            e.putString("fruitmerge_top5", list.joinToString(";") { "${it.first}:${it.second}" })
        }
        e.apply()
        if (mergeLevelInfo(xpAfter).first > mergeLevelInfo(xpBefore).first) levelUpTo = mergeLevelInfo(xpAfter).first
        if (games >= 10) unlockAch(MergeAchRunden)
        if (mode == MergeMode.ZEN && zenTotal >= 600L) unlockAch(MergeAchZen)
    }

    fun finishGame() {
        if (over || won) return
        if (score > highscore) {
            highscore = score
            newRecord = true
            prefs.edit().putInt("fruitmerge_highscore", score).apply()
        }
        flushRound(0)
        shownScore = score
        over = true
        buzz(HapticFeedbackType.LongPress)
        if (soundOn) sound.gameOver()
    }

    fun winLevel() {
        if (over || won || def == null) return
        stars = mergeStarsFor(def, world.drops, world.time)
        val cur = mergeLoadStars(prefs)
        if (stars > cur[levelIdx]) {
            cur[levelIdx] = stars
            mergeSaveStars(prefs, cur)
        }
        if (levelIdx >= 4) unlockAch(MergeAchChal)
        flushRound(stars)
        shownScore = score
        won = true
        buzz(HapticFeedbackType.LongPress)
        if (soundOn) sound.win()
        // Konfetti-Regen beim Level-Sieg
        if (world.initialized) {
            val n = if (fxReduced) 24 else 84
            for (k in 0 until 3) {
                mergeSpawnConfetti(
                    world,
                    world.left + (world.right - world.left) * (0.25f + 0.25f * k),
                    world.top + 30f * world.scale,
                    n / 3, world.scale,
                )
            }
        }
    }

    fun failLevel(reason: String) {
        if (over || won) return
        failReason = reason
        flushRound(0)
        shownScore = score
        over = true
        buzz(HapticFeedbackType.LongPress)
        if (soundOn) sound.gameOver()
    }

    fun zenRelease() {
        val victims = world.fruits.sortedByDescending { it.y }.take(5)
        for (v in victims) {
            world.fruits.remove(v)
            if (!fxReduced) {
                repeat(6) {
                    val ang = Random.nextFloat() * 2f * PI.toFloat()
                    val spd = (120f + Random.nextFloat() * 200f) * world.scale
                    world.sparks.add(
                        MergeSpark(
                            v.x, v.y, cos(ang) * spd, sin(ang) * spd - 80f * world.scale,
                            0.4f + Random.nextFloat() * 0.3f, 0.7f,
                            Color.White.copy(alpha = 0.7f),
                            (3f + Random.nextFloat() * 4f) * world.scale,
                        )
                    )
                }
            }
        }
        world.pops.add(MergeTextPop(world.w / 2f, world.limitY + 60f * world.scale, "Sanft gelöst ✨", 1.2f))
        for (f in world.fruits) f.overTime = 0f
        buzz(HapticFeedbackType.TextHandleMove)
        if (soundOn) sound.power()
    }

    fun resetRound() {
        world.fruits.clear()
        world.sparks.clear()
        world.pops.clear()
        world.rings.clear()
        world.bokeh.clear()
        world.flash = 0f
        world.camShake = 0f
        world.slowMo = 0f
        world.freeze = 0
        world.chainPop = 0f
        world.chain = 0
        world.chainTimer = 0f
        world.longestChain = 0
        world.mergesForShake = 0
        world.mergesForPop = 0
        world.mergesForSwap = 0
        world.mergesForBomb = 0
        world.shakeCharges = 0
        world.popCharges = 0
        world.swapCharges = 0
        world.bombCharges = 0
        world.shakeVis = 0f
        world.usedShake = false
        world.usedPop = false
        world.usedSwap = false
        world.usedBomb = false
        world.mergesRound = 0
        world.drops = 0
        world.bestFruit = 0
        world.milestones.fill(false)
        world.fillLevel = 0f
        world.graceTimer = -1f
        world.current = MergeHandNone
        world.cooldown = 0f
        world.warn = 0f
        world.time = 0f
        world.initialized = false
        next1 = mergeRollDrop()
        next2 = mergeRollDrop()
        score = 0
        shownScore = 0
        over = false
        won = false
        newRecord = false
        recordShown = false
        recordBanner = false
        statsFlushed = false
        stars = 0
        failReason = ""
        levelUpTo = 0
        milestone = null
        reachedMask = 0
        shakeCh = 0
        popCh = 0
        swapCh = 0
        bombCh = 0
        shakeFill = 0f
        popFill = 0f
        swapFill = 0f
        bombFill = 0f
        zenSecs = 0
        timeLeft = def?.limit ?: 0
        dropsState = 0
        mergesState = 0
        paused = false
        restartKey++
    }

    fun dropFruit() {
        if (over || won || paused || showHelp || !world.initialized) return
        val cur = world.current
        if (cur == MergeHandNone) return
        val rainbow = cur == MergeHandRainbow
        val lvl = if (rainbow) 0 else cur
        val r = if (rainbow) world.radii[1] else world.radii[lvl]
        world.fruits.add(
            MergeFruit(
                x = world.aimX.coerceIn(world.left + r + 2f, world.right - r - 2f),
                y = world.hangY,
                vx = 0f,
                vy = 180f * world.scale,
                level = lvl,
                r = r,
                rainbow = rainbow,
            )
        )
        if (!rainbow) noteFruit(lvl)
        world.drops++
        dropsState = world.drops
        world.current = MergeHandNone
        world.cooldown = 0.55f
        buzz(HapticFeedbackType.TextHandleMove)
        if (soundOn) sound.drop()
    }

    fun doShake() {
        if (world.shakeCharges <= 0 || over || won || paused || !powerAllowed) return
        world.shakeCharges--
        shakeCh = world.shakeCharges
        world.usedShake = true
        world.shakeVis = 0.6f
        for (f in world.fruits) {
            f.vx += (if (Random.nextBoolean()) 1f else -1f) * (350f + Random.nextFloat() * 350f) * world.scale
            f.vy -= (120f + Random.nextFloat() * 180f) * world.scale
            f.angVel += (Random.nextFloat() - 0.5f) * 6f
        }
        buzz(HapticFeedbackType.LongPress)
        if (soundOn) sound.power()
        if (world.usedShake && world.usedPop) unlockAch(MergeAchPower)
    }

    fun doPop() {
        if (world.popCharges <= 0 || over || won || paused || !powerAllowed) return
        val target = world.fruits.filter { !it.rainbow }.minByOrNull { it.level } ?: return
        world.fruits.remove(target)
        world.popCharges--
        popCh = world.popCharges
        world.usedPop = true
        if (!fxReduced) {
            repeat(10) {
                val ang = Random.nextFloat() * 2f * PI.toFloat()
                val spd = (140f + Random.nextFloat() * 260f) * world.scale
                world.sparks.add(
                    MergeSpark(
                        target.x, target.y, cos(ang) * spd, sin(ang) * spd,
                        0.3f + Random.nextFloat() * 0.25f, 0.55f,
                        MergeColors[target.level],
                        (3f + Random.nextFloat() * 4f) * world.scale,
                    )
                )
            }
        }
        world.rings.add(MergeRing(target.x, target.y, 0.35f, 0.35f, target.r * 2.4f, MergeColors[target.level], 4f * world.scale))
        world.pops.add(MergeTextPop(target.x, target.y, "Pop!", 0.7f))
        buzz(HapticFeedbackType.TextHandleMove)
        if (soundOn) sound.power()
        if (world.usedShake && world.usedPop) unlockAch(MergeAchPower)
    }

    // Tausch: Hand-Frucht ↔ nächste Frucht
    fun doSwap() {
        if (world.swapCharges <= 0 || over || won || paused || !powerAllowed) return
        val cur = world.current
        if (cur == MergeHandNone) return
        world.swapCharges--
        swapCh = world.swapCharges
        world.current = next1
        next1 = cur
        world.usedSwap = true
        unlockAch(MergeAchTausch)
        world.pops.add(MergeTextPop(world.aimX, world.hangY - 46f * world.scale, "🔀 Getauscht!", 0.8f))
        buzz(HapticFeedbackType.TextHandleMove)
        if (soundOn) sound.power()
    }

    // Bombe: sprengt die drei kleinsten Früchte mit Explosionseffekt
    fun doBomb() {
        if (world.bombCharges <= 0 || over || won || paused || !powerAllowed) return
        val targets = world.fruits.filter { !it.rainbow }.sortedWith(compareBy({ it.level }, { it.r })).take(3)
        if (targets.isEmpty()) return
        world.bombCharges--
        bombCh = world.bombCharges
        world.usedBomb = true
        unlockAch(MergeAchBombe)
        val sc = world.scale
        for (t in targets) {
            world.fruits.remove(t)
            world.rings.add(MergeRing(t.x, t.y, 0.4f, 0.4f, t.r * 3.2f, HikariAmber, 5f * sc))
            if (!fxReduced) {
                repeat(12) {
                    val ang = Random.nextFloat() * 2f * PI.toFloat()
                    val spd = (180f + Random.nextFloat() * 340f) * sc
                    world.sparks.add(
                        MergeSpark(
                            t.x, t.y, cos(ang) * spd, sin(ang) * spd - 60f * sc,
                            0.35f + Random.nextFloat() * 0.3f, 0.65f,
                            listOf(HikariAmber, Color(0xFFFF7043), MergeColors[t.level]).random(),
                            (3f + Random.nextFloat() * 5f) * sc,
                        )
                    )
                }
            }
        }
        if (!fxReduced) {
            world.flash = 0.5f
            world.camShake = 10f
        }
        world.pops.add(MergeTextPop(world.w / 2f, world.bottom - 130f * sc, "💥 Boom!", 1f, sizeMul = 1.3f, golden = true))
        buzz(HapticFeedbackType.LongPress)
        if (soundOn) sound.boom()
    }

    // System-Back: pausiert im Spiel, verlässt aus Overlays
    BackHandler {
        when {
            confirmRestart -> confirmRestart = false
            showHelp -> {
                showHelp = false
                prefs.edit().putBoolean("fruitmerge_help_seen", true).apply()
            }
            over || won -> onMenu()
            else -> paused = !paused
        }
    }

    // Auto-Pause bei App-Wechsel + Stats-Flush beim Verlassen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !over && !won) paused = true
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            if (!statsFlushed && world.time > 5f) flushRound(0)
        }
    }

    // Banner-Timer
    LaunchedEffect(milestone) {
        if (milestone != null) {
            delay(2200)
            milestone = null
        }
    }
    LaunchedEffect(recordBanner) {
        if (recordBanner) {
            delay(2500)
            recordBanner = false
        }
    }
    LaunchedEffect(achToasts.firstOrNull()) {
        if (achToasts.isNotEmpty()) {
            delay(2400)
            if (achToasts.isNotEmpty()) achToasts.removeAt(0)
        }
    }
    LaunchedEffect(namesPopup) {
        if (namesPopup != null) {
            delay(2600)
            namesPopup = null
        }
    }

    // Game-Loop mit fixem Substep + Zeitlupe/Impact-Frames
    LaunchedEffect(restartKey) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.032f)
                last = now

                // Hintergrund-Bokeh läuft immer weiter (Ambient)
                if (dt > 0f && world.initialized) {
                    for (b in world.bokeh) {
                        b.y -= b.speed * dt
                        if (b.y < -12f * world.scale) {
                            b.y = world.h + 12f * world.scale
                            b.x = Random.nextFloat() * world.w
                        }
                    }
                }

                val running = dt > 0f && world.initialized && !over && !won && !paused && !showHelp
                if (running) {
                    // Zeit-Skalierung: Impact-Frames (0) und Slow-Mo (0.35)
                    val ts = when {
                        world.freeze > 0 -> { world.freeze--; 0f }
                        world.slowMo > 0f -> { world.slowMo -= dt; 0.35f }
                        else -> 1f
                    }
                    val sdt = dt * ts
                    val sc = world.scale
                    val g = 2000f * sc
                    world.time += dt
                    if (mode == MergeMode.ZEN) {
                        val s = world.time.toInt()
                        if (s != zenSecs) zenSecs = s
                    }

                    // Score-Count-up
                    if (shownScore < score) {
                        shownScore = min(score, shownScore + max(1, ((score - shownScore) * 0.18f).toInt()))
                    }

                    // Juice-Decay
                    if (world.flash > 0f) world.flash = max(0f, world.flash - dt * 2.6f)
                    if (world.camShake > 0f) world.camShake = max(0f, world.camShake * (1f - 6.5f * dt) - dt)
                    if (world.chainPop > 0f) world.chainPop = max(0f, world.chainPop - dt * 2.4f)

                    // Ketten-Countdown
                    if (world.chainTimer > 0f) {
                        world.chainTimer -= dt
                        if (world.chainTimer <= 0f) world.chain = 0
                    }
                    if (world.shakeVis > 0f) world.shakeVis = max(0f, world.shakeVis - dt)

                    // Nachschub nach Cooldown (bei Drop-Limit versiegt die Hand)
                    if (world.current == MergeHandNone) {
                        world.cooldown -= dt
                        val capped = dropLimited && def != null && world.drops >= def.limit
                        if (world.cooldown <= 0f && !capped) {
                            world.current = next1
                            next1 = next2
                            next2 = mergeRollDrop()
                        }
                    }

                    // Physik: 4 Substeps pro Frame
                    val fruits = world.fruits
                    val sub = 4
                    val hstep = sdt / sub
                    if (sdt > 0f) repeat(sub) {
                        for (f in fruits) {
                            f.vy += g * hstep
                            f.x += f.vx * hstep
                            f.y += f.vy * hstep
                        }
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
                                            val tx = -ny
                                            val ty = nx
                                            val vt = rvx * tx + rvy * ty
                                            val jt = -vt * 0.2f / (1f / ma + 1f / mb)
                                            a.vx -= tx * jt / ma
                                            a.vy -= ty * jt / ma
                                            b.vx += tx * jt / mb
                                            b.vy += ty * jt / mb
                                            // Spin aus Tangential-Kontakt
                                            a.angVel += vt / a.r * 0.22f
                                            b.angVel -= vt / b.r * 0.22f
                                            // Squash bei harten Kollisionen
                                            if (-vn > 340f * sc) {
                                                val bump = min(0.30f, (-vn - 340f * sc) / (3000f * sc))
                                                a.sq += bump * 0.7f
                                                b.sq += bump * 0.7f
                                            }
                                        }
                                    }
                                }
                            }
                            // Pins (Challenge-Maps): feste Kreise
                            for (f in fruits) {
                                for (p in world.pins) {
                                    val dx = f.x - p.x
                                    val dy = f.y - p.y
                                    val rs = f.r + p.r
                                    val d2 = dx * dx + dy * dy
                                    if (d2 < rs * rs && d2 > 0.0001f) {
                                        val d = sqrt(d2)
                                        val nx = dx / d
                                        val ny = dy / d
                                        f.x = p.x + nx * rs
                                        f.y = p.y + ny * rs
                                        val vn = f.vx * nx + f.vy * ny
                                        if (vn < 0f) {
                                            f.vx -= nx * vn * 1.15f
                                            f.vy -= ny * vn * 1.15f
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
                                    val imp = f.vy
                                    f.y = maxY
                                    // Squash + Rollen bei Bodenkontakt
                                    if (imp > 420f * sc) f.sq = min(0.45f, (imp - 420f * sc) / (2200f * sc))
                                    f.angVel = f.angVel * 0.9f + (f.vx / f.r) * 0.1f
                                    if (f.vy > 0f) f.vy = if (f.vy < 260f * sc) 0f else -f.vy * 0.15f
                                    f.vx *= 0.94f
                                }
                            }
                        }
                    }

                    // Ruhige Stapel beruhigen + Squash-Feder + Rotation + Regenbogen-Glitzer
                    for (f in fruits) {
                        val sp = abs(f.vx) + abs(f.vy)
                        if (sp < 30f * sc) {
                            f.vx *= 0.75f
                            f.vy *= 0.85f
                        }
                        f.pop = min(1f, f.pop + sdt * 4.5f)
                        // Gedämpfte Feder für Squash & Stretch
                        f.sqv += (-240f * f.sq - 13f * f.sqv) * sdt
                        f.sq = (f.sq + f.sqv * sdt).coerceIn(-0.5f, 0.6f)
                        f.angle = (f.angle + f.angVel * sdt) % (2f * PI.toFloat())
                        f.angVel *= max(0f, 1f - 2.5f * sdt)
                        // Glitzernde Funken um Regenbogen-Früchte
                        if (f.rainbow && !fxReduced && Random.nextFloat() < sdt * 5f) {
                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                            world.sparks.add(
                                MergeSpark(
                                    f.x + cos(ang) * f.r, f.y + sin(ang) * f.r,
                                    cos(ang) * 40f * sc, sin(ang) * 40f * sc - 30f * sc,
                                    0.35f, 0.35f,
                                    Color.White.copy(alpha = 0.9f),
                                    2.5f * sc,
                                )
                            )
                        }
                    }

                    // Merges (Kettenreaktionen über Folge-Frames)
                    var i = 0
                    outer@ while (i < fruits.size) {
                        val a = fruits[i]
                        var j = i + 1
                        while (j < fruits.size) {
                            val b = fruits[j]
                            val anyRainbow = a.rainbow || b.rainbow
                            val match = anyRainbow || a.level == b.level
                            if (match) {
                                val dx = b.x - a.x
                                val dy = b.y - a.y
                                val rs = a.r + b.r + 2f * sc
                                if (dx * dx + dy * dy <= rs * rs) {
                                    val mx = (a.x + b.x) / 2f
                                    val my = (a.y + b.y) / 2f
                                    // Basis-Stufe: Regenbogen übernimmt die Stufe des Partners
                                    val baseLevel = when {
                                        a.rainbow && b.rainbow -> 4
                                        a.rainbow -> b.level
                                        b.rainbow -> a.level
                                        else -> a.level
                                    }
                                    fruits.removeAt(j)
                                    fruits.removeAt(i)
                                    world.mergesRound += 1
                                    mergesState = world.mergesRound
                                    if (anyRainbow) unlockAch(MergeAchRainbow)

                                    // Ketten-Combo
                                    if (world.chainTimer > 0f) world.chain += 1 else world.chain = 1
                                    world.chainTimer = 2f
                                    world.chainPop = 1f
                                    if (world.chain > world.longestChain) world.longestChain = world.chain
                                    if (world.chain >= 5) unlockAch(MergeAchKette)
                                    if (world.chain >= 8) unlockAch(MergeAchTurbo)
                                    val mult = if (world.chain >= 4) 3 else if (world.chain >= 2) 2 else 1

                                    // Power-up-Aufladung
                                    if (world.shakeCharges < 2) {
                                        world.mergesForShake += 1
                                        if (world.mergesForShake >= 15) {
                                            world.mergesForShake = 0
                                            world.shakeCharges += 1
                                            shakeCh = world.shakeCharges
                                        }
                                        shakeFill = world.mergesForShake / 15f
                                    }
                                    if (world.popCharges < 2) {
                                        world.mergesForPop += 1
                                        if (world.mergesForPop >= 20) {
                                            world.mergesForPop = 0
                                            world.popCharges += 1
                                            popCh = world.popCharges
                                        }
                                        popFill = world.mergesForPop / 20f
                                    }
                                    if (world.swapCharges < 2) {
                                        world.mergesForSwap += 1
                                        if (world.mergesForSwap >= 10) {
                                            world.mergesForSwap = 0
                                            world.swapCharges += 1
                                            swapCh = world.swapCharges
                                        }
                                        swapFill = world.mergesForSwap / 10f
                                    }
                                    if (world.bombCharges < 1) {
                                        world.mergesForBomb += 1
                                        if (world.mergesForBomb >= 25) {
                                            world.mergesForBomb = 0
                                            world.bombCharges += 1
                                            bombCh = world.bombCharges
                                        }
                                        bombFill = world.mergesForBomb / 25f
                                    }

                                    if (baseLevel >= 9) {
                                        // 🍉 + 🍉 → Feuerwerk + Bonus
                                        val pts = 500 * mult
                                        score += pts
                                        unlockAch(MergeAchFeuerwerk)
                                        buzz(HapticFeedbackType.LongPress)
                                        if (soundOn) { sound.boom(); sound.fanfare(world.chain) }
                                        world.pops.add(MergeTextPop(mx, my - 40f * sc, "+$pts Bonus!", 1.6f, sizeMul = 1.5f, golden = true))
                                        world.rings.add(MergeRing(mx, my, 0.7f, 0.7f, world.radii[9] * 3.2f, HikariAmber, 8f * sc))
                                        world.rings.add(MergeRing(mx, my, 0.9f, 0.9f, world.radii[9] * 4.6f, Color.White, 4f * sc))
                                        if (!fxReduced) {
                                            world.flash = 1f
                                            world.slowMo = 0.28f
                                            world.freeze = 2
                                            world.camShake = 18f
                                        }
                                        mergeSpawnConfetti(world, mx, my, if (fxReduced) 14 else 60, sc)
                                    } else {
                                        val nl = baseLevel + 1
                                        val pts = (nl + 1) * 10 * mult
                                        score += pts
                                        // Haptik-Abstufung nach Merge-Größe
                                        buzz(
                                            when {
                                                nl >= 4 -> HapticFeedbackType.LongPress
                                                else -> HapticFeedbackType.TextHandleMove
                                            }
                                        )
                                        if (soundOn) {
                                            sound.merge(nl)
                                            if (world.chain >= 3) sound.fanfare(world.chain)
                                        }
                                        val nf = MergeFruit(
                                            mx, my,
                                            (a.vx + b.vx) * 0.25f,
                                            min((a.vy + b.vy) * 0.25f, 0f) - 60f * sc,
                                            nl, world.radii[nl],
                                            pop = 0f,
                                        )
                                        nf.sq = 0.5f
                                        nf.angVel = (a.angVel + b.angVel) * 0.5f + (Random.nextFloat() - 0.5f) * 2f
                                        fruits.add(nf)
                                        noteFruit(nl)
                                        // Glow-Ring in der Farbe der neuen Frucht
                                        world.rings.add(
                                            MergeRing(mx, my, 0.45f, 0.45f, world.radii[nl] * 2.4f, MergeColors[nl], max(3f, (4f + nl) * sc))
                                        )
                                        // Flash + Kamera-Shake + Zeitlupe bei großen Merges
                                        if (!fxReduced) {
                                            if (nl >= 6) {
                                                world.flash = min(1f, 0.35f + nl * 0.06f)
                                                world.camShake = min(16f, (nl - 4) * 3f)
                                            }
                                            if (nl >= 8) world.freeze = 2
                                            if (nl == 9) world.slowMo = 0.25f
                                        }
                                        // Meilenstein: erste große Frucht dieser Runde
                                        if (nl >= 6 && !world.milestones[nl]) {
                                            world.milestones[nl] = true
                                            val bonus = when (nl) { 6 -> 50; 7 -> 100; 8 -> 200; else -> 500 }
                                            score += bonus
                                            milestone = nl to "Erste ${MergeEmoji[nl]} ${MergeNames[nl]}! +$bonus"
                                            if (nl == 9) unlockAch(MergeAchMelone)
                                            mergeSpawnConfetti(world, mx, my, if (fxReduced) 12 else 30, sc, listOf(HikariAmber, Color.White, MergeColors[nl]))
                                        }
                                        world.pops.add(
                                            MergeTextPop(
                                                mx, my - world.radii[nl] - 18f * sc, "+$pts", 0.9f,
                                                sizeMul = (1f + min(0.7f, pts / 300f)),
                                                golden = pts >= 200,
                                            )
                                        )
                                        if (!fxReduced) {
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
                                    }

                                    if (mode == MergeMode.CLASSIC) {
                                        if (score >= 1000) unlockAch(MergeAchTausend)
                                        if (!recordShown && highscore > 0 && score > highscore) {
                                            recordShown = true
                                            recordBanner = true
                                        }
                                    }
                                    continue@outer
                                }
                            }
                            j++
                        }
                        i++
                    }

                    // Füllstand (höchster ruhender Stapel relativ zur Limit-Linie)
                    var minTop = world.bottom
                    for (f in fruits) {
                        if (abs(f.vx) + abs(f.vy) < 90f * sc) minTop = min(minTop, f.y - f.r)
                    }
                    val denom = (world.bottom - world.limitY).coerceAtLeast(1f)
                    world.fillLevel = ((world.bottom - minTop) / denom).coerceIn(0f, 1f)

                    // Überlauf: ruhende Frucht ragt >1.2s über die Limit-Linie
                    val warnBefore = world.warn > 0.05f
                    var anyOver = false
                    for (f in fruits) {
                        val slow = abs(f.vx) + abs(f.vy) < 60f * world.scale
                        if (slow && f.y - f.r < world.limitY) {
                            f.overTime += dt
                            if (f.overTime > 0.1f) anyOver = true
                            if (f.overTime > 1.2f) {
                                when (mode) {
                                    MergeMode.CLASSIC -> finishGame()
                                    MergeMode.CHALLENGE -> failLevel("Über die Limit-Linie gestapelt")
                                    MergeMode.ZEN -> zenRelease()
                                }
                                break
                            }
                        } else {
                            f.overTime = 0f
                        }
                    }
                    world.warn = if (anyOver) min(1f, world.warn + dt * 4f) else max(0f, world.warn - dt * 4f)
                    if (world.warn > 0.05f && !warnBefore && soundOn) sound.warn()

                    // Challenge-Ziele prüfen
                    if (mode == MergeMode.CHALLENGE && def != null && !won && !over) {
                        when (def.type) {
                            MergeGoalType.FRUIT_DROPS -> if (world.bestFruit >= def.target) winLevel()
                            MergeGoalType.SCORE_DROPS -> if (score >= def.target) winLevel()
                            MergeGoalType.SCORE_CLEAN -> if (score >= def.target) winLevel()
                            MergeGoalType.MERGES_TIME -> {
                                if (world.mergesRound >= def.target) winLevel()
                                val tl = (def.limit - world.time).toInt().coerceAtLeast(0)
                                if (tl != timeLeft) timeLeft = tl
                                if (!won && world.time >= def.limit) failLevel("Zeit abgelaufen")
                            }
                        }
                        if (!won && !over && dropLimited && world.drops >= def.limit && world.current == MergeHandNone) {
                            if (world.graceTimer < 0f) world.graceTimer = 4f
                            world.graceTimer -= dt
                            if (world.graceTimer <= 0f) failLevel("Drops aufgebraucht")
                        }
                    }

                    // Partikel (Gravitation + Drag + Rotation)
                    val sit = world.sparks.iterator()
                    while (sit.hasNext()) {
                        val s = sit.next()
                        s.vy += g * 0.35f * sdt
                        if (s.drag > 0f) {
                            val d = max(0f, 1f - s.drag * sdt)
                            s.vx *= d
                            s.vy *= d
                        }
                        s.x += s.vx * sdt
                        s.y += s.vy * sdt
                        s.rot += s.rotV * sdt
                        s.life -= sdt
                        if (s.life <= 0f) sit.remove()
                    }
                    // Ringe
                    val rit = world.rings.iterator()
                    while (rit.hasNext()) {
                        val r = rit.next()
                        r.life -= sdt
                        if (r.life <= 0f) rit.remove()
                    }
                    // Punkte-Popups (aufsteigend)
                    val pit = world.pops.iterator()
                    while (pit.hasNext()) {
                        val p = pit.next()
                        p.life -= sdt
                        p.y -= sdt * p.vy * world.scale
                        if (p.life <= 0f) pit.remove()
                    }
                }
                tick++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
        // Kopfzeile: Pause-Chip · Titel · Score-Pille
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            GxIconChip(if (over || won) "←" else "❚❚", size = 38.dp) {
                if (over || won) onMenu() else paused = true
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (mode) {
                        MergeMode.CLASSIC -> "Fruit Merge"
                        MergeMode.ZEN -> "Zen 🧘"
                        MergeMode.CHALLENGE -> "Level ${levelIdx + 1}: ${def?.title ?: ""}"
                    },
                    fontSize = 16.sp, color = MergeAccent, fontWeight = FontWeight.Black,
                )
                Text(
                    when (mode) {
                        MergeMode.CLASSIC ->
                            if (highscore > 0 && score < highscore) "Noch ${highscore - score} bis Rekord"
                            else if (highscore > 0 && score >= highscore) "✨ Rekord!"
                            else "Erste Runde"
                        MergeMode.ZEN -> "${mergeFmtTime(zenSecs)} · ohne Druck"
                        MergeMode.CHALLENGE -> if (def == null) "" else when (def.type) {
                            MergeGoalType.MERGES_TIME -> "⏱ ${timeLeft}s · ${mergesState}/${def.target}"
                            MergeGoalType.FRUIT_DROPS, MergeGoalType.SCORE_DROPS -> "Drops ${dropsState}/${def.limit}"
                            MergeGoalType.SCORE_CLEAN -> "ohne Power-ups"
                        }
                    },
                    fontSize = 10.sp,
                    color = if (mode == MergeMode.CLASSIC && highscore in 1..score) MergeAccent else HikariTextMuted,
                )
            }
            GxHudPill(
                "Punkte", "$shownScore",
                accent = if (highscore in 1..score) MergeAccent else null,
            )
        }

        // Werkzeugleiste: Booster mit Ladering · Vorschau (Kette läuft im Spielfeld)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (powerAllowed) {
                    MergePowerButton("🌀", shakeCh, shakeFill, enabled = shakeCh > 0) { doShake() }
                    MergePowerButton("💥", popCh, popFill, enabled = popCh > 0) { doPop() }
                    MergePowerButton("🔀", swapCh, swapFill, enabled = swapCh > 0) { doSwap() }
                    MergePowerButton("💣", bombCh, bombFill, enabled = bombCh > 0) { doBomb() }
                } else {
                    Text("Power-ups gesperrt", fontSize = 10.sp, color = HikariTextFaint)
                }
                GxIconChip("?", size = 34.dp) { showHelp = true }
            }
            // Vorschau nächste zwei Früchte — antippbar für Namen, Pop bei Wechsel
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariCardBg)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .gxPressable {
                        val handName = when (world.current) {
                            MergeHandRainbow -> "🌈 Regenbogen"
                            MergeHandNone -> "—"
                            else -> "${MergeEmoji[world.current]} ${MergeNames[world.current]}"
                        }
                        val n1 = if (next1 == MergeHandRainbow) "🌈 Regenbogen" else "${MergeEmoji[next1]} ${MergeNames[next1]}"
                        val n2 = if (next2 == MergeHandRainbow) "🌈 Regenbogen" else "${MergeEmoji[next2]} ${MergeNames[next2]}"
                        namesPopup = "Hand: $handName\nDanach: $n1, $n2"
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Nächste", fontSize = 9.sp, color = HikariTextFaint)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (next1 == MergeHandRainbow) "🌈" else MergeEmoji[next1],
                    fontSize = 21.sp,
                    modifier = Modifier.graphicsLayer { scaleX = nextPop.value; scaleY = nextPop.value },
                )
                Spacer(Modifier.width(2.dp))
                Text(if (next2 == MergeHandRainbow) "🌈" else MergeEmoji[next2], fontSize = 14.sp, modifier = Modifier.alpha(0.55f))
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(restartKey) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (over || won || paused || showHelp) return@awaitEachGesture
                            world.aimX = down.position.x
                            val completed = drag(down.id) { change ->
                                world.aimX = change.position.x
                                change.consume()
                            }
                            if (completed && !over && !won && !paused && !showHelp) dropFruit()
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
                    val geom = def?.geom ?: MergeGeom.NORMAL
                    val marginX = when (geom) {
                        MergeGeom.NORMAL -> w * 0.035f
                        MergeGeom.NARROW -> w * 0.16f
                        MergeGeom.WIDE -> w * 0.02f
                    }
                    world.left = marginX
                    world.right = w - marginX
                    world.bottom = h - w * 0.035f
                    world.top = if (geom == MergeGeom.WIDE) h * 0.34f else h * 0.15f
                    world.limitY = world.top + h * 0.045f
                    world.hangY = world.top * 0.45f
                    world.pins.clear()
                    for ((fx, fy, rf) in def?.pins ?: emptyList()) {
                        world.pins.add(
                            MergePin(
                                world.left + fx * (world.right - world.left),
                                world.top + fy * (world.bottom - world.top),
                                rf * w,
                            )
                        )
                    }
                    // Hintergrund-Bokeh
                    world.bokeh.clear()
                    repeat(if (fxReduced) 7 else 14) {
                        world.bokeh.add(
                            MergeBokeh(
                                Random.nextFloat() * w,
                                Random.nextFloat() * h,
                                (2.5f + Random.nextFloat() * 5f) * world.scale,
                                (10f + Random.nextFloat() * 26f) * world.scale,
                                0.05f + Random.nextFloat() * 0.08f,
                            )
                        )
                    }
                    if (world.aimX == 0f) world.aimX = w / 2f
                    world.initialized = true
                }
                val sc = world.scale
                val zen = mode == MergeMode.ZEN
                val t = world.time

                // ——— Hintergrund: vertikaler Gradient + Glow-Blobs + Bokeh ———
                val bgTop = when (mode) {
                    MergeMode.CLASSIC -> Color(0xFF16181D)
                    MergeMode.ZEN -> Color(0xFF1E1810)
                    MergeMode.CHALLENGE -> Color(0xFF161522)
                }
                val bgBot = when (mode) {
                    MergeMode.CLASSIC -> Color(0xFF0E0F12)
                    MergeMode.ZEN -> Color(0xFF120E08)
                    MergeMode.CHALLENGE -> Color(0xFF0E0D16)
                }
                drawRect(Brush.verticalGradient(listOf(bgTop, bgBot), startY = 0f, endY = h))
                val blobAccent = when (mode) {
                    MergeMode.ZEN -> Color(0xFFF59E0B)
                    MergeMode.CHALLENGE -> Color(0xFF8B7CF6)
                    else -> MergeAccent
                }
                val blobFx = floatArrayOf(0.22f, 0.82f, 0.45f)
                val blobFy = floatArrayOf(0.18f, 0.45f, 0.85f)
                val blobCols = arrayOf(blobAccent, HikariAmber, blobAccent)
                for (bi in 0 until 3) {
                    val bx = w * blobFx[bi] + sin(t * 0.11f + bi * 2.2f) * w * 0.07f
                    val by = h * blobFy[bi] + cos(t * 0.09f + bi * 1.7f) * h * 0.05f
                    drawRect(
                        Brush.radialGradient(
                            listOf(blobCols[bi].copy(alpha = if (bi == 1) 0.06f else 0.09f), Color.Transparent),
                            center = Offset(bx, by),
                            radius = w * 0.5f,
                        )
                    )
                }
                for (b in world.bokeh) {
                    drawCircle(Color.White.copy(alpha = b.alpha), b.r, Offset(b.x, b.y))
                }

                // Kamera-/Container-Offset (Shake bei großen Merges + Schütteln)
                val camX = if (world.camShake > 0f) (Random.nextFloat() * 2f - 1f) * world.camShake * sc else 0f
                val camY = if (world.camShake > 0f) (Random.nextFloat() * 2f - 1f) * world.camShake * 0.7f * sc else 0f
                val offX = (if (world.shakeVis > 0f) sin(world.time * 70f) * world.shakeVis * 12f * sc else 0f) + camX
                val offY = camY

                // ——— Behälter: Glas-/Premium-Look ———
                val inTop = if (zen) Color(0xFF241B0E) else Color(0xFF17171B)
                val inBot = if (zen) Color(0xFF13100A) else Color(0xFF0D0D10)
                val cTopLeft = Offset(world.left + offX, world.top + offY)
                val cSize = Size(world.right - world.left, world.bottom - world.top)
                drawRect(
                    Brush.verticalGradient(listOf(inTop, inBot), startY = world.top, endY = world.bottom),
                    topLeft = cTopLeft, size = cSize,
                )
                // Innenschatten an den Wänden
                val shW = 30f * sc
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent), startX = world.left + offX, endX = world.left + offX + shW),
                    topLeft = cTopLeft, size = Size(shW, cSize.height),
                )
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)), startX = world.right + offX - shW, endX = world.right + offX),
                    topLeft = Offset(world.right + offX - shW, cTopLeft.y), size = Size(shW, cSize.height),
                )
                // Boden-Glow
                val floorCol = if (zen) Color(0xFFF59E0B) else MergeAccent
                drawRect(
                    Brush.radialGradient(
                        listOf(floorCol.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset((world.left + world.right) / 2f + offX, world.bottom + offY),
                        radius = (world.right - world.left) * 0.55f,
                    ),
                    topLeft = cTopLeft, size = cSize,
                )

                // Pins
                for (p in world.pins) {
                    drawCircle(Color(0xFF3A3226), p.r, Offset(p.x + offX, p.y + offY))
                    drawCircle(HikariAmber.copy(alpha = 0.7f), p.r, Offset(p.x + offX, p.y + offY), style = Stroke(max(2f, 3f * sc)))
                    drawCircle(Color.White.copy(alpha = 0.12f), p.r * 0.45f, Offset(p.x + offX - p.r * 0.2f, p.y + offY - p.r * 0.25f))
                }

                // Boden-Schatten unter den Früchten (Alpha je nach Höhe)
                for (f in world.fruits) {
                    val hFrac = ((world.bottom - (f.y + f.r)) / (world.bottom - world.top)).coerceIn(0f, 1f)
                    val sa = 0.34f * (1f - hFrac) * (1f - hFrac)
                    if (sa > 0.01f) {
                        val sw2 = f.r * (1.25f - 0.45f * hFrac)
                        drawOval(
                            Color.Black.copy(alpha = sa),
                            topLeft = Offset(f.x + offX - sw2, world.bottom + offY - f.r * 0.16f),
                            size = Size(sw2 * 2f, f.r * 0.32f),
                        )
                    }
                }

                // Limit-Linie (animiert/pulsierend, rot glühend bei Gefahr)
                val warnPulse = if (world.warn > 0f) (0.5f + 0.5f * sin(t * 10f)) * world.warn else 0f
                val lineColor = lerp(HikariTextFaint, HikariDanger, min(1f, world.warn + warnPulse * 0.4f))
                val lineW = max(2f, 2.5f * sc) * (1f + 0.22f * sin(t * 3f) + warnPulse * 0.6f)
                if (world.warn > 0.02f) {
                    drawLine(
                        HikariDanger.copy(alpha = 0.30f * world.warn * (0.6f + 0.4f * sin(t * 10f))),
                        Offset(world.left + 8f * sc + offX, world.limitY + offY),
                        Offset(world.right - 8f * sc + offX, world.limitY + offY),
                        strokeWidth = lineW * 4f,
                        cap = StrokeCap.Round,
                    )
                }
                drawLine(
                    lineColor,
                    Offset(world.left + 8f * sc + offX, world.limitY + offY),
                    Offset(world.right - 8f * sc + offX, world.limitY + offY),
                    strokeWidth = lineW,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f * sc, 14f * sc), phase = -t * 30f * sc),
                )

                // Ziel-Hilfslinie + Ghost + Hand-Frucht (Landepunkt via vertikalem Ray)
                if (!over && !won && world.current != MergeHandNone) {
                    val cur = world.current
                    val rainbow = cur == MergeHandRainbow
                    val cr = if (rainbow) world.radii[1] else world.radii[cur]
                    val cx = world.aimX.coerceIn(world.left + cr + 2f, world.right - cr - 2f)
                    var landY = world.bottom - cr
                    for (f in world.fruits) {
                        val dx = abs(f.x - cx)
                        val rs = f.r + cr
                        if (dx < rs) {
                            val dy = sqrt(rs * rs - dx * dx)
                            if (f.y - dy < landY) landY = f.y - dy
                        }
                    }
                    for (p in world.pins) {
                        val dx = abs(p.x - cx)
                        val rs = p.r + cr
                        if (dx < rs) {
                            val dy = sqrt(rs * rs - dx * dx)
                            if (p.y - dy < landY) landY = p.y - dy
                        }
                    }
                    landY = max(landY, world.hangY)
                    val curCol = if (rainbow) Color.hsv((t * 120f) % 360f, 0.7f, 1f) else MergeColors[cur]
                    drawLine(
                        curCol.copy(alpha = 0.22f),
                        Offset(cx, world.hangY + cr),
                        Offset(cx, landY),
                        strokeWidth = 2.5f * sc,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * sc, 12f * sc), phase = -t * 40f * sc),
                    )
                    // Ghost-Frucht am Landepunkt, sanft pulsierend
                    val gp = 0.5f + 0.5f * sin(t * 5f)
                    drawCircle(curCol.copy(alpha = 0.07f + 0.05f * gp), cr, Offset(cx, landY))
                    drawCircle(
                        curCol.copy(alpha = 0.30f + 0.18f * gp),
                        cr,
                        Offset(cx, landY),
                        style = Stroke(width = max(2f, 2.5f * sc), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * sc, 8f * sc), phase = -t * 26f * sc)),
                    )
                    val sway = sin(t * 2.2f) * 0.06f
                    if (rainbow) {
                        drawMergeRainbowPro(cx, world.hangY, cr, t, sway, 0f, specPaint, emojiPaint, sc)
                    } else {
                        drawMergeFruitPro(cx, world.hangY, cr, cur, sway, 0f, bodyPaints[cur], rimPaints[cur], specPaint, emojiPaint, sc)
                    }
                } else if (!over && !won && world.current == MergeHandNone && world.cooldown > 0f) {
                    // Cooldown-Ring an der Hand-Position
                    val rr = world.radii[1]
                    val cx = world.aimX.coerceIn(world.left + rr, world.right - rr)
                    val prog = 1f - (world.cooldown / 0.55f).coerceIn(0f, 1f)
                    drawArc(
                        HikariAmber.copy(alpha = 0.45f),
                        startAngle = -90f,
                        sweepAngle = prog * 360f,
                        useCenter = false,
                        topLeft = Offset(cx - rr, world.hangY - rr),
                        size = Size(rr * 2f, rr * 2f),
                        style = Stroke(width = max(2f, 3f * sc)),
                    )
                }

                // Früchte (mit Squash & Stretch, Rotation, Countdown-Ring bei Gefahr)
                for (f in world.fruits) {
                    val popT = f.pop
                    val visR = f.r * (0.55f + 0.45f * popT) * (1f + 0.18f * sin(popT * PI.toFloat()))
                    if (f.rainbow) {
                        drawMergeRainbowPro(f.x + offX, f.y + offY, visR, t, f.angle, f.sq, specPaint, emojiPaint, sc)
                    } else {
                        drawMergeFruitPro(f.x + offX, f.y + offY, visR, f.level, f.angle, f.sq, bodyPaints[f.level], rimPaints[f.level], specPaint, emojiPaint, sc)
                    }
                    if (f.overTime > 0.08f) {
                        val prog = (f.overTime / 1.2f).coerceIn(0f, 1f)
                        val rr = visR + 8f * sc
                        val rc = Offset(f.x + offX, f.y + offY)
                        drawArc(
                            HikariDanger.copy(alpha = 0.25f),
                            startAngle = -90f, sweepAngle = 360f * prog, useCenter = false,
                            topLeft = Offset(rc.x - rr, rc.y - rr), size = Size(rr * 2f, rr * 2f),
                            style = Stroke(width = 7f * sc, cap = StrokeCap.Round),
                        )
                        drawArc(
                            HikariDanger,
                            startAngle = -90f, sweepAngle = 360f * prog, useCenter = false,
                            topLeft = Offset(rc.x - rr, rc.y - rr), size = Size(rr * 2f, rr * 2f),
                            style = Stroke(width = 3.5f * sc, cap = StrokeCap.Round),
                        )
                    }
                }

                // Shockwave-/Glow-Ringe
                for (r0 in world.rings) {
                    val lt = (r0.life / r0.maxLife).coerceIn(0f, 1f)
                    val rr = r0.maxR * (1f - lt * lt)
                    drawCircle(
                        r0.color.copy(alpha = 0.55f * lt),
                        rr,
                        Offset(r0.x + offX, r0.y + offY),
                        style = Stroke(width = r0.width * lt + 1f),
                    )
                }

                // Behälter-Wände: äußerer Glow + Hauptkontur + innerer Highlight-Stroke
                val corner = 28f * sc
                val wallPath = Path().apply {
                    moveTo(world.left + offX, world.top + offY)
                    lineTo(world.left + offX, world.bottom - corner + offY)
                    quadraticBezierTo(world.left + offX, world.bottom + offY, world.left + corner + offX, world.bottom + offY)
                    lineTo(world.right - corner + offX, world.bottom + offY)
                    quadraticBezierTo(world.right + offX, world.bottom + offY, world.right + offX, world.bottom - corner + offY)
                    lineTo(world.right + offX, world.top + offY)
                }
                drawPath(
                    wallPath,
                    (if (zen) Color(0xFFF59E0B) else MergeAccent).copy(alpha = 0.13f),
                    style = Stroke(width = max(12f, 16f * sc), cap = StrokeCap.Round),
                )
                drawPath(
                    wallPath,
                    if (zen) Color(0xFF5A4626) else Color(0xFF454052),
                    style = Stroke(width = max(6f, 8f * sc), cap = StrokeCap.Round),
                )
                drawPath(
                    wallPath,
                    Color.White.copy(alpha = 0.10f),
                    style = Stroke(width = max(1.5f, 2f * sc), cap = StrokeCap.Round),
                )

                // Gefahren-Meter am rechten Behälterrand
                val meterW = 7f * sc
                val meterX = world.right - meterW - 6f * sc + offX
                drawRoundRect(
                    HikariSurfaceHigh.copy(alpha = 0.5f),
                    Offset(meterX, world.limitY + offY),
                    Size(meterW, world.bottom - world.limitY),
                    androidx.compose.ui.geometry.CornerRadius(meterW / 2f, meterW / 2f),
                )
                if (world.fillLevel > 0.01f) {
                    val mh = (world.bottom - world.limitY) * world.fillLevel
                    val mc = when {
                        world.fillLevel > 0.8f -> HikariDanger
                        world.fillLevel > 0.55f -> HikariAmber
                        else -> Color(0xFF4ADE80)
                    }
                    drawRoundRect(
                        mc.copy(alpha = 0.75f),
                        Offset(meterX, world.bottom + offY - mh),
                        Size(meterW, mh),
                        androidx.compose.ui.geometry.CornerRadius(meterW / 2f, meterW / 2f),
                    )
                }

                // Große Combo-Anzeige am Spielfeldrand mit Timer-Ring
                if (world.chain >= 2 && world.chainTimer > 0f) {
                    val chainCol = if (world.chain >= 4) Color(0xFFEC4899) else HikariAmber
                    val ccx = world.right - 66f * sc + offX
                    val ccy = world.limitY + 60f * sc + offY
                    val cScale = 1f + 0.35f * world.chainPop
                    val ringR = 34f * sc * cScale
                    drawCircle(chainCol.copy(alpha = 0.12f), ringR, Offset(ccx, ccy))
                    drawArc(
                        chainCol.copy(alpha = 0.25f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(ccx - ringR, ccy - ringR), size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = 4f * sc, cap = StrokeCap.Round),
                    )
                    drawArc(
                        chainCol,
                        startAngle = -90f, sweepAngle = 360f * (world.chainTimer / 2f).coerceIn(0f, 1f), useCenter = false,
                        topLeft = Offset(ccx - ringR, ccy - ringR), size = Size(ringR * 2f, ringR * 2f),
                        style = Stroke(width = 4f * sc, cap = StrokeCap.Round),
                    )
                    popPaint.textSize = 34f * sc * cScale
                    popPaint.color = chainCol.toArgb()
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawText("×${world.chain}", ccx, ccy - (popPaint.ascent() + popPaint.descent()) / 2f, popPaint)
                    }
                }

                // Funken / Feuerwerk / Konfetti (Kreise, Streifen, Sterne)
                for (s in world.sparks) {
                    val a = (s.life / s.maxLife).coerceIn(0f, 1f)
                    when (s.shape) {
                        1 -> withTransform({
                            translate(s.x + offX, s.y + offY)
                            rotate(s.rot, Offset.Zero)
                        }) {
                            drawRect(
                                s.color.copy(alpha = a),
                                topLeft = Offset(-s.size * 1.3f, -s.size * 0.4f),
                                size = Size(s.size * 2.6f, s.size * 0.8f),
                            )
                        }
                        2 -> withTransform({
                            translate(s.x + offX, s.y + offY)
                            rotate(s.rot, Offset.Zero)
                            scale(s.size, s.size, Offset.Zero)
                        }) {
                            drawPath(starPath, s.color.copy(alpha = a))
                        }
                        else -> drawCircle(s.color.copy(alpha = a), s.size, Offset(s.x + offX, s.y + offY))
                    }
                }

                // Punkte-Popups: Scale-In (Overshoot) + Aufsteigen + Fade, goldene Großwerte
                for (p in world.pops) {
                    val age = p.maxLife - p.life
                    val inT = (age / 0.14f).coerceIn(0f, 1f)
                    val u = inT - 1f
                    val back = 1f + 2.70158f * u * u * u + 1.70158f * u * u
                    val alpha = (p.life / (p.maxLife * 0.45f)).coerceIn(0f, 1f)
                    popPaint.textSize = 42f * sc * p.sizeMul * back
                    popPaint.color = Color.Black.copy(alpha = 0.55f * alpha).toArgb()
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawText(p.text, p.x + 2f * sc, p.y + 2f * sc, popPaint)
                    }
                    popPaint.color = (if (p.golden) HikariAmber else Color.White).copy(alpha = alpha).toArgb()
                    drawIntoCanvas { c ->
                        c.nativeCanvas.drawText(p.text, p.x, p.y, popPaint)
                    }
                }

                // Weißer Flash bei großen Merges (nur Behälterbereich)
                if (world.flash > 0f) {
                    drawRect(
                        Color.White.copy(alpha = world.flash * 0.20f),
                        topLeft = Offset(world.left, world.top),
                        size = Size(world.right - world.left, world.bottom - world.top),
                    )
                }

                // Puls-Vignette bei akuter Gefahr (radial)
                if (!zen && (world.fillLevel > 0.8f || world.warn > 0f)) {
                    val strength = max((world.fillLevel - 0.8f) * 5f, world.warn)
                    val pulse = (0.5f + 0.5f * sin(t * 8f)) * strength
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, HikariDanger.copy(alpha = 0.22f * pulse)),
                            center = Offset(w / 2f, h / 2f),
                            radius = max(w, h) * 0.72f,
                        )
                    )
                }
            }

            // Evolutionsleiste am linken Rand
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 3.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (lvl in 9 downTo 0) {
                    Text(
                        MergeEmoji[lvl],
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .alpha(if (reachedMask and (1 shl lvl) != 0) 1f else 0.22f),
                    )
                }
            }

            // Meilenstein-Banner
            milestone?.let { (_, text) ->
                Box(Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text,
                        fontSize = 17.sp,
                        color = HikariPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xDD1F1F22))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }

            // Live-Rekord-Banner
            if (recordBanner) {
                Box(Modifier.fillMaxSize().padding(top = 14.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "✨ Neuer Rekord!",
                        fontSize = 15.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HikariPrimary)
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    )
                }
            }

            // Frucht-Namen-Popup
            namesPopup?.let { text ->
                Box(Modifier.fillMaxSize().padding(top = 46.dp, end = 10.dp), contentAlignment = Alignment.TopEnd) {
                    Text(
                        text,
                        fontSize = 12.sp,
                        color = HikariText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xEE28282C))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            // Erfolgs-Toast
            achToasts.firstOrNull()?.let { ach ->
                Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xEE28282C))
                            .border(1.dp, HikariPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ach.emoji, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Erfolg freigeschaltet!", fontSize = 10.sp, color = HikariPrimary)
                            Text(ach.title, fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

        }
        }

        // ————— Vollbild-Overlays (über Kopfzeile und Spielfeld) —————

        // Hilfe-Sheet
        if (showHelp) {
            val closeHelp = {
                showHelp = false
                prefs.edit().putBoolean("fruitmerge_help_seen", true).apply()
            }
            GxSheet("So geht's", MergeAccent, onClose = { closeHelp() }) {
                val lines = buildList {
                    add("👆 Ziehen zum Zielen, loslassen zum Fallenlassen.")
                    add("🍒 Zwei gleiche Früchte verschmelzen zur nächsten Stufe.")
                    add("⛓️ Schnelle Folge-Merges bilden Ketten: ×2 ab Kette 2, ×3 ab Kette 4.")
                    add("🌀 schüttelt den Behälter, 💥 entfernt die kleinste Frucht.")
                    add("🔀 tauscht die Hand-Frucht mit der nächsten, 💣 sprengt die drei kleinsten.")
                    add("🌈 verschmilzt mit der ersten berührten Frucht.")
                    when (mode) {
                        MergeMode.ZEN -> add("🧘 Zen: Kein Game Over — Überlauf löst sich sanft auf.")
                        MergeMode.CHALLENGE -> if (def != null) add("🎯 Ziel: ${mergeGoalText(def)}")
                        else -> add("⚠️ Über der Linie stapeln = Game Over. Viel Glück!")
                    }
                }
                for (l in lines) {
                    Text(l, fontSize = 13.sp, color = HikariText, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(14.dp))
                GxPrimaryButton("Los geht's", MergeAccent, Modifier.fillMaxWidth()) { closeHelp() }
            }
        }

        // Pause-Overlay
        if (paused && !over && !won && !showHelp && !confirmRestart) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 36.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF232326))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏸", fontSize = 28.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Pause", fontSize = 22.sp, color = HikariText, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(18.dp))
                    GxPrimaryButton("Weiter", MergeAccent, Modifier.fillMaxWidth()) { paused = false }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        GxGhostButton("Neustart", Modifier.weight(1f)) { confirmRestart = true }
                        GxGhostButton("Menü", Modifier.weight(1f)) {
                            if (!statsFlushed && world.time > 5f) flushRound(0)
                            onMenu()
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GxToggle("Haptik", null, MergeAccent, hapticsOn) {
                        hapticsOn = it
                        prefs.edit().putBoolean("fruitmerge_haptics", it).apply()
                    }
                    GxToggle("Sound", null, MergeAccent, soundOn) {
                        soundOn = it
                        prefs.edit().putBoolean("fruitmerge_sound", it).apply()
                    }
                    GxToggle("Reduzierte Effekte", null, MergeAccent, fxReduced) {
                        fxReduced = it
                        prefs.edit().putBoolean("fruitmerge_fx_reduced", it).apply()
                    }
                }
            }
        }

        // Neustart-Bestätigung
        if (confirmRestart) {
            GxConfirmDialog(
                title = "Neustart?",
                text = "Die laufende Runde geht verloren.",
                confirmLabel = "Neu starten",
                accent = MergeAccent,
                danger = true,
                onConfirm = {
                    confirmRestart = false
                    resetRound()
                },
                onDismiss = { confirmRestart = false },
            )
        }

        // Challenge gewonnen
        if (won && def != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF232326))
                        .border(1.dp, MergeAccent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Level geschafft!", fontSize = 22.sp, color = MergeAccent, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    GxStarRow(stars, size = 30.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("${gxAnimatedCount(score)}", fontSize = 34.sp, color = HikariText, fontWeight = FontWeight.Black)
                    Text("Punkte", fontSize = 11.sp, color = HikariTextMuted)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        GxStatTile("${world.drops}", "Drops", MergeAccent, Modifier.weight(1f))
                        GxStatTile("${world.mergesRound}", "Merges", MergeAccent, Modifier.weight(1f))
                        GxStatTile(mergeFmtTime(world.time.toInt()), "Zeit", MergeAccent, Modifier.weight(1f))
                    }
                    if (levelUpTo > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text("⬆ Stufe $levelUpTo erreicht!", fontSize = 14.sp, color = MergeAccent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (levelIdx < MergeLevels.size - 1) {
                        GxPrimaryButton("Nächstes Level", MergeAccent, Modifier.fillMaxWidth(), onClick = onNextLevel)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        GxGhostButton("Nochmal", Modifier.weight(1f)) { resetRound() }
                        GxGhostButton("Menü", Modifier.weight(1f), onClick = onMenu)
                    }
                }
            }
        }

        // Game Over / Level gescheitert
        if (over) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF232326))
                        .border(1.dp, HikariDanger.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (mode == MergeMode.CHALLENGE) "Nicht geschafft" else "Game Over",
                        fontSize = 24.sp, color = HikariDanger, fontWeight = FontWeight.Black,
                    )
                    if (mode == MergeMode.CHALLENGE && failReason.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(failReason, fontSize = 12.sp, color = HikariTextMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("${gxAnimatedCount(score)}", fontSize = 36.sp, color = HikariText, fontWeight = FontWeight.Black)
                    Text("Punkte", fontSize = 11.sp, color = HikariTextMuted)
                    Spacer(Modifier.height(6.dp))
                    if (mode == MergeMode.CLASSIC) {
                        if (newRecord) {
                            Text("✨ Neuer Rekord!", fontSize = 15.sp, color = MergeAccent, fontWeight = FontWeight.Black)
                        } else {
                            Text("Rekord: $highscore", fontSize = 13.sp, color = HikariTextMuted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        GxStatTile(mergeFmtTime(world.time.toInt()), "Zeit", MergeAccent, Modifier.weight(1f))
                        GxStatTile("${world.mergesRound}", "Merges", MergeAccent, Modifier.weight(1f))
                        GxStatTile("×${world.longestChain}", "Kette", MergeAccent, Modifier.weight(1f))
                        GxStatTile(MergeEmoji[world.bestFruit], "Höchste", MergeAccent, Modifier.weight(1f))
                    }
                    if (levelUpTo > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text("⬆ Stufe $levelUpTo erreicht!", fontSize = 14.sp, color = MergeAccent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(18.dp))
                    GxPrimaryButton("Nochmal", MergeAccent, Modifier.fillMaxWidth()) { resetRound() }
                    Spacer(Modifier.height(10.dp))
                    GxGhostButton("Zum Menü", Modifier.fillMaxWidth(), onClick = onMenu)
                }
            }
        }
    }
}

// Booster-Button: Ladering zeigt Fortschritt zur nächsten Ladung, Zähler unten rechts.
@Composable
private fun MergePowerButton(emoji: String, count: Int, fill: Float, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .gxPressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GxProgressRing(
            frac = if (count > 0) 1f else fill,
            accent = MergeAccent,
            size = 46.dp,
            stroke = 3.dp,
        ) {
            Text(emoji, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text(
                "$count",
                fontSize = 9.sp,
                color = if (enabled) MergeAccent else HikariTextFaint,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
            )
        }
    }
}

// Frucht (AA-Rendering): radialer Verlauf mit Licht von oben-links, dunkler Rim,
// weicher Specular — gezeichnet als rotierte Ellipse (Squash & Stretch).
// Die gecachten Shader leben im Einheitskreis; die Canvas-Transform skaliert sie.
private fun DrawScope.drawMergeFruitPro(
    x: Float,
    y: Float,
    r: Float,
    level: Int,
    angle: Float,
    sq: Float,
    bodyPaint: Paint,
    rimPaint: Paint,
    specPaint: Paint,
    emojiPaint: Paint,
    sc: Float,
) {
    val sx = 1f + sq * 0.55f
    val sy = 1f - sq * 0.50f
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        nc.save()
        nc.translate(x, y)
        nc.rotate(angle * 57.29578f)
        nc.scale(r * sx, r * sy)
        nc.drawCircle(0f, 0f, 1f, bodyPaint)
        rimPaint.strokeWidth = (2.6f * sc) / r
        nc.drawCircle(0f, 0f, 0.985f, rimPaint)
        nc.drawCircle(0f, 0f, 1f, specPaint)
        nc.restore()
        // Emoji: gedämpft mitrotiert und gesquasht
        nc.save()
        nc.translate(x, y)
        nc.rotate(angle * 34f)
        nc.scale(1f + sq * 0.28f, 1f - sq * 0.25f)
        emojiPaint.textSize = r * 1.15f
        val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
        nc.drawText(MergeEmoji[level], 0f, -yOff, emojiPaint)
        nc.restore()
    }
}

// Regenbogen-Frucht: rotierender Sweep-Gradient + Specular + Glanz, Squash-fähig
private fun DrawScope.drawMergeRainbowPro(
    x: Float,
    y: Float,
    r: Float,
    time: Float,
    angle: Float,
    sq: Float,
    specPaint: Paint,
    paint: Paint,
    sc: Float,
) {
    val hue = (time * 150f) % 360f
    val cols = List(9) { i -> Color.hsv((hue + i * 45f) % 360f, 0.72f, 1f) }
    withTransform({
        translate(x, y)
        rotate(time * 130f + angle * 57.29578f, Offset.Zero)
        scale(r * (1f + sq * 0.5f), r * (1f - sq * 0.45f), Offset.Zero)
    }) {
        drawCircle(Brush.sweepGradient(cols, Offset.Zero), radius = 1f, center = Offset.Zero)
    }
    drawCircle(Color.White.copy(alpha = 0.35f), radius = r, center = Offset(x, y), style = Stroke(width = max(2f, 3f * sc)))
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        nc.save()
        nc.translate(x, y)
        nc.scale(r, r)
        nc.drawCircle(0f, 0f, 1f, specPaint)
        nc.restore()
        nc.save()
        nc.translate(x, y)
        nc.rotate(angle * 34f)
        paint.textSize = r * 1.05f
        val yOff = (paint.ascent() + paint.descent()) / 2f
        nc.drawText("🌈", 0f, -yOff, paint)
        nc.restore()
    }
}
