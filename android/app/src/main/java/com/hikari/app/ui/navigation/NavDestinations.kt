package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

// Bottom-Nav: Feed → Manga → Profil (rightmost). Tuning lebt ab v0.25.0 als
// Sub-Page in den Settings — Gear-Icon im Profil → "Filter & AI" → Tuning.
val hikariDestinations = listOf(
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("manga", "Manga", Icons.Default.MenuBook),
    NavDest("profile", "Profil", Icons.Default.Person),
)
