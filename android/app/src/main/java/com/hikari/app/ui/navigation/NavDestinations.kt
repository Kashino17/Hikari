package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

// Bottom-Nav (v0.25.1): Bibliothek (leftmost = start) → Feed → News → Music → Manga → Games → Profil
// (rightmost). Tuning erreichbar via Profil-Gear → Settings → "Filter & AI".
val hikariDestinations = listOf(
    NavDest("library", "Bibliothek", Icons.Default.GridView),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("news", "News", Icons.AutoMirrored.Filled.Article),
    NavDest("music", "Music", Icons.Default.MusicNote),
    NavDest("manga", "Manga", Icons.Default.MenuBook),
    NavDest("games", "Spiele", Icons.Default.Star),
    NavDest("profile", "Profil", Icons.Default.Person),
)
