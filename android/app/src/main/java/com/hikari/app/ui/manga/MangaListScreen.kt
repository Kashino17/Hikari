package com.hikari.app.ui.manga

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.sync.SyncStatus
import com.hikari.app.ui.manga.components.MangaCard
import com.hikari.app.ui.manga.components.MangaHero
import com.hikari.app.ui.manga.components.MangaRow
import com.hikari.app.ui.manga.components.MangaSyncBanner
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

@Composable
fun MangaListScreen(
    onSeriesClick: (seriesId: String) -> Unit,
    onContinueClick: (seriesId: String, chapterId: String, page: Int) -> Unit,
    vm: MangaListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()

    DisposableEffect(Unit) {
        vm.startSyncPolling()
        onDispose { vm.stopSyncPolling() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        if (syncStatus is SyncStatus.Active) {
            MangaSyncBanner((syncStatus as SyncStatus.Active).job)
        }
        when (val s = state) {
            is MangaListUiState.Loading -> MangaLoadingSkeleton()
            is MangaListUiState.Error -> MangaErrorState(
                message = s.message,
                onRetry = { vm.reload() },
            )
            is MangaListUiState.Success -> {
                if (s.series.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 100.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MANGA",
                                color = HikariTextFaint,
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = "Noch keine Mangas",
                                color = HikariText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = "Trigger den Sync im Tuning-Tab → System.",
                                color = HikariTextFaint,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                } else {
                    val firstCont = s.continueItems.firstOrNull()
                    val heroSeries = firstCont
                        ?.let { c -> s.series.find { it.id == c.seriesId } }
                        ?: s.series.first()
                    MangaHero(
                        series = heroSeries,
                        cont = firstCont,
                        coverUrl = heroSeries.coverPath?.let { vm.coverUrl(baseUrl, it) },
                        onCta = {
                            if (firstCont != null) {
                                onContinueClick(firstCont.seriesId, firstCont.chapterId, firstCont.pageNumber)
                            } else {
                                onSeriesClick(heroSeries.id)
                            }
                        },
                    )
                    if (s.continueItems.isNotEmpty()) {
                        MangaRow("Weiterlesen") {
                            items(s.continueItems) { c ->
                                val series = s.series.find { it.id == c.seriesId }
                                if (series != null) {
                                    MangaCard(
                                        series = series,
                                        coverUrl = series.coverPath?.let { vm.coverUrl(baseUrl, it) },
                                        onClick = { onSeriesClick(series.id) },
                                    )
                                }
                            }
                        }
                    }
                    MangaRow("Alle Mangas") {
                        items(s.series) { series ->
                            MangaCard(
                                series = series,
                                coverUrl = series.coverPath?.let { vm.coverUrl(baseUrl, it) },
                                onClick = { onSeriesClick(series.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pulsierendes Skeleton im Seiten-Layout: Hero-Rechteck + Kartenreihe. */
@Composable
private fun MangaLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(Modifier.fillMaxWidth().alpha(pulse)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(HikariSurfaceHigh),
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .width(110.dp)
                .height(15.dp)
                .clip(RoundedCornerShape(50))
                .background(HikariSurfaceHigh),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Column {
                    Box(
                        Modifier
                            .width(128.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HikariSurfaceHigh),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HikariSurfaceHigh),
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MangaErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = HikariTextFaint,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Mangas konnten nicht geladen werden",
            color = HikariText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            color = HikariTextFaint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary, contentColor = Color.Black),
            shape = RoundedCornerShape(50),
        ) {
            Text("Erneut versuchen", fontWeight = FontWeight.Bold)
        }
    }
}
