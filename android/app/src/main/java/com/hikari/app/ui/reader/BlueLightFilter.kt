package com.hikari.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import kotlinx.coroutines.delay

/** Blaulichtfilter-Stufen der Leser (Manga und Bücher). */
object BlueFilter {
    const val OFF = 0
    const val LIGHT = 1
    const val MEDIUM = 2
    const val STRONG = 3
    /** Mittel, aber nur von 20 bis 7 Uhr. */
    const val EVENING = 4

    val OPTIONS = listOf(OFF to "Aus", LIGHT to "Leicht", MEDIUM to "Mittel", STRONG to "Stark", EVENING to "Abends")

    fun hint(level: Int) = when (level) {
        OFF -> "Normale Farben."
        EVENING -> "Schaltet sich von 20 bis 7 Uhr von selbst ein (Mittel)."
        else -> "Wärmeres Licht, weniger Blau — schont die Augen am Abend."
    }
}

/** Deckkraft des warmen Filters für eine Stufe zu einer Stunde (0..23). */
fun blueFilterStrength(level: Int, hour: Int): Float = when (level) {
    BlueFilter.LIGHT -> 0.12f
    BlueFilter.MEDIUM -> 0.22f
    BlueFilter.STRONG -> 0.34f
    BlueFilter.EVENING -> if (hour >= 20 || hour < 7) 0.22f else 0f
    else -> 0f
}

/**
 * Warmer Schleier über dem Leser. Fängt keine Berührungen ab — Blättern, Zoomen, Menüs gehen hindurch.
 * Liegt zuoberst, damit auch die Menüs nicht grell sind.
 */
@Composable
fun BlueLightFilter(level: Int, modifier: Modifier = Modifier) {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    if (level == BlueFilter.EVENING) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            }
        }
    }
    val a = blueFilterStrength(level, hour)
    if (a > 0f) Box(modifier.fillMaxSize().background(FILTER.copy(alpha = a)))
}

/** Bernstein — nimmt Blau heraus, ohne die Schrift zu verfälschen. */
private val FILTER = Color(0xFFFF8A00)
