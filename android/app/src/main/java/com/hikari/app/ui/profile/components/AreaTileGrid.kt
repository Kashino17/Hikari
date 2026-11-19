package com.hikari.app.ui.profile.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.LocalFireDepartment
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.profile.AreaTiles
import com.hikari.app.domain.profile.MangaTile
import com.hikari.app.domain.profile.ReadKind
import com.hikari.app.domain.profile.RussianTile
import com.hikari.app.ui.profile.AreaTilesViewModel
import com.hikari.app.ui.profile.FitnessTile
import com.hikari.app.ui.profile.MindTile
import com.hikari.app.ui.profile.MusicTile
import com.hikari.app.ui.profile.NewsTile

/**
 * Eine Farbe je Bereich — VOLL eingesetzt (Fläche, Plakat), nie als blasse Tönung.
 * Kadir 20.11.2026: »keine verblassten Glaskarten, visueller, mehr Bilder«.
 */
internal object AreaColors {
    val Russian = Color(0xFFF472B6)
    val Fitness = Color(0xFFFFFFFF)
    val Mind = Color(0xFFF4C163)
    val MindNight = Color(0xFF0A0F26)
    val Music = Color(0xFF22D3EE)
    val Manga = Color(0xFFA78BFA)
    val Books = Color(0xFFD4A373)
    val News = Color(0xFF3B82F6)
    val Games = Color(0xFFFB923C)
    val Browser = Color(0xFF2DD4BF)
    val WhatsApp = Color(0xFF4ADE80)

    /** Schrift und Symbole auf voll gefärbten Flächen. */
    val Ink = Color(0xFF0B0B0D)
}

internal val TileShape = RoundedCornerShape(22.dp)
internal val Gap = 12.dp
internal val Pad = 16.dp

/**
 * Die Bereiche, in drei ruhigen Abschnitten (Kadir wählte am 20.11.2026 Entwurf H):
 * »Heute« (Achtsamkeit, Russisch + Musik, Fitness), »Lesen & Nachrichten« (gleich großes
 * Paar) und »Apps« (Spiele, WhatsApp, Browser als neutrale Symbol-Leiste). Mehr Luft,
 * gleiche Abstände und Höhen — vorher wirkte die Seite zusammengequetscht und zu bunt.
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
    val fitness by vm.fitness.collectAsState()
    val mind by vm.mind.collectAsState()
    val games by vm.games.collectAsState()
    val rescued by vm.whatsAppRescued.collectAsState()
    val browser by vm.browser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        SectionHeader("Heute", first = true)
        MindAreaTile(mind) { onOpenSection("mind") }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            RussianAreaTile(russian, Modifier.weight(1f)) { onOpenSection("russian") }
            MusicAreaTile(music, Modifier.weight(1f)) { onOpenSection("music") }
        }
        FitnessAreaTile(fitness) { onOpenSection("fitness") }

        SectionHeader("Lesen & Nachrichten")
        Row(Modifier.height(PairHeight), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            ReadStackTile(manga, Modifier.weight(1f).fillMaxHeight()) { onOpenSection("manga") }
            NewsPaperTile(news, Modifier.weight(1f).fillMaxHeight()) { onOpenSection("news") }
        }

        SectionHeader("Apps")
        AppsDock(
            games = games,
            rescued = rescued,
            browserCount = browser?.count,
            onGames = { onOpenSection("games") },
            onWhatsApp = { onOpenSection("whatsapp") },
            onBrowser = { onOpenSection("browser") },
        )

        // Platz für die Musik-Blase unten rechts und die Navigationsleiste.
        Spacer(Modifier.height(84.dp))
    }
}

// ── Bausteine ────────────────────────────────────────────────────────────────

/** Kachel mit voller Fläche; drückt sich beim Antippen leicht ein. */
@Composable
internal fun Tile(
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: Boolean = false,
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
            .background(background)
            .then(if (border) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), TileShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = content,
    )
}

/** Dunkler Verlauf am unteren Rand — Text bleibt auf jedem Bild lesbar. */
@Composable
internal fun BoxScope.BottomScrim(strength: Float = 0.92f) {
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                // Oben ein Hauch Dunkel für die Bereichs-Beschriftung, unten kräftig für Titel und Text.
                0f to Color.Black.copy(alpha = 0.55f),
                0.22f to Color.Transparent,
                0.5f to Color.Black.copy(alpha = strength * 0.3f),
                1f to Color.Black.copy(alpha = strength),
            ),
        ),
    )
}

