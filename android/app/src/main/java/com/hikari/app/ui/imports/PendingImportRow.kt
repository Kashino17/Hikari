package com.hikari.app.ui.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.data.api.dto.PendingImportPatch
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Eine laufende Übertragung, dargestellt im Zeilenformat der Kanalansicht.
 *
 * Steht bewusst zwischen den fertigen Videos derselben Liste: Ein Import wird
 * dort erwartet, wo er später auch landet. Statt des Bewertungs-Abzeichens
 * trägt die Zeile einen Fortschrittsbalken, und an Stelle der Vorschau steht
 * der Anteil in Prozent — solange er bekannt ist.
 */
@Composable
fun PendingImportRow(
    item: PendingImportDto,
    expanded: Boolean,
    saving: Boolean,
    onToggleEdit: () -> Unit,
    onSave: (PendingImportPatch) -> Unit,
    onDismiss: () -> Unit,
) {
    val failed = item.status == "failed"

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // Platzhalter im Format der Video-Vorschaubilder, damit die Zeile
            // nicht aus der Liste ausbricht.
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HikariBorder),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    failed -> Text("FEHLER", color = HikariTextFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    item.progress != null -> Text(
                        "${(item.progress * 100).toInt()} %",
                        color = HikariAmber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    else -> CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = HikariAmber,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    item.displayTitle(),
                    color = if (failed) HikariTextMuted else HikariText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))

                if (failed) {
                    Text(
                        item.error ?: "Unbekannter Fehler",
                        color = HikariTextFaint,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    // Kennt der Server den Anteil noch nicht — bei HLS bis zum
                    // ersten Fragment der Fall — läuft ein unbestimmter Balken.
                    // Besser als einer, der minutenlang bei null klebt.
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
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onToggleEdit,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(if (expanded) "Schließen" else "Angaben bearbeiten", fontSize = 12.sp)
                }
            }

            if (failed) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Entfernen", tint = HikariTextFaint, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            PendingEditFields(item, saving, onSave)
        }
    }
}

@Composable
private fun PendingEditFields(
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
                label = { Text("St.", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = episode,
                onValueChange = { episode = it },
                label = { Text("Fo.", fontSize = 12.sp) },
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
        Spacer(Modifier.height(10.dp))
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
