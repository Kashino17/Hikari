package com.hikari.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hikari.app.ui.channels.ChannelsScreen
import com.hikari.app.ui.feed.FeedScreen
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.tuning.TuningScreen

@Composable
fun HikariNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Feed is fullscreen — bottom nav floats over via Box, not Scaffold.
    val isFeed = currentRoute == "feed"

    Scaffold(
        containerColor = HikariBg,
        bottomBar = {
            if (!isFeed) MinimalBottomBar(currentRoute) { route ->
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                nav,
                startDestination = "feed",
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
                composable("channels") { ChannelsScreen() }
                composable("tuning") { TuningScreen() }
            }
            // Floating bottom nav over feed (always visible)
            if (isFeed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(androidx.compose.ui.Alignment.BottomCenter),
                ) {
                    MinimalBottomBar(currentRoute) { route ->
                        nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Box {
        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
        NavigationBar(
            containerColor = HikariBg.copy(alpha = 0.95f),
            contentColor = HikariTextFaint,
            tonalElevation = 0.dp,
            modifier = Modifier.height(64.dp),
        ) {
            hikariDestinations.forEach { d ->
                NavigationBarItem(
                    selected = currentRoute == d.route,
                    onClick = { onNavigate(d.route) },
                    icon = { Icon(d.icon, d.label) },
                    label = { Text(d.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        unselectedIconColor = HikariTextFaint,
                        unselectedTextColor = HikariTextFaint,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
