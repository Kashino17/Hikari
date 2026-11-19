package com.hikari.app.ui.profile.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.profile.MangaTile
import com.hikari.app.domain.profile.ReadKind
import com.hikari.app.ui.games.GameAppIcon
import com.hikari.app.ui.games.GameCatalog
import com.hikari.app.ui.profile.BrowserTile
import com.hikari.app.ui.profile.GamesTile
import com.hikari.app.ui.profile.NewsTile
import com.hikari.app.ui.profile.WhatsAppTile
import kotlinx.coroutines.delay

/** Höhe der Paar-Reihen — gleich hoch wie Russisch/Musik, damit das Raster ruhig bleibt. */
internal val LowerRowHeight = 204.dp

private const val NEWS_SLIDE_MS = 5200
private const val WA_SLIDE_MS = 4600

// ── Tagesbericht: Pressefotos im Wechsel, Story-Balken oben ─────────────────

@Composable
internal fun NewsPhotoTile(tile: NewsTile?, modifier: Modifier, onClick: () -> Unit) {
    val slides = tile?.slides.orEmpty().let { all -> all.filter { it.imageUrl != null }.ifEmpty { all } }
    val withPhotos = slides.any { it.imageUrl != null }

    if (tile == null || !withPhotos) {
        // Kein Foto: kräftiges blaues Plakat mit der Schlagzeile.
        Tile(AreaColors.News, onClick, modifier.height(LowerRowHeight)) {
            Column(Modifier.fillMaxSize().padding(Pad)) {
                Text("Tagesbericht", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    tile?.headline ?: "Kommt jeden Morgen",
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                tile?.day?.let { Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp) }
            }
        }
        return
    }

    var index by remember(slides) { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slides, index) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(NEWS_SLIDE_MS, easing = LinearEasing))
        index = (index + 1) % slides.size
    }

    Tile(Color.Black, onClick, modifier.height(LowerRowHeight)) {
        Crossfade(targetState = index, animationSpec = tween(700), label = "news-slide") { i ->
            val slide = slides[i]
            Box(Modifier.fillMaxSize()) {
                KenBurnsImage(slide.imageUrl, key = i, durationMs = NEWS_SLIDE_MS + 900)
                BottomScrim(0.96f)
                Column(Modifier.fillMaxSize().padding(Pad).padding(top = 34.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        slide.headline,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(slide.source.ifBlank { null }, tile.day).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Story-Balken: welche Meldung gerade läuft — wie bei Instagram.
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                slides.indices.forEach { i ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    Box(
                        Modifier.weight(1f).height(2.5.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.32f)),
                    ) {
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImageLabel("Tagesbericht")
                if (tile.count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${tile.count}",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AreaColors.News)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Bild, das langsam heranfährt — die Kachel lebt, ohne zu zappeln. */
@Composable
private fun KenBurnsImage(model: Any?, key: Any, durationMs: Int, from: Float = 1f, to: Float = 1.12f) {
    val zoom = remember(key) { Animatable(from) }
    LaunchedEffect(key) { zoom.animateTo(to, tween(durationMs, easing = LinearEasing)) }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.value
            scaleY = zoom.value
        },
    )
}

// ── Lesen: violettes Plakat, das Buch liegt schräg darauf ───────────────────

@Composable
internal fun ReadPosterTile(tile: MangaTile?, modifier: Modifier, onClick: () -> Unit) {
    val ink = AreaColors.Ink
    val color = AreaColors.Manga
    val covers = tile?.covers.orEmpty()
    Tile(color, onClick, modifier.height(LowerRowHeight)) {
        if (covers.isNotEmpty()) {
            // Hinteres Buch leicht nach rechts gekippt, vorderes nach links — wie hingelegt.
            covers.getOrNull(1)?.let { url ->
                CoverCard(
                    url = url, kind = tile?.kinds?.getOrNull(1), title = tile?.titles?.getOrNull(1).orEmpty(),
                    width = 78.dp, height = 114.dp, angle = 11f,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 24.dp, y = 2.dp),
                )
            }
            CoverCard(
                url = covers.first(), kind = tile?.kinds?.firstOrNull(), title = tile?.titles?.firstOrNull().orEmpty(),
                width = 88.dp, height = 128.dp, angle = -8f,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = 22.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 14.dp).size(120.dp),
            )
        }
        Column(Modifier.fillMaxSize().padding(Pad)) {
            Text("Lesen", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                tile?.title ?: "Bücher und Manga",
                color = ink,
                fontSize = 19.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tile?.detail ?: "Weiterlesen, wo du warst",
                color = ink.copy(alpha = 0.72f),
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, end = 64.dp),
            )
            Spacer(Modifier.weight(1f))
            ProgressPlay(progress = tile?.progress, fill = ink, glyph = color, track = ink.copy(alpha = 0.18f))
        }
    }
}

/** Titelbild als kleines Buch: Ecken, Schatten, schräg. */
@Composable
private fun CoverCard(
    url: String,
    kind: ReadKind?,
    title: String,
    width: Dp,
    height: Dp,
    angle: Float,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .rotate(angle)
            .size(width, height)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Color(0xFF1B1B1F)),
    ) {
        if (kind == ReadKind.BOOK) {
            com.hikari.app.ui.ebooks.BookCover(
                title = title, authors = "", category = null, isPdf = false,
                imageUrl = url.takeIf { it.startsWith("/api/") },
                localPath = url.takeUnless { it.startsWith("/api/") },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Buchrücken: schmaler dunkler Streifen links.
        Box(Modifier.width(4.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.28f)))
    }
}

/** Start-Knopf; mit Fortschritt als Ring drumherum. */
@Composable
private fun ProgressPlay(progress: Float?, fill: Color, glyph: Color, track: Color) {
    if (progress == null) {
        PlayDot(done = false, fill = fill, ink = glyph)
        return
    }
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                fill, -90f, 360f * progress.coerceIn(0.02f, 1f), false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        PlayDot(done = false, fill = fill, ink = glyph, modifier = Modifier.size(42.dp))
    }
}

