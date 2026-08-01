package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

// Bottom-Nav (ab v0.53.0): verschlankt auf 3 Tabs. News, Musik, Manga und
// Spiele sind keine Tabs mehr, sondern Bereiche im Profil-Hub — die Routen
// bleiben unverändert erreichbar. Tuning via Profil-Gear → Settings.
val hikariDestinations = listOf(
    NavDest("library", "Bibliothek", Icons.Default.GridView),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("profile", "Profil", Icons.Default.Person),
)

/** Sections ohne Bottom-Tab — erreichbar über den Profil-Hub und Deep-Links. */
val hubSectionRoutes = setOf("news", "music", "manga", "games")
