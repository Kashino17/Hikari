package com.hikari.app.ui.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import kotlin.math.sin

private val TRACK_WIDTH = 46.dp
private val TRACK_HEIGHT = 28.dp
private val THUMB_SIZE = 22.dp
private const val THUMB_STRETCH = 5f

/**
 * Schalter im Hikari-Stil. Der Knopf zieht sich beim Wechsel kurz in die
 * Länge und federt am Ziel aus — die Bewegung erzählt die Richtung, statt
 * dass die Farbe einfach umspringt. Beim Drücken dehnt er sich ebenfalls,
 * sodass sich der Schalter schon vor dem Loslassen lebendig anfühlt.
 */
@Composable
fun HikariSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "switch-progress",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) HikariPrimary else HikariSurfaceHigh,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "switch-track",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.Black else HikariTextFaint,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "switch-thumb",
    )
    val pressStretch by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switch-press",
    )

    // In der Mitte der Bewegung am breitesten — die klassische Squash-Kurve.
    val travelStretch = sin(progress * Math.PI).toFloat()
    val thumbWidth = THUMB_SIZE + (THUMB_STRETCH * maxOf(travelStretch, pressStretch)).dp

    val padding = (TRACK_HEIGHT - THUMB_SIZE) / 2
    val travel = TRACK_WIDTH - thumbWidth - padding * 2

    Box(
        modifier
            .width(TRACK_WIDTH)
            .height(TRACK_HEIGHT)
            .clip(RoundedCornerShape(TRACK_HEIGHT / 2))
            .background(trackColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onClick = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = padding + travel * progress)
                .width(thumbWidth)
                .height(THUMB_SIZE)
                .clip(RoundedCornerShape(THUMB_SIZE / 2))
                .background(if (checked) thumbColor else HikariText.copy(alpha = 0.55f)),
        )
    }
}
