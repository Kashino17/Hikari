package com.hikari.app.ui.channels

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.data.api.dto.RecommendationDto
import com.hikari.app.domain.model.Channel
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

// ─── Formatting helpers ─────────────────────────────────────────────────────
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun formatSubs(n: Long?): String? {
    if (n == null) return null
    return when {
        n >= 1_000_000 -> {
            val m = n / 1_000_000.0
            if (m >= 10.0) "${m.toLong()}M" else "%.1fM".format(m)
        }
        n >= 1_000 -> "${n / 1000}K"
        else -> n.toString()
    }
}

private fun formatRelativeTime(epochMs: Long?): String? {
    if (epochMs == null) return null
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000 -> "gerade eben"
        diff < 3_600_000 -> "vor ${diff / 60_000}min"
        diff < 86_400_000 -> "vor ${diff / 3_600_000}h"
        diff < 30 * 86_400_000L -> "vor ${diff / 86_400_000}d"
        else -> "vor ${diff / (30 * 86_400_000L)}mo"
    }
}

/** Deterministic colour for the initials avatar — same channel always picks the same hue. */
private fun avatarColor(seed: String): Color {
    val hue = (seed.hashCode().toULong() % 360u).toFloat()
    return Color.hsv(hue, 0.55f, 0.45f)
}

