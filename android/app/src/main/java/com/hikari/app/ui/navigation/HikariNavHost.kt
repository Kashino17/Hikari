package com.hikari.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.NavController
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

    Scaffold(
        containerColor = HikariBg,
        bottomBar = {
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
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = "feed",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("feed") {
                FeedScreen(onNavigate = { route -> navTo(nav, route) })
            }
            composable("channels") { ChannelsScreen() }
            composable("tuning") { TuningScreen() }
        }
    }
}
