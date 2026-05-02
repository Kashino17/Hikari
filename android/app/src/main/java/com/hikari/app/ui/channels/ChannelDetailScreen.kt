package com.hikari.app.ui.channels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private fun fmtDur(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun ChannelDetailScreen(
    onBack: () -> Unit,
    onEditVideo: (String) -> Unit = {},
    vm: ChannelDetailViewModel = hiltViewModel(),
) {
    val channel by vm.channel.collectAsState()
    val videos by vm.videos.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()

    var deleteTarget by remember { mutableStateOf<ChannelVideoDto?>(null) }
    var expandedVideo by remember { mutableStateOf<String?>(null) }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = HikariBg,
            title = { Text("Video deinstallieren?", color = HikariText) },
            text = {
                Text(
                    "„${target.title}“ wird unwiderruflich entfernt — DB-Eintrag und Datei.",
                    color = HikariTextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVideo(target.videoId)
                    deleteTarget = null
                }) { Text("Deinstallieren", color = HikariDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
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
                DetailHeader(
                    channelTitle = channel?.title,
                    handle = channel?.handle,
                    autoApprove = channel?.autoApprove == true,
                    onBack = onBack,
                    onToggleAutoApprove = { vm.toggleAutoApprove() },
                    onSync = { vm.syncAndClip() },
                    syncing = syncing,
                )
            }

            item {
                val approved = videos.count { it.decision == "approved" }
                val rejected = videos.count { it.decision == "rejected" }
                val processing = videos.count { it.decision == null }
                val parts = buildList {
                    add("${videos.size} VIDEOS")
                    if (approved > 0) add("$approved OK")
                    if (rejected > 0) add("$rejected ABG")
                    if (processing > 0) add("$processing IN ARBEIT")
                }
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
                        ),
                        color = HikariTextFaint,
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            items(videos, key = { it.videoId }) { video ->
                VideoRow(
                    video = video,
                    expanded = expandedVideo == video.videoId,
                    onToggle = {
                        expandedVideo = if (expandedVideo == video.videoId) null else video.videoId
                    },
                    onLongPress = { onEditVideo(video.videoId) },
                    onDelete = { deleteTarget = video },
                )
                HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
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
private fun DetailHeader(
    channelTitle: String?,
    handle: String?,
    autoApprove: Boolean,
    onBack: () -> Unit,
    onToggleAutoApprove: () -> Unit,
    onSync: () -> Unit,
    syncing: Boolean,
) {
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
                    contentDescription = "Sync & Clip",
                    tint = HikariText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onToggleAutoApprove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (autoApprove) Icons.Default.Star
                else Icons.Outlined.StarBorder,
                contentDescription = if (autoApprove)
                    "Vertrauenskanal aktiv — alle Videos werden ohne KI-Bewertung übernommen"
                else
                    "Vertrauenskanal aktivieren",
                tint = if (autoApprove) HikariAmber else HikariTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(
    video: ChannelVideoDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 16:9 thumbnail, 120dp wide
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HikariSurfaceHigh),
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Duration pill, bottom-right
                Text(
                    fmtDur(video.durationSeconds),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = HikariText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecisionBadge(decision = video.decision, score = video.score)
                    video.category?.let {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            it.uppercase(),
                            color = HikariTextFaint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.size(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(HikariSurfaceHigh.copy(alpha = 0.5f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Deinstallieren",
                    tint = HikariTextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Expanded reasoning section
        if (expanded && !video.reasoning.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HikariSurface, RoundedCornerShape(6.dp))
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp),
            ) {
                Text(
                    video.reasoning,
                    color = HikariTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DecisionBadge(decision: String?, score: Int?) {
    val (label, fg, bg, border) = when (decision) {
        "approved" -> Quad(
            "OK ${score ?: ""}".trim(),
            HikariAmber,
            HikariAmberSoft,
            HikariAmber.copy(alpha = 0.3f),
        )
        "rejected" -> Quad(
            "ABG ${score ?: ""}".trim(),
            HikariDanger,
            HikariDanger.copy(alpha = 0.12f),
            HikariDanger.copy(alpha = 0.3f),
        )
        else -> Quad("…", HikariTextMuted, HikariSurfaceHigh, HikariBorder)
    }
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(0.5.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        when (decision) {
            "approved" -> Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(10.dp))
            "rejected" -> Icon(Icons.Default.Close, null, tint = fg, modifier = Modifier.size(10.dp))
            else -> {}
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
    }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: Color)
