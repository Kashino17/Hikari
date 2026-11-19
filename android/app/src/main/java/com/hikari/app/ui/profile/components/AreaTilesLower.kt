package com.hikari.app.ui.profile.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.profile.MangaTile
import com.hikari.app.domain.profile.ReadKind
import com.hikari.app.ui.games.GameAppIcon
import com.hikari.app.ui.games.GameCatalog
import com.hikari.app.ui.profile.GamesTile
import com.hikari.app.ui.profile.NewsTile
import kotlinx.coroutines.delay

/** Höhe des Paars »Lesen & Nachrichten«. */
internal val PairHeight = 250.dp

private const val NEWS_SLIDE_MS = 5200L
private val Paper = Color(0xFFF4F1EA)
private val PaperInk = Color(0xFF111111)
private val PaperMuted = Color(0xFF666666)
private val ReadNight = Color(0xFF18122B)
private val DockSurface = Color(0xFF17171A)

// ── Abschnitts-Überschrift ──────────────────────────────────────────────────

@Composable
internal fun SectionHeader(title: String, first: Boolean = false) {
    Text(
        title,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = if (first) 2.dp else 14.dp, start = 2.dp),
    )
}

// ── Lesen: Titelbilder als Stapel auf dunklem Violett ──────────────────────

@Composable
internal fun ReadStackTile(tile: MangaTile?, modifier: Modifier, onClick: () -> Unit) {
    val covers = tile?.covers.orEmpty().take(3)
    Tile(ReadNight, onClick, modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text("Lesen", color = AreaColors.Manga, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
                if (covers.isEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = AreaColors.Manga,
                        modifier = Modifier.align(Alignment.Center).size(72.dp),
                    )
                }
                // Hinterste zuerst zeichnen, das zuletzt Gelesene liegt oben.
                val angles = listOf(-4f, 5f, 12f)
                for (k in covers.indices.reversed()) {
                    CoverCard(
                        url = covers[k],
                        kind = tile?.kinds?.getOrNull(k),
                        title = tile?.titles?.getOrNull(k).orEmpty(),
                        width = 92.dp,
                        height = 134.dp,
                        angle = angles[k],
                        modifier = Modifier.offset(x = (k * 24).dp, y = (k * 6 + 4).dp),
                    )
                }
            }
            Text(
                tile?.title ?: "Bücher und Manga",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tile?.detail ?: "Weiterlesen, wo du warst",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            .shadow(12.dp, shape)
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
    }
}

// ── Tagesbericht: Zeitungsseite, oben Pressefotos im Wechsel ────────────────

