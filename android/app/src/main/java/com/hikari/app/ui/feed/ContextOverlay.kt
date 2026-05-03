package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * AI-generated 1-3 sentence context shown at the top of a clip during the
 * first ~6 seconds. Sets up the topic so the viewer can follow without prior
 * context from the source video. Fades out gracefully so the player has the
 * stage afterwards.
 */
@Composable
fun ContextOverlay(
    context: String?,
    isCurrent: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier,
    visibleForMs: Long = 6_000L,
    fadeOutOverMs: Int = 600,
) {
    if (context.isNullOrBlank()) return
    val visible = isCurrent && positionMs < visibleForMs

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(fadeOutOverMs)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = context,
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
