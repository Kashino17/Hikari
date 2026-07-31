package com.hikari.app.ui.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.ChapterGroup
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

// Quellmaterial sind YouTube-Thumbnails (16:9). Quadratische Karten würden
// jedes Cover mittig beschneiden, deshalb übernimmt das Layout das Format.
private val MIX_CARD_WIDTH = 208.dp
private val MIX_CARD_HEIGHT = 117.dp
private val TILE_WIDTH = 168.dp
private val TILE_HEIGHT = 95.dp

/**
 * Kuratierter Mix als Karte: ein Cover trägt die Fläche, der Titel liegt im
 * Verlauf darüber. Die Cover sind das einzige Farbige im sonst schwarzen
 * Layout — deshalb bekommen sie den Platz und alles andere bleibt still.
 */
@Composable
fun MixCard(
    title: String,
    songs: List<MusicSong>,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "mix-press",
    )

    Box(
        Modifier
            .width(MIX_CARD_WIDTH)
            .height(MIX_CARD_HEIGHT)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurfaceHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        CoverCollage(songs, Modifier.fillMaxSize())

        // Kräftiger Verlauf: die Titel müssen auch auf hellen Covern stehen.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to HikariBg.copy(alpha = 0.35f),
                        0.7f to HikariBg.copy(alpha = 0.85f),
                        1f to HikariBg.copy(alpha = 0.97f),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 11.dp),
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (songs.size == 1) "1 Song" else "${songs.size} Songs",
                fontSize = 11.sp,
                color = HikariTextMuted,
            )
        }
    }
}

/** Abgerundete Cover-Collage für Kopfbereiche außerhalb der Mix-Karte. */
@Composable
fun MixCoverPreview(songs: List<MusicSong>, modifier: Modifier = Modifier) {
    CoverCollage(
        songs,
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariSurfaceHigh),
    )
}

/**
 * Ein einzelnes Cover statt einer Collage: YouTube-Thumbnails tragen selbst
 * schon Text und Grafik, vier davon nebeneinander werden zur Unruhe.
 */
@Composable
private fun CoverCollage(songs: List<MusicSong>, modifier: Modifier = Modifier) {
    val cover = songs.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl

    if (cover == null) {
        Box(modifier.background(HikariSurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = HikariTextFaint, modifier = Modifier.size(34.dp))
        }
    } else {
        AsyncImage(
            model = cover,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

/** Quadratische Cover-Kachel für horizontale Reihen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongTile(
    song: MusicSong,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "tile-press",
    )

    Column(
        Modifier
            .width(TILE_WIDTH)
            .scale(scale)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box {
            AsyncImage(
                model = song.thumbnailUrl.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier
                    .width(TILE_WIDTH)
                    .height(TILE_HEIGHT)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .width(TILE_WIDTH)
                        .height(TILE_HEIGHT)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.GraphicEq, "Läuft gerade", tint = HikariPrimary, modifier = Modifier.size(26.dp))
                }
            }
            if (isDownloaded) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(HikariBg.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↓", fontSize = 11.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isCurrent) HikariPrimary else HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.uploader,
            fontSize = 11.sp,
            color = HikariTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Chart-Zeile mit Platzziffer. Nur dort einsetzen, wo die Reihenfolge
 * wirklich etwas aussagt — sonst ist die Nummer bloß Dekoration.
 */
@Composable
fun RankedSongRow(
    rank: Int,
    song: MusicSong,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
            color = if (rank <= 3) HikariPrimary else HikariTextFaint,
            modifier = Modifier.width(26.dp),
        )
        Box {
            AsyncImage(
                model = song.thumbnailUrl.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.GraphicEq, null, tint = HikariPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) HikariPrimary else HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.uploader,
                    fontSize = 11.sp,
                    color = HikariTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDownloaded) {
                    Text("  ·  offline", fontSize = 11.sp, color = HikariPrimary)
                }
            }
        }
        if (song.duration > 0) {
            Text(
                formatDuration(song.duration),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = HikariTextFaint,
            )
        }
    }
}

/**
 * Kompakter Verlauf: die zuletzt gehörten Stücke bleiben eine Wischbewegung
 * entfernt, ohne eine eigene Seite oder viel Höhe zu belegen.
 */
