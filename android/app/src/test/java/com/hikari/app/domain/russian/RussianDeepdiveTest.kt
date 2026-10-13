package com.hikari.app.domain.russian

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianDeepdiveTest {
    private val assets = File("src/main/assets/russian")

    /** Muss mit hashlib.sha1(ru.encode()).hexdigest()[:10] aus generate_deepdive.py übereinstimmen. */
    @Test fun clipNameIstSha1Praefix() {
        assertEquals("dd_86f7e437fa", RuDeepdiveLogic.clipName("a"))
    }

    @Test fun jedesDeepdiveAudioExistiert() {
        val json = File(assets, "deepdive.json")
        if (!json.exists()) return
        val dd = ruJson.decodeFromString(RuDeepdive.serializer(), json.readText())
        val course = parseRuCourse(File(assets, "course.json").readText())
        val ids = course.days.flatMap { d -> d.items.map { it.id } }.toSet()
        val missing = mutableListOf<String>()
        for (e in dd.entries) {
            assertTrue("${e.id} ist kein Kurs-Satz", e.id in ids)
            val strings = e.variants.map { it.ru } + e.examples.map { it.ru } + e.gender?.forms.orEmpty().map { it.ru }
            for (ru in strings) {
                val n = RuDeepdiveLogic.clipName(ru)
                if (!File(assets, "audio/$n.ogg").exists() || !File(assets, "audio/${n}_slow.ogg").exists()) missing += ru
            }
        }
        assertTrue("Audio fehlt für: $missing", missing.isEmpty())
    }

    @Test fun verwandteSaetzeFindenGleichesWort() {
        val a = RuPhrase("a", "Я хоч+у к+офе.", "Ich möchte Kaffee.", null, null, 9, "a")
        val b = RuPhrase("b", "Я люблю к+офе.", "Ich liebe Kaffee.", null, null, 8, "b")
        val c = RuPhrase("c", "Пок+а!", "Tschüss", null, null, 1, "c")
        assertEquals(listOf("b"), RuDeepdiveLogic.related(a, listOf(a, b, c)).map { it.id })
    }
}
