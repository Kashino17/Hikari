package com.hikari.app.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.theme.HikariAmber

/**
 * Die Bildfläche einer hochkanten Plakatkarte.
 *
 * Gibt es ein echtes Plakat ([posterUrl]), steht es für sich — der Titel ist
 * darauf schon gestaltet. Sonst wird aus dem Standbild ([frameUrl]) ein Plakat
 * gebaut: leicht herangezoomt (schneidet die schwarzen Kinobalken weg), oben
 * und unten abgedunkelt, mit Akzentstrich und dem Titel in Versalien.
 */
@Composable
fun PosterFace(
    title: String,
    subtitle: String,
    posterUrl: String?,
    frameUrl: String?,
    modifier: Modifier = Modifier,
) {
    var posterLoaded by remember(posterUrl) { mutableStateOf(false) }
    // Ein von Hand gesetztes Cover ist oft selbst schon ein hochkantes Plakat —
    // dann gilt es als echtes Plakat: kein Zoom, kein Titel darüber.
    var frameIsPoster by remember(frameUrl) { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        FallbackArtwork(title = title, showTitle = false)

        if (!posterLoaded && !frameUrl.isNullOrBlank()) {
            val zoom = if (frameIsPoster) 1f else FRAME_ZOOM
            AsyncImage(
                model = frameUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                },
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    frameIsPoster = size.width > 0f && size.height > 0f && size.width / size.height < 0.9f
                },
            )
        }
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { posterLoaded = true },
                onError = { posterLoaded = false },
            )
        }

        if (!posterLoaded && !frameIsPoster) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.38f),
                        0.3f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 8.dp)) {
                Box(Modifier.width(18.dp).height(2.dp).background(HikariAmber))
                Spacer(Modifier.height(5.dp))
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Kinofilme im 16:9-Standbild haben oben und unten schwarze Balken (~12 %) — dieser Zoom nimmt sie weg. */
private const val FRAME_ZOOM = 1.32f
