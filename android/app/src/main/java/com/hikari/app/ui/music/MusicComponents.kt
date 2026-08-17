package com.hikari.app.ui.music

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Song-Zeile für alle Musik-Listen. Zeigt Download-Zustand als Tri-State
 * (nicht geladen / lädt / offline verfügbar) analog zu [com.hikari.app.ui.profile.components.LocalDownloadIcon].
 *
 * Player-/Download-/Netz-Zustände kommen als Primitive herein — die Flows
 * werden einmal im umgebenden Screen/Tab collectet (Muster wie RankedRowBound/
 * TileBound in MusicScreen), statt dass jede Zeile vier StateFlows abonniert.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: MusicSong,
    viewModel: MusicViewModel,
    contextQueue: List<MusicSong>,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    progress: Float?,
    online: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onOpenArtist: ((channelId: String, name: String) -> Unit)? = null,
) {
    val isFavorite = song.videoId in viewModel.favoriteIds
    val playable = isDownloaded || online

    var menuOpen by remember { mutableStateOf(false) }

    // Press-Scale wie muPressable, aber über combinedClickable — der
    // Long-Click fürs Kontextmenü muss erhalten bleiben.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val pressScale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f),
        label = "songrow-press",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .background(if (isCurrent) HikariPrimary.copy(alpha = 0.06f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.play(song, contextQueue)
                },
                onLongClick = { menuOpen = true },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // size()-Hint passend zur Zeilenhöhe — sonst dekodiert Coil die
            // volle Thumbnail-Auflösung für ein 48-dp-Bild.
            val thumbPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.thumbnailUrl.ifEmpty { null })
                    .size(thumbPx)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    MuEqualizerBars(playing = true, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = when {
                    isCurrent -> HikariPrimary
                    !playable -> HikariTextFaint
                    else -> HikariText
                },
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Klick auf den Uploader öffnet die Artist-Seite — nur wenn die
                // Suche eine Kanal-URL mitgeliefert hat.
                val artistChannelId = song.uploaderUrl.substringAfterLast("/", "")
                Text(
                    song.uploader,
                    fontSize = 12.sp,
                    color = HikariTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (onOpenArtist != null && artistChannelId.isNotBlank()) {
                                Modifier.clickable { onOpenArtist(artistChannelId, song.uploader) }
                            } else {
                                Modifier
                            },
                        ),
                )
                if (song.duration > 0) {
                    // Lange Inhalte (ab 10 min) mit Einheit statt mm:ss —
                    // "51 m" liest sich schneller als "51:28".
                    val durationText = if (song.duration >= 600) {
                        formatDurationUnits(song.duration)
                    } else {
                        formatDuration(song.duration)
                    }
                    Text("  ·  $durationText", fontSize = 12.sp, color = HikariTextFaint)
                }
                if (badge != null) {
                    Text("  ·  $badge", fontSize = 12.sp, color = HikariPrimary)
                }
                if (!playable) {
                    Text("  ·  offline nicht verfügbar", fontSize = 11.sp, color = HikariTextFaint)
                }
            }
        }

        DownloadStateButton(
            isDownloaded = isDownloaded,
            progress = progress,
            onDownload = { viewModel.downloadSong(song) },
            onDelete = { viewModel.deleteDownload(song.videoId) },
            onCancel = { viewModel.cancelDownload(song.videoId) },
        )

        MuIconButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDesc = "Favorit",
            tint = if (isFavorite) Color(0xFFFF5252) else HikariTextMuted,
            iconSize = 22.dp,
            onClick = { viewModel.toggleFavorite(song) },
        )

        Box {
            MuIconButton(Icons.Default.MoreVert, "Mehr", iconSize = 22.dp, onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Zu Playlist hinzufügen") },
                    onClick = {
                        menuOpen = false
                        viewModel.addToPlaylistTarget = song
                    },
                )
                if (isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Download löschen") },
                        onClick = {
                            menuOpen = false
                            viewModel.deleteDownload(song.videoId)
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Herunterladen") },
                        onClick = {
                            menuOpen = false
                            viewModel.downloadSong(song)
                        },
                    )
                }
                onRemoveFromPlaylist?.let { remove ->
                    DropdownMenuItem(
                        text = { Text("Aus Playlist entfernen") },
                        onClick = {
                            menuOpen = false
                            remove()
                        },
                    )
                }
                // Kollaborationen: ein Eintrag pro Artist mit eigener Seite.
                if (onOpenArtist != null && song.artists.size > 1) {
                    song.artists.forEach { a ->
                        val chId = a.channelId
                        if (chId != null) {
                            DropdownMenuItem(
                                text = { Text("Zu ${a.name}") },
                                onClick = {
                                    menuOpen = false
                                    onOpenArtist(chId, a.name)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadStateButton(
    isDownloaded: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    when {
        isDownloaded -> MuIconButton(
            icon = Icons.Outlined.OfflinePin,
            contentDesc = "Heruntergeladen",
            tint = Color(0xFF4ADE80),
            iconSize = 22.dp,
            onClick = onDelete,
        )
        // Lädt: Tipp auf den Ring bricht den Download ab (✕ statt Prozentzahl).
        progress != null -> Box(
            Modifier
                .size(44.dp)
                .let { if (onCancel != null) it.muPressable(onClick = onCancel) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (progress <= 0f) {
                CircularProgressIndicator(color = HikariPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    color = HikariPrimary,
                    trackColor = HikariSurfaceHigh,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(30.dp),
                )
            }
            if (onCancel != null) {
                Text("✕", fontSize = 11.sp, color = HikariTextMuted, fontWeight = FontWeight.Bold)
            } else if (progress > 0f) {
                Text(
                    "${(progress * 100).toInt()}",
                    fontSize = 9.sp,
                    color = HikariPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        else -> MuIconButton(
            icon = Icons.Outlined.CloudDownload,
            contentDesc = "Herunterladen",
            iconSize = 22.dp,
            onClick = onDownload,
        )
    }
}

/**
 * Ein Speichern-Button für Sammlungen (Playlist/Mix/Gruppe): bündelt
 * "in Bibliothek speichern" und "alles herunterladen" in einem Menü —
 * zwei getrennte Pillen für fast dieselbe Aktion sind UX-Ballast.
 * Läuft ein Massen-Download, wird die Pille zum Abbrechen-Knopf.
 */
@Composable
fun CollectionSaveMenu(
    saved: Boolean,
    allOffline: Boolean,
    downloading: Boolean,
    downloadedCount: Int,
    totalCount: Int,
    onSave: () -> Unit,
    onSaveAndDownload: () -> Unit,
    onCancelDownloads: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        MuActionPill(
            icon = when {
                allOffline -> Icons.Outlined.OfflinePin
                saved -> Icons.Filled.Check
                else -> Icons.AutoMirrored.Outlined.PlaylistAdd
            },
            label = when {
                downloading -> "✕ Abbrechen $downloadedCount/$totalCount"
                allOffline -> "Offline ✓"
                saved -> "Gespeichert ✓"
                else -> "Speichern"
            },
            active = saved || allOffline,
            activeColor = if (allOffline) Color(0xFF4ADE80) else HikariPrimary,
        ) {
            if (downloading) onCancelDownloads() else menuOpen = true
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!saved) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = HikariTextMuted)
                    },
                    text = { Text("In Bibliothek speichern") },
                    onClick = {
                        menuOpen = false
                        onSave()
                    },
                )
            }
            if (!allOffline) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Outlined.CloudDownload, null, tint = HikariTextMuted)
                    },
                    text = { Text(if (saved) "Alle herunterladen" else "Speichern + herunterladen") },
                    onClick = {
                        menuOpen = false
                        onSaveAndDownload()
                    },
                )
            }
        }
    }
}

