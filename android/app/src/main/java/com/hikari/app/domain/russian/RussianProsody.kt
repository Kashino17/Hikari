package com.hikari.app.domain.russian

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Satzmelodie, Rhythmus und Tempo aus rohem Audio — ohne Modell, rein
 * Autokorrelation. Verglichen wird in Halbtönen relativ zur eigenen Stimmlage
 * und auf die Sprechzeit normalisiert, damit Mann/Frau und schnell/langsam
 * nicht als »falsche Melodie« zählen.
 */
object RuProsody {
    const val SAMPLE_RATE = 16_000
    private const val FRAME = 640      // 40 ms
    private const val HOP = 160        // 10 ms
    private const val MIN_LAG = 40     // 400 Hz
    private const val MAX_LAG = 228    // ~70 Hz
    private const val POINTS = 40

    class Contour(
        /** Halbtöne je 10-ms-Frame relativ zum Median; NaN = stimmlos/Stille. */
        val semitones: FloatArray,
        /** RMS je Frame. */
        val energy: FloatArray,
        /** Erster/letzter Frame mit Sprache. */
        val speechStart: Int,
        val speechEnd: Int,
    ) {
        val speechSeconds: Float get() = if (speechEnd <= speechStart) 0f else (speechEnd - speechStart) * HOP / SAMPLE_RATE.toFloat()
        val voicedFrames: Int get() = semitones.count { !it.isNaN() }
    }

    data class Comparison(
        /** 0–100: wie ähnlich ist der Tonhöhenverlauf. */
        val melody: Int,
        /** 0–100: wie ähnlich ist die Betonungs-Hüllkurve (Rhythmus). */
        val rhythm: Int,
        /** Deine Sprechzeit / Original (1.0 = gleich schnell). */
        val tempoRatio: Float,
        /** Auf [POINTS] Stützpunkte normalisierte Kurven für die Anzeige (Halbtöne). */
        val refCurve: FloatArray,
        val userCurve: FloatArray,
    )

    fun analyze(pcm: FloatArray): Contour {
        val frames = if (pcm.size < FRAME) 0 else (pcm.size - FRAME) / HOP + 1
        if (frames == 0) return Contour(FloatArray(0), FloatArray(0), 0, 0)
        val energy = FloatArray(frames)
        val f0 = FloatArray(frames) { Float.NaN }
        for (i in 0 until frames) {
            val off = i * HOP
            var sum = 0.0
            for (k in 0 until FRAME) sum += pcm[off + k] * pcm[off + k]
            energy[i] = sqrt(sum / FRAME).toFloat()
        }
        val peak = energy.maxOrNull() ?: 0f
        val gate = peak * 0.06f
        for (i in 0 until frames) {
            if (energy[i] < gate || peak <= 0f) continue
            val off = i * HOP
            var r0 = 0.0
            for (k in 0 until FRAME) r0 += pcm[off + k] * pcm[off + k]
            if (r0 <= 1e-9) continue
            var bestLag = -1
            var best = 0.0
            for (lag in MIN_LAG..MAX_LAG) {
                var r = 0.0
                for (k in 0 until FRAME - lag) r += pcm[off + k] * pcm[off + k + lag]
                val norm = r / r0
                if (norm > best) { best = norm; bestLag = lag }
            }
            if (bestLag > 0 && best > 0.4) f0[i] = SAMPLE_RATE.toFloat() / bestLag
        }
        medianSmooth(f0)
        val voiced = f0.filter { !it.isNaN() }.sorted()
        val semis = FloatArray(frames) { Float.NaN }
        if (voiced.isNotEmpty()) {
            val med = voiced[voiced.size / 2]
            for (i in 0 until frames) if (!f0[i].isNaN()) semis[i] = (12.0 * ln((f0[i] / med).toDouble()) / ln(2.0)).toFloat()
        }
        var start = energy.indexOfFirst { it >= gate }
        var end = energy.indexOfLast { it >= gate }
        if (start < 0) { start = 0; end = 0 }
        return Contour(semis, energy, start, end + 1)
    }

