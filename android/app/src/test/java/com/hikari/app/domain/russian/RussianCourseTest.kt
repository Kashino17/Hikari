package com.hikari.app.domain.russian

import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Prüft den echten Kurs aus den Assets — Inhalte, Audios, Übungen. */
class RussianCourseTest {

    private val assets = File("src/main/assets/russian")
    private val course = parseRuCourse(File(assets, "course.json").readText())
    private val index = RuCourseIndex(course)
    private val male = RuSettings(RuGender.MALE, RuGender.MALE)
    private val female = RuSettings(RuGender.FEMALE, RuGender.FEMALE)
    private val audioFiles = File(assets, "audio").list()!!.toSet()

    @Test
    fun vierzehnTageMitInhalt() {
        assertEquals((1..14).toList(), index.days.map { it.day })
        for (d in index.days) {
            assertTrue(d.items.size in 12..20, "Tag ${d.day}: ${d.items.size} Items")
            assertTrue(d.dialog.lines.count { it.isUser } >= 3, "Tag ${d.day}: zu wenige eigene Zeilen")
            assertTrue(d.sound.examples.isNotEmpty())
        }
        assertTrue(index.itemById.size >= 200)
    }

    @Test
    fun jedesAudioExistiertInAllenVarianten() {
        val missing = mutableListOf<String>()
        for (s in listOf(male, female, RuSettings(RuGender.MALE, RuGender.FEMALE), RuSettings(RuGender.FEMALE, RuGender.MALE))) {
            for (d in index.days) {
                val names = index.dayPhrases(d, s).map { it.audio } + index.dialog(d, s).map { it.second.audio }
                for (n in names) {
                    if ("$n.ogg" !in audioFiles) missing += n
                    if ("${n}_slow.ogg" !in audioFiles) missing += "${n}_slow"
                }
            }
        }
        assertTrue(missing.isEmpty(), "Fehlende Audios: ${missing.distinct().take(20)}")
    }

    @Test
    fun audiosPassenZumAktuellenText() {
        // Das Manifest hält fest, welcher Text in jeder Datei gesprochen wird.
        val manifest = ruJson.parseToJsonElement(File(assets, "audio/manifest.json").readText()).jsonObject
        val stale = mutableListOf<String>()
        for (s in listOf(male, female)) {
            for (d in index.days) {
                val phrases = index.dayPhrases(d, s) + index.dialog(d, s).map { it.second }
                for (p in phrases) {
                    val entry = manifest[p.audio]?.jsonObject
                    assertNotNull(entry, "kein Manifest-Eintrag für ${p.audio}")
                    if (entry["text"]!!.jsonPrimitive.content != p.ru) stale += "${p.audio}: ${p.ru}"
                }
            }
        }
        assertTrue(stale.isEmpty(), "Audio veraltet — tools/russian_audio/generate.py neu laufen lassen: $stale")
    }

    @Test
    fun weiblicheFormenWerdenGewaehlt() {
        val tired = index.phrase("d03_ya_ustal", female)!!
        assertEquals("Я уст+ала.", tired.ru)
        assertEquals("d03_ya_ustal_f", tired.audio)
        assertEquals("Я уст+ал.", index.phrase("d03_ya_ustal", male)!!.ru)
        // Frage der Partnerin an eine Frau
        val line = index.dialog(index.day(7)!!, female).first { it.first.id == "d07_dlg_7" }.second
        assertEquals("А ты з+амужем?", line.ru)
        assertTrue(line.audio.endsWith("_p"))
    }

    @Test
    fun umschriftFuerAllesVorhandenUndBetont() {
        val out = StringBuilder()
        for (d in index.days) {
            val phrases = index.dayPhrases(d, male) + index.dialog(d, male).map { it.second } +
                index.dayPhrases(d, female).filter { it.ru != index.phrase(it.id, male)!!.ru }
            for (p in phrases) {
                val spans = RussianPhonetics.transliterate(p.ru)
                val flat = RussianPhonetics.transliterateFlat(p.ru)
                val multi = RuLessonBuilder.words(p.ru).any { w -> w.count { it.lowercaseChar() in "аеёиоуыэюя" } >= 2 }
                if (multi) assertTrue(spans.any { it.stressed }, "keine Betonung in ${p.ru}")
                assertFalse(flat.any { it in 'а'..'я' || it in 'А'..'Я' }, "Kyrillisch in Umschrift: $flat")
                out.append(p.id).append('\t').append(p.display).append('\t').append(flat).append('\n')
            }
        }
        File("build/russian_translit.tsv").writeText(out.toString())
    }

