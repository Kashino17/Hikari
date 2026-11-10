package com.hikari.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.data.api.dto.isFilm
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.components.ResumeArtwork
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Die Bibliothek als eine durchgehende Seite, geordnet nach dem, was man hier
 * wirklich tut:
 *  1. weiterschauen (großes Bild der Stelle, an der man war),
 *  2. eine Serie oder einen Film wählen,
 *  3. sehen, was die abonnierten Kanäle Neues haben — je Kanal eine eigene Reihe,
 *  4. im Verlauf etwas wiederfinden.
 */
@Composable
internal fun LibraryHome(
    data: LibraryResponse,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlay: (LibraryVideoDto) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    onShowAll: (title: String, videos: List<LibraryVideoDto>) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seriesById = remember(data.series) { data.series.associateBy { it.id } }
    val continueWatching = data.continueWatching

    // Je Kanal die neuesten Videos; Kanäle mit dem frischesten Upload zuerst.
    val channelShelves = remember(data.channels, data.channelUploads) {
        val byChannel = data.channelUploads.groupBy { it.channel_id }
        data.channels
            .filter { it.id != MANUAL_CHANNEL }
            .mapNotNull { channel ->
                val videos = byChannel[channel.id].orEmpty().sortedByDescending { it.published_at }
                if (videos.isEmpty()) null else channel to videos
            }
            .sortedByDescending { (_, videos) -> videos.first().published_at }
    }

    val films = remember(data.recentlyAdded) { data.recentlyAdded.filter { it.isFilm() } }

    // Oben steht, womit es weitergeht — sonst das Neueste von den Kanälen.
    val hero = continueWatching.firstOrNull()
        ?: channelShelves.firstOrNull()?.second?.firstOrNull()
        ?: data.recentlyAdded.firstOrNull()
    val moreToContinue = continueWatching.drop(1)

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        if (hero != null) {
            item(key = "hero") {
                Hero(
                    video = hero,
                    series = hero.series_id?.let(seriesById::get),
                    onPlay = { onPlay(hero) },
                    onOpenSeries = onOpenSeries,
                )
            }
        }

        if (moreToContinue.isNotEmpty()) {
            item(key = "continue") {
                ShelfTitle(
                    "Weiterschauen",
                    onShowAll = { onShowAll("Weiterschauen", continueWatching) },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(moreToContinue, key = { it.id }) { video ->
                        ContinueCard(
                            video = video,
                            series = video.series_id?.let(seriesById::get),
                            onClick = { onPlay(video) },
                        )
                    }
                }
            }
        }

        if (data.series.isNotEmpty() || films.isNotEmpty()) {
            item(key = "archive") {
                ShelfTitle(if (films.isEmpty()) "Serien" else "Serien & Filme")
                ArchiveGrid(
                    series = data.series,
                    films = films,
                    // Für „Weiter mit S3 · F9" an der Kachel: die angefangene Folge je Serie.
                    resumeBySeries = remember(continueWatching) {
                        continueWatching.filter { it.series_id != null }
                            .groupBy { it.series_id!! }
                            .mapValues { it.value.first() }
                    },
                    onOpenSeries = onOpenSeries,
                    onLongPressSeries = onLongPressSeries,
                    onPlayFilm = onPlay,
                )
            }
        }

        if (channelShelves.isNotEmpty()) {
            item(key = "channels-title") { ShelfTitle("Deine Kanäle") }
            items(channelShelves, key = { (channel, _) -> "channel-${channel.id}" }) { (channel, videos) ->
                ChannelShelf(
                    channel = channel,
                    videos = videos,
                    onOpenChannel = { onOpenChannel(channel.id) },
                    onPlay = onPlay,
                )
            }
        }

        if (data.watchHistory.isNotEmpty()) {
            item(key = "history") {
                ShelfTitle(
                    "Verlauf",
                    onShowAll = { onShowAll("Verlauf", data.watchHistory) },
                    actionLabel = "Leeren",
                    onAction = onClearHistory,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.watchHistory, key = { "history-${it.id}" }) { video ->
                        VideoCard(
                            video = video,
                            secondLine = video.series_id?.let(seriesById::get)?.title ?: video.channelTitle,
                            onClick = { onPlay(video) },
                            onRemove = { onRemoveHistory(video.id) },
                        )
                    }
                }
            }
        }

        if (hero == null && data.series.isEmpty() && films.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Noch nichts in der Bibliothek", color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Importiere Serien oder Filme, oder abonniere Kanäle — dann erscheinen sie hier.",
                        color = HikariTextMuted,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

private const val MANUAL_CHANNEL = "manual"

// ── Oben: womit es weitergeht ────────────────────────────────────────────────

/**
 * Das Bild liegt in voller Breite im Format 16:9 — genau das Format von
 * YouTube-Vorschaubildern und Standbildern, es wird also nichts abgeschnitten.
 * Text und Knöpfe schieben sich von unten ein Stück ins Bild.
 */
@Composable
private fun Hero(
    video: LibraryVideoDto,
    series: SeriesDto?,
    onPlay: () -> Unit,
    onOpenSeries: (String) -> Unit,
) {
    val started = (video.progress_seconds ?: 0f) > 0f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val imageHeight = maxWidth * 9f / 16f
        Box(modifier = Modifier.fillMaxWidth().height(imageHeight).clickable(onClick = onPlay)) {
            ResumeArtwork(video = video, modifier = Modifier.fillMaxSize(), showTitleFallback = false)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(imageHeight * 0.62f)
                    // Kräftig genug, dass Text auch über einem hellen Standbild lesbar bleibt.
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to HikariBg.copy(alpha = 0.72f),
                            0.7f to HikariBg.copy(alpha = 0.96f),
                            1f to HikariBg,
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = imageHeight - 44.dp, start = 16.dp, end = 16.dp),
        ) {
            Text(
                text = when {
                    series != null -> listOf(series.title, episodeTag(video.season, video.episode))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    video.isFilm() -> "Film"
                    else -> video.channelTitle.orEmpty()
                },
                color = HikariAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (started) {
                ProgressLine(progressFraction(video), height = 4)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = if (started) "Noch ${remainingLabel(video)}" else durationLabel(video.duration_seconds),
                color = HikariTextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onPlay),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (started) "Weiterschauen" else "Abspielen",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (series != null) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, HikariBorderStrong, RoundedCornerShape(50))
                            .clickable { onOpenSeries(series.id) }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FormatListBulleted, contentDescription = null, tint = HikariText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Folgen", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Weiterschauen ────────────────────────────────────────────────────────────

@Composable
private fun ContinueCard(video: LibraryVideoDto, series: SeriesDto?, onClick: () -> Unit) {
    Column(modifier = Modifier.width(236.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurface),
        ) {
            ResumeArtwork(video = video, modifier = Modifier.fillMaxSize())
            ProgressLine(progressFraction(video), height = 4, modifier = Modifier.align(Alignment.BottomStart))
        }
        Text(
            text = series?.title ?: video.title,
            color = HikariText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = listOfNotNull(
                if (series != null) episodeTag(video.season, video.episode).takeIf { it.isNotBlank() }
                else video.channelTitle?.takeIf { it.isNotBlank() },
                "noch ${remainingLabel(video)}",
            ).joinToString(" · "),
            color = HikariTextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── Serien & Filme ───────────────────────────────────────────────────────────

/** Zweispaltiges Raster: bei einer Handvoll Titel sieht man alle auf einen Blick, ohne seitlich zu wischen. */
@Composable
private fun ArchiveGrid(
    series: List<SeriesDto>,
    films: List<LibraryVideoDto>,
    resumeBySeries: Map<String, LibraryVideoDto>,
    onOpenSeries: (String) -> Unit,
    onLongPressSeries: (SeriesDto) -> Unit,
    onPlayFilm: (LibraryVideoDto) -> Unit,
) {
    val tiles: List<@Composable (Modifier) -> Unit> = buildList {
        series.forEach { s ->
            add { modifier ->
                val resume = resumeBySeries[s.id]
                ArchiveTile(
                    title = s.title,
                    coverUrl = s.thumbnail_url,
                    line = when {
                        resume != null -> "Weiter mit ${episodeTag(resume.season, resume.episode)}"
                        s.episode_count > 0 -> seriesScope(s)
                        else -> "Serie"
                    },
                    highlightLine = resume != null,
                    progress = resume?.let(::progressFraction),
                    onClick = { onOpenSeries(s.id) },
                    onLongClick = { onLongPressSeries(s) },
                    modifier = modifier,
                )
            }
        }
        films.forEach { film ->
            add { modifier ->
                val started = (film.progress_seconds ?: 0f) > 0f
                ArchiveTile(
                    title = film.title,
                    coverUrl = film.thumbnail_url,
                    line = if (started) "Noch ${remainingLabel(film)}" else "Film · ${durationLabel(film.duration_seconds)}",
                    highlightLine = started,
                    progress = if (started) progressFraction(film) else null,
                    onClick = { onPlayFilm(film) },
                    onLongClick = null,
                    modifier = modifier,
                )
            }
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveTile(
    title: String,
    coverUrl: String?,
    line: String,
    highlightLine: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurface),
        ) {
            CoverArtwork(title = title, coverUrl = coverUrl)
            if (progress != null) {
                ProgressLine(progress, height = 4, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
        Text(
            text = title,
            color = HikariText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = line,
            color = if (highlightLine) HikariAmber else HikariTextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Cover in einer 16:9-Kachel. Die meisten Cover sind Standbilder im selben
 * Format und füllen die Kachel. Ein hochkantes Plakat würde beim Zuschneiden
 * Kopf und Titel verlieren — es wird deshalb ganz gezeigt, vor einer
 * unscharfen, abgedunkelten Fassung seiner selbst.
 */
@Composable
internal fun CoverArtwork(title: String, coverUrl: String?, showTitleFallback: Boolean = true) {
    var upright by remember(coverUrl) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        FallbackArtwork(title = title, showTitle = showTitleFallback)
        if (coverUrl.isNullOrBlank()) return@Box
        if (upright) {
            // Ruhige Unterlage — sonst schimmert der Ersatztitel durch die Unschärfe.
            Box(Modifier.fillMaxSize().background(HikariSurface))
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(18.dp).alpha(0.55f),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (upright) ContentScale.Fit else ContentScale.Crop,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                upright = size.width > 0f && size.height > 0f && size.width / size.height < 1.3f
            },
        )
    }
}

// ── Kanäle ───────────────────────────────────────────────────────────────────

/** Ein Kanal, eine Reihe: Kopf mit Bild und Name (führt zur Kanalseite), darunter seine neuesten Videos. */
@Composable
private fun ChannelShelf(
    channel: ChannelDto,
    videos: List<LibraryVideoDto>,
    onOpenChannel: () -> Unit,
    onPlay: (LibraryVideoDto) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChannel)
                .padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(HikariSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    channel.title.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    color = HikariTextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (!channel.thumbnail_url.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.thumbnail_url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.title,
                    color = HikariText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Neuestes ${relativeAge(videos.first().published_at)}",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Kanal öffnen", tint = HikariTextFaint, modifier = Modifier.size(24.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    secondLine = relativeAge(video.published_at),
                    onClick = { onPlay(video) },
                    // Bei „Neu vom Kanal" erkennt man ein Video am Vorschaubild, nicht am Standbild.
                    resumeFrame = false,
                )
            }
        }
    }
}

/** YouTube-Karte: Bild mit Dauer, darunter Titel und eine ruhige zweite Zeile. */
@Composable
private fun VideoCard(
    video: LibraryVideoDto,
    secondLine: String?,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    resumeFrame: Boolean = true,
) {
    val fraction = progressFraction(video)
    Column(modifier = Modifier.width(236.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurface),
        ) {
            ResumeArtwork(video = video, modifier = Modifier.fillMaxSize(), resumeFrame = resumeFrame)
            Text(
                clockLabel(video.duration_seconds),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Aus dem Verlauf entfernen", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            if (fraction > 0f) {
                ProgressLine(fraction, height = 4, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
        Text(
            text = video.title,
            color = HikariText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!secondLine.isNullOrBlank()) {
            Text(
                text = secondLine,
                color = HikariTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ── Bausteine ────────────────────────────────────────────────────────────────

@Composable
private fun ShelfTitle(
    title: String,
    onShowAll: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 30.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = HikariTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (onShowAll != null) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onShowAll).padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alle", color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = HikariText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, height: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height.dp).background(HikariAmber))
    }
}

private fun progressFraction(video: LibraryVideoDto): Float {
    val progress = video.progress_seconds ?: return 0f
    return if (video.duration_seconds > 0) (progress / video.duration_seconds).coerceIn(0f, 1f) else 0f
}

/** „S3 · F9", „Folge 9" oder leer. */
internal fun episodeTag(season: Int?, episode: Int?): String = when {
    season != null && episode != null -> "S$season · F$episode"
    episode != null -> "Folge $episode"
    else -> ""
}

/** „3 Staffeln · 42 Folgen" — bei nur einer Staffel reicht die Folgenzahl. */
internal fun seriesScope(series: SeriesDto): String {
    val episodes = if (series.episode_count == 1) "1 Folge" else "${series.episode_count} Folgen"
    return if (series.season_count > 1) "${series.season_count} Staffeln · $episodes" else episodes
}

/** Restlaufzeit: „8 min", „1 h 12 min". */
internal fun remainingLabel(video: LibraryVideoDto): String =
    durationLabel((video.duration_seconds - (video.progress_seconds ?: 0f).toInt()).coerceAtLeast(0))

internal fun durationLabel(seconds: Int): String {
    val minutes = (seconds / 60).coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/** Dauer als Uhr: „26:57", „1:02:30". */
internal fun clockLabel(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** „heute", „gestern", „vor 3 Tagen", „vor 2 Wochen" … [timestampMs] ≤ 0 ergibt leer. */
internal fun relativeAge(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0L) return ""
    val days = ((nowMs - timestampMs) / 86_400_000L).coerceAtLeast(0L)
    return when {
        days == 0L -> "heute"
        days == 1L -> "gestern"
        days < 7 -> "vor $days Tagen"
        days < 14 -> "vor 1 Woche"
        days < 31 -> "vor ${days / 7} Wochen"
        days < 61 -> "vor 1 Monat"
        days < 365 -> "vor ${days / 30} Monaten"
        days < 730 -> "vor 1 Jahr"
        else -> "vor ${days / 365} Jahren"
    }
}
