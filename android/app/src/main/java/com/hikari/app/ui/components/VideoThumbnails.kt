package com.hikari.app.ui.components

import com.hikari.app.data.api.dto.LibraryVideoDto

/** Siehe Doku an [resumeAwareThumbnail]. */
private const val MIN_RESUME_PROGRESS_SECONDS = 30f

/**
 * Thumbnail-Auswahl im Netflix-Stil: Ist das Video angefangen, aber noch nicht
 * (zu ~95 %) fertig geschaut, liefert das Backend über
 * `/videos/{id}/frame?at={sekunden}` genau den Frame an der Stopp-Position.
 * Sonst gilt weiterhin das normale `thumbnail_url`.
 *
 * Unter [MIN_RESUME_PROGRESS_SECONDS] wird bewusst kein Resume-Frame gezeigt:
 * Die ersten Sekunden sind bei vielen Videos Intro/Fade-in (schwarzes Bild
 * mit Logo) und sehen in der Übersicht nach "kaputt" aus.
 *
 * Der Pfad ist absichtlich relativ — der zentrale Coil-Mapper in HikariApp
 * prefixt relative Pfade automatisch mit der eingestellten Backend-URL.
 */
fun resumeAwareThumbnail(
    videoId: String,
    thumbnailUrl: String?,
    progressSeconds: Float?,
    durationSeconds: Int,
): Any? {
    val progress = progressSeconds ?: return thumbnailUrl
    val inProgress = progress >= MIN_RESUME_PROGRESS_SECONDS &&
        durationSeconds > 0 &&
        progress < durationSeconds.toFloat() * 0.95f
    return if (inProgress) "/videos/$videoId/frame?at=${progress.toInt()}" else thumbnailUrl
}

/** Bequeme Variante direkt auf dem Bibliotheks-DTO. */
fun LibraryVideoDto.resumeAwareThumbnail(): Any? =
    resumeAwareThumbnail(id, thumbnail_url, progress_seconds, duration_seconds)
