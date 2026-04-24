package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hikari.app.data.sponsor.SegmentCategory

/**
 * Overlay pill shown when the current playback position enters a SKIP_MANUAL segment.
 * Fades in, shows "{category label} überspringen →", dismisses after 5s or on click.
 */
@Composable
fun ManualSkipPill(
    category: SegmentCategory?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember(category) { mutableStateOf(category != null) }
    LaunchedEffect(category) {
        if (category != null) {
            visible = true
            kotlinx.coroutines.delay(5_000)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible && category != null,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { it / 3 },
        modifier = modifier,
    ) {
        category?.let { cat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .clickable { onSkip() }
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(cat.color, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${cat.label} überspringen  →",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
