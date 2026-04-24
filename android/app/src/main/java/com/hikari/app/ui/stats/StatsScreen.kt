package com.hikari.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: StatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val stats by vm.stats.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Stats") },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                stats != null -> {
                    val s = stats!!
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "Last ${s.windowDays} days",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        // Summary cards row
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatCard("Viewed", s.viewed.toString(), Modifier.weight(1f))
                            StatCard("Approved", s.approved.toString(), Modifier.weight(1f))
                            StatCard("Rejected", s.rejected.toString(), Modifier.weight(1f))
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatCard(
                                "Avg Score",
                                "${"%.1f".format(s.avgScore)}",
                                Modifier.weight(1f),
                            )
                            StatCard("Disk Used", "${s.diskUsedMb} MB", Modifier.weight(1f))
                        }

                        if (s.byCategory.isNotEmpty()) {
                            Text(
                                "By Category",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val maxCount = s.byCategory.values.maxOrNull() ?: 1
                            s.byCategory.entries
                                .sortedByDescending { it.value }
                                .forEach { (cat, count) ->
                                    val fraction = count.toFloat() / maxCount
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            cat,
                                            modifier = Modifier.width(100.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Box(
                                            Modifier
                                                .width((fraction * 180).dp)
                                                .height(8.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    shape = MaterialTheme.shapes.small,
                                                ),
                                        )
                                        Text(
                                            count.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
