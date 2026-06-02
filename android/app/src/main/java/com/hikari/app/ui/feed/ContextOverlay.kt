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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariAmber

/**
 * AI-generated short context shown during the first ~6 seconds of a clip.
 *
 * Placement: a clip is a 16:9 source centered in the 9:16 frame, leaving an
 * empty band above the video. We put the overlay INSIDE that band (anchored to
 * the band, growing upward from the video's top edge) with a SOLID dark card —
 * so it sits ABOVE the picture instead of on top of it, and the text stays
 * fully readable without covering the video. Capped at 2 lines.
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
    // Safety net so a long server string can't overflow onto the video. Keep it
    // to ~2 lines' worth; the backend also generates shorter text now.
    val shown = if (context.length > 150) context.take(147).trimEnd() + "…" else context

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = maxWidth
        val containerH = maxHeight
        val effectiveAspect = effectiveContentAspect(kind, aspectRatio)
        val contentH = containerW / effectiveAspect
        // Top of the visible video content. The card sits in the band ABOVE this
        // line and is bottom-anchored to it, so it never overlaps the picture.
        val videoTop = if (contentH < containerH) (containerH - contentH) / 2 else 0.dp

        // Lift the card well into the empty band above the video (not flush
        // against the video edge), with a status-bar-safe floor so it never
        // slides under the clock. ~150dp clears a 2-line card plus a gap.
        val cardTop = (videoTop - 150.dp).coerceAtLeast(56.dp)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(fadeOutOverMs)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = cardTop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(
                        // Solid dark card — readable, no video bleeding through.
                        Color.Black.copy(alpha = 0.85f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(38.dp)
                            .background(HikariAmber, RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = shown,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
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
