package com.hikari.app.ui.ebooks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariTextMuted

private val MangaHue = Color(0xFFA78BFA)

/** Bereich „Lesen“: oben der Umschalter Manga | Bücher, darunter der jeweilige Teil. */
@Composable
fun LesenScreen(
    initialTab: String?,
    manga: @Composable () -> Unit,
    books: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(if (initialTab == "buecher") "buecher" else "manga") }
    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(14.dp)).background(HikariSurface).padding(4.dp),
        ) {
            Segment("Manga", tab == "manga", MangaHue) { tab = "manga" }
            Segment("Bücher", tab == "buecher", BooksAccent) { tab = "buecher" }
        }
        Box(Modifier.weight(1f)) { if (tab == "manga") manga() else books() }
    }
}

@Composable
private fun Segment(label: String, active: Boolean, hue: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) hue else Color.Transparent, label = "seg")
    Text(
        label,
        color = if (active) Color(0xFF17171A) else HikariTextMuted,
        fontSize = 14.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
    )
}
