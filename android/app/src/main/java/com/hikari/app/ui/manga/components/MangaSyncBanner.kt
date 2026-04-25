package com.hikari.app.ui.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaSyncJobDto

private val Accent = Color(0xFFFBBF24)

@Composable
fun MangaSyncBanner(job: MangaSyncJobDto, modifier: Modifier = Modifier) {
    val total = if (job.totalChapters == 0) 1 else job.totalChapters
    val progress = (job.doneChapters.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x4D78350F))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${job.doneChapters} / $total",
            color = Color(0xFFFCD34D),
            fontSize = 12.sp,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(2.dp),
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.1f),
        )
        Text(
            text = "SYNC LÄUFT",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
    }
}
