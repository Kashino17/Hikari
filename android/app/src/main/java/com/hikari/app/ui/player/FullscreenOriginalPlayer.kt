package com.hikari.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.VideoFullDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.player.HikariPlayerFactory
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

private const val ORIGINAL_CHROME_HIDE_MS = 3_000L

/**
 * Fullscreen player that always plays the complete original video (no start/end clip).
 * Opened from the feed when the user taps "Original ansehen" on a clip item.
 *
 * UX matches the feed's ReelPlayer fullscreen mode:
 *   - Forces landscape on enter, restores on exit
 *   - Immersive system bars (hidden, swipe to peek)
 *   - Single tap  = toggle play/pause + PlayPauseIndicator + show scrubber
 *   - Double tap L = seek -5s + SeekBadge(Backward)
 *   - Double tap R = seek +5s + SeekBadge(Forward)
 *   - Bottom scrubber with mm:ss / mm:ss, auto-hides after 3s while playing
 *   - Back button top-left (always visible)
 */
@OptIn(UnstableApi::class)
@Composable
fun FullscreenOriginalPlayer(
    videoId: String,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

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
    var showTrigger by remember { mutableIntStateOf(0) }
    var seekBadge by remember { mutableStateOf<SeekDirection?>(null) }
    var boxWidth by remember { mutableIntStateOf(0) }

    KeepScreenOn()

    // ── Force landscape + immersive system bars ──────────────────────────────
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── Fetch video metadata + prepare player ────────────────────────────────
    LaunchedEffect(videoId) {
        val dto = runCatching { api.getVideoFull(videoId) }.getOrNull()
        if (dto == null) {
            loadError = true
            return@LaunchedEffect
        }
        videoInfo = dto
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

    // ── Auto-hide controls after 3s while playing ────────────────────────────
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            kotlinx.coroutines.delay(ORIGINAL_CHROME_HIDE_MS)
            controlsVisible = false
        }
    }

    // ── Position polling ─────────────────────────────────────────────────────
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(150)
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { boxWidth = it.size.width }
            .pointerInput(videoId) {
                detectTapGestures(
                    onTap = {
                        playing = !playing
                        player.playWhenReady = playing
                        showTrigger++
                        controlsVisible = true
                    },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < boxWidth / 2f
                        val delta = if (isLeft) -5_000L else 5_000L
                        val newPos = (player.currentPosition + delta).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        seekBadge = if (isLeft) SeekDirection.Backward else SeekDirection.Forward
                        controlsVisible = true
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

        // ── Center: play/pause indicator ─────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseIndicator(playing = playing, showTrigger = showTrigger)
        }

        // ── Double-tap seek badges (left / right) ────────────────────────────
        seekBadge?.let { dir ->
            val alignment = if (dir == SeekDirection.Backward) Alignment.CenterStart else Alignment.CenterEnd
            Box(modifier = Modifier.align(alignment).padding(24.dp)) {
                SeekBadge(direction = dir, onDismiss = { seekBadge = null })
            }
        }

        // ── Top-left back button — always visible ────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
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

        // ── Bottom scrubber with time labels (auto-hides with controls) ──────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                LinearProgressIndicator(
                    progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = HikariAmber,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatPlayerTime(positionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        formatPlayerTime(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
