package com.hikari.app.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.HomeSectionKind
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.InstrumentalCatalog
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/** Wiedererkennungsfarbe des Instrumental-Modus: warmes Elfenbein wie Klaviertasten. */
internal val INSTRUMENTAL_ACCENT = Color(0xFFEADBB8)

/**
 * Farbe je Instrument — jede Kategorie bekommt ihre eigene, satte Farbe,
 * damit das Raster beim Scrollen Orientierung gibt (keine Braun-/Korall-
 * töne: die wirken auf Schwarz schlammig).
 */
private val INSTRUMENT_COLORS = mapOf(
    "violin" to Color(0xFFC2416B),
    "guitar" to Color(0xFF0E9F9E),
    "sax" to Color(0xFFCA8A04),
    "cello" to Color(0xFF7C3AED),
    "harp" to Color(0xFF2563EB),
    "flute" to Color(0xFF059669),
    "orchestra" to Color(0xFFB91C1C),
    "koto" to Color(0xFFDB2777),
    "synth" to Color(0xFF4F46E5),
    "lofi" to Color(0xFF7E6BB8),
    "jazz" to Color(0xFF0369A1),
)

/**
 * Instrument-Kategorien: Piano als breite Hero-Kachel mit gezeichneten
 * Tasten ganz oben, darunter die übrigen Instrumente im Zweier-Raster.
 * Antippen öffnet die Mix-Seite des Instruments (gefiltert ohne Gesang).
 */
