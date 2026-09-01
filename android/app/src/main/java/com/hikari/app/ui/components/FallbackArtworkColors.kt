package com.hikari.app.ui.components

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Farbpalette für das Fallback-Artwork als nackte ARGB-Longs — bewusst ohne
 * Compose-Abhängigkeit, damit die Ableitung in JVM-Unit-Tests prüfbar bleibt.
 *
 * [start]/[end] bilden den diagonalen Verlauf (satt oben, fast schwarz unten),
 * [glow] ist der hellere radiale Akzent in der Mitte.
 */
data class FallbackArtworkColors(
    val start: Long,
    val end: Long,
    val glow: Long,
)

// Neutrale Anthrazit-Variante für leere/blank Seeds — unauffällig, aber
// bewusst gestaltet (passt zu HikariBg/HikariSurfaceHigh).
private val NeutralFallbackColors = FallbackArtworkColors(
    start = 0xFF34343C,
    end = 0xFF16161A,
    glow = 0xFF4A4A54,
)

/**
 * Leitet aus einem Seed (meist der Titel) deterministisch drei harmonische
 * Farben ab: Der FNV-1a-Hash bestimmt den Grundfarbton, daraus entstehen über
 * HSV drei Stufen — sattes Mittel oben, dunkles Ende unten (cinematic),
 * hellerer Glanz als Akzent. Leerer/blank Seed → neutrale Anthrazit-Palette.
 */
fun fallbackArtworkColors(seed: String): FallbackArtworkColors {
    val key = seed.trim()
    if (key.isEmpty()) return NeutralFallbackColors
    val hash = fnv1a(key.lowercase())
    val hue = (hash % 360u).toFloat()
    // Zweitton leicht verschoben → Duo-Ton-Verlauf statt flächiger Einzelfarbe.
    val hueShift = 24f + ((hash.toLong() shr 12) and 0x1F).toFloat()
    return FallbackArtworkColors(
        start = hsvToArgb(hue, 0.62f, 0.36f),
        end = hsvToArgb(hue + hueShift, 0.72f, 0.12f),
        glow = hsvToArgb(hue + hueShift / 2f, 0.50f, 0.60f),
    )
}

/** FNV-1a-Hash — spezifiziert und plattformstabil (im Gegensatz zu String.hashCode ein explizit gewähltes Verfahren). */
internal fun fnv1a(text: String): UInt {
    var h = 0x811C9DC5u
    for (c in text) {
        h = h xor c.code.toUInt()
        h *= 0x01000193u
    }
    return h
}

/** HSV → ARGB-Long (0xAARRGGBB), reine Mathematik ohne android.graphics.Color. */
internal fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(f: Float): Long = ((f + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    return (0xFFL shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}
