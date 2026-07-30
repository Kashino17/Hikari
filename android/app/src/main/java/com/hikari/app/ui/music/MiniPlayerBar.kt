package com.hikari.app.ui.music

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun MiniPlayerBar(
    song: MusicSong?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
) {
    if (song == null) return

    Surface(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(onHorizontalDrag = { _, delta ->
                // Future: seek
            })
        },
        tonalElevation = 8.dp,
        color = HikariCardBg.copy(alpha = 0.95f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))

            // Info
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 13.sp, color = HikariText, maxLines = 1)
                Text(song.uploader, fontSize = 11.sp, color = HikariTextMuted, maxLines = 1)
            }

            // Controls
            IconButton(onClick = { onPrevious() }) {
                Icon(Icons.Default.SkipPrevious, null, tint = HikariText, modifier = Modifier.size(28.dp))
            }
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape),
                color = HikariPrimary.copy(alpha = 0.2f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = HikariPrimary, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            IconButton(onClick = { onNext() }) {
                Icon(Icons.Default.SkipNext, null, tint = HikariText, modifier = Modifier.size(28.dp))
            }
        }
    }
}
