package com.hikari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Hikari palette — dark only, single warm-amber accent ("Hikari" = light).
val HikariBg = Color(0xFF0A0A0A)
val HikariSurface = Color(0xFF111111)
val HikariSurfaceHigh = Color(0xFF1A1A1A)
val HikariBorder = Color(0x0FFFFFFF)        // ~6% white
val HikariBorderStrong = Color(0x1FFFFFFF)  // ~12% white
val HikariText = Color(0xEBFFFFFF)          // ~92% white
val HikariTextMuted = Color(0x7AFFFFFF)     // ~48% white
val HikariTextFaint = Color(0x47FFFFFF)     // ~28% white

val HikariAmber = Color(0xFFFBBF24)
val HikariAmberSoft = Color(0x1FFBBF24)
val HikariDanger = Color(0xFFF87171)

private val HikariColors = darkColorScheme(
    primary = HikariAmber,
    onPrimary = Color.Black,
    secondary = HikariAmber,
    onSecondary = Color.Black,
    background = HikariBg,
    onBackground = HikariText,
    surface = HikariSurface,
    onSurface = HikariText,
    surfaceVariant = HikariSurfaceHigh,
    onSurfaceVariant = HikariTextMuted,
    outline = HikariBorder,
    outlineVariant = HikariBorderStrong,
    error = HikariDanger,
    onError = Color.Black,
)

private val HikariTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = HikariText),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = HikariText),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = HikariText),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = HikariText),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = HikariText),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = HikariTextMuted),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HikariText),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = HikariTextMuted),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.5.sp, color = HikariTextFaint),
)

@Composable
fun HikariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HikariColors,
        typography = HikariTypography,
        content = content,
    )
}
