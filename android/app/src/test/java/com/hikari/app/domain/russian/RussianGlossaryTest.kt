package com.hikari.app.domain.russian

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/** Wort-für-Wort-Bedeutungen: jeder Kurs-Satz hat eine Glosse pro Wort. */
class RussianGlossaryTest {

    private val assets = File("src/main/assets/russian")
    private val data = ruJson.decodeFromString(RuGlossData.serializer(), File(assets, "glosses.json").readText())
    private val course = parseRuCourse(File(assets, "course.json").readText())

    @Test
    fun jederKursSatzHatPassendeGlossen() {
        val missing = mutableListOf<String>()
        for (d in course.days) {
            val texts = d.items.flatMap { listOfNotNull(it.ru, it.ruF) } +
                d.dialog.lines.flatMap { listOfNotNull(it.ru, it.ruF) }
            for (t in texts) {
                val g = data.s[RussianPhonetics.plain(t).trim()]
                if (g == null || g.size != RussianPhonetics.words(t).size) missing += t
            }
        }
        assertTrue(missing.isEmpty(), "Ohne passende Glosse (build_glosses.py laufen lassen): ${missing.take(10)}")
    }

    @Test
    fun festeWendungWirdEinBlock() {
        val chunks = RussianGlossary.chunks("Потом+у что +это интер+есно.", data)
        assertEquals(listOf("Потому", "что"), chunks.first().words.map { it.plain })
        assertEquals("weil", chunks.first().gloss)
        assertEquals(3, chunks.size)
        assertTrue(chunks.last().words.last().after == ".")
    }

    @Test
    fun umschriftProWortPasstZumGanzenSatz() {
        for (s in listOf("Потом+у что +это интер+есно.", "Я в семь вст+аю.", "Пойдём в каф+е? — Почем+у бы и нет?")) {
            val flat = RussianPhonetics.transliterate(s).joinToString("") { it.text }.filter { it.isLetter() }
            val perWord = RussianPhonetics.words(s).joinToString("") { w -> w.latin.joinToString("") { it.text } }
                .filter { it.isLetter() }
            assertEquals(flat, perWord, s)
        }
    }

    @Test
    fun laufzeitSatzNutztBekannteTeile() {
        // Kombi aus zwei Kurs-Sätzen, die so nicht in den Daten steht.
        val a = course.days.first().items.first().ru
        val b = course.days.first().items[1].ru
        val glosses = RussianGlossary.glossesFor("$a $b", data)
        assertEquals(RussianPhonetics.words("$a $b").size, glosses.size)
        glosses.forEach { assertNotNull(it) }
    }

    @Test
    fun satzzeichenKlebenAmWort() {
        val w = RussianPhonetics.words("Пойдём в каф+е? — Почем+у бы и нет?")
        assertEquals("?", w[2].after)
        assertEquals("— ", w[3].before)
        assertEquals("кафе́", w[2].cyrillic)
    }

    @Test
    fun fehlendeBetonungWirdErgaenzt() {
        assertEquals("Прив+ет! Ты л+юбишь к+офе?", RussianGlossary.withStress("Привет! Ты любишь кофе?", data))
        // Schon betonte Texte bleiben unverändert.
        assertEquals("Прив+ет!", RussianGlossary.withStress("Прив+ет!", data))
        val latin = RussianGlossary.chunks("Привет!", data).single().words.single().latin.joinToString("") { it.text }
        assertEquals("Priwjet", latin)
    }
}
