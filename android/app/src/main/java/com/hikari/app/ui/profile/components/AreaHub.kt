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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.ui.russian.RussianHomeViewModel
import com.hikari.app.domain.model.NewsItem
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val CARD_WIDTH = 252.dp
private val CARD_HEIGHT = 138.dp

/**
 * Bereichs-Hub im Profil: Tagesbericht, Manga, Musik, Russisch, Spiele und Browser
 * als inhaltsreiche Karten mit echten Bildern — ein Tap öffnet die jeweilige Section.
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
        Spacer(Modifier.height(10.dp))
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
                HubCard(onClick = { onOpenSection("music?from=profile") }) {
                    MusicCardContent()
                }
            }
            item {
                HubCard(onClick = { onOpenSection("russian") }) {
                    RussianCardContent()
                }
            }
            item {
                HubCard(onClick = { onOpenSection("games") }) {
                    GamesCardContent()
                }
            }
            item {
                HubCard(onClick = { onOpenSection("browser") }) {
                    BrowserCardContent()
                }
            }
        }
    }
}

/**
 * Vollständige Ansicht für den eigenständigen "Bereiche"-Tab.
 * Homescreen App Grid (3 Spalten) mit taktiler Feder-Animation, lebendigen Farbverläufen,
 * edlen Squircles, Status-Badges und Highlight-Spotlight.
 */
