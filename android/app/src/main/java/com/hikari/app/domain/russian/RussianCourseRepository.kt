package com.hikari.app.domain.russian

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lädt den Kurs einmalig aus den Assets — offline, ohne Server. */
@Singleton
class RussianCourseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val course: RuCourse by lazy {
        context.assets.open("russian/course.json").bufferedReader().use { parseRuCourse(it.readText()) }
    }

    val index: RuCourseIndex by lazy { RuCourseIndex(course) }
}

/** Nachschlage-Strukturen über dem Kurs (pure, testbar). */
class RuCourseIndex(val course: RuCourse) {
    val days: List<RuDay> = course.days.sortedBy { it.day }
    val itemById: Map<String, RuItem> = days.flatMap { it.items }.associateBy { it.id }
    val dayOfItem: Map<String, Int> = days.flatMap { d -> d.items.map { it.id to d.day } }.toMap()

    fun day(n: Int): RuDay? = days.firstOrNull { it.day == n }

    fun phrase(id: String, settings: RuSettings): RuPhrase? {
        val item = itemById[id] ?: return null
        return item.resolve(dayOfItem.getValue(id), settings.gender, settings.voice)
    }

    fun dayPhrases(day: RuDay, settings: RuSettings): List<RuPhrase> =
        day.items.map { it.resolve(day.day, settings.gender, settings.voice) }

    fun dialog(day: RuDay, settings: RuSettings): List<Pair<RuLine, RuPhrase>> =
        day.dialog.lines.map { it to it.resolve(day.day, settings.gender) }

    /** Alle Phrasen bis einschließlich [maxDay] — Pool für Ablenker und Spiele. */
    fun phrasesUpTo(maxDay: Int, settings: RuSettings): List<RuPhrase> =
        days.filter { it.day <= maxDay }.flatMap { dayPhrases(it, settings) }
}

/** Einstellungen, die bestimmen, welche Form/Stimme gespielt wird. */
data class RuSettings(
    val gender: RuGender = RuGender.MALE,
    /** Stimme für Vokabeln (Sätze mit Geschlechtsform folgen immer [gender]). */
    val voice: RuGender = RuGender.MALE,
)