/** Bereichsname auf einem Bild: kleines schwarzes Etikett — auf jedem Cover lesbar. */
@Composable
internal fun ImageLabel(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
internal fun PlayDot(done: Boolean, fill: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(46.dp).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (done) Icons.Rounded.Check else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(26.dp),
        )
    }
}

// ── Fitness: schwarz-weiß, das Übungsbild des Tages auf Weiß ────────────────

@Composable
private fun FitnessAreaTile(tile: FitnessTile?, onClick: () -> Unit) {
    Tile(Color.Black, onClick, Modifier.fillMaxWidth().height(184.dp), border = true) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(Pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fitness", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val streak = tile?.streak ?: 0
                    if (streak > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                        Text("$streak", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tile?.title?.ifBlank { null } ?: "Training, Skills, Ernährung",
                    color = Color.White,
                    fontSize = 23.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tile?.detail?.ifBlank { null } ?: "KI-Coach, Calisthenics, Foto-Tracker",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                PlayDot(tile?.done == true, Color.White, Color.Black)
            }
            Box(
                Modifier.fillMaxHeight().width(138.dp).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (tile?.imageUrl != null) {
                    AsyncImage(
                        model = tile.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ── Achtsamkeit: Nachtblau, goldener Mond ───────────────────────────────────

@Composable
private fun MindAreaTile(tile: MindTile?, onClick: () -> Unit) {
    val gold = AreaColors.Mind
    val night = AreaColors.MindNight
    Tile(night, onClick, Modifier.fillMaxWidth().height(150.dp), border = true) {
        // Mond: goldene Scheibe, von einer nachtblauen Scheibe zur Sichel angeschnitten.
        Canvas(Modifier.align(Alignment.CenterEnd).size(150.dp).offset(x = 18.dp)) {
            val r = size.minDimension * 0.30f
            val c = Offset(size.width * 0.50f, size.height * 0.50f)
            drawCircle(gold, radius = r, center = c)
            drawCircle(night, radius = r, center = Offset(c.x + r * 0.45f, c.y - r * 0.30f))
            drawCircle(gold.copy(alpha = 0.9f), radius = 3.5f.dp.toPx(), center = Offset(size.width * 0.18f, size.height * 0.22f))
            drawCircle(gold.copy(alpha = 0.7f), radius = 2.5f.dp.toPx(), center = Offset(size.width * 0.80f, size.height * 0.80f))
        }
        Column(Modifier.fillMaxSize().padding(Pad).padding(end = 110.dp)) {
            Text("Achtsamkeit", color = gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                tile?.title ?: "Ein Moment für dich",
                color = Color(0xFFF6F0E4),
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                tile?.detail ?: "Yoga, Meditation, Atmen",
                color = Color(0xFFA4AACB),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Russisch: Plakat in Pink ────────────────────────────────────────────────

@Composable
private fun RussianAreaTile(tile: RussianTile?, modifier: Modifier, onClick: () -> Unit) {
    val ink = AreaColors.Ink
    Tile(AreaColors.Russian, onClick, modifier.height(204.dp)) {
        Text(
            "Я",
            color = ink,
            fontSize = 170.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 44.dp),
        )
        Column(Modifier.fillMaxSize().padding(Pad)) {
            Text("Russisch", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                tile?.headline ?: "Russisch lernen",
                color = ink,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tile?.detail ?: "Sprechkurs",
                color = ink.copy(alpha = 0.72f),
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, end = 30.dp),
            )
            Spacer(Modifier.weight(1f))
            PlayDot(tile?.done == true, ink, AreaColors.Russian)
        }
    }
}

// ── Musik: das Cover randlos ────────────────────────────────────────────────

@Composable
private fun MusicAreaTile(tile: MusicTile?, modifier: Modifier, onClick: () -> Unit) {
    val hasCover = tile?.coverUrl != null
    Tile(if (hasCover) Color.Black else AreaColors.Music, onClick, modifier.height(204.dp)) {
        if (hasCover) {
            AsyncImage(
                model = tile?.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            BottomScrim()
        } else {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = AreaColors.Ink,
                modifier = Modifier.align(Alignment.Center).size(84.dp),
            )
        }
        val fg = if (hasCover) Color.White else AreaColors.Ink
        Column(Modifier.fillMaxSize().padding(Pad)) {
            if (hasCover) ImageLabel("Musik") else Text("Musik", color = fg.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                tile?.title ?: "Noch nichts gehört",
                color = fg,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tile?.playing == true) {
                    PlayingBars(if (hasCover) AreaColors.Music else AreaColors.Ink)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    tile?.artist ?: "Songs und Playlists",
                    color = fg.copy(alpha = 0.72f),
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
