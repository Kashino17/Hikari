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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.domain.repo.PlaylistWithSongs
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private const val TAB_DISCOVER = 0
private const val TAB_PLAYLISTS = 1
private const val TAB_DOWNLOADS = 2
private const val TAB_FAVORITES = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onOpenNowPlaying: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
    onOpenGroup: (title: String, unit: String, query: String, mode: String) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_DISCOVER) }
    // Der Verlauf lebt als Widget oben im Entdecken-Tab, nicht als eigene Seite.
    val tabs = listOf("Entdecken", "Playlists", "Downloads", "Favoriten")
    val currentSong by viewModel.player.currentSong.collectAsState()
    val online by viewModel.isOnline.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Ohne Netz ist "Entdecken" sinnlos — automatisch zu den Downloads wechseln.
    LaunchedEffect(online) {
        if (!online && tab == TAB_DISCOVER) tab = TAB_DOWNLOADS
    }

    // Auch Entdecken braucht frische Daten — dort steht der Verlauf.
    LaunchedEffect(tab) { viewModel.refreshLibrary() }

    viewModel.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbar.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Musik",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = HikariText,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
            )

            if (!online) OfflineBanner()

            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = HikariBg,
                contentColor = HikariPrimary,
                edgePadding = 12.dp,
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
                    TAB_DISCOVER -> DiscoverTab(viewModel, online, onOpenMix, onOpenGroup, onOpenArtist)
                    TAB_PLAYLISTS -> PlaylistsTab(viewModel, onOpenPlaylist)
                    TAB_DOWNLOADS -> DownloadsTab(viewModel)
                    TAB_FAVORITES -> FavoritesTab(viewModel)
                }
            }

            if (currentSong != null) {
                MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
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

@Composable
private fun DiscoverTab(
    viewModel: MusicViewModel,
    online: Boolean,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
    onOpenGroup: (title: String, unit: String, query: String, mode: String) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
) {
    if (!online) {
        EmptyHint(Icons.Outlined.CloudDownload, "Entdecken braucht Internet — deine Downloads findest du im Downloads-Tab.")
        return
    }

    val searching = viewModel.searchAttempted
    val instrumental by viewModel.instrumentalOnly.collectAsState()
    val currentSong by viewModel.player.currentSong.collectAsState()
    val searchMode = viewModel.searchMode

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "search") {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = {
                    Text(
                        when (searchMode) {
                            MusicSearchMode.MUSIC -> "Songs, Artists suchen…"
                            MusicSearchMode.AUDIOBOOK -> "Hörbücher suchen…"
                            MusicSearchMode.PODCAST -> "Podcasts suchen…"
                            MusicSearchMode.TRUECRIME -> "Fälle, Shows suchen…"
                        },
                        color = HikariTextFaint,
                    )
                },
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
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(viewModel.searchQuery) }),
            )
        }

        item(key = "mode-chips") {
            SearchModeChips(
                selected = searchMode,
                onSelect = { viewModel.selectSearchMode(it) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }

        // "Ohne Gesang" ergibt nur bei Musik Sinn — Hörbuch und Podcast leben von der Stimme.
        if (searchMode == MusicSearchMode.MUSIC) {
            item(key = "instrumental-toggle") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    InstrumentalToggle(
                        enabled = instrumental,
                        onToggle = { viewModel.toggleInstrumental() },
                    )
                }
            }
        }

        if (!searching && viewModel.history.isNotEmpty()) {
            item(key = "history-strip") {
                HistoryStrip(
                    songs = viewModel.history.take(10),
                    currentVideoId = currentSong?.videoId,
                    onPlay = { song -> viewModel.play(song, viewModel.history.take(10)) },
                )
            }
        }

        if (searching) {
            when {
                viewModel.searchLoading -> item(key = "search-loading") { CenteredLoader() }
                viewModel.searchResults.isEmpty() && viewModel.searchGroups.isEmpty() -> item(key = "search-empty") {
                    if (instrumental) {
                        // Der Filter ist die wahrscheinlichste Ursache — also
                        // gleich den Ausweg anbieten statt nur "nichts gefunden".
                        FilteredEmptyHint(onDisableFilter = { viewModel.toggleInstrumental() })
                    } else {
                        EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                    }
                }
                else -> {
                    // Zuerst die erkannten Hörbücher/Shows als Gruppe, dann die
                    // einzelnen Treffer, die keiner Gruppe zugeordnet werden konnten.
                    items(viewModel.searchGroups, key = { "g-${it.uploader}" }) { group ->
                        val unit = if (searchMode == MusicSearchMode.AUDIOBOOK) "Kapitel" else "Folgen"
                        GroupRow(
                            group = group,
                            unitLabel = unit,
                            badge = if (searchMode == MusicSearchMode.TRUECRIME) {
                                viewModel.languageBadge(group.chapters.first())
                            } else {
                                null
                            },
                            onClick = {
                                onOpenGroup(group.uploader, unit, viewModel.searchQuery, searchMode.apiValue)
                            },
                        )
                    }
                    items(viewModel.searchResults, key = { "s-${it.videoId}" }) { song ->
                        SongRow(
                            song,
                            viewModel,
                            viewModel.searchResults,
                            badge = if (searchMode == MusicSearchMode.TRUECRIME) {
                                viewModel.languageBadge(song)
                            } else {
                                null
                            },
                            onOpenArtist = onOpenArtist,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        when {
            viewModel.discoverLoading -> item(key = "disc-loading") { DiscoverSkeleton() }
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
            else -> discoverContent(viewModel, onOpenMix)
        }
    }
}

/**
 * Aufbau der Entdecken-Seite: erst die Mixe als Karten, dann die Charts als
 * Rangliste, danach wechseln sich Kachelreihen und kompakte Listen ab —
 * so bleibt beim Scrollen ein Rhythmus statt einer durchgehenden Liste.
 */
private fun LazyListScope.discoverContent(
    viewModel: MusicViewModel,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
) {
    val sections = viewModel.discoverSections
    if (sections.isEmpty()) return
    val mode = viewModel.searchMode.apiValue

    item(key = "mixes-header") {
        SectionHeader(
            if (viewModel.searchMode == MusicSearchMode.MUSIC) "Mixe" else "Genres",
            eyebrow = "KURATIERT",
        )
    }
    item(key = "mixes-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sections, key = { "mix-${it.title}" }) { section ->
                MixCard(
                    title = section.title,
                    songs = section.songs,
                    onClick = { onOpenMix(section.title, section.query, mode) },
                )
            }
        }
    }

    // Erste Sektion als Rangliste — hier sagt die Reihenfolge etwas aus.
    val charts = sections.first()
    item(key = "charts-header") {
        SectionHeader(charts.title, onSeeAll = { onOpenMix(charts.title, charts.query, mode) })
    }
    itemsIndexed(
        charts.songs.take(5),
        key = { _, song -> "chart-${song.videoId}" },
    ) { index, song ->
        RankedRowBound(index + 1, song, viewModel, charts.songs)
    }

    sections.drop(1).forEachIndexed { index, section ->
        item(key = "h-${section.title}") {
            SectionHeader(section.title, onSeeAll = { onOpenMix(section.title, section.query, mode) })
        }
        if (index % 2 == 0) {
            item(key = "row-${section.title}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.songs, key = { "t-${section.title}-${it.videoId}" }) { song ->
                        TileBound(song, viewModel, section.songs)
                    }
                }
            }
        } else {
            items(
                section.songs.take(4),
                key = { "l-${section.title}-${it.videoId}" },
            ) { song ->
                SongRow(song, viewModel, section.songs)
            }
        }
    }
}

