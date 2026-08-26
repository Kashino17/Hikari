package com.hikari.app.ui.imports

import com.hikari.app.data.api.dto.PendingImportDto

// ---- Aufbereitung für die Anzeige ---------------------------------------

internal fun PendingImportDto.displayTitle(): String {
    val ep = episode?.let { "Folge $it" }
    return when {
        !title.isNullOrBlank() -> title
        !seriesTitle.isNullOrBlank() && ep != null -> "$seriesTitle — $ep"
        !seriesTitle.isNullOrBlank() -> seriesTitle
        ep != null -> ep
        else -> pageUrl
    }
}

internal fun PendingImportDto.subtitle(): String {
    val parts = buildList {
        seriesTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
        season?.let { add("Staffel $it") }
        dubLanguage?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    }
    return if (parts.isEmpty()) pageUrl else parts.joinToString(" · ")
}

/**
 * Die Zeile unter dem Balken: Anteil, geladene Menge, Tempo und Restzeit —
 * so viel davon, wie gerade bekannt ist.
 */
internal fun PendingImportDto.progressLine(): String {
    if (status == "queued") return "Wartet auf Start"
    val parts = buildList {
        progress?.let { add("${(it * 100).toInt()} %") }
        add(
            formatBytes(downloadedBytes.toLong()) +
                (totalBytes?.let { " von ${formatBytes(it.toLong())}" } ?: ""),
        )
        fragmentCount?.let { total ->
            fragmentIndex?.let { idx -> add("Teil $idx/$total") }
        }
        speedBps?.takeIf { it > 0 }?.let { add("${formatBytes(it.toLong())}/s") }
        etaSeconds?.takeIf { it > 0 }?.let { add("noch ${formatDuration(it.toInt())}") }
    }
    return parts.joinToString(" · ")
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun formatDuration(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    seconds >= 60 -> "${seconds / 60} min"
    else -> "$seconds s"
}
