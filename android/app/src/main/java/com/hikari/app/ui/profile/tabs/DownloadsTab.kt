package com.hikari.app.ui.profile.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.ui.profile.DownloadsUiState
import com.hikari.app.ui.profile.DownloadsViewModel
import com.hikari.app.ui.profile.MangaSummary
import com.hikari.app.ui.profile.components.CategoryCard
import com.hikari.app.ui.profile.components.CategoryStyle
import com.hikari.app.ui.profile.formatBytes
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

enum class DownloadCategory { SERIES, CHANNELS, MOVIES, MANGAS }

@Composable
fun DownloadsTab(
    onOpenCategory: (DownloadCategory) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val smart by vm.smartDownloads.collectAsState()
    val localSummary by vm.localSummary.collectAsState()
    val mangaSummary by vm.mangaSummary.collectAsState()

    // Reload when the user returns to this tab (e.g. after deleting items in
    // DownloadCategoryScreen). Without this, storage-strip + counts go stale.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    when (val s = state) {
        is DownloadsUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = HikariAmber) }

        is DownloadsUiState.Error -> Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) { Text(s.message, color = HikariTextMuted, fontSize = 13.sp) }

        is DownloadsUiState.Success -> DownloadsContent(
            data = s.data,
            smartEnabled = smart,
            localCount = localSummary.count,
            localTotalBytes = localSummary.totalBytes,
            mangaSummary = mangaSummary,
            onSmartChange = vm::setSmartDownloads,
            onOpenCategory = onOpenCategory,
        )
    }
}

@Composable
private fun DownloadsContent(
    data: DownloadsResponse,
    smartEnabled: Boolean,
    localCount: Int,
    localTotalBytes: Long,
    mangaSummary: MangaSummary,
    onSmartChange: (Boolean) -> Unit,
    onOpenCategory: (DownloadCategory) -> Unit,
) {
    val totalCount = data.series.sumOf { it.episode_count } +
        data.channels.sumOf { it.video_count } +
        data.movies.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            StorageStrip(
                totalBytes = data.total_bytes,
                limitBytes = data.limit_bytes,
                count = totalCount,
                localCount = localCount,
                localBytes = localTotalBytes,
            )
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (data.series.isNotEmpty()) {
                    val totalEps = data.series.sumOf { it.episode_count }
                    val totalSize = data.series.sumOf { it.total_bytes }
                    CategoryCard(
                        title = "Serien",
                        meta = "${data.series.size} Serien · $totalEps Folgen · ${formatBytes(totalSize)}",
                        style = CategoryStyle.POSTERS,
                        coverUrls = data.series.map { it.thumbnail_url },
                        seedFallbacks = data.series.map { it.id },
                        glowColor = HikariAmber,
                        onClick = { onOpenCategory(DownloadCategory.SERIES) },
                    )
                }
                if (data.channels.isNotEmpty()) {
                    val totalVids = data.channels.sumOf { it.video_count }
                    val totalSize = data.channels.sumOf { it.total_bytes }
                    CategoryCard(
                        title = "Kanäle",
                        meta = "${data.channels.size} Kanäle · $totalVids Videos · ${formatBytes(totalSize)}",
                        style = CategoryStyle.AVATARS,
                        coverUrls = data.channels.map { it.thumbnail_url },
                        seedFallbacks = data.channels.map { it.title },
                        glowColor = Color(0xFF3b82f6),
                        onClick = { onOpenCategory(DownloadCategory.CHANNELS) },
                    )
                }
                if (data.movies.isNotEmpty()) {
                    val totalSize = data.movies.sumOf { it.size_bytes }
                    CategoryCard(
                        title = "Filme",
                        meta = "${data.movies.size} Filme · ${formatBytes(totalSize)}",
                        style = CategoryStyle.POSTERS,
                        coverUrls = data.movies.map { it.thumbnail_url },
                        seedFallbacks = data.movies.map { it.id },
                        glowColor = Color(0xFFf472b6),
                        onClick = { onOpenCategory(DownloadCategory.MOVIES) },
                    )
                }
                if (mangaSummary.arcCount > 0) {
                    CategoryCard(
                        title = "Mangas",
                        meta = "${mangaSummary.arcCount} Arcs · " +
                            "${mangaSummary.totalPages} Seiten · " +
                            formatBytes(mangaSummary.totalBytes),
                        style = CategoryStyle.POSTERS,
                        // Cover-Path noch nicht im Manifest — seed-basierte
                        // Platzhalter sorgen für stabile Optik pro Arc.
                        coverUrls = mangaSummary.arcIds.map { null },
                        seedFallbacks = mangaSummary.arcIds,
                        glowColor = Color(0xFF22d3ee),
                        onClick = { onOpenCategory(DownloadCategory.MANGAS) },
                    )
                }
                if (data.series.isEmpty() && data.channels.isEmpty() &&
                    data.movies.isEmpty() && mangaSummary.arcCount == 0
                ) {
                    EmptyState()
                }
            }
        }
        item {
            SmartDownloadsCard(
                enabled = smartEnabled,
                onToggle = onSmartChange,
            )
        }
    }
}

@Composable
private fun StorageStrip(
    totalBytes: Long,
    limitBytes: Long,
    count: Int,
    localCount: Int,
    localBytes: Long,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatBytes(totalBytes)} / ${formatBytes(limitBytes)}",
                color = HikariText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$count DOWNLOADS",
                color = HikariTextFaint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HikariSurfaceHigh),
            ) {
                val frac = if (limitBytes > 0) (totalBytes.toFloat() / limitBytes).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(HikariAmber, Color(0xFFB45309))),
                        ),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "OFFLINE",
                color = HikariAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (localCount > 0) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(2.dp))
                Text(
                    "$localCount lokal · ${formatBytes(localBytes)}",
                    color = HikariAmber,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

@Composable
private fun SmartDownloadsCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariAmber.copy(alpha = 0.06f))
            .border(0.5.dp, HikariAmber.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(HikariAmber.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = HikariAmber,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (enabled) "Smart Downloads aktiv" else "Smart Downloads aus",
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Lädt automatisch Folgen abonnierter Serien · nur über WLAN",
                color = HikariTextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HikariBg,
                checkedTrackColor = HikariAmber,
                checkedBorderColor = HikariAmber,
                uncheckedThumbColor = HikariTextMuted,
                uncheckedTrackColor = HikariSurface,
                uncheckedBorderColor = HikariBorder,
            ),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Noch keine Downloads",
            color = HikariText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Importiere Videos oder folge Kanälen, dann tauchen sie hier auf.",
            color = HikariTextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}
