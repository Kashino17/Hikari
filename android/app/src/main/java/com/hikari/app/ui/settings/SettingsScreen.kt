package com.hikari.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onNavigateToStats: () -> Unit = {},
) {
    val backend by vm.backendUrl.collectAsState()
    val budget by vm.dailyBudget.collectAsState()
    var draftUrl by remember(backend) { mutableStateOf(backend) }
    var draftBudget by remember(budget) { mutableStateOf(budget.toString()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Hikari Backend URL (Tailscale)")
            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.setBackendUrl(draftUrl.trim()) }) { Text("Save URL") }

            Spacer(Modifier.height(24.dp))
            Text("Daily Budget (max reels per day)")
            OutlinedTextField(
                value = draftBudget,
                onValueChange = { draftBudget = it.filter(Char::isDigit) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                draftBudget.toIntOrNull()?.let { vm.setDailyBudget(it) }
            }) { Text("Save Budget") }

            Spacer(Modifier.height(24.dp))
            Text("Changes to the backend URL require an app restart.",
                style = MaterialTheme.typography.labelSmall)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateToStats, modifier = Modifier.fillMaxWidth()) {
                Text("Weekly Stats")
            }
        }
    }
}
