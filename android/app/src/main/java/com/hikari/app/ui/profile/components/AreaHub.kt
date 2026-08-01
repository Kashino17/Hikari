package com.hikari.app.ui.profile.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private data class HubArea(
    val route: String,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val AREAS = listOf(
    HubArea("music", "Musik", "Songs, Hörbücher, Podcasts, True Crime", Icons.Default.MusicNote),
    HubArea("news", "Tagesbericht", "Deine KI-News, jeden Morgen", Icons.AutoMirrored.Filled.Article),
    HubArea("manga", "Manga", "Weiterlesen & alle Serien", Icons.Default.MenuBook),
    HubArea("games", "Spiele", "Für zwischendurch", Icons.Default.Star),
)

/**
 * Bereichs-Hub im Profil: Musik, Tagesbericht, Manga und Spiele als 2×2-Karten.
 * Sie ersetzen die Bottom-Tabs dieser Sections — ein Tap öffnet die Section,
 * System-Zurück führt ins Profil zurück.
 */
@Composable
fun AreaHub(onOpenSection: (route: String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "DEINE BEREICHE",
            color = HikariTextFaint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
        Spacer(Modifier.height(12.dp))
        AREAS.chunked(2).forEach { rowAreas ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowAreas.forEach { area ->
                    AreaCard(area, onClick = { onOpenSection(area.route) }, modifier = Modifier.weight(1f))
                }
                if (rowAreas.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AreaCard(area: HubArea, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "area-press",
    )

    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = HikariTextFaint,
            modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
        )
        Column {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(area.icon, contentDescription = null, tint = HikariPrimary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(area.name, color = HikariText, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(area.subtitle, color = HikariTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
