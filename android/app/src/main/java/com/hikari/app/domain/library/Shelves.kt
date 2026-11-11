package com.hikari.app.domain.library

import com.hikari.app.data.api.dto.LibraryVideoDto

/** Regeln für die Reihen der Bibliothek — nach dem Vorbild von Netflix. */
object Shelves {
    /** So viele Titel zeigt eine Reihe; gibt es mehr, führt »Alle« zur vollständigen Übersicht. */
    const val SHELF_LIMIT = 10

    /** So viele Titel stehen höchstens in »Weiterschauen« — die zuletzt gesehenen. */
    const val CONTINUE_LIMIT = 10

    /** Was die Reihe zeigt. */
    fun <T> preview(items: List<T>): List<T> = items.take(SHELF_LIMIT)

    /** »Alle« gibt es nur, wenn die Reihe nicht schon alles zeigt. */
    fun hasMore(items: List<*>): Boolean = items.size > SHELF_LIMIT

    /**
     * »Weiterschauen«: ein Eintrag je Titel (eine Serie nur einmal, mit ihrer jüngsten Folge),
     * zuletzt gesehen zuerst, höchstens [CONTINUE_LIMIT]. Der Server liefert das schon so;
     * die Regel steht hier noch einmal, damit auch ein älterer Server keine fünf Folgen
     * derselben Serie nebeneinander stellt.
     */
    fun continueRow(videos: List<LibraryVideoDto>): List<LibraryVideoDto> {
        val seen = HashSet<String>()
        return videos
            .filter { seen.add(it.series_id?.let { id -> "s:$id" } ?: "v:${it.id}") }
            .take(CONTINUE_LIMIT)
    }

    /**
     * Zweite Zeile der Weiterschauen-Karte: Folge und Reststand — oder »Nächste Folge«,
     * wenn die vorige zu Ende gesehen ist.
     */
    fun continueDetail(episodeTag: String, remaining: String, upNext: Boolean): String =
        listOfNotNull(
            episodeTag.takeIf { it.isNotBlank() },
            if (upNext) "Nächste Folge" else "noch $remaining",
        ).joinToString(" · ")
}
