package com.hikari.app.ui.music

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/** Alle Songs eines kuratierten Mixes — abspielbar und offline speicherbar. */
@Composable
fun MixDetailScreen(
    title: String,
    query: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val songs = viewModel.mixSongs
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val downloadedCount = songs.count { it.videoId in downloadedIds }
    val allDownloaded = songs.isNotEmpty() && downloadedCount == songs.size

    LaunchedEffect(query) { viewModel.loadMix(query) }

    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HikariText,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp)) {
                    MixCoverPreview(songs)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (songs.size == 1) "1 Song" else "${songs.size} Songs",
                        fontSize = 14.sp,
                        color = HikariText,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (allDownloaded) Icons.Outlined.OfflinePin else Icons.Outlined.CloudDownload,
                            null,
                            tint = if (allDownloaded) HikariPrimary else HikariTextFaint,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when {
                                songs.isEmpty() -> "wird geladen"
                                allDownloaded -> "komplett offline verfügbar"
                                downloadedCount > 0 -> "$downloadedCount von ${songs.size} offline"
                                else -> "noch nichts offline"
                            },
                            fontSize = 12.sp,
                            color = HikariTextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { songs.firstOrNull()?.let { viewModel.play(it, songs); onOpenNowPlaying() } },
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abspielen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        val shuffled = songs.shuffled()
                        shuffled.firstOrNull()?.let { viewModel.play(it, shuffled); onOpenNowPlaying() }
                    },
                    enabled = songs.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zufall", color = HikariPrimary, fontSize = 13.sp)
                }
                if (!allDownloaded && songs.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.downloadMix(title) }) {
                        Icon(Icons.Outlined.CloudDownload, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Alle laden", color = HikariPrimary, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                viewModel.mixLoading && songs.isEmpty() -> CenteredLoader()
                songs.isEmpty() -> EmptyHint(
                    Icons.Outlined.CloudDownload,
                    "Dieser Mix ist gerade nicht erreichbar — später nochmal versuchen.",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(songs, key = { it.videoId }) { song ->
                        SongRow(song, viewModel, songs)
                    }
                }
            }
        }

        if (currentSong != null) {
            MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
        }
    }
}
