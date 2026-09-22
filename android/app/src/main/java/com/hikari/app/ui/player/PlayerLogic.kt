package com.hikari.app.ui.player

/**
 * Reine, testbare Player-Logik — kein Compose, kein ExoPlayer.
 * Alles, was der Netflix-artige Player an Entscheidungen trifft, steht hier,
 * damit Empfindlichkeit und Verhalten per Unit-Test festgenagelt sind.
 */

// ── Tap-Zonen ────────────────────────────────────────────────────────────────

enum class TapZone { Left, Center, Right }

/** Anteil der Breite je Seite, in dem ein Doppeltipp spult. Die Mitte (36 %) ist neutral. */
const val SEEK_ZONE_FRACTION = 0.32f

fun tapZoneFor(x: Float, widthPx: Float): TapZone {
    if (widthPx <= 0f) return TapZone.Center
    return when {
        x < widthPx * SEEK_ZONE_FRACTION -> TapZone.Left
        x > widthPx * (1f - SEEK_ZONE_FRACTION) -> TapZone.Right
        else -> TapZone.Center
    }
}

// ── Seek-Stapelung ───────────────────────────────────────────────────────────

/**
 * Netflix stapelt schnelle Doppeltipps: +10 → +20 → +30. Richtungswechsel
 * oder eine Pause länger als [windowMs] setzen den Stapel zurück.
 * Rückgabe: der *kumulierte* Versatz für die Anzeige (das Spulen selbst
 * geschieht pro Tipp um [stepMs]).
 */
class SeekAccumulator(private val stepMs: Long = 10_000L, private val windowMs: Long = 800L) {
    private var lastAt = Long.MIN_VALUE
    private var lastForward: Boolean? = null
    private var total = 0L

    fun tap(forward: Boolean, nowMs: Long): Long {
        val expired = lastAt == Long.MIN_VALUE || nowMs - lastAt > windowMs
        if (expired || lastForward != forward) total = 0L
        total += if (forward) stepMs else -stepMs
        lastAt = nowMs
        lastForward = forward
        return total
    }
}

// ── Helligkeit / Lautstärke ──────────────────────────────────────────────────

/** Ein Wisch über 60 % der Höhe deckt die ganze Spanne 0..1 ab. */
const val LEVEL_DRAG_SPAN_FRACTION = 0.6f

fun adjustLevel(current: Float, dragDeltaY: Float, heightPx: Float): Float {
    if (heightPx <= 0f) return current
    val span = heightPx * LEVEL_DRAG_SPAN_FRACTION
    val delta = -dragDeltaY / span
    return (current + delta).coerceIn(0f, 1f)
}

// ── Orientierung ─────────────────────────────────────────────────────────────

enum class OrientationMode { Auto, Landscape, Portrait }
enum class PlayerOrientation { Landscape, Portrait }

fun resolveOrientation(mode: OrientationMode, videoW: Int, videoH: Int): PlayerOrientation = when (mode) {
    OrientationMode.Landscape -> PlayerOrientation.Landscape
    OrientationMode.Portrait -> PlayerOrientation.Portrait
    OrientationMode.Auto -> if (videoW > 0 && videoH > videoW) PlayerOrientation.Portrait else PlayerOrientation.Landscape
}

/**
 * Das Hochformat-Panel (Video oben, Infos + Folgen darunter) gibt es nur,
 * wenn ein Querformat-Video im Hochformat läuft. Ein 9:16-Video füllt den
 * Bildschirm selbst.
 */
fun showsPortraitPanel(orientation: PlayerOrientation, videoW: Int, videoH: Int): Boolean {
    if (orientation != PlayerOrientation.Portrait) return false
    val portraitVideo = videoW > 0 && videoH > videoW
    return !portraitVideo
}

// ── Scrubber ─────────────────────────────────────────────────────────────────

/** Gain im Feinmodus (Finger deutlich ober-/unterhalb der Leiste). */
const val SCRUB_FINE_GAIN = 0.25f

fun scrubTarget(startMs: Long, durationMs: Long, dragDeltaX: Float, trackWidthPx: Float, fine: Boolean): Long {
    val width = trackWidthPx.coerceAtLeast(1f)
    val gain = if (fine) SCRUB_FINE_GAIN else 1f
    val delta = (dragDeltaX / width) * durationMs.toFloat() * gain
    return (startMs + delta.toLong()).coerceIn(0L, durationMs.coerceAtLeast(0L))
}

// ── Zeitformat ───────────────────────────────────────────────────────────────

fun formatClock(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatRemaining(positionMs: Long, durationMs: Long): String =
    "-" + formatClock((durationMs - positionMs).coerceAtLeast(0L))

// ── Pinch → Zoom ─────────────────────────────────────────────────────────────

/** true = auf Bildschirm füllen, false = Original, null = zu wenig Bewegung. */
fun pinchDecision(scale: Float): Boolean? = when {
    scale >= 1.2f -> true
    scale <= 0.8f -> false
    else -> null
}

// ── Labels ───────────────────────────────────────────────────────────────────

fun episodeLabel(season: Int?, episode: Int?): String = buildString {
    if (season != null) append("S$season")
    if (episode != null) {
        if (isNotEmpty()) append(' ')
        append("F$episode")
    }
}
