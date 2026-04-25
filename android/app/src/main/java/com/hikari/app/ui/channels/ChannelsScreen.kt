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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    if (bytes < 1024L * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
    return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun formatSubs(n: Long?): String? {
    if (n == null) return null
    return when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "${n / 1000}K"
        else -> n.toString()
    }
}

@Composable
fun ChannelsScreen(vm: ChannelsViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val error by vm.error.collectAsState()
    val pollStatus by vm.pollStatus.collectAsState()
    val query by vm.query.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()

    val isSearching = query.trim().length >= 2

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    items(channels, key = { it.first.id }) { (c, stats) ->
                        SubscribedRow(
                            title = c.title,
                            url = c.url,
                            statsLine = stats?.let {
                                "${it.approved} ok · ${it.rejected} abg · ${formatBytes(it.diskBytes)}"
                            },
                            onPoll = { vm.poll(c.id) },
                            onRemove = { vm.remove(c.id) },
                        )
                        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    error: String?,
    pollStatus: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Kanäle", style = MaterialTheme.typography.titleMedium, color = HikariText)
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(HikariSurface, RoundedCornerShape(8.dp))
                .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
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
                                Text(
                                    "Kanal suchen…",
                                    color = HikariTextFaint,
                                    style = TextStyle(fontSize = 13.sp),
                                )
                            }
                            inner()
                        },
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onClear),
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
            Text(it, color = HikariTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = HikariTextFaint,
        )
    }
}

@Composable
private fun SearchResultRow(r: ChannelSearchResultDto, onFollow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = r.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(HikariSurface),
        )
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
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            if (subscribed) "Abonniert" else "Folgen",
            color = fg,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        )
    }
}

@Composable
private fun SubscribedRow(
    title: String,
    url: String,
    statsLine: String?,
    onPoll: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = HikariText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                url,
                color = HikariTextFaint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (statsLine != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    statsLine,
                    color = HikariTextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onPoll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Aktualisieren",
                    tint = HikariTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Entfernen",
                    tint = HikariTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
