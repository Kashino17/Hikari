package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

val hikariDestinations = listOf(
    NavDest("library", "Bibliothek", Icons.Default.GridView),
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("manga", "Manga", Icons.Default.MenuBook),
    NavDest("channels", "Kanäle", Icons.AutoMirrored.Filled.List),
    NavDest("tuning", "Tuning", Icons.Default.Settings),
)
