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
import com.hikari.app.ui.library.LibraryScreen
import com.hikari.app.ui.library.SeriesDetailScreen
import com.hikari.app.ui.manga.MangaDetailScreen
import com.hikari.app.ui.manga.MangaListScreen
import com.hikari.app.ui.manga.MangaReaderScreen
import com.hikari.app.ui.player.VideoPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder
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

private fun playVideoRoute(videoId: String, title: String, channel: String): String {
    val t = URLEncoder.encode(title, "UTF-8")
    val c = URLEncoder.encode(channel, "UTF-8")
    return "video/$videoId?title=$t&channel=$c"
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

    val isVideoRoute = currentRoute?.startsWith("video/") == true
    val isReaderRoute = currentRoute?.matches(Regex("manga/[^/]+/[^/?]+(\\?.*)?")) == true

    Scaffold(
        containerColor = HikariBg,
        bottomBar = {
            if (!(currentRoute == "feed" && feedFullscreen) && !isVideoRoute && !isReaderRoute) {
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
            startDestination = "library",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("library") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    LibraryScreen(
                        onOpenSeries = { id -> nav.navigate("series/$id") },
                        onOpenChannel = { id -> nav.navigate("channel/$id") },
                        onPlayVideo = { videoId, title, channel ->
                            nav.navigate(playVideoRoute(videoId, title, channel))
                        },
                    )
                }
            }
            composable(
                route = "series/{seriesId}",
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val seriesId = backStackEntry.arguments?.getString("seriesId")
                Box(Modifier.fillMaxSize()) {
                    SeriesDetailScreen(
                        seriesId = seriesId,
                        onBack = { nav.popBackStack() },
                        onPlayVideo = { videoId ->
                            nav.navigate(playVideoRoute(videoId, "", ""))
                        },
                    )
                }
            }
            composable(
                route = "video/{videoId}?title={title}&channel={channel}",
                arguments = listOf(
                    navArgument("videoId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("channel") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId").orEmpty()
                val title = URLDecoder.decode(
                    backStackEntry.arguments?.getString("title").orEmpty(), "UTF-8",
                )
                val channel = URLDecoder.decode(
                    backStackEntry.arguments?.getString("channel").orEmpty(), "UTF-8",
                )
                VideoPlayerScreen(
                    videoId = videoId,
                    title = title,
                    channel = channel,
                    onBack = { nav.popBackStack() },
                )
            }
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
            composable("manga") {
                MangaListScreen(
                    onSeriesClick = { id ->
                        nav.navigate("manga/${URLEncoder.encode(id, "UTF-8")}")
                    },
                    onContinueClick = { sId, cId, page ->
                        val sE = URLEncoder.encode(sId, "UTF-8")
                        val cE = URLEncoder.encode(cId, "UTF-8")
                        nav.navigate("manga/$sE/$cE?page=$page")
                    },
                )
            }
            composable(
                "manga/{seriesId}",
                arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
            ) { entry ->
                val sId = URLDecoder.decode(
                    entry.arguments!!.getString("seriesId")!!,
                    "UTF-8",
                )
                MangaDetailScreen(
                    seriesId = sId,
                    onBack = { nav.popBackStack() },
                    onChapterClick = { cId, page ->
                        val sE = URLEncoder.encode(sId, "UTF-8")
                        val cE = URLEncoder.encode(cId, "UTF-8")
                        val pq = page?.let { "?page=$it" } ?: ""
                        nav.navigate("manga/$sE/$cE$pq")
                    },
                )
            }
            composable(
                "manga/{seriesId}/{chapterId}?page={page}",
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = 1
                    },
                ),
            ) { entry ->
                val sId = URLDecoder.decode(entry.arguments!!.getString("seriesId")!!, "UTF-8")
                val cId = URLDecoder.decode(entry.arguments!!.getString("chapterId")!!, "UTF-8")
                val page = entry.arguments!!.getInt("page")
                MangaReaderScreen(
                    seriesId = sId,
                    chapterId = cId,
                    initialPage = page,
                    onBack = { nav.popBackStack() },
                    onOpenChapter = { nextChapterId ->
                        val sE = URLEncoder.encode(sId, "UTF-8")
                        val cE = URLEncoder.encode(nextChapterId, "UTF-8")
                        nav.navigate("manga/$sE/$cE") {
                            popUpTo("manga/$sE/${URLEncoder.encode(cId, "UTF-8")}") {
                                inclusive = true
                            }
                        }
                    },
                )
            }
        }
    }
}
