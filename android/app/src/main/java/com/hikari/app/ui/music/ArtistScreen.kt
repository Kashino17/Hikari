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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.ArtistPlaylist
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val PLAYLIST_CARD_WIDTH = 208.dp
private val PLAYLIST_CARD_HEIGHT = 117.dp

/**
 * Artist-Seite: Profil, Top-Songs und Playlists eines Künstlers. Die Inhalte
 * lädt das Backend über die Piped-Suche, weil die Kanal-Tabs der Instanzen
 * degradiert sind. [fallbackName] ist der Anzeigename aus dem Einstiegspunkt
 * und dient zugleich als Suchbegriff für Top-Songs und Playlists.
 */
@Composable
fun ArtistScreen(
    channelId: String,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenPlaylistMix: (title: String, query: String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val artist = viewModel.artist
    val topSongs = viewModel.artistTop
    val playlists = viewModel.artistPlaylists
    val currentSong by viewModel.player.currentSong.collectAsState()

    LaunchedEffect(channelId, fallbackName) { viewModel.loadArtist(channelId, fallbackName) }

    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Text(
                artist?.name ?: fallbackName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HikariText,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                viewModel.artistLoading && artist == null -> CenteredLoader()
                viewModel.artistFailed && artist == null -> Column(
                    Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = HikariTextFaint, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Künstler konnte nicht geladen werden",
                        color = HikariTextMuted,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.loadArtist(channelId, fallbackName) }) {
                        Text("Erneut versuchen", color = HikariPrimary)
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item(key = "header") {
                        ArtistHeader(
                            name = artist?.name ?: fallbackName,
                            avatarUrl = artist?.avatarUrl,
                            subscriberCount = artist?.subscriberCount ?: 0,
                            description = artist?.description.orEmpty(),
                            verified = artist?.verified == true,
                        )
                    }

                    if (topSongs.isNotEmpty()) {
                        item(key = "top-header") { SectionHeader("Top-Songs") }
                        items(topSongs, key = { "top-${it.videoId}" }) { song ->
                            SongRow(
                                song,
                                viewModel,
                                topSongs,
                                badge = if (song.views > 0) formatViewsDE(song.views) else null,
                            )
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item(key = "playlists-header") { SectionHeader("Alben & Playlists") }
                        item(key = "playlists-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(playlists, key = { it.playlistId }) { playlist ->
                                    ArtistPlaylistCard(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylistMix(playlist.name, playlist.name) },
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
}

@Composable
private fun ArtistHeader(
    name: String,
    avatarUrl: String?,
    subscriberCount: Long,
    description: String,
    verified: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl.isNullOrEmpty()) {
                Icon(Icons.Default.MusicNote, null, tint = HikariTextFaint, modifier = Modifier.size(44.dp))
            } else {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (verified) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Verified,
                    "Verifiziert",
                    tint = HikariPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (subscriberCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                formatSubscribersDE(subscriberCount),
                fontSize = 13.sp,
                color = HikariTextMuted,
            )
        }
        if (description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                fontSize = 13.sp,
                color = HikariTextMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Playlist-Karte im MixCard-Format: Thumbnail, Name und Anzahl der Songs. */
@Composable
private fun ArtistPlaylistCard(playlist: ArtistPlaylist, onClick: () -> Unit) {
    Column(
        Modifier
            .width(PLAYLIST_CARD_WIDTH)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = playlist.thumbnailUrl.ifEmpty { null },
            contentDescription = null,
            modifier = Modifier
                .width(PLAYLIST_CARD_WIDTH)
                .height(PLAYLIST_CARD_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (playlist.videoCount == 1) "1 Song" else "${playlist.videoCount} Songs",
            fontSize = 11.sp,
            color = HikariTextMuted,
        )
    }
}
