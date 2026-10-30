package com.hikari.app.domain.russian

import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class RussianPodcastCourseTest {
    private val course = parseRuCourse(File("src/main/assets/russian/podcast.json").readText())
    private val index = RuCourseIndex(course)
    private val settings = RuSettings()

    @Test
    fun hundertLektionenSindVollstaendig() {
        assertEquals((1..100).toList(), index.days.map { it.day })
        val ids = index.days.flatMap { it.items }.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        val mainIds = RuCourseIndex(parseRuCourse(File("src/main/assets/russian/course.json").readText())).itemById.keys
        assertTrue(ids.none { it in mainIds })
        index.days.forEach { day ->
            val media = assertNotNull(day.media, "Lektion ${day.day}")
            assertTrue(media.videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
            assertTrue(media.sourceUrl.startsWith("https://www.russlandjournal.de/"))
            assertTrue(media.summary.isNotBlank())
            assertTrue(media.notes.isNotEmpty(), "Lektion ${day.day}: Notizen fehlen")
            assertTrue(day.items.size >= 6, "Lektion ${day.day}: zu wenige Lernphrasen")
            day.items.forEach { item ->
                assertTrue(item.id.startsWith("rj${day.day.toString().padStart(3, '0')}_"))
                assertTrue(item.ru.any { it in 'А'..'Я' || it in 'а'..'я' || it == 'Ё' || it == 'ё' })
                assertFalse(item.ru.any { it in 'A'..'Z' || it in 'a'..'z' }, "${item.id}: Latein im Russischen")
                assertTrue(item.de.isNotBlank())
                assertFalse(item.de.any { it in 'А'..'Я' || it in 'а'..'я' || it == 'Ё' || it == 'ё' })
            }
        }
        assertEquals(100, index.days.mapNotNull { it.media?.videoId }.distinct().size)
    }

    @Test
    fun allePodcastLektionenErzeugenGueltigeUebungen() {
        index.days.forEach { day ->
            repeat(3) { seed ->
                val steps = RuLessonBuilder.podcastLesson(index, day.day, settings, speechAvailable = true, rng = Random(seed))
                assertEquals(day.items.size, steps.count { it is RuExercise.Intro }, "Lektion ${day.day}")
                assertTrue(steps.any { it is RuExercise.ListenChoose || it is RuExercise.MeaningChoose })
                steps.forEach { exercise ->
                    when (exercise) {
                        is RuExercise.ListenChoose -> checkOptions(exercise.phrase, exercise.options)
                        is RuExercise.MeaningChoose -> checkOptions(exercise.phrase, exercise.options)
                        is RuExercise.Build -> {
                            assertTrue(exercise.answer.size >= 3)
                            assertTrue(RuAnswerCheck.buildCorrect(exercise, exercise.answer))
                        }
                        is RuExercise.StressTap -> assertTrue(exercise.stressedIndex in exercise.syllables.indices)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun checkOptions(target: RuPhrase, options: List<RuPhrase>) {
        assertTrue(target in options)
        assertTrue(options.size >= 3, "Zu wenige Ablenker für ${target.id}")
        assertEquals(options.size, options.map { it.id }.distinct().size)
        assertEquals(options.size, options.map { RuLessonBuilder.norm(it.de) }.distinct().size)
        options.filter { it != target }.forEach {
            assertFalse(RuLessonBuilder.confusable(it.de, target.de), "Mehrdeutig: ${it.de} / ${target.de}")
        }
    }
}
