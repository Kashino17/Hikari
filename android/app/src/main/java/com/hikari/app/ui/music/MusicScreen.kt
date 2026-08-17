package com.hikari.app.ui.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.HomeItem
import com.hikari.app.domain.model.HomeSectionKind
import com.hikari.app.domain.model.MusicSearchResult
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicSearchFilter
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

@Composable
fun MusicScreen(
    onOpenNowPlaying: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
    onOpenGroup: (title: String, unit: String, query: String, mode: String) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
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
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                color = HikariText,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
            )

            if (!online) OfflineBanner()

            // Tab-Leiste als Chip-Reihe mit Press-Feedback statt Material-TabRow.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    MuChip(title, active = tab == index, onClick = { tab = index })
                }
            }

            Box(Modifier.weight(1f)) {
                Crossfade(tab, animationSpec = tween(200), label = "musicTab") { t ->
                    when (t) {
                        TAB_DISCOVER -> DiscoverTab(viewModel, online, onOpenMix, onOpenGroup, onOpenArtist, onOpenCollection)
                        TAB_PLAYLISTS -> PlaylistsTab(viewModel, onOpenPlaylist)
                        TAB_DOWNLOADS -> DownloadsTab(viewModel)
                        TAB_FAVORITES -> FavoritesTab(viewModel)
                    }
                }
            }

            if (currentSong != null) {
                MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) { data ->
            // Material-Default ist hell — auf die dunklen Hikari-Flächen abstimmen.
            Snackbar(
                snackbarData = data,
                containerColor = HikariSurfaceHigh,
                contentColor = HikariText,
                shape = RoundedCornerShape(12.dp),
            )
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

@Composable
private fun DiscoverTab(
    viewModel: MusicViewModel,
    online: Boolean,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
    onOpenGroup: (title: String, unit: String, query: String, mode: String) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
) {
    if (!online) {
        EmptyHint(Icons.Outlined.CloudDownload, "Entdecken braucht Internet — deine Downloads findest du im Downloads-Tab.")
        return
    }

    val searching = viewModel.searchAttempted
    val instrumental by viewModel.instrumentalOnly.collectAsState()
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val searchMode = viewModel.searchMode
    val musicMode = searchMode == MusicSearchMode.MUSIC

    LazyColumn(
        Modifier.fillMaxSize(),
        // Zustand lebt im ViewModel: überlebt Tab-Crossfade und Rücknavigation.
        state = viewModel.discoverListState,
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item(key = "search") {
            MusicSearchField(
                value = viewModel.searchQuery,
                placeholder = when (searchMode) {
                    MusicSearchMode.MUSIC -> "Songs, Artists suchen…"
                    MusicSearchMode.AUDIOBOOK -> "Hörbücher suchen…"
                    MusicSearchMode.PODCAST -> "Podcasts suchen…"
                    MusicSearchMode.TRUECRIME -> "Fälle, Shows suchen…"
                },
                onValueChange = {
                    viewModel.searchQuery = it
                    viewModel.onSearchQueryChange(it)
                },
                onFocus = { viewModel.onSearchFocus() },
                onClear = { viewModel.clearSearch() },
                onSearch = { viewModel.search(viewModel.searchQuery) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
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
        if (musicMode) {
            item(key = "instrumental-toggle") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    InstrumentalToggle(
                        enabled = instrumental,
                        onToggle = { viewModel.toggleInstrumental() },
                    )
                }
            }
        }

        // Musik-Modus mit aktivem Suchfeld: Verlauf und Genres bzw. Vorschläge
        // statt der Entdecken-Inhalte.
        if (musicMode && viewModel.searchActive && !searching) {
            if (viewModel.searchQuery.isBlank()) {
                if (searchHistory.isNotEmpty()) {
                    item(key = "search-history") {
                        SearchHistorySection(
                            history = searchHistory,
                            onSelect = {
                                viewModel.searchQuery = it
                                viewModel.search(it)
                            },
                            onRemove = { viewModel.removeHistoryEntry(it) },
                            onClearAll = { viewModel.clearHistory() },
                        )
                    }
                }
                item(key = "genre-browse") {
                    GenreBrowseGrid(onOpenGenre = { title, query ->
                        onOpenMix(title, query, MusicSearchMode.MUSIC.apiValue)
                    })
                }
            } else {
                item(key = "suggestions") {
                    SuggestionsList(
                        query = viewModel.searchQuery,
                        suggestions = viewModel.suggestions,
                        onSelect = {
                            viewModel.searchQuery = it
                            viewModel.search(it)
                        },
                    )
                }
            }
            return@LazyColumn
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

        if (searching && musicMode) {
            smartSearchResults(
                viewModel = viewModel,
                instrumental = instrumental,
                currentVideoId = currentSong?.videoId,
                downloadedIds = downloadedIds,
                progressMap = progressMap,
                online = online,
                onOpenArtist = onOpenArtist,
                onOpenCollection = onOpenCollection,
            )
            return@LazyColumn
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
                            isCurrent = currentSong?.videoId == song.videoId,
                            isDownloaded = song.videoId in downloadedIds,
                            progress = progressMap[song.videoId],
                            online = online,
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
                    Spacer(Modifier.height(14.dp))
                    MuGhostButton("Erneut versuchen", onClick = { viewModel.loadDiscover() })
                }
            }
            musicMode -> homeContent(
                viewModel = viewModel,
                currentVideoId = currentSong?.videoId,
                downloadedIds = downloadedIds,
                progressMap = progressMap,
                online = online,
                onOpenMix = onOpenMix,
                onOpenArtist = onOpenArtist,
                onOpenCollection = onOpenCollection,
            )
            else -> discoverContent(
                viewModel = viewModel,
                currentVideoId = currentSong?.videoId,
                downloadedIds = downloadedIds,
                progressMap = progressMap,
                online = online,
                onOpenMix = onOpenMix,
            )
        }
    }
}

/**
 * Personalisierter Home-Feed des Musik-Modus: "Dein Mix" und "Ähnlich wie …"
 * aus den eigenen Hör-Seeds, dazu die YouTube-Music-Sektionen des Backends
 * mit gemischten Inhalten (Songs, Playlists, Alben, Künstler). Song-Sektionen
 * wechseln sich als Kachelreihe und kompakte Liste ab — gleicher Rhythmus wie
 * die kuratierten Mixe.
 */
private fun LazyListScope.homeContent(
    viewModel: MusicViewModel,
    currentVideoId: String?,
    downloadedIds: Set<String>,
    progressMap: Map<String, Float>,
    online: Boolean,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
) {
    val sections = viewModel.homeSections
    if (sections.isEmpty()) return

    var songRowToggle = 0
    sections.forEachIndexed { index, section ->
        item(key = "hh-$index") {
            when (section.kind) {
                HomeSectionKind.MIX -> SectionHeader(
                    section.title,
                    eyebrow = "FÜR DICH",
                    onSeeAll = { section.songs.firstOrNull()?.let { viewModel.play(it, section.songs) } },
                    actionLabel = "Abspielen",
                )
                HomeSectionKind.SIMILAR, HomeSectionKind.FAVORITES -> SectionHeader(
                    section.title,
                    onSeeAll = { section.songs.firstOrNull()?.let { viewModel.play(it, section.songs) } },
                    actionLabel = "Abspielen",
                )
                HomeSectionKind.CURATED -> SectionHeader(
                    section.title,
                    onSeeAll = { onOpenMix(section.title, section.query, MusicSearchMode.MUSIC.apiValue) },
                )
                HomeSectionKind.BACKEND -> SectionHeader(section.title)
            }
        }

        if (section.items.isNotEmpty()) {
            // Backend-Sektion: gemischtes Karussell aus allen Item-Typen.
            val sectionQueue = section.items
                .mapNotNull { (it as? HomeItem.SongItem)?.song }
            item(key = "hi-$index") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(section.items) { itemIndex, entry ->
                        when (entry) {
                            is HomeItem.SongItem -> TileBound(entry.song, viewModel, sectionQueue)
                            is HomeItem.PlaylistItem -> HomeCollectionCard(
                                name = entry.playlist.name,
                                subtitle = entry.playlist.uploaderName,
                                thumbnailUrl = entry.playlist.thumbnailUrl,
                                onClick = {
                                    onOpenCollection(entry.playlist.playlistId, entry.playlist.name, false)
                                },
                            )
                            is HomeItem.AlbumItem -> HomeCollectionCard(
                                name = entry.album.name,
                                subtitle = entry.album.artistName,
                                thumbnailUrl = entry.album.thumbnailUrl,
                                onClick = {
                                    onOpenCollection(entry.album.playlistId, entry.album.name, true)
                                },
                            )
                            is HomeItem.ArtistItem -> HomeArtistBubble(
                                name = entry.artist.name,
                                thumbnailUrl = entry.artist.thumbnailUrl,
                                onClick = { onOpenArtist(entry.artist.channelId, entry.artist.name) },
                            )
                        }
                    }
                }
            }
        } else if (section.songs.isNotEmpty()) {
            if (songRowToggle % 2 == 0) {
                item(key = "hr-$index") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.songs, key = { "ht-$index-${it.videoId}" }) { song ->
                            TileBound(song, viewModel, section.songs)
                        }
                    }
                }
            } else {
                items(
                    section.songs.take(4),
                    key = { "hl-$index-${it.videoId}" },
                ) { song ->
                    SongRow(
                        song,
                        viewModel,
                        section.songs,
                        isCurrent = currentVideoId == song.videoId,
                        isDownloaded = song.videoId in downloadedIds,
                        progress = progressMap[song.videoId],
                        online = online,
                        onOpenArtist = onOpenArtist,
                    )
                }
            }
            songRowToggle++
        }
    }
}

