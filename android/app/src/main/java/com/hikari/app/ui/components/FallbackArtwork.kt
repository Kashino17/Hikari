package com.hikari.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Gestaltetes Ersatz-Artwork für fehlende Bilder (Cover, Poster, Thumbnails):
 * ein aus dem Titel deterministisch abgeleiteter, dunkler Duo-Ton-Gradient mit
 * radialem Glanz und Vignette — dazu der Titel so groß wie möglich zentriert.
 * Soll gewollt wirken (Netflix/Epic-Stil), nicht wie ein Ladefehler.
 *
 * Funktioniert in jeder Aspect-Ratio (2:3-Poster, 16:9-Thumbnail, quadratisch),
 * weil sich die Schriftgröße an der tatsächlichen Box-Größe misst. Als
 * Unterlage unter einem Coil-AsyncImage eingesetzt, deckt die Komponente drei
 * Fälle ab: URL null/leer, Bild lädt noch, Bild fehlgeschlagen.
 *
 * [showTitle] = false für Hero-Flächen, die ihren Titel bereits selbst
 * einblenden — dann bleibt nur das gestaltete Artwork ohne doppelten Text.
 */
@Composable
fun FallbackArtwork(
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = title,
    showTitle: Boolean = true,
) {
    val colors = remember(title) { fallbackArtworkColors(title) }
    val cleanTitle = title.trim()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                // Diagonal von oben-links nach unten-rechts (Offset.Infinite = Ecke).
                Brush.linearGradient(
                    colors = listOf(Color(colors.start), Color(colors.end)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            )
            .then(
                if (contentDescription.isNullOrBlank()) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Radialer Glanz über der Mitte (Muster: Akzent-Glow in GameUiKit).
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0f to Color(colors.glow).copy(alpha = 0.32f),
                    0.6f to Color.Transparent,
                ),
            ),
        )
        // Vignette: Ränder abdunkeln, Mitte frei — wirkt cinematic.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.42f),
                ),
            ),
        )
        if (!showTitle) return@BoxWithConstraints
        if (cleanTitle.isEmpty()) {
            // Neutraler Zustand ohne Titel: nur Artwork + dezentes Icon.
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(minOf(maxWidth, maxHeight) * 0.3f),
            )
        } else {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val availW = with(density) { (maxWidth - 20.dp).toPx() }
            val availH = with(density) { (maxHeight - 16.dp).toPx() }
            val fontSize = remember(cleanTitle, availW, availH) {
                fittingFontSize(measurer, cleanTitle, availW, availH)
            }
            Text(
                text = cleanTitle,
                color = Color.White,
                fontSize = fontSize,
                lineHeight = fontSize * 1.05f,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Größte Schriftgröße (Binary Search), bei der der gewrappte Titel noch in die Box passt. */
private fun fittingFontSize(
    measurer: TextMeasurer,
    text: String,
    maxWidthPx: Float,
    maxHeightPx: Float,
    maxLines: Int = 4,
): TextUnit {
    if (maxWidthPx <= 0f || maxHeightPx <= 0f ||
        maxWidthPx.isInfinite() || maxHeightPx.isInfinite()
    ) {
        return 20.sp
    }
    var low = 8f
    var high = 320f
    repeat(14) {
        val mid = (low + high) / 2f
        val layout: TextLayoutResult = measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontSize = mid.sp,
                lineHeight = (mid * 1.05f).sp,
                fontWeight = FontWeight.Black,
            ),
            constraints = Constraints(maxWidth = maxWidthPx.roundToInt()),
            maxLines = maxLines,
            softWrap = true,
        )
        val fits = !layout.hasVisualOverflow &&
            layout.size.width <= maxWidthPx &&
            layout.size.height <= maxHeightPx
        if (fits) low = mid else high = mid
    }
    return low.sp
}
