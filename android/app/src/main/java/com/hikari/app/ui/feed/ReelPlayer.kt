package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.data.sponsor.SponsorSegment
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.SponsorSkipListener

// ─────────────────────────────────────────────────────────────────────────────
// Internal helpers
// ─────────────────────────────────────────────────────────────────────────────

private enum class SeekDirection { Backward, Forward }

/** Animated play/pause indicator shown in center when tapping. */
@Composable
private fun PlayPauseIndicator(
    playing: Boolean,
    showTrigger: Int,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(showTrigger) {
        if (showTrigger > 0) {
            visible = true
            kotlinx.coroutines.delay(650)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.6f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
            scaleOut(targetScale = 1.15f, animationSpec = tween(180, easing = FastOutLinearInEasing)),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) HikariIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Paused" else "Playing",
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

/**
 * Seek badge shown briefly after a double-tap seek.
 * Dismisses itself after 600ms.
 */
@Composable
private fun SeekBadge(direction: SeekDirection?, onDismiss: () -> Unit) {
    if (direction == null) return
    LaunchedEffect(direction) {
        kotlinx.coroutines.delay(600)
        onDismiss()
    }
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (direction == SeekDirection.Backward) HikariIcons.Replay5 else HikariIcons.Forward5,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (direction == SeekDirection.Backward) "-5s" else "+5s",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun ReelPlayer(
    item: FeedItem,
    player: ExoPlayer,
    isCurrent: Boolean,
    sponsorBlock: SponsorBlockClient,
    playbackRepo: PlaybackRepository,
    onSeen: () -> Unit,
    onToggleSave: () -> Unit,
    onLessLikeThis: () -> Unit,
    onUnplayable: () -> Unit,
    /** Called when the user taps/double-taps so the parent can show its top chrome. */
    onShowControls: () -> Unit = {},
) {
    var playing by remember { mutableStateOf(true) }
    var showTrigger by remember { mutableIntStateOf(0) }

    // ── Controls visibility (auto-hide after 3s when playing) ────────────────
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            kotlinx.coroutines.delay(3_000)
            controlsVisible = false
        }
    }

    // ── Double-tap seek state ────────────────────────────────────────────────
    var showSeekBadge by remember { mutableStateOf<SeekDirection?>(null) }
    var boxWidth by remember { mutableStateOf(0) }

    // ── Error listener ───────────────────────────────────────────────────────
    DisposableEffect(item.videoId, player, isCurrent) {
        if (!isCurrent) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onUnplayable() }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ── Restore / save position ──────────────────────────────────────────────
    LaunchedEffect(item.videoId, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        val savedPos = playbackRepo.getPosition(item.videoId)
        if (savedPos > 0L) player.seekTo(savedPos)
    }
    DisposableEffect(item.videoId, isCurrent) {
        onDispose {
            if (isCurrent) {
                val pos = player.currentPosition
                if (pos > 0L) {
                    kotlinx.coroutines.runBlocking { playbackRepo.savePosition(item.videoId, pos) }
                }
            }
        }
    }

    // ── Mark seen after 3s of playback ──────────────────────────────────────
    var seenFired by remember(item.videoId) { mutableStateOf(false) }
    LaunchedEffect(item.videoId, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        while (!seenFired) {
            kotlinx.coroutines.delay(500)
            if (player.currentPosition >= 3_000L) { seenFired = true; onSeen() }
        }
    }

    // ── SponsorBlock skip ────────────────────────────────────────────────────
    var segments by remember(item.videoId) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    LaunchedEffect(item.videoId) { segments = sponsorBlock.fetchSegments(item.videoId) }
    LaunchedEffect(item.videoId, segments, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(200)
            val pos = player.currentPosition
            val skip = SponsorSkipListener.skipTargetMs(pos, segments)
            if (skip != null && skip > pos) player.seekTo(skip)
        }
    }

    // ── Position tracking ────────────────────────────────────────────────────
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    val wasPlayingBeforeScrub = remember { mutableStateOf(true) }

    LaunchedEffect(item.videoId, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(500)
            if (!isScrubbing) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(1L)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // UI
    // ════════════════════════════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { boxWidth = it.size.width }
            .pointerInput(item.videoId) {
                detectTapGestures(
                    onTap = {
                        // Single tap: toggle play/pause + show controls
                        playing = !playing
                        player.playWhenReady = playing
                        showTrigger++
                        controlsVisible = true
                        onShowControls()
                    },
                    onDoubleTap = { offset ->
                        // Double-tap left/right: seek -5s / +5s
                        val isLeft = offset.x < boxWidth / 2f
                        val delta = if (isLeft) -5_000L else 5_000L
                        val newPos = (player.currentPosition + delta).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        position = newPos.coerceIn(0L, duration)
                        showSeekBadge = if (isLeft) SeekDirection.Backward else SeekDirection.Forward
                        controlsVisible = true
                        onShowControls()
                    },
                    onLongPress = { onToggleSave() },
                )
            },
    ) {
        // ── Task 1: Thumbnail underlay + PlayerView with transparent shutter ──
        // The thumbnail shows through until ExoPlayer renders its first video frame,
        // eliminating the stale-last-frame flash that used to blink on every swipe.
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Transparent shutter: thumbnail shows through until first video frame renders
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // Don't freeze on last frame when player is detached
                    setKeepContentOnPlayerReset(false)
                }
            },
            update = { view ->
                view.player = if (isCurrent) player else null
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Center: play/pause indicator ─────────────────────────────────────
        Box(modifier = Modifier.align(Alignment.Center)) {
            PlayPauseIndicator(playing = playing, showTrigger = showTrigger)
        }

        // ── Double-tap seek badges ────────────────────────────────────────────
        showSeekBadge?.let { dir ->
            val alignment = if (dir == SeekDirection.Backward) Alignment.CenterStart else Alignment.CenterEnd
            Box(modifier = Modifier.align(alignment).padding(24.dp)) {
                SeekBadge(direction = dir, onDismiss = { showSeekBadge = null })
            }
        }

        // ── Task 2 + 3: animated chrome (auto-hides after 3s) ────────────────

        // Bottom gradient — subtler, 180dp, 40% black max
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                        ),
                    ),
            )
        }

        // Channel + title text — fades with controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 52.dp),  // sits above the always-visible scrubber
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = item.channelTitle,
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Always-visible PrecisionScrubber ────────────────────────────────────
        // At rest: thin 2dp line. On touch: expands to 4dp + thumb. Drag anytime,
        // no need to tap-to-reveal first. This matches YouTube/Instagram UX where
        // the progress bar IS the scrubber.
        PrecisionScrubber(
            positionMs = position,
            durationMs = duration,
            onScrubStart = {
                wasPlayingBeforeScrub.value = player.playWhenReady
                player.playWhenReady = false
                isScrubbing = true
                controlsVisible = true
                onShowControls()
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth(),
        )
    }
}
