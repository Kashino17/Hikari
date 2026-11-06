package com.hikari.app.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.BulkJobStatusDto
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.ImportResultDto
import com.hikari.app.data.api.dto.MovieEntryDto
import com.hikari.app.data.api.dto.SeriesGroupDto
import com.hikari.app.ui.components.ConfirmDeleteDialog
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.imports.PendingImportRow
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private fun fmtDur(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** „vor 3 Tagen" für Millisekunden-Zeitstempel — wie unter YouTube-Videos. */
private fun relativeAge(ms: Long?, now: Long = System.currentTimeMillis()): String? {
    if (ms == null || ms <= 0L) return null
    val minutes = (now - ms) / 60_000
    return when {
        minutes < 1 -> "gerade eben"
        minutes < 60 -> "vor $minutes Min."
        minutes < 60 * 24 -> "vor ${minutes / 60} Std."
        minutes < 60 * 24 * 2 -> "gestern"
        minutes < 60 * 24 * 30 -> "vor ${minutes / (60 * 24)} Tagen"
        minutes < 60 * 24 * 365 -> (minutes / (60 * 24 * 30)).let { if (it == 1L) "vor 1 Monat" else "vor $it Monaten" }
        else -> (minutes / (60 * 24 * 365)).let { if (it == 1L) "vor 1 Jahr" else "vor $it Jahren" }
    }
}

private fun fmtBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "${bytes / 1_000_000} MB"
    else -> "${bytes / 1_000} KB"
}

