package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.ui.theme.*
import java.time.LocalDate
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay

// ————— Modi / Screens / Objekt-Arten —————

private enum class HoleMode { KLASSIK, RUSH, WELTEN }
private enum class HoleScreen { MENU, PLAY, WELTWAHL, STATS, ERFOLGE }
private enum class HoleKind { FRUCHT, BOMBE, GOLD, HERZ, UHR, MAGNET, REGENBOGEN }

// Pseudo-3D-Formen der Map-Objekte
private enum class HoleShape { BALL, TOWER, PYRAMID, BOX, TREE, SPECIAL }

// Akzentfarbe des Spiels (lila) — konsistent mit der Karte im GamesScreen
private val HoleAccent = Color(0xFFA78BFA)

// Farb-Helfer
private fun holeTint(c: Color, f: Float): Color = Color(
    c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f,
)
private fun holeShade(c: Color, f: Float): Color = Color(c.red * f, c.green * f, c.blue * f)

// ————— Themen: helle, gesättigte hole.io-Paletten mit Gras-Karo —————

private class HoleTheme(
    val name: String,
    val grassA: Color,   // Karo hell
    val grassB: Color,   // Karo dunkel
    val palette: List<Color>, // Objekt-Farben
    val fruits: List<String>, // Emoji für Frucht-Objekte
    val glow: Color,     // Loch-Rand / Akzente
)

private val HoleThemes = listOf(
    HoleTheme(
        "Süßigkeitenland", Color(0xFF8BC34A), Color(0xFF7CB342),
        listOf(Color(0xFFEC4899), Color(0xFFFBBF24), Color(0xFF60A5FA), Color(0xFF4ADE80), Color(0xFFA78BFA)),
        listOf("🍩", "🧁", "🍬", "🍰", "🍭"),
        Color(0xFFF472B6),
    ),
    HoleTheme(
        "Picknick-Wiese", Color(0xFF9CCC65), Color(0xFF8BC34A),
        listOf(Color(0xFFEF5350), Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFFFF8A65), Color(0xFFAB47BC)),
        listOf("🍎", "🍓", "🍇", "🍉", "🥪"),
        Color(0xFFEF5350),
    ),
    HoleTheme(
        "Kleinstadt", Color(0xFF66BB6A), Color(0xFF57A85C),
        listOf(Color(0xFF90A4AE), Color(0xFFFFB74D), Color(0xFF4FC3F7), Color(0xFFE57373), Color(0xFF81C784)),
        listOf("🏠", "🚗", "🌳", "📦", "⛲"),
        Color(0xFF4FC3F7),
    ),
)

// ————— Welten-Reise: 4 Welten mit eigener Mechanik + Missionen —————

private class HoleMissionDef(val desc: String, val kind: Int, val target: Int) // 0=Früchte 1=Combo 2=Entschärft 3=Powerups 4=Punkte 5=Gold

private class HoleWorldDef(
    val name: String,
    val emoji: String,
    val mechanik: String,
    val grassA: Color,
    val grassB: Color,
    val palette: List<Color>,
    val fruits: List<String>,
    val glow: Color,
    val missions: List<HoleMissionDef>,
)

private val HoleWorlds = listOf(
    HoleWorldDef(
        "Wiese", "🌿", "Sanfter Einstieg",
        Color(0xFF8BC34A), Color(0xFF7CB342),
        listOf(Color(0xFFEF5350), Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFF60A5FA)),
        listOf("🍎", "🍓", "🍇", "🍉"),
        Color(0xFF86EFAC),
        listOf(
            HoleMissionDef("30 Objekte in einer Runde", 0, 30),
            HoleMissionDef("Combo 8 erreichen", 1, 8),
            HoleMissionDef("300 Punkte in einer Runde", 4, 300),
        ),
    ),
    HoleWorldDef(
        "Wüste", "🏜️", "Seitenwind treibt das Loch ab",
        Color(0xFFEBD79A), Color(0xFFE3C983),
        listOf(Color(0xFFFF8A65), Color(0xFFFBBF24), Color(0xFFAB47BC), Color(0xFF66BB6A)),
        listOf("🌵", "🍊", "🍋", "🥥"),
        Color(0xFFFBBF24),
        listOf(
            HoleMissionDef("40 Objekte in einer Runde", 0, 40),
            HoleMissionDef("2 Bomben entschärfen", 2, 2),
            HoleMissionDef("Combo 10 erreichen", 1, 10),
        ),
    ),
    HoleWorldDef(
        "Schnee", "❄️", "Das Loch rutscht nach",
        Color(0xFFE8F1F8), Color(0xFFD8E7F2),
        listOf(Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFEF5350), Color(0xFFA78BFA)),
        listOf("⛄", "🎁", "🍏", "🍐"),
        Color(0xFF7DD3FC),
        listOf(
            HoleMissionDef("500 Punkte in einer Runde", 4, 500),
            HoleMissionDef("3 Power-ups einsammeln", 3, 3),
            HoleMissionDef("50 Objekte in einer Runde", 0, 50),
        ),
    ),
    HoleWorldDef(
        "Weltraum", "🌌", "Schwacher Sog, Objekte treiben",
        Color(0xFF3B3663), Color(0xFF322D57),
        listOf(Color(0xFFA78BFA), Color(0xFF22D3EE), Color(0xFFF472B6), Color(0xFFFBBF24)),
        listOf("🪐", "🚀", "⭐", "🛸"),
        Color(0xFFA78BFA),
        listOf(
            HoleMissionDef("Combo 12 erreichen", 1, 12),
            HoleMissionDef("2 Gold-Objekte fangen", 5, 2),
            HoleMissionDef("800 Punkte in einer Runde", 4, 800),
        ),
    ),
)

// ————— Erfolge (String-Keys; alte unverändert, neue nur ergänzt) —————

private class HoleAchDef(val id: String, val emoji: String, val title: String, val desc: String)

private val HoleAchs = listOf(
    HoleAchDef("combo20", "🔥", "Combo-König", "Erreiche Combo 20"),
    HoleAchDef("level10", "🚀", "Marathon", "Erreiche Level 10 im Klassik-Modus"),
    HoleAchDef("entschaerft", "✂️", "Entschärfer", "Entschärfe eine Bombe per Fingertipp"),
    HoleAchDef("sammler", "🎒", "Sammler", "5 Power-ups in einer Runde"),
    HoleAchDef("rush500", "⏱️", "Rush-Meister", "500 Punkte in Rush Hour"),
    HoleAchDef("weltmeister", "🌍", "Weltenbummler", "Alle Missionen einer Welt geschafft"),
    HoleAchDef("vielfrass", "🍽️", "Vielfraß", "100 Objekte in einer Runde"),
    HoleAchDef("fieber", "🌡️", "Fieber!", "Fieber-Modus aktiviert (Combo 12)"),
    HoleAchDef("gold10", "🌟", "Goldgräber", "10 goldene Objekte insgesamt"),
    HoleAchDef("punkte1000", "🏆", "Punktejäger", "1000 Punkte in einer Runde"),
    HoleAchDef("wachstum5", "🕳️", "Schwarzes Loch", "Erreiche Größenstufe 5"),
    HoleAchDef("big20", "🏙️", "Großstadt-Appetit", "Verschlucke 20 große Objekte (ab Klasse 2) in einer Runde"),
)

private val HoleSens = floatArrayOf(0.8f, 1.15f, 1.6f)

// Loch-Größenstufen: nötige Wachstums-Punkte (kumuliert) → Radius-Multiplikator
private val HoleGrowNeed = intArrayOf(8, 20, 40, 70, 110, 160)
private val HoleGrowMul = floatArrayOf(1.0f, 1.22f, 1.45f, 1.72f, 2.0f, 2.3f)

// ————— Persistenz-Helfer —————

private fun holeLevelFromXp(xp: Int): Pair<Int, Float> {
    var lvl = 1
    var rem = xp
    while (rem >= lvl * 150) {
        rem -= lvl * 150
        lvl++
    }
    return lvl to rem / (lvl * 150f)
}

private fun holeAddTopScore(prefs: SharedPreferences, score: Int, label: String) {
    if (score <= 0) return
    val raw = prefs.getString("fruithole_top5", "") ?: ""
    val entries = raw.split(";").filter { it.isNotBlank() }.toMutableList()
    entries.add("$score|$label")
    val sorted = entries.sortedByDescending { it.substringBefore("|").toIntOrNull() ?: 0 }.take(5)
    prefs.edit().putString("fruithole_top5", sorted.joinToString(";")).apply()
}

private fun holeTop5(prefs: SharedPreferences): List<Pair<Int, String>> {
    val raw = prefs.getString("fruithole_top5", "") ?: ""
    return raw.split(";").filter { it.isNotBlank() }.map {
        (it.substringBefore("|").toIntOrNull() ?: 0) to it.substringAfter("|", "")
    }
}

private fun holeZeitFmt(sek: Int): String {
    val h = sek / 3600
    val m = (sek % 3600) / 60
    return if (h > 0) "${h}h ${m}min" else if (m > 0) "${m}min" else "${sek}s"
}

// ————— Prozeduraler Mini-Synth via AudioTrack (keine Assets) —————

private class HoleSound {
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
            val env = exp(-t * 16.0)
            (sin(2 * kotlin.math.PI * freq * t) * 0.72 + sin(2 * kotlin.math.PI * freq * 2 * t) * 0.28) * env
        }

    // Schlürf: abfallender Chirp — tiefer je größer das Objekt
    fun slurp(cls: Int) = play(synth(0.14f, 0.26f) { t ->
        val f0 = 640.0 - cls * 80.0
        val f = f0 - t * (2200.0 - cls * 200.0)
        sin(2 * kotlin.math.PI * f * t) * exp(-t * 26.0)
    })

    // Wachstums-Fanfare (neue Größenstufe)
    fun grow() = play(concat(note(392.0, 0.09f, 0.22f), note(523.0, 0.09f, 0.22f), note(659.0, 0.16f, 0.26f)))

    // Bombe: Noise-Burst + tiefer Drop
    fun boom() = play(synth(0.5f, 0.36f) { t ->
        val env = exp(-t * 9.0)
        val noise = (Random.nextDouble() * 2.0 - 1.0) * exp(-t * 16.0) * 0.5
        (sin(2 * kotlin.math.PI * (75.0 - t * 65.0) * t) * 0.9 + noise) * env
    })

    // Entschärfen: kurzer Klick
    fun defuse() = play(synth(0.07f, 0.22f) { t ->
        val f = 700.0 - t * 4000.0
        sin(2 * kotlin.math.PI * f * t) * exp(-t * 50.0)
    })

    // Power-up-Pling (Pitch je Art)
    fun power(kind: Int) = play(note(470.0 + kind * 90.0, 0.16f, 0.26f))

    // Zu groß: dumpfes Abblocken
    fun blocked() = play(synth(0.08f, 0.16f) { t ->
        sin(2 * kotlin.math.PI * 150.0 * t) * exp(-t * 36.0)
    })

    fun gameOver() = play(concat(note(392.0, 0.16f, 0.24f), note(311.0, 0.16f, 0.24f), note(233.0, 0.30f, 0.26f)))

    fun win() = play(concat(note(523.0, 0.11f, 0.24f), note(659.0, 0.11f, 0.24f), note(784.0, 0.11f, 0.24f), note(1046.0, 0.24f, 0.28f)))
}

