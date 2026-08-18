package com.hikari.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

/**
 * Feed-Seite für ein Langvideo: keine Wiedergabe im Pager, sondern eine ruhige
 * Vorschaukarte (Thumbnail, Titel, Kanal, Dauer, KI-Teaser). Tap öffnet den
 * Vollbild-Player, der das Video streamt.
 */
@Composable
fun LongVideoCard(
    item: FeedItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onSubscribeChannel: () -> Unit = {},
    onBlockChannel: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HikariBg)
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp)
            .padding(top = 96.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Text(
                text = formatCardDuration(item.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            color = HikariText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.channelTitle,
            style = MaterialTheme.typography.labelMedium,
            color = HikariTextFaint,
        )
        if (item.isDiscovery()) {
            Spacer(Modifier.height(10.dp))
            DiscoveryActions(item = item, onSubscribe = onSubscribeChannel, onBlock = onBlockChannel)
        }
        val teaser = item.summary ?: item.reasoning
        if (teaser.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = teaser,
                style = MaterialTheme.typography.bodyMedium,
                color = HikariTextFaint,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Tippen zum Ansehen",
            style = MaterialTheme.typography.labelMedium,
            color = HikariAmber,
        )
    }
}

private fun formatCardDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
