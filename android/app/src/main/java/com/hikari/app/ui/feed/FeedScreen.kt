package com.hikari.app.ui.feed

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.player.PreloadCoordinator
import com.hikari.app.ui.navigation.hikariDestinations
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeedEntryPoint {
    fun playerFactory(): HikariPlayerFactory
    fun sponsorBlockClient(): SponsorBlockClient
    fun playbackRepository(): PlaybackRepository
    fun sponsorBlockPrefs(): SponsorBlockPrefs
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit = {},
) {
    val mode by vm.mode.collectAsState()
    val newItems by vm.items.collectAsState()
    val oldItems by vm.oldItems.collectAsState()
    val items = when (mode) {
        FeedMode.NEW -> newItems
        FeedMode.OLD -> oldItems
    }
    val baseUrl by vm.backendUrl.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val today by vm.today.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFilterDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    // Top chrome auto-hides after 3s, same pattern as bottom chrome in ReelPlayer
    var topControlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(topControlsVisible) {
        if (topControlsVisible) {
            kotlinx.coroutines.delay(3_000)
            topControlsVisible = false
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var moreOptionsOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    val moreOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Hoisted current page index so the MoreOptions sheet (rendered at Box level) knows which item
    var currentPageIndex by remember { mutableStateOf(0) }

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(ctx, FeedEntryPoint::class.java)
    }
    val factory = remember { entryPoint.playerFactory() }
    val sponsorBlock = remember { entryPoint.sponsorBlockClient() }
    val playbackRepo = remember { entryPoint.playbackRepository() }
    val sponsorBlockPrefs = remember { entryPoint.sponsorBlockPrefs() }
    val player = remember { factory.create() }

    // Get activity for fullscreen control
    val activity = remember {
        var c = ctx
        while (c !is ComponentActivity) {
            c = (c as android.content.ContextWrapper).baseContext
        }
        c as ComponentActivity
    }

    // Fullscreen toggle helper
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Restore portrait on leave
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            if (isFullscreen) {
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    // Category filter dialog
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter by Category") },
            text = {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { vm.selectCategory(null); showFilterDialog = false },
                        label = { Text("All") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { vm.selectCategory(cat); showFilterDialog = false },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("Close") }
            },
        )
    }

    // Navigation menu bottom sheet
    if (menuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                hikariDestinations.filter { it.route != "feed" }.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.label) },
                        leadingContent = { Icon(d.icon, d.label) },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                menuOpen = false
                                onNavigate(d.route)
                            }
                        },
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Video löschen?") },
            text = { Text("Das Video wird unwiderruflich von deinem Gerät und Server entfernt.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.onDelete(deleteTargetId!!)
                        showDeleteConfirm = false
                        deleteTargetId = null
                    },
                ) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            },
        )
    }

    val edgeZoneDp = 40.dp
    val triggerDp = 50.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val screenWidthPx = size.width.toFloat()
                    val edgePx = edgeZoneDp.toPx()
                    val triggerPx = triggerDp.toPx()

                    // Only consider touches starting near the right edge
                    if (down.position.x < screenWidthPx - edgePx) return@awaitEachGesture

                    var dx = 0f
                    var dy = 0f
                    var decided = false
                    var willOpenMenu = false

                    while (!decided) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val moved = change.positionChange()
                        dx += moved.x
                        dy += moved.y

                        // Decide early if we should open menu (aggressive leftward drag)
                        if (dx < -triggerPx && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            willOpenMenu = true
                            decided = true
                            change.consume()
                        }

                        // If movement is primarily vertical, bail so VerticalPager handles it
                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                            decided = true
                        }
                    }

                    if (willOpenMenu && !menuOpen) {
                        menuOpen = true
                    }
                }
            },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No new reels today. Come back tomorrow.", Modifier.padding(24.dp))
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { items.size })

                // Keep hoisted page index in sync for MoreOptions sheet
                LaunchedEffect(pagerState.currentPage) {
                    currentPageIndex = pagerState.currentPage
                }

                // CRITICAL: only rebuild the player queue when the USER swipes to a new
                // page — NEVER when items reshuffles underneath us (e.g., mark-seen,
                // save-toggle, or any other Room state change). Rebuilding the queue
                // calls setMediaItems(resetPosition = true), which snaps the player
                // back to position 0 of item 0 — and that was responsible for the
                // "seek forward, snap back" bug users were seeing after ±5s seeks.
                //
                // We therefore key ONLY on pagerState.currentPage. We also fire once
                // on the empty→non-empty transition to initialize the queue when
                // items first load.
                val itemsState = androidx.compose.runtime.rememberUpdatedState(items)
                val firstItemReady = items.isNotEmpty()
                LaunchedEffect(pagerState.currentPage, firstItemReady) {
                    if (!firstItemReady) return@LaunchedEffect
                    val current = itemsState.value
                    val idx = pagerState.currentPage.coerceAtMost(current.size - 1)
                    val upcoming = current.drop(idx).take(3).map {
                        factory.mediaItemFor(baseUrl, it.videoId)
                    }
                    PreloadCoordinator.setQueue(player, upcoming)
                    player.playWhenReady = true
                }

                VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val item = items[page]
                    ReelPlayer(
                        item = item,
                        player = player,
                        isCurrent = page == pagerState.currentPage,
                        sponsorBlock = sponsorBlock,
                        playbackRepo = playbackRepo,
                        sponsorBlockPrefs = sponsorBlockPrefs,
                        onSeen = { vm.onSeen(item.videoId) },
                        onToggleSave = { vm.onToggleSave(item.videoId, item.saved) },
                        onLessLikeThis = { vm.onLessLikeThis(item.videoId) },
                        onUnplayable = {
                            vm.onUnplayable(item.videoId)
                            scope.launch {
                                if (page + 1 < items.size) {
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            }
                        },
                        onShowControls = { topControlsVisible = true },
                    )
                }
            }
        }

        // MoreOptions bottom sheet (Save / Less-like-this / Delete)
        if (moreOptionsOpen) {
            val current = items.getOrNull(currentPageIndex)
            if (current != null) {
                ModalBottomSheet(
                    onDismissRequest = { moreOptionsOpen = false },
                    sheetState = moreOptionsSheetState,
                ) {
                    Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        ListItem(
                            headlineContent = {
                                Text(if (current.saved) "Aus Merkliste entfernen" else "Speichern")
                            },
                            leadingContent = { Icon(Icons.Default.Favorite, null) },
                            modifier = Modifier.clickable {
                                vm.onToggleSave(current.videoId, current.saved)
                                scope.launch { moreOptionsSheetState.hide() }.invokeOnCompletion {
                                    moreOptionsOpen = false
                                }
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Weniger wie das") },
                            leadingContent = { Icon(Icons.Default.Close, null) },
                            modifier = Modifier.clickable {
                                vm.onLessLikeThis(current.videoId)
                                scope.launch { moreOptionsSheetState.hide() }.invokeOnCompletion {
                                    moreOptionsOpen = false
                                }
                            },
                        )
                        if (mode == FeedMode.OLD) {
                            HorizontalDivider()
                            ListItem(
                                headlineContent = {
                                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    deleteTargetId = current.videoId
                                    scope.launch { moreOptionsSheetState.hide() }.invokeOnCompletion {
                                        moreOptionsOpen = false
                                        showDeleteConfirm = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Top overlay: menu + budget + fullscreen toggle + New/Old tabs
        // Fades out after 3s of no interaction; tap/double-tap on player reveals it again.
        // Hidden in fullscreen landscape (system bars hidden, user taps for transient reveal).
        if (!isFullscreen) {
            AnimatedVisibility(
                visible = topControlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                // Top row: hamburger / filter / budget / fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Hamburger / menu icon
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }

                    // Category filter (only meaningful in NEW mode)
                    if (mode == FeedMode.NEW && categories.isNotEmpty()) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Filter by category",
                                tint = if (selectedCategory != null)
                                    MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Daily budget indicator — only relevant in NEW mode
                    if (mode == FeedMode.NEW) {
                        today?.let { todayData ->
                            Text(
                                "${todayData.unseenCount} / ${todayData.dailyBudget}",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }

                    // More options
                    IconButton(onClick = { moreOptionsOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }

                    // Fullscreen toggle
                    IconButton(onClick = { toggleFullscreen() }) {
                        Icon(
                            imageVector = if (isFullscreen) HikariIcons.FullscreenExit else HikariIcons.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                            tint = Color.White,
                        )
                    }
                }

                // New / Old tabs — full width under the top row
                PrimaryTabRow(
                    selectedTabIndex = if (mode == FeedMode.NEW) 0 else 1,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ) {
                    Tab(
                        selected = mode == FeedMode.NEW,
                        onClick = { vm.setMode(FeedMode.NEW) },
                    ) {
                        Text("New", modifier = Modifier.padding(16.dp), color = Color.White)
                    }
                    Tab(
                        selected = mode == FeedMode.OLD,
                        onClick = { vm.setMode(FeedMode.OLD) },
                    ) {
                        Text("Old", modifier = Modifier.padding(16.dp), color = Color.White)
                    }
                }
            }
            } // end AnimatedVisibility
        } else {
            // In landscape fullscreen: just the exit button top-right
            IconButton(
                onClick = { toggleFullscreen() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = HikariIcons.FullscreenExit,
                    contentDescription = "Exit fullscreen",
                    tint = Color.White,
                )
            }
        }
    }
}
