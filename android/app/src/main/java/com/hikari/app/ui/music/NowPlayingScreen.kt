package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.music.MusicViewModel
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: MusicSong,
    onBack: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jetzt abspielen", fontWeight = FontWeight.Bold) },
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            // Album art
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(Modifier.height(32.dp))

            // Song info
            Text(song.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = HikariText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.uploader, fontSize = 16.sp, color = HikariTextMuted, maxLines = 1)

            Spacer(Modifier.height(32.dp))

            // Duration
            Text(formatDuration(song.duration), fontSize = 14.sp, color = HikariTextMuted)

            Spacer(Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Shuffle, null, tint = HikariTextMuted)
                }
                IconButton(onClick = { viewModel.playPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, null, tint = HikariText, modifier = Modifier.size(40.dp))
                }
                Surface(
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                    color = HikariPrimary.copy(alpha = 0.2f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(56.dp)) {
                            Icon(
                                if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                tint = HikariPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(Icons.Default.SkipNext, null, tint = HikariText, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Repeat, null, tint = HikariTextMuted)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Queue
            Text("NÄCHSTE SONGS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = HikariTextMuted)
            Spacer(Modifier.height(8.dp))
            viewModel.queue.drop(1).take(5).forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { viewModel.playSong(s) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HikariCardBg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(model = s.thumbnailUrl, contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(s.title, fontSize = 13.sp, color = HikariText, maxLines = 1)
                        Text(s.uploader, fontSize = 11.sp, color = HikariTextMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
