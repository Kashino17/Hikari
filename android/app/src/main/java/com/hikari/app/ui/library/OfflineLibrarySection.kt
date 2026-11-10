package com.hikari.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.data.db.LocalMangaArcEntity
import com.hikari.app.domain.library.OfflineLibrary
import com.hikari.app.domain.library.OfflinePlaylist
import com.hikari.app.domain.library.OfflinePlaylistKind
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.components.ResumeArtwork
import com.hikari.app.ui.library.components.CinemaPosterCard
import com.hikari.app.ui.library.components.LibrarySegment
import com.hikari.app.ui.library.components.LibrarySegmentBar
import com.hikari.app.ui.profile.formatBytes
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.io.File

/**
 * Die Bibliothek ohne Netz — aufgebaut wie die echte: derselbe Kopf, dieselben
 * Reiter, dieselben Karten und Plakate, nur gefüllt mit dem, was auf dem Gerät
 * liegt. Serien öffnen ihre gewohnte Seite, „Weiterschauen" kennt den Stand
 * vom Gerät, und die Musik steht als Playlists da (Favoriten, alle Downloads,
 * die eigenen) — jeweils nur mit den Songs, die geladen sind.
 *
 * Bewusst kein Fehlertext: sobald der Server wieder antwortet, lädt die
 * Bibliothek von selbst nach.
 */
@Composable
fun OfflineLibrary(
    library: OfflineLibrary,
    onOpenSeries: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onPlaySong: (song: MusicSong, queue: List<MusicSong>) -> Unit,
    onPlayAll: (queue: List<MusicSong>, shuffled: Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val tabs = remember(library) { OfflineTab.entries.filter { it.hasContent(library) } }
    var selected by rememberSaveable { mutableStateOf(OfflineTab.ALL) }
    // Verschwindet der Inhalt eines Reiters (letzter Download gelöscht), zurück auf „Alles".
    val tab = selected.takeIf { it in tabs } ?: OfflineTab.ALL
    var openPlaylistKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openPlaylist = library.playlists.firstOrNull { it.key == openPlaylistKey }

    fun play(video: LibraryVideoDto) = onPlayVideo(
        video.id,
        video.title,
        video.channelTitle ?: video.series_id?.let { library.seriesById[it]?.title }.orEmpty(),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineTopBar(onRetry = onRetry)

        if (library.isEmpty) {
            OfflineEmpty()
            return@Column
        }

        // Reiter lohnen erst, wenn es mehr als eine Art Inhalt gibt.
        if (tabs.size > 2) {
            LibrarySegmentBar(
                segments = tabs.map { LibrarySegment(it.label(library), it.icon) },
                selectedIndex = tabs.indexOf(tab),
                onSelect = { selected = tabs[it] },
            )
            Spacer(Modifier.height(4.dp))
        }

        // Jeder Reiter ist eine eigene Liste und beginnt oben — sonst erbte er
        // die Scroll-Position des vorigen.
        key(tab) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                when (tab) {
                    OfflineTab.ALL -> overview(
                        library = library,
                        onOpenSeries = onOpenSeries,
                        onPlay = ::play,
                        onPlaySong = onPlaySong,
                        onOpenPlaylist = { openPlaylistKey = it.key },
                        onShowMusic = { selected = OfflineTab.MUSIC },
                    )
                    OfflineTab.SERIES -> seriesTab(library, onOpenSeries, ::play)
                    OfflineTab.FILMS -> filmsTab(library, ::play)
                    OfflineTab.MUSIC -> musicTab(
                        library = library,
                        onPlaySong = onPlaySong,
                        onPlayAll = onPlayAll,
                        onOpenPlaylist = { openPlaylistKey = it.key },
                    )
                }
            }
        }
    }

    // Eine Playlist schiebt sich von rechts über die Bibliothek — wie der Kanal-Bereich.
    AnimatedVisibility(
        visible = openPlaylist != null,
        enter = slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)),
        exit = slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160)),
    ) {
        // Während des Ausblendens ist die Auswahl schon weg — die zuletzt gezeigte bleibt stehen.
        var shown by remember { mutableStateOf(openPlaylist) }
        if (openPlaylist != null) shown = openPlaylist
        shown?.let { playlist ->
            BackHandler { openPlaylistKey = null }
            OfflinePlaylistScreen(
                playlist = playlist,
                onBack = { openPlaylistKey = null },
                onPlaySong = onPlaySong,
                onPlayAll = onPlayAll,
            )
        }
    }
}

