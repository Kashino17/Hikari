package com.hikari.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun HikariNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Feed is fullscreen — no bottom nav on that route
    val isFeed = currentRoute == "feed"

    Scaffold(
        bottomBar = {
            if (!isFeed) {
                NavigationBar {
                    hikariDestinations.forEach { d ->
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
        NavHost(
            nav,
            startDestination = "feed",
            // Feed is edge-to-edge, other screens respect scaffold padding
            modifier = if (isFeed) Modifier else Modifier.padding(padding),
        ) {
            composable("feed") {
                FeedScreen(
                    onNavigate = { route ->
                        nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
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
