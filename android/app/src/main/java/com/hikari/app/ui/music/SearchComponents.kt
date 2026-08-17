package com.hikari.app.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hikari.app.domain.model.MusicAlbum
import com.hikari.app.domain.model.MusicSearchResult
import com.hikari.app.domain.model.RemotePlaylist
import com.hikari.app.domain.model.SearchArtist
import com.hikari.app.domain.model.SearchSuggestion
import com.hikari.app.domain.model.SuggestionKind
import com.hikari.app.domain.repo.MusicSearchFilter
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Panel-Container für alles, was unter der Suchleiste andockt (Verlauf,
 * Vorschläge, Ergebnisfilter): gleiche Breite wie die Pille, oben eckig
 * anschließend, unten gerundet — fährt sanft aus der Bar aus. Der Grundton
 * ist derselbe wie der der Pille, damit beide als eine Einheit wirken.
 */
@Composable
internal fun SearchDockPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val shape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = 16.dp, bottomEnd = 16.dp,
    )
    AnimatedVisibility(
        visibleState = visible,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(shape)
                .background(HikariCardBg.copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), shape),
            content = content,
        )
    }
}

/**
 * Gespeicherte Suchanfragen unter dem leeren Suchfeld: Antippen sucht erneut,
 * das X entfernt den Eintrag, der Footer räumt den ganzen Verlauf auf.
 */
