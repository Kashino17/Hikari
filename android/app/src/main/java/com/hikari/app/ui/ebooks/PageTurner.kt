package com.hikari.app.ui.ebooks

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.hikari.app.ui.manga.PageAnim
import com.hikari.app.ui.manga.turnOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel

/**
 * Blätter-Animation über dem Buch: Die Leser-Seite (WebView) blättert selbst sofort um; darüber liegen
 * Standbilder der alten und neuen Seite, die die App bewegt. [p] 0 = frühere Seite liegt da, 1 = ganz weg.
 */
@Stable
class PageTurner {
    var top by mutableStateOf<ImageBitmap?>(null)
    var under by mutableStateOf<ImageBitmap?>(null)
    var active by mutableStateOf(false)
    val p = Animatable(0f)
    var forward = true
    var dragging = false
    var prep: Job? = null
    /** Rückmeldung von reader.js: hat sich die Seite wirklich bewegt? */
    val turned = Channel<Boolean>(Channel.CONFLATED)

    fun drainTurned() { while (turned.tryReceive().isSuccess) Unit }

    fun reset() {
        active = false
        top = null
        under = null
    }
}

internal fun pageAnimOf(name: String): PageAnim = runCatching { PageAnim.valueOf(name) }.getOrDefault(PageAnim.BOOK)

/** Die bewegten Standbilder; liegt genau über der WebView. */
@Composable
internal fun PageTurnLayer(turner: PageTurner, anim: PageAnim, paper: Color, modifier: Modifier = Modifier) {
    if (!turner.active) return
    Box(modifier.fillMaxSize()) {
        turner.under?.let { img ->
            Image(img, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().background(paper))
        }
        // Schatten auf der darunterliegenden Seite, solange die obere noch darüber liegt.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = turnOverlay(false, turner.p.value, size.width, anim).shade.coerceIn(0f, 1f) }
                .background(Color.Black),
        )
        turner.top?.let { img ->
            Image(
                img, null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        val t = turnOverlay(true, turner.p.value, size.width, anim)
                        translationX = t.translationX
                        rotationY = t.rotationY
                        alpha = t.alpha
                        transformOrigin = TransformOrigin(t.pivotX, 0.5f)
                        cameraDistance = 14f * density
                        shadowElevation = if (anim == PageAnim.SLIDE && turner.p.value > 0f) 24f else 0f
                    }
                    .background(paper)
                    .drawWithContent {
                        drawContent()
                        val shade = turnOverlay(true, turner.p.value, size.width, anim).shade
                        if (shade > 0f) drawRect(Color.Black.copy(alpha = shade * 0.6f))
                    },
            )
        }
    }
}
