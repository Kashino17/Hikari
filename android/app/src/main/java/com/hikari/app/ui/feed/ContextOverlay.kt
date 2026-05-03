package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariAmber

/**
 * AI-generated 1-3 sentence context shown at the top of a clip during the
 * first ~6 seconds. The overlay aligns with the top of the visible video
 * content (16:9 source inside the clip's 9:16 frame, or the native letterbox
 * top for legacy videos) rather than floating in the empty band above.
 */
@Composable
fun ContextOverlay(
    context: String?,
    kind: String,
    aspectRatio: String?,
    isCurrent: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier,
    visibleForMs: Long = 6_000L,
    fadeOutOverMs: Int = 600,
) {
    if (context.isNullOrBlank()) return
    val visible = isCurrent && positionMs < visibleForMs

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = maxWidth
        val containerH = maxHeight
        val effectiveAspect = effectiveContentAspect(kind, aspectRatio)
        val contentH = containerW / effectiveAspect
        val topOffset = if (contentH < containerH) (containerH - contentH) / 2 else 0.dp

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(fadeOutOverMs)),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topOffset),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.80f),
                            0.65f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Transparent,
                        ),
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 30.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(48.dp)
                            .background(HikariAmber, RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = context,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Inside a clip the 16:9 source sits centered with a blur band above/below;
 *  for legacy items the player letterboxes the native aspect itself. */
private fun effectiveContentAspect(kind: String, aspectRatio: String?): Float {
    if (kind == "clip") return 16f / 9f
    val parts = aspectRatio?.split(":", "/") ?: return 16f / 9f
    if (parts.size != 2) return 16f / 9f
    val w = parts[0].toFloatOrNull() ?: return 16f / 9f
    val h = parts[1].toFloatOrNull() ?: return 16f / 9f
    return if (h > 0) w / h else 16f / 9f
}
