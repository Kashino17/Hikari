package com.hikari.app.domain.library

import com.hikari.app.data.api.dto.LibraryVideoDto

/** Wo es in einer Serie weitergeht — eine Regel für Serienseite und Smart-Downloads. */
object SeriesResume {

    private const val FINISHED_FRACTION = 0.95f

    fun ordered(videos: List<LibraryVideoDto>): List<LibraryVideoDto> =
        videos.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))

    fun isFinished(video: LibraryVideoDto): Boolean {
        val progress = video.progress_seconds ?: return false
        return video.duration_seconds > 0 && progress >= video.duration_seconds * FINISHED_FRACTION
    }

    fun isStarted(video: LibraryVideoDto): Boolean = (video.progress_seconds ?: 0f) > 0f

    /**
     * Die Folge, bei der es weitergeht:
     *  - die zuletzt geschaute, solange sie nicht zu Ende ist,
     *  - sonst die Folge danach (auch über die Staffelgrenze),
     *  - ohne jeden Fortschritt die erste Folge.
     * Ist die zuletzt geschaute die allerletzte und fertig, bleibt es bei ihr.
     */
    fun target(videos: List<LibraryVideoDto>): LibraryVideoDto? {
        val list = ordered(videos)
        if (list.isEmpty()) return null
        val touched = list.filter(::isStarted)
        // Ältere Server liefern keinen Zeitstempel (0) — dann zählt die Reihenfolge der Folgen.
        val last = touched.maxWithOrNull(
            compareBy<LibraryVideoDto> { it.progress_updated_at ?: 0L }.thenBy { list.indexOf(it) },
        ) ?: return list.first()
        if (!isFinished(last)) return last
        return list.getOrNull(list.indexOf(last) + 1) ?: last
    }

    /**
     * Die Folgen, die Smart-Downloads vorhalten sollen: ab der Folge, bei der
     * es weitergeht, die nächsten [count] — nur solche, deren Datei auf dem
     * Server liegt. Ohne angefangene Folge gibt es nichts vorzuhalten.
     */
    fun upcoming(videos: List<LibraryVideoDto>, count: Int): List<LibraryVideoDto> {
        val list = ordered(videos)
        if (list.none(::isStarted)) return emptyList()
        val start = target(list) ?: return emptyList()
        return list.drop(list.indexOf(start))
            .filter { it.downloaded != 0 && !isFinished(it) }
            .take(count)
    }
}
