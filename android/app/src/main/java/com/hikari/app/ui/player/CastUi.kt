package com.hikari.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hikari.app.cast.CastController
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

/** Auswahl der Fernseher/Chromecasts im WLAN; sucht nur, solange sie offen ist. */
@Composable
fun CastPickerDialog(cast: CastController, onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        cast.startSearch()
        onDispose { cast.stopSearch() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C1C20))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cast, null, tint = HikariAmber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Auf Fernseher streamen", color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            if (cast.casting) {
                Text(
                    "Verbunden mit ${cast.deviceName ?: "Fernseher"}",
                    color = HikariTextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { cast.disconnect(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verbindung trennen")
                }
            } else if (cast.targets.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HikariAmber)
                    Spacer(Modifier.width(10.dp))
                    Text("Suche Geräte im WLAN …", color = HikariTextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Handy und Fernseher müssen im selben WLAN sein.",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                )
            } else {
                cast.targets.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !t.connecting) { cast.connect(t); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Cast, null, tint = HikariText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t.name,
                            color = HikariText,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (t.connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HikariAmber)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}