private enum class OfflineTab(val icon: ImageVector) {
    ALL(Icons.Outlined.AutoAwesome),
    SERIES(Icons.Outlined.Tv),
    FILMS(Icons.Outlined.Movie),
    MUSIC(Icons.Outlined.MusicNote),
    ;

    fun hasContent(library: OfflineLibrary): Boolean = when (this) {
        ALL -> true
        SERIES -> library.series.isNotEmpty()
        FILMS -> library.films.isNotEmpty() || library.channelVideos.isNotEmpty()
        MUSIC -> library.hasMusic
    }

    fun label(library: OfflineLibrary): String = when (this) {
        ALL -> "Alles"
        SERIES -> "Serien"
        // Liegen nur YouTube-Videos auf dem Gerät, wäre „Filme" gelogen.
        FILMS -> if (library.films.isEmpty()) "Videos" else "Filme"
        MUSIC -> "Musik"
    }
}

/** Luft zwischen den Bereichen — derselbe Abstand wie in der Bibliothek. */
private val SECTION_GAP = 40.dp

/** Farben der Musik-Bereiche: Favoriten Rosa, Playlists Violett; Amber steht für „auf dem Gerät". */
private val FavoritePink = Color(0xFFF472B6)
private val PlaylistViolet = Color(0xFFA78BFA)

private fun OfflinePlaylist.accent(): Color = when (kind) {
    OfflinePlaylistKind.FAVORITES -> FavoritePink
    OfflinePlaylistKind.DOWNLOADS -> HikariAmber
    OfflinePlaylistKind.PLAYLIST -> PlaylistViolet
}

// ── Kopf ─────────────────────────────────────────────────────────────────────

