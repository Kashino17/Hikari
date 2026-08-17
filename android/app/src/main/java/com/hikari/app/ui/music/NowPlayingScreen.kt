package com.hikari.app.ui.music

import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val controller = viewModel.player
    val song by controller.currentSong.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val isBuffering by controller.isBuffering.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()
    val error by controller.error.collectAsState()
    val videoMode by controller.videoMode.collectAsState()

    // Nichts spielend (z. B. Prozess-Neustart) — nichts anzuzeigen. Der
    // Sprung zurück gehört in einen Effekt, nicht mitten in die Composition.
    LaunchedEffect(song) {
        if (song == null) onBack()
    }
    val current = song ?: return
    val isFavorite = current.videoId in viewModel.favoriteIds
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloaded = current.videoId in downloadedIds

    // Swipe-down-to-close: der ganze Screen folgt dem Finger nach unten und
    // schließt ab 30 % Höhe oder bei schnellem Wisch — wie bei den großen Playern.
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    var screenH by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { screenH = it.height.toFloat() }
            .graphicsLayer {
                translationY = dragY.value
                alpha = 1f - (dragY.value / screenH.coerceAtLeast(1f)) * 0.35f
            }
            .pointerInput(Unit) {
                var lastDelta = 0f
                detectVerticalDragGestures(
                    onDragStart = { lastDelta = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        lastDelta = amount
                        val next = (dragY.value + amount).coerceAtLeast(0f)
                        scope.launch { dragY.snapTo(next) }
                    },
                    onDragEnd = {
                        if (dragY.value > screenH * 0.3f || lastDelta > 45f) {
                            onBack()
                        } else {
                            scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f)) }
                    },
                )
            },
    ) {
        // Ambient-Ebene: weichgezeichnetes Artwork + Scrim statt flachem Grund.
        MuArtworkBackdrop(current.thumbnailUrl.ifEmpty { null })

        Column(
            Modifier.fillMaxSize().statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuIconButton(
                    Icons.Default.KeyboardArrowDown, "Schließen",
                    tint = HikariText, iconSize = 28.dp,
                ) { onBack() }
                Text(
                    if (isDownloaded) "Läuft gerade · offline" else "Läuft gerade",
                    fontSize = 13.sp,
                    color = HikariTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                // Audio ↔ Video: bei Podcasts/True Crime läuft das Bild
                // nahtlos an der aktuellen Position weiter.
                MuIconButton(
                    if (videoMode) Icons.Outlined.Audiotrack else Icons.Outlined.SmartDisplay,
                    if (videoMode) "Zur Audio-Ansicht" else "Zur Video-Ansicht",
                    active = videoMode,
                ) { controller.toggleVideoMode() }
            }

            // Cover + Titel + Aktionen sitzen als Block MITTIG zwischen
            // Kopfzeile und Slider (dehnbarer Raum oben UND unten) — kein
            // Riesenloch mehr unter den Aktionen.
            Spacer(Modifier.weight(1f))

            // Quellmaterial sind YouTube-Thumbnails (16:9) — ein quadratischer
            // Zuschnitt würde die Seiten abstutzen, deshalb volle Breite in 16:9.
            // Beim Songwechsel schiebt sich das neue Cover federnd ins Bild.
            val coverAnim = remember { Animatable(1f) }
            LaunchedEffect(current.videoId) {
                coverAnim.snapTo(0.94f)
                coverAnim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 380f))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        scaleX = coverAnim.value
                        scaleY = coverAnim.value
                        alpha = ((coverAnim.value - 0.94f) / 0.06f).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(HikariSurfaceHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (videoMode) {
                    // TextureView statt SurfaceView: respektiert das Compose-
                    // Clipping der runden Karte. Der Player rendert direkt hinein.
                    val ctx = LocalContext.current
                    val textureView = remember { TextureView(ctx) }
                    AndroidView(factory = { textureView }, modifier = Modifier.fillMaxSize())
                    DisposableEffect(Unit) {
                        val p = controller.playerForSession()
                        p.setVideoTextureView(textureView)
                        onDispose { p.clearVideoTextureView(textureView) }
                    }
                    if (isBuffering) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = HikariText,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                } else if (current.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = current.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = HikariTextFaint, modifier = Modifier.size(80.dp))
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                current.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HikariText,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(8.dp))

            // Interpreten: bei Kollaborationen eine Pille PRO Artist (jede
            // öffnet die eigene Seite), sonst die gewohnte Einzel-Pille.
            if (current.artists.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 28.dp),
                ) {
                    current.artists.forEach { a ->
                        ArtistPill(
                            name = a.name,
                            clickable = a.channelId != null,
                            onClick = { a.channelId?.let { onOpenArtist(it, a.name) } },
                        )
                    }
                }
            } else {
                val artistChannelId = current.uploaderUrl.substringAfterLast("/", "")
                if (artistChannelId.isNotBlank()) {
                    ArtistPill(
                        name = current.uploader,
                        clickable = true,
                        onClick = { onOpenArtist(artistChannelId, current.uploader) },
                    )
                } else {
                    Text(current.uploader, fontSize = 14.sp, color = HikariTextMuted, maxLines = 1)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Song-Aktionen als beschriftete Pillen statt anonymer Icons oben.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuActionPill(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (isFavorite) "Gemerkt" else "Favorit",
                    active = isFavorite,
                    activeColor = Color(0xFFFF5252),
                ) { viewModel.toggleFavorite(current) }

                val progress = downloadProgress[current.videoId]
                MuActionPill(
                    icon = if (isDownloaded) Icons.Outlined.OfflinePin else Icons.Outlined.CloudDownload,
                    label = when {
                        isDownloaded -> "Offline ✓"
                        progress != null && progress > 0f -> "✕ ${(progress * 100).toInt()} %"
                        progress != null -> "✕ Lädt …"
                        else -> "Laden"
                    },
                    active = isDownloaded,
                ) {
                    when {
                        isDownloaded -> viewModel.deleteDownload(current.videoId)
                        // Läuft schon: Tipp bricht den Download ab
                        progress != null -> viewModel.cancelDownload(current.videoId)
                        else -> viewModel.downloadSong(current)
                    }
                }

                MuActionPill(
                    icon = Icons.Default.PlaylistAdd,
                    label = "Playlist",
                    active = false,
                ) { viewModel.addToPlaylistTarget = current }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = Color(0xFFF87171),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF87171).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFF87171).copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            // Eigenes Composable: der 500-ms-positionMs-Tick recomposed nur diese
            // Sektion, nicht den ganzen Screen inklusive Cover-AsyncImage.
            SeekSection(controller)

            Spacer(Modifier.height(14.dp))

            // --- transport controls ---
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuIconButton(
                    Icons.Default.Shuffle, "Zufallswiedergabe",
                    active = shuffle,
                ) { controller.toggleShuffle() }
                MuIconButton(
                    Icons.Default.SkipPrevious, "Zurück",
                    tint = HikariText, iconSize = 38.dp, touchSize = 56.dp,
                ) { controller.previous() }
                MuPlayButton(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playIcon = Icons.Default.PlayArrow,
                    pauseIcon = Icons.Default.Pause,
                    size = 72.dp,
                ) { controller.toggle() }
                MuIconButton(
                    Icons.Default.SkipNext, "Weiter",
                    tint = HikariText, iconSize = 38.dp, touchSize = 56.dp,
                ) { controller.next() }
                MuIconButton(
                    if (repeatMode == MusicPlayerController.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Wiederholen",
                    active = repeatMode != MusicPlayerController.REPEAT_OFF,
                ) { controller.cycleRepeat() }
            }

            Spacer(Modifier.height(36.dp))
        }
    }

    viewModel.addToPlaylistTarget?.let { song ->
        AddToPlaylistSheet(
            song = song,
            playlists = viewModel.playlists,
            onDismiss = { viewModel.addToPlaylistTarget = null },
            onSelect = { playlistId -> viewModel.addToPlaylist(playlistId, song) },
            onCreate = { name -> viewModel.createPlaylist(name, addAfterwards = song) },
        )
    }
}


