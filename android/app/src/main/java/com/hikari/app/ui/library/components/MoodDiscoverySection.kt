package com.hikari.app.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.genre.Genre
import com.hikari.app.ui.theme.HikariAmber
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun MoodDiscoverySection(
    selectedGenre: Genre,
    onSelectGenre: (Genre) -> Unit,
    genreCounts: Map<Genre, Int> = emptyMap(),
    modifier: Modifier = Modifier,
    /** Je Genre ein Bild aus dem eigenen Bestand — die Kachel zeigt, was drin ist. */
    genreCovers: Map<Genre, String> = emptyMap(),
) {
    // Leere Genres sind nur Rauschen — Kacheln ohne Inhalt führten ins Leere.
    val genres = Genre.entries.filter { it != Genre.ALL && (genreCounts[it] ?: 0) > 0 }
    if (genres.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Themen & Genres",
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "ENTDECKEN",
                        color = Color(0xFFA1A1AA),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            if (selectedGenre != Genre.ALL) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectGenre(Genre.ALL) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zurücksetzen",
                        tint = com.hikari.app.ui.theme.HikariAmber,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Zurücksetzen",
                        color = com.hikari.app.ui.theme.HikariAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Bildkacheln: jedes Genre zeigt ein Standbild aus seinem Inhalt, in
        // der Genre-Farbe getönt — statt bloßer Farbflächen.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(genres, key = { it.id }) { genre ->
                val isSelected = genre == selectedGenre
                val count = genreCounts[genre] ?: 0

                MoodGenreTile(
                    genre = genre,
                    count = count,
                    coverUrl = genreCovers[genre],
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) onSelectGenre(Genre.ALL)
                        else onSelectGenre(genre)
                    },
                )
            }
        }
    }
}

@Composable
fun MoodGenreTile(
    genre: Genre,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .width(168.dp)
            .height(96.dp)
            .clip(shape)
            .background(HikariSurface)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) HikariAmber else Color.White.copy(alpha = 0.08f),
                ),
                shape,
            )
            .clickable(onClick = onClick),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Abdunklung von links unten, leicht in der Genre-Farbe.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to genre.accentColor.copy(alpha = 0.55f),
                    0.45f to Color.Black.copy(alpha = 0.62f),
                    1f to Color.Black.copy(alpha = 0.30f),
                ),
            ),
        )
        Box(
            modifier = Modifier
                .padding(10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(genre.accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = genre.icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = genre.title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (count == 1) "1 Titel" else "$count Titel",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
        }
    }
}
