package com.hikari.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hikari.app.ui.channels.ChannelDetailScreen
import com.hikari.app.ui.channels.ChannelsScreen
import com.hikari.app.ui.feed.FeedScreen
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.tuning.TuningScreen

private fun navTo(nav: NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun HikariNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var feedFullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != "feed" && feedFullscreen) {
            feedFullscreen = false
        }
    }

    Scaffold(
        containerColor = HikariBg,
        bottomBar = {
            if (!(currentRoute == "feed" && feedFullscreen)) {
                HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                NavigationBar(
                    containerColor = HikariBg,
                    contentColor = HikariTextFaint,
                    tonalElevation = 0.dp,
                ) {
                    hikariDestinations.forEach { d ->
                        NavigationBarItem(
                            selected = currentRoute == d.route,
                            onClick = { navTo(nav, d.route) },
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
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = "feed",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("feed") {
                FeedScreen(
                    fullscreen = feedFullscreen,
                    onFullscreenChange = { feedFullscreen = it },
                    onNavigate = { route -> navTo(nav, route) },
                )
            }
            composable("channels") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ChannelsScreen(
                        onOpenChannel = { id -> nav.navigate("channel/$id") },
                    )
                }
            }
            composable(
                route = "channel/{channelId}",
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
            ) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ChannelDetailScreen(onBack = { nav.popBackStack() })
                }
            }
            composable("tuning") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    TuningScreen()
                }
            }
        }
    }
}
