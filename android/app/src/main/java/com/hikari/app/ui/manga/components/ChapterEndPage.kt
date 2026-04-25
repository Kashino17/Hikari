package com.hikari.app.ui.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFFBBF24)

@Composable
fun ChapterEndPage(
    nextChapterId: String?,
    onNextChapter: () -> Unit,
    onBackToOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "KAPITEL-ENDE",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        Button(
            onClick = if (nextChapterId != null) onNextChapter else onBackToOverview,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = if (nextChapterId != null) "Nächstes Kapitel →" else "Zur Übersicht",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
