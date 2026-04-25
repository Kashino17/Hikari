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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.ui.feed.HikariIcons
import com.hikari.app.ui.feed.PrecisionScrubber
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoPlayerEntryPoint {
    fun playerFactory(): HikariPlayerFactory
    fun playbackRepository(): PlaybackRepository
    fun settingsStore(): SettingsStore
}

/**
 * Standalone fullscreen-style video player — opened from the Library hero
 * "Abspielen" CTA. Owns its own ExoPlayer (separate from the Feed's shared
 * player) so a Library playback doesn't disturb the feed queue.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    title: String,
    channel: String,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

    val ep = remember {
        EntryPointAccessors.fromApplication(ctx, VideoPlayerEntryPoint::class.java)
    }
    val factory = remember { ep.playerFactory() }
    val playbackRepo = remember { ep.playbackRepository() }
    val settingsStore = remember { ep.settingsStore() }
    val baseUrl = remember { runBlocking { settingsStore.backendUrl.first() } }
    val player = remember { factory.create() }

    var landscape by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }

    // ── Lifecycle: prepare once, save position on dispose ────────────────────
    LaunchedEffect(videoId) {
        val savedPos = playbackRepo.getPosition(videoId)
        player.setMediaItem(factory.mediaItemFor(baseUrl, videoId), savedPos)
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            if (pos > 0L) runBlocking { playbackRepo.savePosition(videoId, pos) }
            player.release()
        }
    }

    // ── Orientation + system bars ────────────────────────────────────────────
    DisposableEffect(activity, landscape) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (landscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── Auto-hide chrome after 3s when playing ───────────────────────────────
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            kotlinx.coroutines.delay(3_000)
            controlsVisible = false
        }
    }

    // ── Position polling ─────────────────────────────────────────────────────
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    val wasPlayingBeforeScrub = remember { mutableStateOf(true) }
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
            .background(HikariBg)
            .pointerInput(videoId) {
                detectTapGestures(
                    onTap = {
                        playing = !playing
                        player.playWhenReady = playing
                        controlsVisible = true
                    },
                )
            },
    ) {
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

        // ── Top scrim + back arrow + title ───────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                            ),
                        ),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onBack() }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = HikariText,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    if (channel.isNotBlank()) {
                        Text(
                            text = channel.uppercase(),
                            color = HikariAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = title.ifBlank { "Wird geladen…" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(landscape) {
                            detectTapGestures(onTap = { landscape = !landscape })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (landscape) HikariIcons.FullscreenExit else HikariIcons.Fullscreen,
                        contentDescription = if (landscape) "Vollbild beenden" else "Vollbild",
                        tint = HikariText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ── Center play/pause indicator (when paused) ────────────────────────
        if (!playing && controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // ── Bottom scrim + scrubber ──────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        ),
                    ),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
        ) {
            PrecisionScrubber(
                positionMs = position,
                durationMs = duration,
                onScrubStart = {
                    wasPlayingBeforeScrub.value = player.playWhenReady
                    player.playWhenReady = false
                    isScrubbing = true
                    controlsVisible = true
                },
                onScrubUpdate = { previewMs -> position = previewMs },
                onScrubEnd = { finalMs ->
                    player.seekTo(finalMs)
                    if (wasPlayingBeforeScrub.value) {
                        player.playWhenReady = true
                        playing = true
                    }
                    isScrubbing = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Listen to player state to keep `playing` in sync (e.g. ended)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
