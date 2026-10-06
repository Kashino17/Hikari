package com.hikari.app.ui.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun MoodDiscoverySection(
    selectedGenre: Genre,
    onSelectGenre: (Genre) -> Unit,
    genreCounts: Map<Genre, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val genres = Genre.entries.filter { it != Genre.ALL }

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
                Text(
                    text = "Zurücksetzen ✕",
                    color = com.hikari.app.ui.theme.HikariAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onSelectGenre(Genre.ALL) }
                        .padding(4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Horizontale Reihe eleganter Gradient-Kacheln
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
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF141316),
            genre.accentColor.copy(alpha = if (isSelected) 0.5f else 0.28f),
            genre.accentColor.copy(alpha = if (isSelected) 0.8f else 0.45f),
        ),
    )

    Surface(
        modifier = modifier
            .width(136.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF141316),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.7.dp,
            color = if (isSelected) genre.accentColor else genre.accentColor.copy(alpha = 0.25f),
        ),
        shadowElevation = if (isSelected) 8.dp else 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(10.dp),
        ) {
            // Emoji Icon oben rechts
            Text(
                text = genre.emoji,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.TopEnd),
            )

            // Titel & Zähler unten links
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.85f),
            ) {
                Text(
                    text = genre.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 0) {
                    Text(
                        text = "$count Titel",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