@Composable
fun HistoryStrip(
    songs: List<MusicSong>,
    currentVideoId: String?,
    onPlay: (MusicSong) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.History, null, tint = HikariTextFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "ZULETZT GEHÖRT",
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = HikariTextFaint,
                fontWeight = FontWeight.Medium,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(songs, key = { "hist-${it.videoId}" }) { song ->
                HistoryChip(
                    song = song,
                    isCurrent = song.videoId == currentVideoId,
                    onClick = { onPlay(song) },
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(song: MusicSong, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isCurrent) HikariSurfaceHigh else HikariCardBg)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = song.thumbnailUrl.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier
                    .width(58.dp)
                    .height(33.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .width(58.dp)
                        .height(33.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.GraphicEq, null, tint = HikariPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) HikariPrimary else HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.uploader,
                fontSize = 10.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Gruppen-Zeile in den Suchergebnissen: ein Hörbuch oder eine Podcast-Show
 * als eine Zeile statt vieler loser Kapitel. Antippen öffnet die Kapitelliste.
 */
@Composable
fun GroupRow(
    group: ChapterGroup,
    unitLabel: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = group.chapters.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HikariSurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.uploader,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${group.chapters.size} $unitLabel",
                fontSize = 11.sp,
                color = HikariTextMuted,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = HikariTextFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Moduswahl der Suche: Musik, Hörbücher oder Podcasts. */
@Composable
fun SearchModeChips(
    selected: MusicSearchMode,
    onSelect: (MusicSearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = mapOf(
        MusicSearchMode.MUSIC to "Musik",
        MusicSearchMode.AUDIOBOOK to "Hörbücher",
        MusicSearchMode.PODCAST to "Podcasts",
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicSearchMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Text(
                labels.getValue(mode),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.Black else HikariTextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) HikariPrimary else HikariCardBg)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Schalter für rein instrumentale Vorschläge. */
@Composable
fun InstrumentalToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint by animateColorAsState(
        targetValue = if (enabled) HikariPrimary else HikariTextFaint,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "toggle-icon",
    )

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .clickable(onClick = onToggle)
            .padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Durchgestrichenes Mikrofon sagt "keine Stimmen" direkter als eine Note.
        Crossfade(targetState = enabled, label = "toggle-symbol") { on ->
            Icon(
                if (on) Icons.Default.MicOff else Icons.Default.Mic,
                null,
                tint = iconTint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Ohne Gesang",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HikariText,
            )
            AnimatedContent(
                targetState = enabled,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                },
                label = "toggle-caption",
            ) { on ->
                Text(
                    if (on) "Nur Instrumentales" else "Alle Vorschläge",
                    fontSize = 11.sp,
                    color = HikariTextMuted,
                )
            }
        }
        HikariSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

/**
 * Leere Trefferliste bei aktivem Instrumental-Filter. Nennt den Grund und
 * bietet die Lösung direkt an, statt den Suchenden raten zu lassen.
 */
@Composable
fun FilteredEmptyHint(onDisableFilter: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.MicOff, null, tint = HikariPrimary, modifier = Modifier.size(38.dp))
        Text(
            "Keine Treffer ohne Gesang",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = HikariText,
            textAlign = TextAlign.Center,
        )
        Text(
            "„Ohne Gesang“ ist aktiv — dadurch bleiben Stücke mit Stimme außen vor. " +
                "Schalte den Filter aus, um die vollständigen Ergebnisse zu sehen.",
            fontSize = 13.sp,
            color = HikariTextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(HikariPrimary)
                .clickable(onClick = onDisableFilter)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Mic, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "Filter ausschalten",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    eyebrow: String? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(
                    it,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    color = HikariTextFaint,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HikariText)
        }
        onSeeAll?.let {
            Row(
                Modifier.clickable(onClick = it).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alle", fontSize = 12.sp, color = HikariTextMuted)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = HikariTextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Ruhige Platzhalter statt Spinner: die Seitenstruktur ist sofort sichtbar. */
@Composable
fun DiscoverSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(start = 16.dp, top = 22.dp, bottom = 12.dp)
                .size(width = 92.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HikariSurfaceHigh),
        )
        Row(
            Modifier.padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .width(MIX_CARD_WIDTH)
                        .height(MIX_CARD_HEIGHT)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HikariSurfaceHigh),
                )
            }
        }
        Box(
            Modifier
                .padding(start = 16.dp, top = 28.dp, bottom = 12.dp)
                .size(width = 116.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HikariSurfaceHigh),
        )
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HikariSurfaceHigh),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(HikariSurfaceHigh),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(HikariSurfaceHigh),
                    )
                }
            }
        }
    }
}