// ── Spiele: Plakat in der Farbe des letzten Spiels, Symbole als Fächer ──────

@Composable
internal fun GamesPosterTile(tile: GamesTile?, modifier: Modifier, onClick: () -> Unit) {
    val ink = AreaColors.Ink
    val info = GameCatalog.firstOrNull { it.id == tile?.id }
    val color = info?.color ?: AreaColors.Games
    val float = rememberInfiniteTransition(label = "game-float")
    val bob by float.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Tile(color, onClick, modifier) {
        tile?.others?.getOrNull(1)?.let {
            GameAppIcon(it, 58.dp, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = (-74).dp).rotate(16f).shadow(8.dp, RoundedCornerShape(14.dp)))
        }
        tile?.others?.getOrNull(0)?.let {
            GameAppIcon(it, 62.dp, Modifier.align(Alignment.BottomEnd).offset(x = (-76).dp, y = (-4).dp).rotate(-14f).shadow(8.dp, RoundedCornerShape(15.dp)))
        }
        GameAppIcon(
            tile?.id ?: "blockblast",
            92.dp,
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = (-10).dp)
                .graphicsLayer {
                    translationY = bob.dp.toPx()
                    rotationZ = -6f + bob * 0.5f
                }
                .shadow(16.dp, RoundedCornerShape(22.dp)),
        )
        Column(Modifier.fillMaxSize().padding(Pad)) {
            Text("Spiele", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                tile?.title ?: "${GameCatalog.size} Spiele",
                color = ink,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tile?.earned == true) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    tile?.stat ?: "Weiterspielen",
                    color = ink.copy(alpha = if (tile?.earned == true) 0.9f else 0.72f),
                    fontSize = 12.5.sp,
                    fontWeight = if (tile?.earned == true) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            PlayDot(done = false, fill = ink, ink = color)
        }
    }
}

// ── WhatsApp: grüne Fläche, die zuletzt gerettete Nachricht als Sprechblase ─

private val WaGreen = Color(0xFF25D366)
private val WaDark = Color(0xFF075E54)

@Composable
internal fun WhatsAppChatTile(tile: WhatsAppTile?, modifier: Modifier, onClick: () -> Unit) {
    val ink = AreaColors.Ink
    val msgs = tile?.latest.orEmpty()
    var index by remember(msgs) { mutableIntStateOf(0) }
    LaunchedEffect(msgs) {
        if (msgs.size < 2) return@LaunchedEffect
        while (true) {
            delay(WA_SLIDE_MS.toLong())
            index = (index + 1) % msgs.size
        }
    }
    Tile(WaGreen, onClick, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("WhatsApp", color = ink.copy(alpha = 0.75f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if ((tile?.count ?: 0) > 0) "${tile?.count} gerettet" else "Wächter an",
                    color = ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInVertically(tween(420)) { it / 2 } + fadeIn(tween(420))) togetherWith
                        (slideOutVertically(tween(320)) { -it / 2 } + fadeOut(tween(260)))
                },
                label = "wa-bubble",
            ) { i ->
                val m = msgs.getOrNull(i)
                Bubble(
                    sender = m?.sender ?: "Wächter",
                    text = m?.text ?: "Gelöschte Nachrichten landen hier",
                    tag = when {
                        m == null -> null
                        m.deleted -> "gelöscht"
                        else -> "bearbeitet"
                    },
                )
            }
        }
    }
}

@Composable
private fun Bubble(sender: String, text: String, tag: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp))
            .background(Color.White)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sender,
                color = WaDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (tag != null) {
                Spacer(Modifier.width(6.dp))
                Text(tag, color = Color(0xFF8696A0), fontSize = 10.sp, maxLines = 1)
            }
        }
        Text(
            text,
            color = AreaColors.Ink,
            fontSize = 12.5.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Browser: Vorschaubild des zuletzt gefundenen Videos ─────────────────────

@Composable
internal fun BrowserPhotoTile(tile: BrowserTile?, modifier: Modifier, onClick: () -> Unit) {
    val thumb = tile?.thumbnailUrl
    if (thumb == null) {
        Tile(AreaColors.Browser, onClick, modifier) {
            Icon(
                Icons.Outlined.Public,
                contentDescription = null,
                tint = AreaColors.Ink,
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp).size(96.dp),
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("Browser", color = AreaColors.Ink.copy(alpha = 0.75f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("Videos finden", color = AreaColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
        return
    }
    Tile(Color.Black, onClick, modifier) {
        KenBurnsImage(thumb, key = thumb, durationMs = 14_000, to = 1.18f)
        BottomScrim(0.9f)
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            ImageLabel("Browser")
            Spacer(Modifier.weight(1f))
            Text(
                tile.title,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Vorschau-Schalter: neu ↔ bisher, bis Kadir die neue Gestaltung freigibt ─

@Composable
internal fun PreviewSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Vorschau", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(3.dp),
        ) {
            listOf(true to "Neu", false to "Bisher").forEach { (value, label) ->
                val selected = value == on
                Text(
                    label,
                    color = if (selected) AreaColors.Ink else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { onChange(value) }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }
    }
}
