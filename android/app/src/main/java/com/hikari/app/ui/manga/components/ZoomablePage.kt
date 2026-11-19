package com.hikari.app.ui.manga.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Wie weit man in eine Seite hineinzoomen kann — weit genug für Kleingedrucktes und Zeichendetails. */
const val MANGA_MAX_ZOOM = 10f

/**
 * Eine Manga-Seite mit freiem Zoom (Zwei Finger, Doppeltippen) und scharfer Darstellung in jeder Stufe
 * (telephoto lädt große Bilder kachelweise nach). Gezoomt lässt sich bis an jede Kante schieben.
 *
 * @param isCurrent nur die gerade sichtbare Seite behält ihren Zoom; alle anderen springen zurück.
 * @param onTap Tippen mit der waagerechten Stelle 0 (links) bis 1 (rechts) — erst nach der Doppeltipp-Frist,
 *              damit ein Doppeltippen nie zusätzlich blättert.
 */
@Composable
fun ZoomablePage(
    pageImageUrl: String,
    pageNumber: Int,
    isCurrent: Boolean,
    onZoomChange: (isZoomed: Boolean) -> Unit,
    onTap: (x: Float) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = MANGA_MAX_ZOOM, preventOverOrUnderZoom = false),
    )
    val imageState = rememberZoomableImageState(zoomableState)
    val isZoomed by remember { derivedStateOf { (zoomableState.zoomFraction ?: 0f) > 0.01f } }
    LaunchedEffect(isZoomed, isCurrent) { if (isCurrent) onZoomChange(isZoomed) }
    LaunchedEffect(isCurrent) { if (!isCurrent) zoomableState.resetZoom(false) }

    val imageRequest = remember(pageImageUrl) {
        ImageRequest.Builder(context)
            .data(pageImageUrl)
            .listener(onError = { _, _ -> onError() })
            .build()
    }
    var width by remember { mutableIntStateOf(1) }
    ZoomableAsyncImage(
        model = imageRequest,
        contentDescription = "Seite $pageNumber",
        state = imageState,
        onClick = { offset -> onTap(offset.x / width.coerceAtLeast(1)) },
        // Doppeltippen: erst auf 2,5-fach an die getippte Stelle, nochmal = zurück.
        onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 2.5f),
        modifier = modifier.fillMaxSize().onSizeChanged { width = it.width },
    )
}
