package com.hikari.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.player.HikariPlayerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerFactoryEntryPoint {
    fun playerFactory(): HikariPlayerFactory
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(vm: FeedViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()
    val ctx = LocalContext.current
    val factory = remember {
        EntryPointAccessors.fromApplication(ctx, PlayerFactoryEntryPoint::class.java).playerFactory()
    }
    val player = remember { factory.create() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new reels today. Come back tomorrow.", Modifier.padding(24.dp))
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val item = items[page]
        val mediaItem = factory.mediaItemFor(baseUrl, item.videoId)
        ReelPlayer(
            item = item,
            player = player,
            mediaItem = mediaItem,
            onSeen = { vm.onSeen(item.videoId) },
            onToggleSave = { vm.onToggleSave(item.videoId, item.saved) },
            onLessLikeThis = { vm.onLessLikeThis(item.videoId) },
        )
    }
}
