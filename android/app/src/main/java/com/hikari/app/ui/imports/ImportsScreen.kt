package com.hikari.app.ui.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.data.api.dto.PendingImportPatch
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * "Manuell hinzugefügt" — die Ansicht, die aus einem Import etwas Sichtbares
 * macht.
 *
 * Vorher war ein Import eine Blackbox: absenden, warten, hoffen. Hier steht,
 * was gerade lädt, wie weit es ist und wie lange es noch dauert — und die
 * Angaben lassen sich schon eintragen, während geladen wird.
 */
@Composable
fun ImportsScreen(
    onClose: () -> Unit,
    onOpenSeries: (String) -> Unit,
    vm: ImportsViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Zurück", tint = HikariTextMuted)
            }
            Text(
                "Manuell hinzugefügt",
                color = HikariText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::refresh) { Text("Aktualisieren", fontSize = 13.sp) }
        }

        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)

        LazyColumn(Modifier.fillMaxSize()) {
            if (ui.active.isNotEmpty()) {
                item {
                    SectionHeader("Lädt gerade", "${ui.active.size}")
                }
                items(ui.active, key = { it.id }) { item ->
                    PendingCard(
                        item = item,
                        expanded = ui.editingId == item.id,
                        saving = ui.savingId == item.id,
                        onToggleEdit = { vm.toggleEdit(item.id) },
                        onSave = { patch -> vm.save(item.id, patch) },
                    )
                    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                }
            }

            if (ui.failed.isNotEmpty()) {
                item { SectionHeader("Fehlgeschlagen", "${ui.failed.size}") }
                items(ui.failed, key = { it.id }) { item ->
                    FailedRow(item, onDismiss = { vm.dismissFailed(item.id) })
                    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                }
            }

            if (ui.series.isNotEmpty()) {
                item { SectionHeader("Fertig", "${ui.series.size}") }
                items(ui.series, key = { it.id }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSeries(s.id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.title, color = HikariText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
                }
            }

            if (!ui.loading && ui.pending.isEmpty() && ui.series.isEmpty()) {
                item {
                    Text(
                        "Noch nichts hinzugefügt.",
                        color = HikariTextFaint,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(count, color = HikariTextFaint, fontSize = 12.sp)
    }
}

/**
 * Eine laufende Übertragung.
 *
 * Der Balken beantwortet die erste Frage ("läuft überhaupt was?"), die Zeile
 * darunter die zweite ("wie lange noch?"). Antippen klappt die Felder auf.
 */
@Composable
private fun PendingCard(
    item: PendingImportDto,
    expanded: Boolean,
    saving: Boolean,
    onToggleEdit: () -> Unit,
    onSave: (PendingImportPatch) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayTitle(),
                    color = HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.subtitle(),
                    color = HikariTextFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onToggleEdit) {
                Text(if (expanded) "Schließen" else "Bearbeiten", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Kennt der Server den Anteil noch nicht (HLS meldet die Gesamtgröße
        // erst nach dem ersten Fragment), läuft ein unbestimmter Balken —
        // besser als ein Balken, der bei null klebt.
        val progress = item.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = HikariAmber,
                trackColor = HikariBorder,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = HikariAmber,
                trackColor = HikariBorder,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(item.progressLine(), color = HikariTextMuted, fontSize = 11.sp)

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            EditFields(item, saving, onSave)
        }
    }
}

@Composable
private fun EditFields(
    item: PendingImportDto,
    saving: Boolean,
    onSave: (PendingImportPatch) -> Unit,
) {
    var title by remember(item.id) { mutableStateOf(item.title.orEmpty()) }
    var seriesTitle by remember(item.id) { mutableStateOf(item.seriesTitle.orEmpty()) }
    var season by remember(item.id) { mutableStateOf(item.season?.toString().orEmpty()) }
    var episode by remember(item.id) { mutableStateOf(item.episode?.toString().orEmpty()) }
    var dub by remember(item.id) { mutableStateOf(item.dubLanguage.orEmpty()) }
    var sub by remember(item.id) { mutableStateOf(item.subLanguage.orEmpty()) }

    Column {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titel", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = seriesTitle,
                onValueChange = { seriesTitle = it },
                label = { Text("Serie", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = season,
                onValueChange = { season = it },
                label = { Text("Staffel", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = episode,
                onValueChange = { episode = it },
                label = { Text("Folge", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dub,
                onValueChange = { dub = it },
                label = { Text("Ton", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = sub,
                onValueChange = { sub = it },
                label = { Text("Untertitel", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onSave(
                    PendingImportPatch(
                        title = title.ifBlank { null },
                        seriesTitle = seriesTitle.ifBlank { null },
                        season = season.toIntOrNull(),
                        episode = episode.toIntOrNull(),
                        dubLanguage = dub.ifBlank { null },
                        subLanguage = sub.ifBlank { null },
                    ),
                )
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = HikariBg)
            } else {
                Text("Speichern", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Wird beim Abschluss übernommen — der Download läuft weiter.",
            color = HikariTextFaint,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FailedRow(item: PendingImportDto, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.displayTitle(),
                color = HikariTextMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.error ?: "Unbekannter Fehler",
                color = HikariTextFaint,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Entfernen", tint = HikariTextFaint, modifier = Modifier.size(18.dp))
        }
    }
}

// ---- Aufbereitung für die Anzeige ---------------------------------------

internal fun PendingImportDto.displayTitle(): String {
    val ep = episode?.let { "Folge $it" }
    return when {
        !title.isNullOrBlank() -> title
        !seriesTitle.isNullOrBlank() && ep != null -> "$seriesTitle — $ep"
        !seriesTitle.isNullOrBlank() -> seriesTitle
        ep != null -> ep
        else -> pageUrl
    }
}

internal fun PendingImportDto.subtitle(): String {
    val parts = buildList {
        seriesTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
        season?.let { add("Staffel $it") }
        dubLanguage?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    }
    return if (parts.isEmpty()) pageUrl else parts.joinToString(" · ")
}

/**
 * Die Zeile unter dem Balken: Anteil, geladene Menge, Tempo und Restzeit —
 * so viel davon, wie gerade bekannt ist.
 */
internal fun PendingImportDto.progressLine(): String {
    if (status == "queued") return "Wartet auf Start"
    val parts = buildList {
        progress?.let { add("${(it * 100).toInt()} %") }
        add(formatBytes(downloadedBytes) + (totalBytes?.let { " von ${formatBytes(it)}" } ?: ""))
        fragmentCount?.let { total ->
            fragmentIndex?.let { idx -> add("Teil $idx/$total") }
        }
        speedBps?.takeIf { it > 0 }?.let { add("${formatBytes(it)}/s") }
        etaSeconds?.takeIf { it > 0 }?.let { add("noch ${formatDuration(it)}") }
    }
    return parts.joinToString(" · ")
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun formatDuration(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    seconds >= 60 -> "${seconds / 60} min"
    else -> "$seconds s"
}