/**
 * Ergebnisbereich der Musik-Smart-Search: Filter-Chips oben, darunter bei
 * „Alle“ die Vollsuche-Sektionen (Top-Ergebnis, Songs, Künstler, Alben,
 * Playlists) — leere Sektionen bleiben unsichtbar. Die anderen Filter zeigen
 * ihre lazy geladenen Trefferlisten.
 */
private fun LazyListScope.smartSearchResults(
    viewModel: MusicViewModel,
    instrumental: Boolean,
    currentVideoId: String?,
    downloadedIds: Set<String>,
    progressMap: Map<String, Float>,
    online: Boolean,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
) {
    item(key = "filter-chips") {
        ResultFilterChips(
            selected = viewModel.activeFilter,
            onSelect = { viewModel.selectFilter(it) },
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    if (viewModel.searchLoading) {
        item(key = "search-loading") { CenteredLoader() }
        return
    }

    when (viewModel.activeFilter) {
        MusicSearchFilter.ALLE -> {
            val full = viewModel.fullResults
            val nothingFound = full == null ||
                (full.topResult == null && full.songs.isEmpty() && full.artists.isEmpty() &&
                    full.albums.isEmpty() && full.playlists.isEmpty())
            if (nothingFound) {
                item(key = "search-empty") {
                    if (instrumental) {
                        FilteredEmptyHint(onDisableFilter = { viewModel.toggleInstrumental() })
                    } else {
                        EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                    }
                }
                return
            }
            full!!.topResult?.let { top ->
                item(key = "top-result") {
                    TopResultCard(top, onClick = {
                        when (top) {
                            is MusicSearchResult.Song -> viewModel.play(top.song, full.songs)
                            is MusicSearchResult.Artist -> onOpenArtist(top.artist.channelId, top.artist.name)
                            is MusicSearchResult.Album ->
                                onOpenCollection(top.album.playlistId, top.album.name, true)
                            is MusicSearchResult.Playlist ->
                                onOpenCollection(top.playlist.playlistId, top.playlist.name, false)
                        }
                    })
                }
            }
            if (full.songs.isNotEmpty()) {
                item(key = "songs-header") { SectionHeader("Songs") }
                items(full.songs, key = { "s-${it.videoId}" }) { song ->
                    SongRow(
                        song,
                        viewModel,
                        full.songs,
                        isCurrent = currentVideoId == song.videoId,
                        isDownloaded = song.videoId in downloadedIds,
                        progress = progressMap[song.videoId],
                        online = online,
                        onOpenArtist = onOpenArtist,
                    )
                }
            }
            if (full.artists.isNotEmpty()) {
                item(key = "artists-header") { SectionHeader("Künstler") }
                items(full.artists, key = { "a-${it.channelId}" }) { artist ->
                    ArtistResultRow(artist, onClick = { onOpenArtist(artist.channelId, artist.name) })
                }
            }
            if (full.albums.isNotEmpty()) {
                item(key = "albums-header") { SectionHeader("Alben") }
                items(full.albums, key = { "al-${it.playlistId}" }) { album ->
                    AlbumResultRow(album, onClick = { onOpenCollection(album.playlistId, album.name, true) })
                }
            }
            if (full.playlists.isNotEmpty()) {
                item(key = "playlists-header") { SectionHeader("Playlists") }
                items(full.playlists, key = { "pl-${it.playlistId}" }) { playlist ->
                    PlaylistResultRow(
                        playlist,
                        onClick = { onOpenCollection(playlist.playlistId, playlist.name, false) },
                    )
                }
            }
        }
        MusicSearchFilter.SONGS -> {
            when {
                viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                viewModel.typedSongs.isEmpty() -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(viewModel.typedSongs, key = { "ts-${it.videoId}" }) { song ->
                    SongRow(
                        song,
                        viewModel,
                        viewModel.typedSongs,
                        isCurrent = currentVideoId == song.videoId,
                        isDownloaded = song.videoId in downloadedIds,
                        progress = progressMap[song.videoId],
                        online = online,
                        onOpenArtist = onOpenArtist,
                    )
                }
            }
        }
        MusicSearchFilter.ALBEN -> {
            when {
                viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                viewModel.typedAlbums.isEmpty() -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(viewModel.typedAlbums, key = { "ta-${it.playlistId}" }) { album ->
                    AlbumResultRow(album, onClick = { onOpenCollection(album.playlistId, album.name, true) })
                }
            }
        }
        MusicSearchFilter.KUENSTLER -> {
            when {
                viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                viewModel.typedArtists.isEmpty() -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(viewModel.typedArtists, key = { "tk-${it.channelId}" }) { artist ->
                    ArtistResultRow(artist, onClick = { onOpenArtist(artist.channelId, artist.name) })
                }
            }
        }
        MusicSearchFilter.PLAYLISTS -> {
            when {
                viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                viewModel.typedPlaylists.isEmpty() -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(viewModel.typedPlaylists, key = { "tp-${it.playlistId}" }) { playlist ->
                    PlaylistResultRow(
                        playlist,
                        onClick = { onOpenCollection(playlist.playlistId, playlist.name, false) },
                    )
                }
            }
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
    currentVideoId: String?,
    downloadedIds: Set<String>,
    progressMap: Map<String, Float>,
    online: Boolean,
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
                SongRow(
                    song,
                    viewModel,
                    section.songs,
                    isCurrent = currentVideoId == song.videoId,
                    isDownloaded = song.videoId in downloadedIds,
                    progress = progressMap[song.videoId],
                    online = online,
                )
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

/** Playlist-/Album-Karte im Home-Feed: quadratisches Cover, Name darunter. */
@Composable
private fun HomeCollectionCard(
    name: String,
    subtitle: String,
    thumbnailUrl: String,
    onClick: () -> Unit,
) {
    Column(Modifier.width(140.dp)) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .muPressable(onClick = onClick),
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(36.dp).align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = HikariText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                fontSize = 11.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Runder Künstler-Avatar für Home-Karusselle — Tipp öffnet die Artist-Seite. */
@Composable
private fun HomeArtistBubble(
    name: String,
    thumbnailUrl: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh)
                .muPressable(onClick = onClick),
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(30.dp).align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
            MuChip("Neue Playlist", active = true, icon = Icons.Default.Add, onClick = { showCreate = true })
        }

        if (viewModel.playlists.isEmpty()) {
            EmptyHint(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                "Noch keine Playlists — tippe auf „Neu“ oder lange auf einen Song.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = viewModel.playlistsListState,
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
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
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .muPressable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.radialGradient(
                        listOf(HikariPrimary.copy(alpha = 0.25f), HikariPrimary.copy(alpha = 0.08f))
                    )
                ),
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
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

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
        LazyColumn(
            Modifier.fillMaxSize(),
            state = viewModel.downloadsListState,
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(songs, key = { it.videoId }) { song ->
                SongRow(
                    song,
                    viewModel,
                    songs,
                    isCurrent = currentSong?.videoId == song.videoId,
                    isDownloaded = song.videoId in downloadedIds,
                    progress = progressMap[song.videoId],
                    online = online,
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(viewModel: MusicViewModel) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    if (viewModel.favorites.isEmpty()) {
        EmptyHint(Icons.Default.FavoriteBorder, "Tippe das Herz bei einem Song — er landet hier.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = viewModel.favoritesListState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(viewModel.favorites, key = { it.videoId }) { song ->
            SongRow(
                song,
                viewModel,
                viewModel.favorites,
                isCurrent = currentSong?.videoId == song.videoId,
                isDownloaded = song.videoId in downloadedIds,
                progress = progressMap[song.videoId],
                online = online,
            )
        }
    }
}

/**
 * Suchfeld als runde Pille mit animierter Amber-Border bei Fokus — ersetzt
 * das Material-OutlinedTextField. Feste Höhe, damit der Clear-Button beim
 * Tippen keinen Layout-Sprung verursacht.
 */
@Composable
private fun MusicSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (focused) HikariPrimary else Color.White.copy(alpha = 0.08f),
        tween(180),
        label = "searchBorder",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
        interactionSource = interaction,
        singleLine = true,
        textStyle = TextStyle(color = HikariText, fontSize = 15.sp),
        cursorBrush = SolidColor(HikariPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HikariCardBg)
                    .border(1.dp, borderColor, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search, null,
                    tint = if (focused) HikariPrimary else HikariTextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = HikariTextFaint, fontSize = 15.sp, maxLines = 1)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    MuIconButton(
                        Icons.Default.Close, "Löschen",
                        iconSize = 16.dp, touchSize = 36.dp,
                        onClick = onClear,
                    )
                }
            }
        },
    )
}

