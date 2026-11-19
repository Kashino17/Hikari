package com.hikari.app.domain.ebooks

import kotlinx.serialization.Serializable

/**
 * Lesestand eines Buchs. Dieselben Regeln wie auf dem Server (backend/src/ebooks/state-merge.ts),
 * geprüft mit denselben Testfällen (test/resources/ebooks/merge-cases.json).
 */
@Serializable
data class Stamped<T>(val value: T, val at: Long)

@Serializable
data class ReadingPosition(val cfi: String = "", val percent: Double = 0.0, val page: Int? = null)

@Serializable
data class Mark(
    val id: String,
    val cfi: String,
    val label: String? = null,
    val text: String? = null,
    val color: String? = null,
    val at: Long,
    val deleted: Boolean? = null,
)

@Serializable
data class BookState(
    val position: Stamped<ReadingPosition>? = null,
    val saved: Stamped<Boolean>? = null,
    val finished: Stamped<Boolean>? = null,
    val bookmarks: List<Mark> = emptyList(),
    val highlights: List<Mark> = emptyList(),
    val lastOpenedAt: Long? = null,
) {
    val liveBookmarks: List<Mark> get() = bookmarks.filter { it.deleted != true }
    val liveHighlights: List<Mark> get() = highlights.filter { it.deleted != true }
}

/**
 * Markiert eine Stelle. Ist sie schon markiert (auch gelöscht), wird dieselbe Markierung umgefärbt
 * statt eine zweite darüberzulegen — sonst bliebe die alte Farbe sichtbar.
 */
fun BookState.withHighlight(cfi: String, text: String, color: String, now: Long, newId: String): BookState {
    val same = highlights.firstOrNull { it.cfi == cfi && it.deleted != true } ?: highlights.firstOrNull { it.cfi == cfi }
    return if (same == null) {
        copy(highlights = highlights + Mark(id = newId, cfi = cfi, text = text, color = color, at = now))
    } else {
        copy(highlights = highlights.map { if (it.id == same.id) it.copy(color = color, text = text, at = now, deleted = null) else it })
    }
}

/** Bei gleicher Zeit gewinnt b (die Seite, die gerade hereinkommt). */
private fun <T> newer(a: Stamped<T>?, b: Stamped<T>?): Stamped<T>? = when {
    a == null -> b
    b == null -> a
    b.at >= a.at -> b
    else -> a
}

private fun mergeMarks(a: List<Mark>, b: List<Mark>): List<Mark> {
    val byId = LinkedHashMap<String, Mark>()
    for (m in a) byId[m.id] = m
    for (m in b) {
        val prev = byId[m.id]
        byId[m.id] = if (prev == null || m.at >= prev.at) m else prev
    }
    return byId.values.sortedWith(compareBy<Mark> { it.at }.thenBy { it.id })
}

fun mergeState(a: BookState, b: BookState): BookState {
    val opened = maxOf(a.lastOpenedAt ?: -1, b.lastOpenedAt ?: -1)
    return BookState(
        position = newer(a.position, b.position),
        saved = newer(a.saved, b.saved),
        finished = newer(a.finished, b.finished),
        bookmarks = mergeMarks(a.bookmarks, b.bookmarks),
        highlights = mergeMarks(a.highlights, b.highlights),
        lastOpenedAt = if (opened >= 0) opened else null,
    )
}
