package com.hikari.app.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: ChannelsViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
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
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(channels, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.title) },
                        supportingContent = {
                            Text(c.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            TextButton(onClick = { vm.remove(c.id) }) { Text("Remove") }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