@Composable
fun AreaHubShowcase(
    news: NewsItem?,
    newsCount: Int,
    mangaCovers: List<String>,
    mangaLabel: String?,
    onOpenSection: (route: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        // Kopfzeile: Subtiler Bereichstitel + Zähler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SCHNELLZUGRIFF",
                color = HikariTextFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariSurfaceHigh.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    "6 Bereiche",
                    color = HikariAmber,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Row 1: Tagesbericht · Manga · Musik
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AreaAppTile(
                    title = "Tagesbericht",
                    subtitle = "News & Trends",
                    icon = Icons.AutoMirrored.Filled.Article,
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),
                    ),
                    badgeText = if (newsCount > 0) "$newsCount neu" else null,
                    badgeColor = HikariAmber,
                    badgeTextColor = Color.Black,
                    onClick = { onOpenSection("news") },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AreaAppTile(
                    title = "Manga",
                    subtitle = mangaLabel ?: "Reader",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFF5B247A), Color(0xFF1BCED8)),
                    ),
                    badgeText = if (!mangaLabel.isNullOrBlank()) "Aktiv" else null,
                    badgeColor = Color(0xFF00E5FF),
                    badgeTextColor = Color.Black,
                    onClick = { onOpenSection("manga") },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AreaAppTile(
                    title = "Musik",
                    subtitle = "HiFi Stream",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)),
                    ),
                    badgeText = "HiFi",
                    badgeColor = Color(0xFFFF5252),
                    badgeTextColor = Color.White,
                    onClick = { onOpenSection("music?from=profile") },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Row 2: Russisch · Spiele · Browser
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AreaAppTile(
                    title = "Russisch",
                    subtitle = "Vokabeltrainer",
                    icon = Icons.Default.Translate,
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFFD31027), Color(0xFFEA384D)),
                    ),
                    badgeText = "Lernen",
                    badgeColor = HikariAmber,
                    badgeTextColor = Color.Black,
                    onClick = { onOpenSection("russian") },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AreaAppTile(
                    title = "Spiele",
                    subtitle = "Arcade Hub",
                    icon = Icons.Default.SportsEsports,
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFFFF8008), Color(0xFFFFC837)),
                    ),
                    badgeText = "9 Spiele",
                    badgeColor = Color(0xFFFFD700),
                    badgeTextColor = Color.Black,
                    onClick = { onOpenSection("games") },
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AreaAppTile(
                    title = "Browser",
                    subtitle = "Video Sniffer",
                    icon = Icons.Default.Public,
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                    ),
                    badgeText = "Sniffer",
                    badgeColor = Color(0xFF00E676),
                    badgeTextColor = Color.Black,
                    onClick = { onOpenSection("browser") },
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        // Spotlight Widget / Live Card
        if (news != null) {
            SpotlightCard(
                category = "TAGESBERICHT",
                title = news.title,
                icon = Icons.AutoMirrored.Filled.Article,
                accentColor = Color(0xFF2A5298),
                onClick = { onOpenSection("news") },
            )
        } else if (mangaCovers.isNotEmpty()) {
            SpotlightCard(
                category = "MANGA HIGHLIGHT",
                title = mangaLabel ?: "Lese deinen Manga fort",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                accentColor = Color(0xFF5B247A),
                onClick = { onOpenSection("manga") },
            )
        } else {
            SpotlightCard(
                category = "ARCADE HIGHLIGHT",
                title = "Block Blast, Sudoku, 2048 & mehr offline spielen",
                icon = Icons.Default.SportsEsports,
                accentColor = Color(0xFFFF8008),
                onClick = { onOpenSection("games") },
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AreaAppTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    badgeText: String? = null,
    badgeColor: Color = HikariAmber,
    badgeTextColor: Color = Color.Black,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "app-press",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Squircle Icon Container
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(gradient)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Subtle gloss highlight on top
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            // Live Badge
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .border(1.2.dp, HikariBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        color = badgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = title,
            color = HikariText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(1.dp))

        Text(
            text = subtitle,
            color = HikariTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpotlightCard(
    category: String,
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "spotlight-press",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.25f))
                    .border(0.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category,
                    color = HikariAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    color = HikariText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HikariTextMuted,
                modifier = Modifier.size(16.dp),
            )
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

@Composable
private fun FullWidthHubCard(
    onClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp = 130.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "hub-full-press",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
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
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HikariBg.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HikariPrimary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
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
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
        ) {
            covers.take(3).forEachIndexed { _, url ->
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
            Icons.AutoMirrored.Filled.MenuBook,
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
            icon = Icons.AutoMirrored.Filled.MenuBook,
        )
    }
}

@Composable
private fun BoxScope.MusicCardContent() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF4338CA), Color(0xFF312E81), Color(0xFF1E1B4B)),
            ),
        ),
    )
    Icon(
        Icons.AutoMirrored.Filled.PlaylistPlay,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.15f),
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(58.dp),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        CardLabel(
            name = "Musik",
            subtitle = "Playlists, Favoriten & High-Speed Stream",
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
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

@Composable
private fun BoxScope.BrowserCardContent() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF0F766E), Color(0xFF115E59), Color(0xFF042F2E)),
            ),
        ),
    )
    Icon(
        Icons.Default.Public,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.15f),
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(54.dp),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        CardLabel(
            name = "Browser & Sniffer",
            subtitle = "Streams im Web erkennen & direkt importieren",
            icon = Icons.Default.Public,
        )
    }
}

@Composable
private fun BoxScope.RussianCardContent() {
    val vm: RussianHomeViewModel = hiltViewModel()
    val p by vm.progress.collectAsState()
    val done = p.dayStars.size
    val total = vm.index.days.size
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(HikariSurfaceHigh, HikariCardBg, HikariBg)),
        ),
    )
    Text(
        "Привет",
        color = HikariText.copy(alpha = 0.07f),
        fontSize = 64.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp),
    )
    Text(
        "Прив\u0435\u0301т · priwjét",
        color = HikariPrimary.copy(alpha = 0.75f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        CardLabel(
            name = "Russisch",
            subtitle = when {
                done >= total -> "Kurs geschafft · weiter wiederholen"
                done == 0 -> "In 14 Tagen zum Small Talk"
                else -> "Tag ${done + 1} von $total · ${p.streak} Tage Serie"
            },
            icon = Icons.Default.Translate,
        )
    }
}
