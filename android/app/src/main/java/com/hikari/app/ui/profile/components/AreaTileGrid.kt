package com.hikari.app.ui.profile.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.hikari.app.domain.profile.AreaTiles
import com.hikari.app.domain.profile.MangaTile
import com.hikari.app.domain.profile.RussianTile
import com.hikari.app.ui.profile.AreaTilesViewModel
import com.hikari.app.ui.profile.MusicTile
import com.hikari.app.ui.profile.NewsTile
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Eine Farbe je Bereich — aus ihr entstehen Fläche (leicht getönt), Rand und Symbol der Kachel.
 * Russisch trägt das Rosa seines eigenen Bereichs (RuColors.Speak); Amber bleibt für Erfolge.
 */
internal object AreaColors {
    val Russian = Color(0xFFF472B6)
    val Music = Color(0xFF22D3EE)
    val Manga = Color(0xFFA78BFA)
    val News = Color(0xFF60A5FA)
    val Games = Color(0xFFFB923C)
    val Browser = Color(0xFF2DD4BF)
    val WhatsApp = Color(0xFF4ADE80)

    /** Dunkle Schrift und Symbole auf voll gefärbten Flächen. */
    val Ink = Color(0xFF17171A)
}

private val TileShape = RoundedCornerShape(18.dp)
private val TilePadding = 13.dp
private val Gap = 10.dp

/**
 * Die Bereiche als Kacheln mit Leben: jede zeigt, was dort gerade los ist. Wichtiges ist groß
 * (Russisch, Musik, Manga, Tagesbericht), Nebensachen klein (Spiele, Browser, WhatsApp).
 */
@Composable
fun AreaTileGrid(
    vm: AreaTilesViewModel,
    onOpenSection: (route: String) -> Unit,
) {
    val russian by vm.russian.collectAsState()
    val music by vm.music.collectAsState()
    val manga by vm.manga.collectAsState()
    val news by vm.news.collectAsState()
    val whatsAppDeleted by vm.whatsAppDeleted.collectAsState()
    val lastGame by vm.lastGame.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        RussianAreaTile(russian) { onOpenSection("russian") }

        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            MusicAreaTile(music, Modifier.weight(1f)) { onOpenSection("music?from=profile") }
            MangaAreaTile(manga, Modifier.weight(1f)) { onOpenSection("manga") }
        }

        NewsAreaTile(news) { onOpenSection("news") }

        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            SmallAreaTile(
                hue = AreaColors.Games,
                icon = Icons.Outlined.SportsEsports,
                title = "Spiele",
                subtitle = lastGame?.let { "$it weiter" } ?: "${AreaTiles.GAME_COUNT} Spiele",
                modifier = Modifier.weight(1f),
            ) { onOpenSection("games") }
            SmallAreaTile(
                hue = AreaColors.Browser,
                icon = Icons.Outlined.Public,
                title = "Browser",
                subtitle = "Videos finden",
                modifier = Modifier.weight(1f),
            ) { onOpenSection("browser") }
            SmallAreaTile(
                hue = AreaColors.WhatsApp,
                icon = Icons.AutoMirrored.Outlined.Chat,
                title = "WhatsApp",
                subtitle = if (whatsAppDeleted > 0) "$whatsAppDeleted gerettet" else "Wächter",
                modifier = Modifier.weight(1f),
            ) { onOpenSection("whatsapp") }
        }

        // Platz für die Musik-Blase unten rechts und die Navigationsleiste.
        Spacer(Modifier.height(84.dp))
    }
}

// ── Bausteine ────────────────────────────────────────────────────────────────

