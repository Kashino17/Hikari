package com.hikari.app.ui.rejected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.RejectedItemDto

private fun formatDuration(secs: Int): String {
    val m = secs / 60
    val s = secs % 60
    return "%d:%02d".format(m, s)
}

private fun scoreColor(score: Int): Color = when {
    score <= 40 -> Color(0xFFD32F2F)
    score <= 60 -> Color(0xFFF57C00)
    else -> Color(0xFF388E3C)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RejectedScreen(vm: RejectedViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rejected (${items.size})") },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No rejected videos yet.", Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(items, key = { it.videoId }) { item ->
                    RejectedCard(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RejectedCard(item: RejectedItemDto) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Thumbnail 16:9
            if (item.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Title
            Text(item.title, style = MaterialTheme.typography.titleSmall)

            Spacer(Modifier.height(4.dp))

            // Channel + duration + category
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val channelLabel = item.channelTitle ?: item.channelId
                Text(
                    channelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatDuration(item.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(item.category, style = MaterialTheme.typography.labelSmall) },
                )
            }

            Spacer(Modifier.height(6.dp))

            // Score + risk chips row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Score: ${item.overallScore}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scoreColor(item.overallScore),
                )
                if (item.clickbaitRisk > 50) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "Clickbait ${item.clickbaitRisk}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF57C00),
                            )
                        },
                    )
                }
                if (item.emotionalManipulation > 50) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                "Manip ${item.emotionalManipulation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD32F2F),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Qwen reasoning
            Text(
                item.reasoning,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 5,
            )
        }
    }
}
