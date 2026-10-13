package com.hikari.app.domain.russian

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianPatternsTest {
    private val assets = File("src/main/assets/russian")
    private val patterns by lazy {
        ruJson.decodeFromString(RuPatterns.serializer(), File(assets, "patterns.json").readText()).patterns
    }

    @Test fun baueSatzMitMaennlichenUndWeiblichenFormen() {
        val p = patterns.first { it.id == "p07_proshloe" }
        val m = RuPatternLogic.build(p, listOf(0, 0), RuGender.MALE)
        val f = RuPatternLogic.build(p, listOf(0, 0), RuGender.FEMALE)
        assertEquals("Вчер+а я был в каф+е.", m.ru)
        assertEquals("Вчер+а я был+а в каф+е.", f.ru)
        assertEquals("Gestern war ich im Café.", m.de)
    }

    @Test fun jedePlatzhalterWirdErsetzt() {
        for (p in patterns) {
            val s = RuPatternLogic.build(p, List(p.slots.size) { 0 }, RuGender.MALE)
            assertTrue("${p.id}: Platzhalter übrig in ${s.ru}", !s.ru.contains("{") && !s.de.contains("{"))
        }
    }

    @Test fun alleSzenenUndKernsaetzeHabenAudio() {
        val missing = mutableListOf<String>()
        fun need(name: String, text: String) {
            if (!File(assets, "audio/$name.ogg").exists() || !File(assets, "audio/${name}_slow.ogg").exists()) missing += text
        }
        for (p in patterns) {
            for (l in p.scene.lines) {
                need(RuPatternLogic.lineClip(l, RuGender.MALE), l.ru)
                if (l.ruF != null) need(RuPatternLogic.lineClip(l, RuGender.FEMALE), l.ruF)
            }
            for (c in p.core + p.free.model) need(RuDeepdiveLogic.clipName(c.ru), c.ru)
        }
        assertTrue("Audio fehlt für: $missing", missing.isEmpty())
    }
}
