package com.hikari.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.player.LastFrameStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LastFrameEntryPoint {
    fun lastFrameStore(): LastFrameStore
}

/** Echte YouTube-Video-IDs: 11 Zeichen. Nur für alles andere liegt eine Datei auf dem Server. */
private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Vorschaubild eines Videos — für angefangene Videos das Bild der Stelle, an
 * der man aufgehört hat. Probiert der Reihe nach, bis eines lädt:
 *  1. das auf diesem Gerät festgehaltene letzte Bild (geht auch bei YouTube),
 *  2. das Standbild vom Server (nur für Videos, deren Datei dort liegt),
 *  3. das normale Vorschaubild.
 * Darunter liegt immer das FallbackArtwork, damit nie eine leere Fläche bleibt.
 */
@Composable
fun ResumeArtwork(
    video: LibraryVideoDto,
    modifier: Modifier = Modifier,
    showTitleFallback: Boolean = true,
    /** false = immer das normale Vorschaubild, auch wenn das Video angefangen ist. */
    resumeFrame: Boolean = true,
) {
    val ctx = LocalContext.current
    val store = remember {
        EntryPointAccessors.fromApplication(ctx, LastFrameEntryPoint::class.java).lastFrameStore()
    }
    val progress = video.progress_seconds ?: 0f
    val inProgress = resumeFrame && progress >= MIN_RESUME_SECONDS &&
        video.duration_seconds > 0 &&
        progress < video.duration_seconds * 0.95f

    val candidates = remember(video.id, video.thumbnail_url, progress.toInt(), resumeFrame) {
        buildList<Any> {
            if (inProgress) {
                store.frameFile(video.id)?.let { file ->
                    // Der Dateiname bleibt gleich — der Zeitstempel im Schlüssel sorgt dafür,
                    // dass ein neueres Standbild nicht hinter dem alten im Cache verschwindet.
                    add(
                        ImageRequest.Builder(ctx)
                            .data(file)
                            .memoryCacheKey("last-frame:${video.id}:${file.lastModified()}")
                            .build(),
                    )
                }
                if (!YOUTUBE_ID.matches(video.id)) add("/videos/${video.id}/frame?at=${progress.toInt()}")
            }
            video.thumbnail_url?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    var index by remember(candidates) { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        FallbackArtwork(title = video.title, showTitle = showTitleFallback)
        candidates.getOrNull(index)?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { if (index < candidates.lastIndex) index++ },
            )
        }
    }
}

/** Die ersten Sekunden sind oft Intro/Schwarzbild — dann lieber das normale Vorschaubild. */
private const val MIN_RESUME_SECONDS = 30f
