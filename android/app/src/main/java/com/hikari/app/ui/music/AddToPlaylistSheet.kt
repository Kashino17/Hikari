package com.hikari.app.ui.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.PlaylistWithSongs
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay

@Composable
fun AddToPlaylistSheet(
    song: MusicSong,
    playlists: List<PlaylistWithSongs>,
    onDismiss: () -> Unit,
    onSelect: (playlistId: Int) -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var addedId by remember { mutableStateOf<Int?>(null) }

    // Kurzes ✓-Feedback zeigen, dann das Sheet von selbst schließen.
    LaunchedEffect(addedId) {
        if (addedId != null) {
            delay(550)
            onDismiss()
        }
    }

    MuSheet(title = "Zu Playlist hinzufügen", onClose = onDismiss) {
        Text(
            song.title,
            fontSize = 13.sp,
            color = HikariTextMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))

        // "Neue Playlist" als gestrichelte Karte
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .drawBehind {
                    drawRoundRect(
                        color = HikariPrimary.copy(alpha = 0.45f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                        ),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    )
                }
                .muPressable { showCreate = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(HikariPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, null, tint = HikariPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("Neue Playlist erstellen", fontSize = 15.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        // Playlist-Zeilen (das Sheet selbst scrollt — deshalb keine LazyColumn)
        playlists.forEach { entry ->
            val alreadyIn = entry.songs.any { it.videoId == song.videoId }
            val justAdded = addedId == entry.playlist.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (justAdded) HikariPrimary.copy(alpha = 0.10f) else Color.Transparent)
                    .muPressable(enabled = !alreadyIn && addedId == null) {
                        onSelect(entry.playlist.id)
                        addedId = entry.playlist.id
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(HikariSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (entry.songs.isEmpty()) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            null,
                            tint = HikariTextMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        MixCoverPreview(entry.songs, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.playlist.name,
                        fontSize = 15.sp,
                        color = if (alreadyIn && !justAdded) HikariTextMuted else HikariText,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            justAdded -> "hinzugefügt"
                            alreadyIn -> "bereits enthalten"
                            entry.songs.size == 1 -> "1 Song"
                            else -> "${entry.songs.size} Songs"
                        },
                        fontSize = 12.sp,
                        color = if (justAdded) HikariPrimary else HikariTextMuted,
                    )
                }
                if (justAdded || alreadyIn) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = if (justAdded) HikariPrimary else HikariTextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    if (showCreate) {
        NamePlaylistDialog(
            title = "Neue Playlist",
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                onCreate(name)
            },
        )
    }
}

/** Dunkler Benennen-Dialog mit Scale-In und Amber-Fokus — ersetzt AlertDialog. */
@Composable
fun NamePlaylistDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val appear = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 600f))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF232326))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp),
        ) {
            Text(title, fontSize = 18.sp, color = HikariText, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Name der Playlist", color = HikariTextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HikariPrimary,
                    unfocusedBorderColor = HikariSurfaceHigh,
                    focusedTextColor = HikariText,
                    unfocusedTextColor = HikariText,
                    cursorColor = HikariPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MuGhostButton("Abbrechen", modifier = Modifier.weight(1f)) { onDismiss() }
                MuPrimaryButton(
                    "Speichern",
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = if (name.isNotBlank()) 1f else 0.45f },
                ) { if (name.isNotBlank()) onConfirm(name) }
            }
        }
    }
}
