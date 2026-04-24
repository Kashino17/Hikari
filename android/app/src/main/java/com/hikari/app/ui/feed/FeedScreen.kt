package com.hikari.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.player.PreloadCoordinator
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
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: FeedViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val today by vm.today.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(ctx, FeedEntryPoint::class.java)
    }
    val factory = remember { entryPoint.playerFactory() }
    val sponsorBlock = remember { entryPoint.sponsorBlockClient() }
    val playbackRepo = remember { entryPoint.playbackRepository() }
    val player = remember { factory.create() }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    LaunchedEffect(Unit) { vm.refresh() }

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

            // A3: Preload next 2 videos via ExoPlayer queue
            LaunchedEffect(pagerState.currentPage, items) {
                val upcoming = items.drop(pagerState.currentPage).take(3).map {
                    factory.mediaItemFor(baseUrl, it.videoId)
                }
                PreloadCoordinator.setQueue(player, upcoming)
                player.seekTo(0, 0L)
                player.playWhenReady = true
            }

            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val item = items[page]
                ReelPlayer(
                    item = item,
                    player = player,
                    sponsorBlock = sponsorBlock,
                    playbackRepo = playbackRepo,
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
                )
            }

            // B5: Daily budget indicator
            today?.let { todayData ->
                Text(
                    "${todayData.unseenCount} / ${todayData.dailyBudget}",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
