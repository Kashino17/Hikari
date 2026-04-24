package com.hikari.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDest(val route: String, val label: String, val icon: ImageVector)

val hikariDestinations = listOf(
    NavDest("feed", "Feed", Icons.Default.PlayArrow),
    NavDest("saved", "Saved", Icons.Default.Favorite),
    NavDest("channels", "Channels", Icons.AutoMirrored.Filled.List),
    NavDest("settings", "Settings", Icons.Default.Settings),
    NavDest("rejected", "Rejected", Icons.Default.Close),
)