@Composable
private fun OfflineTopBar(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "HIKARI",
            color = HikariAmber,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.SansSerif,
        )
        Spacer(Modifier.weight(1f))
        // Zeigt den Zustand — und ist zugleich der Knopf, es noch einmal zu versuchen.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(HikariSurface)
                .border(0.6.dp, HikariBorderStrong, RoundedCornerShape(50))
                .clickable(onClickLabel = "Erneut verbinden", onClick = onRetry)
                .padding(start = 11.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = HikariTextMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Offline", color = HikariText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OfflineEmpty() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(76.dp).clip(CircleShape).background(HikariSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = HikariTextMuted, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Noch nichts auf dem Gerät",
            color = HikariText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Lade Serien, Filme oder Musik herunter, solange du verbunden bist — dann findest du sie hier auch ohne Netz.",
            color = HikariTextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Reiter „Alles" ───────────────────────────────────────────────────────────

private fun LazyListScope.overview(
    library: OfflineLibrary,
    onOpenSeries: (String) -> Unit,
    onPlay: (LibraryVideoDto) -> Unit,
    onPlaySong: (MusicSong, List<MusicSong>) -> Unit,
    onOpenPlaylist: (OfflinePlaylist) -> Unit,
    onShowMusic: () -> Unit,
) {
    library.hero?.let { hero ->
        item(key = "hero") {
            LibraryHero(hero, hero.series_id?.let(library.seriesById::get), onPlay = { onPlay(hero) })
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    if (library.continueWatching.isNotEmpty()) {
        item(key = "continue") {
            SectionHeader("Weiterschauen", count = library.continueWatching.size)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(library.continueWatching, key = { it.id }) { video ->
                    ContinueCard(video, video.series_id?.let(library.seriesById::get), onClick = { onPlay(video) })
                }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    if (library.series.isNotEmpty()) {
        item(key = "series") {
            SectionHeader("Serien", count = library.series.size)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(library.series, key = { it.id }) { series ->
                    SeriesPoster(series, onClick = { onOpenSeries(series.id) })
                }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    if (library.films.isNotEmpty()) {
        item(key = "films") {
            SectionHeader("Filme", count = library.films.size)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(library.films, key = { it.id }) { film ->
                    FilmPoster(film, onClick = { onPlay(film) })
                }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    if (library.hasMusic) {
        item(key = "music") {
            SectionHeader("Deine Musik", count = library.songs.size, onShowAll = onShowMusic)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(library.playlists, key = { it.key }) { playlist ->
                    PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist) }, modifier = Modifier.width(148.dp))
                }
            }
            if (library.songs.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                library.songs.take(RECENT_SONGS).forEach { song ->
                    SongRow(song, onClick = { onPlaySong(song, library.songs) })
                }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    if (library.channelVideos.isNotEmpty()) {
        item(key = "clips") {
            SectionHeader("Von deinen Kanälen", count = library.channelVideos.size)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(library.channelVideos, key = { it.id }) { video ->
                    ClipCard(video, onClick = { onPlay(video) })
                }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    if (library.manga.isNotEmpty()) {
        item(key = "manga") {
            SectionHeader("Manga", count = library.manga.size)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Kein Klick: die Manga-Seite braucht den Server für ihre Kapitelliste.
                // Lieber keine Aktion als eine, die ins Leere führt.
                items(library.manga, key = { it.arcId }) { arc -> MangaCard(arc) }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }

    item(key = "footer") { OfflineFooter(library) }
}

/** Was auf dem Gerät liegt, in einer Zeile — und dass die ganze Bibliothek von selbst zurückkommt. */
@Composable
private fun OfflineFooter(library: OfflineLibrary) {
    val summary = remember(library) {
        listOfNotNull(
            library.videoCount.takeIf { it > 0 }?.let { if (it == 1) "1 Video" else "$it Videos" },
            library.songs.size.takeIf { it > 0 }?.let { if (it == 1) "1 Song" else "$it Songs" },
            library.videoBytes.takeIf { it > 0 }?.let(::formatBytes),
        ).joinToString(" · ")
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (summary.isNotBlank()) {
            Text("$summary auf diesem Gerät", color = HikariTextMuted, fontSize = 12.5.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            "Sobald der Server wieder erreichbar ist, erscheint hier deine ganze Bibliothek.",
            color = HikariTextFaint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Reiter „Serien" ──────────────────────────────────────────────────────────

private fun LazyListScope.seriesTab(
    library: OfflineLibrary,
    onOpenSeries: (String) -> Unit,
    onPlay: (LibraryVideoDto) -> Unit,
) {
    val continueSeries = library.continueWatching.filter { it.series_id != null }
    val hero = continueSeries.firstOrNull() ?: library.series.firstOrNull()?.let { library.nextEpisode(it.id) }
    hero?.let {
        item(key = "hero") {
            LibraryHero(it, it.series_id?.let(library.seriesById::get), onPlay = { onPlay(it) })
            Spacer(Modifier.height(SECTION_GAP))
        }
    }
    if (continueSeries.size > 1) {
        item(key = "continue") {
            SectionHeader("Weiter bingen", count = continueSeries.size)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(continueSeries, key = { it.id }) { video ->
                    ContinueCard(video, video.series_id?.let(library.seriesById::get), onClick = { onPlay(video) })
                }
            }
            Spacer(Modifier.height(SECTION_GAP))
        }
    }
    item(key = "all-head") { SectionHeader("Alle Serien", count = library.series.size) }
    posterGrid(library.series, key = { it.id }) { series, width, height ->
        SeriesPoster(series, onClick = { onOpenSeries(series.id) }, width = width, height = height)
    }
}

// ── Reiter „Filme" ───────────────────────────────────────────────────────────

private fun LazyListScope.filmsTab(library: OfflineLibrary, onPlay: (LibraryVideoDto) -> Unit) {
    if (library.films.isNotEmpty()) {
        item(key = "films-head") {
            Spacer(Modifier.height(10.dp))
            SectionHeader("Filme", count = library.films.size)
        }
        posterGrid(library.films, key = { it.id }) { film, width, height ->
            FilmPoster(film, onClick = { onPlay(film) }, width = width, height = height)
        }
    }
    if (library.channelVideos.isNotEmpty()) {
        item(key = "clips-head") {
            Spacer(Modifier.height(if (library.films.isEmpty()) 10.dp else SECTION_GAP - 12.dp))
            SectionHeader("Von deinen Kanälen", count = library.channelVideos.size)
        }
        items(library.channelVideos, key = { "clip-${it.id}" }) { video ->
            ClipRow(video, onClick = { onPlay(video) })
        }
    }
}

// ── Reiter „Musik" ───────────────────────────────────────────────────────────

private fun LazyListScope.musicTab(
    library: OfflineLibrary,
    onPlaySong: (MusicSong, List<MusicSong>) -> Unit,
    onPlayAll: (List<MusicSong>, Boolean) -> Unit,
    onOpenPlaylist: (OfflinePlaylist) -> Unit,
) {
    if (library.playlists.isNotEmpty()) {
        item(key = "playlists-head") {
            Spacer(Modifier.height(10.dp))
            SectionHeader("Playlists", count = library.playlists.size)
        }
        items(library.playlists.chunked(2), key = { row -> row.joinToString("+") { it.key } }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { playlist ->
                    PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (library.songs.isNotEmpty()) {
        item(key = "songs-head") {
            Spacer(Modifier.height(SECTION_GAP - 20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alle Songs", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${library.songs.size} auf dem Gerät · ${durationLabel(library.songs.sumOf { it.duration })}",
                        color = HikariTextMuted,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                PlayActions(
                    onPlay = { onPlayAll(library.songs, false) },
                    onShuffle = { onPlayAll(library.songs, true) },
                )
            }
        }
        items(library.songs, key = { "song-${it.videoId}" }) { song ->
            SongRow(song, onClick = { onPlaySong(song, library.songs) })
        }
    }
}

// ── Eine Playlist ────────────────────────────────────────────────────────────

@Composable
private fun OfflinePlaylistScreen(
    playlist: OfflinePlaylist,
    onBack: () -> Unit,
    onPlaySong: (MusicSong, List<MusicSong>) -> Unit,
    onPlayAll: (List<MusicSong>, Boolean) -> Unit,
) {
    val accent = playlist.accent()
    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        // Die Farbe des Bereichs läuft von oben in den Hintergrund aus.
        Box(
            Modifier.fillMaxWidth().height(340.dp).background(
                Brush.verticalGradient(0f to accent.copy(alpha = 0.30f), 1f to Color.Transparent),
            ),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item(key = "head") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlaylistCover(playlist, modifier = Modifier.size(196.dp), corner = 18.dp, badge = false)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        playlist.name,
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Text(
                        text = playlistSummary(playlist),
                        color = HikariTextMuted,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    PlayActions(
                        onPlay = { onPlayAll(playlist.songs, false) },
                        onShuffle = { onPlayAll(playlist.songs, true) },
                        large = true,
                    )
                }
            }
            items(playlist.songs, key = { it.videoId }) { song ->
                SongRow(song, onClick = { onPlaySong(song, playlist.songs) })
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** „23 Songs · 1 h 12 min". */
private fun playlistSummary(playlist: OfflinePlaylist): String =
    "${playlistCaption(playlist)} · ${durationLabel(playlist.totalSeconds)}"

/** Kurzfassung unter der Playlist-Karte. */
private fun playlistCaption(playlist: OfflinePlaylist): String =
    if (playlist.songs.size == 1) "1 Song" else "${playlist.songs.size} Songs"

// ── Bausteine ────────────────────────────────────────────────────────────────

@Composable
private fun SeriesPoster(series: SeriesDto, onClick: () -> Unit, width: Dp = 120.dp, height: Dp = 175.dp) {
    CinemaPosterCard(
        title = series.title,
        subtitle = seriesScope(series),
        thumbnailUrl = series.thumbnail_url,
        posterUrl = series.poster_url,
        onClick = onClick,
        width = width,
        height = height,
    )
}

@Composable
private fun FilmPoster(film: LibraryVideoDto, onClick: () -> Unit, width: Dp = 120.dp, height: Dp = 175.dp) {
    CinemaPosterCard(
        title = film.title,
        subtitle = durationLabel(film.duration_seconds),
        thumbnailUrl = film.thumbnail_url,
        posterUrl = film.poster_url,
        onClick = onClick,
        width = width,
        height = height,
    )
}

/** Plakate in drei Spalten über die ganze Breite — jede Zeile ein Eintrag der Liste. */
private fun <T> LazyListScope.posterGrid(
    entries: List<T>,
    key: (T) -> String,
    poster: @Composable (item: T, width: Dp, height: Dp) -> Unit,
) {
    items(entries.chunked(GRID_COLUMNS), key = { row -> "grid-" + row.joinToString("+", transform = key) }) { row ->
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            val gap = 10.dp
            val width = (maxWidth - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { item -> poster(item, width, width * POSTER_RATIO) }
            }
        }
    }
}

/** Ein YouTube-Video in einer Reihe: Bild im Format 16:9, darunter Titel und Kanal. */
@Composable
private fun ClipCard(video: LibraryVideoDto, onClick: () -> Unit) {
    Column(modifier = Modifier.width(236.dp).clickable(onClick = onClick)) {
        ClipThumbnail(video, modifier = Modifier.fillMaxWidth(), corner = 12.dp)
        Text(
            text = video.title,
            color = HikariText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        video.channelTitle?.takeIf { it.isNotBlank() }?.let { channel ->
            Text(channel, color = HikariTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Dasselbe Video als Zeile einer Liste: Bild links, Titel und Kanal rechts. */
@Composable
private fun ClipRow(video: LibraryVideoDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipThumbnail(video, modifier = Modifier.width(152.dp), corner = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = HikariText,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            video.channelTitle?.takeIf { it.isNotBlank() }?.let { channel ->
                Text(channel, color = HikariTextMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ClipThumbnail(video: LibraryVideoDto, modifier: Modifier, corner: Dp) {
    val fraction = progressFraction(video)
    Box(modifier = modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(corner)).background(HikariSurface)) {
        ResumeArtwork(video = video, modifier = Modifier.fillMaxSize(), resumeFrame = false)
        if (video.duration_seconds > 0) {
            Text(
                clockLabel(video.duration_seconds),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (fraction > 0f) ProgressLine(fraction, height = 3, modifier = Modifier.align(Alignment.BottomStart))
    }
}

/** Eine Playlist als Karte: Titelbild aus ihren Covern, darunter Name und wie viel davon offline geht. */
@Composable
private fun PlaylistCard(playlist: OfflinePlaylist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        PlaylistCover(playlist, modifier = Modifier.fillMaxWidth())
        Text(
            playlist.name,
            color = HikariText,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            playlistCaption(playlist),
            color = HikariTextMuted,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

/**
 * Das Titelbild einer Playlist: vier Cover als Mosaik, bei weniger eines groß.
 * Ein farbiges Zeichen unten links sagt, was für eine Liste es ist (Favoriten,
 * alle Downloads, eigene Playlist). Ohne ein einziges Cover trägt die Farbe
 * des Bereichs die Fläche.
 */
@Composable
private fun PlaylistCover(
    playlist: OfflinePlaylist,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    badge: Boolean = true,
) {
    val covers = remember(playlist) { playlist.covers }
    val accent = playlist.accent()
    val icon = when (playlist.kind) {
        OfflinePlaylistKind.FAVORITES -> Icons.Filled.Favorite
        OfflinePlaylistKind.DOWNLOADS -> Icons.Outlined.DownloadDone
        OfflinePlaylistKind.PLAYLIST -> Icons.AutoMirrored.Outlined.QueueMusic
    }
    Box(modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(corner)).background(HikariSurface)) {
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
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.16f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.fillMaxSize(0.34f))
            }
        }
        if (badge && covers.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF16161A), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CoverImage(url: String, modifier: Modifier) {
    AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

/** Abspielen (weiß, wie überall in der App) und Mischen als ein Paar. */
@Composable
private fun PlayActions(onPlay: () -> Unit, onShuffle: () -> Unit, large: Boolean = false) {
    val size = if (large) 50.dp else 42.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                .clickable(onClick = onShuffle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Shuffle, contentDescription = "Zufällig abspielen", tint = HikariText, modifier = Modifier.size(if (large) 22.dp else 19.dp))
        }
        Spacer(Modifier.width(if (large) 14.dp else 10.dp))
        if (large) {
            Row(
                modifier = Modifier
                    .height(size)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onPlay)
                    .padding(start = 18.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text("Abspielen", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                modifier = Modifier.size(size).clip(CircleShape).background(Color.White).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Alle abspielen", tint = Color.Black, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Ein Song als ruhige Zeile: Cover, Titel, Interpret und Länge — ein rosa Herz, wenn er Favorit ist. */
@Composable
private fun SongRow(song: MusicSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(HikariSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = HikariTextFaint, modifier = Modifier.size(20.dp))
            if (song.thumbnailUrl.isNotBlank()) CoverImage(song.thumbnailUrl, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = HikariText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    song.uploader.takeIf { it.isNotBlank() },
                    song.duration.takeIf { it > 0 }?.let(::clockLabel),
                ).joinToString(" · "),
                color = HikariTextMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (song.isFavorite) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.Favorite, contentDescription = "Favorit", tint = FavoritePink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MangaCard(arc: LocalMangaArcEntity) {
    Column(modifier = Modifier.width(120.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(HikariSurface),
        ) {
            // Das Cover liegt als Datei auf dem Gerät — Coil lädt sie direkt, ohne Netz.
            FallbackArtwork(title = arc.seriesTitle)
            AsyncImage(
                model = remember(arc.seriesCoverPath) { arc.seriesCoverPath?.let(::File) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = arc.seriesTitle,
            color = HikariText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "${arc.arcTitle} · ${arc.expectedPageCount} Seiten",
            color = HikariTextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Wie viele der zuletzt geladenen Songs die Übersicht zeigt — der Rest steht im Reiter „Musik". */
private const val RECENT_SONGS = 4
private const val GRID_COLUMNS = 3

/** Höhe zu Breite eines Plakats (120 × 175 wie in den Reihen). */
private const val POSTER_RATIO = 175f / 120f
