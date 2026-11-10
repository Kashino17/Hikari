package com.hikari.app.domain.profile

import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuToday
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Was die Russisch-Kachel im Profil zeigt. */
data class RussianTile(
    /** Große Zeile: der nächste Schritt, z. B. »Lektion 12«. */
    val headline: String,
    /** Kleine Zeile darunter: worum es geht. */
    val detail: String,
    /** Anteil abgeschlossener Lektionen des Sprechkurses, 0..1. */
    val progress: Float,
    /** Heute ist alles erledigt — die Kachel lädt dann nicht mehr zum Start ein. */
    val done: Boolean,
)

/** Eine Reihe aus dem Manga-Regal; [cover] ist die fertige Bildadresse. */
data class MangaShelfEntry(
    val title: String,
    val cover: String?,
    val totalChapters: Int = 0,
)

/** Eine angefangene Reihe mit der Stelle, an der man stehen geblieben ist. */
data class MangaReading(
    val title: String,
    val cover: String?,
    val chapterId: String,
    val page: Int,
)

/** Was die Manga-Kachel im Profil zeigt. */
data class MangaTile(
    val title: String,
    val detail: String,
    /** Bis zu drei Titelbilder für den Fächer; das der genannten Reihe steht vorn. */
    val covers: List<String>,
)

/** Reine Logik hinter den Bereichs-Kacheln im Profil. */
object AreaTiles {

    /**
     * Der nächste Schritt im Russisch-Bereich, in dieser Reihenfolge:
     * Lektion des Sprechkurses → fällige Sätze wiederholen → Podcast-Folge → geschafft.
     *
     * Gezählt werden Lektionen, nicht Kalendertage des Jahreskurses (dort liegen
     * Gesprächstage dazwischen) — deshalb »Lektion N«, nie »Tag N«.
     */
    fun russian(
        p: RuProgress,
        today: Long,
        courseDays: Int,
        podcastDays: Int,
        lessonTitle: (Int) -> String?,
    ): RussianTile {
        val (course, review, podcast) = RuToday.plan(p, today, courseDays, podcastDays)
        val progress = if (courseDays > 0) (p.dayStars.size.toFloat() / courseDays).coerceIn(0f, 1f) else 0f
        val courseNote = if (course.allDone) "Sprechkurs abgeschlossen" else "Lektion ${course.day} ist geschafft"

        return when {
            !course.done -> RussianTile(
                headline = "Lektion ${course.day}",
                detail = lessonTitle(course.day)?.takeIf { it.isNotBlank() } ?: "Sprechkurs",
                progress = progress,
                done = false,
            )
            !review.done -> RussianTile(
                headline = if (review.count == 1) "1 Satz wiederholen" else "${review.count} Sätze wiederholen",
                detail = courseNote,
                progress = progress,
                done = false,
            )
            !podcast.done -> RussianTile(
                headline = "Podcast, Folge ${podcast.day}",
                detail = if (podcast.practice) "Übungen zur Folge" else courseNote,
                progress = progress,
                done = false,
            )
            else -> RussianTile(
                headline = "Heute geschafft",
                detail = if (course.allDone) "Alles erledigt" else "Morgen geht es weiter",
                progress = progress,
                done = true,
            )
        }
    }

    /**
     * Manga-Kachel: die zuletzt gelesene Reihe mit ihrem Kapitel — und solange noch nichts
     * angefangen ist, die erste Reihe aus dem Regal. So zeigt die Kachel Titelbilder, sobald es
     * welche gibt, nicht erst nach dem ersten Lesen. Leeres Regal → null (Kachel bleibt beim Namen).
     */
    fun manga(reading: List<MangaReading>, shelf: List<MangaShelfEntry>): MangaTile? {
        val current = reading.firstOrNull()
        val featured = shelf.firstOrNull { it.cover != null } ?: shelf.firstOrNull()
        val covers = (
            listOf(current?.cover ?: featured?.cover.takeIf { current == null }) +
                reading.map { it.cover } +
                shelf.map { it.cover }
            ).filterNotNull().distinct().take(3)
        return when {
            current != null -> MangaTile(
                title = current.title,
                detail = chapterNumber(current.chapterId)?.let { "Kapitel $it" } ?: "Seite ${current.page}",
                covers = covers,
            )
            featured != null -> MangaTile(
                title = featured.title,
                detail = if (featured.totalChapters > 0) "${featured.totalChapters} Kapitel" else "Noch nicht angefangen",
                covers = covers,
            )
            else -> null
        }
    }

    /** Kapitelnummer aus der Kennung »quelle:reihe:nummer«; ganze Kapitel ohne Nachkommastelle. */
    fun chapterNumber(chapterId: String): String? {
        val raw = chapterId.substringAfterLast(':', "")
        val number = raw.toDoubleOrNull()?.takeIf { it >= 0 && raw.matches(Regex("""\d+(\.\d+)?""")) } ?: return null
        return if (number % 1.0 == 0.0) number.toLong().toString() else raw
    }

    /** Lernzeit von heute als kurze Angabe; unter einer Minute zählt noch nicht. */
    fun studyMinutes(seconds: Int): String? {
        val minutes = seconds / 60
        return when {
            minutes < 1 -> null
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} Std"
            else -> "${minutes / 60} Std ${minutes % 60} min"
        }
    }

    private val longDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    /**
     * Von wann der Tagesbericht ist — »Heute«, »Gestern« oder der Wochentag mit Datum.
     * [publishedAt] ist der ISO-Zeitstempel des Backends; unlesbar → null (Zeile entfällt).
     */
    fun newsDay(publishedAt: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? {
        val day = runCatching { OffsetDateTime.parse(publishedAt).atZoneSameInstant(zone).toLocalDate() }
            .recoverCatching { Instant.parse(publishedAt).atZone(zone).toLocalDate() }
            .recoverCatching { LocalDate.parse(publishedAt.take(10)) }
            .getOrNull() ?: return null
        return when (day) {
            today -> "Heute"
            today.minusDays(1) -> "Gestern"
            else -> day.format(longDate)
        }
    }

    /** Anzeigename eines Spiels zur Kennung aus »last_played_game« (ui/games/GamesScreen). */
    fun gameTitle(id: String?): String? = when (id) {
        "blockblast" -> "Block Blast"
        "fruitmerge" -> "Fruit Merge"
        "spaceshooter" -> "Sky Strike"
        "fruithole" -> "Hungry Hole"
        "colorsort" -> "Color Sort"
        "2048" -> "2048"
        "sudoku" -> "Sudoku"
        "snake" -> "Snake"
        "tictactoe" -> "Tic-Tac-Toe"
        else -> null
    }

    const val GAME_COUNT = 9
}
