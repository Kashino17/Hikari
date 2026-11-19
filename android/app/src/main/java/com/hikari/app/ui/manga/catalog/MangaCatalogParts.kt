package com.hikari.app.ui.manga.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.MangaCatalogCardDto
import com.hikari.app.ui.manga.MangaAccent
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/** Sprachfarben — überall gleich, damit man Deutsch und Englisch auf einen Blick unterscheidet. */
internal val LangDe = Color(0xFFE8B04A)
internal val LangEn = Color(0xFF6AA8FF)

/** „DE“ / „EN“ (klein) oder „DEUTSCH“ / „ENGLISCH“ (groß). */
@Composable
internal fun LangBadge(lang: String, big: Boolean = false, modifier: Modifier = Modifier) {
    val de = lang == "de"
    Text(
        when { de && big -> "DEUTSCH"; de -> "DE"; big -> "ENGLISCH"; else -> "EN" },
        color = Color(0xFF111111),
        fontSize = if (big) 12.sp else 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.6.sp,
        modifier = modifier.clip(RoundedCornerShape(if (big) 6.dp else 4.dp)).background(if (de) LangDe else LangEn)
            .padding(horizontal = if (big) 8.dp else 5.dp, vertical = if (big) 4.dp else 2.dp),
    )
}

/** Sprache eines Kapitels aus seiner Kennung (mangadex:<uuid>:<lang>:<nr>); onepiece.tube ist Deutsch. */
internal fun langOfChapter(chapterId: String): String = when {
    chapterId.startsWith("mangadex:") -> chapterId.split(":").getOrNull(2)?.takeIf { it == "de" || it == "en" } ?: "en"
    else -> "de"
}

/** Titelbild einer Katalog-Reihe mit Sprach- und Art-Schild. */
@Composable
internal fun CatalogCover(card: MangaCatalogCardDto, coverUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(HikariSurfaceHigh)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(6.dp)),
    ) {
        if (coverUrl != null) AsyncImage(coverUrl, card.displayTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(card.displayTitle, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp).align(Alignment.Center))
        Row(Modifier.padding(5.dp).align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (l in card.langs.sorted()) LangBadge(l)
        }
        if (card.kind != "Manga") {
            Text(card.kind.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                modifier = Modifier.padding(5.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(4.dp)).background(Color(0xD0101010))
                    .padding(horizontal = 5.dp, vertical = 2.dp))
        }
    }
}

@Composable
internal fun CatalogCard(card: MangaCatalogCardDto, coverUrl: String?, width: Dp?, onClick: () -> Unit) {
    Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clickable(onClick = onClick)) {
        CatalogCover(card, coverUrl, Modifier.fillMaxWidth())
        Text(card.displayTitle, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(card.authors.firstOrNull() ?: card.tags.take(2).joinToString(", "), color = HikariTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CatalogRow(items: List<MangaCatalogCardDto>, cover: (MangaCatalogCardDto) -> String?, onOpen: (MangaCatalogCardDto) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { c -> CatalogCard(c, cover(c), 112.dp) { onOpen(c) } }
    }
}

/** Farbe je Genre für Kacheln und Punkte. */
internal fun genreColor(name: String): Color = when (name) {
    "Action" -> Color(0xFFC2410C)
    "Abenteuer" -> Color(0xFFB45309)
    "Romantik" -> Color(0xFFBE185D)
    "Komödie" -> Color(0xFFA16207)
    "Isekai" -> Color(0xFF6D28D9)
    "Alltag" -> Color(0xFF0F766E)
    "Sport" -> Color(0xFF15803D)
    "Drama" -> Color(0xFF1D4ED8)
    "Psycho" -> Color(0xFF475569)
    "Thriller", "Horror" -> Color(0xFF991B1B)
    "Mystery" -> Color(0xFF3730A3)
    "Historisch" -> Color(0xFF92400E)
    "Schule" -> Color(0xFF0E7490)
    "Fantasy" -> Color(0xFF7C3AED)
    "Sci-Fi" -> Color(0xFF0369A1)
    "Geister", "Dämonen", "Monster" -> Color(0xFF4338CA)
    else -> MangaAccent.copy(alpha = 0.8f)
}
