package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Entdecken", "Verlauf", "Favoriten")
    val currentSong by viewModel.player.currentSong.collectAsState()

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Text(
            "Musik",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = HikariText,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
        )

        TabRow(
            selectedTabIndex = tab,
            containerColor = HikariBg,
            contentColor = HikariPrimary,
            indicator = { },
            divider = { },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title, fontSize = 14.sp) },
                    selectedContentColor = HikariPrimary,
                    unselectedContentColor = HikariTextFaint,
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> DiscoverTab(viewModel)
                1 -> HistoryTab(viewModel)
                2 -> FavoritesTab(viewModel)
            }
        }

        if (currentSong != null) {
            MiniPlayerBar(
                controller = viewModel.player,
                onOpen = onOpenNowPlaying,
            )
        }
    }
}

@Composable
private fun DiscoverTab(viewModel: MusicViewModel) {
    val searching = viewModel.searchAttempted

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Songs, Artists suchen…", color = HikariTextFaint) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = HikariTextMuted) },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Close, "Löschen", tint = HikariTextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HikariPrimary,
                    unfocusedBorderColor = HikariSurfaceHigh,
                    focusedTextColor = HikariText,
                    unfocusedTextColor = HikariText,
                    cursorColor = HikariPrimary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.search(viewModel.searchQuery) },
                ),
            )
        }

        if (searching) {
            when {
                viewModel.searchLoading -> item(key = "search-loading") { CenteredLoader() }
                viewModel.searchResults.isEmpty() -> item(key = "search-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(viewModel.searchResults, key = { "s-${it.videoId}" }) { song ->
                    SongRow(song, viewModel, viewModel.searchResults)
                }
            }
            return@LazyColumn
        }

        when {
            viewModel.discoverLoading -> item(key = "disc-loading") { CenteredLoader() }
            viewModel.discoverFailed -> item(key = "disc-error") {
                Column(
                    Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Musik-Server nicht erreichbar", color = HikariTextMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.loadDiscover() }) {
                        Text("Erneut versuchen", color = HikariPrimary)
                    }
                }
            }
            else -> viewModel.discoverSections.forEach { section ->
                item(key = "h-${section.title}") {
                    Text(
                        section.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HikariText,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
                items(section.songs, key = { "${section.title}-${it.videoId}" }) { song ->
                    SongRow(song, viewModel, section.songs)
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(viewModel: MusicViewModel) {
    if (viewModel.history.isEmpty()) {
        EmptyHint(Icons.Default.MusicNote, "Noch nichts gehört — starte im Entdecken-Tab!")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(viewModel.history, key = { it.videoId }) { song ->
            SongRow(song, viewModel, viewModel.history, showDelete = true)
        }
    }
}

@Composable
private fun FavoritesTab(viewModel: MusicViewModel) {
    if (viewModel.favorites.isEmpty()) {
        EmptyHint(Icons.Default.FavoriteBorder, "Tippe das Herz bei einem Song — er landet hier.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(viewModel.favorites, key = { it.videoId }) { song ->
            SongRow(song, viewModel, viewModel.favorites)
        }
    }
}

@Composable
private fun SongRow(
    song: MusicSong,
    viewModel: MusicViewModel,
    contextQueue: List<MusicSong>,
    showDelete: Boolean = false,
) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val isCurrent = currentSong?.videoId == song.videoId
    val isFavorite = song.videoId in viewModel.favoriteIds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) HikariSurfaceHigh else HikariCardBg)
            .clickable { viewModel.play(song, contextQueue) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = song.thumbnailUrl.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = HikariPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = if (isCurrent) HikariPrimary else HikariText,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.uploader, fontSize = 12.sp, color = HikariTextMuted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (song.duration > 0) {
                    Text("  ·  ${formatDuration(song.duration)}", fontSize = 12.sp, color = HikariTextFaint)
                }
            }
        }
        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorit",
                tint = if (isFavorite) Color(0xFFFF5252) else HikariTextMuted,
            )
        }
        if (showDelete) {
            IconButton(onClick = { viewModel.removeFromHistory(song) }) {
                Icon(Icons.Outlined.DeleteOutline, "Entfernen", tint = HikariTextFaint)
            }
        }
    }
}

@Composable
private fun CenteredLoader() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = HikariPrimary)
    }
}

@Composable
private fun EmptyHint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = HikariTextFaint, modifier = Modifier.size(44.dp))
            Text(text, color = HikariTextMuted, fontSize = 14.sp)
        }
    }
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

internal fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000).toInt())
