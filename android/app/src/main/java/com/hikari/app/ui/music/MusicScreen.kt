package com.hikari.app.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Entdecken", "Bibliothek", "Favoriten")

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
                0 -> DiscoverTab(viewModel)
                1 -> LibraryTab(viewModel)
                2 -> FavoritesTab(viewModel)
            }
        }
    }
}

@Composable
private fun DiscoverTab(viewModel: MusicViewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadSuggestions()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Search bar
        var query by remember { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth()
                .padding(16.dp)
                .background(HikariCardBg, RoundedCornerShape(24.dp)),
            placeholder = { Text("Song, Künstler oder Album suchen...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = HikariTextMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, null, tint = HikariTextMuted)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HikariPrimary,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { if (query.isNotBlank()) viewModel.search(query) },
            ),
        )

        // Search results
        if (viewModel.searchResults.isNotEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text("Suchergebnisse", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HikariText)
                Spacer(Modifier.height(12.dp))
                viewModel.searchResults.forEach { song ->
                    MusicSongRow(song, viewModel, showPlaylistButton = true)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Suggestions / recommendations
        if (viewModel.suggestionsLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            // Group suggestions by uploader
            val grouped = viewModel.suggestions.groupBy { it.uploader }
            grouped.forEach { (uploader, songs) ->
                Column(Modifier.padding(16.dp)) {
                    Text(uploader, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HikariText)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(songs) { song ->
                            MusicCardSmall(song, viewModel)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(viewModel: MusicViewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadAllSongs()
    }

    Column(Modifier.fillMaxSize()) {
        if (viewModel.allSongs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, null, tint = HikariTextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Noch keine Songs", color = HikariTextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.allSongs, { it.videoId }) { song ->
                    MusicSongRow(song, viewModel)
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(viewModel: MusicViewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    Column(Modifier.fillMaxSize()) {
        if (viewModel.favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Favorite, null, tint = HikariTextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Keine Favoriten", color = HikariTextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.favorites, { it.videoId }) { song ->
                    MusicSongRow(song, viewModel, showPlaylistButton = false)
                }
            }
        }
    }
}

@Composable
private fun MusicSongRow(
    song: MusicSong,
    viewModel: MusicViewModel,
    showPlaylistButton: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .clickable { viewModel.playSong(song) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Image(
            painter = rememberAsyncImagePainter(song.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))

        // Info
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = HikariText, maxLines = 1)
            Text(song.uploader, fontSize = 12.sp, color = HikariTextMuted, maxLines = 1)
        }

        // Duration
        Text(formatDuration(song.duration), fontSize = 12.sp, color = HikariTextMuted)

        // Favorite
        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
            Icon(
                if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                null,
                tint = if (song.isFavorite) Color(0xFFFF5252) else HikariTextMuted,
            )
        }

        // Add to playlist
        if (showPlaylistButton) {
            IconButton(onClick = { /* show playlist picker */ }) {
                Icon(Icons.Default.Add, null, tint = HikariTextMuted)
            }
        }
    }
}

@Composable
private fun MusicCardSmall(song: MusicSong, viewModel: MusicViewModel) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { viewModel.playSong(song) },
    ) {
        Image(
            painter = rememberAsyncImagePainter(song.thumbnailUrl),
            contentDescription = song.title,
            modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(song.title, fontSize = 12.sp, color = HikariText, maxLines = 1, fontWeight = FontWeight.Medium)
        Text(song.uploader, fontSize = 11.sp, color = HikariTextMuted, maxLines = 1)
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
