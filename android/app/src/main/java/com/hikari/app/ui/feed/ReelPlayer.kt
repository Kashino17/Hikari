package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
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
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SegmentCategories
import com.hikari.app.data.sponsor.SegmentCategory
import com.hikari.app.data.sponsor.SponsorBlockClient
import com.hikari.app.data.sponsor.SponsorSegment
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.SponsorSkipListener
import com.hikari.app.ui.player.PlayPauseIndicator
import com.hikari.app.ui.player.SeekBadge
import com.hikari.app.ui.player.SeekDirection
import kotlinx.coroutines.launch

// Hikari's bottom-tab-bar (Bibliothek/Feed/Manga/Profil) sits inside the
// nav-bar inset zone and is ~72dp tall. We add 16dp breathing room so the
// scrubber doesn't visually touch the tab labels — feels like YouTube
// Shorts / Instagram Reels where the progress bar floats clearly above
// system UI.
private val FeedBottomBarClearance = 88.dp

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun ReelPlayer(
    item: FeedItem,
    player: ExoPlayer,
    isCurrent: Boolean,
    fullscreen: Boolean,
    sponsorBlock: SponsorBlockClient,
    playbackRepo: PlaybackRepository,
    sponsorBlockPrefs: SponsorBlockPrefs,
    onSeen: () -> Unit,
    onToggleSave: () -> Unit,
    onLessLikeThis: () -> Unit,
    onUnplayable: () -> Unit,
    onToggleFullscreen: () -> Unit,
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

    // ── First-frame tracking + error listener ────────────────────────────────
    // Thumbnail is shown until the player renders its first frame for this item.
    // Keyed on videoId so a re-visit (swipe back) re-arms the gate.
    var firstFrameRendered by remember(item.videoId) { mutableStateOf(false) }
    DisposableEffect(item.videoId, player, isCurrent) {
        if (!isCurrent) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onUnplayable() }
            override fun onRenderedFirstFrame() { firstFrameRendered = true }
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

    val behaviors by sponsorBlockPrefs.behaviors.collectAsState(
        initial = SegmentCategories.all.associate { it.apiKey to it.defaultBehavior }
    )

    var manualSegmentCategory by remember(item.videoId) {
        mutableStateOf<SegmentCategory?>(null)
    }
    var manualTargetMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.videoId, segments, isCurrent, behaviors) {
        if (!isCurrent) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(200)
            val pos = player.currentPosition
            when (val decision = SponsorSkipListener.evaluate(pos, segments, behaviors)) {
                is SponsorSkipListener.Decision.Auto -> {
                    if (decision.targetMs > pos) {
                        val saved = decision.targetMs - pos
                        player.seekTo(decision.targetMs)
                        scope.launch { sponsorBlockPrefs.recordSkip(saved) }
                        if (manualSegmentCategory != null) manualSegmentCategory = null
                    }
                }
                is SponsorSkipListener.Decision.Manual -> {
                    val category = SegmentCategories.byKey(decision.segment.category)
                    if (manualSegmentCategory?.apiKey != category?.apiKey) {
                        manualSegmentCategory = category
                        manualTargetMs = decision.targetMs
                    }
                }
                SponsorSkipListener.Decision.None -> {
                    if (manualSegmentCategory != null) manualSegmentCategory = null
                }
            }
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

    // ── Caption position — 20fps poll for smooth word-sync ──────────────────
    var playerPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(item.videoId, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(50)
            playerPositionMs = player.currentPosition
        }
    }

    // "Nächste Folge"-Button macht im Feed keinen Sinn (Feed ist scroll-basiert,
    // nicht episodisch). Permanently off; the mechanism stays available for
    // the Library/Series-Detail-Screen which uses a different player.
    val showNextEpisode = false

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
        // ── Thumbnail underlay + PlayerView with opaque letterbox ────────────
        // Thumbnail is only shown for non-current pages or while the current page
        // is still loading its first frame. This prevents the thumbnail from
        // bleeding through the player's letterbox bars when the thumbnail's
        // aspect ratio differs from the video's (common with YouTube thumbnails).
        if (!isCurrent || !firstFrameRendered) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // PlayerView only mounts for the current page — non-current pages show
        // the thumbnail above and don't need a Surface in the tree. Avoids
        // multiple PlayerViews fighting over the shared ExoPlayer surface.
        if (isCurrent) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        // Opaque black letterbox bars — no thumbnail bleed-through
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(false)
                    }
                },
                update = { view -> view.player = player },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

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

        // ── Manual-skip pill (SponsorBlock SKIP_MANUAL) ───────────────────────
        ManualSkipPill(
            category = manualSegmentCategory,
            onSkip = {
                val target = manualTargetMs
                if (target > 0L && isCurrent) {
                    val pos = player.currentPosition
                    val saved = (target - pos).coerceAtLeast(0L)
                    player.seekTo(target)
                    scope.launch { sponsorBlockPrefs.recordSkip(saved) }
                    manualSegmentCategory = null
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (fullscreen) 100.dp else 190.dp),
        )

        // ── Next Episode button ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = showNextEpisode && isCurrent,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (fullscreen) 100.dp else 190.dp)
        ) {
            Button(
                onClick = { /* TODO: next item in pager */ },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text("NÄCHSTE FOLGE", fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
            }
        }

        // ── Task 2 + 3: animated chrome (auto-hides after 3s) ────────────────

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 16.dp, bottom = if (fullscreen) 28.dp else 140.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                    // clickable consumes the tap reliably; the outer Box's
                    // detectTapGestures cannot then also fire (which would
                    // re-show the scrubber and swallow the toggle).
                    .clickable(onClick = onToggleFullscreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (fullscreen) HikariIcons.FullscreenExit else HikariIcons.Fullscreen,
                    contentDescription = if (fullscreen) "Vollbild beenden" else "Vollbild",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Subtle bottom gradient — 120dp tall, max 60% black at very bottom only
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )
        }

        // Channel meta-line + title — minimal, matches /feed mock
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (fullscreen) 40.dp else 120.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.channelTitle.uppercase(),
                        color = com.hikari.app.ui.theme.HikariAmber,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.title,
                    color = Color.White.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Context overlay — first 6 seconds, then fades out ────────────────
        ContextOverlay(
            context = item.context,
            isCurrent = isCurrent,
            positionMs = playerPositionMs,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 56.dp),  // leave space for the top counter "01/05" + Alle/Gespeichert/Archiv pills
        )

        // ── Caption overlay — YouTube-Shorts-style word sync ─────────────────
        CaptionOverlay(
            captions = item.captions,
            positionMs = playerPositionMs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (fullscreen) 100.dp else 195.dp),
        )

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
                .padding(bottom = if (fullscreen) 20.dp else FeedBottomBarClearance)
                .fillMaxWidth(),
        )
    }
}
