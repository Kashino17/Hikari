package com.hikari.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.VideoFullDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.ui.feed.PrecisionScrubber
import com.hikari.app.ui.theme.HikariAmber
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FullscreenOriginalEntryPoint {
    fun hikariApi(): HikariApi
    fun playerFactory(): HikariPlayerFactory
    fun settingsStore(): SettingsStore
}

private const val ORIGINAL_CHROME_HIDE_MS = 4_000L

/**
 * Fullscreen player that always plays the complete original video (no start/end clip).
 * Opened from the feed when the user taps "Original ansehen" on a clip item.
 *
 * Follows the same EntryPoint + ExoPlayer lifecycle pattern as [VideoPlayerScreen].
 */
@OptIn(UnstableApi::class)
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
    val factory = remember { ep.playerFactory() }
    val settingsStore = remember { ep.settingsStore() }
    val baseUrl = remember { runBlocking { settingsStore.backendUrl.first() } }
    val player = remember { factory.create() }

    var videoInfo by remember { mutableStateOf<VideoFullDto?>(null) }
    var loadError by remember { mutableStateOf(false) }

    var playing by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var chromeBumpToken by remember { mutableIntStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    val wasPlayingBeforeScrub = remember { mutableStateOf(true) }

    fun showChrome() {
        controlsVisible = true
        chromeBumpToken++
    }

    KeepScreenOn()

    // ── Fetch video metadata + prepare player ────────────────────────────────
    LaunchedEffect(videoId) {
        val dto = runCatching { api.getVideoFull(videoId) }.getOrNull()
        if (dto == null) {
            loadError = true
            return@LaunchedEffect
        }
        videoInfo = dto
        // fileUrl is a server-relative path like /media/originals/foo.mp4
        val url = if (dto.fileUrl.startsWith("http")) dto.fileUrl
                  else baseUrl.trimEnd('/') + dto.fileUrl
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Mirror ExoPlayer's isPlaying into our flag (handles end-of-stream etc.)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ── Auto-hide chrome ─────────────────────────────────────────────────────
    LaunchedEffect(chromeBumpToken, controlsVisible, playing) {
        if (controlsVisible && playing) {
            kotlinx.coroutines.delay(ORIGINAL_CHROME_HIDE_MS)
            controlsVisible = false
        }
    }

    // ── Position polling ─────────────────────────────────────────────────────
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    LaunchedEffect(videoId) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (!isScrubbing) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(1L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(videoId) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) chromeBumpToken++
                    },
                )
            },
    ) {
        // ── Video surface ────────────────────────────────────────────────────
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(false)
                }
            },
            update = { view -> view.player = player },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Loading / error state ────────────────────────────────────────────
        if (videoInfo == null && !loadError) {
            CircularProgressIndicator(
                color = HikariAmber,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (loadError) {
            Text(
                "Video konnte nicht geladen werden.",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ── Top chrome: back button ──────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onBack() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Schließen",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // ── Bottom scrubber ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            PrecisionScrubber(
                positionMs = position,
                durationMs = duration,
                onScrubStart = {
                    wasPlayingBeforeScrub.value = player.playWhenReady
                    player.playWhenReady = false
                    isScrubbing = true
                    showChrome()
                },
                onScrubUpdate = { previewMs -> position = previewMs },
                onScrubEnd = { finalMs ->
                    player.seekTo(finalMs)
                    if (wasPlayingBeforeScrub.value) {
                        player.playWhenReady = true
                        playing = true
                    }
                    isScrubbing = false
                    showChrome()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
