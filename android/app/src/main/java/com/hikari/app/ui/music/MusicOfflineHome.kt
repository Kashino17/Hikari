package com.hikari.app.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.music.OfflineCollection
import com.hikari.app.domain.music.OfflineCollectionKind
import com.hikari.app.domain.music.OfflineMusic
import com.hikari.app.domain.music.OfflineMusicBuilder
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay

/**
 * Die Musikseite ohne Verbindung zum Server.
 *
 * Statt Vorschlägen, die sich nicht streamen lassen, steht hier die eigene
 * Sammlung — und zwar nur der Teil, der als Datei auf dem Gerät liegt: alle
 * Downloads als ein Stapel zum Durchhören, zuletzt Gehörtes, Favoriten und
 * Playlists (jeweils nur ihre geladenen Songs), Interpreten und die ganze
 * Liste. Das Suchfeld durchsucht die Downloads statt das Netz.
 *
 * Kommt die Verbindung zurück, wechselt die Seite von selbst wieder zum
 * Entdecken — hier wird in Abständen nachgefragt.
 */
@Composable
internal fun MusicOfflineHome(
    viewModel: MusicViewModel,
    onOpenProfile: () -> Unit,
    onNavigateBack: (() -> Unit)?,
) {
    val downloaded by viewModel.downloadedSongs.collectAsState()
    val currentSong by viewModel.player.currentSong.collectAsState()
    val avatarPath by viewModel.avatarPath.collectAsState()
    val music = remember(downloaded, viewModel.history, viewModel.favorites, viewModel.playlists, viewModel.offlineMode) {
        OfflineMusicBuilder.build(
            downloaded = downloaded,
            history = viewModel.history,
            favorites = viewModel.favorites,
            playlists = viewModel.playlists,
            mode = viewModel.offlineMode,
        )
    }
    val query = viewModel.offlineQuery
    val openCollection = music.collection(viewModel.offlineCollectionKey)
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.refreshLibrary()
        // Still nachfragen, ob der Server wieder da ist — dann wechselt die Seite zurück.
        while (true) {
            delay(RECHECK_MS)
            viewModel.checkServer()
        }
    }

    BackHandler(enabled = openCollection != null || query.isNotEmpty()) {
        when {
            openCollection != null -> viewModel.offlineCollectionKey = null
            else -> {
                viewModel.offlineQuery = ""
                focus.clearFocus()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onNavigateBack != null) {
                    MuIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText, touchSize = 40.dp) {
                        onNavigateBack()
                    }
                } else {
                    MusicAvatarChip(avatarPath = avatarPath, size = 40.dp, onClick = onOpenProfile)
                }
                Spacer(Modifier.width(10.dp))
                OfflineSearchField(
                    value = query,
                    onValueChange = { viewModel.offlineQuery = it },
                    onClear = { viewModel.offlineQuery = "" },
                    onRetry = { viewModel.checkServer(announce = true) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (downloaded.isEmpty()) {
                EmptyHint(
                    Icons.Outlined.CloudDownload,
                    "Noch nichts auf dem Gerät. Lade Songs oder ganze Playlists herunter, solange du verbunden bist — dann spielen sie hier auch ohne Netz.",
                )
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (query.isNotBlank()) {
                    searchResults(
                        music = music,
                        query = query,
                        viewModel = viewModel,
                        currentVideoId = currentSong?.videoId,
                        onOpen = { viewModel.offlineCollectionKey = it.key },
                    )
                } else {
                    overview(
                        music = music,
                        viewModel = viewModel,
                        currentVideoId = currentSong?.videoId,
                        onOpen = { viewModel.offlineCollectionKey = it.key },
                    )
                }
            }
        }

        // Eine Sammlung schiebt sich von rechts über die Seite.
        AnimatedVisibility(
            visible = openCollection != null,
            enter = slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160)),
        ) {
            // Beim Ausblenden ist die Auswahl schon weg — die zuletzt gezeigte bleibt stehen.
            var shown by remember { mutableStateOf(openCollection) }
            if (openCollection != null) shown = openCollection
            shown?.let { collection ->
                OfflineCollectionScreen(
                    collection = collection,
                    viewModel = viewModel,
                    currentVideoId = currentSong?.videoId,
                    onBack = { viewModel.offlineCollectionKey = null },
                )
            }
        }
    }
}