@Composable
internal fun InstrumentGrid(onOpenInstrument: (title: String, query: String) -> Unit) {
    val piano = InstrumentalCatalog.INSTRUMENTS.first()
    val rest = InstrumentalCatalog.INSTRUMENTS.drop(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        PianoHeroTile(onClick = { onOpenInstrument(piano.title, piano.query) })
        Spacer(Modifier.height(10.dp))
        rest.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { instrument ->
                    val color = INSTRUMENT_COLORS[instrument.id] ?: Color(0xFF6B7A8F)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.60f))))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .muPressable { onOpenInstrument(instrument.title, instrument.query) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // Gleicher Glanzkreis wie bei den Genre-Kacheln.
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 16.dp, y = 20.dp)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                        )
                        Text(
                            instrument.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Breite Piano-Kachel: dunkler Grund, rechts eine angedeutete Klaviatur. */
@Composable
private fun PianoHeroTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E1D22), Color(0xFF2B2A31))))
            .border(1.dp, INSTRUMENTAL_ACCENT.copy(alpha = 0.35f), shape)
            .muPressable(onClick = onClick),
    ) {
        // Klaviatur: Elfenbein-Tasten mit schwarzen Halbtönen im 2-3-Muster.
        Canvas(Modifier.fillMaxHeight().width(170.dp).align(Alignment.CenterEnd)) {
            val keys = 7
            val keyW = size.width / keys
            for (i in 0 until keys) {
                drawRoundRect(
                    color = INSTRUMENTAL_ACCENT.copy(alpha = 0.92f),
                    topLeft = Offset(i * keyW + 1.5f, 0f),
                    size = Size(keyW - 3f, size.height),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
            listOf(0, 1, 3, 4, 5).forEach { i ->
                drawRoundRect(
                    color = Color(0xFF141317),
                    topLeft = Offset((i + 1) * keyW - keyW * 0.3f, 0f),
                    size = Size(keyW * 0.6f, size.height * 0.6f),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        // Weicher Übergang, damit der Text nicht auf den Tasten steht.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF1E1D22),
                        0.45f to Color(0xFF1E1D22).copy(alpha = 0.95f),
                        0.75f to Color(0xFF1E1D22).copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, end = 120.dp)) {
            Text(
                "DEIN INSTRUMENT",
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = INSTRUMENTAL_ACCENT,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text("Piano", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                "Solo, Covers, Klassik & Anime",
                fontSize = 12.sp,
                color = HikariTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Radio-Karte: startet einen Strom aus Songs, die noch nie liefen; Autoplay
 * hängt am Ende automatisch Passendes an. Cover rechts, Text links auf
 * Verlauf, weißer runder Play-Button (Spotify-Stil).
 */
@Composable
internal fun RadioHeroCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    songs: List<MusicSong>,
    accent: Color,
    isPlayingFrom: Boolean,
    /** Tipp auf die Karte: Titel als Playlist-Seite öffnen. */
    onOpen: () -> Unit,
    /** Tipp auf den Play-Button: Radio sofort starten. */
    onPlay: () -> Unit,
) {
    if (songs.isEmpty()) return
    val shape = RoundedCornerShape(20.dp)
    val cover = songs.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .background(HikariCardBg)
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .muPressable(onClick = onOpen),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.75f,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.CenterEnd),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to HikariCardBg,
                        0.42f to HikariCardBg.copy(alpha = 0.92f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        // Feiner Farbschein in der Bereichsfarbe oben links.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset.Zero,
                        radius = 700f,
                    ),
                ),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, end = 96.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlayingFrom) {
                    MuEqualizerBars(playing = true, color = accent, modifier = Modifier.height(10.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    eyebrow,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = HikariTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previews = songs.drop(1).filter { it.thumbnailUrl.isNotEmpty() }.take(3)
                Box(Modifier.width((22 + 16 * (previews.size - 1).coerceAtLeast(0)).dp).height(22.dp)) {
                    previews.forEachIndexed { i, s ->
                        AsyncImage(
                            model = s.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .offset(x = (16 * i).dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, HikariCardBg, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${songs.size} Titel · danach Autoplay",
                    fontSize = 11.sp,
                    color = HikariTextMuted,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White)
                .muPressable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, "Radio starten", tint = Color.Black, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * Feed des Instrumental-Modus. Statt gewöhnlicher Playlists: Radio ohne
 * Gesang, die Instrument-Kategorien (Piano oben), "Piano für dich", die
 * rotierenden Mix-Instrumentals als Karten und danach deren Inhalte im
 * Wechsel aus Kachelreihe und kompakter Liste.
 */
internal fun LazyListScope.instrumentalContent(
    viewModel: MusicViewModel,
    currentVideoId: String?,
    downloadedIds: Set<String>,
    progressMap: Map<String, Float>,
    online: Boolean,
    onOpenMix: (title: String, query: String, mode: String) -> Unit,
) {
    val sections = viewModel.homeSections
    if (sections.isEmpty()) return
    val mode = MusicSearchMode.INSTRUMENTAL.apiValue

    sections.firstOrNull { it.kind == HomeSectionKind.MIX }?.let { radio ->
        item(key = "inst-radio") {
            RadioHeroCard(
                eyebrow = "RADIO · OHNE GESANG",
                title = radio.title,
                subtitle = "Endlos Instrumentals, die du noch nicht kennst — Piano, Streicher, Gitarre.",
                songs = radio.songs,
                accent = INSTRUMENTAL_ACCENT,
                isPlayingFrom = radio.songs.any { it.videoId == currentVideoId },
                onOpen = {
                    viewModel.stageCollection(radio.query, radio.songs)
                    onOpenMix(radio.title, radio.query, mode)
                },
                onPlay = { viewModel.play(radio.songs.first(), radio.songs) },
            )
        }
    }

    item(key = "inst-grid-header") { SectionHeader("Instrumente", eyebrow = "STÖBERN") }
    item(key = "inst-grid") {
        InstrumentGrid(onOpenInstrument = { title, query -> onOpenMix(title, query, mode) })
    }

    val curated = sections.filter { it.kind == HomeSectionKind.CURATED }
    val pianoFocus = curated.firstOrNull { it.title == InstrumentalCatalog.PIANO_FOCUS_TITLE }
    val mixes = curated.filter { it !== pianoFocus }

    pianoFocus?.let { piano ->
        item(key = "inst-piano-header") {
            SectionHeader(
                piano.title,
                eyebrow = "DEIN INSTRUMENT",
                onSeeAll = { onOpenMix("Piano", piano.query, mode) },
            )
        }
        item(key = "inst-piano-card") {
            QuickPickPagerCard(
                songs = piano.songs,
                currentVideoId = currentVideoId,
                showRanks = false,
                onPlay = { song -> viewModel.play(song, piano.songs) },
            )
        }
    }

    if (mixes.isNotEmpty()) {
        item(key = "inst-mixes-header") { SectionHeader("Mix-Instrumentals", eyebrow = "KURATIERT") }
        item(key = "inst-mixes-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(mixes, key = { "im-${it.title}" }) { section ->
                    MixCard(
                        title = section.title,
                        songs = section.songs,
                        onClick = { onOpenMix(section.title, section.query, mode) },
                    )
                }
            }
        }
    }

    sections.firstOrNull { it.kind == HomeSectionKind.SIMILAR }?.let { similar ->
        item(key = "inst-similar-header") {
            SectionHeader(
                similar.title,
                onSeeAll = {
                    viewModel.stageCollection(similar.query, similar.songs)
                    onOpenMix(similar.title, similar.query, mode)
                },
            )
        }
        item(key = "inst-similar-card") {
            QuickPickPagerCard(
                songs = similar.songs,
                currentVideoId = currentVideoId,
                showRanks = false,
                onPlay = { song -> viewModel.play(song, similar.songs) },
            )
        }
    }

    // Inhalte der Mixe im Abwechslungs-Rhythmus: Kachelreihe, dann Liste.
    mixes.forEachIndexed { index, section ->
        item(key = "inst-h-${section.title}") {
            SectionHeader(section.title, onSeeAll = { onOpenMix(section.title, section.query, mode) })
        }
        if (index % 2 == 0) {
            item(key = "inst-row-${section.title}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.songs, key = { "it-${section.title}-${it.videoId}" }) { song ->
                        TileBound(song, viewModel, section.songs)
                    }
                }
            }
        } else {
            items(section.songs.take(4), key = { "il-${section.title}-${it.videoId}" }) { song ->
                SongRow(
                    song,
                    viewModel,
                    section.songs,
                    isCurrent = currentVideoId == song.videoId,
                    isDownloaded = song.videoId in downloadedIds,
                    progress = progressMap[song.videoId],
                    online = online,
                )
            }
        }
    }
}