/** Interpret-Pille — klickbar mit Chevron, wenn eine Kanal-Seite existiert. */
@Composable
private fun ArtistPill(name: String, clickable: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HikariCardBg.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .then(if (clickable) Modifier.muPressable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 13.sp, color = HikariTextMuted, maxLines = 1)
        if (clickable) {
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.ChevronRight, null, tint = HikariTextFaint, modifier = Modifier.size(16.dp))
        }
    }
}

/** Eigener Seekbar + Zeitangaben — hört allein auf positionMs/durationMs,
 *  damit der 500-ms-Tick nicht den ganzen Screen (inkl. Cover) recomposed.
 *  Beim Ziehen zeigt die linke Zeit die Vorschau-Position. */
@Composable
private fun SeekSection(controller: MusicPlayerController) {
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    var previewFrac by remember { mutableStateOf<Float?>(null) }

    MuSeekBar(
        progress = if (duration > 0) position.toFloat() / duration else 0f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        onSeekPreview = { previewFrac = it },
        onSeek = { frac ->
            if (duration > 0) controller.seekTo((frac * duration).toLong())
            previewFrac = null
        },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        val preview = previewFrac
        Text(
            formatDurationMs(if (preview != null && duration > 0) (preview * duration).toLong() else position),
            fontSize = 12.sp,
            color = if (preview != null) HikariText else HikariTextMuted,
            fontWeight = if (preview != null) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.weight(1f))
        Text(formatDurationMs(duration), fontSize = 12.sp, color = HikariTextMuted)
    }
}
