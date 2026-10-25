package com.hikari.app.ui.russian

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val JourneyPaper = Color(0xFFFAF7F0)
internal val JourneyInk = Color(0xFF243B37)
internal val JourneyMuted = Color(0xFF63756D)
internal val JourneyGreen = Color(0xFF355F50)
internal val JourneySage = Color(0xFFE2EBDD)
internal val JourneyCoral = Color(0xFFC6563F)
internal val JourneyPeach = Color(0xFFF4DCD0)
internal val JourneyLine = Color(0xFFE2E5DC)

@Composable internal fun JourneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = JourneyGreen, onPrimary = Color.White, secondary = JourneyCoral,
        primaryContainer = JourneySage, onPrimaryContainer = JourneyInk,
        secondaryContainer = JourneySage, onSecondaryContainer = JourneyInk,
        background = JourneyPaper, onBackground = JourneyInk, surface = Color.White,
        onSurface = JourneyInk, surfaceVariant = JourneySage, onSurfaceVariant = JourneyMuted,
        outline = JourneyMuted, outlineVariant = JourneyLine, error = JourneyCoral,
    ), content = content)
}

@Composable internal fun JourneyTitle(text: String, small: Boolean = false) {
    Text(text, color = JourneyInk, fontFamily = FontFamily.Serif, fontSize = if (small) 29.sp else 35.sp,
        lineHeight = if (small) 34.sp else 40.sp, fontWeight = FontWeight.Medium)
}
@Composable internal fun JourneyBody(text: String, muted: Boolean = false) {
    Text(text, color = if (muted) JourneyMuted else JourneyInk, fontSize = 16.sp, lineHeight = 23.sp)
}
@Composable internal fun JourneyLabel(text: String) {
    Text(text, color = JourneyMuted, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}
@Composable internal fun JourneySmall(text: String) {
    Text(text, color = JourneyMuted, fontSize = 13.sp, lineHeight = 19.sp)
}
@Composable internal fun JourneyCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(color).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable internal fun JourneyButton(text: String, icon: ImageVector = Icons.Rounded.ArrowForward,
    enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JourneyGreen, contentColor = Color.White)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp)); Icon(icon, null, Modifier.size(22.dp))
    }
}
@Composable internal fun JourneyAudio(text: String = "Anhören", onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = JourneySage, contentColor = JourneyGreen),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.VolumeUp, null, Modifier.size(21.dp)); Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable internal fun JourneyLink(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = JourneyGreen)
        Text(text, color = JourneyInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = JourneyMuted)
    }
}

