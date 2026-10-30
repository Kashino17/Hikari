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

    val podcast: RuCourse by lazy {
        context.assets.open("russian/podcast.json").bufferedReader().use { parseRuCourse(it.readText()) }
    }

    val podcastIndex: RuCourseIndex by lazy { RuCourseIndex(podcast) }

    fun phrase(id: String, settings: RuSettings): RuPhrase? =
        index.phrase(id, settings) ?: podcastIndex.phrase(id, settings)

    /** Vertiefungen (assets/russian/deepdive.json), nach Satz-ID; fehlt die Datei, bleibt es leer. */
    val deepdive: Map<String, RuDeepEntry> by lazy {
        runCatching {
            context.assets.open("russian/deepdive.json").bufferedReader().use {
                ruJson.decodeFromString(RuDeepdive.serializer(), it.readText())
            }.entries.associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    /** Grammatik-Muster (assets/russian/patterns.json). */
    val patterns: List<RuPattern> by lazy {
        runCatching {
            context.assets.open("russian/patterns.json").bufferedReader().use {
                ruJson.decodeFromString(RuPatterns.serializer(), it.readText()).patterns
            }
        }.getOrDefault(emptyList())
    }

    /** Themen-Tags fürs Satzbuch (assets/russian/phrasebook.json); fehlt die Datei, bleibt es leer. */
    val phrasebook: RuPhrasebookMeta by lazy {
        runCatching {
            context.assets.open("russian/phrasebook.json").bufferedReader().use {
                ruJson.decodeFromString(RuPhrasebookMeta.serializer(), it.readText())
            }
        }.getOrDefault(RuPhrasebookMeta())
    }
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
