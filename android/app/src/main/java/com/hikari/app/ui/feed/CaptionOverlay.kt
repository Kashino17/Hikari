package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.model.Caption

/**
 * YouTube-Shorts-style caption overlay. Shows the currently-spoken word
 * (or short word-group) large, white, with a black drop shadow for
 * readability over any video content. Updates as [positionMs] changes.
 *
 * Renders nothing if [captions] is null/empty or no caption is active.
 */
@Composable
fun CaptionOverlay(
    captions: List<Caption>?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (captions.isNullOrEmpty()) return
    // Find currently-active caption. Linear scan is fine for ≤200 words.
    val active = remember(positionMs, captions) {
        captions.firstOrNull { positionMs in it.startMs..it.endMs }
    } ?: return

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = active.text,
            transitionSpec = {
                fadeIn(tween(120)) togetherWith fadeOut(tween(80))
            },
            label = "caption",
        ) { word ->
            Text(
                text = word,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 0f),
                        blurRadius = 12f,
                    ),
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