/** Hält die Player-/Download-Zustände aus dem ViewModel an der Chart-Zeile. */
@Composable
private fun RankedRowBound(
    rank: Int,
    song: MusicSong,
    viewModel: MusicViewModel,
    queue: List<MusicSong>,
) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    RankedSongRow(
        rank = rank,
        song = song,
        isCurrent = currentSong?.videoId == song.videoId,
        isDownloaded = song.videoId in downloadedIds,
        onClick = { viewModel.play(song, queue) },
    )
}

@Composable
private fun TileBound(
    song: MusicSong,
    viewModel: MusicViewModel,
    queue: List<MusicSong>,
) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    SongTile(
        song = song,
        isCurrent = currentSong?.videoId == song.videoId,
        isDownloaded = song.videoId in downloadedIds,
        onClick = { viewModel.play(song, queue) },
        onLongClick = { viewModel.addToPlaylistTarget = song },
    )
}

@Composable
private fun PlaylistsTab(viewModel: MusicViewModel, onOpenPlaylist: (Int) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (viewModel.playlists.size == 1) "1 Playlist" else "${viewModel.playlists.size} Playlists",
                fontSize = 13.sp,
                color = HikariTextMuted,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Neu", color = HikariPrimary, fontSize = 13.sp)
            }
        }

        if (viewModel.playlists.isEmpty()) {
            EmptyHint(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                "Noch keine Playlists — tippe auf „Neu“ oder lange auf einen Song.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(viewModel.playlists, key = { it.playlist.id }) { entry ->
                    PlaylistCard(entry, onClick = { onOpenPlaylist(entry.playlist.id) })
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
                viewModel.createPlaylist(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun PlaylistCard(entry: PlaylistWithSongs, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = HikariPrimary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.playlist.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = HikariText, maxLines = 1)
            val songLabel = if (entry.songs.size == 1) "1 Song" else "${entry.songs.size} Songs"
            val offlineLabel = when {
                entry.songs.isEmpty() -> ""
                entry.downloadedCount == entry.songs.size -> "  ·  komplett offline"
                entry.downloadedCount > 0 -> "  ·  ${entry.downloadedCount} offline"
                else -> ""
            }
            Text("$songLabel$offlineLabel", fontSize = 12.sp, color = HikariTextMuted)
        }
    }
}

@Composable
private fun DownloadsTab(viewModel: MusicViewModel) {
    val songs by viewModel.downloadedSongs.collectAsState()

    if (songs.isEmpty()) {
        EmptyHint(
            Icons.Outlined.CloudDownload,
            "Noch nichts heruntergeladen — tippe bei einem Song auf das Download-Symbol.",
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (songs.size == 1) "1 Song offline verfügbar" else "${songs.size} Songs offline verfügbar",
            fontSize = 13.sp,
            color = HikariTextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(songs, key = { it.videoId }) { song ->
                SongRow(song, viewModel, songs)
            }
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

