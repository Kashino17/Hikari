package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom-Sheet zum direkten Hinzufügen von Songs in eine Playlist (Spotify / YouTube Music-Parität).
 * Ermöglicht schnelles Durchsuchen von Favoriten, Verlauf oder freier Suche.
 */
@Composable
fun AddSongsToPlaylistSheet(
    playlistId: Int,
    playlistName: String,
    existingSongIds: Set<String>,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Favoriten, 1 = Zuletzt gehört
    var searchResults by remember { mutableStateOf<List<MusicSong>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Lokal hinzugefügte Video-IDs für sofortiges visuelles Feedback
    val locallyAdded = remember { mutableStateListOf<String>() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(350)
            isSearching = true
            val results = runCatching { viewModel.searchForPicker(query) }.getOrDefault(emptyList())
            searchResults = results
            isSearching = false
        }
    }

    MuSheet(
        title = "Songs zu „$playlistName“ hinzufügen",
        onClose = onDismiss,
    ) {
        // Suchfeld
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Song oder Interpret suchen …", color = HikariTextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = HikariTextMuted, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        "Löschen",
                        tint = HikariTextMuted,
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HikariPrimary,
                unfocusedBorderColor = HikariSurfaceHigh,
                focusedTextColor = HikariText,
                unfocusedTextColor = HikariText,
                cursorColor = HikariPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )

        Spacer(Modifier.height(12.dp))

        // Wenn kein Suchbegriff eingegeben ist: Tabs für Favoriten & Verlauf
        if (query.isBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuActionPill(
                    icon = Icons.Default.Favorite,
                    label = "Favoriten (${viewModel.favorites.size})",
                    active = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                MuActionPill(
                    icon = Icons.Default.History,
                    label = "Zuletzt gehört (${viewModel.history.size})",
                    active = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Song-Liste
        val displaySongs = when {
            query.isNotBlank() -> searchResults
            selectedTab == 0 -> viewModel.favorites
            else -> viewModel.history
        }

        if (isSearching) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HikariPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
        } else if (displaySongs.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isNotBlank()) "Keine Treffer für „$query“"
                    else if (selectedTab == 0) "Noch keine Favoriten vorhanden"
                    else "Noch keine Songs im Verlauf",
                    color = HikariTextMuted,
                    fontSize = 13.sp,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                displaySongs.forEach { song ->
                    val isAlreadyIn = song.videoId in existingSongIds || song.videoId in locallyAdded
                    AddSongRow(
                        song = song,
                        isAdded = isAlreadyIn,
                        onAdd = {
                            viewModel.addToPlaylist(playlistId, song)
                            locallyAdded.add(song.videoId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSongRow(
    song: MusicSong,
    isAdded: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.uploader,
                fontSize = 12.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))

        if (isAdded) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HikariPrimary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Check, null, tint = HikariPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dabei", fontSize = 12.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HikariPrimary)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Hinzufügen", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