// ————— Gecachte Unit-Space-Paints (Gradient liegt bei (0,0), Skalierung per Transform) —————

private class HolePaintCache {
    private val ball = HashMap<Int, Paint>()
    private val side = HashMap<Int, Paint>()
    private val top = HashMap<Int, Paint>()

    fun ballFor(c: Color): Paint = ball.getOrPut(c.toArgb()) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                -0.32f, -0.36f, 1.4f,
                intArrayOf(holeTint(c, 0.55f).toArgb(), c.toArgb(), holeShade(c, 0.45f).toArgb()),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    fun sideFor(c: Color): Paint = side.getOrPut(c.toArgb()) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -1f, 0f, 0.6f,
                intArrayOf(holeTint(c, 0.30f).toArgb(), holeShade(c, 0.62f).toArgb()),
                null, Shader.TileMode.CLAMP,
            )
        }
    }

    fun topFor(c: Color): Paint = top.getOrPut(c.toArgb()) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                -0.25f, -0.35f, 1.3f,
                intArrayOf(holeTint(c, 0.62f).toArgb(), holeTint(c, 0.18f).toArgb()),
                null, Shader.TileMode.CLAMP,
            )
        }
    }
}

// ————— Entities —————

private class HoleItem(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val r: Float,               // Basis-Radius in Welt-Einheiten
    val cls: Int,               // Größenklasse 0..5
    val emoji: String,
    val kind: HoleKind,
    val shape: HoleShape,
    val color: Color,
) {
    var swallowing = false
    var swallow = 0f
    var wobble = 0f
    var sinkX = 0f
    var sinkY = 0f
}

private class HolePop(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float,
    val bad: Boolean,
    val big: Boolean = false,
    val maxLife: Float = life,
)

private class HoleSpark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
)

private class HoleWorld(val den: Float) {
    var w = 0f
    var h = 0f
    var initialized = false

    // Map (Welt-Koordinaten)
    var mapW = 0f
    var mapH = 0f
    val items = ArrayList<HoleItem>()

    // Loch
    var holeX = 0f
    var holeY = 0f
    var holeTargetX = 0f
    var holeTargetY = 0f
    var holeVx = 0f
    var holeVy = 0f
    var holeR = 0f
    var holeTargetR = 0f
    var baseR = 0f

    // Kamera (Smooth-Follow + Zoom nach Lochgröße)
    var camX = 0f
    var camY = 0f
    var zoom = 1f

    // Wachstum
    var growPts = 0
    var holeLevel = 1

    val pops = ArrayList<HolePop>()
    val sparks = ArrayList<HoleSpark>()
    var flash = 0f
    var shake = 0f
    var time = 0f
    var runTime = 0f
    var wind = 0f
    var feverFx = 0f
    var sogFx = 0f
    var tooBigCd = 0f
    var comboTimer = 0f
    var rng: Random = Random.Default

    // HUD-sichtbare Zustände (Compose-States, damit die Anzeige live folgt)
    var eaten by mutableIntStateOf(0)
    var defusedRun by mutableIntStateOf(0)
    var goldRun by mutableIntStateOf(0)
    var powerupsRun by mutableIntStateOf(0)
    var bigRun by mutableIntStateOf(0)
    var bestComboRun by mutableIntStateOf(0)
    var slowTimer by mutableFloatStateOf(0f)
    var magnetTimer by mutableFloatStateOf(0f)
    var rainbowTimer by mutableFloatStateOf(0f)
    var fever by mutableStateOf(false)
}

// ————— Map-Generator: Ringe, Spiralen, Pyramiden, Stadtviertel —————

private fun holeItem(
    x: Float, y: Float, cls: Int, shape: HoleShape, color: Color,
    emoji: String, kind: HoleKind = HoleKind.FRUCHT, den: Float,
): HoleItem {
    val r = when (cls) {
        0 -> 15f; 1 -> 20f; 2 -> 26f; 3 -> 34f; 4 -> 44f; else -> 56f
    } * den
    return HoleItem(x, y, r = r, cls = cls, emoji = emoji, kind = kind, shape = shape, color = color)
}

private fun holeGenMap(world: HoleWorld, theme: HoleTheme, mode: HoleMode, weltIdx: Int) {
    val den = world.den
    val rng = world.rng
    world.mapW = (if (mode == HoleMode.RUSH) 1350f else 1500f) * den
    world.mapH = (if (mode == HoleMode.RUSH) 1750f else 2050f) * den
    world.baseR = 30f * den
    world.holeR = world.baseR
    world.holeTargetR = world.baseR
    world.items.clear()

    val mw = world.mapW
    val mh = world.mapH
    val pal = theme.palette
    val fruits = theme.fruits

    fun fruit(cls: Int, x: Float, y: Float) =
        holeItem(x, y, cls, HoleShape.BALL, pal[rng.nextInt(pal.size)], fruits[rng.nextInt(fruits.size)], den = den)

    fun tower(cls: Int, x: Float, y: Float) =
        holeItem(x, y, cls, HoleShape.TOWER, pal[rng.nextInt(pal.size)], fruits[rng.nextInt(fruits.size)], den = den)

    fun pyramid(x: Float, y: Float) =
        holeItem(x, y, 3, HoleShape.PYRAMID, pal[rng.nextInt(pal.size)], fruits[rng.nextInt(fruits.size)], den = den)

    fun building(cls: Int, x: Float, y: Float) =
        holeItem(x, y, cls, HoleShape.BOX, pal[rng.nextInt(pal.size)], "🏠", den = den)

    fun tree(x: Float, y: Float) =
        holeItem(x, y, 2, HoleShape.TREE, Color(0xFF388E3C), "🌳", den = den)

    // Ring aus kleinen Objekten
    fun ring(cx: Float, cy: Float, rad: Float, n: Int, cls: Int) {
        for (i in 0 until n) {
            val a = i.toFloat() / n * 6.2832f
            world.items.add(fruit(cls, cx + cos(a) * rad, cy + sin(a) * rad))
        }
    }

    // Spirale ("oddly satisfying")
    fun spiral(cx: Float, cy: Float, n: Int) {
        for (i in 0 until n) {
            val a = i * 0.55f
            val rad = (i * 9f + 20f) * den
            world.items.add(fruit(if (i % 5 == 4) 1 else 0, cx + cos(a) * rad, cy + sin(a) * rad))
        }
    }

    // Kuchen-Pyramide (Dreiecksgitter)
    fun cakePile(cx: Float, cy: Float, rows: Int) {
        for (r in 0 until rows) {
            for (i in 0..r) {
                val x = cx + (i - r / 2f) * 34f * den
                val y = cy + r * 30f * den
                world.items.add(tower(if (r == 0) 1 else 0, x, y))
            }
        }
    }

    // Stadtviertel (Gebäude-Raster + Bäume am Rand)
    fun district(cx: Float, cy: Float, cols: Int, rows: Int) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = cx + (c - (cols - 1) / 2f) * 120f * den
                val y = cy + (r - (rows - 1) / 2f) * 130f * den
                if ((r + c) % 3 == 2) world.items.add(tree(x + 40f * den, y + 45f * den))
                world.items.add(building(if ((r + c) % 2 == 0) 4 else 5, x, y))
            }
        }
    }

    // Mischung je nach Modus
    val clusters = if (mode == HoleMode.RUSH) 5 else 4
    for (k in 0 until clusters) {
        val cx = mw * (0.18f + rng.nextFloat() * 0.64f)
        val cy = mh * (0.15f + rng.nextFloat() * 0.7f)
        when (rng.nextInt(4)) {
            0 -> ring(cx, cy, (60f + rng.nextFloat() * 60f) * den, 8 + rng.nextInt(6), rng.nextInt(2))
            1 -> spiral(cx, cy, 12 + rng.nextInt(8))
            2 -> cakePile(cx, cy, 3 + rng.nextInt(2))
            else -> district(cx, cy, 2 + rng.nextInt(2), 2 + rng.nextInt(2))
        }
    }
    // Lose Streuung
    val loose = if (mode == HoleMode.RUSH) 60 else 42
    repeat(loose) {
        world.items.add(
            fruit(
                if (rng.nextFloat() < 0.15f) 1 else 0,
                mw * (0.06f + rng.nextFloat() * 0.88f),
                mh * (0.06f + rng.nextFloat() * 0.88f),
            )
        )
    }
    repeat(if (mode == HoleMode.RUSH) 6 else 8) {
        world.items.add(tree(mw * rng.nextFloat(), mh * rng.nextFloat()))
    }
    repeat(3) {
        world.items.add(pyramid(mw * (0.15f + rng.nextFloat() * 0.7f), mh * (0.15f + rng.nextFloat() * 0.7f)))
    }

    // Spezial-Objekte
    fun special(kind: HoleKind, emoji: String, color: Color, n: Int) {
        repeat(n) {
            world.items.add(
                holeItem(
                    mw * (0.08f + rng.nextFloat() * 0.84f),
                    mh * (0.08f + rng.nextFloat() * 0.84f),
                    1, HoleShape.SPECIAL, color, emoji, kind, den,
                )
            )
        }
    }
    special(HoleKind.BOMBE, "💣", Color(0xFF37474F), if (mode == HoleMode.RUSH) 8 else 6)
    special(HoleKind.GOLD, "🌟", Color(0xFFFBBF24), 3)
    if (mode != HoleMode.RUSH) special(HoleKind.HERZ, "❤️", Color(0xFFEF5350), 2)
    special(HoleKind.UHR, "⏰", Color(0xFF22D3EE), 2)
    special(HoleKind.MAGNET, "🌀", Color(0xFF60A5FA), 2)
    special(HoleKind.REGENBOGEN, "🌈", Color(0xFFA78BFA), 2)

    // Loch startet in der Map-Mitte
    world.holeX = mw / 2f
    world.holeY = mh / 2f
    world.holeTargetX = world.holeX
    world.holeTargetY = world.holeY
    world.holeVx = 0f
    world.holeVy = 0f
    world.camX = world.holeX
    world.camY = world.holeY
    world.growPts = 0
    world.holeLevel = 1
}

