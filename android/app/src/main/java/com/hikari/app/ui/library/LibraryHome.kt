package com.hikari.app.ui.library

import com.hikari.app.domain.library.Shelves
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.hikari.app.data.api.dto.isArchive
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
 * Der Kanal-Bereich, aufgebaut wie die YouTube-App: oben die Kanäle als runde
 * Karten zum Filtern (ganz links die Startseite mit allen Kanälen), darunter
 * die Videos groß untereinander, das neueste zuerst. Geladen werden immer nur
 * ein paar — die nächsten erst beim Scrollen.
 */
@Composable
internal fun ChannelsHome(
    data: LibraryResponse,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlay: (LibraryVideoDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelsFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val channels = remember(data.channels, data.channelUploads) { storyChannels(data) }
    val channelById = remember(data.channels) { data.channels.associateBy { it.id } }
    var searching by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.start(data.channelUploads) }

    // Kurz vor dem Ende der Liste die nächsten Videos holen.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearEnd, state.videos.size, state.loading, state.endReached) {
        if (nearEnd && !state.loading && !state.endReached) viewModel.loadMore()
    }
    // Neuer Kanal oder neue Suche: wieder oben anfangen.
    LaunchedEffect(state.channelId, state.query) { listState.scrollToItem(0) }

    fun closeSearch() {
        searching = false
        viewModel.setQuery("")
    }
    BackHandler(enabled = searching) { closeSearch() }

    Column(modifier = modifier.fillMaxSize().background(HikariBg)) {
        if (searching) {
            ChannelSearchBar(query = state.query, onQueryChange = viewModel::setQuery, onClose = ::closeSearch)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onBack)
                Spacer(Modifier.width(4.dp))
                Text("Deine Kanäle", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                BarButton(Icons.Outlined.Search, "Suchen") {
                    // Gesucht wird über alle Kanäle, nicht nur im gerade gewählten.
                    viewModel.selectChannel(null)
                    searching = true
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            if (!searching) {
                item(key = "stories") {
                    StoryRow(
                        channels = channels,
                        selectedId = state.channelId,
                        onSelect = viewModel::selectChannel,
                    )
                }
            }

            // Ist ein Kanal gewählt, steht sein Name über der Liste — mit dem Weg zur Kanalseite.
            channelById[state.channelId]?.takeIf { !searching }?.let { channel ->
                item(key = "channel-head") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            channel.title,
                            color = HikariText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenChannel(channel.id) }
                                .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Zum Kanal", color = HikariTextMuted, fontSize = 13.sp)
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = HikariTextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            items(state.videos, key = { it.id }) { video ->
                FeedCard(
                    video = video,
                    channel = channelById[video.channel_id],
                    onClick = { onPlay(video) },
                    modifier = Modifier.animateItem(fadeInSpec = tween(240), fadeOutSpec = tween(120)),
                )
            }

            item(key = "footer") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator(color = HikariTextMuted, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        state.videos.isEmpty() -> Text(
                            if (state.query.isNotBlank()) "Nichts gefunden für »${state.query}«."
                            else "Hier gibt es noch keine Videos.",
                            color = HikariTextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp),
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** Kanäle für die Leiste oben: die mit dem frischesten Upload zuerst. */
private fun storyChannels(data: LibraryResponse): List<StoryChannel> {
    val weekAgo = System.currentTimeMillis() - 7L * 86_400_000L
    val newest = data.channelUploads.groupBy { it.channel_id }.mapValues { (_, v) -> v.maxOf { it.published_at } }
    return data.channels
        .filter { it.id != MANUAL_CHANNEL }
        .map { StoryChannel(it, hasNew = (newest[it.id] ?: 0L) >= weekAgo, newestAt = newest[it.id] ?: 0L) }
        .sortedByDescending { it.newestAt }
}

private data class StoryChannel(val channel: ChannelDto, val hasNew: Boolean, val newestAt: Long)

/**
 * Die Kanäle als runde Karten. Ganz links die Startseite (alle Kanäle); ein
 * farbiger Ring heißt: von diesem Kanal gibt es diese Woche etwas Neues.
 */
@Composable
private fun StoryRow(channels: List<StoryChannel>, selectedId: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
    ) {
        item(key = "start") {
            Story(label = "Start", selected = selectedId == null, hasNew = false, onClick = { onSelect(null) }) {
                Box(Modifier.fillMaxSize().background(HikariSurface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Home, contentDescription = null, tint = HikariText, modifier = Modifier.size(26.dp))
                }
            }
        }
        items(channels, key = { it.channel.id }) { story ->
            Story(
                label = story.channel.title,
                selected = selectedId == story.channel.id,
                hasNew = story.hasNew,
                onClick = { onSelect(story.channel.id) },
            ) {
                Box(Modifier.fillMaxSize().background(HikariSurface), contentAlignment = Alignment.Center) {
                    Text(
                        story.channel.title.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                        color = HikariTextMuted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!story.channel.thumbnail_url.isNullOrBlank()) {
                        AsyncImage(
                            model = story.channel.thumbnail_url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Story(
    label: String,
    selected: Boolean,
    hasNew: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val ring: Brush = when {
        selected -> SolidColor(Color.White)
        hasNew -> Brush.linearGradient(listOf(HikariAmber, Color(0xFFF472B6)))
        else -> SolidColor(Color.White.copy(alpha = 0.14f))
    }
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, tween(200), label = "storyScale")
    Column(
        modifier = Modifier.width(78.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(if (selected) 3.dp else 2.dp, ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape),
        ) {
            content()
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = if (selected) Color.White else HikariTextMuted,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Ein Video wie in der YouTube-App: großes Bild, darunter Kanalbild, Titel und eine Zeile mit Kanal, Länge und Alter. */
@Composable
private fun FeedCard(
    video: LibraryVideoDto,
    channel: ChannelDto?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = progressFraction(video)
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 22.dp)) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(HikariSurface),
        ) {
            ResumeArtwork(video = video, modifier = Modifier.fillMaxSize(), resumeFrame = false)
            Text(
                clockLabel(video.duration_seconds),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (fraction > 0f) {
                ProgressLine(fraction, height = 4, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
        Row(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(HikariSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel?.thumbnail_url.isNullOrBlank()) {
                    AsyncImage(
                        model = channel?.thumbnail_url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = HikariText,
                    fontSize = 15.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        (channel?.title ?: video.channelTitle)?.takeIf { it.isNotBlank() },
                        durationLabel(video.duration_seconds),
                        relativeAge(video.published_at).takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    color = HikariTextMuted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** Suchzeile: ersetzt den Kopf, solange gesucht wird. */
@Composable
private fun ChannelSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.AutoMirrored.Outlined.ArrowBack, "Suche schließen", onClose)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(HikariSurface)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Videos und Kanäle suchen", color = HikariTextFaint, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    cursorBrush = SolidColor(HikariAmber),
                    textStyle = TextStyle(color = HikariText, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Eingabe löschen", tint = HikariTextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = HikariText, modifier = Modifier.size(22.dp))
    }
}

/** Je Kanal die neuesten Videos; Kanäle mit dem frischesten Upload zuerst. */
internal fun channelShelves(data: LibraryResponse): List<Pair<ChannelDto, List<LibraryVideoDto>>> {
    val byChannel = data.channelUploads.groupBy { it.channel_id }
    return data.channels
        .filter { it.id != MANUAL_CHANNEL }
        .mapNotNull { channel ->
            val videos = byChannel[channel.id].orEmpty().sortedByDescending { it.published_at }
            if (videos.isEmpty()) null else channel to videos
        }
        .sortedByDescending { (_, videos) -> videos.first().published_at }
}

/**
 * Der Einstieg in den Kanal-Bereich auf der Bibliothek: ein Banner aus den
 * neuesten Vorschaubildern, davor die Kanalbilder und was es Neues gibt.
 */
@Composable
internal fun ChannelsBanner(data: LibraryResponse, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shelves = remember(data.channels, data.channelUploads) { channelShelves(data) }
    if (shelves.isEmpty()) return
    val newest = remember(shelves) { shelves.map { it.second.first() }.take(3) }
    val weekAgo = remember { System.currentTimeMillis() - 7L * 86_400_000L }
    val newThisWeek = remember(shelves) { shelves.sumOf { (_, videos) -> videos.count { it.published_at >= weekAgo } } }
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(148.dp)
            .clip(shape)
            .background(HikariSurface)
            .clickable(onClick = onClick),
    ) {
        // Hintergrund: die neuesten Vorschaubilder nebeneinander — unscharf und
        // nach links abgedunkelt, damit ihre Schrift nicht mit dem Text kämpft.
        Row(modifier = Modifier.fillMaxSize().blur(14.dp)) {
            newest.forEach { video ->
                AsyncImage(
                    model = video.thumbnail_url,
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.94f),
                    0.6f to Color.Black.copy(alpha = 0.80f),
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            ),
        )
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp, end = 72.dp)) {
            // Kanalbilder leicht überlappend.
            Row {
                shelves.take(5).forEachIndexed { index, (channel, _) ->
                    Box(
                        modifier = Modifier
                            .offset(x = (-10 * index).dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(HikariSurface),
                    ) {
                        AsyncImage(
                            model = channel.thumbnail_url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Deine Kanäle", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                text = buildString {
                    append(if (shelves.size == 1) "1 Kanal" else "${shelves.size} Kanäle")
                    append(" · ")
                    append(
                        when (newThisWeek) {
                            0 -> "nichts Neues diese Woche"
                            1 -> "1 neues Video diese Woche"
                            else -> "$newThisWeek neue Videos diese Woche"
                        },
                    )
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Kanäle öffnen", tint = Color.Black, modifier = Modifier.size(26.dp))
        }
    }
}

private const val MANUAL_CHANNEL = "manual"

// ── Oben: womit es weitergeht ────────────────────────────────────────────────

/**
 * Das Bild liegt in voller Breite im Format 16:9 — genau das Format von
 * Vorschaubildern und Standbildern, es wird also nichts abgeschnitten. Titel
 * und der eine Knopf sitzen unten im Bild; darunter beginnt gleich der
 * nächste Bereich.
 */
@Composable
internal fun LibraryHero(
    video: LibraryVideoDto,
    series: SeriesDto?,
    onPlay: () -> Unit,
) {
    val started = (video.progress_seconds ?: 0f) > 0f
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable(onClick = onPlay)) {
        ResumeArtwork(video = video, modifier = Modifier.fillMaxSize(), showTitleFallback = false)
        // Nach unten abdunkeln, damit Titel und Knopf auch über hellen Bildern lesbar sind.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.38f to Color.Transparent,
                    0.72f to Color.Black.copy(alpha = 0.72f),
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = when {
                    series != null -> listOf(series.title, episodeTag(video.season, video.episode))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    video.isFilm() -> "Film"
                    else -> video.channelTitle.orEmpty()
                },
                color = HikariAmber,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(SoftCorner)
                        .background(Color.White)
                        .clickable(onClick = onPlay)
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (started) "Weiterschauen" else "Abspielen",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (started) "noch ${remainingLabel(video)}" else durationLabel(video.duration_seconds),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                )
            }
        }
        if (started) {
            ProgressLine(progressFraction(video), height = 3, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

// ── Weiterschauen ────────────────────────────────────────────────────────────

/**
 * Karte in »Weiterschauen«. [onMenu] öffnet das Menü des Titels (drei Punkte oder langes Drücken) —
 * dort lässt er sich aus der Reihe nehmen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContinueCard(
    video: LibraryVideoDto,
    series: SeriesDto?,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
) {
    val upNext = video.up_next == 1
    Column(modifier = Modifier.width(236.dp).combinedClickable(onClick = onClick, onLongClick = onMenu)) {
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
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = series?.title ?: video.title,
                    color = HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Shelves.continueDetail(
                        episodeTag = if (series != null) episodeTag(video.season, video.episode)
                        else video.channelTitle?.takeIf { it.isNotBlank() }.orEmpty(),
                        remaining = remainingLabel(video),
                        upNext = upNext,
                    ),
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (onMenu != null) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Menü",
                    tint = HikariTextMuted,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMenu)
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
        }
    }
}

/**
 * Cover in einer 16:9-Fläche. Die meisten Cover sind Standbilder im selben
 * Format und füllen sie. Ein hochkantes Plakat würde beim Zuschneiden
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

// ── Bausteine ────────────────────────────────────────────────────────────────

@Composable
internal fun ProgressLine(fraction: Float, height: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height.dp).background(HikariAmber))
    }
}

internal fun progressFraction(video: LibraryVideoDto): Float {
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
