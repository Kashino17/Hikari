package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.player.MusicPlayerController
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Warteschlangen-Drawer (Queue / Up Next) für den Musik-Player.
 * Spotify- und YouTube Music-Parität: nächste Titel einsehen, anspringen,
 * entfernen, leeren und Endloswiedergabe (Autoplay) steuern.
 */
@Composable
fun QueueSheet(
    controller: MusicPlayerController,
    onDismiss: () -> Unit,
) {
    val queue by controller.queue.collectAsState()
    val currentIndex by controller.queueIndexFlow.collectAsState()
    val currentSong by controller.currentSong.collectAsState()
    val autoplay by controller.autoplayEnabled.collectAsState()

    MuSheet(
        title = "Warteschlange",
        onClose = onDismiss,
    ) {
        // Obere Steuerzeile: Titelanzahl + Autoplay-Umschalter
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${queue.size} Titel in der Queue",
                    fontSize = 13.sp,
                    color = HikariTextMuted,
                )
            }

            // Autoplay Toggle-Pille
            MuActionPill(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = if (autoplay) "Autoplay an" else "Autoplay aus",
                active = autoplay,
                onClick = { controller.toggleAutoplay() },
            )
        }

        Spacer(Modifier.height(14.dp))

        // Aktueller Song
        if (currentSong != null) {
            Text(
                "SPRICHT / SPIELT GERADE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HikariPrimary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            CurrentQueueItem(currentSong!!)
            Spacer(Modifier.height(16.dp))
        }

        // Kommende Titel
        val upcomingWithIndices = queue.mapIndexed { idx, s -> idx to s }.filter { it.first > currentIndex }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NÄCHSTE TITEL (${upcomingWithIndices.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HikariTextFaint,
                letterSpacing = 1.sp,
            )
            if (upcomingWithIndices.isNotEmpty()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { controller.clearUpcomingQueue() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ClearAll, null, tint = HikariTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Leeren", fontSize = 12.sp, color = HikariTextMuted)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (upcomingWithIndices.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariCardBg.copy(alpha = 0.4f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Keine weiteren Titel in der Warteschlange",
                        color = HikariTextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (autoplay) "Endloswiedergabe ist aktiv — ähnliche Songs starten automatisch."
                        else "Autoplay ist aus — Wiedergabe stoppt nach diesem Titel.",
                        color = HikariTextFaint,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                upcomingWithIndices.forEach { (index, song) ->
                    UpcomingQueueItem(
                        position = index + 1,
                        song = song,
                        onClick = { controller.jumpToQueueIndex(index) },
                        onRemove = { controller.removeFromQueue(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentQueueItem(song: MusicSong) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariPrimary.copy(alpha = 0.12f))
            .border(1.dp, HikariPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.uploader,
                fontSize = 12.sp,
                color = HikariPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.GraphicEq,
            "Spielt gerade",
            tint = HikariPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun UpcomingQueueItem(
    position: Int,
    song: MusicSong,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            fontSize = 12.sp,
            color = HikariTextFaint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.uploader,
                fontSize = 12.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                "Aus Warteschlange entfernen",
                tint = HikariTextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
