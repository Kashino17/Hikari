package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.PlaylistWithSongs
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: MusicSong,
    playlists: List<PlaylistWithSongs>,
    onDismiss: () -> Unit,
    onSelect: (playlistId: Int) -> Unit,
    onCreate: (name: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                "Zu Playlist hinzufügen",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = HikariText,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 2.dp),
            )
            Text(
                song.title,
                fontSize = 13.sp,
                color = HikariTextMuted,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showCreate = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(HikariSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, null, tint = HikariPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Neue Playlist erstellen", fontSize = 15.sp, color = HikariPrimary)
            }

            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(playlists, key = { it.playlist.id }) { entry ->
                    val alreadyIn = entry.songs.any { it.videoId == song.videoId }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !alreadyIn) { onSelect(entry.playlist.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(HikariSurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                null,
                                tint = HikariTextMuted,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.playlist.name,
                                fontSize = 15.sp,
                                color = if (alreadyIn) HikariTextMuted else HikariText,
                                maxLines = 1,
                            )
                            Text(
                                if (alreadyIn) "bereits enthalten" else "${entry.songs.size} Songs",
                                fontSize = 12.sp,
                                color = HikariTextMuted,
                            )
                        }
                    }
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

@Composable
fun NamePlaylistDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HikariSurface,
        title = { Text(title, color = HikariText, fontWeight = FontWeight.Bold) },
        text = {
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
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Speichern", color = if (name.isNotBlank()) HikariPrimary else HikariTextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = HikariTextMuted) }
        },
    )
}
