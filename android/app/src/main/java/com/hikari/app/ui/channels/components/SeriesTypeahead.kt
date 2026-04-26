package com.hikari.app.ui.channels.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hikari.app.data.api.dto.SeriesItemDto

private val Accent = Color(0xFFFBBF24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesTypeahead(
    value: String,
    allSeries: List<SeriesItemDto>,
    onChange: (input: String, seriesId: String?, seriesTitle: String?) -> Unit,
    label: String = "Serie",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, allSeries) {
        if (value.isBlank()) allSeries
        else allSeries.filter { it.title.startsWith(value, ignoreCase = true) }
    }
    val exactMatch = matches.firstOrNull { it.title.equals(value, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                // Free-text typing: clears existing-id binding, sets seriesTitle for new
                onChange(input, null, input.takeIf { it.isNotBlank() })
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            matches.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title) },
                    onClick = {
                        onChange(s.title, s.id, null)
                        expanded = false
                    },
                )
            }
            if (value.isNotBlank() && exactMatch == null) {
                DropdownMenuItem(
                    text = { Text("+ Erstellen: \"$value\"", color = Accent) },
                    onClick = {
                        onChange(value, null, value)
                        expanded = false
                    },
                )
            }
        }
    }
}
