package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

// Bottom-Nav (ab v0.53.1): Bibliothek · Feed · Musik · Profil. Musik bleibt
// als täglicher Treiber ein Tab; Tagesbericht, Manga und Spiele sind
// Bereiche im Profil-Hub. Tuning via Profil-Gear → Settings.
val hikariDestinations = listOf(
    NavDest("library", "Bibliothek", Icons.Default.GridView),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("music", "Musik", Icons.Default.MusicNote),
    NavDest("profile", "Profil", Icons.Default.Person),
)

/** Sections ohne Bottom-Tab — erreichbar über den Profil-Hub und Deep-Links. */
val hubSectionRoutes = setOf("news", "manga", "games")
