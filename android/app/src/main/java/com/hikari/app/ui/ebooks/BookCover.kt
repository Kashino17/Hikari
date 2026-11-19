package com.hikari.app.ui.ebooks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** Farbe einer Kategorie — für typografische Titelbilder und Akzente. */
internal fun categoryColor(category: String?): Color = when (category) {
    "krimi" -> Color(0xFF7F1D1D)
    "abenteuer" -> Color(0xFF9A3412)
    "fantastik" -> Color(0xFF4C1D95)
    "historisch" -> Color(0xFF78350F)
    "liebe" -> Color(0xFF9D174D)
    "humor" -> Color(0xFFA16207)
    "maerchen" -> Color(0xFF115E59)
    "drama" -> Color(0xFF1E3A8A)
    "lyrik" -> Color(0xFF6B21A8)
    "kinder" -> Color(0xFF0F766E)
    "roman", "erzaehlungen" -> Color(0xFF14532D)
    "geschichte", "biografie" -> Color(0xFF57534E)
    "philosophie", "religion", "psychologie" -> Color(0xFF312E81)
    "wissenschaft", "reisen" -> Color(0xFF155E75)
    "politik" -> Color(0xFF334155)
    "kunst" -> Color(0xFF831843)
    else -> Color(0xFF3F3F46)
}

/**
 * Titelbild eines Buchs: echtes Bild, sonst ein schlichtes typografisches Titelbild.
 * PDF-Bücher tragen immer ein deutliches „PDF“-Schild.
 */
@Composable
fun BookCover(
    title: String,
    authors: String,
    category: String?,
    isPdf: Boolean,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    localPath: String? = null,
) {
    var failed by remember(imageUrl, localPath) { mutableStateOf(false) }
    val data: Any? = localPath?.let(::File)?.takeIf { it.exists() } ?: imageUrl
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(6.dp)),
    ) {
        if (data == null || failed) {
            TypographicCover(title, authors, category)
        } else {
            AsyncImage(
                model = data,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
        if (isPdf) PdfBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
fun PdfBadge(modifier: Modifier = Modifier) {
    Text(
        "PDF",
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.8.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xE6111111))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Welches Ornament-Band ein Titel bekommt (0–3) — fest je Titel, damit nicht alle gleich aussehen. */
internal fun coverOrnament(title: String): Int = (title.fold(7) { h, c -> h * 31 + c.code } and 0x7fffffff) % 4

/**
 * Eigenes Titelbild für Bücher ohne echten Deckel — klassisch wie die Insel-Bücherei: gedeckter Grund
 * in der Farbe der Kategorie, feiner Doppelrahmen, Ornament-Band, Titel in Serifenschrift, Autor gesperrt.
 */
@Composable
private fun TypographicCover(title: String, authors: String, category: String?) {
    val base = categoryColor(category)
    val ink = Color(0xFFF3E9D2) // Papierweiß für Schrift und Linien
    val ornament = coverOrnament(title)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(base.copy(alpha = 0.92f).compositeOver(Color(0xFF2A2420)), base.copy(alpha = 0.7f).compositeOver(Color(0xFF14110F))))),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val line = ink.copy(alpha = 0.55f)
            val m1 = w * 0.06f
            val m2 = w * 0.085f
            // Doppelrahmen
            drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(m1, m1), size = androidx.compose.ui.geometry.Size(w - 2 * m1, h - 2 * m1), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.008f))
            drawRect(line.copy(alpha = 0.35f), topLeft = androidx.compose.ui.geometry.Offset(m2, m2), size = androidx.compose.ui.geometry.Size(w - 2 * m2, h - 2 * m2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.004f))
            // Ornament-Band oben und unten
            val bandH = h * 0.07f
            for (yTop in listOf(h * 0.14f, h * 0.79f)) {
                val cy = yTop + bandH / 2
                val step = w * 0.09f
                var x = m2 + step / 2
                while (x < w - m2) {
                    when (ornament) {
                        0 -> { // Rauten
                            val r = bandH * 0.32f
                            val path = androidx.compose.ui.graphics.Path().apply { moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close() }
                            drawPath(path, ink.copy(alpha = 0.6f))
                        }
                        1 -> drawCircle(ink.copy(alpha = 0.6f), radius = bandH * 0.13f, center = androidx.compose.ui.geometry.Offset(x, cy)) // Punkte
                        2 -> { // Bögen
                            drawArc(ink.copy(alpha = 0.55f), 180f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(x - step / 2, cy - bandH * 0.25f), size = androidx.compose.ui.geometry.Size(step, bandH * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.006f))
                        }
                        else -> drawLine(ink.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x - step * 0.3f, cy + bandH * 0.3f), androidx.compose.ui.geometry.Offset(x + step * 0.3f, cy - bandH * 0.3f), strokeWidth = w * 0.007f) // Schraffur
                    }
                    x += step
                }
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                title,
                color = ink,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (authors.isNotBlank()) {
                Box(Modifier.padding(vertical = 7.dp).size(width = 18.dp, height = 1.dp).background(ink.copy(alpha = 0.6f)))
                Text(
                    authors.uppercase(),
                    color = ink.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Serif,
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun Color.compositeOver(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha),
    green = green * alpha + bg.green * (1 - alpha),
    blue = blue * alpha + bg.blue * (1 - alpha),
    alpha = 1f,
)
