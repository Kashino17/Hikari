package com.hikari.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

/** Ein Tab: Beschriftung und, wo es etwas zu zählen gibt, die Zahl dahinter. */
data class TextTab(val label: String, val count: Int? = null)

/**
 * Schlichte Text-Tabs auf einer feinen Linie: der aktive ist hell und trägt den Amber-Strich.
 * Gleiche Form im Profil und in der Spiele-Übersicht.
 */
@Composable
fun TextTabs(
    tabs: List<TextTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(HikariBorder),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            tabs.forEachIndexed { index, tab ->
                TextTabLabel(tab, selected = index == selected) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun TextTabLabel(tab: TextTab, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (selected) HikariText else HikariTextFaint,
        animationSpec = tween(160),
        label = "tab-color",
    )
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                tab.label,
                color = color,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
            if (tab.count != null) {
                Spacer(Modifier.width(4.dp))
                Text(tab.count.toString(), color = color, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(if (selected) HikariAmber else Color.Transparent),
        )
    }
}
