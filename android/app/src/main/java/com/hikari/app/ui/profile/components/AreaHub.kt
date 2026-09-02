package com.hikari.app.ui.profile.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.NewsItem
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val CARD_WIDTH = 252.dp
private val CARD_HEIGHT = 148.dp

/**
 * Bereichs-Hub im Profil: Tagesbericht, Manga und Spiele als inhaltsreiche
 * Karten mit echten Bildern (aktuelles News-Bild, echte Manga-Cover) statt
 * generischer Icon-Kacheln — ein Tap öffnet die Section.
 */
@Composable
fun AreaHub(
    news: NewsItem?,
    newsCount: Int,
    mangaCovers: List<String>,
    mangaLabel: String?,
    onOpenSection: (route: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "DEINE BEREICHE",
            color = HikariTextFaint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HubCard(onClick = { onOpenSection("news") }) {
                    NewsCardContent(news, newsCount)
                }
            }
            item {
                HubCard(onClick = { onOpenSection("manga") }) {
                    MangaCardContent(mangaCovers, mangaLabel)
                }
            }
            item {
                HubCard(onClick = { onOpenSection("games") }) {
                    GamesCardContent()
                }
            }
        }
    }
}

@Composable
private fun HubCard(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "hub-press",
    )
    Box(
        Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(HikariCardBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content()
    }
}

/** Dunkler Scrim, damit Text auf jedem Bild lesbar bleibt. */
@Composable
private fun Scrim() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to HikariBg.copy(alpha = 0.25f),
                1f to HikariBg.copy(alpha = 0.92f),
            ),
        ),
    )
}

@Composable
private fun CardLabel(name: String, subtitle: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(HikariBg.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HikariPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = HikariText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = HikariTextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = HikariTextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun BoxScope.NewsCardContent(news: NewsItem?, newsCount: Int) {
    val image = news?.imageUrls?.firstOrNull()
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // Kein Bild verfügbar: gestaltete Fläche statt Leere.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(HikariPrimary.copy(alpha = 0.22f), HikariSurfaceHigh, HikariBg),
                ),
            ),
        )
        Icon(
            Icons.AutoMirrored.Filled.Article,
            contentDescription = null,
            tint = HikariPrimary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(44.dp),
        )
    }
    Scrim()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        CardLabel(
            name = "Tagesbericht",
            subtitle = when {
                newsCount > 0 -> "$newsCount Nachrichten von heute"
                news != null -> news.title
                else -> "Deine KI-News, jeden Morgen"
            },
            icon = Icons.AutoMirrored.Filled.Article,
        )
    }
}

@Composable
private fun BoxScope.MangaCardContent(covers: List<String>, label: String?) {
    Box(Modifier.fillMaxSize().background(HikariSurfaceHigh))
    if (covers.isNotEmpty()) {
        // Echte Cover als Fächer — Inhalt statt Icon.
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
        ) {
            covers.take(3).forEachIndexed { index, url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(74.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(HikariSurfaceHigh, HikariCardBg, HikariBg)),
            ),
        )
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            tint = HikariPrimary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(44.dp),
        )
    }
    Scrim()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        CardLabel(
            name = "Manga",
            subtitle = label ?: "Weiterlesen & alle Serien",
            icon = Icons.Default.MenuBook,
        )
    }
}

@Composable
private fun BoxScope.GamesCardContent() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(HikariPrimary.copy(alpha = 0.85f), Color(0xFFB45309), Color(0xFF3B2503)),
            ),
        ),
    )
    Icon(
        Icons.Default.Star,
        contentDescription = null,
        tint = Color.Black.copy(alpha = 0.25f),
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(58.dp),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        CardLabel(
            name = "Spiele",
            subtitle = "9 Spiele · Highscore jagen",
            icon = Icons.Default.Star,
        )
    }
}