    @Test
    fun lektionenSindFuerAlleTageUndFormenBaubar() {
        for (s in listOf(male, female)) {
            for (d in index.days) {
                repeat(5) { seed ->
                    val steps = RuLessonBuilder.lesson(index, d.day, s, speechAvailable = seed % 2 == 0, rng = Random(seed))
                    assertTrue(steps.first() is RuExercise.SoundTip)
                    assertTrue(steps.last() is RuExercise.Dialog)
                    assertEquals(d.items.size, steps.count { it is RuExercise.Intro })
                    steps.forEach { checkExercise(it, "Tag ${d.day}/$seed") }
                }
            }
        }
    }

    @Test
    fun wiederholungBautAusFaelligenKarten() {
        val due = index.days.take(5).flatMap { it.items }.map { it.id }
        val steps = RuLessonBuilder.review(index, due, 5, male, speechAvailable = true, rng = Random(3))
        assertEquals(20, steps.size)
        steps.forEach { checkExercise(it, "Review") }
    }

    private fun checkExercise(ex: RuExercise, where: String) {
        when (ex) {
            is RuExercise.ListenChoose -> checkOptions(ex.phrase, ex.options, where)
            is RuExercise.MeaningChoose -> checkOptions(ex.phrase, ex.options, where)
            is RuExercise.StressTap -> {
                assertTrue(ex.stressedIndex in ex.syllables.indices, "$where: ${ex.word}")
                assertTrue(ex.syllables.size >= 2)
                assertEquals(ex.word.replace("-", ""), ex.syllables.joinToString("").replace("-", ""), where)
            }
            is RuExercise.Build -> {
                assertTrue(ex.answer.size >= 3, where)
                assertTrue(ex.tiles.none { it.first().isUpperCase() && RussianPhonetics.plain(it).lowercase() !in setOf("кадир", "аня", "германии", "германию", "россии", "москве", "сергей", "телеграм") }, "$where: Großbuchstabe verrät Satzanfang ${ex.tiles}")
                val pool = ex.tiles.toMutableList()
                for (w in ex.answer) assertTrue(pool.remove(w), "$where: Kachel fehlt $w")
                assertTrue(RuAnswerCheck.buildCorrect(ex, ex.answer))
                assertFalse(RuAnswerCheck.buildCorrect(ex, ex.answer.reversed()) && ex.answer.distinct().size > 1 && ex.answer != ex.answer.reversed())
            }
            is RuExercise.Reply -> {
                assertTrue(ex.options.count { it.id in ex.correctIds } == 1, "$where: genau eine passende Antwort erwartet (${ex.question.ru})")
                assertTrue(ex.options.size >= 3)
                assertEquals(ex.options.size, ex.options.map { it.id }.distinct().size)
            }
            is RuExercise.Dialog -> assertTrue(ex.lines.isNotEmpty())
            is RuExercise.Intro, is RuExercise.SoundTip, is RuExercise.Speak -> Unit
        }
    }

    private fun checkOptions(target: RuPhrase, options: List<RuPhrase>, where: String) {
        assertTrue(target in options, "$where: Lösung fehlt")
        assertTrue(options.size >= 3, "$where: nur ${options.size} Optionen für ${target.ru}")
        assertEquals(options.size, options.map { it.de }.distinct().size, "$where: doppelte Bedeutungen")
        assertEquals(options.size, options.map { it.plain }.distinct().size, "$where: doppelte Sätze")
        for (o in options) if (o != target) {
            assertFalse(RuLessonBuilder.confusable(o.de, target.de), "$where: mehrdeutig '${o.de}' vs '${target.de}'")
        }
    }

    @Test
    fun mehrdeutigeBedeutungenWerdenErkannt() {
        assertTrue(RuLessonBuilder.confusable("Guten Tag!", "Guten Tag! / Hallo! (höflich)"))
        assertTrue(RuLessonBuilder.confusable("Wie geht's?", "Wie geht's dir? (vertraut)"))
        assertFalse(RuLessonBuilder.confusable("Mich auch. / Ebenfalls.", "Ich auch!"))
        assertFalse(RuLessonBuilder.confusable("Danke!", "Bitte! / Gern geschehen!"))
    }

