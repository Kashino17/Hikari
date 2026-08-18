package com.hikari.app.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.HomeItem
import com.hikari.app.domain.model.HomeSectionKind
import com.hikari.app.domain.model.MusicSearchResult
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicSearchFilter
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun MusicScreen(
    onOpenNowPlaying: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
    onOpenGroup: (title: String, unit: String, query: String, mode: String) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val online by viewModel.isOnline.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showModeSheet by remember { mutableStateOf(false) }

    // Zurück-Geste auf der Musik-Übersicht: NIE zum Bibliothek-Tab
    // zurückfallen — erst offene Zustände schließen, dann die App in den
    // Hintergrund schicken (wie bei YouTube Music).
    val backContext = LocalContext.current
    BackHandler {
        when {
            showModeSheet -> showModeSheet = false
            viewModel.searchActive || viewModel.searchQuery.isNotEmpty() || viewModel.searchAttempted ->
                viewModel.clearSearch()
            else -> (backContext as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    viewModel.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbar.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val instrumental by viewModel.instrumentalOnly.collectAsState()
    val headerHistory by viewModel.searchHistory.collectAsState()
    val searchMode = viewModel.searchMode

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            // Suchleiste ersetzt den Titel in der obersten Zeile und bleibt
            // dadurch beim Scrollen fest stehen; Profilbild sitzt links.
            val panelsOpen = viewModel.searchAttempted ||
                (viewModel.searchActive &&
                    (viewModel.searchQuery.isNotBlank() || headerHistory.isNotEmpty()))
            MusicHeaderBar(
                avatarPath = viewModel.avatarPath.collectAsState().value,
                onOpenProfile = onOpenProfile,
                value = viewModel.searchQuery,
                placeholder = when (searchMode) {
                    MusicSearchMode.MUSIC -> "Songs, Artists, Alben suchen…"
                    MusicSearchMode.AUDIOBOOK -> "Hörbücher suchen…"
                    MusicSearchMode.PODCAST -> "Podcasts suchen…"
                    MusicSearchMode.TRUECRIME -> "Fälle, Shows suchen…"
                },
                filtered = searchMode != MusicSearchMode.MUSIC || instrumental,
                panelsOpen = panelsOpen,
                onValueChange = {
                    viewModel.searchQuery = it
                    viewModel.onSearchQueryChange(it)
                },
                onFocus = { viewModel.onSearchFocus() },
                onClear = { viewModel.clearSearch() },
                onSearch = { viewModel.search(viewModel.searchQuery) },
                onModeClick = { showModeSheet = true },
                searchOpen = viewModel.searchActive || viewModel.searchQuery.isNotEmpty() ||
                    viewModel.searchAttempted,
                onBack = { viewModel.clearSearch() },
            )

            if (!online) OfflineBanner()

            Box(Modifier.weight(1f)) {
                DiscoverTab(
                    viewModel, online,
                    onOpenMix, onOpenGroup, onOpenArtist, onOpenCollection,
                    onOpenProfile = onOpenProfile,
                )
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

        if (showModeSheet) {
            ModeSheet(viewModel, onClose = { showModeSheet = false })
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

/**
 * Kopfzeile des Musik-Bereichs: kein Titel mehr — links das Profilbild
 * (Zugang zur Bibliothek), rechts daneben füllt die Suchleiste die Zeile.
 * Sitzt fest über der Liste und bleibt damit beim Scrollen stehen.
 */
@Composable
private fun MusicHeaderBar(
    avatarPath: String?,
    onOpenProfile: () -> Unit,
    value: String,
    placeholder: String,
    filtered: Boolean,
    panelsOpen: Boolean,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onModeClick: () -> Unit,
    searchOpen: Boolean,
    onBack: () -> Unit,
) {
    // Docken Panels unter dem Header an, schrumpft der Abstand nach unten —
    // Bar und Panel wirken als eine Einheit.
    val bottomGap by animateDpAsState(if (panelsOpen) 4.dp else 12.dp, tween(200), label = "headerGap")
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Offene Suche: das Profilbild morpht zum Zurück-Pfeil — ein Tipp
        // führt zurück zur Entdecken-Seite (intuitiver Ausstieg aus der Suche).
        Crossfade(searchOpen, animationSpec = tween(200), label = "avatarBack") { open ->
            if (open) {
                MuIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Zurück zum Entdecken",
                    tint = HikariText,
                    touchSize = 40.dp,
                ) { onBack() }
            } else {
                MusicAvatarChip(avatarPath = avatarPath, size = 40.dp, onClick = onOpenProfile)
            }
        }
        Spacer(Modifier.width(10.dp))
        MusicSearchField(
            value = value,
            placeholder = placeholder,
            filtered = filtered,
            onValueChange = onValueChange,
            onFocus = onFocus,
            onClear = onClear,
            onSearch = onSearch,
            onModeClick = onModeClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Rundes Profilbild mit Amber-Ring — Fallback ist ein Personen-Icon. */
@Composable
private fun MusicAvatarChip(avatarPath: String?, size: Dp = 38.dp, onClick: () -> Unit) {
    // Legacy-"?v="-Suffix abschneiden wie auf der Profil-Seite; fehlt die
    // Datei, bleibt der Icon-Fallback statt eines leeren Kreises.
    val avatarFile = remember(avatarPath) {
        avatarPath?.substringBefore("?")?.let(::File)?.takeIf { it.exists() }
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(HikariCardBg)
            .border(1.5.dp, HikariPrimary.copy(alpha = 0.55f), CircleShape)
            .muPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = "Deine Bibliothek",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(
                Icons.Default.Person, "Deine Bibliothek",
                tint = HikariTextMuted,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** Ein Modus-Eintrag des Auswahl-Sheets — Material-Icon statt Emoji. */
private data class ModeEntry(
    val mode: MusicSearchMode,
    val icon: ImageVector,
    val title: String,
    val desc: String,
)

private val MODE_ENTRIES = listOf(
    ModeEntry(MusicSearchMode.MUSIC, Icons.Default.MusicNote, "Musik", "Songs, Artists, Alben und Playlists"),
    ModeEntry(MusicSearchMode.AUDIOBOOK, Icons.Outlined.MenuBook, "Hörbücher", "Ganze Bücher und Kapitel zum Zuhören"),
    ModeEntry(MusicSearchMode.PODCAST, Icons.Outlined.Podcasts, "Podcasts", "Shows und Folgen aller Themen"),
    ModeEntry(MusicSearchMode.TRUECRIME, Icons.Outlined.LocalPolice, "True Crime", "Echte Kriminalfälle als Audio"),
)

/**
 * Modus-Sheet hinter dem Tune-Icon in der Suchleiste: wählt zwischen Musik,
 * Hörbüchern, Podcasts und True Crime; im Musik-Modus wohnt hier auch der
 * "Ohne Gesang"-Schalter. Zurückhaltend: die aktive Zeile trägt nur einen
 * feinen Amber-Border, keine Farbfläche.
 */
@Composable
private fun ModeSheet(viewModel: MusicViewModel, onClose: () -> Unit) {
    val instrumental by viewModel.instrumentalOnly.collectAsState()
    MuSheet("Was möchtest du hören?", onClose = onClose) {
        MODE_ENTRIES.forEach { entry ->
            val active = viewModel.searchMode == entry.mode
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        if (active) HikariPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(16.dp),
                    )
                    .muPressable {
                        viewModel.selectSearchMode(entry.mode)
                        onClose()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        entry.icon, null,
                        tint = if (active) HikariPrimary else HikariTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) HikariPrimary else HikariText,
                    )
                    Text(entry.desc, fontSize = 12.sp, color = HikariTextMuted)
                }
                if (active) {
                    Icon(Icons.Default.Check, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (viewModel.searchMode == MusicSearchMode.MUSIC) {
            Spacer(Modifier.height(8.dp))
            InstrumentalToggle(
                enabled = instrumental,
                onToggle = { viewModel.toggleInstrumental() },
            )
            Spacer(Modifier.height(4.dp))
        }
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
    onOpenProfile: () -> Unit,
) {
    if (!online) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyHint(Icons.Outlined.CloudDownload, "Entdecken braucht Internet — deine Downloads warten in deiner Bibliothek.")
            MuGhostButton("Zur Bibliothek", onClick = onOpenProfile)
        }
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
        // Aktives Suchfeld: Verlauf (alle Modi) und Genres (nur Musik) bzw.
        // Vorschläge statt der Entdecken-Inhalte.
        if (viewModel.searchActive && !searching) {
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
                // Die Genre-Kacheln sind Musik — in den anderen Modi bleibt
                // unter dem leeren Suchfeld nur der Verlauf.
                if (musicMode) {
                    item(key = "genre-browse") {
                        GenreBrowseGrid(onOpenGenre = { title, query ->
                            onOpenMix(title, query, MusicSearchMode.MUSIC.apiValue)
                        })
                    }
                }
            } else {
                item(key = "suggestions") {
                    SuggestionsList(
                        query = viewModel.searchQuery,
                        suggestions = viewModel.suggestions,
                        onSelectQuery = {
                            viewModel.searchQuery = it
                            viewModel.search(it)
                        },
                        onPlaySong = { s ->
                            val videoId = s.videoId ?: return@SuggestionsList
                            viewModel.noteSearch(s.text)
                            val song = MusicSong(
                                videoId = videoId,
                                title = s.text,
                                uploader = s.subtitle.orEmpty(),
                                uploaderUrl = "",
                                thumbnailUrl = s.thumbnailUrl
                                    ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                                duration = 0,
                                views = 0,
                            )
                            viewModel.play(song, listOf(song))
                        },
                        onOpenArtist = { channelId, name ->
                            viewModel.noteSearch(name)
                            onOpenArtist(channelId, name)
                        },
                        onOpenCollection = { playlistId, name, isAlbum ->
                            viewModel.noteSearch(name)
                            onOpenCollection(playlistId, name, isAlbum)
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

        // Schnellauswahl: meistgehörte Songs der letzten 7 Tage.
        if (!searching && viewModel.topWeekSongs.size >= 4) {
            item(key = "top-week") {
                TopWeekQuickPicks(
                    songs = viewModel.topWeekSongs,
                    currentVideoId = currentSong?.videoId,
                    onPlay = { song -> viewModel.play(song, viewModel.topWeekSongs) },
                )
            }
        }

        if (searching) {
            // Smart-Search-Ergebnisse in allen Modi: Filter-Chips + Sektionen.
            smartSearchResults(
                viewModel = viewModel,
                mode = searchMode,
                instrumental = instrumental,
                currentVideoId = currentSong?.videoId,
                downloadedIds = downloadedIds,
                progressMap = progressMap,
                online = online,
                onOpenArtist = onOpenArtist,
                onOpenCollection = onOpenCollection,
                onOpenGroup = onOpenGroup,
            )
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

    // Kuratierte Mixe als Karten-Reihe — die Cover bringen Farbe und
    // Abwechslung in den Feed (gleiche Optik wie im Genre-Entdecken).
    val curated = sections.filter { it.kind == HomeSectionKind.CURATED && it.songs.isNotEmpty() }
    if (curated.isNotEmpty()) {
        item(key = "home-mixes-header") { SectionHeader("Mixe", eyebrow = "KURATIERT") }
        item(key = "home-mixes-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(curated, key = { "hm-${it.title}" }) { section ->
                    MixCard(
                        title = section.title,
                        songs = section.songs,
                        onClick = { onOpenMix(section.title, section.query, MusicSearchMode.MUSIC.apiValue) },
                    )
                }
            }
        }
    }

    // Abwechslungs-Rhythmus wie früher: Kachelreihe und kompakte Liste im
    // Wechsel — "Ähnlich wie X" und die Charts laufen als Schnellauswahl-Karten.
    var songRowToggle = 0
    var chartsShown = false
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
            if (section.kind == HomeSectionKind.SIMILAR) {
                // Explizit gewünscht: "Ähnlich wie X" im Schnellauswahl-Stil —
                // 2×2-Karte, 4 Songs pro Seite, Punkt-Navigation.
                item(key = "hc-$index") {
                    QuickPickPagerCard(
                        songs = section.songs,
                        currentVideoId = currentVideoId,
                        showRanks = false,
                        onPlay = { song -> viewModel.play(song, section.songs) },
                    )
                }
            } else if (section.kind == HomeSectionKind.CURATED && !chartsShown) {
                // Erste kuratierte Sektion ("Top Charts"): Schnellauswahl-Karte
                // mit Rang-Nummern — ebenfalls explizit gewünscht.
                chartsShown = true
                item(key = "hcharts-$index") {
                    QuickPickPagerCard(
                        songs = section.songs,
                        currentVideoId = currentVideoId,
                        showRanks = true,
                        onPlay = { song -> viewModel.play(song, section.songs) },
                    )
                }
            } else if (songRowToggle++ % 2 == 0) {
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
        }
    }
}

/**
 * Ergebnisbereich der Smart-Search (alle Modi): Filter-Chips bleiben als
 * Sticky-Header beim Scrollen sichtbar, darunter bei „Alle“ die
 * Vollsuche-Sektionen — leere Sektionen bleiben unsichtbar. Im Musik-Modus
 * laden die Filter ihre großen Trefferlisten lazy nach; in den anderen Modi
 * filtern sie die schon geladene Vollsuche clientseitig.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LazyListScope.smartSearchResults(
    viewModel: MusicViewModel,
    mode: MusicSearchMode,
    instrumental: Boolean,
    currentVideoId: String?,
    downloadedIds: Set<String>,
    progressMap: Map<String, Float>,
    online: Boolean,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
    onOpenGroup: (title: String, unit: String, query: String, mode: String) -> Unit,
) {
    val musicMode = mode == MusicSearchMode.MUSIC
    // Modusgerechte Chip-Beschriftung: außerhalb der Musik gibt es keine
    // Alben, und "Songs/Künstler" heißen dort "Inhalte/Kanäle".
    val filterEntries = if (musicMode) {
        MusicSearchFilter.entries.toList()
    } else {
        listOf(
            MusicSearchFilter.ALLE,
            MusicSearchFilter.SONGS,
            MusicSearchFilter.KUENSTLER,
            MusicSearchFilter.PLAYLISTS,
        )
    }
    val labelFor: (MusicSearchFilter) -> String = { filter ->
        when {
            musicMode -> filter.label
            filter == MusicSearchFilter.SONGS -> "Inhalte"
            filter == MusicSearchFilter.KUENSTLER -> "Kanäle"
            else -> filter.label
        }
    }
    val songBadge: (MusicSong) -> String? = { song ->
        if (mode == MusicSearchMode.TRUECRIME) viewModel.languageBadge(song) else null
    }
    val groupUnit = if (mode == MusicSearchMode.AUDIOBOOK) "Kapitel" else "Folgen"

    stickyHeader(key = "filter-chips") {
        // Eigener Grund hinter dem Panel, damit beim Scrollen nichts durchscheint.
        Box(Modifier.fillMaxWidth().background(HikariBg).padding(bottom = 10.dp)) {
            ResultFilterChips(
                selected = viewModel.activeFilter,
                entries = filterEntries,
                labelFor = labelFor,
                onSelect = { viewModel.selectFilter(it) },
            )
        }
    }

    if (viewModel.searchLoading) {
        item(key = "search-loading") { CenteredLoader() }
        return
    }

    // Gruppen (Hörbücher/Shows) gibt es nur außerhalb des Musik-Modus.
    fun LazyListScope.groupRows() {
        items(viewModel.searchGroups, key = { "g-${it.uploader}" }) { group ->
            GroupRow(
                group = group,
                unitLabel = groupUnit,
                badge = songBadge(group.chapters.first()),
                onClick = {
                    onOpenGroup(group.uploader, groupUnit, viewModel.searchQuery, mode.apiValue)
                },
            )
        }
    }

    when (viewModel.activeFilter) {
        MusicSearchFilter.ALLE -> {
            val full = viewModel.fullResults
            val nothingFound = full == null ||
                (full.topResult == null && full.songs.isEmpty() && full.artists.isEmpty() &&
                    full.albums.isEmpty() && full.playlists.isEmpty())
            if (nothingFound) {
                item(key = "search-empty") {
                    if (musicMode && instrumental) {
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
            if (!musicMode && viewModel.searchGroups.isNotEmpty()) {
                item(key = "groups-header") { SectionHeader(if (mode == MusicSearchMode.AUDIOBOOK) "Hörbücher" else "Shows") }
                groupRows()
            }
            // Außerhalb der Musik zeigen die Song-Zeilen nur die Einzelgänger —
            // alles Gruppierte steht schon oben bei den Shows.
            val songList = if (musicMode) full.songs else viewModel.searchResults
            if (songList.isNotEmpty()) {
                item(key = "songs-header") { SectionHeader(if (musicMode) "Songs" else "Inhalte") }
                items(songList, key = { "s-${it.videoId}" }) { song ->
                    SongRow(
                        song,
                        viewModel,
                        songList,
                        isCurrent = currentVideoId == song.videoId,
                        isDownloaded = song.videoId in downloadedIds,
                        progress = progressMap[song.videoId],
                        online = online,
                        badge = songBadge(song),
                        onOpenArtist = onOpenArtist,
                    )
                }
            }
            if (full.artists.isNotEmpty()) {
                item(key = "artists-header") { SectionHeader(if (musicMode) "Künstler" else "Kanäle") }
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
            // Musik: lazy nachgeladene große Liste; sonst Gruppen + alle Inhalte
            // aus der Vollsuche.
            val songs = if (musicMode) viewModel.typedSongs else viewModel.searchResults
            when {
                musicMode && viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                songs.isEmpty() && (musicMode || viewModel.searchGroups.isEmpty()) -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> {
                    if (!musicMode) groupRows()
                    items(songs, key = { "ts-${it.videoId}" }) { song ->
                        SongRow(
                            song,
                            viewModel,
                            songs,
                            isCurrent = currentVideoId == song.videoId,
                            isDownloaded = song.videoId in downloadedIds,
                            progress = progressMap[song.videoId],
                            online = online,
                            badge = songBadge(song),
                            onOpenArtist = onOpenArtist,
                        )
                    }
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
            val artists = if (musicMode) viewModel.typedArtists else viewModel.fullResults?.artists.orEmpty()
            when {
                musicMode && viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                artists.isEmpty() -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(artists, key = { "tk-${it.channelId}" }) { artist ->
                    ArtistResultRow(artist, onClick = { onOpenArtist(artist.channelId, artist.name) })
                }
            }
        }
        MusicSearchFilter.PLAYLISTS -> {
            val playlists = if (musicMode) viewModel.typedPlaylists else viewModel.fullResults?.playlists.orEmpty()
            when {
                musicMode && viewModel.typedLoading -> item(key = "typed-loading") { CenteredLoader() }
                playlists.isEmpty() -> item(key = "typed-empty") {
                    EmptyHint(Icons.Default.Search, "Nichts gefunden — anderer Suchbegriff?")
                }
                else -> items(playlists, key = { "tp-${it.playlistId}" }) { playlist ->
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

    // Erste Sektion als Rangliste im Schnellauswahl-Stil: 2×2-Karte,
    // 4 Songs pro Seite, Punkt-Navigation, fortlaufende Rang-Nummern.
    val charts = sections.first()
    item(key = "charts-header") {
        SectionHeader(charts.title, onSeeAll = { onOpenMix(charts.title, charts.query, mode) })
    }
    item(key = "charts-card") {
        QuickPickPagerCard(
            songs = charts.songs,
            currentVideoId = currentVideoId,
            showRanks = true,
            onPlay = { song -> viewModel.play(song, charts.songs) },
        )
    }

    // Restliche Sektionen im alten Abwechslungs-Rhythmus: Kachelreihe und
    // kompakte Liste im Wechsel — nur die Charts oben laufen als Karussell.
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

/**
 * Suchfeld als Glas-Pille mit dezenten Mikro-Animationen: Fokus bringt einen
 * weichen äußeren Glow-Ring und einen Feder-Pop des Such-Icons, das Löschen-✕
 * skaliert ein und aus, beim Tippen hellt der Trenner zum Modus-Icon kurz auf,
 * und im Ruhezustand läuft alle ~9 s ein einmaliger Schimmer über die Border.
 */
@Composable
private fun MusicSearchField(
    value: String,
    placeholder: String,
    filtered: Boolean,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focusT by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "searchFocus")
    val pressScale by animateFloatAsState(if (pressed) 0.99f else 1f, tween(120), label = "searchPress")
    val borderColor by animateColorAsState(
        if (focused) HikariPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
        tween(250), label = "searchBorder",
    )
    val shape = RoundedCornerShape(27.dp)
    val outerShape = RoundedCornerShape(30.dp)

    // Feder-Pop des Such-Icons beim Fokussieren.
    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused) {
            iconScale.snapTo(1f)
            iconScale.animateTo(1.15f, tween(110))
            iconScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
        }
    }

    // Tipp-Puls: der Trenner zum Modus-Icon hellt bei jeder Eingabe kurz auf.
    val typePulse = remember { Animatable(0f) }
    LaunchedEffect(value) {
        if (focused && value.isNotEmpty()) {
            typePulse.snapTo(1f)
            typePulse.animateTo(0f, tween(550))
        }
    }

    // Einmaliger Ruhe-Schimmer alle ~9 s entlang der Border (nur unfokussiert).
    val idleT by rememberInfiniteTransition(label = "searchIdle").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "searchIdleT",
    )
    val shimmerP = ((idleT * 9000f) - 7800f) / 1200f

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
            Box(Modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }) {
                // Weicher äußerer Glow-Ring bei Fokus.
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, HikariPrimary.copy(alpha = 0.20f * focusT), outerShape),
                )
                Row(
                    Modifier
                        .padding(3.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(shape)
                        .background(HikariCardBg.copy(alpha = 0.92f))
                        .border(if (focused) 1.5.dp else 1.dp, borderColor, shape)
                        .drawWithContent {
                            drawContent()
                            if (!focused && shimmerP in 0f..1f) {
                                val band = size.width * 0.35f
                                val startX = (size.width + band) * shimmerP - band
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        0.5f to Color.White.copy(alpha = 0.12f),
                                        1f to Color.Transparent,
                                        startX = startX,
                                        endX = startX + band,
                                    ),
                                    cornerRadius = CornerRadius(27.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search, null,
                        tint = lerp(HikariTextMuted, HikariPrimary, focusT),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value },
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Crossfade(placeholder, animationSpec = tween(250), label = "searchHint") { hint ->
                                Text(hint, color = HikariTextFaint, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                        inner()
                    }
                    AnimatedVisibility(
                        visible = value.isNotEmpty(),
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120)),
                    ) {
                        MuIconButton(
                            Icons.Default.Close, "Löschen",
                            iconSize = 16.dp, touchSize = 36.dp,
                            onClick = onClear,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    // Modus-Zugang IN der Leiste: Hairline-Trenner + Tune-Icon;
                    // aktiver Filter meldet sich nur über einen 6-dp-Amber-Punkt.
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(
                                lerp(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.30f),
                                    typePulse.value,
                                ),
                            ),
                    )
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .muPressable(onClick = onModeClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Tune, "Modus wählen",
                            tint = if (filtered) HikariPrimary else HikariTextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                        if (filtered) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(HikariPrimary),
                            )
                        }
                    }
                }
            }
        },
    )
}

