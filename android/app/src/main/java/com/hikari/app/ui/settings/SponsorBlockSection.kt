package com.hikari.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorBlockSection(
    categories: List<SegmentCategory>,
    behaviors: Map<String, SegmentBehavior>,
    totalSkippedCount: Long,
    totalSkippedMs: Long,
    onBehaviorChange: (apiKey: String, SegmentBehavior) -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("SponsorBlock", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // Stats card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Statistik",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$totalSkippedCount Segmente übersprungen",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Zeit gespart: ${formatHms(totalSkippedMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onResetStats) { Text("Statistik zurücksetzen") }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Kategorien", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))

        categories.forEachIndexed { i, cat ->
            CategoryRow(
                cat = cat,
                current = behaviors[cat.apiKey] ?: cat.defaultBehavior,
                onChange = { onBehaviorChange(cat.apiKey, it) },
            )
            if (i < categories.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    cat: SegmentCategory,
    current: SegmentBehavior,
    onChange: (SegmentBehavior) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(cat.color, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cat.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    cat.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val options = listOf(
            SegmentBehavior.SKIP_AUTO to "Auto",
            SegmentBehavior.SKIP_MANUAL to "Manuell",
            SegmentBehavior.IGNORE to "Aus",
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { idx, (value, label) ->
                SegmentedButton(
                    selected = current == value,
                    onClick = { onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                ) { Text(label) }
            }
        }
    }
}

private fun formatHms(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (hours > 0 || minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}
