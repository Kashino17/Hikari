package com.hikari.app.ui.channels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.ui.channels.SharedDefaults

@Composable
fun SharedDefaultsBlock(
    defaults: SharedDefaults,
    allSeries: List<SeriesItemDto>,
    onUpdate: (SharedDefaults.() -> SharedDefaults) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111111))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Defaults für alle",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        SeriesTypeahead(
            value = defaults.seriesTitle.orEmpty(),
            allSeries = allSeries,
            onChange = { _, sid, stitle ->
                onUpdate { copy(seriesId = sid, seriesTitle = stitle) }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = defaults.season?.toString().orEmpty(),
                onValueChange = { input ->
                    val v = input.toIntOrNull()
                    onUpdate { copy(season = v) }
                },
                label = { Text("Staffel") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
            )
            OutlinedTextField(
                value = defaults.dubLanguage.orEmpty(),
                onValueChange = { input ->
                    onUpdate { copy(dubLanguage = input.takeIf { it.isNotBlank() }) }
                },
                label = { Text("Sprache") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = defaults.subLanguage.orEmpty(),
            onValueChange = { input ->
                onUpdate { copy(subLanguage = input.takeIf { it.isNotBlank() }) }
            },
            label = { Text("Untertitel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
