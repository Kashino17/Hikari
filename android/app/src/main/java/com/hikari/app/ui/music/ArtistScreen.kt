package com.hikari.app.ui.music

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.ArtistAlbum
import com.hikari.app.domain.model.ArtistPlaylist
import com.hikari.app.domain.model.SearchArtist
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val PLAYLIST_CARD_WIDTH = 208.dp
private val PLAYLIST_CARD_HEIGHT = 117.dp

/**
 * Artist-Seite im YouTube-Music-Stil: Profil, Top-Songs, Alben, Singles,
 * Playlists und ähnliche Künstler — alles aus einem Backend-Call, garantiert
 * vom richtigen Kanal. [fallbackName] dient nur noch dem Fallback gegen alte
 * Backends ohne den Page-Endpunkt.
 */
@Composable
fun ArtistScreen(
    channelId: String,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val page = viewModel.artistPage
    val artist = page?.artist
    val topSongs = page?.topSongs.orEmpty()
    val latest = page?.latest.orEmpty()
    val playlists = page?.playlists.orEmpty()
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    // Kanal-Ansicht: Umschalter zwischen den beliebtesten und neuesten Videos.
    var channelTab by rememberSaveable { mutableIntStateOf(0) }
    // Beschreibung ist eingeklappt 3 Zeilen — aufklappbar statt abgeschnitten.
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(channelId, fallbackName) { viewModel.loadArtist(channelId, fallbackName) }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    viewModel.artistLoading && artist == null -> CenteredLoader()
                    viewModel.artistFailed && artist == null -> Column(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = HikariTextFaint, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Künstler konnte nicht geladen werden",
                            color = HikariTextMuted,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        MuGhostButton("Erneut versuchen", Icons.Default.Refresh) {
                            viewModel.loadArtist(channelId, fallbackName)
                        }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        // Hero im Spotify-Stil: Name groß IM Bild, Verifiziert-
                        // Haken dezent daneben — keine Abo-/Aufruf-Zahlen.
                        // Scrollt komplett mit weg (nichts klebt oben fest).
                        item(key = "header") {
                            CollectionHero(
                                imageUrl = artist?.avatarUrl,
                                title = artist?.name ?: fallbackName,
                                subtitle = null,
                                height = 300.dp,
                                verified = artist?.verified == true,
                            )
                        }

                        val description = artist?.description.orEmpty()
                        if (description.isNotBlank()) {
                            item(key = "about") {
                                // Aufklappbar statt hart abgeschnitten — der
                                // volle Text ist sonst unerreichbar.
                                Column(
                                    Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateContentSize()
                                ) {
                                    Text(
                                        description,
                                        fontSize = 13.sp,
                                        color = HikariTextMuted,
                                        lineHeight = 18.sp,
                                        maxLines = if (aboutExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (description.length > 120) {
                                        val chevron by animateFloatAsState(
                                            if (aboutExpanded) 180f else 0f,
                                            tween(220),
                                            label = "aboutChevron",
                                        )
                                        Row(
                                            Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .muPressable { aboutExpanded = !aboutExpanded }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                if (aboutExpanded) "Weniger anzeigen" else "Mehr anzeigen",
                                                fontSize = 12.sp,
                                                color = HikariPrimary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                null,
                                                tint = HikariPrimary,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .graphicsLayer { rotationZ = chevron },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Normale YouTube-Kanäle (True Crime, Podcasts) haben
                        // keine Music-Sektionen — dann sind die Treffer schlicht
                        // "Videos", nicht "Top-Songs".
                        val isPlainChannel = page != null &&
                            page.albums.isEmpty() && page.singles.isEmpty() && page.related.isEmpty()

                        // Aktionen im Spotify-Layout: rechtsbündig ein dezenter
                        // Zufällig-Chip neben dem runden Amber-Play-Knopf.
                        if (topSongs.isNotEmpty()) {
                            item(key = "actions") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    GhostIconChip(Icons.Default.Shuffle, "Zufällig") {
                                        val shuffled = topSongs.shuffled()
                                        viewModel.play(shuffled.first(), shuffled)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    PlayRoundButton { viewModel.play(topSongs.first(), topSongs) }
                                }
                            }
                            // Kompakte Top-5/Neuste-5-Auswahl — bei Kanälen UND
                            // Music-Artists, sobald es "Neuste" gibt; der Rest
                            // der Beliebten folgt als eigene Sektion darunter.
                            if (latest.isNotEmpty()) {
                                item(key = "channel-tabs") {
                                    ChannelListTabs(channelTab) { channelTab = it }
                                }
                                val tabQueue = if (channelTab == 0) topSongs else latest
                                itemsIndexed(
                                    tabQueue.take(5),
                                    key = { _, s -> "tab-${s.videoId}" },
                                ) { i, song ->
                                    SongRow(
                                        song,
                                        viewModel,
                                        tabQueue,
                                        isCurrent = currentSong?.videoId == song.videoId,
                                        isDownloaded = song.videoId in downloadedIds,
                                        progress = progressMap[song.videoId],
                                        online = online,
                                        number = i + 1,
                                        onOpenArtist = null,
                                    )
                                }
                            } else {
                                item(key = "top-header") {
                                    SectionHeader(if (isPlainChannel) "Videos" else "Top-Songs")
                                }
                                itemsIndexed(
                                    topSongs,
                                    key = { _, s -> "top-${s.videoId}" },
                                ) { i, song ->
                                    SongRow(
                                        song,
                                        viewModel,
                                        topSongs,
                                        isCurrent = currentSong?.videoId == song.videoId,
                                        isDownloaded = song.videoId in downloadedIds,
                                        progress = progressMap[song.videoId],
                                        online = online,
                                        number = i + 1,
                                        onOpenArtist = null,
                                    )
                                }
                            }
                        }

                        val albums = page?.albums.orEmpty()
                        if (albums.isNotEmpty()) {
                            item(key = "albums-header") { SectionHeader("Alben") }
                            item(key = "albums-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(albums, key = { "al-${it.playlistId}" }) { album ->
                                        ArtistAlbumCard(
                                            album = album,
                                            onClick = { onOpenCollection(album.playlistId, album.name, true) },
                                        )
                                    }
                                }
                            }
                        }

                        val singles = page?.singles.orEmpty()
                        if (singles.isNotEmpty()) {
                            item(key = "singles-header") { SectionHeader("Singles & EPs") }
                            item(key = "singles-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(singles, key = { "si-${it.playlistId}" }) { single ->
                                        ArtistAlbumCard(
                                            album = single,
                                            onClick = { onOpenCollection(single.playlistId, single.name, true) },
                                        )
                                    }
                                }
                            }
                        }

                        if (playlists.isNotEmpty()) {
                            item(key = "playlists-header") { SectionHeader("Playlists") }
                            item(key = "playlists-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(playlists, key = { "pl-${it.playlistId}" }) { playlist ->
                                        ArtistPlaylistCard(
                                            playlist = playlist,
                                            onClick = { onOpenCollection(playlist.playlistId, playlist.name, false) },
                                        )
                                    }
                                }
                            }
                        }

                        // Unter den Playlisten: die restlichen Beliebten (Rang 6+)
                        // — bei Kanälen zusätzlich alles noch nicht Gehörte.
                        if (latest.isNotEmpty()) {
                            val popular = topSongs.drop(5).take(10)
                            if (popular.isNotEmpty()) {
                                item(key = "popular-header") {
                                    SectionHeader(if (isPlainChannel) "Beliebte Videos" else "Beliebte Songs")
                                }
                                itemsIndexed(popular, key = { _, s -> "pop-${s.videoId}" }) { i, song ->
                                    SongRow(
                                        song,
                                        viewModel,
                                        popular,
                                        isCurrent = currentSong?.videoId == song.videoId,
                                        isDownloaded = song.videoId in downloadedIds,
                                        progress = progressMap[song.videoId],
                                        online = online,
                                        number = i + 6,
                                        onOpenArtist = null,
                                    )
                                }
                            }
                        }
                        if (isPlainChannel) {
                            val seenIds = viewModel.history.map { it.videoId }.toSet()
                            val unseen = (latest + topSongs)
                                .distinctBy { it.videoId }
                                .filter { it.videoId !in seenIds }
                                .take(10)
                            if (unseen.isNotEmpty()) {
                                item(key = "unseen-header") { SectionHeader("Noch nicht angesehen") }
                                items(unseen, key = { "new-${it.videoId}" }) { song ->
                                    SongRow(
                                        song,
                                        viewModel,
                                        unseen,
                                        isCurrent = currentSong?.videoId == song.videoId,
                                        isDownloaded = song.videoId in downloadedIds,
                                        progress = progressMap[song.videoId],
                                        online = online,
                                        onOpenArtist = null,
                                    )
                                }
                            }
                        }

                        // Gar nichts gefunden (Kanal existiert, aber weder Songs
                        // noch Sektionen): freundlicher Hinweis statt Leere.
                        if (page != null && topSongs.isEmpty() && latest.isEmpty() && albums.isEmpty() &&
                            singles.isEmpty() && playlists.isEmpty() && page.related.isEmpty()
                        ) {
                            item(key = "artist-empty") {
                                EmptyHint(
                                    Icons.Default.MusicNote,
                                    "Von diesem Kanal sind gerade keine Inhalte auffindbar — später nochmal versuchen.",
                                )
                            }
                        }

                        val related = page?.related.orEmpty()
                        if (related.isNotEmpty()) {
                            item(key = "related-header") { SectionHeader("Ähnliche Künstler") }
                            item(key = "related-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(related, key = { "re-${it.channelId}" }) { rel ->
                                        RelatedArtistBubble(
                                            artist = rel,
                                            onClick = { onOpenArtist(rel.channelId, rel.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (currentSong != null) {
                MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
            }
        }

        // Zurück-Chip schwebt über Hero bzw. Lade-/Fehlerzustand
        MuIconButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Zurück",
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.40f), CircleShape),
            tint = HikariText,
        ) { onBack() }
    }
}

/** Prominente Top/Neuste-Segmentpille der Kanal-Ansicht — volle Breite. */
@Composable
private fun ChannelListTabs(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HikariSurfaceHigh)
            .padding(4.dp),
    ) {
        listOf("Top 5", "Neuste 5").forEachIndexed { i, label ->
            val active = i == selected
            val fill by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "chTab$i")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HikariPrimary.copy(alpha = fill))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (active) Color.Black else HikariTextMuted,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                )
            }
        }
    }
}

