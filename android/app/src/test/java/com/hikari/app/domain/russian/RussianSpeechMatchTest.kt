package com.hikari.app.domain.russian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spracherkennung fair, aber streng: Format-Artefakte verzeihen, echte Fehler nicht. */
class RussianSpeechMatchTest {

    private fun ok(target: String, vararg heard: String) = RuAnswerCheck.speech(target, heard.toList()).passed

    // ── Zahlen kommen als Ziffern ───────────────────────────────────────────

    @Test
    fun zifferZaehltAlsZahlwort() {
        assertTrue(ok("д+есять", "10"))
        assertTrue(ok("тр+идцать", "30"))
        assertTrue(ok("дв+адцать", "20"))
        assertTrue(ok("с+орок", "40"))
        assertTrue(ok("Мне дв+адцать од+ин год.", "мне 21 год"))
        assertTrue(ok("Встр+етимся в пять час+ов.", "встретимся в 5 часов"))
        assertTrue(ok("Сег+одня п+ятое м+ая.", "сегодня 5-е мая"))
        assertTrue(ok("Мне тр+идцать два г+ода.", "мне 32 года"))
        assertTrue(ok("в в+осемь тр+идцать", "в 8:30"))
        assertTrue(ok("Я жив+у здесь пять лет.", "я живу здесь 5 лет"))
    }

    @Test
    fun falscheZahlBleibtFalsch() {
        assertFalse(ok("тр+идцать", "13"))
        assertFalse(ok("тр+идцать", "тринадцать"))
        assertFalse(ok("д+есять", "11"))
        assertFalse(ok("Мне дв+адцать од+ин год.", "мне 22 года"))
    }

    @Test
    fun zahlwoerter() {
        assertEquals(listOf("двадцать", "один"), RuSpeechMatch.cardinal(21))
        assertEquals(listOf("тысяча", "девятьсот", "девяносто"), RuSpeechMatch.cardinal(1990))
        assertEquals(listOf("двадцать", "первый"), RuSpeechMatch.ordinal(21))
        assertEquals(listOf("сотый"), RuSpeechMatch.ordinal(100))
        assertTrue(RuSpeechMatch.sameNumeral("пяти", "пять"))
        assertTrue(RuSpeechMatch.sameNumeral("двух", "два"))
        assertFalse(RuSpeechMatch.sameNumeral("пятница", "пять"))
    }

    // ── Gleich klingende Schreibungen ───────────────────────────────────────

    @Test
    fun unbetonteVokaleUndAuslautSindGleich() {
        assertTrue(ok("+Это интер+есно.", "эта интересно"))
        assertTrue(ok("Хорош+о.", "харашо"))
        assertTrue(ok("Мой г+ород.", "мой горот"))
    }

    @Test
    fun betonterVokalMussStimmen() {
        assertFalse(ok("м+ама", "мома"))
        assertFalse(ok("д+ом", "дам"))
    }

    @Test
    fun lateinischeNamenZaehlen() {
        assertTrue(ok("Мен+я зов+ут Кад+ир.", "меня зовут Kadir"))
    }

    @Test
    fun alternativeHypothesenZaehlen() {
        // Die Erkennung schwankt zwischen zwei Lesarten — beide gehören zum Ton.
        assertTrue(ok("Я не зн+аю.", "я не знал", "я не знаю"))
    }

    // ── Streng bleibt streng ────────────────────────────────────────────────

    @Test
    fun echteFehlerFallenDurch() {
        assertFalse(ok("Спас+ибо!", "пожалуйста"))
        assertFalse(ok("Я не поним+аю.", "я понимаю"))
        assertFalse(ok("Я уч+у р+усский яз+ык.", "я учу английский язык"))
        assertFalse(ok("Мы б+ыли там.", "ми были там"))
        assertFalse(ok("Да.", ""))
    }

    // ── Ganzer Kurs ─────────────────────────────────────────────────────────

    private val course = parseRuCourse(java.io.File("src/main/assets/russian/course.json").readText())
    private val allTexts = course.days.flatMap { d ->
        d.items.flatMap { listOfNotNull(it.ru, it.ruF) } + d.dialog.lines.flatMap { listOfNotNull(it.ru, it.ruF) }
    }

    @Test
    fun jederKursSatzBestehtWennErWoertlichErkanntWird() {
        val failed = allTexts.filterNot { ok(it, RussianPhonetics.plain(it)) }
        assertTrue(failed.isEmpty(), "Fällt trotz wörtlicher Erkennung durch: ${failed.take(10)}")
    }

    @Test
    fun jederKursSatzBestehtMitZiffernStattZahlwoertern() {
        val digits = mapOf(
            "один" to "1", "два" to "2", "три" to "3", "четыре" to "4", "пять" to "5", "шесть" to "6",
            "семь" to "7", "восемь" to "8", "девять" to "9", "десять" to "10", "двадцать" to "20",
            "тридцать" to "30", "сорок" to "40", "пятьдесят" to "50", "сто" to "100",
        )
        val failed = mutableListOf<String>()
        for (t in allTexts) {
            val plain = RussianPhonetics.plain(t).lowercase()
            val asDigits = plain.split(' ').joinToString(" ") { w ->
                val core = w.trim { !it.isLetter() }
                digits[core]?.let { w.replace(core, it) } ?: w
            }
            if (asDigits != plain && !ok(t, asDigits)) failed += "$t → $asDigits"
        }
        assertTrue(failed.isEmpty(), "Ziffern nicht erkannt: ${failed.take(10)}")
    }

    // ── Rückmeldung ─────────────────────────────────────────────────────────

    @Test
    fun jedesVerfehlteWortBekommtEinenHinweis() {
        val sc = RuAnswerCheck.speech("Я уч+у р+усский яз+ык.", listOf("я учу английский язык"))
        val hints = RuSoundCoach.hints(sc)
        assertEquals(1, hints.size)
        assertEquals("русский", hints[0].target)
        assertTrue(hints[0].tip.isNotBlank())
    }

    @Test
    fun fehlendesWortWirdBenannt() {
        val sc = RuAnswerCheck.speech("Я оч+ень уст+ал.", listOf("я устал"))
        val hint = RuSoundCoach.hints(sc).single()
        assertEquals("очень", hint.target)
        assertNull(hint.heard)
        assertTrue("nicht zu hören" in hint.tip)
    }

    @Test
    fun betonungInDerLesehilfe() {
        // Betonte Silbe groß — so sieht man sofort, wo der Druck hingehört.
        assertEquals("dJEssit'", RuSoundCoach.reading("д+есять"))
    }
}
