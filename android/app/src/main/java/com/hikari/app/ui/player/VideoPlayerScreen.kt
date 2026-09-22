package com.hikari.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.NextVideoDto
import com.hikari.app.data.api.dto.VideoDetailDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.PlaybackRepository
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.ui.feed.HikariIcons
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoPlayerEntryPoint {
    fun playerFactory(): HikariPlayerFactory
    fun playbackRepository(): PlaybackRepository
    fun settingsStore(): SettingsStore
    fun localDownloadManager(): com.hikari.app.domain.download.LocalDownloadManager
    fun hikariApi(): HikariApi
}

private const val SEEK_STEP_MS = 10_000L
private const val CHROME_AUTO_HIDE_MS = 4_000L
private const val NEXT_COUNTDOWN_S = 8
private const val TOAST_MS = 1_200L

private enum class Sheet { None, Speed, Tracks, Episodes }

/**
 * Netflix-artiger Player für Serien, Filme und lange Videos.
 *
 *  - Öffnet automatisch im Vollbild: Querformat-Videos drehen ins Querformat,
 *    Hochformat-Videos bleiben hochkant und füllen den Bildschirm.
 *  - Hochformat-Modus (das kann Netflix nicht): Querformat-Video oben, darunter
 *    Titel, Beschreibung und die Folgenliste — bequem einhändig.
 *  - Tipp = Bedienelemente ein/aus. Doppeltipp nur in den äußeren Dritteln
 *    spult ±10 s (stapelbar). Die Mitte ist neutral, damit nichts versehentlich
 *    springt.
 *  - Wischen links = Helligkeit, rechts = Lautstärke. Pinch = Zoom/Original.
 *  - Bildschirmsperre, Tempo, Audio & Untertitel, Folgen, Nächste Folge.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    title: String,
    channel: String,
    onBack: () -> Unit,
    startInLandscape: Boolean = false,
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val scope = rememberCoroutineScope()
    val ioScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val ep = remember { EntryPointAccessors.fromApplication(ctx, VideoPlayerEntryPoint::class.java) }
    val factory = remember { ep.playerFactory() }
    val playbackRepo = remember { ep.playbackRepository() }
    val settingsStore = remember { ep.settingsStore() }
    val localDl = remember { ep.localDownloadManager() }
    val api = remember { ep.hikariApi() }
    val baseUrl = remember { runBlocking { settingsStore.backendUrl.first() } }
    val player = remember { factory.create() }

    KeepScreenOn()

    // ── Zustand ──────────────────────────────────────────────────────────
    var currentVideoId by remember { mutableStateOf(videoId) }
    var currentTitle by remember { mutableStateOf(title) }
    var detail by remember { mutableStateOf<VideoDetailDto?>(null) }
    var episodes by remember { mutableStateOf<List<LibraryVideoDto>>(emptyList()) }

    var playing by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var chromeBumpToken by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var lockHintVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastToken by remember { mutableIntStateOf(0) }

    var orientationMode by remember { mutableStateOf(OrientationMode.Auto) }
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }
    var zoomed by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var tracks by remember { mutableStateOf<Tracks>(Tracks.EMPTY) }

    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    val wasPlayingBeforeScrub = remember { mutableStateOf(true) }

    var nextVideo by remember { mutableStateOf<NextVideoDto?>(null) }
    var nextDismissed by remember { mutableStateOf(false) }
    var showNextOverlay by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(NEXT_COUNTDOWN_S) }

    val seekAccumulator = remember { SeekAccumulator(SEEK_STEP_MS) }
    var seekBadge by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    var seekBadgeToken by remember { mutableIntStateOf(0) }

    var levelKind by remember { mutableStateOf<LevelKind?>(null) }
    var brightness by remember { mutableFloatStateOf(initialBrightness(activity)) }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }
    var levelHideToken by remember { mutableIntStateOf(0) }

    // `startInLandscape` ist historisch: Auto öffnet ohnehin im Vollbild und
    // richtet sich nach dem Videoformat.
    val orientation = resolveOrientation(orientationMode, videoW, videoH)
    val panelMode = showsPortraitPanel(orientation, videoW, videoH)

    fun showChrome() {
        controlsVisible = true
        chromeBumpToken++
    }

    fun showToast(text: String) {
        toast = text
        toastToken++
    }

    fun savePositionAsync(id: String, pos: Long) {
        if (pos > 0L) ioScope.launch { runCatching { playbackRepo.savePosition(id, pos) } }
    }

    fun switchTo(id: String, newTitle: String) {
        val pos = player.currentPosition
        savePositionAsync(currentVideoId, pos)
        player.stop()
        currentVideoId = id
        currentTitle = newTitle
        showNextOverlay = false
        nextDismissed = false
        sheet = Sheet.None
        showChrome()
    }

    fun playNext() {
        val next = nextVideo ?: return
        switchTo(next.id, next.title)
    }

    // ── Laden + Position sichern ─────────────────────────────────────────
    LaunchedEffect(currentVideoId) {
        val savedPos = playbackRepo.getPosition(currentVideoId)
        val localPath = localDl.localFile(currentVideoId)?.absolutePath
        player.setMediaItem(factory.mediaItemFor(baseUrl, currentVideoId, localPath), savedPos)
        player.prepare()
        player.playWhenReady = true
        player.setPlaybackSpeed(speed)
        nextVideo = playbackRepo.nextVideo(currentVideoId)
        nextDismissed = false
        showNextOverlay = false
        val d = runCatching { api.getVideo(currentVideoId) }.getOrNull()
        detail = d
        if (d != null && currentTitle.isBlank()) currentTitle = d.title
        val sid = d?.series_id
        episodes = if (sid != null) {
            runCatching { api.getSeries(sid).videos }.getOrDefault(emptyList())
                .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
        } else emptyList()
    }
    DisposableEffect(Unit) {
        onDispose {
            val pos = player.currentPosition
            val id = currentVideoId
            player.release()
            if (pos > 0L) ioScope.launch { runCatching { playbackRepo.savePosition(id, pos) } }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                    savePositionAsync(currentVideoId, player.currentPosition)
                }
                Lifecycle.Event.ON_RESUME -> { player.playWhenReady = true }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Orientierung + Systemleisten (immer immersiv) ────────────────────
    DisposableEffect(activity, orientation) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = when (orientation) {
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Helligkeit gilt nur im Player; beim Verlassen zurück auf System.
    DisposableEffect(activity) {
        onDispose { activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } } }
    }

    // ── Auto-Hide, Toast, Badges ─────────────────────────────────────────
    LaunchedEffect(chromeBumpToken, controlsVisible, playing, sheet) {
        if (controlsVisible && playing && sheet == Sheet.None) {
            kotlinx.coroutines.delay(CHROME_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(toastToken) {
        if (toast != null) {
            kotlinx.coroutines.delay(TOAST_MS)
            toast = null
        }
    }
    LaunchedEffect(seekBadgeToken) {
        if (seekBadge != null) {
            kotlinx.coroutines.delay(900)
            seekBadge = null
        }
    }
    LaunchedEffect(levelHideToken) {
        if (levelKind != null) {
            kotlinx.coroutines.delay(700)
            levelKind = null
        }
    }
    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            kotlinx.coroutines.delay(2_200)
            lockHintVisible = false
        }
    }

    LaunchedEffect(currentVideoId) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (!isScrubbing) {
                position = player.currentPosition
                buffered = player.bufferedPosition
                duration = player.duration.coerceAtLeast(1L)
            }
        }
    }

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

    // ── Player-Ereignisse ────────────────────────────────────────────────
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && nextVideo != null && !nextDismissed) showNextOverlay = true
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoW = videoSize.width
                    videoH = videoSize.height
                }
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ── Aktionen ─────────────────────────────────────────────────────────
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

    fun onDoubleTap(x: Float, widthPx: Float) {
        when (tapZoneFor(x, widthPx)) {
            TapZone.Left -> {
                seekBy(-SEEK_STEP_MS)
                seekBadge = false to seekAccumulator.tap(forward = false, nowMs = System.currentTimeMillis())
                seekBadgeToken++
            }
            TapZone.Right -> {
                seekBy(SEEK_STEP_MS)
                seekBadge = true to seekAccumulator.tap(forward = true, nowMs = System.currentTimeMillis())
                seekBadgeToken++
            }
            TapZone.Center -> togglePlayPause()
        }
    }

    fun applyBrightness(value: Float) {
        brightness = value
        activity?.window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
        }
    }

    fun applyVolume(value: Float) {
        volume = value
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * maxVolume).roundToInt().coerceIn(0, maxVolume), 0)
    }

    fun setSpeed(s: Float) {
        speed = s
        player.setPlaybackSpeed(s)
        showToast("Tempo ${speedLabel(s)}")
        sheet = Sheet.None
    }

    fun toggleZoom() {
        zoomed = !zoomed
        showToast(if (zoomed) "Bildschirm füllen" else "Originalgröße")
    }

    fun toggleOrientation() {
        orientationMode = if (orientation == PlayerOrientation.Landscape) OrientationMode.Portrait else OrientationMode.Landscape
        sheet = Sheet.None
        showChrome()
    }

    fun toggleLock() {
        locked = !locked
        controlsVisible = !locked
        sheet = Sheet.None
        showToast(if (locked) "Bildschirm gesperrt" else "Entsperrt")
    }

    BackHandler {
        when {
            locked -> lockHintVisible = true
            sheet != Sheet.None -> sheet = Sheet.None
            else -> onBack()
        }
    }

    val audioChoices = remember(tracks) { trackChoices(tracks, C.TRACK_TYPE_AUDIO) }
    val subtitleChoices = remember(tracks) { trackChoices(tracks, C.TRACK_TYPE_TEXT) }
    val hasTrackChoices = audioChoices.size > 1 || subtitleChoices.isNotEmpty()

    fun pickTrack(type: Int, choice: TrackChoice?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (choice == null) {
            params.setTrackTypeDisabled(type, true)
        } else {
            val group = tracks.groups.getOrNull(choice.id.substringBefore(':').toInt()) ?: return
            val index = choice.id.substringAfter(':').toInt()
            params.setTrackTypeDisabled(type, false)
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        player.trackSelectionParameters = params.build()
        sheet = Sheet.None
    }

    // ── Aufbau ───────────────────────────────────────────────────────────
    val seriesLabel = buildString {
        val d = detail
        val se = episodeLabel(d?.season, d?.episode)
        val s = d?.series_title
        if (!s.isNullOrBlank()) append(s)
        if (se.isNotBlank()) { if (isNotEmpty()) append(" · "); append(se) }
        if (isEmpty() && channel.isNotBlank()) append(channel)
    }

    val surface: @Composable BoxScope.() -> Unit = {
        var boxWidthPx by remember { mutableIntStateOf(0) }
        var boxHeightPx by remember { mutableIntStateOf(0) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { boxWidthPx = it.size.width; boxHeightPx = it.size.height }
                .pointerInput(currentVideoId, locked) {
                    detectPlayerGestures(
                        onTap = {
                            if (locked) {
                                lockHintVisible = !lockHintVisible
                            } else if (sheet != Sheet.None) {
                                sheet = Sheet.None
                            } else {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) chromeBumpToken++
                            }
                        },
                        onDoubleTap = { offset -> if (!locked) onDoubleTap(offset.x, boxWidthPx.toFloat()) },
                        onVerticalDragStart = { side ->
                            if (!locked) {
                                levelKind = if (side == DragSide.Left) LevelKind.Brightness else LevelKind.Volume
                            }
                        },
                        onVerticalDrag = { side, dy ->
                            if (locked) return@detectPlayerGestures
                            val h = boxHeightPx.toFloat()
                            if (side == DragSide.Left) applyBrightness(adjustLevel(brightness, dy, h))
                            else applyVolume(adjustLevel(volume, dy, h))
                        },
                        onVerticalDragEnd = { levelHideToken++ },
                        onPinch = { scale ->
                            if (locked) return@detectPlayerGestures
                            val wantZoom = pinchDecision(scale) ?: return@detectPlayerGestures
                            if (wantZoom != zoomed) toggleZoom()
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
                update = { view ->
                    view.player = player
                    view.resizeMode = if (zoomed) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )

            val chrome = controlsVisible && !locked && sheet == Sheet.None

            // Mitte: Replay / Play-Pause / Forward
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(if (panelMode) 28.dp else 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleControl(Icons.Default.Replay10, "10 Sekunden zurück", if (panelMode) 46.dp else 56.dp, 26.dp) { seekBy(-SEEK_STEP_MS); showChrome() }
                    CircleControl(
                        if (playing) HikariIcons.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Wiedergabe",
                        if (panelMode) 62.dp else 78.dp, 40.dp,
                    ) { togglePlayPause() }
                    CircleControl(Icons.Default.Forward10, "10 Sekunden vor", if (panelMode) 46.dp else 56.dp, 26.dp) { seekBy(SEEK_STEP_MS); showChrome() }
                }
            }

            // Oben: Scrim + Zurück + Titel + Drehen
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(if (panelMode) 90.dp else 140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))),
                )
            }
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .then(if (panelMode) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleControl(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", 40.dp, 22.dp, Color.Black.copy(alpha = 0.45f), onBack)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (seriesLabel.isNotBlank()) {
                            Text(
                                text = seriesLabel.uppercase(),
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
                    CircleControl(
                        if (zoomed) Icons.Outlined.FitScreen else Icons.Outlined.ZoomOutMap,
                        if (zoomed) "Originalgröße" else "Bildschirm füllen",
                        40.dp, 20.dp, Color.Black.copy(alpha = 0.45f),
                    ) { toggleZoom(); showChrome() }
                    Spacer(Modifier.width(8.dp))
                    CircleControl(
                        Icons.Outlined.ScreenRotation,
                        if (orientation == PlayerOrientation.Landscape) "Hochformat" else "Querformat",
                        40.dp, 20.dp, Color.Black.copy(alpha = 0.45f),
                    ) { toggleOrientation() }
                }
            }

            // Unten: Scrim + Leiste + Aktionen
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(if (panelMode) 110.dp else 170.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
                )
            }
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .then(if (panelMode) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout)),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = if (panelMode) 12.dp else 24.dp)) {
                    PlayerSeekBar(
                        positionMs = position,
                        durationMs = duration,
                        bufferedMs = buffered,
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
                    )
                    if (!panelMode) {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BottomAction(LockIcon, "Sperren", { toggleLock() })
                            if (episodes.isNotEmpty()) BottomAction(Icons.Outlined.PlaylistPlay, "Folgen", { sheet = Sheet.Episodes })
                            BottomAction(Icons.Outlined.Speed, "Tempo (${speedLabel(speed)})", { sheet = Sheet.Speed })
                            if (hasTrackChoices) BottomAction(Icons.Outlined.Subtitles, "Audio & Untertitel", { sheet = Sheet.Tracks })
                            if (nextVideo != null) BottomAction(Icons.Outlined.SkipNext, "Nächste Folge", { playNext() })
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Sperre: kleiner Knopf oben rechts, wenn gesperrt und Hinweis sichtbar
            LockedHint(
                visible = locked && lockHintVisible,
                onUnlock = { toggleLock() },
                modifier = Modifier.align(Alignment.Center),
            )

            // Doppeltipp-Badge
            seekBadge?.let { (forward, total) ->
                StackedSeekBadge(
                    forward = forward,
                    totalMs = total,
                    modifier = Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 36.dp),
                )
            }

            // Pegel
            LevelIndicator(
                kind = levelKind,
                level = if (levelKind == LevelKind.Brightness) brightness else volume,
                modifier = Modifier.align(if (levelKind == LevelKind.Brightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 28.dp),
            )

            PlayerToast(toast, modifier = Modifier.align(Alignment.TopCenter).padding(top = if (panelMode) 56.dp else 72.dp))

            // Nächste Folge
            val next = nextVideo
            if (showNextOverlay && next != null && !locked) {
                NextEpisodeCard(
                    next = next,
                    countdown = nextCountdown,
                    onCancel = { nextDismissed = true; showNextOverlay = false },
                    onPlayNow = { playNext() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (panelMode) 24.dp else 120.dp),
                )
            }

            // Auswahl-Flächen (Tempo / Spuren / Folgen) als eingebettete Panels
            if (!panelMode && sheet != Sheet.None) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { sheet = Sheet.None }) },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HikariBg)
                        .pointerInput(Unit) { detectTapGestures(onTap = { }) },
                ) {
                    when (sheet) {
                        Sheet.Speed -> SpeedChooser(speed, ::setSpeed)
                        Sheet.Tracks -> TracksChooser(
                            audio = audioChoices,
                            subtitles = subtitleChoices,
                            onPickAudio = { pickTrack(C.TRACK_TYPE_AUDIO, it) },
                            onPickSubtitle = { pickTrack(C.TRACK_TYPE_TEXT, it) },
                        )
                        Sheet.Episodes -> Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
                            Text(
                                detail?.series_title ?: "Folgen",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                            EpisodeList(
                                episodes = episodes,
                                currentId = currentVideoId,
                                onPick = { ep -> switchTo(ep.id, ep.title) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Sheet.None -> Unit
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        if (!panelMode) {
            Box(Modifier.fillMaxSize(), content = surface)
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .aspectRatio(if (videoW > 0 && videoH > 0) videoW.toFloat() / videoH else 16f / 9f),
                    content = surface,
                )
                PortraitPanel(
                    title = currentTitle,
                    seriesLabel = seriesLabel,
                    description = detail?.description,
                    speed = speed,
                    hasTracks = hasTrackChoices,
                    locked = locked,
                    next = nextVideo,
                    episodes = episodes,
                    currentId = currentVideoId,
                    sheet = sheet,
                    audio = audioChoices,
                    subtitles = subtitleChoices,
                    onSheet = { sheet = if (sheet == it) Sheet.None else it },
                    onSpeed = ::setSpeed,
                    onPickAudio = { pickTrack(C.TRACK_TYPE_AUDIO, it) },
                    onPickSubtitle = { pickTrack(C.TRACK_TYPE_TEXT, it) },
                    onLock = { toggleLock() },
                    onNext = { playNext() },
                    onPickEpisode = { ep -> switchTo(ep.id, ep.title) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

// ── Hochformat-Panel ─────────────────────────────────────────────────────────

@Composable
private fun PortraitPanel(
    title: String,
    seriesLabel: String,
    description: String?,
    speed: Float,
    hasTracks: Boolean,
    locked: Boolean,
    next: NextVideoDto?,
    episodes: List<LibraryVideoDto>,
    currentId: String,
    sheet: Sheet,
    audio: List<TrackChoice>,
    subtitles: List<TrackChoice>,
    onSheet: (Sheet) -> Unit,
    onSpeed: (Float) -> Unit,
    onPickAudio: (TrackChoice) -> Unit,
    onPickSubtitle: (TrackChoice?) -> Unit,
    onLock: () -> Unit,
    onNext: () -> Unit,
    onPickEpisode: (LibraryVideoDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(HikariBg)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (seriesLabel.isNotBlank()) {
                Text(
                    seriesLabel.uppercase(),
                    color = HikariAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                title.ifBlank { "Wird geladen…" },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomAction(if (locked) UnlockIcon else LockIcon, if (locked) "Entsperren" else "Sperren", onLock)
                BottomAction(Icons.Outlined.Speed, speedLabel(speed), { onSheet(Sheet.Speed) }, tint = if (sheet == Sheet.Speed) HikariAmber else Color.White)
                if (hasTracks) BottomAction(Icons.Outlined.Subtitles, "Audio", { onSheet(Sheet.Tracks) }, tint = if (sheet == Sheet.Tracks) HikariAmber else Color.White)
                Spacer(Modifier.weight(1f))
                if (next != null) BottomAction(Icons.Outlined.SkipNext, "Nächste", onNext)
            }
        }
        when (sheet) {
            Sheet.Speed -> SpeedChooser(speed, onSpeed)
            Sheet.Tracks -> TracksChooser(audio, subtitles, onPickAudio, onPickSubtitle)
            else -> Unit
        }
        if (episodes.isNotEmpty()) {
            Text(
                "Folgen",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            EpisodeList(
                episodes = episodes,
                currentId = currentId,
                onPick = onPickEpisode,
                modifier = Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.navigationBars),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ── Hilfen ───────────────────────────────────────────────────────────────────

private fun initialBrightness(activity: Activity?): Float {
    val override = activity?.window?.attributes?.screenBrightness ?: -1f
    if (override >= 0f) return override
    return runCatching {
        Settings.System.getInt(activity?.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.6f).coerceIn(0f, 1f)
}

@OptIn(UnstableApi::class)
private fun trackChoices(tracks: Tracks, type: Int): List<TrackChoice> {
    val out = mutableListOf<TrackChoice>()
    tracks.groups.forEachIndexed { gi, group ->
        if (group.type != type) return@forEachIndexed
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.GERMAN).ifBlank { it } }
            val label = listOfNotNull(f.label?.takeIf { it.isNotBlank() }, lang).distinct().joinToString(" · ")
                .ifBlank { if (type == C.TRACK_TYPE_AUDIO) "Ton ${out.size + 1}" else "Spur ${out.size + 1}" }
            out += TrackChoice(id = "$gi:$i", label = label, selected = group.isTrackSelected(i))
        }
    }
    return out
}

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
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (next.thumbnailUrl != null) {
            AsyncImage(
                model = next.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("NÄCHSTE FOLGE", color = HikariAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(2.dp))
            val se = episodeLabel(next.season, next.episode)
            Text(
                text = if (se.isNotEmpty()) "$se — ${next.title}" else next.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text("in $countdown s", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Abbrechen") }
                Button(onClick = onPlayNow) { Text("Jetzt abspielen") }
            }
        }
    }
}

@Composable
private fun CircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    background: Color = Color.Black.copy(alpha = 0.55f),
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = HikariText, modifier = Modifier.size(iconSize))
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