@Composable
fun OfflineBanner(text: String = "Offline — du siehst deine Downloads") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HikariPrimary.copy(alpha = 0.10f))
            .border(1.dp, HikariPrimary.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = HikariText, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CenteredLoader() {
    val pulse = muShimmerAlpha()
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote, null,
                tint = HikariPrimary,
                modifier = Modifier.size(34.dp).graphicsLayer { alpha = pulse },
            )
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(color = HikariPrimary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun EmptyHint(icon: ImageVector, text: String) {
    val pulse = muShimmerAlpha()
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon, null,
                tint = HikariTextFaint,
                modifier = Modifier.size(56.dp).graphicsLayer { alpha = pulse },
            )
            Text(
                text,
                color = HikariTextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

internal fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000).toInt())

/**
 * Dauer mit Einheit für lange Inhalte (Podcasts, True Crime, Hörbücher):
 * "45 s", "25 m", "1 h 12 m" — bei Laufzeiten liest sich das schneller
 * als ein nacktes mm:ss.
 */
internal fun formatDurationUnits(seconds: Int): String {
    if (seconds < 60) return "$seconds s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) {
        if (m > 0) "$h h $m m" else "$h h"
    } else {
        "$m m"
    }
}

/**
 * Großer Hero-Kopf für Kanal-, Artist- und Sammel-Seiten im Spotify-Stil:
 * Bild läuft über den unteren Scrim direkt in den Seitenhintergrund aus,
 * Name groß IM Bild statt darunter — der verifiziert-Haken sitzt dezent
 * neben dem Namen statt in einer eigenen Zeile.
 */
@Composable
internal fun CollectionHero(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    height: Dp = 300.dp,
    verified: Boolean = false,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(HikariSurfaceHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = HikariTextFaint, modifier = Modifier.size(72.dp))
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, HikariBg.copy(alpha = 0.65f), HikariBg),
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 28.sp,
                    color = HikariText,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (verified) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Verified, "Verifiziert", tint = HikariPrimary, modifier = Modifier.size(18.dp))
                }
            }
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = HikariTextMuted, maxLines = 1)
            }
        }
    }
}

/** Runder Spotify-artiger Abspielen-Knopf — der eine Amber-Akzent der Seite. */
@Composable
internal fun PlayRoundButton(
    size: Dp = 56.dp,
    contentDesc: String = "Abspielen",
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFD263), HikariPrimary)))
            .muPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.PlayArrow, contentDesc, tint = Color.Black, modifier = Modifier.size(30.dp))
    }
}

/** Dezenter runder Geist-Chip (Zufällig etc.) neben dem Play-Knopf. */
@Composable
internal fun GhostIconChip(
    icon: ImageVector,
    contentDesc: String,
    size: Dp = 48.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .muPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDesc, tint = HikariText, modifier = Modifier.size(22.dp))
    }
}
