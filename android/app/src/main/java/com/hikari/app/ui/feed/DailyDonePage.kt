package com.hikari.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

/**
 * Bewusster Tagesabschluss als letzte Feed-Seite — KEIN Nachladen, kein
 * Endlos-Scroll. Ruhig und wertschätzend, ohne erhobenen Zeigefinger.
 */
@Composable
fun DailyDonePage(watchedMinutes: Int?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HikariBg)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nachschub wird gesucht",
            style = MaterialTheme.typography.headlineSmall,
            color = HikariText,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (watchedMinutes != null && watchedMinutes > 0) {
                "Du hast heute rund $watchedMinutes Minuten geschaut. " +
                    "Neue Videos werden gerade geprüft — gleich nochmal hochziehen."
            } else {
                "Neue Videos werden gerade geprüft — gleich nochmal hochziehen."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = HikariTextFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Nur Geprüftes kommt in den Feed",
            style = MaterialTheme.typography.labelMedium,
            color = HikariAmber,
        )
    }
}
