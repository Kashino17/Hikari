package com.hikari.app.ui.common

import android.content.Context
import android.content.Intent

/** Echte YouTube-Video-IDs: genau 11 Zeichen aus A–Z, a–z, 0–9, "-" und "_". */
private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Teilbarer Link zu einem Inhalt. Songs und YouTube-Videos bekommen den
 * kurzen youtu.be-Link (öffnet bei jedem die YouTube- bzw. YT-Music-App);
 * importierte Folgen ohne YouTube-ID ihre Quellseite, sofern bekannt.
 * null heißt: nichts Teilbares — die Oberfläche blendet den Knopf aus.
 */
fun shareUrlFor(videoId: String, sourceUrl: String? = null): String? = when {
    YOUTUBE_ID.matches(videoId) -> "https://youtu.be/$videoId"
    sourceUrl != null && (sourceUrl.startsWith("https://") || sourceUrl.startsWith("http://")) -> sourceUrl
    else -> null
}

/** Öffnet das System-Teilen-Menü mit Titel und Link. */
fun shareLink(context: Context, title: String, url: String) {
    val text = if (title.isBlank()) url else "$title\n$url"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Teilen über…")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