private const val RECHECK_MS = 20_000L

/** Farbe der Favoriten — dieselbe wie im Offline-Bereich der Bibliothek. */
private val FavoritePink = Color(0xFFF472B6)

private fun modeLabel(mode: MusicSearchMode): String = when (mode) {
    MusicSearchMode.MUSIC -> "Musik"
    MusicSearchMode.INSTRUMENTAL -> "Instrumental"
    MusicSearchMode.AUDIOBOOK -> "Hörbücher"
    MusicSearchMode.PODCAST -> "Podcasts"
    MusicSearchMode.TRUECRIME -> "True Crime"
}

private fun songCount(count: Int): String = if (count == 1) "1 Song" else "$count Songs"

/** „23 Songs · 1 h 12 min". */
private fun collectionSummary(collection: OfflineCollection): String =
    listOfNotNull(
        songCount(collection.songs.size),
        collection.totalSeconds.takeIf { it > 0 }?.let(::formatDurationUnits),
    ).joinToString(" · ")

// ── Übersicht ────────────────────────────────────────────────────────────────

private fun LazyListScope.overview(
    music: OfflineMusic,
    viewModel: MusicViewModel,
    currentVideoId: String?,
    onOpen: (OfflineCollection) -> Unit,
) {
    // Bereiche nur anbieten, wenn aus mehr als einem etwas geladen ist.
    if (music.modes.size > 1) {
        item(key = "modes") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                item { MuChip("Alles", active = viewModel.offlineMode == null) { viewModel.offlineMode = null } }
                items(music.modes, key = { it.name }) { mode ->
                    MuChip(modeLabel(mode), active = viewModel.offlineMode == mode) { viewModel.offlineMode = mode }
                }
            }
        }
    }

    item(key = "hero") {
        val all = music.all
        RadioHeroCard(
            eyebrow = "OFFLINE · AUF DEM GERÄT",
            title = "Deine Downloads",
            subtitle = "Alles, was ohne Netz läuft — ein Tipp, und es spielt gemischt.",
            songs = all.songs,
            accent = HikariPrimary,
            isPlayingFrom = all.songs.any { it.videoId == currentVideoId },
            onOpen = { onOpen(all) },
            onPlay = { viewModel.playAllOffline(all.songs, shuffled = true) },
            footnote = collectionSummary(all),
            playDescription = "Gemischt abspielen",
        )
    }

    if (music.recent.size >= 2) {
        item(key = "recent") {
            HistoryStrip(
                songs = music.recent,
                currentVideoId = currentVideoId,
                onPlay = { song -> viewModel.playOffline(song, music.recent) },
            )
        }
    }

    if (music.shelves.isNotEmpty()) {
        item(key = "shelves") {
            SectionHeader("Playlists", eyebrow = "OHNE NETZ SPIELBAR")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(music.shelves, key = { it.key }) { collection ->
                    ShelfTile(collection, onClick = { onOpen(collection) })
                }
            }
        }
    }

    // Ein einzelner Interpret wäre dieselbe Liste noch einmal.
    if (music.artists.size >= 2) {
        item(key = "artists") {
            SectionHeader("Interpreten")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(music.artists, key = { it.key }) { artist ->
                    ArtistBubble(artist, onClick = { onOpen(artist) })
                }
            }
        }
    }

    item(key = "songs-head") {
        SectionHeader("Alle Songs", eyebrow = songCount(music.songs.size).uppercase() + " · ZULETZT GELADEN ZUERST")
    }
    items(music.songs, key = { "song-${it.videoId}" }) { song ->
        OfflineSongRow(song, viewModel, queue = music.songs, isCurrent = song.videoId == currentVideoId)
    }
}

