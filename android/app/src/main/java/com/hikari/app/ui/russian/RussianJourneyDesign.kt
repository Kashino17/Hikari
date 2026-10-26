package com.hikari.app.ui.russian

import com.hikari.app.ui.theme.*
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun JourneyTitle(text: String, small: Boolean = false) {
    Text(text, color = HikariText, fontSize = if (small) 22.sp else 26.sp,
        lineHeight = if (small) 28.sp else 32.sp, fontWeight = FontWeight.SemiBold)
}
@Composable internal fun JourneyBody(text: String, muted: Boolean = false) {
    Text(text, color = if (muted) HikariTextMuted else HikariText, fontSize = 16.sp, lineHeight = 23.sp)
}
@Composable internal fun JourneyLabel(text: String) {
    Text(text, color = HikariTextMuted, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}
@Composable internal fun JourneySmall(text: String) {
    Text(text, color = HikariTextMuted, fontSize = 13.sp, lineHeight = 19.sp)
}
@Composable internal fun JourneyCard(modifier: Modifier = Modifier, color: Color = HikariCardBg, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color).border(1.dp, HikariBorder, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable internal fun JourneyButton(text: String, icon: ImageVector = Icons.Rounded.ArrowForward,
    enabled: Boolean = true, onClick: () -> Unit) {
    RuPrimaryButton(text = text, enabled = enabled, trailingIcon = icon, onClick = onClick)
}

@Composable internal fun JourneyAudio(text: String = "Anhören", onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HikariBorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HikariText),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.VolumeUp, null, Modifier.size(21.dp)); Spacer(Modifier.width(8.dp))
        Text(text, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable internal fun JourneyLink(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = HikariAmber)
        Text(text, color = HikariText, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = HikariTextMuted)
    }
}

/** The same Material icon language and amber accent as the surrounding Russian course. */
@Composable internal fun JourneyArtwork(kind: String, modifier: Modifier = Modifier) {
    val (icon, description) = when (kind) {
        "hello" -> Icons.Rounded.WavingHand to "Begrüßung"
        "tea", "coffee", "cafe" -> Icons.Rounded.LocalCafe to "Ein Gespräch im Café"
        "yes" -> Icons.Rounded.CheckCircleOutline to "Ja"
        "no" -> Icons.Rounded.RemoveCircleOutline to "Nein"
        "heart", "thanks" -> Icons.Rounded.FavoriteBorder to "Ein freundlicher Gesprächsmoment"
        "choice" -> Icons.Rounded.CompareArrows to "Zwei Möglichkeiten"
        else -> Icons.Rounded.ChatBubbleOutline to "Gespräch"
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(icon, description, modifier = Modifier.size(52.dp), tint = HikariAmber)
    }
}
