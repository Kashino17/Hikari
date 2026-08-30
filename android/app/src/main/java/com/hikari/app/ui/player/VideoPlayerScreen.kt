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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.NextVideoDto
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
    fun localDownloadManager(): com.hikari.app.domain.download.LocalDownloadManager
}

private const val SEEK_STEP_MS = 10_000L
private const val CHROME_AUTO_HIDE_MS = 4_000L
private const val NEXT_COUNTDOWN_S = 8

/**
 * Standalone fullscreen video player — opened from the Library hero
 * "Abspielen" CTA. Owns its own ExoPlayer (separate from the Feed's shared
 * player) so a Library playback doesn't disturb the feed queue.
 *
 * Tap behaviour matches Netflix / YouTube full-screen:
 *   - single tap   = toggle chrome (the controls overlay)
 *   - double tap L = -10s
 *   - double tap R = +10s
 *   - tap on play  = actually toggles play/pause
 *
 * Keeps the screen awake while the screen is on the back stack.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    title: String,
    channel: String,
    onBack: () -> Unit,
    // When true (e.g. opened via "Original ansehen" from the feed), the screen
    // starts in landscape immersive mode. User can still toggle back via the
    // fullscreen button. Defaults to false for normal Library/Series-Detail flow.
    startInLandscape: Boolean = false,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

    val ep = remember {
        EntryPointAccessors.fromApplication(ctx, VideoPlayerEntryPoint::class.java)
    }
    val factory = remember { ep.playerFactory() }
    val playbackRepo = remember { ep.playbackRepository() }
    val settingsStore = remember { ep.settingsStore() }
    val localDl = remember { ep.localDownloadManager() }
    val baseUrl = remember { runBlocking { settingsStore.backendUrl.first() } }
    val player = remember { factory.create() }

    KeepScreenOn()

    var landscape by remember { mutableStateOf(startInLandscape) }
    var playing by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var chromeBumpToken by remember { mutableIntStateOf(0) } // resets the auto-hide timer

    fun showChrome() {
        controlsVisible = true
        chromeBumpToken++
    }

    // ── Nächste Folge ────────────────────────────────────────────────────
    // Aktuelles Video als State: beim Sprung zur nächsten Folge bleibt der
    // Screen (und sein Player) bestehen, nur das MediaItem wechselt.
    var currentVideoId by remember { mutableStateOf(videoId) }
    var currentTitle by remember { mutableStateOf(title) }
    var nextVideo by remember { mutableStateOf<NextVideoDto?>(null) }
    var nextDismissed by remember { mutableStateOf(false) }
    var showNextOverlay by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(NEXT_COUNTDOWN_S) }

    fun playNext() {
        val next = nextVideo ?: return
        if (player.currentPosition > 0) {
            runBlocking { playbackRepo.savePosition(currentVideoId, player.currentPosition) }
        }
        player.stop()
        val nextLocalPath = runBlocking { localDl.localFile(next.id)?.absolutePath }
        player.setMediaItem(factory.mediaItemFor(baseUrl, next.id, nextLocalPath), 0L)
        player.prepare()
        player.playWhenReady = true
        currentVideoId = next.id
        currentTitle = next.title
        showNextOverlay = false
        nextDismissed = false
    }

    // ── Lifecycle: prepare once, save position on dispose ────────────────────
    LaunchedEffect(currentVideoId) {
        val savedPos = playbackRepo.getPosition(currentVideoId)
        val localPath = localDl.localFile(currentVideoId)?.absolutePath
        player.setMediaItem(factory.mediaItemFor(baseUrl, currentVideoId, localPath), savedPos)
        player.prepare()
        player.playWhenReady = true
        nextVideo = playbackRepo.nextVideo(currentVideoId)
        nextDismissed = false
        showNextOverlay = false
    }
    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            if (pos > 0L) runBlocking { playbackRepo.savePosition(currentVideoId, pos) }
            player.release()
        }
    }

    // Pause + flush position when backgrounded so audio never plays off-screen
    // and the resume point survives a process kill; resume on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                    val pos = player.currentPosition
                    if (pos > 0L) runBlocking { playbackRepo.savePosition(currentVideoId, pos) }
                }
                Lifecycle.Event.ON_RESUME -> { player.playWhenReady = true }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // ── Auto-hide chrome — restarts when chromeBumpToken changes ─────────────
    LaunchedEffect(chromeBumpToken, controlsVisible, playing) {
        if (controlsVisible && playing) {
            kotlinx.coroutines.delay(CHROME_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // ── Position polling ─────────────────────────────────────────────────────
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    val wasPlayingBeforeScrub = remember { mutableStateOf(true) }
    LaunchedEffect(currentVideoId) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (!isScrubbing) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(1L)
            }
        }
    }

    // Countdown fürs „Nächste Folge"-Overlay: 8 s, dann automatisch weiter.
    LaunchedEffect(showNextOverlay) {
        if (showNextOverlay) {
            nextCountdown = NEXT_COUNTDOWN_S
            while (nextCountdown > 0) {
                kotlinx.coroutines.delay(1_000)
                nextCountdown--
            }
            playNext()
        }
    }

    // ── Transient seek badge after a double-tap ──────────────────────────────
    var seekBadge by remember { mutableStateOf<SeekDir?>(null) }
    LaunchedEffect(seekBadge) {
        if (seekBadge != null) {
            kotlinx.coroutines.delay(650)
            seekBadge = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val newPos = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(newPos)
        position = newPos
    }

    fun togglePlayPause() {
        playing = !playing
        player.playWhenReady = playing
        showChrome()
    }

    var boxWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg)
            .onGloballyPositioned { boxWidthPx = it.size.width }
            .pointerInput(currentVideoId) {
                detectTapGestures(
                    onTap = {
                        // Netflix-style: a tap *only* toggles the chrome,
                        // never pause/play. Pause lives on the centre button.
                        controlsVisible = !controlsVisible
                        if (controlsVisible) chromeBumpToken++
                    },
                    onDoubleTap = { offset ->
                        val isLeft = boxWidthPx > 0 && offset.x < boxWidthPx / 2f
                        if (isLeft) {
                            seekBy(-SEEK_STEP_MS)
                            seekBadge = SeekDir.Backward
                        } else {
                            seekBy(SEEK_STEP_MS)
                            seekBadge = SeekDir.Forward
                        }
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

        // Centre transport controls (Replay10 / Play-Pause / Forward10) ──────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl(
                    icon = Icons.Default.Replay10,
                    contentDescription = "10 Sekunden zurück",
                    size = 56.dp,
                    iconSize = 28.dp,
                    onClick = {
                        seekBy(-SEEK_STEP_MS)
                        showChrome()
                    },
                )
                CircleControl(
                    icon = if (playing) HikariIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Wiedergabe",
                    size = 76.dp,
                    iconSize = 42.dp,
                    onClick = { togglePlayPause() },
                )
                CircleControl(
                    icon = Icons.Default.Forward10,
                    contentDescription = "10 Sekunden vor",
                    size = 56.dp,
                    iconSize = 28.dp,
                    onClick = {
                        seekBy(SEEK_STEP_MS)
                        showChrome()
                    },
                )
            }
        }

        // Top scrim + back arrow + title ─────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
        ) {
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
                CircleControl(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    size = 40.dp,
                    iconSize = 22.dp,
                    background = Color.Black.copy(alpha = 0.45f),
                    onClick = onBack,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
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
                        text = currentTitle.ifBlank { "Wird geladen…" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (nextVideo != null) {
                    CircleControl(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Nächste Folge",
                        size = 40.dp,
                        iconSize = 20.dp,
                        background = Color.Black.copy(alpha = 0.45f),
                        onClick = { playNext() },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                CircleControl(
                    icon = if (landscape) HikariIcons.FullscreenExit else HikariIcons.Fullscreen,
                    contentDescription = if (landscape) "Vollbild beenden" else "Vollbild",
                    size = 40.dp,
                    iconSize = 20.dp,
                    background = Color.Black.copy(alpha = 0.45f),
                    onClick = {
                        landscape = !landscape
                        showChrome()
                    },
                )
            }
        }

        // Bottom scrim + scrubber ────────────────────────────────────────────
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
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Double-tap badges (always above chrome, fades quickly) ─────────────
        seekBadge?.let { dir ->
            val align = if (dir == SeekDir.Backward) Alignment.CenterStart else Alignment.CenterEnd
            Box(modifier = Modifier.align(align).padding(horizontal = 32.dp)) {
                SeekBadge(dir)
            }
        }

        // „Nächste Folge"-Overlay — unabhängig vom Chrome sichtbar ──────────
        val next = nextVideo
        if (showNextOverlay && next != null) {
            NextEpisodeCard(
                next = next,
                countdown = nextCountdown,
                onCancel = {
                    nextDismissed = true
                    showNextOverlay = false
                },
                onPlayNow = { playNext() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 96.dp),
            )
        }
    }

    // Mirror player state into our `playing` flag (handles end-of-stream too)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && nextVideo != null && !nextDismissed) {
                    showNextOverlay = true
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

private enum class SeekDir { Backward, Forward }

@Composable
private fun NextEpisodeCard(
    next: NextVideoDto,
    countdown: Int,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (next.thumbnailUrl != null) {
            AsyncImage(
                model = next.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(96.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NÄCHSTE FOLGE",
                color = HikariAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(2.dp))
            val se = buildString {
                if (next.season != null) append("S${next.season} ")
                if (next.episode != null) append("F${next.episode}")
            }.trim()
            Text(
                text = if (se.isNotEmpty()) "$se — ${next.title}" else next.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "in $countdown s",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Abbrechen") }
                Button(onClick = onPlayNow) { Text("Jetzt abspielen") }
            }
        }
    }
}

@Composable
private fun SeekBadge(dir: SeekDir) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (dir == SeekDir.Backward) Icons.Default.Replay10 else Icons.Default.Forward10,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (dir == SeekDir.Backward) "−10s" else "+10s",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    background: Color = Color.Black.copy(alpha = 0.55f),
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = HikariText,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Keeps the screen awake while this composable is in the composition.
 * Useful for video players and (future) manga readers.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
