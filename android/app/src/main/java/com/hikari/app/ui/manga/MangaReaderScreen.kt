package com.hikari.app.ui.manga

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.hikari.app.MainActivity
import kotlinx.coroutines.launch
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
            is ReaderUiState.Syncing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Der Mac holt das Kapitel …", color = Color.White, fontSize = 16.sp)
                    Text(
                        if (s.total > 0) "${s.ready} von ${s.total} Seiten" else "Gleich geht es los",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (s.total > 0) s.ready.toFloat() / s.total else 0f },
                        color = MangaAccent, trackColor = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 14.dp).width(220.dp).height(4.dp),
                    )
                    Text("Bleib kurz hier — sobald alle Seiten da sind, öffnet sich das Kapitel von selbst.",
                        color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("hikari_manga_reader", Context.MODE_PRIVATE) }
    var settings by remember { mutableStateOf(MangaReaderSettings.decode(prefs.getString("settings", null))) }
    val update: (MangaReaderSettings) -> Unit = { s ->
        settings = s
        prefs.edit().putString("settings", s.encode()).apply()
    }
    val pageCount = pages.size + 1 // +1 = Kapitelende
    val pagerState = rememberPagerState(
        initialPage = (initialPage - 1).coerceIn(0, pages.size - 1),
        pageCount = { pageCount },
    )
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val failedPages = remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    var position by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { idx ->
            position = idx
            if (idx < pages.size) vm.savePosition(idx + 1)
            if (idx == pages.size - 1) vm.markChapterRead()
        }
    }

    // Neue Seite = nicht gezoomt (auch auf der Kapitelende-Seite, die selbst keinen Zoom meldet).
    LaunchedEffect(position) { zoomed = false }
    // Vom Streifen zurück zu Seiten: dort weitermachen, wo man im Streifen war.
    LaunchedEffect(settings.direction) {
        if (settings.direction != ReadDirection.VERTICAL && pagerState.currentPage != position) {
            pagerState.scrollToPage(position.coerceIn(0, pageCount - 1))
        }
    }

    fun turn(forward: Boolean) {
        if (settings.direction == ReadDirection.VERTICAL) return
        val target = pagerState.currentPage + if (forward) 1 else -1
        if (target !in 0 until pageCount || pagerState.isScrollInProgress) return
        scope.launch {
            if (settings.anim == PageAnim.NONE) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(
                target,
                animationSpec = tween(if (settings.anim == PageAnim.BOOK) 560 else 340, easing = FastOutSlowInEasing),
            )
        }
    }
    val onTap: (Float) -> Unit = { x ->
        when (tapAction(x, settings.direction, settings.tapTurn, chromeVisible)) {
            TapAction.NEXT -> turn(true)
            TapAction.PREV -> turn(false)
            TapAction.MENU -> chromeVisible = true
            TapAction.CLOSE_MENU -> chromeVisible = false
        }
    }

    // Lautstärketasten: leiser = weiter, lauter = zurück.
    val activity = ctx as? MainActivity
    val currentSettings by rememberUpdatedState(settings)
    DisposableEffect(activity) {
        val pager = object : MainActivity.VolumePager {
            override fun wantsVolumeKeys() = currentSettings.volumeKeys && currentSettings.direction != ReadDirection.VERTICAL
            override fun page(forward: Boolean) = turn(forward)
        }
        activity?.volumePager = pager
        onDispose { if (activity?.volumePager === pager) activity.volumePager = null }
    }

    val bg = when (settings.background) {
        "weiss" -> Color(0xFFF6F3EE)
        "grau" -> Color(0xFF2A2A2D)
        else -> Color.Black
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        if (settings.direction == ReadDirection.VERTICAL) {
            VerticalStrip(
                pages = pages,
                startIndex = position.coerceIn(0, pages.size - 1),
                imageUrl = { vm.pageImageUrl(baseUrl, it.id) },
                onPage = { idx ->
                    position = idx
                    vm.savePosition(idx + 1)
                    if (idx == pages.size - 1) vm.markChapterRead()
                },
                onTap = { chromeVisible = !chromeVisible },
                nextChapterId = nextChapterId,
                onNextChapter = { nextChapterId?.let(onOpenChapter) },
                onBack = onBack,
            )
        } else {
            HorizontalPager(
                state = pagerState,
                reverseLayout = settings.direction == ReadDirection.RTL,
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = if (settings.anim == PageAnim.NONE) snap() else tween(
                        if (settings.anim == PageAnim.BOOK) 480 else 300,
                        easing = FastOutSlowInEasing,
                    ),
                ),
                modifier = Modifier.fillMaxSize(),
            ) { pageIdx ->
                val anim = settings.anim
                val dir = settings.direction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(-pageIdx.toFloat())
                        .graphicsLayer {
                            val t = pageTransform(pageIdx, pagerState.currentPage + pagerState.currentPageOffsetFraction, size.width, dir, anim)
                            translationX = t.translationX
                            rotationY = t.rotationY
                            alpha = t.alpha
                            cameraDistance = 14f * density
                            transformOrigin = TransformOrigin(t.pivotX, 0.5f)
                        }
                        .background(bg)
                        .drawWithContent {
                            drawContent()
                            val t = pageTransform(pageIdx, pagerState.currentPage + pagerState.currentPageOffsetFraction, size.width, dir, anim)
                            if (t.shade > 0f) drawRect(Color.Black.copy(alpha = t.shade))
                        },
                ) {
                    if (pageIdx == pages.size) {
                        Box(
                            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures(onTap = { o -> onTap(o.x / size.width) })
                            },
                        ) {
                            ChapterEndPage(
                                nextChapterId = nextChapterId,
                                onNextChapter = { nextChapterId?.let(onOpenChapter) },
                                onBackToOverview = onBack,
                            )
                        }
                    } else {
                        val page = pages[pageIdx]
                        ZoomablePage(
                            pageImageUrl = vm.pageImageUrl(baseUrl, page.id),
                            pageNumber = page.pageNumber,
                            isCurrent = position == pageIdx,
                            onZoomChange = { zoomed = it },
                            onTap = onTap,
                            onError = { failedPages.value = failedPages.value + page.id },
                        )
                    }
                }
            }
        }
        ReaderChrome(
            visible = chromeVisible,
            currentPage = (position + 1).coerceAtMost(pages.size),
            totalPages = pages.size,
            missingCount = failedPages.value.size,
            onBack = onBack,
            onSettings = { showSettings = true },
            onSeek = { page ->
                scope.launch {
                    if (settings.direction != ReadDirection.VERTICAL) pagerState.scrollToPage((page - 1).coerceIn(0, pages.size - 1))
                }
            },
        )
    }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = Color(0xFF1C1B20)) {
            MangaReaderSettingsSheet(settings, update)
        }
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
