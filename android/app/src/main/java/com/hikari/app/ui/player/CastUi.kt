package com.hikari.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import com.hikari.app.cast.CastController
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/** Auswahl der Fernseher/Chromecasts im WLAN; sucht nur, solange sie offen ist. */
@UnstableApi
@Composable
fun CastPickerDialog(cast: CastController, onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        cast.startSearch()
        onDispose { cast.stopSearch() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1C20))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cast, null, tint = HikariAmber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Auf Fernseher streamen", color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            if (cast.casting) {
                Text(
                    "Verbunden mit ${cast.deviceName ?: "Fernseher"}",
                    color = HikariTextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { cast.disconnect(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verbindung trennen")
                }
            } else if (cast.targets.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HikariAmber)
                    Spacer(Modifier.width(10.dp))
                    Text("Suche Geräte im WLAN …", color = HikariTextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Handy und Fernseher müssen im selben WLAN sein.",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                )
            } else {
                cast.targets.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !t.connecting) { cast.connect(t); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Cast, null, tint = HikariText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t.name,
                            color = HikariText,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (t.connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HikariAmber)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}

/** Fernbedienung, solange das Video auf dem Fernseher läuft (das Handy-Bild ist aus). */
@Composable
fun CastRemoteOverlay(
    deviceName: String?,
    title: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val dur = durationMs.coerceAtLeast(1L)
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.CastConnected, null, tint = HikariAmber, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Läuft auf ${deviceName ?: "dem Fernseher"}",
                color = HikariAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                title.ifBlank { "Wird geladen …" },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            Slider(
                value = scrub ?: (positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let { onSeekTo((it * dur).toLong()) }
                    scrub = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(((scrub ?: (positionMs.toFloat() / dur)) * dur).toLong()), color = HikariTextMuted, fontSize = 12.sp)
                Text(formatClock(durationMs), color = HikariTextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteButton(Icons.Default.Replay10, "10 Sekunden zurück", 52.dp) { onSeekBy(-10_000L) }
                RemoteButton(
                    if (playing) com.hikari.app.ui.feed.HikariIcons.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pause" else "Wiedergabe",
                    72.dp,
                ) { onTogglePlay() }
                RemoteButton(Icons.Default.Forward10, "10 Sekunden vor", 52.dp) { onSeekBy(10_000L) }
            }
            Spacer(Modifier.height(26.dp))
            TextButton(onClick = onStop) { Text("Cast beenden", color = Color.White) }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text("‹ Zurück", color = Color.White)
        }
    }
}

@Composable
private fun RemoteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}