// ── Suche in den Downloads ───────────────────────────────────────────────────

private fun LazyListScope.searchResults(
    music: OfflineMusic,
    query: String,
    viewModel: MusicViewModel,
    currentVideoId: String?,
    onOpen: (OfflineCollection) -> Unit,
) {
    val result = music.search(query)
    if (result.isEmpty) {
        item(key = "no-hit") {
            EmptyHint(Icons.Outlined.SearchOff, "Nichts auf dem Gerät zu »${query.trim()}«.")
        }
        return
    }
    if (result.collections.isNotEmpty()) {
        item(key = "hit-collections-head") { SectionHeader("Playlists & Interpreten") }
        items(result.collections, key = { "hit-${it.key}" }) { collection ->
            CollectionRow(collection, onClick = { onOpen(collection) })
        }
    }
    if (result.songs.isNotEmpty()) {
        item(key = "hit-songs-head") { SectionHeader("Songs") }
        items(result.songs, key = { "hit-song-${it.videoId}" }) { song ->
            OfflineSongRow(song, viewModel, queue = result.songs, isCurrent = song.videoId == currentVideoId)
        }
    }
}

// ── Eine Sammlung ────────────────────────────────────────────────────────────

@Composable
private fun OfflineCollectionScreen(
    collection: OfflineCollection,
    viewModel: MusicViewModel,
    currentVideoId: String?,
    onBack: () -> Unit,
) {
    // Die Song-Reihenfolge einer Playlist ist ihre eigene — dort hilft die Nummer.
    val numbered = collection.kind == OfflineCollectionKind.PLAYLIST
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "hero") {
                CollectionHero(
                    imageUrl = collection.covers.firstOrNull(),
                    title = collection.title,
                    subtitle = collectionSummary(collection) + " · auf dem Gerät",
                    height = 280.dp,
                )
            }
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayRoundButton(onClick = { viewModel.playAllOffline(collection.songs) })
                    Spacer(Modifier.width(12.dp))
                    GhostIconChip(Icons.Default.Shuffle, "Gemischt abspielen") {
                        viewModel.playAllOffline(collection.songs, shuffled = true)
                    }
                }
            }
            itemsIndexed(collection.songs, key = { _, song -> song.videoId }) { index, song ->
                OfflineSongRow(
                    song = song,
                    viewModel = viewModel,
                    queue = collection.songs,
                    isCurrent = song.videoId == currentVideoId,
                    number = if (numbered) index + 1 else null,
                )
            }
        }
        Box(
            Modifier
                .padding(start = 10.dp, top = 10.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .muPressable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Bausteine ────────────────────────────────────────────────────────────────

/** Die gewohnte Song-Zeile, fest auf „liegt auf dem Gerät, kein Netz" gestellt. */
@Composable
private fun OfflineSongRow(
    song: MusicSong,
    viewModel: MusicViewModel,
    queue: List<MusicSong>,
    isCurrent: Boolean,
    number: Int? = null,
) {
    SongRow(
        song = song,
        viewModel = viewModel,
        contextQueue = queue,
        isCurrent = isCurrent,
        isDownloaded = true,
        progress = null,
        online = false,
        number = number,
        onPlay = { viewModel.playOffline(song, queue) },
    )
}

/**
 * Suchfeld der Offline-Seite — gleiche Glas-Pille wie online, aber es
 * durchsucht die Downloads. Wo online der Modus-Knopf sitzt, steht das
 * Offline-Zeichen: ein Tipp darauf versucht, den Server wieder zu erreichen.
 */
@Composable
private fun OfflineSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = LocalFocusManager.current
    val borderColor by animateColorAsState(
        if (focused) HikariPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
        tween(250), label = "offlineSearchBorder",
    )
    val shape = RoundedCornerShape(27.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        interactionSource = interaction,
        singleLine = true,
        textStyle = TextStyle(color = HikariText, fontSize = 15.sp),
        cursorBrush = SolidColor(HikariPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .padding(3.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(HikariCardBg.copy(alpha = 0.92f))
                    .border(if (focused) 1.5.dp else 1.dp, borderColor, shape)
                    .padding(start = 16.dp, end = 6.dp),
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
                        Text("In deinen Downloads suchen…", color = HikariTextFaint, fontSize = 15.sp, maxLines = 1)
                    }
                    inner()
                }
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120)),
                ) {
                    MuIconButton(Icons.Default.Close, "Löschen", iconSize = 16.dp, touchSize = 36.dp, onClick = onClear)
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.08f)))
                Box(
                    Modifier.size(42.dp).clip(CircleShape).muPressable(onClick = onRetry),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CloudOff, "Offline — erneut verbinden",
                        tint = HikariPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

/** Favoriten oder eine Playlist als Kachel: Titelbild, Name, Zahl der spielbaren Songs. */
@Composable
private fun ShelfTile(collection: OfflineCollection, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).muPressable(onClick = onClick)) {
        CollectionCover(collection, Modifier.fillMaxWidth(), corner = 16.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            collection.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(songCount(collection.songs.size), fontSize = 12.sp, color = HikariTextMuted, maxLines = 1)
    }
}