@Composable
fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    SearchDockPanel {
        Spacer(Modifier.height(6.dp))
        history.forEach { query ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .muPressable { onSelect(query) }
                    .padding(start = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, null, tint = HikariTextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    query,
                    fontSize = 14.sp,
                    color = HikariText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MuIconButton(
                    Icons.Default.Close, "Eintrag entfernen",
                    iconSize = 16.dp, touchSize = 40.dp,
                    onClick = { onRemove(query) },
                )
            }
        }
        Text(
            "Verlauf löschen",
            fontSize = 13.sp,
            color = HikariTextMuted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(999.dp))
                .muPressable(onClick = onClearAll)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * Vorschläge zur laufenden Eingabe. Der getippte Text selbst bleibt die erste
 * Option; reine Query-Vorschläge zeigen das Such-Icon, Entity-Vorschläge
 * (Songs, Künstler, Alben, Playlists der ersten Suchergebnisse) bringen ein
 * Mini-Thumbnail mit und springen direkt zum Ziel.
 */
@Composable
fun SuggestionsList(
    query: String,
    suggestions: List<SearchSuggestion>,
    onSelectQuery: (String) -> Unit,
    onPlaySong: (SearchSuggestion) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
) {
    SearchDockPanel {
        Spacer(Modifier.height(4.dp))
        SuggestionRow(text = query, typed = query, onClick = { onSelectQuery(query) })
        suggestions
            .filter { !(it.kind == SuggestionKind.QUERY && it.text.equals(query, ignoreCase = true)) }
            .forEach { s ->
                val hasTarget = s.videoId != null || s.channelId != null || s.playlistId != null
                if (s.kind == SuggestionKind.QUERY || !hasTarget) {
                    SuggestionRow(text = s.text, typed = query, onClick = { onSelectQuery(s.text) })
                } else {
                    EntitySuggestionRow(
                        suggestion = s,
                        typed = query,
                        onClick = {
                            when (s.kind) {
                                SuggestionKind.SONG, SuggestionKind.VIDEO ->
                                    if (s.videoId != null) onPlaySong(s) else onSelectQuery(s.text)
                                SuggestionKind.ARTIST ->
                                    s.channelId?.let { onOpenArtist(it, s.text) }
                                        ?: onSelectQuery(s.text)
                                SuggestionKind.ALBUM ->
                                    s.playlistId?.let { onOpenCollection(it, s.text, true) }
                                        ?: onSelectQuery(s.text)
                                SuggestionKind.PLAYLIST ->
                                    s.playlistId?.let { onOpenCollection(it, s.text, false) }
                                        ?: onSelectQuery(s.text)
                                SuggestionKind.QUERY -> onSelectQuery(s.text)
                            }
                        },
                    )
                }
            }
        Spacer(Modifier.height(4.dp))
    }
}

/** Entity-Vorschlag: Mini-Thumbnail (rund bei Künstlern), Titel + Untertyp. */
@Composable
private fun EntitySuggestionRow(
    suggestion: SearchSuggestion,
    typed: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .muPressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbPx = with(LocalDensity.current) { 36.dp.roundToPx() }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(suggestion.thumbnailUrl)
                .size(thumbPx)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(
                    if (suggestion.kind == SuggestionKind.ARTIST) CircleShape
                    else RoundedCornerShape(8.dp),
                )
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlightMatch(suggestion.text, typed),
                fontSize = 14.sp,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = suggestion.subtitle ?: when (suggestion.kind) {
                SuggestionKind.SONG -> "Song"
                SuggestionKind.VIDEO -> "Video"
                SuggestionKind.ARTIST -> "Künstler"
                SuggestionKind.ALBUM -> "Album"
                SuggestionKind.PLAYLIST -> "Playlist"
                SuggestionKind.QUERY -> null
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = HikariTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = HikariTextFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SuggestionRow(text: String, typed: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .muPressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = HikariTextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            highlightMatch(text, typed),
            fontSize = 14.sp,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Der getippte Teil des Vorschlags wird fett gesetzt, der Rest bleibt normal. */
private fun highlightMatch(text: String, typed: String): AnnotatedString = buildAnnotatedString {
    val index = text.indexOf(typed, ignoreCase = true)
    if (index < 0 || typed.isEmpty()) {
        append(text)
    } else {
        append(text.substring(0, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(index, index + typed.length))
        }
        append(text.substring(index + typed.length))
    }
}

private data class GenreEntry(val title: String, val query: String, val color: Color)

/** Kuratierte Genres des „Stöbern“-Grids — Titel mit der Suche dahinter. */
private val GENRES = listOf(
    GenreEntry("Lo-Fi", "lofi hip hop beats", Color(0xFF7E6BB8)),
    GenreEntry("Hip-Hop", "hip hop 2026", Color(0xFFB2503C)),
    GenreEntry("Deutschrap", "deutschrap 2026", Color(0xFF444B52)),
    GenreEntry("R'n'B", "r&b hits", Color(0xFF9C5B8F)),
    GenreEntry("Pop", "pop hits 2026", Color(0xFFD0607E)),
    GenreEntry("US-Charts", "billboard hot 100", Color(0xFF2E6FA7)),
    GenreEntry("Dance", "edm dance mix", Color(0xFF2F9E8F)),
    GenreEntry("Rock", "rock classics", Color(0xFF8C3A2B)),
    GenreEntry("Indie", "indie mix", Color(0xFF5B8C5A)),
    GenreEntry("Jazz", "jazz lounge", Color(0xFF9E7B2D)),
    GenreEntry("Klassik", "classical music", Color(0xFF6B7A8F)),
    GenreEntry("Metal", "metal mix", Color(0xFF4A4A55)),
    GenreEntry("Workout", "workout motivation music", Color(0xFFC7482D)),
    GenreEntry("Chill", "chill vibes mix", Color(0xFF3D8A80)),
    GenreEntry("K-Pop", "kpop hits", Color(0xFFD05FA0)),
    GenreEntry("Latin", "latin hits", Color(0xFFC7742C)),
)

/**
 * Genre-Kacheln unter dem leeren Suchfeld — der Einstieg für alle, die nicht
 * wissen, wonach sie suchen wollen. Antippen öffnet den Mix hinter dem Genre.
 */
@Composable
fun GenreBrowseGrid(onOpenGenre: (title: String, query: String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        MuSectionTitle("Stöbern")
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GENRES.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { genre ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(genre.color, genre.color.copy(alpha = 0.60f)),
                                    ),
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                .muPressable { onOpenGenre(genre.title, genre.query) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            // Dekorativer Glanzkreis, halb aus der Ecke ragend — gibt Tiefe.
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 16.dp, y = 20.dp)
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f)),
                            )
                            Text(
                                genre.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // Ungerade Anzahl: Platzhalter, damit die letzte Karte halb breit bleibt.
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Ergebnisfilter der Smart-Search. [entries] und [labelFor] erlauben
 * modusgerechte Untermengen und Beschriftungen (z. B. "Inhalte"/"Kanäle"
 * statt "Songs"/"Künstler" außerhalb des Musik-Modus).
 */
@Composable
fun ResultFilterChips(
    selected: MusicSearchFilter,
    onSelect: (MusicSearchFilter) -> Unit,
    modifier: Modifier = Modifier,
    entries: List<MusicSearchFilter> = MusicSearchFilter.entries.toList(),
    labelFor: (MusicSearchFilter) -> String = { it.label },
) {
    // Zurückhaltende Text-Tabs statt gefüllter Chips: Amber nur als Textfarbe
    // und schmaler Unterstrich des aktiven Filters.
    SearchDockPanel(modifier) {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(entries, key = { it.name }) { filter ->
                val active = filter == selected
                val labelColor by animateColorAsState(
                    if (active) HikariPrimary else HikariTextMuted,
                    tween(180), label = "filterTab",
                )
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .muPressable { onSelect(filter) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        labelFor(filter),
                        fontSize = 13.sp,
                        color = labelColor,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) HikariPrimary else Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * Das Top-Ergebnis der Vollsuche als große Karte: Typ-Badge, Bild (rund bei
 * Künstlern, sonst abgerundetes Cover) und Titel mit Unterzeile.
 */
@Composable
fun TopResultCard(result: MusicSearchResult, onClick: () -> Unit) {
    val typeLabel: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String
    val isArtist: Boolean
    when (result) {
        is MusicSearchResult.Song -> {
            typeLabel = "Song"
            title = result.song.title
            subtitle = result.song.uploader
            thumbnailUrl = result.song.thumbnailUrl
            isArtist = false
        }
        is MusicSearchResult.Artist -> {
            typeLabel = "Künstler"
            title = result.artist.name
            subtitle = if (result.artist.subscribers > 0) {
                "Künstler • ${formatSubscribers(result.artist.subscribers)}"
            } else {
                "Künstler"
            }
            thumbnailUrl = result.artist.thumbnailUrl
            isArtist = true
        }
        is MusicSearchResult.Album -> {
            typeLabel = "Album"
            title = result.album.name
            subtitle = "Album • ${result.album.artistName}"
            thumbnailUrl = result.album.thumbnailUrl
            isArtist = false
        }
        is MusicSearchResult.Playlist -> {
            typeLabel = "Playlist"
            title = result.playlist.name
            subtitle = "Playlist • ${result.playlist.uploaderName}"
            thumbnailUrl = result.playlist.thumbnailUrl
            isArtist = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .muPressable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TOP-ERGEBNIS",
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = HikariTextFaint,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                typeLabel,
                fontSize = 10.sp,
                color = HikariPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HikariPrimary.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = thumbnailUrl.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
                        .background(HikariSurfaceHigh),
                    contentScale = ContentScale.Crop,
                )
                // Songs starten direkt — das Play-Overlay macht das sichtbar.
                if (result is MusicSearchResult.Song) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = HikariPrimary, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HikariText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = HikariTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Künstler-Treffer: rundes Avatar, Name und Abonnentenzahl. */
@Composable
fun ArtistResultRow(artist: SearchArtist, onClick: () -> Unit) {
    SearchResultRow(
        thumbnailUrl = artist.thumbnailUrl,
        round = true,
        title = artist.name,
        subtitle = if (artist.subscribers > 0) {
            "Künstler • ${formatSubscribers(artist.subscribers)}"
        } else {
            "Künstler"
        },
        onClick = onClick,
    )
}

/** Album-Treffer: Cover, Name und Künstler. */
@Composable
fun AlbumResultRow(album: MusicAlbum, onClick: () -> Unit) {
    SearchResultRow(
        thumbnailUrl = album.thumbnailUrl,
        round = false,
        title = album.name,
        subtitle = "Album • ${album.artistName}",
        onClick = onClick,
    )
}

/** Playlist-Treffer: Cover, Name und Ersteller. */
@Composable
fun PlaylistResultRow(playlist: RemotePlaylist, onClick: () -> Unit) {
    SearchResultRow(
        thumbnailUrl = playlist.thumbnailUrl,
        round = false,
        title = playlist.name,
        subtitle = "Playlist • ${playlist.uploaderName}",
        onClick = onClick,
    )
}

/** Gemeinsame Zeile für Künstler-, Album- und Playlist-Treffer. */
@Composable
private fun SearchResultRow(
    thumbnailUrl: String,
    round: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .muPressable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = thumbnailUrl.ifEmpty { null },
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(10.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Abonnentenzahlen kompakt auf Deutsch: 1,2 Mio. / 340 Tsd. */
private fun formatSubscribers(count: Long): String = when {
    count >= 1_000_000 -> "${compactNumber(count / 1_000_000.0)} Mio. Abonnenten"
    count >= 1_000 -> "${compactNumber(count / 1_000.0)} Tsd. Abonnenten"
    else -> "$count Abonnenten"
}

private fun compactNumber(value: Double): String =
    "%.1f".format(value).replace('.', ',').removeSuffix(",0")