// Nachschub-Cluster, wenn die Map leergefressen ist (Klassik/Rush)
private fun holeRegen(world: HoleWorld, theme: HoleTheme) {
    val rng = world.rng
    val den = world.den
    val mw = world.mapW
    val mh = world.mapH
    // Freie Stelle abseits des Lochs suchen
    var cx = mw / 2f
    var cy = mh / 4f
    repeat(8) {
        val tx = mw * (0.12f + rng.nextFloat() * 0.76f)
        val ty = mh * (0.12f + rng.nextFloat() * 0.76f)
        val dx = tx - world.holeX
        val dy = ty - world.holeY
        if (dx * dx + dy * dy > (400f * den) * (400f * den)) {
            cx = tx
            cy = ty
            return@repeat
        }
    }
    val pal = theme.palette
    val fruits = theme.fruits
    val n = 12 + rng.nextInt(6)
    when (rng.nextInt(3)) {
        0 -> for (i in 0 until n) {
            val a = i.toFloat() / n * 6.2832f
            world.items.add(
                holeItem(
                    cx + cos(a) * 70f * den, cy + sin(a) * 70f * den, rng.nextInt(2), HoleShape.BALL,
                    pal[rng.nextInt(pal.size)], fruits[rng.nextInt(fruits.size)], den = den,
                )
            )
        }
        1 -> for (i in 0 until n) {
            val a = i * 0.55f
            val rad = (i * 9f + 20f) * den
            world.items.add(
                holeItem(
                    cx + cos(a) * rad, cy + sin(a) * rad, if (i % 5 == 4) 1 else 0, HoleShape.BALL,
                    pal[rng.nextInt(pal.size)], fruits[rng.nextInt(fruits.size)], den = den,
                )
            )
        }
        else -> for (i in 0 until n) {
            world.items.add(
                holeItem(
                    cx + (rng.nextFloat() - 0.5f) * 180f * den, cy + (rng.nextFloat() - 0.5f) * 180f * den,
                    rng.nextInt(2), HoleShape.BALL, pal[rng.nextInt(pal.size)],
                    fruits[rng.nextInt(fruits.size)], den = den,
                )
            )
        }
    }
}