/** Ein Interpret als runder Punkt mit dem Cover seines neuesten Songs. */
@Composable
private fun ArtistBubble(artist: OfflineCollection, onClick: () -> Unit) {
    Column(
        Modifier.width(92.dp).muPressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, tint = HikariTextFaint, modifier = Modifier.size(34.dp))
            artist.covers.firstOrNull()?.let { RoundCover(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artist.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(songCount(artist.songs.size), fontSize = 11.5.sp, color = HikariTextMuted, maxLines = 1)
    }
}

/** Eine Sammlung als Zeile — für die Treffer der Suche. */
@Composable
private fun CollectionRow(collection: OfflineCollection, onClick: () -> Unit) {
    val isArtist = collection.kind == OfflineCollectionKind.ARTIST
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .muPressable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isArtist) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(HikariSurfaceHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = HikariTextFaint, modifier = Modifier.size(22.dp))
                collection.covers.firstOrNull()?.let { RoundCover(it) }
            }
        } else {
            CollectionCover(collection, Modifier.size(48.dp), corner = 10.dp, badge = false)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(collection.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HikariText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (isArtist) "Interpret" else "Playlist") + " · " + songCount(collection.songs.size),
                fontSize = 12.sp,
                color = HikariTextMuted,
                maxLines = 1,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = HikariTextFaint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Titelbild einer Sammlung: vier Cover als Mosaik, bei weniger eines groß.
 * Favoriten tragen unten links ihr rosa Herz.
 */
@Composable
private fun CollectionCover(
    collection: OfflineCollection,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    badge: Boolean = true,
) {
    val covers = remember(collection) { collection.covers }
    val isFavorites = collection.kind == OfflineCollectionKind.FAVORITES
    val fallbackIcon: ImageVector =
        if (isFavorites) Icons.Default.Favorite else Icons.AutoMirrored.Filled.PlaylistPlay
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(corner)).background(HikariSurfaceHigh)) {
        when {
            covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                covers.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f)) {
                        pair.forEach { url -> CoverImage(url, Modifier.weight(1f).fillMaxSize()) }
                    }
                }
            }
            covers.isNotEmpty() -> CoverImage(covers.first(), Modifier.fillMaxSize())
            else -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        if (isFavorites) listOf(FavoritePink.copy(alpha = 0.55f), FavoritePink.copy(alpha = 0.16f))
                        else listOf(HikariSurfaceHigh, HikariCardBg),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(fallbackIcon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.fillMaxSize(0.34f))
            }
        }
        if (badge && isFavorites && covers.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(8.dp).size(30.dp).clip(CircleShape).background(FavoritePink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFF16161A), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CoverImage(url: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, tint = HikariTextFaint, modifier = Modifier.size(18.dp))
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Cover eines Interpreten — füllt den Kreis, das Personen-Zeichen darunter bleibt der Ersatz. */
@Composable
private fun RoundCover(url: String) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