@Composable
fun ChannelDetailScreen(
    onBack: () -> Unit,
    onEditVideo: (String) -> Unit = {},
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit = { _, _, _ -> },
    onOpenSeries: (String) -> Unit = {},
    onOpenMovie: (String) -> Unit = {},
    vm: ChannelDetailViewModel = hiltViewModel(),
) {
    val channel by vm.channel.collectAsState()
    val videos by vm.videos.collectAsState()
    val manual by vm.manual.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val removed by vm.removed.collectAsState()
    val pending by vm.pending.collectAsState()
    val editingImport by vm.editingImport.collectAsState()
    val savingImport by vm.savingImport.collectAsState()
    val retryingImport by vm.retryingImport.collectAsState()
    val bulkJob by vm.bulkJob.collectAsState()
    val bulkJobDismissed by vm.bulkJobDismissed.collectAsState()

    // When the channel is unsubscribed, leave the now-empty detail screen.
    LaunchedEffect(removed) { if (removed) onBack() }

    // Zurück aus Serie/Film-Detail (dort kann gelöscht werden) → neu laden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else vm.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var deleteTarget by remember { mutableStateOf<ChannelVideoDto?>(null) }
    var showUnsubscribe by remember { mutableStateOf(false) }
    var bulkExpanded by remember { mutableStateOf(false) }
    val channelTitle = channel?.title.orEmpty()

    if (showUnsubscribe) {
        AlertDialog(
            onDismissRequest = { showUnsubscribe = false },
            containerColor = HikariBg,
            title = { Text("Kanal deabonnieren?", color = HikariText) },
            text = {
                Text(
                    "Du bekommst dann keine neuen Videos mehr von „${channel?.title ?: "diesem Kanal"}“. " +
                        "Bereits gespeicherte Videos bleiben erhalten.",
                    color = HikariTextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.unsubscribe()
                    showUnsubscribe = false
                }) { Text("Deabonnieren", color = HikariDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubscribe = false }) {
                    Text("Abbrechen", color = HikariTextMuted)
                }
            },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            title = "Video vom PC löschen?",
            message = "„${target.title}“ wird endgültig gelöscht — vom PC und aus der App. " +
                "Das lässt sich nicht rückgängig machen.",
            confirmLabel = "Endgültig löschen",
            onConfirm = { vm.deleteVideo(target.videoId) },
            onDismiss = { deleteTarget = null },
        )
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                DetailHeader(
                    channelTitle = channel?.title,
                    handle = channel?.handle,
                    onBack = onBack,
                    onSync = { vm.sync() },
                    syncing = syncing,
                    onUnsubscribe = { showUnsubscribe = true },
                )
            }

            item {
                val loadingCount = pending.count { it.status != "failed" }
                val m = manual
                val parts = buildList {
                    if (m != null) {
                        if (m.series.isNotEmpty()) add("${m.series.size} SERIEN")
                        if (m.movies.isNotEmpty()) add("${m.movies.size} ${if (m.movies.size == 1) "FILM" else "FILME"}")
                    }
                    add("${videos.size} VIDEOS")
                    if (loadingCount > 0) add("$loadingCount LÄDT")
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
                    ),
                    color = HikariTextFaint,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            // Ergebnis-Karte des letzten Bulk-Imports (Sheet/Browser).
            bulkJob?.takeIf { !bulkJobDismissed }?.let { job ->
                item {
                    BulkJobCard(
                        job = job,
                        expanded = bulkExpanded,
                        onToggle = { bulkExpanded = !bulkExpanded },
                        onDismiss = { vm.dismissBulkJob() },
                    )
                }
            }

            error?.let {
                item {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            if (loading && videos.isEmpty()) {
                item {
                    Text(
                        "Lade Videos…",
                        color = HikariTextFaint,
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(pending, key = { "pending-${it.id}" }) { item ->
                PendingImportRow(
                    item = item,
                    expanded = editingImport == item.id,
                    saving = savingImport == item.id,
                    retrying = retryingImport == item.id,
                    onToggleEdit = { vm.toggleImportEdit(item.id) },
                    onSave = { patch -> vm.saveImport(item.id, patch) },
                    onDismiss = { vm.dismissImport(item.id) },
                    onRetry = { vm.retryImport(item) },
                )
                HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
            }

            val m = manual
            if (m != null) {
                // ── Manuell hinzugefügt: gegliedert wie Bibliothek/Downloads ──
                if (m.series.isNotEmpty()) {
                    item { ShelfTitle("Serien", m.series.size) }
                    items(m.series.chunked(3), key = { row -> "s-" + row.first().id }) { row ->
                        PosterRow {
                            row.forEach { s ->
                                SeriesPoster(
                                    series = s,
                                    onClick = { onOpenSeries(s.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (m.movies.isNotEmpty()) {
                    item { ShelfTitle("Filme", m.movies.size) }
                    items(m.movies.chunked(3), key = { row -> "m-" + row.first().id }) { row ->
                        PosterRow {
                            row.forEach { movie ->
                                MoviePoster(
                                    movie = movie,
                                    onClick = { onOpenMovie(movie.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (m.singles.isNotEmpty()) {
                    item { ShelfTitle("Einzelne Videos", m.singles.size) }
                    items(m.singles, key = { it.videoId }) { video ->
                        YouTubeVideoCard(
                            video = video,
                            channelTitle = null,
                            onPlay = { onPlayVideo(video.videoId, video.title, channelTitle) },
                            onEdit = { onEditVideo(video.videoId) },
                            onDelete = { deleteTarget = video },
                        )
                    }
                }
            } else {
                // ── YouTube-Kanal: großes Bild, Titel darunter, ⋮-Menü ──
                items(videos, key = { it.videoId }) { video ->
                    YouTubeVideoCard(
                        video = video,
                        channelTitle = channelTitle,
                        onPlay = { onPlayVideo(video.videoId, video.title, channelTitle) },
                        onEdit = { onEditVideo(video.videoId) },
                        onDelete = { deleteTarget = video },
                    )
                }
            }
        }

        syncMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(4000)
                vm.dismissSyncMessage()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(msg, color = HikariText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ShelfTitle(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(count.toString(), color = HikariTextFaint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PosterRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun Poster(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(HikariSurfaceHigh),
        ) {
            FallbackArtwork(title = title)
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            title,
            color = HikariText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(subtitle, color = HikariTextMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SeriesPoster(series: SeriesGroupDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Poster(
        title = series.title,
        subtitle = "${series.episode_count} Folgen · ${fmtBytes(series.total_bytes)}",
        thumbnailUrl = series.thumbnail_url,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MoviePoster(movie: MovieEntryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Poster(
        title = movie.title,
        subtitle = "${movie.duration_seconds / 60} Min · ${fmtBytes(movie.size_bytes)}",
        thumbnailUrl = movie.thumbnail_url,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Video wie bei YouTube: großes 16:9-Bild über die volle Breite, darunter Titel
 * und Metazeile, rechts das ⋮-Menü (Bearbeiten, Löschen). Antippen spielt ab.
 */
@Composable
private fun YouTubeVideoCard(
    video: ChannelVideoDto,
    channelTitle: String?,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh),
        ) {
            FallbackArtwork(title = video.title)
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                fmtDur(video.durationSeconds),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = HikariText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    channelTitle?.takeIf { it.isNotBlank() },
                    relativeAge(video.publishedAt ?: video.discoveredAt),
                    video.downloadedBytes?.takeIf { it > 0 }?.let { "auf dem PC · ${fmtBytes(it)}" },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Mehr",
                        tint = HikariTextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(HikariSurface),
                ) {
                    DropdownMenuItem(
                        text = { Text("Bearbeiten", color = HikariText) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null, tint = HikariTextMuted, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Vom PC löschen", color = HikariDanger) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = HikariDanger, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    channelTitle: String?,
    handle: String?,
    onBack: () -> Unit,
    onSync: () -> Unit,
    syncing: Boolean,
    onUnsubscribe: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = HikariText,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channelTitle ?: "—",
                color = HikariText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            handle?.let {
                Text(
                    it,
                    color = HikariTextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(enabled = !syncing, onClick = onSync),
            contentAlignment = Alignment.Center,
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = HikariAmber,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Sync",
                    tint = HikariText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = { menuOpen = true }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Mehr",
                    tint = HikariText,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(HikariSurface),
            ) {
                DropdownMenuItem(
                    text = { Text("Kanal deabonnieren", color = HikariDanger) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = HikariDanger,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onUnsubscribe()
                    },
                )
            }
        }
    }
    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
}

/**
 * Kompakte Zusammenfassung des letzten Bulk-Imports über der Liste.
 * Antippen klappt das Ergebnis pro URL auf; das X blendet die Karte bis zum
 * nächsten Job aus.
 */
@Composable
private fun BulkJobCard(
    job: BulkJobStatusDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finished = job.finishedAt != null
    val done = job.ok + job.duplicate + job.failed
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HikariSurface)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!finished) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = HikariAmber)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                if (finished) {
                    buildList {
                        add("${job.ok} OK")
                        if (job.duplicate > 0) add("${job.duplicate} Duplikate")
                        if (job.failed > 0) add("${job.failed} fehlgeschlagen")
                    }.joinToString(" · ", prefix = "Fertig: ")
                } else {
                    "Import läuft: $done von ${job.total}"
                },
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Ausblenden",
                    tint = HikariTextFaint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            job.results.forEach { BulkResultRow(it) }
        }
    }
}

@Composable
private fun BulkResultRow(result: ImportResultDto) {
    val (icon, tint) = when (result.status) {
        "ok" -> Icons.Default.Check to HikariAmber
        "duplicate" -> Icons.Default.ContentCopy to HikariTextMuted
        "failed" -> Icons.Default.Close to HikariDanger
        else -> Icons.Default.Refresh to HikariTextMuted
    }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = result.status, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(6.dp))
        Column {
            Text(
                result.title ?: result.url,
                color = HikariText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.status == "failed" && result.error != null) {
                Text(
                    result.error,
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