    @Test
    fun silbenFuerBetonungsuebung() {
        assertEquals(listOf("спа", "си", "бо") to 1, RuLessonBuilder.syllables("спас+ибо"))
        assertEquals(listOf("здра", "вствуй", "те") to 0, RuLessonBuilder.syllables("Здр+авствуйте"))
        assertEquals(listOf("всё") to 0, RuLessonBuilder.syllables("всё"))
    }
}

class RussianSrsTest {

    @Test
    fun richtigSteigtFalschFaelltZurueck() {
        val c = RuSrs.introduce(null, 100)
        assertEquals(1, c.box)
        assertEquals(101, c.due)
        val up = RuSrs.grade(c, true, 101)
        assertEquals(2, up.box)
        assertEquals(103, up.due)
        val down = RuSrs.grade(up.copy(box = 5), false, 110)
        assertEquals(1, down.box)
        assertEquals(110, down.due)
        assertEquals(1, down.wrong)
        assertEquals(RuSrs.MAX_BOX, RuSrs.grade(c.copy(box = 6), true, 0).box)
    }

    @Test
    fun faelligSortiertNachDatum() {
        val cards = mapOf("a" to RuCard(3, 10), "b" to RuCard(1, 5), "c" to RuCard(2, 50))
        assertEquals(listOf("b", "a"), RuSrs.dueIds(cards, 10))
    }

    @Test
    fun serieUndLektionAbschluss() {
        var p = RuProgress(gender = RuGender.MALE)
        val result = RuSessionResult(
            xp = 50,
            graded = mapOf("x" to true, "y" to false),
            introduced = setOf("x", "y"),
            completedDay = 1,
            stars = 2,
        )
        p = p.apply(result, 200)
        assertEquals(1, p.streak)
        assertEquals(mapOf(1 to 2), p.dayStars)
        assertEquals(201, p.cards["x"]!!.due) // richtig → morgen
        assertEquals(200, p.cards["y"]!!.due) // falsch → heute nochmal
        assertEquals(2, p.unlockedDay(14))
        assertEquals(50, p.xpOn(200))
        p = p.apply(RuSessionResult(10, emptyMap()), 201)
        assertEquals(2, p.streak)
        assertEquals(2, RuSrs.visibleStreak(p, 202))
        assertEquals(0, RuSrs.visibleStreak(p, 204))
        p = p.apply(RuSessionResult(10, emptyMap()), 205)
        assertEquals(1, p.streak)
        assertEquals(2, p.bestStreak)
        // Mehr Sterne bleiben, weniger überschreiben nicht
        p = p.apply(RuSessionResult(5, emptyMap(), completedDay = 1, stars = 1), 205)
        assertEquals(2, p.dayStars[1])
    }

    @Test
    fun sterne() {
        assertEquals(3, RuSrs.stars(9, 10))
        assertEquals(2, RuSrs.stars(7, 10))
        assertEquals(1, RuSrs.stars(3, 10))
    }
}

class RussianAnswerCheckTest {

    @Test
    fun spracherkennungToleriertKleinigkeiten() {
        val ok = RuAnswerCheck.speech("Прив+ет! Как дел+а?", listOf("привет как дела"))
        assertTrue(ok.passed)
        assertEquals(1.0, ok.score)
        // ё/е und Großschreibung egal
        assertTrue(RuAnswerCheck.speech("Всё хорош+о.", listOf("Все хорошо")).passed)
        // falscher Satz
        assertFalse(RuAnswerCheck.speech("Спас+ибо!", listOf("пожалуйста")).passed)
        // beste Hypothese zählt
        assertTrue(RuAnswerCheck.speech("Я не зн+аю.", listOf("я не знал", "я не знаю")).passed)
        // leer
        assertFalse(RuAnswerCheck.speech("Да.", emptyList()).passed)
    }

    @Test
    fun wortweiseMarkierung() {
        val s = RuAnswerCheck.speech("Я уч+у р+усский яз+ык.", listOf("я учу английский язык"))
        assertEquals(listOf(true, true, false, true), s.words.map { it.second })
        // 75 % Wörter, aber das Inhaltswort ist falsch → nicht bestanden
        assertFalse(s.passed)
        // Verneinung vergessen → nicht bestanden
        assertFalse(RuAnswerCheck.speech("Я не поним+аю.", listOf("я понимаю")).passed)
        // kurzes Füllwort verschluckt → trotzdem bestanden
        assertTrue(RuAnswerCheck.speech("А у теб+я как дел+а?", listOf("у тебя как дела")).passed)
    }
}
