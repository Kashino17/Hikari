package com.hikari.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariText

/** Feed-Item stammt aus einer Discovery-Quelle (Probe-Kanal oder Themen-Suche). */
fun FeedItem.isDiscovery(): Boolean = source == "probe" || source == "topic"

/**
 * Entdeckte Inhalte kennzeichnen und in einem Tipp entscheiden: Kanal behalten
 * oder nie wieder zeigen. Die Aktionen sind echte Flächen mit Rand und
 * Mindesthöhe — auf dem Video wie auf der Karte gut sicht- und treffbar.
 */
@Composable
fun DiscoveryActions(
    item: FeedItem,
    onSubscribe: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
    overVideo: Boolean = false,
) {
    var acted by remember(item.videoId) { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        DiscoveryBadge()
        Spacer(Modifier.height(8.dp))
        when (acted) {
            "sub" -> StatusPill(Icons.Default.Check, "Kanal abonniert", overVideo)
            "block" -> StatusPill(Icons.Default.Close, "Wird nicht mehr gezeigt", overVideo)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Themen-Treffer haben keinen echten Kanal (Sammel-Eintrag),
                // deshalb dort nur das Ausblenden.
                if (item.source == "probe") {
                    ActionPill(
                        icon = Icons.Default.Add,
                        label = "Abonnieren",
                        accent = true,
                        overVideo = overVideo,
                    ) {
                        acted = "sub"
                        onSubscribe()
                    }
                }
                ActionPill(
                    icon = Icons.Default.Close,
                    label = "Nicht mehr zeigen",
                    accent = false,
                    overVideo = overVideo,
                ) {
                    acted = "block"
                    onBlock()
                }
            }
        }
    }
}

/** Amber-Punkt + Wortmarke: taucht im Feed nur an entdeckten Inhalten auf. */
@Composable
private fun DiscoveryBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HikariAmber.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(HikariAmber),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "NEU FÜR DICH",
            color = HikariAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    accent: Boolean,
    overVideo: Boolean,
    onClick: () -> Unit,
) {
    val content = if (accent) HikariAmber else if (overVideo) Color.White.copy(alpha = 0.92f) else HikariText
    val fill = when {
        accent -> HikariAmber.copy(alpha = 0.14f)
        overVideo -> Color.Black.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    val stroke = if (accent) HikariAmber.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.18f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .border(1.dp, stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Ruhige Bestätigung nach der Entscheidung — gleiche Höhe, kein Layout-Sprung. */
@Composable
private fun StatusPill(icon: ImageVector, label: String, overVideo: Boolean) {
    val content = if (overVideo) Color.White.copy(alpha = 0.7f) else HikariText.copy(alpha = 0.7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
