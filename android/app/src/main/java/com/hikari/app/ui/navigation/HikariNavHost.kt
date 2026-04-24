package com.hikari.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hikari.app.ui.channels.ChannelsScreen
import com.hikari.app.ui.feed.FeedScreen
import com.hikari.app.ui.rejected.RejectedScreen
import com.hikari.app.ui.saved.SavedScreen
import com.hikari.app.ui.settings.SettingsScreen
import com.hikari.app.ui.stats.StatsScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val bottomNavDestinations = listOf(
    Dest("feed", "Feed", Icons.Default.PlayArrow),
    Dest("saved", "Saved", Icons.Default.Favorite),
    Dest("channels", "Channels", Icons.Default.List),
    Dest("settings", "Settings", Icons.Default.Settings),
    Dest("rejected", "Rejected", Icons.Default.Close),
)

@Composable
fun HikariNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Routes that show bottom nav
    val bottomNavRoutes = bottomNavDestinations.map { it.route }.toSet()

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar {
                    bottomNavDestinations.forEach { d ->
                        NavigationBarItem(
                            selected = currentRoute == d.route,
                            onClick = {
                                nav.navigate(d.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(d.icon, d.label) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "feed", modifier = Modifier.padding(padding)) {
            composable("feed") { FeedScreen() }
            composable("saved") { SavedScreen() }
            composable("channels") { ChannelsScreen() }
            composable("settings") {
                SettingsScreen(onNavigateToStats = { nav.navigate("stats") })
            }
            composable("rejected") { RejectedScreen() }
            composable("stats") { StatsScreen() }
        }
    }
}
