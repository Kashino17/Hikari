package com.hikari.app.ui.feed

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariTextFaint

/** Feed-Item stammt aus einer Discovery-Quelle (Probe-Kanal oder Themen-Suche). */
fun FeedItem.isDiscovery(): Boolean = source == "probe" || source == "topic"

/**
 * Badge + Ein-Tap-Aktionen für Discovery-Inhalte: "Neu für dich" mit
 * Kanal-Abonnieren bzw. Nie-wieder-zeigen. Bei Themen-Treffern gibt es keinen
 * echten Kanal zum Abonnieren (Pseudo-Kanal) — dann nur Badge + Blocken.
 */
@Composable
fun DiscoveryActions(
    item: FeedItem,
    onSubscribe: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var acted by remember(item.videoId) { mutableStateOf<String?>(null) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Neu für dich",
            style = MaterialTheme.typography.labelSmall,
            color = HikariAmber,
            modifier = Modifier
                .border(1.dp, HikariAmber.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        when (acted) {
            "sub" -> Text(
                text = "Abonniert",
                style = MaterialTheme.typography.labelSmall,
                color = HikariTextFaint,
            )
            "block" -> Text(
                text = "Wird nicht mehr gezeigt",
                style = MaterialTheme.typography.labelSmall,
                color = HikariTextFaint,
            )
            else -> {
                if (item.source == "probe") {
                    Text(
                        text = "Kanal abonnieren",
                        style = MaterialTheme.typography.labelSmall,
                        color = HikariTextFaint,
                        modifier = Modifier
                            .clickable {
                                acted = "sub"
                                onSubscribe()
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Text(
                    text = "Nicht mehr zeigen",
                    style = MaterialTheme.typography.labelSmall,
                    color = HikariTextFaint,
                    modifier = Modifier
                        .clickable {
                            acted = "block"
                            onBlock()
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