@Composable
internal fun NewsPaperTile(tile: NewsTile?, modifier: Modifier, onClick: () -> Unit) {
    val slides = tile?.slides.orEmpty().let { all -> all.filter { it.imageUrl != null }.ifEmpty { all } }
    var index by remember(slides) { mutableIntStateOf(0) }
    LaunchedEffect(slides) {
        if (slides.size < 2) return@LaunchedEffect
        while (true) {
            delay(NEWS_SLIDE_MS)
            index = (index + 1) % slides.size
        }
    }
    Tile(Paper, onClick, modifier) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.44f).background(Color.Black)) {
                Crossfade(targetState = index, animationSpec = tween(700), label = "news-photo") { i ->
                    val url = slides.getOrNull(i)?.imageUrl
                    if (url != null) KenBurnsImage(url, key = i, durationMs = NEWS_SLIDE_MS.toInt() + 900)
                    else Box(Modifier.fillMaxSize().background(AreaColors.News))
                }
            }
            Column(Modifier.fillMaxWidth().weight(0.56f).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "Tagesbericht",
                        color = PaperInk,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.weight(1f))
                    if ((tile?.count ?: 0) > 0) {
                        Text("${tile?.count}", color = PaperMuted, fontSize = 10.5.sp)
                    }
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(1.5.dp).background(PaperInk))
                Crossfade(
                    targetState = index,
                    animationSpec = tween(700),
                    label = "news-text",
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                ) { i ->
                    val s = slides.getOrNull(i)
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            s?.headline ?: tile?.headline ?: "Kommt jeden Morgen",
                            color = PaperInk,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        val line = listOfNotNull(s?.source?.ifBlank { null }, tile?.day).joinToString(" · ")
                        if (line.isNotEmpty()) {
                            Text(line, color = PaperMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Bild, das langsam heranfährt — die Kachel lebt, ohne zu zappeln. */
@Composable
private fun KenBurnsImage(model: Any?, key: Any, durationMs: Int) {
    val zoom = remember(key) { Animatable(1f) }
    LaunchedEffect(key) { zoom.animateTo(1.12f, tween(durationMs, easing = LinearEasing)) }
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

// ── Apps: drei gleiche Symbole auf ruhiger Fläche ───────────────────────────

@Composable
internal fun AppsDock(
    games: GamesTile?,
    rescued: Int,
    browserCount: Int?,
    onGames: () -> Unit,
    onWhatsApp: () -> Unit,
    onBrowser: () -> Unit,
) {
    val bob = rememberInfiniteTransition(label = "dock-bob").animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(DockSurface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), TileShape)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val played = games?.played == true
        DockApp(
            name = if (played) games!!.title else "Spiele",
            sub = if (played) games!!.stat else "${GameCatalog.size} Spiele",
            modifier = Modifier.weight(1f),
            onClick = onGames,
        ) {
            GameAppIcon(
                games?.id ?: GameCatalog.first().id,
                DockIcon,
                Modifier.graphicsLayer {
                    translationY = bob.value.dp.toPx()
                    rotationZ = bob.value
                },
            )
        }
        DockDivider()
        DockApp(
            name = "WhatsApp",
            sub = if (rescued > 0) "$rescued gerettet" else "Wächter an",
            modifier = Modifier.weight(1f),
            onClick = onWhatsApp,
        ) { WhatsAppLogo(DockIcon) }
        DockDivider()
        DockApp(
            name = "Browser",
            sub = browserCount?.takeIf { it > 0 }?.let { "$it gefunden" } ?: "Videos finden",
            modifier = Modifier.weight(1f),
            onClick = onBrowser,
        ) { HikariBrowserIcon(DockIcon) }
    }
}

private val DockIcon = 58.dp

@Composable
private fun DockDivider() {
    Box(Modifier.width(1.dp).height(70.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun DockApp(
    name: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            sub,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── Gezeichnete Symbole ─────────────────────────────────────────────────────

private val WaGreen = Color(0xFF25D366)
private val WaBubble = PathParser().parsePathString(
    "M24 6.5c-9.7 0-17.5 7.8-17.5 17.4 0 3.3.9 6.5 2.6 9.2L6.5 41.5l8.6-2.5c2.6 1.5 5.6 2.3 8.9 2.3 " +
        "9.7 0 17.5-7.8 17.5-17.4S33.7 6.5 24 6.5z",
).toPath()
private val WaPhone = PathParser().parsePathString(
    "M17.6 15.2c-.5-1.1-1-1.1-1.5-1.1h-1.3c-.4 0-1.2.2-1.8.8-.6.7-2.3 2.3-2.3 5.6s2.4 6.5 2.7 6.9" +
        "c.3.5 4.6 7.4 11.4 10.1 5.7 2.2 6.8 1.8 8.1 1.7 1.2-.1 4-1.6 4.5-3.2.6-1.6.6-2.9.4-3.2" +
        "-.2-.3-.6-.5-1.3-.8l-4.3-2.1c-.6-.2-1-.3-1.4.3-.4.6-1.6 2.1-2 2.5-.4.4-.7.5-1.4.2" +
        "-.6-.3-2.7-1-5.1-3.2-1.9-1.7-3.2-3.8-3.5-4.4-.4-.6 0-1 .3-1.3l1-1.1c.3-.4.4-.6.6-1.1" +
        ".2-.4.1-.8 0-1.1l-1.9-4.5z",
).toPath()

/** WhatsApp-Logo: weiße Sprechblase mit Hörer auf Grün, im App-Symbol-Rahmen. */
@Composable
private fun WhatsAppLogo(size: Dp) {
    Box(
        Modifier.size(size).shadow(8.dp, RoundedCornerShape(size * 0.24f)).clip(RoundedCornerShape(size * 0.24f)).background(WaGreen),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.74f)) {
            val k = this.size.minDimension / 48f
            scale(k, k, pivot = Offset.Zero) {
                drawPath(WaBubble, Color.White, style = Stroke(width = 3.2f, join = StrokeJoin.Round))
                translate(4.6f, 4.4f) {
                    scale(0.82f, 0.82f, pivot = Offset.Zero) { drawPath(WaPhone, Color.White) }
                }
            }
        }
    }
}

private val Sparkle = PathParser().parsePathString(
    "M38 5l1.6 4.4L44 11l-4.4 1.6L38 17l-1.6-4.4L32 11l4.4-1.6z",
).toPath()

/** Hikari-Browser: amberfarbene Weltkugel mit Funkeln auf fast Schwarz. */
@Composable
private fun HikariBrowserIcon(size: Dp) {
    val bg = Color(0xFF14141A)
    Box(
        Modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(size * 0.24f))
            .clip(RoundedCornerShape(size * 0.24f))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.78f)) {
            val k = this.size.minDimension / 48f
            scale(k, k, pivot = Offset.Zero) {
                val c = Offset(22f, 26f)
                drawCircle(Color(0xFFFBBF24), radius = 14f, center = c)
                val line = Stroke(width = 2.4f)
                drawOval(bg, topLeft = Offset(16f, 12f), size = Size(12f, 28f), style = line)
                drawLine(bg, Offset(8f, 26f), Offset(36f, 26f), strokeWidth = 2.4f)
                drawLine(bg, Offset(10.5f, 19f), Offset(33.5f, 19f), strokeWidth = 2.4f)
                drawLine(bg, Offset(10.5f, 33f), Offset(33.5f, 33f), strokeWidth = 2.4f)
                drawPath(Sparkle, Color(0xFFFDE68A))
            }
        }
    }
}
