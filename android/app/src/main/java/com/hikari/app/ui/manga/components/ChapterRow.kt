package com.hikari.app.ui.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaChapterDto

private val Accent = Color(0xFFFBBF24)

@Composable
fun ChapterRow(
    chapter: MangaChapterDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CH ${chapter.number.toInt()}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = chapter.title.orEmpty(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (chapter.isRead == 1) {
            Text(
                text = "READ",
                color = Accent,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}
