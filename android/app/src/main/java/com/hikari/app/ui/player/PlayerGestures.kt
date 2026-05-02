package com.hikari.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import com.hikari.app.ui.feed.HikariIcons

enum class SeekDirection { Backward, Forward }

/** Animated play/pause indicator shown in center when tapping. */
@Composable
fun PlayPauseIndicator(
    playing: Boolean,
    showTrigger: Int,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(showTrigger) {
        if (showTrigger > 0) {
            visible = true
            kotlinx.coroutines.delay(650)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.6f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
            scaleOut(targetScale = 1.15f, animationSpec = tween(180, easing = FastOutLinearInEasing)),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) HikariIcons.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Paused" else "Playing",
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

/**
 * Seek badge shown briefly after a double-tap seek.
 * Dismisses itself after 600ms.
 */
@Composable
fun SeekBadge(direction: SeekDirection?, onDismiss: () -> Unit) {
    if (direction == null) return
    LaunchedEffect(direction) {
        kotlinx.coroutines.delay(600)
        onDismiss()
    }
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (direction == SeekDirection.Backward) HikariIcons.Replay5 else HikariIcons.Forward5,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (direction == SeekDirection.Backward) "-5s" else "+5s",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
