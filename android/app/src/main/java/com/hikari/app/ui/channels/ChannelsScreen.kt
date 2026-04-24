package com.hikari.app.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    if (bytes < 1024L * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
    return "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: ChannelsViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val pollStatus by vm.pollStatus.collectAsState()
    var newUrl by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Channels") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                label = { Text("Channel URL") },
                placeholder = { Text("https://www.youtube.com/@...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.add(newUrl.trim()); newUrl = "" },
                enabled = !busy && newUrl.isNotBlank(),
            ) { Text("Add") }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            pollStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(channels, key = { it.first.id }) { (c, stats) ->
                    ListItem(
                        headlineContent = { Text(c.title) },
                        supportingContent = {
                            Column {
                                Text(c.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (stats != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${stats.totalVideos} videos · ${stats.approved} approved · ${stats.rejected} rejected · ${formatBytes(stats.diskBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.poll(c.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Poll Now")
                                }
                                TextButton(onClick = { vm.remove(c.id) }) { Text("Remove") }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
