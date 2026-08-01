package com.hikari.app.ui.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

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
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = HikariPrimary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "KAPITEL-ENDE",
            color = HikariTextFaint,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = "Kapitel gelesen",
            color = HikariText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (nextChapterId != null) {
            Button(
                onClick = onNextChapter,
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(50),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nächstes Kapitel", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            TextButton(onClick = onBackToOverview, modifier = Modifier.padding(top = 4.dp)) {
                Text("Zur Übersicht", color = HikariTextMuted, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onBackToOverview,
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(50),
            ) {
                Text("Zur Übersicht", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
