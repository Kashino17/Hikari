package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val controller = viewModel.player
    val song by controller.currentSong.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val isBuffering by controller.isBuffering.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()
    val error by controller.error.collectAsState()

    val current = song ?: run {
        // nothing playing (e.g. process restart) — nothing to show
        onBack()
        return
    }
    val isFavorite = current.videoId in viewModel.favoriteIds

    Column(
        Modifier.fillMaxSize().background(HikariBg).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, "Schließen", tint = HikariText, modifier = Modifier.size(30.dp))
            }
            Text(
                "Läuft gerade",
                fontSize = 13.sp,
                color = HikariTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.toggleFavorite(current) }) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorit",
                    tint = if (isFavorite) Color(0xFFFF5252) else HikariTextMuted,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (current.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = current.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = HikariTextFaint, modifier = Modifier.size(80.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            current.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = HikariText,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(current.uploader, fontSize = 14.sp, color = HikariTextMuted, maxLines = 1)

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 12.sp, color = Color(0xFFF87171), textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp))
        }

        Spacer(Modifier.weight(1f))

        // --- seek bar ---
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val sliderValue = if (dragging) dragValue
        else if (duration > 0) position.toFloat() / duration else 0f

        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (duration > 0) controller.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = HikariPrimary,
                activeTrackColor = HikariPrimary,
                inactiveTrackColor = HikariSurfaceHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            Text(
                formatDurationMs(if (dragging && duration > 0) (dragValue * duration).toLong() else position),
                fontSize = 12.sp, color = HikariTextMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(formatDurationMs(duration), fontSize = 12.sp, color = HikariTextMuted)
        }

        Spacer(Modifier.height(10.dp))

        // --- transport controls ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle, "Zufallswiedergabe",
                    tint = if (shuffle) HikariPrimary else HikariTextMuted,
                )
            }
            IconButton(onClick = { controller.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Zurück", tint = HikariText, modifier = Modifier.size(40.dp))
            }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(HikariPrimary),
                contentAlignment = Alignment.Center,
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                } else {
                    IconButton(onClick = { controller.toggle() }, modifier = Modifier.size(72.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Abspielen",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            IconButton(onClick = { controller.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Weiter", tint = HikariText, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { controller.cycleRepeat() }) {
                Icon(
                    if (repeatMode == MusicPlayerController.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Wiederholen",
                    tint = if (repeatMode != MusicPlayerController.REPEAT_OFF) HikariPrimary else HikariTextMuted,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
