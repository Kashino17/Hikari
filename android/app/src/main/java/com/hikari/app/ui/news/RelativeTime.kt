package com.hikari.app.ui.news

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Relative Zeitangabe für News-Beiträge aus dem ISO-8601 publishedAt:
 * "gerade eben", "vor 5 Min.", "vor 2 Std.", "vor 3 T.", danach Datum.
 * Unlesbare Zeitstempel fallen auf einen leeren String zurück — die UI
 * blendet die Zeit dann einfach aus statt Müll zu zeigen.
 */
fun formatRelativeTime(iso: String, now: Instant = Instant.now()): String {
    val published = parseInstant(iso) ?: return ""
    if (published.isAfter(now)) return "gerade eben"
    val minutes = ChronoUnit.MINUTES.between(published, now)
    if (minutes < 1) return "gerade eben"
    if (minutes < 60) return "vor $minutes Min."
    val hours = ChronoUnit.HOURS.between(published, now)
    if (hours < 24) return "vor $hours Std."
    val days = ChronoUnit.DAYS.between(published, now)
    if (days < 7) return "vor $days T."
    return published.atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun parseInstant(iso: String): Instant? =
    runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
