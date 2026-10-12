package com.hikari.app.domain.russian

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RussianProsodyTest {
    private val sr = RuProsody.SAMPLE_RATE

    /** Ton mit linear laufender Frequenz f0→f1 über [seconds]. */
    private fun sweep(f0: Double, f1: Double, seconds: Double): FloatArray {
        val n = (seconds * sr).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val f = f0 + (f1 - f0) * i / n
            phase += 2 * PI * f / sr
            (0.5 * sin(phase)).toFloat()
        }
    }

    @Test
    fun sameMelody_scoresHigh_otherDirection_scoresLow() {
        val up = RuProsody.analyze(sweep(150.0, 250.0, 1.0))
        val upAgain = RuProsody.analyze(sweep(120.0, 200.0, 1.2)) // andere Stimme, anderes Tempo
        val down = RuProsody.analyze(sweep(250.0, 150.0, 1.0))
        val same = assertNotNull(RuProsody.compare(up, upAgain))
        val opposite = assertNotNull(RuProsody.compare(up, down))
        assertTrue(same.melody >= 85, "gleiche Richtung: ${same.melody}")
        assertTrue(opposite.melody <= 20, "entgegengesetzt: ${opposite.melody}")
    }

    @Test
    fun tempoRatioReflectsDuration() {
        val ref = RuProsody.analyze(sweep(150.0, 200.0, 1.0))
        val slow = RuProsody.analyze(sweep(150.0, 200.0, 2.0))
        val c = assertNotNull(RuProsody.compare(ref, slow))
        assertTrue(c.tempoRatio in 1.7f..2.3f, "ratio ${c.tempoRatio}")
    }

    @Test
    fun silenceGivesNoComparison() {
        val silent = RuProsody.analyze(FloatArray(sr))
        val tone = RuProsody.analyze(sweep(150.0, 200.0, 1.0))
        assertTrue(RuProsody.compare(tone, silent) == null)
    }
}
