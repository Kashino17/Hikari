package com.hikari.app.ui.channels

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.theme.HikariAmber
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

@Composable
fun ChannelsScreen(vm: ChannelsViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val pollStatus by vm.pollStatus.collectAsState()
    var newUrl by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Header(
                    url = newUrl,
                    onUrlChange = { newUrl = it },
                    busy = busy,
                    onAdd = {
                        if (newUrl.isNotBlank()) {
                            vm.add(newUrl.trim())
                            newUrl = ""
                        }
                    },
                    error = error,
                    pollStatus = pollStatus,
                )
            }
            item {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "ABONNIERT · ${channels.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HikariTextFaint,
                    )
                }
            }
            if (channels.isEmpty()) {
                item {
                    Text(
                        "Noch keine Kanäle. URL oben einfügen.",
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
                    ChannelRow(
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

@Composable
private fun Header(
    url: String,
    onUrlChange: (String) -> Unit,
    busy: Boolean,
    onAdd: () -> Unit,
    error: String?,
    pollStatus: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Kanäle",
            style = MaterialTheme.typography.titleMedium,
            color = HikariText,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(HikariSurface, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(HikariAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    decorationBox = { inner ->
                        if (url.isEmpty()) {
                            Text(
                                "https://www.youtube.com/@…",
                                color = HikariTextFaint,
                                style = TextStyle(fontSize = 13.sp),
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .background(
                        if (busy || url.isBlank()) HikariSurface else HikariAmber,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable(enabled = !busy && url.isNotBlank()) { onAdd() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Hinzufügen",
                    color = if (busy || url.isBlank()) HikariTextFaint else androidx.compose.ui.graphics.Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
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
private fun ChannelRow(
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
