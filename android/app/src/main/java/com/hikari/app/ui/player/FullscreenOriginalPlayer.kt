package com.hikari.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.VideoFullDto
import com.hikari.app.ui.theme.HikariAmber
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FullscreenOriginalEntryPoint {
    fun hikariApi(): HikariApi
}

/**
 * "Original ansehen"-Action im Feed öffnet diesen Screen für Clips. Holt
 * Title/Channel via /videos/:id/full und delegiert dann komplett an
 * VideoPlayerScreen mit startInLandscape=true — so kriegt der User exakt
 * die gleiche Player-UX wie aus der Library/Series-Detail (gestures,
 * scrubber, sponsorblock, alles).
 */
@Composable
fun FullscreenOriginalPlayer(
    videoId: String,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val ep = remember {
        EntryPointAccessors.fromApplication(ctx, FullscreenOriginalEntryPoint::class.java)
    }
    val api = remember { ep.hikariApi() }

    var info by remember { mutableStateOf<VideoFullDto?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        runCatching { api.getVideoFull(videoId) }
            .onSuccess { info = it }
            .onFailure { error = true }
    }

    val loaded = info
    when {
        loaded != null -> VideoPlayerScreen(
            videoId = videoId,
            title = loaded.title,
            channel = loaded.channelTitle,
            onBack = onBack,
            startInLandscape = true,
        )
        error -> Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Video konnte nicht geladen werden.", color = Color.White.copy(alpha = 0.7f))
        }
        else -> Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HikariAmber)
        }
    }
}
