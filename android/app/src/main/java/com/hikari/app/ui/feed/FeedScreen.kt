package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.player.PreloadCoordinator
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
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
    @Suppress("UNUSED_PARAMETER") onNavigate: (String) -> Unit = {},
) {
    val mode by vm.mode.collectAsState()
    val items by vm.items.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val error by vm.error.collectAsState()
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var chromeVisible by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            kotlinx.coroutines.delay(2_500)
            chromeVisible = false
        }
    }

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(ctx, FeedEntryPoint::class.java)
    }
    val factory = remember { entryPoint.playerFactory() }
    val sponsorBlock = remember { entryPoint.sponsorBlockClient() }
    val playbackRepo = remember { entryPoint.playbackRepository() }
    val sponsorBlockPrefs = remember { entryPoint.sponsorBlockPrefs() }
    val player = remember { factory.create() }

    DisposableEffect(Unit) { onDispose { player.release() } }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDeleteConfirm && deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = HikariBg,
            title = { Text("Video löschen?") },
            text = { Text("Das Video wird unwiderruflich entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.onDelete(deleteTargetId!!)
                    showDeleteConfirm = false
                    deleteTargetId = null
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        FilterPills(mode = mode, onSelect = { vm.setMode(it) })
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            error ?: when (mode) {
                                FeedMode.NEW -> "Keine neuen Reels heute.\nTipp auf Archiv für ältere Videos."
                                FeedMode.SAVED -> "Noch nichts gespeichert."
                                FeedMode.OLD -> "Archiv ist leer."
                            },
                            color = HikariTextFaint,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { items.size })

                val itemsState = rememberUpdatedState(items)
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
                        onShowControls = { chromeVisible = true },
                    )
                }

                // Top chrome — counter + save (overlaid above the player)
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    val current = items.getOrNull(pagerState.currentPage)
                    Column(Modifier.fillMaxWidth()) {
                        // Top row: counter / pills / save
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${(pagerState.currentPage + 1).toString().padStart(2, '0')} / ${
                                    items.size.toString().padStart(2, '0')
                                }",
                                color = HikariTextFaint,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            )
                            Spacer(Modifier.weight(1f))
                            FilterPills(
                                mode = mode,
                                onSelect = { vm.setMode(it) },
                            )
                            Spacer(Modifier.weight(1f))
                            current?.let {
                                BookmarkButton(
                                    saved = it.saved,
                                    onClick = { vm.onToggleSave(it.videoId, it.saved) },
                                )
                            } ?: Box(Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPills(mode: FeedMode, onSelect: (FeedMode) -> Unit) {
    val items = listOf(
        FeedMode.NEW to "Alle",
        FeedMode.SAVED to "Gespeichert",
        FeedMode.OLD to "Archiv",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(HikariBg.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(2.dp),
    ) {
        items.forEach { (m, label) ->
            val active = mode == m
            Box(
                modifier = Modifier
                    .clickable { onSelect(m) }
                    .background(
                        if (active) HikariAmber else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = if (active) androidx.compose.ui.graphics.Color.Black else HikariTextFaint,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun BookmarkButton(saved: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (saved) HikariIcons.Bookmark else HikariIcons.BookmarkOutline,
            contentDescription = if (saved) "Gespeichert" else "Speichern",
            tint = if (saved) HikariAmber else HikariText,
            modifier = Modifier.size(18.dp),
        )
    }
}
