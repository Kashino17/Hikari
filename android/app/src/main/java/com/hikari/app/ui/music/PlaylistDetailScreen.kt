package com.hikari.app.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val entry = viewModel.playlists.firstOrNull { it.playlist.id == playlistId }
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    // Bearbeiten-Modus: Reihenfolge per Drag & Drop auf einer lokalen Kopie,
    // gespeichert wird erst beim Verlassen ("Fertig" oder Zurück-Geste).
    var editMode by remember { mutableStateOf(false) }
    val editOrder = remember { mutableStateListOf<MusicSong>() }
    fun finishEdit() {
        if (!editMode) return
        editMode = false
        viewModel.reorderPlaylist(playlistId, editOrder.map { it.videoId })
    }
    BackHandler(enabled = editMode) { finishEdit() }

    LaunchedEffect(playlistId) { viewModel.refreshLibrary() }

    // Playlist gelöscht → zurück statt leerem Screen (erst nach dem ersten Laden)
    LaunchedEffect(entry, viewModel.libraryLoaded) {
        if (entry == null && viewModel.libraryLoaded) onBack()
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        if (entry == null) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText) { onBack() }
                Spacer(Modifier.width(6.dp))
                Text("Playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HikariText)
            }
            CenteredLoader()
            return@Column
        }

        val songs = entry.songs
        val allDownloaded = songs.isNotEmpty() && entry.downloadedCount == songs.size

        if (editMode) {
            // Bearbeiten-Modus: schlanke Kopfzeile, darunter die Drag-Liste.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText) { finishEdit() }
                MuActionPill(icon = Icons.Default.Check, label = "Fertig", active = true) { finishEdit() }
            }
            Text(
                "Am Griff ziehen, um die Reihenfolge zu ändern",
                fontSize = 12.sp,
                color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            ReorderList(
                order = editOrder,
                modifier = Modifier.weight(1f),
            )
        } else {
            // YouTube-Music-Aufbau: zentriertes Cover, Titel mittig, Icon-Reihe
            // um den weißen Play-Kreis — alles scrollt mit, nichts klebt.
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    item(key = "yt-header") {
                        Column(
                            Modifier.fillMaxWidth().statusBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(48.dp))
                            val cover = songs.firstOrNull()?.thumbnailUrl
                            if (!cover.isNullOrEmpty()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HikariSurfaceHigh),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.55f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(HikariSurfaceHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                        null,
                                        tint = HikariTextFaint,
                                        modifier = Modifier.size(56.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                entry.playlist.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = HikariText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    songs.isEmpty() -> "Noch keine Songs"
                                    allDownloaded -> "${songs.size} Songs · offline verfügbar"
                                    entry.downloadedCount > 0 ->
                                        "${songs.size} Songs · ${entry.downloadedCount} offline"
                                    else -> if (songs.size == 1) "1 Song" else "${songs.size} Songs"
                                },
                                fontSize = 13.sp,
                                color = HikariTextMuted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(18.dp))

                            // Symmetrische Aktionsreihe wie bei YouTube Music.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val downloading = songs.any { progressMap.containsKey(it.videoId) }
                                GhostIconChip(
                                    when {
                                        downloading -> Icons.Default.Close
                                        allDownloaded -> Icons.Outlined.OfflinePin
                                        else -> Icons.Outlined.CloudDownload
                                    },
                                    "Alle offline speichern",
                                ) {
                                    when {
                                        downloading -> viewModel.cancelAllDownloads()
                                        !allDownloaded && songs.isNotEmpty() -> viewModel.downloadPlaylist(entry)
                                    }
                                }
                                GhostIconChip(Icons.Default.Edit, "Reihenfolge bearbeiten") {
                                    if (songs.size > 1) {
                                        editOrder.clear()
                                        editOrder.addAll(songs)
                                        editMode = true
                                    }
                                }
                                PlayRoundButton(size = 64.dp) {
                                    songs.firstOrNull()?.let { viewModel.play(it, songs); onOpenNowPlaying() }
                                }
                                GhostIconChip(Icons.Default.Shuffle, "Zufällig") {
                                    val shuffled = songs.shuffled()
                                    shuffled.firstOrNull()?.let { viewModel.play(it, shuffled); onOpenNowPlaying() }
                                }
                                Box {
                                    GhostIconChip(Icons.Default.MoreVert, "Mehr") { menuOpen = true }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Umbenennen") },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                                            onClick = { menuOpen = false; showRename = true },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Playlist löschen") },
                                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                                            onClick = {
                                                menuOpen = false
                                                viewModel.deletePlaylist(entry.playlist)
                                                onBack()
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                    if (songs.isEmpty()) {
                        item(key = "empty") {
                            EmptyHint(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                "Diese Playlist ist leer — füge Songs über das Menü eines Songs hinzu.",
                            )
                        }
                    } else {
                        itemsIndexed(songs, key = { _, s -> s.videoId }) { i, song ->
                            SongRow(
                                song = song,
                                viewModel = viewModel,
                                contextQueue = songs,
                                isCurrent = currentSong?.videoId == song.videoId,
                                isDownloaded = song.videoId in downloadedIds,
                                progress = progressMap[song.videoId],
                                online = online,
                                number = i + 1,
                                onRemoveFromPlaylist = { viewModel.removeFromPlaylist(playlistId, song) },
                            )
                        }
                    }
                }

                MuIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Zurück",
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.40f), CircleShape),
                    tint = HikariText,
                ) { onBack() }
            }
        }

        if (currentSong != null) {
            MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
        }
    }

    if (showRename && entry != null) {
        NamePlaylistDialog(
            title = "Playlist umbenennen",
            initial = entry.playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { newName ->
                viewModel.renamePlaylist(entry.playlist, newName)
                showRename = false
            },
        )
    }
}