@Composable
private fun Tile(
    hue: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = TilePadding,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "tile-press",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(TileShape)
            .background(lerp(HikariSurface, hue, 0.13f))
            .border(1.dp, hue.copy(alpha = 0.24f), TileShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun TileLabel(text: String, color: Color, icon: ImageVector? = null, glyph: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        } else if (glyph != null) {
            Text(glyph, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(7.dp))
        Text(text, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Großes, blasses Symbol oben rechts für Kacheln, die noch kein eigenes Bild haben. */
@Composable
private fun BoxScope.GhostIcon(icon: ImageVector, hue: Color) {
    Icon(
        icon,
        contentDescription = null,
        tint = hue.copy(alpha = 0.16f),
        modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = 18.dp).size(72.dp),
    )
}

// ── Russisch ─────────────────────────────────────────────────────────────────

@Composable
private fun RussianAreaTile(tile: RussianTile?, onClick: () -> Unit) {
    val hue = AreaColors.Russian
    Tile(hue, onClick, Modifier.fillMaxWidth().height(128.dp)) {
        Text(
            "Привет",
            color = hue.copy(alpha = 0.09f),
            fontSize = 54.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = (-10).dp),
        )
        Column(Modifier.fillMaxSize()) {
            TileLabel("Russisch", hue, glyph = "Я")
            Spacer(Modifier.height(7.dp))
            Text(
                tile?.headline ?: "Russisch lernen",
                color = HikariText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tile?.detail ?: "Sprechkurs",
                color = HikariTextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 56.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 58.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(tile?.progress ?: 0f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(hue),
                )
            }
        }
        val done = tile?.done == true
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(42.dp)
                .clip(CircleShape)
                .background(if (done) hue.copy(alpha = 0.18f) else hue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (done) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (done) hue else AreaColors.Ink,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── Musik ────────────────────────────────────────────────────────────────────

@Composable
private fun MusicAreaTile(tile: MusicTile?, modifier: Modifier, onClick: () -> Unit) {
    val hue = AreaColors.Music
    val hasCover = tile?.coverUrl != null
    Tile(hue, onClick, modifier.height(168.dp), padding = 0.dp) {
        if (hasCover) {
            AsyncImage(
                model = tile?.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Scrim: oben fast frei, unten dunkel — der Titel bleibt auf jedem Cover lesbar.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x33141418),
                        0.45f to Color(0x8C141418),
                        1f to Color(0xF2141418),
                    ),
                ),
            )
        } else {
            GhostIcon(Icons.Outlined.MusicNote, hue)
        }
        Column(Modifier.fillMaxSize().padding(TilePadding)) {
            TileLabel("Musik", if (hasCover) Color.White else hue, icon = Icons.Outlined.MusicNote)
            Spacer(Modifier.weight(1f))
            Text(
                tile?.title ?: "Noch nichts gehört",
                color = HikariText,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tile?.playing == true) {
                    PlayingBars(hue)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    tile?.artist ?: "Songs und Playlists",
                    color = if (hasCover) Color.White.copy(alpha = 0.72f) else HikariTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Drei wippende Balken — nur solange wirklich Musik läuft. */
@Composable
private fun PlayingBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "bars")
    Row(
        modifier = Modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(520, 380, 640).forEachIndexed { i, duration ->
            val level by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(level)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

// ── Manga ────────────────────────────────────────────────────────────────────

@Composable
private fun MangaAreaTile(tile: MangaTile?, modifier: Modifier, onClick: () -> Unit) {
    val hue = AreaColors.Manga
    val covers = tile?.covers.orEmpty()
    Tile(hue, onClick, modifier.height(168.dp)) {
        if (covers.isEmpty()) GhostIcon(Icons.AutoMirrored.Outlined.MenuBook, hue)
        Column(Modifier.fillMaxSize()) {
            TileLabel("Manga", hue, icon = Icons.AutoMirrored.Outlined.MenuBook)
            if (covers.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(start = if (covers.size == 1) 0.dp else 4.dp, top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy((-14).dp),
                ) {
                    covers.forEachIndexed { i, url ->
                        val middle = covers.size == 3 && i == 1
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .zIndex(if (middle) 1f else 0f)
                                .graphicsLayer {
                                    rotationZ = fanAngle(i, covers.size)
                                    translationY = if (middle) -3.dp.toPx() else 0f
                                }
                                // Ein einzelnes Titelbild darf größer sein als die drei im Fächer.
                                .width(if (covers.size == 1) 50.dp else 42.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(HikariSurfaceHigh),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                tile?.title ?: "Manga lesen",
                color = HikariText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tile?.detail ?: "Deine Reihen",
                color = HikariTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

/** Fächer: außen leicht gedreht, ein einzelnes Cover steht gerade. */
private fun fanAngle(index: Int, count: Int): Float = when {
    count <= 1 -> 0f
    index == 0 -> -7f
    index == count - 1 -> 7f
    else -> 0f
}

// ── Tagesbericht ─────────────────────────────────────────────────────────────

@Composable
private fun NewsAreaTile(tile: NewsTile?, onClick: () -> Unit) {
    val hue = AreaColors.News
    Tile(hue, onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TileLabel("Tagesbericht", hue, icon = Icons.AutoMirrored.Outlined.Article)
                if (tile != null && tile.count > 0) {
                    Text(
                        if (tile.count == 1) "1 Meldung" else "${tile.count} Meldungen",
                        color = AreaColors.Ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(hue)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                tile?.headline ?: "Noch kein Bericht für heute",
                color = HikariText,
                fontSize = 15.sp,
                lineHeight = 19.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val day = if (tile != null) tile.day else "Kommt jeden Morgen"
            if (day != null) {
                Spacer(Modifier.height(4.dp))
                Text(day, color = HikariTextMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

// ── Kleine Kacheln ───────────────────────────────────────────────────────────

@Composable
private fun SmallAreaTile(
    hue: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Tile(hue, onClick, modifier.height(96.dp), padding = 11.dp) {
        Column(Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = hue, modifier = Modifier.size(21.dp))
            Spacer(Modifier.weight(1f))
            Text(
                title,
                color = HikariText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = HikariTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