    private fun medianSmooth(a: FloatArray) {
        val src = a.copyOf()
        for (i in 2 until a.size - 2) {
            val w = (i - 2..i + 2).map { src[it] }
            val v = w.filter { !it.isNaN() }
            a[i] = if (v.size >= 3) v.sorted()[v.size / 2] else Float.NaN
        }
    }

    fun compare(ref: Contour, user: Contour): Comparison? {
        if (ref.voicedFrames < 8 || user.voicedFrames < 8) return null
        val rc = curve(ref)
        val uc = curve(user)
        val melody = (correlation(rc, uc).coerceIn(0f, 1f) * 100).toInt()
        val re = envelope(ref)
        val ue = envelope(user)
        val rhythm = (correlation(re, ue).coerceIn(0f, 1f) * 100).toInt()
        val ratio = if (ref.speechSeconds > 0f) user.speechSeconds / ref.speechSeconds else 1f
        return Comparison(melody, rhythm, ratio, rc, uc)
    }

    /** Tonhöhe über die Sprechzeit auf [POINTS] Punkte; Lücken werden linear gefüllt. */
    private fun curve(c: Contour): FloatArray {
        val span = (c.speechEnd - c.speechStart).coerceAtLeast(1)
        val raw = FloatArray(POINTS) { p ->
            val from = c.speechStart + p * span / POINTS
            val to = max(from + 1, c.speechStart + (p + 1) * span / POINTS)
            var sum = 0f
            var n = 0
            for (i in from until min(to, c.semitones.size)) if (!c.semitones[i].isNaN()) { sum += c.semitones[i]; n++ }
            if (n == 0) Float.NaN else sum / n
        }
        return fill(raw)
    }

    private fun envelope(c: Contour): FloatArray {
        val span = (c.speechEnd - c.speechStart).coerceAtLeast(1)
        val out = FloatArray(POINTS) { p ->
            val from = c.speechStart + p * span / POINTS
            val to = max(from + 1, c.speechStart + (p + 1) * span / POINTS)
            var sum = 0f
            var n = 0
            for (i in from until min(to, c.energy.size)) { sum += c.energy[i]; n++ }
            if (n == 0) 0f else sum / n
        }
        val mx = out.maxOrNull() ?: 0f
        return if (mx > 0f) FloatArray(POINTS) { out[it] / mx } else out
    }

    private fun fill(a: FloatArray): FloatArray {
        val out = a.copyOf()
        val known = out.indices.filter { !out[it].isNaN() }
        if (known.isEmpty()) return FloatArray(a.size)
        for (i in out.indices) {
            if (!out[i].isNaN()) continue
            val prev = known.lastOrNull { it < i }
            val next = known.firstOrNull { it > i }
            out[i] = when {
                prev != null && next != null -> out[prev] + (out[next] - out[prev]) * (i - prev) / (next - prev)
                prev != null -> out[prev]
                else -> out[next!!]
            }
        }
        return out
    }

    internal fun correlation(a: FloatArray, b: FloatArray): Float {
        val n = min(a.size, b.size)
        if (n == 0) return 0f
        val ma = a.take(n).average().toFloat()
        val mb = b.take(n).average().toFloat()
        var num = 0f
        var da = 0f
        var db = 0f
        for (i in 0 until n) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y
            da += x * x
            db += y * y
        }
        val den = sqrt(da * db)
        return if (den < 1e-6f) 0f else num / den
    }

    /** Kurzer deutscher Befund zum Tempo. */
    fun tempoText(ratio: Float): String = when {
        abs(ratio - 1f) <= 0.15f -> "Tempo passt"
        ratio > 1f -> "Du bist %.1f× langsamer".format(ratio)
        else -> "Du bist %.1f× schneller".format(1f / ratio)
    }
}
