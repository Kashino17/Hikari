package com.hikari.app.ui.manga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.ui.manga.components.ChapterEndPage
import com.hikari.app.ui.manga.components.ReaderChrome
import com.hikari.app.ui.manga.components.ZoomablePage
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaReaderScreen(
    seriesId: String,
    chapterId: String,
    initialPage: Int,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit,
    vm: MangaReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId, chapterId) { vm.load(seriesId, chapterId) }
    val state by vm.uiState.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            vm.flushProgress()
            vm.stopPolling()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is ReaderUiState.Loading -> CenterMessage("Lade…")
            is ReaderUiState.Error -> CenterMessage(s.message, color = Color(0xFFFBBF24))
            is ReaderUiState.Syncing -> CenterMessage(
                "Wird gerade synchronisiert…\n\nHikari lädt das Kapitel von der Quelle.\nWenn die Bilder da sind, springt der Reader automatisch los.",
            )
            is ReaderUiState.Success -> {
                if (s.pages.isEmpty()) {
                    CenterMessage("Keine Seiten")
                } else {
                    ReaderContent(
                        pages = s.pages,
                        nextChapterId = s.nextChapterId,
                        seriesId = seriesId,
                        chapterId = chapterId,
                        initialPage = initialPage,
                        baseUrl = baseUrl,
                        onBack = onBack,
                        onOpenChapter = onOpenChapter,
                        vm = vm,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(
    pages: List<MangaPageDto>,
    nextChapterId: String?,
    seriesId: String,
    chapterId: String,
    initialPage: Int,
    baseUrl: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String) -> Unit,
    vm: MangaReaderViewModel,
) {
    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, pages.size - 1),
        pageCount = { pages.size + 1 },  // +1 sentinel = chapter-end
    )
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    val failedPages = remember { mutableStateOf<Set<String>>(emptySet()) }

    // Progress save (debounced inside VM) + mark-as-read on last actual page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { idx ->
            if (idx < pages.size) vm.savePosition(idx + 1)
            if (idx == pages.size - 1) vm.markChapterRead()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = true,   // RTL: forward = swipe left
            beyondViewportPageCount = 1,
            userScrollEnabled = pagerScrollEnabled,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { chromeVisible = !chromeVisible })
                },
        ) { pageIdx ->
            if (pageIdx == pages.size) {
                ChapterEndPage(
                    nextChapterId = nextChapterId,
                    onNextChapter = { nextChapterId?.let(onOpenChapter) },
                    onBackToOverview = onBack,
                )
            } else {
                val page = pages[pageIdx]
                ZoomablePage(
                    pageImageUrl = vm.pageImageUrl(baseUrl, page.id),
                    pageNumber = page.pageNumber,
                    pagerState = pagerState,
                    pageIdx = pageIdx,
                    onZoomChange = { isZoomed -> pagerScrollEnabled = !isZoomed },
                    onError = {
                        failedPages.value = failedPages.value + page.id
                    },
                )
            }
        }
        ReaderChrome(
            visible = chromeVisible,
            currentPage = (pagerState.currentPage + 1).coerceAtMost(pages.size),
            totalPages = pages.size,
            missingCount = failedPages.value.size,
            onBack = onBack,
        )
    }
}

@Composable
private fun CenterMessage(text: String, color: Color = Color.White.copy(alpha = 0.6f)) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text, color = color, fontSize = 14.sp)
        }
    }
}
