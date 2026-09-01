package com.hikari.app.ui.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hikari.app.ui.components.FallbackArtwork

/**
 * Gestaltete Ersatz-Karte für Serien ohne Cover. Delegiert auf das gemeinsame
 * [FallbackArtwork] (cinematic Gradient + großer Titel), damit Manga-Cover wie
 * alle anderen Bild-Fallbacks aussehen. Dient zugleich als Lade- und
 * Fehler-Unterlage unter dem eigentlichen Cover.
 */
@Composable
fun MangaCoverFallback(
    title: String,
    modifier: Modifier = Modifier,
) {
    FallbackArtwork(title = title, modifier = modifier)
}