/** Original vector art: one cafe, the same cups and the same partner throughout the lesson. */
@Composable internal fun JourneyArtwork(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = when (kind) {
        "hello" -> "Anja begrüßt dich im Café"; "coffee" -> "Eine Tasse Kaffee"; "tea" -> "Eine Tasse Tee"
        "yes" -> "Eine freundliche Zustimmung"; "no" -> "Freundlich Nein sagen"
        "choice" -> "Tee oder Kaffee: zwei Möglichkeiten"
        "heart", "thanks" -> "Ein warmer Gesprächsmoment"; else -> "Ein gemütlicher Platz im Café"
    } }) {
        val factor = minOf(size.width / 340f, size.height / 200f)
        scale(factor, factor, pivot = Offset(size.width / 2, size.height / 2)) {
            val x = size.width / 2 - 170f; val y = size.height / 2 - 100f
            fun p(a: Float, b: Float) = Offset(x + a, y + b)
            fun rect(a: Float, b: Float, w: Float, h: Float, c: Color, radius: Float = 0f) =
                drawRoundRect(c, p(a, b), Size(w, h), CornerRadius(radius))
            fun cup(cx: Float, cy: Float, color: Color, tea: Boolean = true) {
                drawOval(JourneyGreen.copy(alpha = .12f), p(cx - 16, cy + 57), Size(111f, 18f))
                drawCircle(color, 21f, p(cx + 78, cy + 22), style = Stroke(10f))
                rect(cx, cy, 76f, 62f, color, 18f)
                drawOval(Color(0xFFFAF3E6), p(cx, cy - 8), Size(76f, 20f))
                drawOval(if (tea) Color(0xFFB9713C) else Color(0xFF613F2D), p(cx + 7, cy - 4), Size(62f, 12f))
                if (tea) {
                    drawLine(JourneyPaper, p(cx + 55, cy + 1), p(cx + 62, cy + 37), 2f)
                    rect(cx + 55, cy + 32, 15f, 21f, JourneyPaper, 3f)
                    drawCircle(JourneyCoral, 3f, p(cx + 62, cy + 42))
                }
                val steam = Path().apply { moveTo(x + cx + 20, y + cy - 18); cubicTo(x + cx + 3, y + cy - 35, x + cx + 38, y + cy - 40, x + cx + 22, y + cy - 58) }
                drawPath(steam, JourneyMuted.copy(alpha = .45f), style = Stroke(3f, cap = StrokeCap.Round))
            }
            fun heart(cx: Float, cy: Float, s: Float) {
                val path = Path().apply {
                    moveTo(x + cx, y + cy + s)
                    cubicTo(x + cx - s * 1.7f, y + cy, x + cx - s, y + cy - s, x + cx, y + cy - s * .3f)
                    cubicTo(x + cx + s, y + cy - s, x + cx + s * 1.7f, y + cy, x + cx, y + cy + s)
                }
                drawPath(path, JourneyCoral)
            }
            when (kind) {
                "choice" -> {
                    drawCircle(JourneySage, 66f, p(106f, 104f))
                    drawCircle(JourneyPeach, 66f, p(252f, 104f))
                    cup(64f, 96f, JourneyGreen)
                    cup(210f, 96f, JourneyCoral, false)
                }
                "hello" -> {
                    drawCircle(JourneyPeach, 78f, p(170f, 106f))
                    rect(112f, 109f, 118f, 98f, JourneyGreen, 42f)
                    rect(162f, 97f, 21f, 27f, Color(0xFFE4B08B), 8f)
                    drawOval(Color(0xFF4D3932), p(129f, 24f), Size(84f, 102f))
                    drawOval(Color(0xFFEAC29D), p(141f, 38f), Size(65f, 78f))
                    drawOval(Color(0xFF4D3932), p(133f, 22f), Size(62f, 38f))
                    drawCircle(JourneyInk, 2.4f, p(159f, 77f)); drawCircle(JourneyInk, 2.4f, p(187f, 77f))
                    drawArc(JourneyCoral, 10f, 150f, false, p(164f, 87f), Size(18f, 10f), style = Stroke(2.5f, cap = StrokeCap.Round))
                    drawLine(JourneyGreen, p(121f, 143f), p(93f, 113f), 20f, StrokeCap.Round)
                    drawLine(Color(0xFFEAC29D), p(93f, 113f), p(85f, 78f), 16f, StrokeCap.Round)
                    for (i in 0..3) drawLine(Color(0xFFEAC29D), p(80f + i * 4, 83f), p(76f + i * 5, 64f - (i % 2) * 5), 4f, StrokeCap.Round)
                    drawLine(JourneyCoral, p(65f, 56f), p(58f, 48f), 3f, StrokeCap.Round)
                    drawLine(JourneyCoral, p(91f, 48f), p(93f, 38f), 3f, StrokeCap.Round)
                    heart(250f, 72f, 10f)
                }
                "yes", "no" -> {
                    drawCircle(if (kind == "yes") JourneySage else JourneyPeach, 67f, p(170f, 100f))
                    if (kind == "yes") {
                        val tick = Path().apply { moveTo(x + 137, y + 100); lineTo(x + 161, y + 123); lineTo(x + 202, y + 77) }
                        drawPath(tick, JourneyGreen, style = Stroke(13f, cap = StrokeCap.Round))
                    } else drawLine(JourneyCoral, p(142f, 100f), p(198f, 100f), 13f, StrokeCap.Round)
                    drawCircle(JourneyCoral.copy(alpha = .3f), 5f, p(260f, 58f)); drawCircle(JourneyGreen.copy(alpha = .3f), 4f, p(82f, 140f))
                }
                "heart", "thanks" -> {
                    drawCircle(JourneyPeach, 67f, p(170f, 102f)); heart(170f, 90f, 38f)
                    drawCircle(JourneyGreen.copy(alpha = .4f), 5f, p(78f, 73f)); drawCircle(JourneyCoral.copy(alpha = .4f), 4f, p(259f, 137f))
                }
                "tea", "coffee" -> {
                    drawCircle(JourneySage, 76f, p(169f, 100f))
                    cup(130f, 100f, if (kind == "tea") JourneyGreen else JourneyCoral, kind == "tea")
                    drawOval(Color(0xFFBD935D), p(227f, 155f), Size(30f, 14f))
                }
                else -> {
                    rect(16f, 14f, 308f, 169f, JourneySage, 26f)
                    rect(197f, 25f, 88f, 106f, Color(0xFFF9EACA), 42f)
                    drawCircle(Color(0xFFEFB971), 23f, p(251f, 62f))
                    drawLine(JourneyGreen.copy(alpha = .18f), p(242f, 25f), p(242f, 132f), 3f)
                    rect(44f, 147f, 248f, 12f, Color(0xFFCBA57B), 6f)
                    cup(112f, 84f, JourneyCoral)
                    rect(53f, 113f, 30f, 34f, JourneyGreen, 6f)
                    drawLine(JourneyGreen, p(68f, 117f), p(68f, 70f), 3f)
                    drawOval(JourneyGreen, p(47f, 72f), Size(21f, 13f))
                    drawOval(Color(0xFF799578), p(68f, 87f), Size(24f, 14f))
                    drawOval(Color(0xFF799578), p(56f, 56f), Size(13f, 23f))
                    heart(288f, 33f, 8f)
                }
            }
        }
    }
}
