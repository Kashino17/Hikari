package com.hikari.app.ui.music

import java.util.Locale
import kotlin.math.round

/**
 * Zähler kompakt auf Deutsch: 850000 → "850 Tsd.", 4520000 → "4,5 Mio.",
 * 1200000000 → "1,2 Mrd.". Unter Tausend bleibt die Zahl unverändert.
 * Bewusst ohne Compose-Abhängigkeit, damit sie als reiner JVM-Test läuft.
 */
fun formatCountDE(value: Long): String {
    if (value < 1_000) return value.toString()
    return when {
        value < 1_000_000 -> formatScaled(value / 1_000.0, "Tsd.")
        value < 1_000_000_000 -> formatScaled(value / 1_000_000.0, "Mio.")
        else -> formatScaled(value / 1_000_000_000.0, "Mrd.")
    }
}

/** Abonnentenzahl für die Artist-Seite: "4,5 Mio. Abonnenten". */
fun formatSubscribersDE(count: Long): String = "${formatCountDE(count)} Abonnenten"

/** Aufrufzahl als Badge: "1,8 Mrd. Aufrufe". */
fun formatViewsDE(views: Long): String = "${formatCountDE(views)} Aufrufe"

private fun formatScaled(value: Double, suffix: String): String {
    val rounded = round(value * 10) / 10
    val text = if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        // Fest US-Locale formatieren und dann ins deutsche Komma drehen —
        // sonst hängt das Ergebnis vom Gerät ab.
        String.format(Locale.US, "%.1f", rounded).replace('.', ',')
    }
    return "$text $suffix"
}
