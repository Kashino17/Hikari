package com.hikari.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.ui.Alignment
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
import com.hikari.app.ui.channels.VideoEditScreen
import com.hikari.app.ui.feed.FeedScreen
import com.hikari.app.ui.library.LibraryScreen
import com.hikari.app.ui.library.SeriesDetailScreen
import com.hikari.app.ui.manga.MangaDetailScreen
import com.hikari.app.ui.manga.MangaListScreen
import com.hikari.app.ui.manga.MangaReaderScreen
import com.hikari.app.ui.player.FullscreenOriginalPlayer
import com.hikari.app.ui.player.VideoPlayerScreen
import com.hikari.app.ui.profile.DownloadCategoryScreen
import com.hikari.app.ui.profile.ProfileScreen
import com.hikari.app.ui.profile.tabs.DownloadCategory
import com.hikari.app.ui.settings.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.tuning.TuningScreen
import com.hikari.app.ui.music.MiniMusicBubble
import com.hikari.app.ui.music.ArtistScreen
import com.hikari.app.ui.music.GroupDetailScreen
import com.hikari.app.ui.music.MixDetailScreen
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.ui.music.MusicProfileScreen
import com.hikari.app.ui.music.MusicScreen
import com.hikari.app.ui.music.NowPlayingScreen
import com.hikari.app.ui.music.PlaylistDetailScreen
import com.hikari.app.ui.music.RemotePlaylistScreen
import com.hikari.app.ui.news.NewsScreen
import com.hikari.app.ui.games.GamesScreen
import com.hikari.app.ui.games.BlockBlastGame
import com.hikari.app.ui.games.FruitHoleGame
import com.hikari.app.ui.games.FruitMergeGame
import com.hikari.app.ui.games.SpaceShooterGame
import com.hikari.app.ui.games.TicTacToeGame

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
fun HikariNavHost(deepLinkRoute: String? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var feedFullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != "feed" && feedFullscreen) {
            feedFullscreen = false
        }
    }

    // Deep-Link aus Intents (z.B. News-Benachrichtigung) auf den Ziel-Tab
    // bzw. die Ziel-Section.
    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute != null &&
            (hikariDestinations.any { it.route == deepLinkRoute } || deepLinkRoute in hubSectionRoutes)
        ) {
            navTo(nav, deepLinkRoute)
        }
    }

    val isVideoRoute = currentRoute?.startsWith("video/") == true ||
        currentRoute?.startsWith("original/") == true
    val isReaderRoute = currentRoute?.matches(Regex("manga/[^/]+/[^/?]+(\\?.*)?")) == true
    // Settings + Tuning sind ab v0.25.0 nur über Profil-Gear erreichbar — sub-pages,
    // also auch ohne Bottom-Nav rendern (eigener Back-Button reicht).
    val isGearSubPage = currentRoute == "settings" || currentRoute?.startsWith("tuning") == true
    val isGameRoute = currentRoute?.startsWith("game/") == true
    val isNowPlaying = currentRoute == "nowplaying"
    val isPlaylistRoute = currentRoute?.startsWith("playlist/") == true
    val isMixRoute = currentRoute?.startsWith("mix/") == true
    val isArtistRoute = currentRoute?.startsWith("artist/") == true
    val isCollectionRoute = currentRoute?.startsWith("music/collection/") == true
    val isMusicProfileRoute = currentRoute == "music/profile"
    val inMusicSection = currentRoute == "music" || isNowPlaying || isPlaylistRoute || isMixRoute ||
        isArtistRoute || isCollectionRoute || isMusicProfileRoute
    val showsBottomBar = !(currentRoute == "feed" && feedFullscreen) && !isVideoRoute &&
        !isReaderRoute && !isGearSubPage && !isGameRoute && !isNowPlaying &&
        !isPlaylistRoute && !isMixRoute && !isArtistRoute && !isCollectionRoute

    Scaffold(
        containerColor = HikariBg,
        bottomBar = {
            if (showsBottomBar) {
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
      Box(Modifier.fillMaxSize()) {
        NavHost(
            nav,
            startDestination = "library",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
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
            composable(
                route = "original/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId") ?: return@composable
                FullscreenOriginalPlayer(
                    videoId = videoId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("feed") {
                FeedScreen(
                    fullscreen = feedFullscreen,
                    onFullscreenChange = { feedFullscreen = it },
                    onNavigate = { route -> nav.navigate(route) },
                )
            }
            composable("news") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    NewsScreen()
                }
            }
            composable(
                route = "channel/{channelId}",
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
            ) { entry ->
                val channelId = entry.arguments?.getString("channelId").orEmpty()
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ChannelDetailScreen(
                        onBack = { nav.popBackStack() },
                        onEditVideo = { videoId -> nav.navigate("video-edit/$videoId") },
                        onOpenFilter = { title ->
                            val cid = URLEncoder.encode(channelId, "UTF-8")
                            val ct = URLEncoder.encode(title, "UTF-8")
                            nav.navigate("tuning?channelId=$cid&channelTitle=$ct")
                        },
                    )
                }
            }
            composable(
                route = "video-edit/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                Box(Modifier.fillMaxSize().padding(padding)) {
                    VideoEditScreen(
                        videoId = videoId,
                        onBack = { nav.popBackStack() },
                        onSaved = { nav.popBackStack() },
                    )
                }
            }
            composable(
                route = "tuning?channelId={channelId}&channelTitle={channelTitle}",
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("channelTitle") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    TuningScreen(onBack = { nav.popBackStack() })
                }
            }
            composable("music") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    MusicScreen(
                        onOpenNowPlaying = { nav.navigate("nowplaying") },
                        onOpenProfile = { nav.navigate("music/profile") },
                        onOpenMix = { title, query, mode ->
                            val t = URLEncoder.encode(title, "UTF-8")
                            val q = URLEncoder.encode(query, "UTF-8")
                            nav.navigate("mix/$t?q=$q&mode=$mode")
                        },
                        onOpenGroup = { title, unit, query, mode ->
                            val t = URLEncoder.encode(title, "UTF-8")
                            val u = URLEncoder.encode(unit, "UTF-8")
                            val q = URLEncoder.encode(query, "UTF-8")
                            nav.navigate("musicGroup/$t?unit=$u&q=$q&mode=$mode")
                        },
                        onOpenArtist = { id, name ->
                            nav.navigate("artist/$id?name=${URLEncoder.encode(name, "UTF-8")}")
                        },
                        onOpenCollection = { playlistId, name, isAlbum ->
                            val n = URLEncoder.encode(name, "UTF-8")
                            nav.navigate("music/collection/$playlistId?name=$n&isAlbum=$isAlbum")
                        },
                    )
                }
            }
            composable("music/profile") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    MusicProfileScreen(
                        onBack = { nav.popBackStack() },
                        onOpenPlaylist = { id -> nav.navigate("playlist/$id") },
                        onOpenNowPlaying = { nav.navigate("nowplaying") },
                    )
                }
            }
            composable("nowplaying") {
                NowPlayingScreen(
                    onBack = { nav.popBackStack() },
                    onOpenArtist = { id, name ->
                        nav.navigate("artist/$id?name=${URLEncoder.encode(name, "UTF-8")}")
                    },
                )
            }
            composable(
                route = "artist/{channelId}?name={name}",
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val channelId = entry.arguments?.getString("channelId").orEmpty()
                val name = URLDecoder.decode(entry.arguments?.getString("name").orEmpty(), "UTF-8")
                ArtistScreen(
                    channelId = channelId,
                    fallbackName = name,
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("nowplaying") },
                    onOpenCollection = { playlistId, collectionName, isAlbum ->
                        val n = URLEncoder.encode(collectionName, "UTF-8")
                        nav.navigate("music/collection/$playlistId?name=$n&isAlbum=$isAlbum")
                    },
                    onOpenArtist = { id, artistName ->
                        nav.navigate("artist/$id?name=${URLEncoder.encode(artistName, "UTF-8")}")
                    },
                )
            }
            composable(
                route = "mix/{title}?q={q}&mode={mode}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "music" },
                ),
            ) { entry ->
                val title = URLDecoder.decode(entry.arguments?.getString("title").orEmpty(), "UTF-8")
                val query = URLDecoder.decode(entry.arguments?.getString("q").orEmpty(), "UTF-8")
                MixDetailScreen(
                    title = title,
                    query = query.ifBlank { title },
                    mode = MusicSearchMode.fromApiValue(entry.arguments?.getString("mode")),
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("nowplaying") },
                )
            }
            composable(
                route = "music/collection/{playlistId}?name={name}&isAlbum={isAlbum}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("isAlbum") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val playlistId = entry.arguments?.getString("playlistId").orEmpty()
                val name = URLDecoder.decode(entry.arguments?.getString("name").orEmpty(), "UTF-8")
                val isAlbum = entry.arguments?.getBoolean("isAlbum") ?: false
                RemotePlaylistScreen(
                    playlistId = playlistId,
                    name = name,
                    isAlbum = isAlbum,
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("nowplaying") },
                )
            }
            composable(
                route = "musicGroup/{title}?unit={unit}&q={q}&mode={mode}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("unit") { type = NavType.StringType; defaultValue = "Kapitel" },
                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "music" },
                ),
            ) { entry ->
                val title = URLDecoder.decode(entry.arguments?.getString("title").orEmpty(), "UTF-8")
                val unit = URLDecoder.decode(entry.arguments?.getString("unit").orEmpty(), "UTF-8")
                    .ifBlank { "Kapitel" }
                val query = URLDecoder.decode(entry.arguments?.getString("q").orEmpty(), "UTF-8")
                GroupDetailScreen(
                    title = title,
                    unitLabel = unit,
                    query = query.ifBlank { title },
                    mode = MusicSearchMode.fromApiValue(entry.arguments?.getString("mode")),
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("nowplaying") },
                )
            }
            composable(
                route = "playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.IntType }),
            ) { entry ->
                val playlistId = entry.arguments?.getInt("playlistId") ?: return@composable
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { nav.popBackStack() },
                    onOpenNowPlaying = { nav.navigate("nowplaying") },
                )
            }
            composable("games") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    GamesScreen(
                        onBack = { nav.popBackStack() },
                        onLaunchGame = { gameId -> nav.navigate("game/$gameId") },
                    )
                }
            }
            composable(
                route = "game/{gameId}",
                arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
            ) { entry ->
                val gameId = entry.arguments?.getString("gameId") ?: return@composable
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (gameId) {
                        "tictactoe" -> TicTacToeGame(onBack = { nav.popBackStack() })
                        "blockblast" -> BlockBlastGame(onBack = { nav.popBackStack() })
                        "spaceshooter" -> SpaceShooterGame(onBack = { nav.popBackStack() })
                        "fruithole" -> FruitHoleGame(onBack = { nav.popBackStack() })
                        "fruitmerge" -> FruitMergeGame(onBack = { nav.popBackStack() })
                        else -> GamesScreen(
                            onBack = { nav.popBackStack() },
                            onLaunchGame = { id -> nav.navigate("game/$id") },
                        )
                    }
                }
            }
            composable("profile") {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ProfileScreen(
                        onOpenSettings = { nav.navigate("settings") },
                        onOpenChannel = { id -> nav.navigate("channel/$id") },
                        onPlayVideo = { videoId, title, channel ->
                            nav.navigate(playVideoRoute(videoId, title, channel))
                        },
                        onOpenDownloadCategory = { cat ->
                            nav.navigate("download-category/${cat.name}")
                        },
                        // Hub-Bereiche als Push — System-Zurück führt ins Profil.
                        onOpenSection = { route -> nav.navigate(route) },
                    )
                }
            }
            composable("settings") {
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenTuning = { nav.navigate("tuning") },
                    )
                }
            }
            composable(
                route = "download-category/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType }),
            ) {
                Box(Modifier.fillMaxSize()) {
                    DownloadCategoryScreen(
                        onBack = { nav.popBackStack() },
                        onPlayVideo = { videoId, title, channel ->
                            nav.navigate(playVideoRoute(videoId, title, channel))
                        },
                    )
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

        // Schnellzugriff auf die laufende Musik — sitzt über der Bottom-Bar
        // im Daumenbereich und nur dort, wo die Leiste ohnehin sichtbar ist.
        if (showsBottomBar) {
            MiniMusicBubble(
                inMusicSection = inMusicSection,
                onOpen = { navTo(nav, "music") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = padding.calculateBottomPadding() + 8.dp,
                        end = 12.dp,
                    ),
            )
        }
      }
    }
}
