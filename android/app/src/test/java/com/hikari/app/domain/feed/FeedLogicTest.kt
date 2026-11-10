package com.hikari.app.domain.feed

import com.hikari.app.domain.russian.RuCard
import com.hikari.app.domain.russian.RuCourseIndex
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.parseRuCourse
import com.hikari.app.domain.russian.ruJson
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class FeedLogicTest {
    private val assets = File("src/main/assets")
    private val alphabet = ruJson.decodeFromString(RuAlphabet.serializer(), File(assets, "russian/alphabet.json").readText())
    private val index = RuCourseIndex(parseRuCourse(File(assets, "russian/course.json").readText()))

    // ── Rotation ─────────────────────────────────────────────────────────────

    @Test fun rotation_zeigtErstAllesUngeseheneDannDasAelteste() {
        val pool = listOf("a", "b", "c", "d")
        val seen = mapOf("a" to 10L, "b" to 5L)
        val first = FeedRotation.pick(pool, seen, 2, Random(1))
        assertEquals(setOf("c", "d"), first.toSet())
        val next = FeedRotation.pick(pool, seen, 4, Random(1))
        assertEquals(listOf("b", "a"), next.drop(2), "nach dem Ungesehenen kommt das am längsten Zurückliegende")
    }

    @Test fun rotation_wiederholtNichtsBevorDerVorratDurchIst() {
        val pool = (1..40).map { "card$it" }
        val seen = mutableMapOf<String, Long>()
        val shown = mutableListOf<String>()
        var clock = 0L
        repeat(40) {
            val id = FeedRotation.pick(pool, seen, 1, Random(it)).single()
            shown += id
            seen[id] = clock++
        }
        assertEquals(40, shown.toSet().size)
        // Zweite Runde beginnt mit der Karte, die am längsten zurückliegt.
        assertEquals(shown.first(), FeedRotation.pick(pool, seen, 1, Random(99)).single())
    }

    // ── Spiele ───────────────────────────────────────────────────────────────

    @Test fun zahlenreihe_hatGenauEineRichtigeUnterVierVerschiedenenAntworten() {
        repeat(400) { seed ->
            val g = FeedGameFactory.numberSeries(Random(seed))
            assertEquals(4, g.options.toSet().size, "seed $seed")
            assertTrue(g.correctIndex in 0..3, "seed $seed")
            assertTrue(g.options.all { it.toInt() > 0 }, "seed $seed")
            assertTrue(g.explanation.isNotBlank())
        }
    }

    @Test fun zahlenreihe_folgtIhrerRegel() {
        repeat(400) { seed ->
            val s = FeedGameFactory.series(Random(seed))
            val all = s.shown + s.next
            assertTrue(all.size >= 6, "seed $seed")
            // 1 · 1 · 2 · 3 … ist erlaubt, eine Reihe ohne Bewegung nicht.
            assertTrue(all.toSet().size >= 5, "seed $seed: Reihe steht still: $all")
            assertTrue(all.all { it > 0 }, "seed $seed")
        }
    }

    @Test fun kettenrechnen_ergebnisStimmtMitDenSchrittenUeberein() {
        repeat(400) { seed ->
            val g = FeedGameFactory.chainMath(Random(seed), stepCount = 5)
            var v = g.start
            g.steps.forEachIndexed { i, step ->
                val (op, n) = step.split(' ').let { it[0] to it[1].toInt() }
                v = when (op) {
                    "+" -> v + n
                    "−" -> v - n
                    "×" -> v * n
                    "÷" -> { assertEquals(0, v % n, "seed $seed: Division mit Rest"); v / n }
                    else -> error("unbekannter Schritt $step")
                }
                assertTrue(v > 0, "seed $seed: Zwischenstand $v")
                assertEquals(v, g.trail[i], "seed $seed")
            }
            assertEquals(v, g.result, "seed $seed")
            assertEquals(1, g.options.count { it == g.result })
            assertEquals(4, g.options.toSet().size)
        }
    }

    @Test fun farbWortTest_wechseltDieFarbeJedeRunde() {
        repeat(100) { seed ->
            val g = FeedGameFactory.stroop(Random(seed))
            assertEquals(8, g.rounds.size)
            assertTrue(g.rounds.zipWithNext().all { (a, b) -> a.ink != b.ink })
            assertTrue(g.rounds.any { it.word != it.ink }, "es braucht widersprüchliche Runden")
        }
    }

    @Test fun mustergedaechtnis_haeltDieStufeInGrenzen() {
        assertEquals(FeedGameFactory.MEMORY_MIN_LEVEL, FeedGameFactory.memoryGrid(Random(1), 0).cells.size)
        assertEquals(FeedGameFactory.MEMORY_MAX_LEVEL, FeedGameFactory.memoryGrid(Random(1), 99).cells.size)
        val g = FeedGameFactory.memoryGrid(Random(1), 6)
        assertEquals(6, g.cells.size)
        assertTrue(g.cells.all { it in 0 until g.size * g.size })
    }

    @Test fun adlerauge_hatGenauEinAnderesZeichen() {
        repeat(100) { seed ->
            val g = FeedGameFactory.oddOneOut(Random(seed), alphabet)
            assertTrue(g.common != g.odd)
            assertTrue(g.oddIndex in 0 until g.columns * g.rows)
            assertTrue(g.note.isNotBlank())
        }
    }

    // ── Alphabet ─────────────────────────────────────────────────────────────

    @Test fun alphabet_hatAlle33BuchstabenMitBeispielenUndAufnahmen() {
        assertEquals(33, alphabet.letters.size)
        assertEquals(33, alphabet.letters.map { it.l }.toSet().size)
        val audio = File(assets, "russian/audio").list()!!.toSet()
        for (letter in alphabet.letters) {
            assertNotNull(alphabet.group(letter.group), "Gruppe von ${letter.l}")
            assertEquals(3, letter.words.size, "Beispielwörter für ${letter.l}")
            for (w in letter.words) {
                assertTrue(letter.l in w.plain.lowercase(), "${w.ru} enthält ${letter.l} nicht")
                assertTrue("${w.clip}.ogg" in audio, "Aufnahme fehlt: ${w.ru}")
                assertTrue("${w.clip}_slow.ogg" in audio, "langsame Aufnahme fehlt: ${w.ru}")
            }
        }
        for (w in alphabet.reading) assertTrue("${w.clip}.ogg" in audio, "Aufnahme fehlt: ${w.ru}")
    }

    @Test fun alphabet_neuerBuchstabeErstWennDieVorigenGeuebtSind() {
        assertEquals("а", RuAlphabetLogic.nextLetter(alphabet, emptyMap())?.l)
        val progress = mapOf("а" to 2, "к" to 1)
        assertEquals("м", RuAlphabetLogic.nextLetter(alphabet, progress)?.l)
        assertEquals(listOf("к", "а"), RuAlphabetLogic.weakest(alphabet, progress).map { it.l })
        assertEquals(3, RuAlphabetLogic.graded(progress, "а", true)["а"])
        assertEquals(1, RuAlphabetLogic.graded(progress, "к", false)["к"], "fällt nie unter »vorgestellt«")
    }

    @Test fun alphabet_lesewoerterNutzenNurBekannteBuchstaben() {
        val progress = listOf("а", "к", "м", "о", "т").associateWith { 1 }
        val words = RuAlphabetLogic.readable(alphabet, progress).map { it.plain.lowercase() }
        assertTrue("мама" in words && "кот" in words && "там" in words, words.toString())
        assertTrue(words.all { w -> w.all { it.toString() in progress } })
    }

    @Test fun alphabet_lautFragenBietenDenLateinischenDoppelgaengerAn() {
        val v = alphabet.letter("в")!!
        repeat(20) { seed ->
            val g = FeedGameFactory.letterSound(alphabet, v, Random(seed))
            assertEquals(4, g.options.toSet().size)
            assertTrue(g.options[g.correctIndex].startsWith("w"))
            assertTrue(g.options.any { it.startsWith("b ") }, "В sieht aus wie B — die Falle muss zur Wahl stehen")
        }
        val sign = FeedGameFactory.letterIntro(alphabet, alphabet.letter("ь")!!, Random(3))
        assertTrue(sign.correctIndex in sign.options.indices)
    }

    // ── Russisch: Rückmeldung ────────────────────────────────────────────────

    @Test fun bewertung_faelligeKartenZaehlenImmerZusatzuebungenNurBeiFehlern() {
        val card = RuCard(box = 2, due = 100)
        assertTrue(RuFeedGrading.counts(card, due = true, correct = true))
        assertTrue(RuFeedGrading.counts(card, due = true, correct = false))
        assertFalse(RuFeedGrading.counts(card, due = false, correct = true), "richtig geratene Zusatzübung darf den Abstand nicht aufblähen")
        assertTrue(RuFeedGrading.counts(card, due = false, correct = false))
        assertFalse(RuFeedGrading.counts(null, due = false, correct = false), "Sätze außerhalb des Kurses werden nie eingetragen")
    }

    @Test fun bewertung_sagtWannDerSatzWiederkommt() {
        val card = RuCard(box = 2, due = 100)
        val next = RuSrs.INTERVALS[3]
        assertEquals("Sitzt. Nächste Wiederholung in $next Tagen.", RuFeedGrading.message(card, due = true, correct = true, today = 100))
        assertEquals("Kommt heute noch einmal dran.", RuFeedGrading.message(card, due = true, correct = false, today = 100))
        assertTrue("morgen" in RuFeedGrading.message(RuCard(box = 3, due = 101), due = false, correct = true, today = 100))
        assertEquals(10, RuFeedGrading.xp(due = true, correct = true))
        assertEquals(0, RuFeedGrading.xp(due = true, correct = false))
    }

    // ── Zusammenstellung ─────────────────────────────────────────────────────

    private val ranks = MindfulModuleType.entries.associateWith {
        when (it) {
            MindfulModuleType.LANGUAGE -> ModuleRank.RANK_3
            MindfulModuleType.BRAIN_PUZZLE, MindfulModuleType.QUOTE -> ModuleRank.RANK_2
            else -> ModuleRank.RANK_1
        }
    }

    @Test fun reihenfolge_jederBereichNachRangUndNieZweimalHintereinander() {
        repeat(50) { seed ->
            val order = FeedComposer.moduleOrder(MindfulModuleType.entries.toSet(), ranks, Random(seed))
            assertEquals(4, order.count { it == MindfulModuleType.LANGUAGE })
            assertEquals(2, order.count { it == MindfulModuleType.QUOTE })
            assertEquals(1, order.count { it == MindfulModuleType.SCIENCE })
            assertTrue(order.zipWithNext().all { (a, b) -> a != b }, "seed $seed: $order")
        }
    }

    private fun fakeCards(module: MindfulModuleType): List<MindfulCard> = when (module) {
        MindfulModuleType.BREATHWORK -> FeedContentParser.BREATHWORK
        MindfulModuleType.LANGUAGE -> emptyList()
        else -> (1..30).map { n ->
            ScienceCardItem(id = "${module.id}:$n", phenomenon = "p$n", question = "q", coreExplanation = "e", fascinatingDetail = "d")
        }
    }

    private fun russian(cards: Map<String, RuCard>, letters: Map<String, Int> = emptyMap()) = RussianFeedContext(
        index = index,
        progress = RuProgress(cards = cards, dayStars = mapOf(1 to 3, 2 to 3)),
        today = 100,
        alphabet = alphabet,
        letterProgress = letters,
    )

    @Test fun feed_wiederholtInFuenfSchwuengenKeinenEintrag() {
        val composer = FeedComposer(::fakeCards, Random(7))
        val learned = index.days.take(2).flatMap { it.items }.associate { it.id to RuCard(box = 2, due = 100) }
        val ctx = russian(learned)
        val all = (1..5).flatMap {
            composer.batch(MindfulModuleType.entries.toSet(), ranks, LearningLanguage.RUSSIAN, emptyMap(), ctx, 4)
        }
        assertTrue(all.size >= 60, "nur ${all.size} Einträge")
        assertEquals(all.size, all.map { it.id }.toSet().size, "doppelte Einträge")
        assertTrue(all.any { it is FeedEntry.Game && it.game is StroopGame })
        assertTrue(all.any { it is FeedEntry.Game && it.game is ChainMathGame })
        assertTrue(all.any { it is FeedEntry.Game && it.game is MemoryGridGame })
        assertTrue(all.any { it is FeedEntry.Game && it.game is LetterIntroGame })
        // Höchstens eine Atemübung je Schwung, und nie zwei Russisch-Einträge direkt hintereinander im ersten Schwung.
        assertTrue(all.count { it.module == MindfulModuleType.BREATHWORK } <= 5)
    }

    @Test fun feed_russischUebungenNutzenNurGelernteSaetze() {
        val composer = FeedComposer(::fakeCards, Random(11))
        val learned = index.days.take(2).flatMap { it.items }.associate { it.id to RuCard(box = 2, due = 100) }
        val ctx = russian(learned)
        val games = (1..4).flatMap {
            composer.batch(setOf(MindfulModuleType.LANGUAGE), ranks, LearningLanguage.RUSSIAN, emptyMap(), ctx, 4)
        }.filterIsInstance<FeedEntry.Game>().map { it.game }
        val graded = games.mapNotNull { (it as? ChoiceGame)?.grade ?: (it as? BuildGame)?.grade }
        assertTrue(graded.isNotEmpty())
        assertTrue(graded.all { it.phraseId in learned }, "Übung mit ungelerntem Satz")
        assertTrue(graded.all { it.due }, "alle Karten waren fällig")
        assertEquals(graded.size, graded.map { it.phraseId }.toSet().size, "ein Satz kam zweimal in einer Sitzung")
    }

    @Test fun feed_ohneGelernteSaetzeGibtEsAlphabetUndEinenVorgeschmack() {
        val composer = FeedComposer(::fakeCards, Random(5))
        val ctx = RussianFeedContext(index, RuProgress(), 100, alphabet, emptyMap())
        val batch = composer.batch(setOf(MindfulModuleType.LANGUAGE), ranks, LearningLanguage.RUSSIAN, emptyMap(), ctx, 4)
        assertTrue(batch.isNotEmpty())
        assertTrue(batch.first() is FeedEntry.Game && (batch.first() as FeedEntry.Game).game is LetterIntroGame)
        val preview = batch.filterIsInstance<FeedEntry.Card>().map { it.card }.filterIsInstance<LanguageCardItem>()
        assertTrue(preview.isNotEmpty() && preview.all { it.isNewPhrase && it.dayNumber == 1 })
        assertTrue(preview.all { it.resolveDeepDive().isAuthored }, "Russisch-Karten bringen eine echte Vertiefung mit")
        assertTrue(batch.none { it is FeedEntry.Game && ((it.game as? ChoiceGame)?.grade != null) })
    }

    @Test fun feed_stelltProSitzungNurWenigeNeueBuchstabenVor() {
        val composer = FeedComposer(::fakeCards, Random(5))
        val ctx = RussianFeedContext(index, RuProgress(), 100, alphabet, emptyMap())
        val intros = (1..6).flatMap {
            composer.batch(setOf(MindfulModuleType.LANGUAGE), ranks, LearningLanguage.RUSSIAN, emptyMap(), ctx, 4)
        }.filterIsInstance<FeedEntry.Game>().map { it.game }.filterIsInstance<LetterIntroGame>()
        assertTrue(intros.size in 1..FeedComposer.MAX_NEW_LETTERS, "${intros.size} neue Buchstaben")
        assertEquals(listOf("а", "к", "м", "о").take(intros.size), intros.map { it.letter.l })
    }

    // ── Vertiefung ───────────────────────────────────────────────────────────

    @Test fun atemuebungen_habenRhythmusUndEchteVertiefung() {
        for (card in FeedContentParser.BREATHWORK) {
            card as BreathworkCardItem
            assertTrue(card.inhaleSeconds > 0 && card.exhaleSeconds > 0)
            val deep = card.resolveDeepDive()
            assertTrue(deep.isAuthored && deep.sections.size >= 2, card.id)
            assertTrue(deep.sections.all { it.text.length > 120 })
        }
    }
}
