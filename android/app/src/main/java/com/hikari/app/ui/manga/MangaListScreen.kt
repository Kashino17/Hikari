package com.hikari.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.sync.SyncStatus
import com.hikari.app.ui.manga.components.MangaCard
import com.hikari.app.ui.manga.components.MangaHero
import com.hikari.app.ui.manga.components.MangaRow
import com.hikari.app.ui.manga.components.MangaSyncBanner
import com.hikari.app.ui.theme.HikariBg

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

    Column(modifier = Modifier.fillMaxSize().background(HikariBg).verticalScroll(rememberScrollState())) {
        if (syncStatus is SyncStatus.Active) {
            MangaSyncBanner((syncStatus as SyncStatus.Active).job)
        }
        when (val s = state) {
            is MangaListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text("Lade…", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            is MangaListUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Color(0xFFFBBF24), fontSize = 14.sp)
                }
            }
            is MangaListUiState.Success -> {
                if (s.series.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 100.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MANGA",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = "Noch keine Mangas",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = "Trigger den Sync im Tuning-Tab → System.",
                                color = Color.White.copy(alpha = 0.5f),
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
