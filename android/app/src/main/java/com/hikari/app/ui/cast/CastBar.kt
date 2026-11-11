package com.hikari.app.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.cast.CastController
import com.hikari.app.domain.cast.CastKind
import com.hikari.app.domain.cast.CastMedia
import com.hikari.app.domain.cast.CastRemote
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/** Höhe der Leiste samt Abstand — so viel Platz räumen andere Elemente über der Navigation frei. */
val CastBarSpace = 68.dp

private val BarShape = RoundedCornerShape(12.dp)
private val BarBg = Color(0xFF2B2B30)

/**
 * Die kleine Leiste, solange etwas auf dem Fernseher läuft: Vorschaubild, Titel, Pause/Weiter
 * und Beenden. Ein Tipp auf die Leiste öffnet die Steuerung im Hochformat.
 */
@Composable
fun CastBar(cast: CastController, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val media = cast.media ?: return
    Box(
        modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(62.dp)
            .shadow(12.dp, BarShape)
            .clip(BarShape)
            .background(BarBg)
            .border(1.dp, HikariBorderStrong, BarShape)
            .clickable(onClickLabel = "Steuerung öffnen", onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CastArtwork(
                media,
                Modifier
                    .size(width = if (media.kind == CastKind.MUSIC) 42.dp else 62.dp, height = 42.dp)
                    .clip(RoundedCornerShape(6.dp)),
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    CastRemote.barTitle(media),
                    color = HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cast, null, tint = HikariAmber, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        CastRemote.barLine(media, cast.deviceName),
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (cast.loading && !cast.playing) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = HikariText, strokeWidth = 2.dp)
                }
            } else {
                BarButton(
                    if (cast.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (cast.playing) "Pause" else "Weiter",
                    26.dp,
                ) { cast.togglePlay() }
            }
            BarButton(Icons.Default.Close, "Cast beenden", 22.dp) { cast.disconnect() }
        }
        // Feine Linie: wie weit das Video bzw. Lied ist.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.5.dp).background(Color.White.copy(alpha = 0.10f)))
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(CastRemote.progress(cast.positionMs, cast.durationMs))
                .height(2.5.dp)
                .background(HikariAmber),
        )
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, iconSize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = HikariText, modifier = Modifier.size(iconSize))
    }
}

/** Vorschaubild des laufenden Stücks; ohne Bild ein ruhiges Feld mit Zeichen. */
@Composable
internal fun CastArtwork(media: CastMedia, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp) {
    Box(modifier.background(HikariSurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(
            if (media.kind == CastKind.MUSIC) Icons.Default.MusicNote else Icons.Default.Tv,
            null,
            tint = HikariTextFaint,
            modifier = Modifier.size(iconSize),
        )
        if (!media.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = media.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
