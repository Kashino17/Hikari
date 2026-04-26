package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

val hikariDestinations = listOf(
    NavDest("profile", "Profil", Icons.Default.Person),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("manga", "Manga", Icons.Default.MenuBook),
    NavDest("tuning", "Tuning", Icons.Default.Settings),
)
