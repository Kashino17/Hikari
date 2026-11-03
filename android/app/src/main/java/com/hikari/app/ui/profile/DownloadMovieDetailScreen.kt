package com.hikari.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.data.db.PlaybackPositionEntity
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Eigenständige Detailseite eines heruntergeladenen Films.
 *
 * Online kommen Beschreibung und Metadaten aus `/videos/:id`, offline aus dem
 * lokalen Download-Eintrag. Abspielen geht immer, Herunterladen/Entfernen
 * direkt auf dieser Seite.
 */
@Composable
fun DownloadMovieDetailScreen(
    onBack: () -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    viewModel: DownloadDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localIds by viewModel.localIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val positions by viewModel.playbackPositions.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is DownloadDetailUiState.Loading -> CircularProgressIndicator(
                color = HikariAmber,
                modifier = Modifier.align(Alignment.Center),
            )
            is DownloadDetailUiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = HikariTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Zurück",
                    color = HikariAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            is DownloadDetailUiState.Series -> Unit
            is DownloadDetailUiState.Movie -> MovieDetailContent(
                movie = s.data,
                isLocal = localIds.contains(s.data.id),
                downloadProgress = downloadProgress[s.data.id],
                position = positions[s.data.id],
                actionError = actionError,
                onBack = onBack,
                onPlay = { onPlayVideo(s.data.id, s.data.title, "") },
                onDownload = { viewModel.downloadMovie(s.data) },
                onRemoveLocal = { viewModel.removeLocal(s.data.id) },
                onDismissError = viewModel::clearActionError,
            )
        }
    }
}

@Composable
private fun MovieDetailContent(
    movie: DownloadMovieDetail,
    isLocal: Boolean,
    downloadProgress: Float?,
    position: PlaybackPositionEntity?,
    actionError: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRemoveLocal: () -> Unit,
    onDismissError: () -> Unit,
) {
    val progress = if (movie.durationSeconds > 0) {
        (progressSeconds(position, movie.serverProgressSeconds) / movie.durationSeconds)
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            MovieHero(
                movie = movie,
                progress = progress,
                onBack = onBack,
                onPlay = onPlay,
            )
        }

        if (actionError != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HikariDanger.copy(alpha = 0.12f))
                        .border(
                            0.5.dp,
                            HikariDanger.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(onClick = onDismissError)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        actionError,
                        color = HikariDanger,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            DownloadAction(
                isLocal = isLocal,
                downloading = downloadProgress != null,
                progress = downloadProgress,
                onDownload = onDownload,
                onRemoveLocal = onRemoveLocal,
            )
        }

        if (!movie.description.isNullOrBlank()) {
            item {
                Text(
                    text = "Beschreibung",
                    color = HikariText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                )
            }
            item {
                Text(
                    text = movie.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(
                    "Details",
                    color = HikariText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                if (movie.year != null) DetailRow("Jahr", "${movie.year}")
                if (movie.durationSeconds > 0) {
                    DetailRow("Laufzeit", formatDuration(movie.durationSeconds))
                }
                DetailRow(
                    "Auf dem Gerät",
                    if (isLocal && movie.sizeBytes > 0) formatBytes(movie.sizeBytes)
                    else if (isLocal) "Ja"
                    else "Nein",
                )
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun MovieHero(
    movie: DownloadMovieDetail,
    progress: Float,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        FallbackArtwork(title = movie.title, showTitle = false)
        AsyncImage(
            model = movie.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            HikariBg.copy(alpha = 0.65f),
                            HikariBg,
                        ),
                    ),
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 40.dp, start = 10.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 32.sp,
            )
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Film",
                    color = HikariAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (movie.year != null) {
                    Text("${movie.year}", color = HikariTextFaint, fontSize = 12.sp)
                }
                if (movie.durationSeconds > 0) {
                    Text(
                        formatDuration(movie.durationSeconds),
                        color = HikariTextFaint,
                        fontSize = 12.sp,
                    )
                }
                if (movie.sizeBytes > 0) {
                    Text(
                        formatBytes(movie.sizeBytes),
                        color = HikariTextFaint,
                        fontSize = 12.sp,
                    )
                }
            }
            if (progress > 0f && progress < 0.95f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(HikariAmber),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (progress > 0f && progress < 0.95f) "Weiterschauen" else "Abspielen",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DownloadAction(
    isLocal: Boolean,
    downloading: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onRemoveLocal: () -> Unit,
) {
    val background = if (isLocal) HikariAmber.copy(alpha = 0.14f) else HikariSurface
    val border = if (isLocal) HikariAmber.copy(alpha = 0.5f) else HikariBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = !downloading) {
                if (isLocal) onRemoveLocal() else onDownload()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            downloading -> CircularProgressIndicator(
                color = HikariAmber,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            isLocal -> Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = HikariAmber,
                modifier = Modifier.size(18.dp),
            )
            else -> Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = HikariTextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = when {
                downloading -> "Wird heruntergeladen…${progress?.let { " ${(it * 100).toInt()}%" } ?: ""}"
                isLocal -> "Auf dem Gerät — zum Entfernen tippen"
                else -> "Auf dem Gerät speichern"
            },
            color = if (isLocal) HikariAmber else HikariText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = HikariTextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = HikariText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun progressSeconds(local: PlaybackPositionEntity?, serverSeconds: Float?): Float =
    if (local != null && local.positionMs > 0L) local.positionMs / 1000f
    else serverSeconds ?: 0f
