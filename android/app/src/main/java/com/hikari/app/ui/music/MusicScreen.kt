package com.hikari.app.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onBack: () -> Unit,
    viewModel: MusicViewModel = viewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Entdecken", "Bibliothek", "Favoriten")

    // Per-tab state
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hikari Music", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HikariBg,
                    titleContentColor = HikariText,
                ),
            )
        },
        containerColor = HikariBg,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = HikariBg,
                contentColor = HikariPrimary,
                indicator = { _ -> },
                divider = {},
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

            when (tab) {
                0 -> {
                    LaunchedEffect(Unit) { viewModel.loadSuggestions() }
                    DiscoverTabContent(viewModel, searchQuery) { searchQuery = it }
                }
                1 -> {
                    LaunchedEffect(Unit) { viewModel.loadAllSongs() }
                    LibraryTabContent(viewModel)
                }
                2 -> {
                    LaunchedEffect(Unit) { viewModel.loadFavorites() }
                    FavoritesTabContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun DiscoverTabContent(
    viewModel: MusicViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Suchen...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = HikariTextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, null, tint = HikariTextMuted)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HikariPrimary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { if (query.isNotBlank()) viewModel.search(query) },
                ),
            )
        }

        if (viewModel.searchResults.isNotEmpty()) {
            item {
                Text("Suchergebnisse", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = HikariText, modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(viewModel.searchResults, key = { it.videoId }) { song ->
                SongRow(song, viewModel, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        if (viewModel.suggestionsLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            val grouped = viewModel.suggestions.groupBy { it.uploader }
            grouped.forEach { (uploader, songs) ->
                item {
                    Text(uploader, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = HikariText, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                items(songs.take(4), key = { it.videoId }) { song ->
                    SongRow(song, viewModel, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryTabContent(viewModel: MusicViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (viewModel.allSongs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null, tint = HikariTextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Noch keine Songs", color = HikariTextMuted)
                    }
                }
            }
        } else {
            items(viewModel.allSongs, key = { it.videoId }) { song ->
                SongRow(song, viewModel, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun FavoritesTabContent(viewModel: MusicViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (viewModel.favorites.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Favorite, null, tint = HikariTextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Keine Favoriten", color = HikariTextMuted)
                    }
                }
            }
        } else {
            items(viewModel.favorites, key = { it.videoId }) { song ->
                SongRow(song, viewModel, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SongRow(
    song: MusicSong,
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .clickable { viewModel.playSong(song) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberAsyncImagePainter(song.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = HikariText, maxLines = 1)
            Text(song.uploader, fontSize = 12.sp, color = HikariTextMuted, maxLines = 1)
        }
        Text(formatDuration(song.duration), fontSize = 12.sp, color = HikariTextMuted)
        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
            Icon(
                if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                null,
                tint = if (song.isFavorite) Color(0xFFFF5252) else HikariTextMuted,
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
