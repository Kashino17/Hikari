package com.hikari.app.ui.news

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Einstellungen des Tagesberichts: Uhrzeit, Themen (Chips + eigene Begriffe),
 * Standort für lokale Nachrichten + Sprache, tägliche Benachrichtigung.
 * „Speichern" schreibt alles in den SettingsStore, plant den Worker um und
 * lädt das Briefing neu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewsSettingsSheet(vm: NewsViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current

    // POST_NOTIFICATIONS ist ab API 33 eine Runtime-Permission — erst anfragen,
    // dann den Schalter einschalten; bei Ablehnung bleibt er aus.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onNewsEnabledChange(granted) }

    // Standort-Toggle: Permission anfragen, dann Stadt + Sprache auflösen.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.setLocationEnabled(true)
            vm.resolveLocation()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HikariCardBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Tagesbericht-Einstellungen",
                color = HikariText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            // Tägliche Benachrichtigung + Uhrzeit
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tägliche Benachrichtigung", color = HikariText, fontSize = 14.sp)
                    Text(
                        "Uhrzeit: ${formatMinutes(vm.newsTimeMinutes)} Uhr",
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = vm.newsEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            vm.onNewsEnabledChange(false)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.onNewsEnabledChange(true)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = HikariPrimary),
                )
            }
            NewsTimePickerButton(
                minutes = vm.newsTimeMinutes,
                onMinutesChange = { vm.setTimeMinutes(it) },
            )

            // Themen
            Text("Themen", color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val chipKeys = (vm.availableTopics.map { it.key } + vm.newsTopicsSelected).distinct()
            if (chipKeys.isEmpty()) {
                Text(
                    "Themenliste vom Server nicht verfügbar — eigene Themen unten hinzufügen.",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chipKeys.forEach { key ->
                    val label = vm.availableTopics.firstOrNull { it.key == key }?.label
                        ?: key.replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = key in vm.newsTopicsSelected,
                        onClick = { vm.toggleTopic(key) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = HikariSurfaceHigh,
                            labelColor = HikariTextMuted,
                            selectedContainerColor = HikariPrimary,
                            selectedLabelColor = Color.Black,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = key in vm.newsTopicsSelected,
                            borderColor = HikariBorder,
                        ),
                    )
                }
            }
            var customTopic by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customTopic,
                    onValueChange = { customTopic = it },
                    label = { Text("Eigenes Thema") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        vm.addCustomTopic(customTopic)
                        customTopic = ""
                    },
                    enabled = customTopic.isNotBlank(),
                ) {
                    Text("Hinzufügen", color = HikariPrimary)
                }
            }

            // Standort
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Standort für lokale Nachrichten + Sprache",
                    color = HikariText,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = vm.newsLocationEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            vm.setLocationEnabled(false)
                        } else if (
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            vm.setLocationEnabled(true)
                            vm.resolveLocation()
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = HikariPrimary),
                )
            }
            if (vm.newsLocationEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.locationResolving) {
                        CircularProgressIndicator(
                            color = HikariPrimary,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        if (vm.newsCity.isBlank()) {
                            "Kein Ort ermittelt — bitte manuell eintragen."
                        } else {
                            "Ermittelter Ort: ${vm.newsCity}"
                        },
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            OutlinedTextField(
                value = vm.newsCity,
                onValueChange = { vm.setManualCity(it) },
                label = { Text("Ort (manuell)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HikariPrimary,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Speichern", fontWeight = FontWeight.Bold)
            }
            Text(
                "Sprache: ${vm.newsLang.uppercase()} (automatisch aus dem Standort, sonst DE)",
                color = HikariTextFaint,
                fontSize = 11.sp,
            )
        }
    }
}

private fun formatMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

/** Öffnet einen TimePicker-Dialog und meldet die Wahl als Minuten seit Mitternacht. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsTimePickerButton(minutes: Int, onMinutesChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text("Uhrzeit ändern", color = HikariPrimary)
    }
    if (!open) return

    val state = rememberTimePickerState(
        initialHour = minutes / 60,
        initialMinute = minutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                onMinutesChange(state.hour * 60 + state.minute)
                open = false
            }) { Text("Übernehmen", color = HikariPrimary) }
        },
        dismissButton = {
            TextButton(onClick = { open = false }) { Text("Abbrechen", color = HikariTextMuted) }
        },
        text = { TimePicker(state = state) },
    )
}
