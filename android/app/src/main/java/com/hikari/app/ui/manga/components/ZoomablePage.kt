package com.hikari.app.ui.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun ZoomablePage(
    pageImageUrl: String,
    pageNumber: Int,
    pagerState: PagerState,
    pageIdx: Int,
    onZoomChange: (isZoomed: Boolean) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zoomableState = rememberZoomableState()
    val imageState = rememberZoomableImageState(zoomableState)
    val isZoomed by remember {
        derivedStateOf { (zoomableState.zoomFraction ?: 0f) > 0.05f }
    }
    LaunchedEffect(isZoomed) { onZoomChange(isZoomed) }

    val imageRequest = remember(pageImageUrl) {
        ImageRequest.Builder(context)
            .data(pageImageUrl)
            .listener(onError = { _, _ -> onError() })
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = pagerState.getOffsetDistanceInPages(pageIdx).coerceIn(-1f, 1f)
                cameraDistance = 16f * density
                rotationY = offset * 75f
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (offset < 0f) 0f else 1f,
                    pivotFractionY = 1f,
                )
                shadowElevation = 24f
            },
    ) {
        ZoomableAsyncImage(
            model = imageRequest,
            contentDescription = "Page $pageNumber",
            state = imageState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