/** Album-/Single-Karte: quadratisches Cover, Name und Jahr darunter. */
@Composable
private fun ArtistAlbumCard(album: ArtistAlbum, onClick: () -> Unit) {
    Column(Modifier.width(140.dp)) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .muPressable(onClick = onClick),
        ) {
            if (album.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = album.thumbnailUrl,
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
            album.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = HikariText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        album.year?.let {
            Text("$it", fontSize = 11.sp, color = HikariTextMuted)
        }
    }
}

/** Runder Avatar eines ähnlichen Künstlers — Tipp öffnet dessen Seite. */
@Composable
private fun RelatedArtistBubble(artist: SearchArtist, onClick: () -> Unit) {
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
            if (artist.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = artist.thumbnailUrl,
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
            artist.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Playlist-Karte: Cover mit Scrim, Name und Songanzahl direkt auf dem Bild. */
@Composable
private fun ArtistPlaylistCard(playlist: ArtistPlaylist, onClick: () -> Unit) {
    Box(
        Modifier
            .width(PLAYLIST_CARD_WIDTH)
            .height(PLAYLIST_CARD_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariSurfaceHigh)
            .muPressable(onClick = onClick),
    ) {
        if (playlist.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = playlist.thumbnailUrl,
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
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                playlist.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 0 heißt "Anzahl unbekannt" (YTM liefert bei Community-
                // Playlists oft keine) — dann lieber gar keine Zahl zeigen.
                when {
                    playlist.videoCount <= 0 -> "Playlist"
                    playlist.videoCount == 1 -> "1 Song"
                    else -> "${playlist.videoCount} Songs"
                },
                fontSize = 11.sp,
                color = HikariText.copy(alpha = 0.75f),
            )
        }
    }
}