/**
 * Drag-&-Drop-Liste des Bearbeiten-Modus. Die gezogene Zeile folgt dem
 * Finger; überschreitet sie die halbe Zeilenhöhe, tauscht sie mit dem
 * Nachbarn (Haptik-Tick), alle anderen Zeilen gleiten animiert nach.
 */
@Composable
private fun ReorderList(order: SnapshotStateList<MusicSong>, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val rowHeightPx = with(LocalDensity.current) { 64.dp.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        itemsIndexed(order, key = { _, s -> s.videoId }) { index, song ->
            val isDragging = index == draggingIndex
            ReorderRow(
                song = song,
                modifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationY = dragOffset
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 16f
                        }
                } else {
                    Modifier.animateItem()
                },
                // pointerInput ist auf die videoId gekeyt: die Geste überlebt
                // die Index-Wechsel der Zeile während des Ziehens.
                dragHandle = Modifier.pointerInput(song.videoId) {
                    detectDragGestures(
                        onDragStart = {
                            draggingIndex = order.indexOfFirst { it.videoId == song.videoId }
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            while (dragOffset > rowHeightPx / 2 && draggingIndex < order.lastIndex) {
                                val i = draggingIndex
                                order.add(i + 1, order.removeAt(i))
                                draggingIndex = i + 1
                                dragOffset -= rowHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            while (dragOffset < -rowHeightPx / 2 && draggingIndex > 0) {
                                val i = draggingIndex
                                order.add(i - 1, order.removeAt(i))
                                draggingIndex = i - 1
                                dragOffset += rowHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { draggingIndex = -1; dragOffset = 0f },
                        onDragCancel = { draggingIndex = -1; dragOffset = 0f },
                    )
                },
            )
        }
    }
}

@Composable
private fun ReorderRow(song: MusicSong, modifier: Modifier, dragHandle: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.DragHandle,
            "Ziehen",
            tint = HikariTextMuted,
            modifier = dragHandle.size(40.dp).padding(9.dp),
        )
        AsyncImage(
            model = song.thumbnailUrl.ifEmpty { null },
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.uploader,
                fontSize = 11.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
