package com.hikari.app.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.CancelPresentation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.cast.CastController
import com.hikari.app.domain.cast.CastKind
import com.hikari.app.domain.cast.CastMedia
import com.hikari.app.domain.cast.CastRemote
import com.hikari.app.ui.music.MuSeekBar
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/** Leicht gerundet, wie die Knöpfe der Bibliothek — keine Pillen. */
private val Soft = RoundedCornerShape(9.dp)

/**
 * Die Steuerung im Hochformat, solange etwas auf dem Fernseher läuft — aufgebaut wie der
 * Musikplayer. Videos bekommen Sprünge von 10 Sekunden, 1 und 5 Minuten; bei Musik geht es
 * stattdessen zum vorigen bzw. nächsten Lied.
 */
@Composable
fun CastRemoteScreen(cast: CastController, onClose: () -> Unit) {
    // Übertragung beendet (hier, an der Leiste oder am Fernseher): die Steuerung hat nichts mehr zu steuern.
    // Schließt genau einmal — »Cast beenden« schließt sofort, das Ende der Verbindung kommt später nach.
    val close = remember {
        var closed = false
        val once: () -> Unit = {
            if (!closed) {
                closed = true
                onClose()
            }
        }
        once
    }
    LaunchedEffect(cast.casting) {
        if (!cast.casting) close()
    }
    val media = cast.media
    Column(
        Modifier
            .fillMaxSize()
            .background(HikariBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Einklappen", onClick = close),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Einklappen", tint = HikariText, modifier = Modifier.size(28.dp))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cast, null, tint = HikariAmber, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "Läuft auf ${cast.deviceName ?: "dem Fernseher"}",
                    color = HikariAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(44.dp))
        }

        if (media == null) {
            Spacer(Modifier.weight(1f))
            Text(
                "Verbunden — starte ein Video oder ein Lied, dann läuft es auf dem Fernseher.",
                color = HikariTextMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(14.dp))
            Artwork(media)
            Spacer(Modifier.height(24.dp))
            CastRemote.remoteLine(media)?.let {
                Text(it, color = HikariTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
            }
            Text(
                media.title.ifBlank { "Wird geladen …" },
                color = HikariText,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            cast.warning?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(18.dp))
            Seek(cast)
            Spacer(Modifier.height(14.dp))
            Controls(cast, media.kind)
            if (media.kind == CastKind.VIDEO) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CastRemote.JUMPS_MS.forEach { delta ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(Soft)
                                .border(1.dp, HikariBorderStrong, Soft)
                                .clickable { cast.seekBy(delta) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(CastRemote.jumpLabel(delta), color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(Soft)
                .background(HikariSurfaceHigh)
                .clickable {
                    cast.disconnect()
                    close()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CancelPresentation, null, tint = HikariText, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("Cast beenden", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Videos im Breitbild, Musik als quadratisches Cover in der Mitte. */
@Composable
private fun Artwork(media: CastMedia) {
    if (media.kind == CastKind.MUSIC) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CastArtwork(
                media,
                Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                iconSize = 56.dp,
            )
        }
    } else {
        CastArtwork(
            media,
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
            iconSize = 48.dp,
        )
    }
}

/** Leiste zum Ziehen; links die Stelle (beim Ziehen die Vorschau), rechts die Restzeit. */
@Composable
private fun Seek(cast: CastController) {
    var preview by remember { mutableStateOf<Float?>(null) }
    val duration = cast.durationMs
    MuSeekBar(
        progress = CastRemote.progress(cast.positionMs, duration),
        modifier = Modifier.fillMaxWidth(),
        accent = HikariAmber,
        onSeekPreview = { preview = it },
        onSeek = { frac ->
            if (duration > 0L) cast.seekTo((frac * duration).toLong())
            preview = null
        },
    )
    val shown = preview?.takeIf { duration > 0L }?.let { (it * duration).toLong() } ?: cast.positionMs
    Row(Modifier.fillMaxWidth()) {
        Text(
            CastRemote.clock(shown),
            color = if (preview != null) HikariText else HikariTextMuted,
            fontSize = 12.sp,
            fontWeight = if (preview != null) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.weight(1f))
        Text(CastRemote.remaining(shown, duration), color = HikariTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Controls(cast: CastController, kind: CastKind) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kind == CastKind.MUSIC) {
            SideButton(Icons.Default.SkipPrevious, "Voriges Lied") { cast.previousSong() }
        } else {
            SideButton(Icons.Default.Replay10, "10 Sekunden zurück") { cast.seekBy(-CastRemote.STEP_MS) }
        }
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClickLabel = if (cast.playing) "Pause" else "Weiter") { cast.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (cast.loading && !cast.playing) {
                CircularProgressIndicator(Modifier.size(30.dp), color = Color(0xFF111111), strokeWidth = 3.dp)
            } else {
                Icon(
                    if (cast.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (cast.playing) "Pause" else "Weiter",
                    tint = Color(0xFF111111),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        if (kind == CastKind.MUSIC) {
            SideButton(Icons.Default.SkipNext, "Nächstes Lied") { cast.nextSong() }
        } else {
            SideButton(Icons.Default.Forward10, "10 Sekunden vor") { cast.seekBy(CastRemote.STEP_MS) }
        }
    }
}

@Composable
private fun SideButton(icon: ImageVector, description: String, size: Dp = 58.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = HikariText, modifier = Modifier.size(30.dp))
    }
}