@Composable
fun FruitHoleGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    val den = LocalDensity.current.density

    // Navigation innerhalb des Spiels
    var screen by remember { mutableStateOf(HoleScreen.MENU) }
    var mode by remember {
        mutableStateOf(
            runCatching { HoleMode.valueOf(prefs.getString("fruithole_last_mode", "KLASSIK") ?: "KLASSIK") }
                .getOrDefault(HoleMode.KLASSIK)
        )
    }
    var daily by remember { mutableStateOf(false) }
    var worldIdx by remember { mutableIntStateOf(0) }

    // Einstellungen (persistiert)
    var haptikOn by remember { mutableStateOf(prefs.getBoolean("fruithole_set_haptik", true)) }
    var fxReduziert by remember { mutableStateOf(prefs.getBoolean("fruithole_set_fx", false)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("fruithole_sound", true)) }
    var sensIdx by remember { mutableIntStateOf(prefs.getInt("fruithole_set_sens", 1).coerceIn(0, 2)) }
    var direktSteuerung by remember { mutableStateOf(prefs.getBoolean("fruithole_set_direkt", false)) }

    // Runden-Zustand
    var score by remember { mutableIntStateOf(0) }
    var shownScore by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var level by remember { mutableIntStateOf(1) }
    var combo by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var countdown by remember { mutableFloatStateOf(0f) }
    var timeLeft by remember { mutableFloatStateOf(60f) }
    var runRecord by remember { mutableIntStateOf(0) }
    var recordBroken by remember { mutableStateOf(false) }
    var showRecordBanner by remember { mutableStateOf(false) }
    var missionDone by remember { mutableStateOf(BooleanArray(3)) }

    // Overlays
    var showSettings by remember { mutableStateOf(false) }
    var showRestart by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(!prefs.getBoolean("fruithole_help_seen", false)) }
    val achToasts = remember { mutableStateListOf<String>() }

    var xp by remember { mutableIntStateOf(prefs.getInt("fruithole_xp", 0)) }

    val world = remember { HoleWorld(den) }
    val holeSound = remember { HoleSound() }
    DisposableEffect(Unit) {
        onDispose { holeSound.release() }
    }
    val paints = remember { HolePaintCache() }
    val emojiPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true } }
    val popPaint = remember { Paint().apply { textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true } }

    // Kombo-Fenster: schnelle Fress-Serien halten die Kombo (Timer in der World)

    val multiplier = if (combo >= 8) 3 else if (combo >= 4) 2 else 1

    fun buzz(type: HapticFeedbackType) {
        if (haptikOn) haptic.performHapticFeedback(type)
    }

    fun snd(f: HoleSound.() -> Unit) {
        if (soundOn) holeSound.f()
    }

    fun unlock(id: String) {
        if (prefs.getBoolean("fruithole_ach_$id", false)) return
        prefs.edit().putBoolean("fruithole_ach_$id", true).apply()
        val a = HoleAchs.firstOrNull { it.id == id } ?: return
        achToasts.add("${a.emoji} Erfolg: ${a.title}")
        buzz(HapticFeedbackType.LongPress)
    }

    fun hsKey(m: HoleMode, isDaily: Boolean): String = when {
        m == HoleMode.RUSH -> "fruithole_rush_highscore"
        m == HoleMode.WELTEN -> "fruithole_world_highscore"
        isDaily -> "fruithole_daily_hs_${LocalDate.now().toEpochDay()}"
        else -> "fruithole_highscore"
    }

    fun modeLabel(): String = when {
        mode == HoleMode.RUSH -> "Rush Hour"
        mode == HoleMode.WELTEN -> HoleWorlds[worldIdx].name
        daily -> "Daily-Challenge"
        else -> "Klassik"
    }

    fun activeTheme(): HoleTheme = if (mode == HoleMode.WELTEN) {
        val w = HoleWorlds[worldIdx]
        HoleTheme(w.name, w.grassA, w.grassB, w.palette, w.fruits, w.glow)
    } else {
        HoleThemes[((level - 1) / 3) % HoleThemes.size]
    }

    fun startRun(m: HoleMode, isDaily: Boolean, wIdx: Int) {
        mode = m
        daily = isDaily
        worldIdx = wIdx
        prefs.edit().putString("fruithole_last_mode", m.name).apply()
        world.pops.clear()
        world.sparks.clear()
        world.eaten = 0
        world.defusedRun = 0
        world.goldRun = 0
        world.powerupsRun = 0
        world.bigRun = 0
        world.bestComboRun = 0
        world.slowTimer = 0f
        world.magnetTimer = 0f
        world.rainbowTimer = 0f
        world.fever = false
        world.flash = 0f
        world.shake = 0f
        world.runTime = 0f
        world.wind = 0f
        world.feverFx = 0f
        world.sogFx = 0f
        world.tooBigCd = 0f
        world.comboTimer = 0f
        world.rng = if (isDaily) Random(LocalDate.now().toEpochDay()) else Random.Default
        score = 0
        shownScore = 0
        lives = 3
        level = 1
        combo = 0
        timeLeft = 60f
        missionDone = BooleanArray(3) { prefs.getBoolean("fruithole_w${wIdx}_m$it", false) }
        runRecord = prefs.getInt(hsKey(m, isDaily), 0)
        recordBroken = false
        showRecordBanner = false
        newRecord = false
        gameOver = false
        paused = false
        countdown = 3f
        if (world.initialized) holeGenMap(world, activeTheme(), m, wIdx)
        restartKey++
        screen = HoleScreen.PLAY
    }

    fun finishRun() {
        if (gameOver) return
        val key = hsKey(mode, daily)
        val hs = prefs.getInt(key, 0)
        if (score > hs) {
            newRecord = true
            prefs.edit().putInt(key, score).apply()
        }
        val e = prefs.edit()
        e.putInt("fruithole_stat_runden", prefs.getInt("fruithole_stat_runden", 0) + 1)
        e.putInt("fruithole_stat_fruechte", prefs.getInt("fruithole_stat_fruechte", 0) + world.eaten)
        if (world.bestComboRun > prefs.getInt("fruithole_stat_combo", 0)) e.putInt("fruithole_stat_combo", world.bestComboRun)
        e.putInt("fruithole_stat_entschaerft", prefs.getInt("fruithole_stat_entschaerft", 0) + world.defusedRun)
        e.putInt("fruithole_stat_powerups", prefs.getInt("fruithole_stat_powerups", 0) + world.powerupsRun)
        if (level > prefs.getInt("fruithole_stat_level", 0)) e.putInt("fruithole_stat_level", level)
        e.putInt("fruithole_stat_zeit", prefs.getInt("fruithole_stat_zeit", 0) + world.runTime.toInt())
        e.apply()
        val d = LocalDate.now()
        holeAddTopScore(prefs, score, "${d.dayOfMonth}.${d.monthValue}. · ${modeLabel()}")
        val lvlVorher = holeLevelFromXp(xp).first
        xp += score / 10
        prefs.edit().putInt("fruithole_xp", xp).apply()
        val lvlNachher = holeLevelFromXp(xp).first
        if (lvlNachher > lvlVorher) achToasts.add("⬆️ Spieler-Level $lvlNachher erreicht!")
        if (mode == HoleMode.RUSH && score >= 500) unlock("rush500")
        buzz(HapticFeedbackType.LongPress)
        snd { gameOver() }
        gameOver = true
    }

    fun collectFx(x: Float, y: Float, col: Color) {
        if (fxReduziert) return
        repeat(8) {
            world.sparks.add(
                HoleSpark(
                    x, y,
                    (Random.nextFloat() * 2f - 1f) * 200f * den,
                    (Random.nextFloat() * 2f - 1f) * 200f * den,
                    0.4f, 0.4f, col, (3f + Random.nextFloat() * 4f) * den,
                )
            )
        }
    }

    // Objekt verschluckt: Punkte, Kombo, Wachstum, Effekte
    fun essen(item: HoleItem) {
        combo += 1
        world.comboTimer = 2.5f
        if (combo > world.bestComboRun) world.bestComboRun = combo
        if (combo >= 12 && !world.fever) {
            world.fever = true
            world.pops.add(HolePop(world.holeX, world.holeY - world.holeR * 2.4f, "🔥 Fieber! Alles ×2", 1.4f, bad = false, big = true))
            unlock("fieber")
            snd { grow() }
        }
        if (combo >= 20) unlock("combo20")
        val mult = if (combo >= 8) 3 else if (combo >= 4) 2 else 1
        val basis = when (item.kind) {
            HoleKind.GOLD -> 50
            else -> 10 * (item.cls + 1)
        }
        var pts = basis * mult
        if (world.rainbowTimer > 0f) pts *= 2
        if (world.fever) pts *= 2
        score += pts
        if (score >= 1000) unlock("punkte1000")
        world.eaten += 1
        if (item.cls >= 2) {
            world.bigRun += 1
            if (world.bigRun >= 20) unlock("big20")
        }
        if (world.eaten >= 100) unlock("vielfrass")
        // Gestuftes Feedback
        if (item.cls >= 2) buzz(HapticFeedbackType.LongPress) else buzz(HapticFeedbackType.TextHandleMove)
        snd { slurp(item.cls) }
        world.pops.add(HolePop(item.x, item.y - item.r, "+$pts", 0.9f, bad = false))
        collectFx(item.x, item.y, item.color)

        // Wachstum: Punkte sammeln → Größenstufen mit Fanfare
        if (item.kind != HoleKind.BOMBE) {
            world.growPts += item.cls + 1
            val lvlIdx = HoleGrowNeed.indexOfFirst { world.growPts < it }.let { if (it < 0) HoleGrowMul.size - 1 else it }
            if (lvlIdx + 1 > world.holeLevel) {
                world.holeLevel = lvlIdx + 1
                world.holeTargetR = world.baseR * HoleGrowMul[(world.holeLevel - 1).coerceIn(0, HoleGrowMul.size - 1)]
                world.pops.add(HolePop(world.holeX, world.holeY - world.holeTargetR * 2f, "⬆ Größe ${world.holeLevel}!", 1.3f, bad = false, big = true))
                if (!fxReduziert) world.shake = max(world.shake, 0.4f)
                buzz(HapticFeedbackType.LongPress)
                snd { grow() }
                if (world.holeLevel >= 5) unlock("wachstum5")
            }
            if (mode != HoleMode.RUSH && world.eaten % 12 == 0) {
                level += 1
                world.pops.add(HolePop(world.holeX, world.holeY - world.holeR * 2.2f, "Level $level", 1.5f, bad = false, big = true))
                if (mode == HoleMode.KLASSIK && (level - 1) % 3 == 0) {
                    val th = HoleThemes[((level - 1) / 3) % HoleThemes.size]
                    world.pops.add(HolePop(world.holeX, world.holeY - world.holeR * 3f, "Neues Gebiet: ${th.name}", 1.6f, bad = false))
                }
                if (level >= 10 && mode == HoleMode.KLASSIK) unlock("level10")
            }
        }
    }

    // Spezial-Objekte verschluckt
    fun swallowSpecial(item: HoleItem) {
        when (item.kind) {
            HoleKind.BOMBE -> {
                combo = 0
                world.comboTimer = 0f
                world.fever = false
                world.flash = 1f
                world.shake = 1f
                buzz(HapticFeedbackType.LongPress)
                snd { boom() }
                if (mode == HoleMode.RUSH) {
                    timeLeft = max(0f, timeLeft - 5f)
                    world.pops.add(HolePop(item.x, item.y - item.r, "-5 s", 1.1f, bad = true))
                } else {
                    lives -= 1
                    world.pops.add(HolePop(item.x, item.y - item.r, "-1 ♥", 1.1f, bad = true))
                    if (lives <= 0) finishRun()
                }
            }
            HoleKind.GOLD -> {
                world.goldRun += 1
                val gTotal = prefs.getInt("fruithole_stat_gold", 0) + 1
                prefs.edit().putInt("fruithole_stat_gold", gTotal).apply()
                if (gTotal >= 10) unlock("gold10")
                snd { power(1) }
                essen(item)
            }
            HoleKind.HERZ -> {
                world.powerupsRun += 1
                if (world.powerupsRun >= 5) unlock("sammler")
                if (lives < 5) {
                    lives += 1
                    world.pops.add(HolePop(item.x, item.y - item.r, "+1 ♥", 1.0f, bad = false))
                } else {
                    world.pops.add(HolePop(item.x, item.y - item.r, "♥ voll", 0.8f, bad = false))
                }
                collectFx(item.x, item.y, HikariDanger)
                buzz(HapticFeedbackType.TextHandleMove)
                snd { power(2) }
            }
            HoleKind.UHR -> {
                world.powerupsRun += 1
                if (world.powerupsRun >= 5) unlock("sammler")
                world.slowTimer = 5f
                world.pops.add(HolePop(item.x, item.y - item.r, "⏰ Slow-Mo", 1.0f, bad = false))
                collectFx(item.x, item.y, Color(0xFF22D3EE))
                buzz(HapticFeedbackType.TextHandleMove)
                snd { power(3) }
            }
            HoleKind.MAGNET -> {
                world.powerupsRun += 1
                if (world.powerupsRun >= 5) unlock("sammler")
                world.magnetTimer = 6f
                world.pops.add(HolePop(item.x, item.y - item.r, "🌀 Magnet", 1.0f, bad = false))
                collectFx(item.x, item.y, Color(0xFF60A5FA))
                buzz(HapticFeedbackType.TextHandleMove)
                snd { power(4) }
            }
            HoleKind.REGENBOGEN -> {
                world.powerupsRun += 1
                if (world.powerupsRun >= 5) unlock("sammler")
                world.rainbowTimer = 8f
                world.pops.add(HolePop(item.x, item.y - item.r, "🌈 Punkte ×2", 1.0f, bad = false))
                collectFx(item.x, item.y, HoleAccent)
                buzz(HapticFeedbackType.TextHandleMove)
                snd { power(5) }
            }
            else -> essen(item)
        }
    }

    // Bombe per Fingertipp entschärfen (Tap ohne Drag)
    fun tryDefuse(sx: Float, sy: Float) {
        if (paused || gameOver || countdown > 0f || !world.initialized) return
        val zm = world.zoom
        val wxp = (sx - world.w / 2f) / zm + world.camX
        val wyp = (sy - world.h / 2f) / zm + world.camY
        val radius = 60f * den
        val bombe = world.items.firstOrNull {
            it.kind == HoleKind.BOMBE && !it.swallowing &&
                (it.x - wxp) * (it.x - wxp) + (it.y - wyp) * (it.y - wyp) < radius * radius
        }
        if (bombe != null) {
            world.items.remove(bombe)
            world.defusedRun += 1
            score += 5
            world.pops.add(HolePop(bombe.x, bombe.y - bombe.r, "Entschärft +5", 0.9f, bad = false))
            collectFx(bombe.x, bombe.y, Color(0xFFB0BEC5))
            unlock("entschaerft")
            buzz(HapticFeedbackType.TextHandleMove)
            snd { defuse() }
        }
    }

    // Auto-Pause bei App-Wechsel
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && screen == HoleScreen.PLAY && !gameOver) {
                paused = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // System-Back: erst Overlays, dann Pause, dann zurück
    BackHandler {
        when {
            showSettings -> showSettings = false
            showHelp -> {
                showHelp = false
                prefs.edit().putBoolean("fruithole_help_seen", true).apply()
            }
            screen == HoleScreen.PLAY && showRestart -> showRestart = false
            screen == HoleScreen.PLAY && gameOver -> screen = HoleScreen.MENU
            screen == HoleScreen.PLAY && !paused -> paused = true
            screen == HoleScreen.PLAY -> screen = HoleScreen.MENU
            screen != HoleScreen.MENU -> screen = HoleScreen.MENU
            else -> onBack()
        }
    }

    // Erfolgs-Toasts nacheinander abbauen
    LaunchedEffect(achToasts.firstOrNull()) {
        val cur = achToasts.firstOrNull()
        if (cur != null) {
            delay(2400)
            if (achToasts.isNotEmpty() && achToasts[0] == cur) achToasts.removeAt(0)
        }
    }
    LaunchedEffect(showRecordBanner) {
        if (showRecordBanner) {
            delay(2200)
            showRecordBanner = false
        }
    }

    // Game-Loop
    LaunchedEffect(screen, restartKey, gameOver) {
        if (screen != HoleScreen.PLAY || gameOver) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val rdt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.032f)
                last = now
                if (rdt > 0f && world.initialized && !gameOver && !paused) {
                    if (countdown > 0f) {
                        countdown = max(0f, countdown - rdt)
                    } else {
                        val sdt = rdt * (if (world.slowTimer > 0f) 0.5f else 1f)
                        world.time += rdt
                        world.runTime += rdt

                        // Effekt-Timer laufen in Echtzeit ab
                        if (world.slowTimer > 0f) world.slowTimer = max(0f, world.slowTimer - rdt)
                        if (world.magnetTimer > 0f) world.magnetTimer = max(0f, world.magnetTimer - rdt)
                        if (world.rainbowTimer > 0f) world.rainbowTimer = max(0f, world.rainbowTimer - rdt)
                        world.flash = max(0f, world.flash - rdt * 2.4f)
                        world.shake = max(0f, world.shake - rdt * 3.2f)
                        if (world.tooBigCd > 0f) world.tooBigCd -= rdt

                        // Rush: Countdown der Rundenzeit
                        if (mode == HoleMode.RUSH) {
                            timeLeft = max(0f, timeLeft - rdt)
                            if (timeLeft <= 0f) finishRun()
                        }

                        // Kombo-Fenster
                        if (world.comboTimer > 0f) {
                            world.comboTimer -= rdt
                            if (world.comboTimer <= 0f && combo > 0) {
                                if (combo >= 4) {
                                    world.pops.add(HolePop(world.holeX, world.holeY - world.holeR * 2f, "Kombo vorbei", 0.9f, bad = true))
                                }
                                combo = 0
                                world.fever = false
                            }
                        }

                        // Wüsten-Wind treibt das Loch
                        if (mode == HoleMode.WELTEN && worldIdx == 1) {
                            world.wind = sin(world.time * 0.35f) * 120f * den
                            world.holeTargetX += world.wind * sdt
                        }

                        // Loch bewegen — im Schnee/Weltraum mit Trägheit
                        world.holeTargetX = world.holeTargetX.coerceIn(world.holeR, world.mapW - world.holeR)
                        world.holeTargetY = world.holeTargetY.coerceIn(world.holeR, world.mapH - world.holeR)
                        if (mode == HoleMode.WELTEN && worldIdx >= 2) {
                            val grip = if (worldIdx == 2) 40f else 26f
                            val damp = if (worldIdx == 2) 3.5f else 2.2f
                            world.holeVx += (world.holeTargetX - world.holeX) * grip * sdt
                            world.holeVy += (world.holeTargetY - world.holeY) * grip * sdt
                            world.holeVx *= max(0f, 1f - damp * sdt)
                            world.holeVy *= max(0f, 1f - damp * sdt)
                            world.holeX = (world.holeX + world.holeVx * sdt).coerceIn(world.holeR, world.mapW - world.holeR)
                            world.holeY = (world.holeY + world.holeVy * sdt).coerceIn(world.holeR, world.mapH - world.holeR)
                        } else {
                            val k = min(1f, sdt * 14f)
                            world.holeX += (world.holeTargetX - world.holeX) * k
                            world.holeY += (world.holeTargetY - world.holeY) * k
                        }

                        // Loch wächst weich zur Zielgröße
                        world.holeR += (world.holeTargetR - world.holeR) * min(1f, sdt * 4f)

                        // Kamera: Smooth-Follow + Zoom abhängig von der Lochgröße
                        val viewW = world.mapW * (0.52f + 0.55f * ((world.holeR - world.baseR) / world.baseR).coerceIn(0f, 1.6f))
                        val zoomT = (world.w / viewW).coerceIn(world.w / world.mapW, world.w / (world.baseR * 16f))
                        world.zoom += (zoomT - world.zoom) * min(1f, rdt * 2.2f)
                        world.camX += (world.holeX - world.camX) * min(1f, rdt * 4.5f)
                        world.camY += (world.holeY - world.camY) * min(1f, rdt * 4.5f)
                        // Kamera in der Map halten
                        val halfW = world.w / 2f / world.zoom
                        val halfH = world.h / 2f / world.zoom
                        world.camX = world.camX.coerceIn(min(halfW, world.mapW / 2f), max(world.mapW - halfW, world.mapW / 2f))
                        world.camY = world.camY.coerceIn(min(halfH, world.mapH / 2f), max(world.mapH - halfH, world.mapH / 2f))

                        // Score-Anzeige zählt animiert hoch
                        if (shownScore < score) {
                            shownScore = min(score, shownScore + max(1, ((score - shownScore) * 8f * rdt).toInt() + 1))
                        }
                        if (!recordBroken && runRecord > 0 && score > runRecord) {
                            recordBroken = true
                            showRecordBanner = true
                        }

                        // Nachschub, wenn die Map leer wird (nicht in Welten-Missionen)
                        if (mode != HoleMode.WELTEN && world.items.count { !it.swallowing } < 24) {
                            holeRegen(world, activeTheme())
                        }

                        // Objekte: Sog, Wackeln, Verschlucken
                        val iter = world.items.iterator()
                        while (iter.hasNext()) {
                            val item = iter.next()
                            if (!item.swallowing) {
                                // Weltraum: Objekte treiben langsam
                                if (mode == HoleMode.WELTEN && worldIdx == 3) {
                                    item.x += item.vx * sdt
                                    item.y += item.vy * sdt
                                    if (item.x < item.r || item.x > world.mapW - item.r) item.vx = -item.vx
                                    if (item.y < item.r || item.y > world.mapH - item.r) item.vy = -item.vy
                                }
                                val dx = world.holeX - item.x
                                val dy = world.holeY - item.y
                                val d2 = dx * dx + dy * dy
                                val d = sqrt(d2).coerceAtLeast(1f)
                                val swallowable = item.r <= world.holeR * 0.95f
                                if (swallowable) {
                                    // Gravitation zum Loch
                                    val pullR = world.holeR * (if (world.magnetTimer > 0f) 4.2f else 2.4f)
                                    if (d < pullR) {
                                        val strength = (if (world.magnetTimer > 0f) 5.5f else 2.4f) * (1f - d / pullR + 0.4f)
                                        item.x += dx * min(1f, sdt * strength)
                                        item.y += dy * min(1f, sdt * strength)
                                    }
                                    // Über dem Rand → hineinrutschen
                                    if (d < world.holeR * 0.75f) {
                                        item.swallowing = true
                                        item.sinkX = item.x
                                        item.sinkY = item.y
                                    }
                                } else if (d < world.holeR + item.r * 0.7f) {
                                    // Zu groß: wackelt und blockt leicht
                                    item.wobble = 0.5f
                                    if (world.tooBigCd <= 0f) {
                                        world.tooBigCd = 1.6f
                                        world.pops.add(HolePop(item.x, item.y - item.r, "Zu groß!", 0.8f, bad = true))
                                        snd { blocked() }
                                    }
                                }
                                if (item.wobble > 0f) item.wobble -= sdt
                            } else {
                                // Sinken: zum Lochzentrum ziehen, kippen, schrumpfen
                                item.swallow += sdt * 3.4f
                                val t = min(1f, item.swallow)
                                item.x = item.sinkX + (world.holeX - item.sinkX) * t
                                item.y = item.sinkY + (world.holeY - item.sinkY) * t
                                if (item.swallow >= 1f) {
                                    iter.remove()
                                    swallowSpecial(item)
                                }
                            }
                        }

                        // Missionen der aktuellen Welt live prüfen
                        if (mode == HoleMode.WELTEN && !gameOver) {
                            val defs = HoleWorlds[worldIdx].missions
                            for (mi in defs.indices) {
                                if (missionDone[mi]) continue
                                val m = defs[mi]
                                val valNow = when (m.kind) {
                                    0 -> world.eaten
                                    1 -> world.bestComboRun
                                    2 -> world.defusedRun
                                    3 -> world.powerupsRun
                                    4 -> score
                                    else -> world.goldRun
                                }
                                if (valNow >= m.target) {
                                    missionDone = missionDone.copyOf().also { it[mi] = true }
                                    prefs.edit().putBoolean("fruithole_w${worldIdx}_m$mi", true).apply()
                                    achToasts.add("🎯 Mission erfüllt: ${m.desc}")
                                    snd { win() }
                                    if (missionDone.all { it }) unlock("weltmeister")
                                }
                            }
                        }

                        // Fieber-Funken am Loch-Rand
                        if (world.fever && !fxReduziert) {
                            world.feverFx -= rdt
                            if (world.feverFx <= 0f) {
                                world.feverFx = 0.08f
                                val a = Random.nextFloat() * 6.2832f
                                world.sparks.add(
                                    HoleSpark(
                                        world.holeX + cos(a) * world.holeR * 0.9f,
                                        world.holeY + sin(a) * world.holeR * 0.5f,
                                        cos(a) * 60f * den, -140f * den,
                                        0.5f, 0.5f, Color(0xFFFFD54F), (3f + Random.nextFloat() * 3f) * den,
                                    )
                                )
                            }
                        }
                        // Magnet-Sog-Partikel Richtung Loch
                        if (world.magnetTimer > 0f && !fxReduziert) {
                            world.sogFx -= rdt
                            if (world.sogFx <= 0f) {
                                world.sogFx = 0.06f
                                val a = Random.nextFloat() * 6.2832f
                                val sx = world.holeX + cos(a) * world.holeR * 2.4f
                                val sy = world.holeY + sin(a) * world.holeR * 2.4f
                                world.sparks.add(
                                    HoleSpark(
                                        sx, sy,
                                        (world.holeX - sx) * 2.6f, (world.holeY - sy) * 2.6f,
                                        0.35f, 0.35f, Color(0xFF60A5FA), (2.5f + Random.nextFloat() * 2f) * den,
                                    )
                                )
                            }
                        }

                        // Partikel
                        val sIt = world.sparks.iterator()
                        while (sIt.hasNext()) {
                            val s = sIt.next()
                            s.x += s.vx * rdt
                            s.y += s.vy * rdt
                            s.life -= rdt
                            if (s.life <= 0f) sIt.remove()
                        }

                        // Punkte-Popups
                        val pit = world.pops.iterator()
                        while (pit.hasNext()) {
                            val p = pit.next()
                            p.life -= rdt
                            p.y -= rdt * 60f * den
                            if (p.life <= 0f) pit.remove()
                        }

                        // Tiefensortierung für den 2.5D-Look
                        world.items.sortWith(compareBy { it.y })
                    }
                }
                tick++
            }
        }
    }

    // ————— UI —————
    // Eine Wurzel-Box, damit Toast/Einstellungen/Hilfe sauber über dem
    // aktiven Screen liegen statt als lose Geschwister-Nodes.
    Box(Modifier.fillMaxSize()) {

    Crossfade(screen, animationSpec = tween(220), label = "holeScreen") { scr ->
    when (scr) {
        HoleScreen.MENU -> HoleMenuInhalt(
            prefs = prefs,
            xp = xp,
            lastMode = mode,
            onBack = onBack,
            onKlassik = { startRun(HoleMode.KLASSIK, false, 0) },
            onDaily = { startRun(HoleMode.KLASSIK, true, 0) },
            onRush = { startRun(HoleMode.RUSH, false, 0) },
            onWelten = { screen = HoleScreen.WELTWAHL },
            onStats = { screen = HoleScreen.STATS },
            onErfolge = { screen = HoleScreen.ERFOLGE },
            onSettings = { showSettings = true },
            onHilfe = { showHelp = true },
        )

        HoleScreen.WELTWAHL -> HoleWeltwahlInhalt(
            prefs = prefs,
            onZurueck = { screen = HoleScreen.MENU },
            onStart = { idx -> startRun(HoleMode.WELTEN, false, idx) },
        )

        HoleScreen.STATS -> HoleStatsInhalt(prefs = prefs, xp = xp, onZurueck = { screen = HoleScreen.MENU })

        HoleScreen.ERFOLGE -> HoleErfolgeInhalt(prefs = prefs, onZurueck = { screen = HoleScreen.MENU })

        HoleScreen.PLAY -> Column(Modifier.fillMaxSize().background(HikariBg)) {
            // Kopf: Pause-Chip · Modus · Punkte-Pille
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                GxIconChip(if (gameOver) "←" else "⏸", size = 44.dp) {
                    if (!gameOver) paused = true else screen = HoleScreen.MENU
                }
                Text(modeLabel(), fontSize = 18.sp, color = HoleAccent, fontWeight = FontWeight.Black)
                GxHudPill("PKT", "$shownScore", accent = if (recordBroken) HoleAccent else null)
            }
            Text(
                when {
                    recordBroken -> "🏆 Neuer Rekord!"
                    runRecord > 0 && score < runRecord -> "Noch ${runRecord - score} bis Rekord"
                    runRecord > 0 -> "Rekord: $runRecord"
                    else -> "Erste Runde!"
                },
                fontSize = 11.sp,
                color = if (recordBroken) HoleAccent else HikariTextFaint,
                fontWeight = if (recordBroken) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(4.dp))

            // Leben / Timer · Kombo-Pille · Level/Welt
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                if (mode == HoleMode.RUSH) {
                    val restSek = ceil(timeLeft).toInt()
                    GxHudPill("ZEIT", "${restSek}s", accent = if (timeLeft <= 10f) HikariDanger else null)
                } else {
                    Row {
                        repeat(5) { i ->
                            Text(
                                "♥",
                                fontSize = 16.sp,
                                color = if (i < lives) HikariDanger else HikariTextFaint,
                                modifier = Modifier.padding(end = 3.dp),
                            )
                        }
                    }
                }
                if (world.fever || multiplier > 1) {
                    Text(
                        if (world.fever) "🔥 Fieber ×2" else "Kombo x$multiplier",
                        fontSize = 12.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (world.fever) Color(0xFFFFD54F) else HoleAccent)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                when (mode) {
                    HoleMode.WELTEN -> GxHudPill("🌍", HoleWorlds[worldIdx].name)
                    HoleMode.RUSH -> GxHudPill("🕳️", "Größe ${world.holeLevel}")
                    else -> GxHudPill("LVL", "$level · Größe ${world.holeLevel}")
                }
            }

            // Kombo-Fortschritt zum nächsten Multiplikator + Wachstum zur nächsten Größe
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val nextAt = if (combo >= 8) 12 else if (combo >= 4) 8 else 4
                val prevAt = if (combo >= 8) 8 else if (combo >= 4) 4 else 0
                val comboFrac = if (combo >= 12) 1f else ((combo - prevAt).toFloat() / (nextAt - prevAt)).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(HikariSurfaceHigh)
                ) {
                    Box(
                        Modifier.fillMaxWidth(comboFrac).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (world.fever) Color(0xFFFFD54F) else HoleAccent)
                    )
                }
                // Wachstums-Füllstand
                val lvlIdx = (world.holeLevel - 1).coerceIn(0, HoleGrowNeed.size - 1)
                val prevNeed = if (lvlIdx <= 0) 0 else HoleGrowNeed[lvlIdx - 1]
                val growFrac = if (lvlIdx >= HoleGrowNeed.size - 1 && world.growPts >= HoleGrowNeed.last()) 1f
                else ((world.growPts - prevNeed).toFloat() / (HoleGrowNeed[lvlIdx] - prevNeed)).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(HikariSurfaceHigh)
                ) {
                    Box(
                        Modifier.fillMaxWidth(growFrac).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF4ADE80))
                    )
                }
            }

            // Aktive Welt-Mission
            if (mode == HoleMode.WELTEN) {
                val defs = HoleWorlds[worldIdx].missions
                val offen = defs.indices.firstOrNull { !missionDone[it] }
                if (offen != null) {
                    val m = defs[offen]
                    val valNow = when (m.kind) {
                        0 -> world.eaten
                        1 -> world.bestComboRun
                        2 -> world.defusedRun
                        3 -> world.powerupsRun
                        4 -> score
                        else -> world.goldRun
                    }
                    Text(
                        "🎯 ${m.desc} · ${min(valNow, m.target)}/${m.target}",
                        fontSize = 12.sp,
                        color = HikariTextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(restartKey) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (paused || gameOver || countdown > 0f) return@awaitEachGesture
                                var moved = false
                                val completed = drag(down.id) { change ->
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) moved = true
                                    if (!paused && !gameOver && countdown <= 0f) {
                                        if (direktSteuerung) {
                                            world.holeTargetX = (change.position.x - size.width / 2f) / world.zoom + world.camX
                                            world.holeTargetY = (change.position.y - size.height / 2f) / world.zoom + world.camY
                                        } else {
                                            world.holeTargetX += dx * HoleSens[sensIdx] / world.zoom
                                            world.holeTargetY += dy * HoleSens[sensIdx] / world.zoom
                                        }
                                    }
                                    change.consume()
                                }
                                if (completed && !moved) tryDefuse(down.position.x, down.position.y)
                            }
                        }
                ) {
                    if (tick < 0) return@Canvas // liest tick → Canvas wird pro Frame neu gezeichnet
                    val w = size.width
                    val h = size.height
                    if (!world.initialized) {
                        world.w = w
                        world.h = h
                        holeGenMap(world, activeTheme(), mode, worldIdx)
                        world.zoom = w / (world.mapW * 0.52f)
                        world.initialized = true
                    } else if (world.w != w || world.h != h) {
                        world.w = w
                        world.h = h
                    }
                    val zm = world.zoom
                    fun wx(x: Float) = (x - world.camX) * zm + w / 2f
                    fun wy(y: Float) = (y - world.camY) * zm + h / 2f
                    val theme = activeTheme()
                    val glowCol = theme.glow
                    val shakeX = if (world.shake > 0f) (Random.nextFloat() * 2f - 1f) * world.shake * 14f else 0f
                    val shakeY = if (world.shake > 0f) (Random.nextFloat() * 2f - 1f) * world.shake * 10f else 0f

                    // ——— Gras-Karo (hell & freundlich, Viewport-Culling) ———
                    drawRect(holeShade(theme.grassB, 0.55f))
                    val tile = 110f * den * zm
                    if (tile > 4f) {
                        val mapL = wx(0f)
                        val mapT = wy(0f)
                        val mapWPx = world.mapW * zm
                        val mapHPx = world.mapH * zm
                        val i0 = max(0, ((-mapL) / tile).toInt())
                        val j0 = max(0, ((-mapT) / tile).toInt())
                        val i1 = min(((w - mapL) / tile).toInt() + 1, ceil(world.mapW * zm / tile).toInt())
                        val j1 = min(((h - mapT) / tile).toInt() + 1, ceil(world.mapH * zm / tile).toInt())
                        for (i in i0..i1) {
                            for (j in j0..j1) {
                                drawRect(
                                    if ((i + j) % 2 == 0) theme.grassA else theme.grassB,
                                    topLeft = Offset(mapL + i * tile, mapT + j * tile),
                                    size = Size(tile + 1f, tile + 1f),
                                )
                            }
                        }
                        // Map-Rand
                        drawRect(
                            holeShade(theme.grassB, 0.45f),
                            topLeft = Offset(mapL, mapT),
                            size = Size(mapWPx, mapHPx),
                            style = Stroke(width = 8f * den),
                        )
                    }

                    // ——— Objekte hinter dem Loch (Tiefensortiert, 2.5D) ———
                    val holeScreenY = wy(world.holeY)
                    for (item in world.items) {
                        if (wy(item.y) < holeScreenY) {
                            holeDrawItem(item, world, w, h, paints, emojiPaint, den, fxReduziert)
                        }
                    }

                    // ——— Das Loch ———
                    val hx = wx(world.holeX) + shakeX
                    val hy = wy(world.holeY) + shakeY
                    val hr = world.holeR * zm
                    val rimCol = when {
                        world.rainbowTimer > 0f -> Color.hsv((world.time * 140f) % 360f, 0.65f, 1f)
                        world.fever -> Color(0xFFFFD54F)
                        else -> glowCol
                    }
                    // Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(rimCol.copy(alpha = 0.40f), Color.Transparent),
                            center = Offset(hx, hy),
                            radius = hr * 1.9f,
                        ),
                        radius = hr * 1.9f,
                        center = Offset(hx, hy),
                    )
                    // Schwarzes Loch (Ellipse, perspektivisch flach)
                    drawOval(
                        Color.Black,
                        topLeft = Offset(hx - hr, hy - hr * 0.62f),
                        size = Size(hr * 2f, hr * 1.24f),
                    )
                    // Innentiefe
                    drawOval(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF000000), Color(0xFF20202C)),
                            center = Offset(hx, hy),
                            radius = hr,
                        ),
                        topLeft = Offset(hx - hr, hy - hr * 0.62f),
                        size = Size(hr * 2f, hr * 1.24f),
                    )
                    // Heller Rand + rotierender Wirbel-Arc
                    drawOval(
                        rimCol,
                        topLeft = Offset(hx - hr, hy - hr * 0.62f),
                        size = Size(hr * 2f, hr * 1.24f),
                        style = Stroke(width = 3.5f * den),
                    )
                    drawArc(
                        rimCol.copy(alpha = 0.5f),
                        startAngle = (world.time * 160f) % 360f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(hx - hr * 0.66f, hy - hr * 0.41f),
                        size = Size(hr * 1.32f, hr * 0.82f),
                        style = Stroke(width = 2f * den, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )

                    // ——— Objekte vor/über dem Loch (fallen sichtbar hinein) ———
                    for (item in world.items) {
                        if (wy(item.y) >= holeScreenY) {
                            holeDrawItem(item, world, w, h, paints, emojiPaint, den, fxReduziert)
                        }
                    }

                    // ——— Partikel ———
                    for (s in world.sparks) {
                        drawCircle(
                            s.color.copy(alpha = (s.life / s.maxLife).coerceIn(0f, 1f)),
                            radius = s.size * zm,
                            center = Offset(wx(s.x) + shakeX, wy(s.y) + shakeY),
                        )
                    }

                    // ——— Wüsten-Wind-Anzeige ———
                    if (mode == HoleMode.WELTEN && worldIdx == 1 && abs(world.wind) > 20f * den) {
                        popPaint.textSize = 18f * den
                        popPaint.color = HikariTextMuted.toArgb()
                        drawIntoCanvas { c ->
                            val txt = if (world.wind > 0f) "💨 →" else "← 💨"
                            c.nativeCanvas.drawText(txt, w * 0.5f, h * 0.06f, popPaint)
                        }
                    }

                    // ——— Punkte-Popups (Overshoot-Scale + Aufsteigen + Fade) ———
                    for (p in world.pops) {
                        val age = p.maxLife - p.life
                        val inT = (age / 0.13f).coerceIn(0f, 1f)
                        val u = inT - 1f
                        val back = 1f + 2.70158f * u * u * u + 1.70158f * u * u
                        val alpha = (p.life / (p.maxLife * 0.5f)).coerceIn(0f, 1f)
                        popPaint.textSize = (if (p.big) 34f else 20f) * den * back
                        val col = if (p.bad) HikariDanger else HikariAmber
                        val px = wx(p.x) + shakeX
                        val py = wy(p.y) + shakeY
                        popPaint.color = Color.Black.copy(alpha = 0.5f * alpha).toArgb()
                        drawIntoCanvas { c -> c.nativeCanvas.drawText(p.text, px + 2f * den, py + 2f * den, popPaint) }
                        popPaint.color = col.copy(alpha = alpha).toArgb()
                        drawIntoCanvas { c -> c.nativeCanvas.drawText(p.text, px, py, popPaint) }
                    }

                    // Slow-Mo: leichter Blaustich
                    if (world.slowTimer > 0f) {
                        drawRect(Color(0x1422D3EE))
                    }
                    // Rush: rote Puls-Vignette in den letzten 10 Sekunden
                    if (mode == HoleMode.RUSH && timeLeft <= 10f && !gameOver) {
                        drawRect(HikariDanger.copy(alpha = 0.06f + 0.06f * (0.5f + 0.5f * sin(world.time * 8f))))
                    }
                    // Roter Flash bei Bombe
                    if (world.flash > 0f) {
                        drawRect(HikariDanger.copy(alpha = world.flash * 0.30f))
                    }
                }

                // Aktive Power-up-Ringe
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (world.slowTimer > 0f) GxProgressRing(world.slowTimer / 5f, Color(0xFF22D3EE), size = 38.dp) { Text("⏰", fontSize = 13.sp) }
                    if (world.magnetTimer > 0f) GxProgressRing(world.magnetTimer / 6f, Color(0xFF60A5FA), size = 38.dp) { Text("🌀", fontSize = 13.sp) }
                    if (world.rainbowTimer > 0f) GxProgressRing(world.rainbowTimer / 8f, HoleAccent, size = 38.dp) { Text("🌈", fontSize = 13.sp) }
                    if (world.fever) GxProgressRing(1f, Color(0xFFFFD54F), size = 38.dp) { Text("🔥", fontSize = 13.sp) }
                }

                // Rekord-Banner
                if (showRecordBanner) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp)) {
                        Text(
                            "🏆 Neuer Rekord!",
                            fontSize = 16.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                                .background(Brush.horizontalGradient(listOf(HoleAccent, Color(0xFFC4B5FD))))
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                }

                // Countdown nach Start / Pause
                if (countdown > 0f && !paused && !gameOver) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(110.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(2.dp, HoleAccent.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${ceil(countdown).toInt()}",
                                fontSize = 56.sp,
                                color = HoleAccent,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }

                // Pause-Overlay
                if (paused && !gameOver) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
                            .pointerInput(Unit) { detectTapGestures { } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier.padding(28.dp)
                                .widthIn(max = 340.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF232326))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("⏸", fontSize = 30.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Pause", fontSize = 22.sp, color = HikariText, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GxHudPill("PKT", "$score", accent = HoleAccent)
                                GxHudPill("KOMBO", "${world.bestComboRun}")
                                GxHudPill("🍎", "${world.eaten}")
                            }
                            Spacer(Modifier.height(20.dp))
                            GxPrimaryButton("Weiter", HoleAccent, Modifier.fillMaxWidth()) {
                                paused = false
                                countdown = 3f
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GxGhostButton("Neustart", Modifier.weight(1f)) { showRestart = true }
                                GxGhostButton("Menü", Modifier.weight(1f)) {
                                    paused = false
                                    screen = HoleScreen.MENU
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            GxToggle("Vibration", null, HoleAccent, haptikOn) {
                                haptikOn = it
                                prefs.edit().putBoolean("fruithole_set_haptik", it).apply()
                            }
                            GxToggle("Sound", null, HoleAccent, soundOn) {
                                soundOn = it
                                prefs.edit().putBoolean("fruithole_sound", it).apply()
                            }
                        }
                    }
                }

                // Neustart-Bestätigung
                if (showRestart) {
                    GxConfirmDialog(
                        title = "Neu starten?",
                        text = "Der aktuelle Lauf geht verloren.",
                        confirmLabel = "Neu starten",
                        accent = HoleAccent,
                        danger = true,
                        onConfirm = { showRestart = false; startRun(mode, daily, worldIdx) },
                        onDismiss = { showRestart = false },
                    )
                }

                // Game-Over-Overlay mit Runden-Statistik
                if (gameOver) {
                    // Count-up: startet bei 0, LaunchedEffect setzt das Ziel nach dem ersten Frame
                    var countTarget by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) { countTarget = score }
                    val displayScore = gxAnimatedCount(countTarget, 900)
                    Column(
                        Modifier.fillMaxSize().background(Color(0xE0000000)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFF232326))
                                .border(1.dp, HoleAccent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                                .padding(horizontal = 22.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (mode == HoleMode.RUSH) "⏱ Zeit um!" else "Game Over",
                                fontSize = 24.sp, color = HikariText, fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("$displayScore", fontSize = 44.sp, color = HoleAccent, fontWeight = FontWeight.Black)
                            if (newRecord) {
                                Text("🏆 Neuer Rekord!", fontSize = 15.sp, color = HoleAccent, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Rekord: ${max(runRecord, score)}", fontSize = 13.sp, color = HikariTextMuted)
                            }
                            Spacer(Modifier.height(16.dp))
                            val dSek = world.runTime.toInt()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GxStatTile("${dSek / 60}:${(dSek % 60).toString().padStart(2, '0')}", "Dauer", HoleAccent, Modifier.weight(1f))
                                GxStatTile("${world.eaten}", "Objekte", HoleAccent, Modifier.weight(1f))
                                GxStatTile("${world.bestComboRun}", "Kombo", HoleAccent, Modifier.weight(1f))
                                GxStatTile("${world.powerupsRun}", "Extras", HoleAccent, Modifier.weight(1f))
                            }
                            if (mode == HoleMode.WELTEN) {
                                Spacer(Modifier.height(12.dp))
                                HoleWorlds[worldIdx].missions.forEachIndexed { i, m ->
                                    Text(
                                        "${if (missionDone[i]) "✅" else "⬜"} ${m.desc}",
                                        fontSize = 12.sp,
                                        color = if (missionDone[i]) HikariText else HikariTextMuted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            GxPrimaryButton("Nochmal", HoleAccent, Modifier.fillMaxWidth()) {
                                startRun(mode, daily, worldIdx)
                            }
                            Spacer(Modifier.height(10.dp))
                            GxGhostButton("Zum Menü", Modifier.fillMaxWidth()) { screen = HoleScreen.MENU }
                        }
                    }
                }
            }
        }
    }
    }

    // Erfolgs-/Missions-Toast (über allen Screens)
    achToasts.firstOrNull()?.let { toast ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            GxAppear(0) {
                Text(
                    toast,
                    fontSize = 14.sp,
                    color = HikariText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 64.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF232326))
                        .border(1.dp, HoleAccent.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    // Einstellungen (Bottom-Sheet, aus dem Menü erreichbar)
    if (showSettings) {
        GxSheet("Einstellungen", HoleAccent, onClose = { showSettings = false }) {
            GxToggle("Vibration", "Haptisches Feedback beim Fressen & bei Treffern", HoleAccent, haptikOn) {
                haptikOn = it
                prefs.edit().putBoolean("fruithole_set_haptik", it).apply()
            }
            GxToggle("Sound", "Prozedural erzeugte Effektgeräusche", HoleAccent, soundOn) {
                soundOn = it
                prefs.edit().putBoolean("fruithole_sound", it).apply()
            }
            GxToggle("Reduzierte Effekte", "Weniger Partikel für flüssigeres Spiel", HoleAccent, fxReduziert) {
                fxReduziert = it
                prefs.edit().putBoolean("fruithole_set_fx", it).apply()
            }
            Spacer(Modifier.height(10.dp))
            Text("Steuerung", fontSize = 12.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            GxSegmented(listOf("Relativ", "Direkt"), if (direktSteuerung) 1 else 0, HoleAccent) { i ->
                direktSteuerung = i == 1
                prefs.edit().putBoolean("fruithole_set_direkt", direktSteuerung).apply()
            }
            Spacer(Modifier.height(14.dp))
            Text("Empfindlichkeit", fontSize = 12.sp, color = HikariTextFaint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            GxSegmented(listOf("Sanft", "Normal", "Flink"), sensIdx, HoleAccent) { i ->
                sensIdx = i
                prefs.edit().putInt("fruithole_set_sens", i).apply()
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // Hilfe (Bottom-Sheet, beim ersten Start automatisch)
    if (showHelp) {
        GxSheet(
            "So funktioniert's", HoleAccent,
            onClose = {
                showHelp = false
                prefs.edit().putBoolean("fruithole_help_seen", true).apply()
            },
        ) {
            listOf(
                "🕳️ Ziehe mit dem Finger — das Loch folgt dir über die Map",
                "🍩 Verschlucke alles, was kleiner ist als das Loch — zu große Objekte wackeln nur",
                "⬆ Fressen lässt das Loch wachsen: bald passen auch Türme und Häuser rein",
                "💣 Bomben kosten ein Leben — oder tippe sie an: Entschärfen gibt +5",
                "🌟 +50 Punkte · ❤️ Extra-Leben · ⏰ Slow-Mo",
                "🌀 Magnet zieht Objekte an · 🌈 Punkte ×2",
                "🔥 Schnelle Serien füllen die Kombo — Combo 12 zündet den Fieber-Modus: alles ×2",
                "🎯 Daily-Challenge: jeden Tag dieselbe Map für alle Versuche",
            ).forEach {
                Text(it, fontSize = 13.sp, color = HikariTextMuted, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            GxPrimaryButton("Los geht's!", HoleAccent, Modifier.fillMaxWidth()) {
                showHelp = false
                prefs.edit().putBoolean("fruithole_help_seen", true).apply()
            }
        }
    }

    } // Ende Wurzel-Box
}

// ————— Pseudo-3D-Objekt zeichnen (Unit-Space-Shader, Viewport-Culling) —————

private fun DrawScope.holeDrawItem(
    item: HoleItem,
    world: HoleWorld,
    w: Float,
    h: Float,
    paints: HolePaintCache,
    emojiPaint: Paint,
    den: Float,
    fxReduziert: Boolean,
) {
    val zm = world.zoom
    val sx = (item.x - world.camX) * zm + w / 2f
    val sy = (item.y - world.camY) * zm + h / 2f
    val sr = item.r * zm
    val cull = sr * 4f
    if (sx < -cull || sx > w + cull || sy < -cull || sy > h + cull) return

    // Wackeln bei "zu groß", Kippen/Schrumpfen beim Sinken
    val wob = if (item.wobble > 0f) sin(item.wobble * 30f) * 7f else 0f
    var sc = 1f
    var alpha = 1f
    var rot = wob
    if (item.swallowing) {
        val t = min(1f, item.swallow)
        sc = 1f - t * 0.92f
        alpha = 1f - t * 0.6f
        rot = wob + t * 150f
    }

    // Bodenschatten (weiche Ellipse)
    drawOval(
        Color(0xFF1A2A12).copy(alpha = 0.22f * alpha),
        topLeft = Offset(sx - sr * 1.05f * sc, sy + sr * 0.06f),
        size = Size(sr * 2.1f * sc, sr * 0.5f * sc),
    )

    // Glühen für Spezial-Objekte
    if (!fxReduziert && item.shape == HoleShape.SPECIAL) {
        when (item.kind) {
            HoleKind.BOMBE -> drawCircle(
                HikariDanger.copy(alpha = 0.25f + 0.15f * sin(world.time * 9f)),
                sr * 1.45f * sc, Offset(sx, sy - sr * 0.9f * sc),
            )
            HoleKind.GOLD -> drawCircle(
                HikariAmber.copy(alpha = 0.28f * (0.6f + 0.4f * sin(world.time * 8f))),
                sr * 1.5f * sc, Offset(sx, sy - sr * 0.9f * sc),
            )
            HoleKind.REGENBOGEN -> drawCircle(
                Color.hsv((world.time * 200f) % 360f, 0.5f, 1f).copy(alpha = 0.24f),
                sr * 1.4f * sc, Offset(sx, sy - sr * 0.9f * sc),
            )
            else -> {}
        }
    }

    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        nc.save()
        nc.translate(sx, sy)
        nc.rotate(rot)
        nc.scale(sr * sc, sr * sc)
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
        when (item.shape) {
            HoleShape.BALL -> {
                val p = paints.ballFor(item.color)
                p.alpha = alphaInt
                nc.drawCircle(0f, -0.9f, 1f, p)
                emojiPaint.alpha = alphaInt
                emojiPaint.textSize = 1.1f
                val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                nc.drawText(item.emoji, 0f, -0.9f - yOff, emojiPaint)
                emojiPaint.alpha = 255
            }
            HoleShape.TOWER -> {
                val sp = paints.sideFor(item.color)
                sp.alpha = alphaInt
                nc.drawRoundRect(-0.75f, -2.6f, 0.75f, 0.1f, 0.22f, 0.22f, sp)
                val tp = paints.topFor(item.color)
                tp.alpha = alphaInt
                nc.drawOval(-0.75f, -3.0f, 0.75f, -2.2f, tp)
                emojiPaint.alpha = alphaInt
                emojiPaint.textSize = 1.2f
                val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                nc.drawText(item.emoji, 0f, -2.6f - yOff, emojiPaint)
                emojiPaint.alpha = 255
            }
            HoleShape.PYRAMID -> {
                val sp = paints.sideFor(item.color)
                sp.alpha = alphaInt
                val tp = paints.topFor(item.color)
                tp.alpha = alphaInt
                var hw = 1.0f
                var y0 = 0.05f
                repeat(3) {
                    nc.drawRoundRect(-hw, y0 - 0.72f, hw, y0, 0.14f, 0.14f, sp)
                    nc.drawRect(-hw, y0 - 0.72f, hw, y0 - 0.56f, tp)
                    hw -= 0.3f
                    y0 -= 0.72f
                }
                emojiPaint.alpha = alphaInt
                emojiPaint.textSize = 1.0f
                val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                nc.drawText(item.emoji, 0f, y0 + 0.25f - yOff, emojiPaint)
                emojiPaint.alpha = 255
            }
            HoleShape.BOX -> {
                val sp = paints.sideFor(item.color)
                sp.alpha = alphaInt
                nc.drawRoundRect(-0.85f, -1.9f, 0.85f, 0.1f, 0.10f, 0.10f, sp)
                val tp = paints.topFor(item.color)
                tp.alpha = alphaInt
                nc.drawRect(-0.85f, -1.9f, 0.85f, -1.55f, tp)
                // Fenster
                emojiPaint.alpha = (alphaInt * 0.85f).toInt().coerceIn(0, 255)
                emojiPaint.textSize = 0.55f
                nc.drawText("▪", -0.4f, -0.9f, emojiPaint)
                nc.drawText("▪", 0.4f, -0.5f, emojiPaint)
                emojiPaint.alpha = 255
                emojiPaint.textSize = 0.9f
                val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                emojiPaint.alpha = alphaInt
                nc.drawText(item.emoji, 0f, -2.0f - yOff, emojiPaint)
                emojiPaint.alpha = 255
            }
            HoleShape.TREE -> {
                val trunk = paints.sideFor(Color(0xFF6D4C41))
                trunk.alpha = alphaInt
                nc.drawRect(-0.14f, -0.9f, 0.14f, 0.1f, trunk)
                val fol = paints.ballFor(Color(0xFF388E3C))
                fol.alpha = alphaInt
                nc.drawCircle(0f, -1.2f, 0.85f, fol)
                nc.drawCircle(0.4f, -0.75f, 0.55f, fol)
            }
            HoleShape.SPECIAL -> {
                val p = paints.ballFor(item.color)
                p.alpha = alphaInt
                nc.drawCircle(0f, -0.9f, 1f, p)
                emojiPaint.alpha = alphaInt
                emojiPaint.textSize = 1.5f
                val yOff = (emojiPaint.ascent() + emojiPaint.descent()) / 2f
                nc.drawText(item.emoji, 0f, -0.9f - yOff, emojiPaint)
                emojiPaint.alpha = 255
            }
        }
        nc.restore()
    }
}

// ————— Menü —————

@Composable
private fun HoleMenuInhalt(
    prefs: SharedPreferences,
    xp: Int,
    lastMode: HoleMode,
    onBack: () -> Unit,
    onKlassik: () -> Unit,
    onDaily: () -> Unit,
    onRush: () -> Unit,
    onWelten: () -> Unit,
    onStats: () -> Unit,
    onErfolge: () -> Unit,
    onSettings: () -> Unit,
    onHilfe: () -> Unit,
) {
    val (lvl, prog) = holeLevelFromXp(xp)
    val hsKlassik = prefs.getInt("fruithole_highscore", 0)
    val hsDaily = prefs.getInt("fruithole_daily_hs_${LocalDate.now().toEpochDay()}", 0)
    val hsRush = prefs.getInt("fruithole_rush_highscore", 0)
    val sterneGesamt = (0..3).sumOf { wi -> (0..2).count { prefs.getBoolean("fruithole_w${wi}_m$it", false) } }

    // Menü-Hero: Loch mit pulsierendem Rand auf Gras
    val heroInf = rememberInfiniteTransition(label = "holeHero")
    val heroPulse by heroInf.animateFloat(
        0.75f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "holeHeroPulse",
    )

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Hungry Hole", HoleAccent, onBack = onBack, right = { GxIconChip("?", onClick = onHilfe) })

            Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(92.dp)) {
                    val hr = size.minDimension * 0.30f * heroPulse
                    // Grasfleck
                    drawCircle(Color(0xFF7CB342), size.minDimension * 0.46f, center)
                    drawCircle(Color(0xFF689F38), size.minDimension * 0.46f, center, style = Stroke(2f))
                    // Glow
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFF472B6).copy(alpha = 0.45f), Color.Transparent),
                            center = center, radius = hr * 1.7f,
                        ),
                        radius = hr * 1.7f, center = center,
                    )
                    // Loch
                    drawOval(Color.Black, Offset(center.x - hr, center.y - hr * 0.62f), Size(hr * 2f, hr * 1.24f))
                    drawOval(
                        Color(0xFFF472B6),
                        Offset(center.x - hr, center.y - hr * 0.62f), Size(hr * 2f, hr * 1.24f),
                        style = Stroke(3f),
                    )
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GxAppear(0) { GxLevelCard(lvl, "$xp XP", prog, HoleAccent) }
                GxAppear(1) {
                    GxModeCard(
                        emoji = "🕳️", title = "Klassik",
                        subtitle = "Leben, Level & wechselnde Gebiete",
                        accent = HoleAccent,
                        highlighted = lastMode == HoleMode.KLASSIK,
                        best = if (hsKlassik > 0) "Best: $hsKlassik" else null,
                        onClick = onKlassik,
                    )
                }
                GxAppear(2) {
                    GxModeCard(
                        emoji = "🎯", title = "Daily-Challenge",
                        subtitle = "Heute dieselbe Map für alle Versuche",
                        accent = HoleAccent,
                        badge = "HEUTE",
                        best = if (hsDaily > 0) "Best heute: $hsDaily" else null,
                        onClick = onDaily,
                    )
                }
                GxAppear(3) {
                    GxModeCard(
                        emoji = "⏱️", title = "Rush Hour",
                        subtitle = "60 Sekunden Dauerregen — Bomben kosten Zeit",
                        accent = HoleAccent,
                        highlighted = lastMode == HoleMode.RUSH,
                        best = if (hsRush > 0) "Best: $hsRush" else null,
                        onClick = onRush,
                    )
                }
                GxAppear(4) {
                    GxModeCard(
                        emoji = "🌍", title = "Welten-Reise",
                        subtitle = "4 Welten mit eigenen Regeln & Missionen",
                        accent = HoleAccent,
                        highlighted = lastMode == HoleMode.WELTEN,
                        best = "$sterneGesamt/12 ★ gesammelt",
                        onClick = onWelten,
                    )
                }
                GxAppear(5) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxSmallAction("📊", "Statistik", Modifier.weight(1f), onStats)
                        GxSmallAction("🏅", "Erfolge", Modifier.weight(1f), onErfolge)
                        GxSmallAction("⚙️", "Optionen", Modifier.weight(1f), onSettings)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ————— Weltwahl —————

@Composable
private fun HoleWeltwahlInhalt(
    prefs: SharedPreferences,
    onZurueck: () -> Unit,
    onStart: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Welten-Reise", HoleAccent, onBack = onZurueck)

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HoleWorlds.forEachIndexed { i, welt ->
                    val sterne = (0..2).count { prefs.getBoolean("fruithole_w${i}_m$it", false) }
                    val vorherSterne = if (i == 0) 3 else (0..2).count { prefs.getBoolean("fruithole_w${i - 1}_m$it", false) }
                    val offen = i == 0 || vorherSterne >= 2
                    GxAppear(i) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(welt.glow.copy(alpha = if (offen) 0.08f else 0.03f))
                                .border(
                                    1.dp,
                                    if (offen) welt.glow.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(20.dp),
                                )
                                .gxPressable(enabled = offen) { onStart(i) }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                        .background(welt.glow.copy(alpha = if (offen) 0.20f else 0.08f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (offen) welt.emoji else "🔒", fontSize = 24.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        welt.name,
                                        fontSize = 16.sp,
                                        color = if (offen) HikariText else HikariTextFaint,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (offen) welt.mechanik else "🔒 ${HoleWorlds[i - 1].name}: 2 Missionen nötig",
                                        fontSize = 12.sp,
                                        color = HikariTextMuted,
                                    )
                                }
                                GxStarRow(sterne)
                            }
                            if (offen) {
                                Spacer(Modifier.height(10.dp))
                                welt.missions.forEachIndexed { mi, m ->
                                    val done = prefs.getBoolean("fruithole_w${i}_m$mi", false)
                                    Text(
                                        "${if (done) "✅" else "⬜"} ${m.desc}",
                                        fontSize = 12.sp,
                                        color = if (done) HikariText else HikariTextMuted,
                                        modifier = Modifier.padding(vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ————— Statistik —————

@Composable
private fun HoleStatsInhalt(prefs: SharedPreferences, xp: Int, onZurueck: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Statistik", HoleAccent, onBack = onZurueck)

            val (lvl, prog) = holeLevelFromXp(xp)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GxAppear(0) { GxLevelCard(lvl, "$xp XP", prog, HoleAccent) }
                GxAppear(1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_runden", 0)}", "Runden", HoleAccent, Modifier.weight(1f))
                        GxStatTile("${prefs.getInt("fruithole_stat_fruechte", 0)}", "Objekte", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_combo", 0)}", "Beste Kombo", HoleAccent, Modifier.weight(1f))
                        GxStatTile("${prefs.getInt("fruithole_stat_entschaerft", 0)}", "Entschärft", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_powerups", 0)}", "Power-ups", HoleAccent, Modifier.weight(1f))
                        GxStatTile("${prefs.getInt("fruithole_stat_gold", 0)}", "Gold-Objekte", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GxStatTile("${prefs.getInt("fruithole_stat_level", 0)}", "Höchstes Level", HoleAccent, Modifier.weight(1f))
                        GxStatTile(holeZeitFmt(prefs.getInt("fruithole_stat_zeit", 0)), "Spielzeit", HoleAccent, Modifier.weight(1f))
                    }
                }
                GxAppear(5) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HikariCardBg)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text("Top 5 Runden", fontSize = 14.sp, color = HikariText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val top = holeTop5(prefs)
                        if (top.isEmpty()) {
                            Text("Noch keine Runden — spiel eine!", fontSize = 13.sp, color = HikariTextFaint)
                        } else {
                            top.forEachIndexed { i, (pts, label) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    Arrangement.SpaceBetween,
                                    Alignment.CenterVertically,
                                ) {
                                    Text("${i + 1}. $label", fontSize = 13.sp, color = HikariTextMuted)
                                    Text("$pts", fontSize = 13.sp, color = HoleAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ————— Erfolge —————

@Composable
private fun HoleErfolgeInhalt(prefs: SharedPreferences, onZurueck: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        GxMenuBackground(HoleAccent)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GxHeader("Erfolge", HoleAccent, onBack = onZurueck)

            val freigeschaltet = HoleAchs.count { prefs.getBoolean("fruithole_ach_${it.id}", false) }
            Text(
                "$freigeschaltet von ${HoleAchs.size} freigeschaltet",
                fontSize = 13.sp,
                color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HoleAchs.forEachIndexed { i, a ->
                    val done = prefs.getBoolean("fruithole_ach_${a.id}", false)
                    GxAppear(i) { GxAchRow(a.emoji, a.title, a.desc, HoleAccent, done) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
