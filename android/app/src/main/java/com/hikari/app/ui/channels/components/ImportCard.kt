package com.hikari.app.ui.channels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.ui.channels.ImportCardState

private val Accent = Color(0xFFFBBF24)
private val FailedRed = Color(0xFFEF4444)

@Composable
fun ImportCard(
    card: ImportCardState,
    allSeries: List<SeriesItemDto>,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onPatchReady: ((ImportCardState.Ready) -> ImportCardState.Ready) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111111))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        when (card) {
            is ImportCardState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1A1A1A)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Analysiere…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        Text(
                            card.url,
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            is ImportCardState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Analyze fehlgeschlagen", color = FailedRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(card.error, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 2)
                        Text(card.url, color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Accent)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Entfernen", tint = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
            is ImportCardState.Ready -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onToggleExpand),
                ) {
                    if (card.thumbnailUrl != null) {
                        AsyncImage(
                            model = card.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 40.dp, height = 60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1A1A)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1A1A)),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.title.ifBlank { "(kein Titel)" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            card.url,
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                        if (card.episode != null) {
                            Text(
                                "S${card.season ?: '-'} · E${card.episode}",
                                color = Accent,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (card.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (card.expanded) "Zuklappen" else "Aufklappen",
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                }
                AnimatedVisibility(visible = card.expanded) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = card.title,
                            onValueChange = { v -> onPatchReady { it -> it.copy(title = v) } },
                            label = { Text("Titel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Film", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = card.isMovie,
                                onCheckedChange = { v -> onPatchReady { it -> it.copy(isMovie = v) } },
                            )
                        }
                        AnimatedVisibility(visible = !card.isMovie) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeriesTypeahead(
                                    value = card.seriesTitle.orEmpty(),
                                    allSeries = allSeries,
                                    onChange = { _, sid, stitle ->
                                        onPatchReady { it -> it.copy(seriesId = sid, seriesTitle = stitle) }
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = card.season?.toString().orEmpty(),
                                        onValueChange = { input ->
                                            val v = input.toIntOrNull()
                                            onPatchReady { it -> it.copy(season = v) }
                                        },
                                        label = { Text("Staffel") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = card.episode?.toString().orEmpty(),
                                        onValueChange = { input ->
                                            val v = input.toIntOrNull()
                                            onPatchReady { it -> it.copy(episode = v) }
                                        },
                                        label = { Text("Folge") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = card.dubLanguage.orEmpty(),
                                onValueChange = { input ->
                                    onPatchReady { it -> it.copy(dubLanguage = input.takeIf { v -> v.isNotBlank() }) }
                                },
                                label = { Text("Sprache") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = card.subLanguage.orEmpty(),
                                onValueChange = { input ->
                                    onPatchReady { it -> it.copy(subLanguage = input.takeIf { v -> v.isNotBlank() }) }
                                },
                                label = { Text("Untertitel") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(onClick = onRemove) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Entfernen",
                                    tint = Color.White.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