// ─── Main screen ────────────────────────────────────────────────────────────
@Composable
fun ChannelsScreen(
    onOpenChannel: (String) -> Unit = {},
    vm: ChannelsViewModel = hiltViewModel(),
) {
    val channels by vm.channels.collectAsState()
    val error by vm.error.collectAsState()
    val pollStatus by vm.pollStatus.collectAsState()
    val query by vm.query.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val recommendations by vm.recommendations.collectAsState()
    val loadingRecs by vm.loadingRecs.collectAsState()
    val ctx = LocalContext.current

    var deepScanTarget by remember { mutableStateOf<Channel?>(null) }
    var importSheetOpen by remember { mutableStateOf(false) }

    val isSearching = query.trim().length >= 2

    if (importSheetOpen) {
        ImportSheet(
            onDismiss = { importSheetOpen = false },
            onImport = { urls, scrapeLinks ->
                vm.importVideos(urls, scrapeLinks) { _: Int -> }
                importSheetOpen = false
            },
        )
    }

    if (deepScanTarget != null) {
        AlertDialog(
            onDismissRequest = { deepScanTarget = null },
            containerColor = HikariBg,
            title = { Text("Tieferes Scannen?", color = HikariText) },
            text = {
                Text(
                    "Lädt bis zu 100 ältere Videos statt nur den 15 neuesten. " +
                        "Kann ein paar Minuten dauern und kostet LM-Studio-Zeit pro Video.",
                    color = HikariTextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deepScanTarget?.let { vm.deepScan(it.id) }
                    deepScanTarget = null
                }) { Text("Tiefer scannen", color = HikariAmber) }
            },
            dismissButton = {
                TextButton(onClick = { deepScanTarget = null }) {
                    Text("Abbrechen", color = HikariTextMuted)
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Header(
                    query = query,
                    onQueryChange = { vm.setQuery(it) },
                    onClear = { vm.clearQuery() },
                    onOpenImport = { importSheetOpen = true },
                    error = error,
                    pollStatus = pollStatus,
                )
            }

            if (isSearching) {
                item {
                    SectionLabel(
                        when {
                            searching -> "SUCHE…"
                            searchResults.isEmpty() -> "NICHTS GEFUNDEN"
                            else -> "${searchResults.size} TREFFER"
                        },
                    )
                }
                items(searchResults, key = { it.channelId }) { r ->
                    SearchResultRow(r, onFollow = { vm.follow(r) })
                    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                }
            } else {
                item { SectionLabel("ABONNIERT · ${channels.size}") }
                if (channels.isEmpty()) {
                    item {
                        Text(
                            "Suche oben nach einem Kanal-Namen.",
                            color = HikariTextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    items(channels, key = { it.first.id }) { (channel, stats) ->
                        SubscribedRow(
                            channel = channel,
                            stats = stats,
                            onClick = { onOpenChannel(channel.id) },
                            onPoll = { vm.poll(channel.id) },
                            onDeepScan = { deepScanTarget = channel },
                            onRemove = { vm.remove(channel.id) },
                        )
                        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                    }
                }

                // ── Empfohlen-Sektion (nur wenn Tuning-Tags Empfehlungen liefern) ──
                if (recommendations.isNotEmpty() || loadingRecs) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "EMPFOHLEN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = HikariTextFaint,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "↻",
                                color = HikariTextMuted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { vm.loadRecommendations(force = true) },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    if (loadingRecs && recommendations.isEmpty()) {
                        item {
                            Text(
                                "Lade Empfehlungen…",
                                color = HikariTextFaint,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    items(recommendations, key = { it.channelId }) { rec ->
                        RecommendationRow(
                            rec = rec,
                            onOpenInBrowser = {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(rec.channelUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            onFollow = { vm.followRecommendation(rec) },
                        )
                        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ─── Header ─────────────────────────────────────────────────────────────────
@Composable
private fun Header(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onOpenImport: () -> Unit,
    error: String?,
    pollStatus: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kanäle",
                style = MaterialTheme.typography.titleMedium,
                color = HikariText,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onOpenImport),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Direkten Video-Link einfügen",
                    tint = HikariAmber,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(HikariSurface, RoundedCornerShape(8.dp))
                .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                        cursorBrush = SolidColor(HikariAmber),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Kanal suchen…", color = HikariTextFaint, style = TextStyle(fontSize = 13.sp))
                            }
                            inner()
                        },
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(24.dp).clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Leeren",
                            tint = HikariTextFaint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        pollStatus?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = HikariAmber, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
            ),
            color = HikariTextFaint,
        )
    }
}

// ─── Avatar (thumbnail or initials fallback) ────────────────────────────────
@Composable
private fun ChannelAvatar(
    title: String,
    thumbnail: String?,
    seed: String,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    if (!thumbnail.isNullOrBlank()) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(HikariSurface),
        )
    } else {
        val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor(seed)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                color = Color.White.copy(alpha = 0.9f),
                style = TextStyle(fontSize = (size.value * 0.42f).sp, fontFamily = FontFamily.Monospace),
            )
        }
    }
}

// ─── Search-result row ──────────────────────────────────────────────────────
@Composable
private fun SearchResultRow(r: ChannelSearchResultDto, onFollow: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(title = r.title, thumbnail = r.thumbnail, seed = r.channelId)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.title,
                    color = HikariText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.verified) {
                    Spacer(Modifier.size(4.dp))
                    Text("✓", color = HikariTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            val sub = formatSubs(r.subscribers)
            val parts = listOfNotNull(r.handle, sub).joinToString(" · ")
            if (parts.isNotEmpty()) {
                Text(
                    parts,
                    color = HikariTextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        FollowPill(subscribed = r.subscribed, onClick = onFollow)
    }
}

@Composable
private fun FollowPill(subscribed: Boolean, onClick: () -> Unit) {
    val bg = if (subscribed) HikariAmberSoft else HikariSurface
    val border = if (subscribed) HikariAmber.copy(alpha = 0.3f) else HikariBorder
    val fg = if (subscribed) HikariAmber else HikariTextMuted
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(0.5.dp, border, RoundedCornerShape(20.dp))
            .clickable(enabled = !subscribed, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (subscribed) {
            Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        }
        Text(
            if (subscribed) "Abonniert" else "Folgen",
            color = fg,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        )
    }
}

// ─── Subscribed row ─────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscribedRow(
    channel: Channel,
    stats: ChannelStatsDto?,
    onClick: () -> Unit,
    onPoll: () -> Unit,
    onDeepScan: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(title = channel.title, thumbnail = channel.thumbnail, seed = channel.id)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.title,
                color = HikariText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Meta line: handle · subs · last polled
            val metaParts = listOfNotNull(
                channel.handle,
                formatSubs(channel.subscribers),
                formatRelativeTime(channel.lastPolledAt)?.let { "akt. $it" },
            )
            if (metaParts.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    metaParts.joinToString(" · "),
                    color = HikariTextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Stats line
            if (stats != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${stats.approved} ok · ${stats.rejected} abg · ${formatBytes(stats.diskBytes)}",
                    color = HikariTextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, letterSpacing = 0.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        IconChip(
            icon = Icons.Default.Refresh,
            contentDescription = "Aktualisieren (lang drücken: Tiefer scannen)",
            modifier = Modifier.combinedClickable(onClick = onPoll, onLongClick = onDeepScan),
        )
        Spacer(Modifier.size(2.dp))
        IconChip(
            icon = Icons.Default.Delete,
            contentDescription = "Entfernen",
            modifier = Modifier.clickable(onClick = onRemove),
        )
    }
}

/** Pill-shaped icon button — softer than a raw IconButton, fits the hairline aesthetic. */
@Composable
private fun IconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(HikariSurfaceHigh.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = HikariTextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ─── Recommendation row (tap row → open in browser, separate Folgen pill) ──
@Composable
private fun RecommendationRow(
    rec: RecommendationDto,
    onOpenInBrowser: () -> Unit,
    onFollow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenInBrowser)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(title = rec.title, thumbnail = rec.thumbnail, seed = rec.channelId)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rec.title,
                    color = HikariText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rec.verified) {
                    Spacer(Modifier.size(4.dp))
                    Text("✓", color = HikariTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            val meta = listOfNotNull(rec.handle, formatSubs(rec.subscribers)).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = HikariTextFaint, style = MaterialTheme.typography.bodySmall)
            }
            if (rec.matchingTags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "passt zu: ${rec.matchingTags.joinToString(", ")}",
                    color = HikariAmber.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        FollowPill(subscribed = rec.subscribed, onClick = onFollow)
    }
}

// ─── Import sheet ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    onDismiss: () -> Unit,
    onImport: (List<String>, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var scrapeLinks by remember { mutableStateOf(false) }

    // Parse input into clean URL list — one per line, comma-separated, trimmed
    val urls = remember(text) {
        text.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://")) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariBg,
        contentColor = HikariText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                if (scrapeLinks) "Sammelseite importieren" else "Direkten Video-Link",
                style = MaterialTheme.typography.titleMedium,
                color = HikariText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (scrapeLinks) {
                    "Eine Seite pro Zeile. Hikari liest die Links auf der Seite aus und importiert die gefundenen Videos."
                } else {
                    "Eine URL pro Zeile (oder Komma-getrennt). yt-dlp probiert die Extraktion. Auto-genehmigt, direkt ins Archiv."
                },
                style = MaterialTheme.typography.bodySmall,
                color = HikariTextMuted,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HikariSurface, RoundedCornerShape(8.dp))
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                    .clickable { scrapeLinks = !scrapeLinks }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sammelseite auslesen",
                        color = HikariText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Links auf der Seite importieren",
                        color = HikariTextFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = scrapeLinks,
                    onCheckedChange = { scrapeLinks = it },
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(HikariSurface, RoundedCornerShape(8.dp))
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = HikariText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                    ),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier.fillMaxSize(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                if (scrapeLinks) "https://deine-seite.example/filme" else "https://…\nhttps://…",
                                color = HikariTextFaint,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp,
                                ),
                            )
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (scrapeLinks) {
                        "${urls.size} Seite${if (urls.size == 1) "" else "n"} erkannt"
                    } else {
                        "${urls.size} URL${if (urls.size == 1) "" else "s"} erkannt"
                    },
                    color = HikariTextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen", color = HikariTextMuted)
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .background(
                            if (urls.isEmpty()) HikariSurface else HikariAmber,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(enabled = urls.isNotEmpty()) { onImport(urls, scrapeLinks) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Importieren",
                        color = if (urls.isEmpty()) HikariTextFaint else Color.Black,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
