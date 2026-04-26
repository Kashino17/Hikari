package com.hikari.app.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.channels.components.ImportCard
import com.hikari.app.ui.channels.components.SharedDefaultsBlock
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import kotlinx.coroutines.launch

/**
 * Bulk-import bottom sheet for adding videos by URL. Used from
 * `ProfileScreen.ChannelsTab` (Phase A renamed it from the old standalone
 * ChannelsScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    onDismiss: () -> Unit,
    vm: ImportSheetViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariBg,
        contentColor = HikariText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Videos importieren",
                color = HikariText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = state.rawInput,
                onValueChange = vm::onInputChanged,
                placeholder = { Text("URLs hier einfügen (eine pro Zeile)…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 6,
            )

            if (state.cards.isNotEmpty()) {
                SharedDefaultsBlock(
                    defaults = state.defaults,
                    allSeries = state.allSeries,
                    allDubLanguages = state.allDubLanguages,
                    allSubLanguages = state.allSubLanguages,
                    onUpdate = { transform -> vm.updateDefaults(transform) },
                )

                state.cards.forEach { card ->
                    ImportCard(
                        card = card,
                        defaults = state.defaults,
                        allSeries = state.allSeries,
                        allDubLanguages = state.allDubLanguages,
                        allSubLanguages = state.allSubLanguages,
                        onToggleExpand = { vm.toggleExpanded(card.url) },
                        onRemove = { vm.removeCard(card.url) },
                        onRetry = { vm.retryCard(card.url) },
                        onPatchReady = { transform ->
                            vm.updateCard(card.url) { transform(this) }
                        },
                    )
                }
            }

            state.submitError?.let { err ->
                Text(err, color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            val readyCount = state.cards.count { it is ImportCardState.Ready }
            val anyLoading = state.cards.any { it is ImportCardState.Loading }
            Button(
                onClick = {
                    scope.launch {
                        val n = vm.submit()
                        if (n != null) onDismiss()
                    }
                },
                enabled = readyCount > 0 && !anyLoading && !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.submitting -> "Importiere…"
                        anyLoading -> "Analysiere $readyCount von ${state.cards.size}…"
                        readyCount == 0 -> "Keine URLs"
                        else -> "$readyCount Importieren"
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
