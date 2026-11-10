package com.hikari.app.ui.feed

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberCoroutineScope
import com.hikari.app.domain.feed.resolveDeepDive
import com.hikari.app.ui.feed.poster.SharePosterSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.hikari.app.domain.russian.RuAnswerCheck
import com.hikari.app.ui.russian.RuListenState
import com.hikari.app.ui.russian.RussianAudioPlayer
import com.hikari.app.ui.russian.RussianRecorder
import com.hikari.app.ui.russian.RussianSpeechRecognizer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.feed.ArtCultureCardItem
import com.hikari.app.domain.feed.BrainPuzzleCardItem
import com.hikari.app.domain.feed.BreathworkCardItem
import com.hikari.app.domain.feed.FinanceCardItem
import com.hikari.app.domain.feed.GeographyCardItem
import com.hikari.app.domain.feed.HistoryCardItem
import com.hikari.app.domain.feed.LanguageCardItem
import com.hikari.app.domain.feed.LearningLanguage
import com.hikari.app.domain.feed.MentalModelCardItem
import com.hikari.app.domain.feed.MindfulCard
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.ModuleRank
import com.hikari.app.domain.feed.PhilosophyCardItem
import com.hikari.app.domain.feed.QuoteCardItem
import com.hikari.app.domain.feed.ScienceCardItem
import com.hikari.app.domain.feed.SpeedMathCardItem
import com.hikari.app.domain.feed.VocabularyCardItem
import com.hikari.app.domain.feed.FeedEntry
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import com.hikari.app.domain.feed.RuAlphabetLogic
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Mindful Feed 2.0 (Reels / Shorts / TikTok Format).
 *
 * Jede Karte belegt exakt einen vollen Bildschirm (1 Slide = 1 Fokus-Einheit).
 * Vertikales Snapping zwischen den Karten: Maximale Konzentration, keine Ablenkung,
 * absolute Ruhe und Balance statt Reizüberflutung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel = hiltViewModel(),
    fullscreen: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    resetTick: Int = 0,
    bottomPadding: Dp = 100.dp,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = remember(bottomPadding, navBarBottom) {
        if (bottomPadding > 0.dp) bottomPadding + 10.dp else (navBarBottom + 16.dp)
    }
    val entries by vm.entries.collectAsState()
    val completedCardIds by vm.completedCards.collectAsState()
    val savedCardIds by vm.savedCardIds.collectAsState()
    val savedCards by vm.savedCards.collectAsState()
    val commuterMode by vm.commuterMode.collectAsState()
    val enabledModules by vm.enabledModules.collectAsState()
    val selectedLanguage by vm.selectedLanguage.collectAsState()
    val moduleRanks by vm.moduleRanks.collectAsState()
    val letterProgress by vm.letterProgress.collectAsState()
    val streak = remember(completedCardIds) { vm.getStreak() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showArchiveSheet by remember { mutableStateOf(false) }
    var sharePosterCard by remember { mutableStateOf<MindfulCard?>(null) }

    // Der Feed hat kein Ende: Beim Scrollen hängt das ViewModel den nächsten Schwung an.
    val pagerState = rememberPagerState(pageCount = { entries.size })
    var settledPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState, entries.isEmpty()) {
        snapshotFlow { pagerState.settledPage }.collect {
            settledPage = it
            vm.onShown(it)
        }
    }

    // Aktive Zeit: zählt nur, solange die App im Vordergrund ist, und je Seite höchstens zwei Minuten —
    // ein liegen gelassenes Handy soll keine Lernzeit sammeln.
    val lifecycleOwner = LocalLifecycleOwner.current
    val settledId = entries.getOrNull(settledPage)?.id
    LaunchedEffect(settledId) {
        if (settledId == null) return@LaunchedEffect
        var counted = 0
        while (counted < 120) {
            delay(5000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                vm.addActiveTime(settledPage, 5)
                counted += 5
            }
        }
    }

    // Neu gemischt (Einstellungen geändert): wieder oben anfangen.
    val firstId = entries.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId != null && pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    // Smart-Modus für unterwegs (Pendler- / Hands-Free-Modus):
    // Spielt 400ms nach dem Erreichen einer Vokabelkarte automatisch das Muttersprachler-Audio ab
    LaunchedEffect(settledPage, commuterMode) {
        val current = (entries.getOrNull(settledPage) as? FeedEntry.Card)?.card
        if (commuterMode && current is LanguageCardItem && !current.audioRes.isNullOrBlank()) {
            delay(400)
            vm.audio?.play(current.audioRes)
        }
    }

    LaunchedEffect(resetTick) {
        if (resetTick > 0 && entries.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg),
    ) {
        if (entries.isEmpty()) {
            if (enabledModules.isEmpty()) FeedEmptyState(onOpenSettings = { showSettingsSheet = true })
        } else {
            val flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.20f,
                snapAnimationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                flingBehavior = flingBehavior,
                key = { page -> entries.getOrNull(page)?.id ?: "page-$page" },
            ) { page ->
                val entry = entries.getOrNull(page) ?: return@VerticalPager
                val goNext: () -> Unit = {
                    coroutineScope.launch { pagerState.animateScrollToPage(page + 1) }
                }
                val chrome = FeedChrome(
                    completedToday = completedCardIds.size,
                    dailyGoal = vm.dailyGoal,
                    streak = streak,
                    isCommuterMode = commuterMode,
                    onToggleCommuterMode = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.toggleCommuterMode()
                    },
                    onOpenSettings = { showSettingsSheet = true },
                    onOpenArchive = { showArchiveSheet = true },
                    showSwipeHint = page == 0,
                    bottomPadding = effectiveBottomPadding,
                )
                when (entry) {
                    is FeedEntry.Card -> {
                        val card = entry.card
                        val isDone = card.id in completedCardIds
                        val isSaved = card.id in savedCardIds

                        // 2D-Gesten: Horizontaler Pager für Deep Dive (Page 0 = Hauptkarte, Page 1 = Deep Dive)
                        val horizontalPagerState = rememberPagerState(pageCount = { 2 })

                        HorizontalPager(
                            state = horizontalPagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { hPage ->
                            if (hPage == 0) {
                                FeedCardSlide(
                                    card = card,
                                    chrome = chrome,
                                    isDone = isDone,
                                    isSaved = isSaved,
                                    onToggleSave = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.toggleSavedCard(card.id)
                                    },
                                    onOpenDeepDive = {
                                        coroutineScope.launch {
                                            horizontalPagerState.animateScrollToPage(1)
                                        }
                                    },
                                    audioPlayer = vm.audio,
                                    onRecordPractice = { phraseId, correct -> vm.recordRussianPractice(phraseId, correct) },
                                    onNavigate = onNavigate,
                                    onDone = { vm.markCardCompleted(card.id) },
                                    onSharePoster = { sharePosterCard = card },
                                )
                            } else {
                                DeepDiveSlide(
                                    card = card,
                                    audioPlayer = vm.audio,
                                    onBack = {
                                        coroutineScope.launch {
                                            horizontalPagerState.animateScrollToPage(0)
                                        }
                                    },
                                    onNavigate = onNavigate,
                                    bottomPadding = effectiveBottomPadding,
                                )
                            }
                        }
                    }
                    is FeedEntry.Game -> FeedGameSlide(
                        game = entry.game,
                        chrome = chrome,
                        isDone = entry.id in completedCardIds,
                        active = page == settledPage,
                        audio = vm.audio,
                        letterProgress = letterProgress,
                        showTranslit = RuAlphabetLogic.mastered(letterProgress) < RuAlphabetLogic.READS_CYRILLIC,
                        autoPlay = commuterMode,
                        onFinish = { correct -> vm.finishGame(entry.game, correct) },
                        onNext = goNext,
                    )
                    is FeedEntry.Milestone -> MindfulCompletionSlide(
                        streak = streak,
                        completedCount = completedCardIds.size,
                        totalCount = vm.dailyGoal,
                        onContinue = goNext,
                        onOpenSettings = { showSettingsSheet = true },
                        bottomPadding = effectiveBottomPadding,
                    )
                }
            }
        }
    }

    if (showArchiveSheet) {
        KnowledgeArchiveSheet(
            savedCards = savedCards,
            onToggleSave = { cardId ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.toggleSavedCard(cardId)
            },
            audioPlayer = vm.audio,
            onSharePoster = { card -> sharePosterCard = card },
            onDismiss = { showArchiveSheet = false },
        )
    }

    sharePosterCard?.let { card ->
        SharePosterSheet(
            card = card,
            onDismiss = { sharePosterCard = null },
        )
    }

    if (showSettingsSheet) {
        FeedSettingsSheet(
            enabledModules = enabledModules,
            selectedLanguage = selectedLanguage,
            moduleRanks = moduleRanks,
            streak = streak,
            completedCount = completedCardIds.size,
            totalCount = vm.dailyGoal,
            feedSecondsToday = remember(showSettingsSheet) { vm.feedSecondsToday() },
            onToggleModule = { module, enabled -> vm.toggleModule(module, enabled) },
            onSetModuleRank = { module, rank -> vm.setModuleRank(module, rank) },
            onSelectLanguage = { vm.setLearningLanguage(it) },
            onResetDaily = { vm.resetDailyProgress() },
            onResetLanguage = { vm.resetLanguageProgress() },
            onDismiss = { showSettingsSheet = false },
        )
    }
}

/** Was jede Feed-Seite oben und unten gleich zeigt: Tagesziel, Serie, die runden Knöpfe. */
internal data class FeedChrome(
    val completedToday: Int,
    val dailyGoal: Int,
    val streak: Int,
    val isCommuterMode: Boolean,
    val onToggleCommuterMode: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenArchive: () -> Unit,
    val showSwipeHint: Boolean,
    val bottomPadding: Dp,
)

internal fun cardAmbientColor(card: MindfulCard): Color = when (card) {
    is QuoteCardItem -> Color(0xFFF59E0B)
    is BrainPuzzleCardItem -> Color(0xFF10B981)
    is LanguageCardItem -> Color(0xFF3B82F6)
    is BreathworkCardItem -> Color(0xFF06B6D4)
    is HistoryCardItem -> Color(0xFFD97706)
    is MentalModelCardItem -> Color(0xFF8B5CF6)
    is ScienceCardItem -> Color(0xFF6366F1)
    is FinanceCardItem -> Color(0xFFEAB308)
    is GeographyCardItem -> Color(0xFF14B8A6)
    is SpeedMathCardItem -> Color(0xFFF43F5E)
    is ArtCultureCardItem -> Color(0xFFEC4899)
    is VocabularyCardItem -> Color(0xFFF59E0B)
    is PhilosophyCardItem -> Color(0xFF7C3AED)
}

/** Eine Wissenskarte im gemeinsamen Rahmen. */
@Composable
private fun FeedCardSlide(
    card: MindfulCard,
    chrome: FeedChrome,
    isDone: Boolean,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDeepDive: () -> Unit,
    audioPlayer: RussianAudioPlayer? = null,
    onRecordPractice: (String?, Boolean) -> Unit = { _, _ -> },
    onNavigate: (String) -> Unit = {},
    onDone: () -> Unit,
    onSharePoster: () -> Unit = {},
) {
    FeedSlideFrame(
        chrome = chrome,
        slideKey = card.id,
        ambientColor = cardAmbientColor(card),
        icon = getCardIcon(card),
        label = getCardCategoryLabel(card),
        topFocus = card.rank == ModuleRank.RANK_3,
        isDone = isDone,
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onOpenDeepDive = onOpenDeepDive,
        onDone = onDone,
        onSharePoster = onSharePoster,
    ) {
        when (card) {
            is QuoteCardItem -> QuoteSlideContent(card, isDone, onDone)
            is BrainPuzzleCardItem -> BrainPuzzleSlideContent(card, isDone, onDone)
            is LanguageCardItem -> LanguageSlideContent(
                item = card,
                audioPlayer = audioPlayer,
                onRecordPractice = onRecordPractice,
                onNavigate = onNavigate,
                isDone = isDone,
                onDone = onDone,
            )
            is HistoryCardItem -> HistorySlideContent(card, isDone, onDone)
            is MentalModelCardItem -> MentalModelSlideContent(card, isDone, onDone)
            is BreathworkCardItem -> BreathworkSlideContent(card, isDone, onDone)
            is ScienceCardItem -> ScienceSlideContent(card, isDone, onDone)
            is FinanceCardItem -> FinanceSlideContent(card, isDone, onDone)
            is GeographyCardItem -> GeographySlideContent(card, isDone, onDone)
            is SpeedMathCardItem -> SpeedMathCardSlideContent(card, isDone, onDone)
            is ArtCultureCardItem -> ArtCultureSlideContent(card, isDone, onDone)
            is VocabularyCardItem -> VocabularySlideContent(card, isDone, onDone)
            is PhilosophyCardItem -> PhilosophySlideContent(card, isDone, onDone)
        }
    }
}

// ── Einzelne Slide (Full-Screen Focus, Zero-Scroll-Garantie) ──────────────────

/**
 * Der gemeinsame Rahmen jeder Feed-Seite: oben Tagesziel und Knöpfe, in der Mitte der Inhalt,
 * unten Vertiefung, Teilen und Erledigt. Spiele lassen die unteren Knöpfe weg.
 */
@Composable
internal fun FeedSlideFrame(
    chrome: FeedChrome,
    slideKey: String,
    ambientColor: Color,
    icon: ImageVector,
    label: String,
    topFocus: Boolean,
    isDone: Boolean,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onOpenDeepDive: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onSharePoster: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val streak = chrome.streak
    val isCommuterMode = chrome.isCommuterMode
    val onToggleCommuterMode = chrome.onToggleCommuterMode
    val onOpenSettings = chrome.onOpenSettings
    val onOpenArchive = chrome.onOpenArchive
    val showSwipeHint = chrome.showSwipeHint
    val bottomPadding = chrome.bottomPadding
    val goalReached = chrome.completedToday >= chrome.dailyGoal

    var showSavePop by remember { mutableStateOf(false) }
    LaunchedEffect(isSaved) {
        if (isSaved) {
            showSavePop = true
            delay(850)
            showSavePop = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(slideKey) {
                detectTapGestures(
                    onDoubleTap = {
                        onToggleSave?.invoke()
                    },
                )
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(ambientColor.copy(alpha = 0.10f), Color.Transparent),
                    radius = 950f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = bottomPadding),
        ) {
            // ── Top Story Progress Bar (Ganz oben, dezent & präzise) ───────────────
            val progress by animateFloatAsState(
                targetValue = (chrome.completedToday.toFloat() / chrome.dailyGoal).coerceIn(0f, 1f),
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "feed-slide-progress",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = HikariAmber,
                trackColor = Color.White.copy(alpha = 0.10f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            Spacer(Modifier.height(12.dp))

            // ── Top Bar: HIKARI Brand + Slide Indicator (Left) & Minimal Controls (Right) ────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Linke Seite: Brand "HIKARI" in Gold + Zähler
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "HIKARI",
                        color = HikariAmber,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "·",
                        color = Color.White.copy(alpha = 0.30f),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (goalReached) "${chrome.completedToday} heute · Ziel geschafft" else "${chrome.completedToday} / ${chrome.dailyGoal} heute",
                        color = HikariTextFaint,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Rechte Seite: 4 dezente Icon-Buttons (32dp, Vektoren, Null Emojis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 1. Commuter Mode (Hands-Free) Toggle
                    Surface(
                        onClick = onToggleCommuterMode,
                        shape = CircleShape,
                        color = if (isCommuterMode) HikariAmber.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(0.6.dp, if (isCommuterMode) HikariAmber else Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Headphones,
                                contentDescription = "Pendler-Modus",
                                tint = if (isCommuterMode) HikariAmber else HikariTextMuted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // 2. Mein Gehirn Wissens-Archiv
                    Surface(
                        onClick = onOpenArchive,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmarks,
                                contentDescription = "Mein Gehirn",
                                tint = HikariTextMuted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // 3. Streak Pill
                    Surface(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalFireDepartment,
                                contentDescription = null,
                                tint = HikariAmber,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "$streak",
                                color = HikariAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // 4. Settings Button
                    Surface(
                        onClick = onOpenSettings,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Feed-Einstellungen",
                                tint = HikariTextMuted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Zeile 2: Kategorie-Chip (Vollständig entzerrt, eigene prominente Zeile) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Surface(
                    color = ambientColor.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.6.dp, ambientColor.copy(alpha = 0.35f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ambientColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        if (topFocus) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "·  TOP-FOKUS",
                                color = HikariAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Center Content Box (Fokus auf genau 1 Thema, Zero-Scroll-Garantie) ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Passt der Inhalt, steht er mittig; ist er länger als der Platz, lässt er sich scrollen.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Ultra-Sleek Cupertino Bottom Bar (Deep Dive + Actions in 1 Row) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Linke Seite: Deep Dive Button
                if (onOpenDeepDive == null) Spacer(Modifier.height(38.dp)) else Surface(
                    onClick = onOpenDeepDive,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = HikariAmber,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Deep Dive",
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp,
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }

                // Rechte Seite: 2 minimalistische 38dp Frosted Glass Buttons (Teilen, Gelernt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 1. Poster / Teilen Button
                    if (onSharePoster != null) Surface(
                        onClick = onSharePoster,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Als Poster teilen",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // 2. Merken — legt die Karte in »Mein Gehirn« (geht auch per Doppeltipp)
                    if (onToggleSave != null) Surface(
                        onClick = onToggleSave,
                        shape = CircleShape,
                        color = if (isSaved) HikariAmber.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.6.dp, if (isSaved) HikariAmber else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Gemerkt" else "Merken",
                                tint = if (isSaved) HikariAmber else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    // 3. Gelernt / Erledigt Button
                    if (onDone != null) Surface(
                        onClick = onDone,
                        shape = CircleShape,
                        color = if (isDone) Color(0xFF10B981).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.6.dp, if (isDone) Color(0xFF10B981) else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Check,
                                contentDescription = if (isDone) "Gelernt" else "Lernen",
                                tint = if (isDone) Color(0xFF34D399) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Double-Tap Animated Pop Feedback ─────────────────────────────────
        AnimatedVisibility(
            visible = showSavePop,
            enter = fadeIn(tween(150)) + androidx.compose.animation.scaleIn(tween(250)),
            exit = androidx.compose.animation.fadeOut(tween(300)) + androidx.compose.animation.scaleOut(tween(300)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.80f),
                border = BorderStroke(1.5.dp, HikariAmber),
                modifier = Modifier.size(92.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Bookmarks,
                            contentDescription = null,
                            tint = HikariAmber,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("Gemerkt", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Swipe Up Hint (nur auf erster Slide) ──────────────────────────────
        if (showSwipeHint) {
            val infiniteTransition = rememberInfiniteTransition(label = "swipe-hint")
            val translateY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "swipe-offset",
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = translateY.dp)
                    .padding(bottom = bottomPadding + 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Nach oben wischen",
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                )
            }
        }
    }
}


private fun getCardIcon(card: MindfulCard): ImageVector = when (card) {
    is QuoteCardItem -> Icons.Outlined.FormatQuote
    is BrainPuzzleCardItem -> Icons.Outlined.Psychology
    is LanguageCardItem -> Icons.Outlined.Translate
    is HistoryCardItem -> Icons.Outlined.AccountBalance
    is MentalModelCardItem -> Icons.Outlined.Lightbulb
    is BreathworkCardItem -> Icons.Outlined.Air
    is ScienceCardItem -> Icons.Outlined.Science
    is FinanceCardItem -> Icons.Outlined.MonetizationOn
    is GeographyCardItem -> Icons.Outlined.Public
    is SpeedMathCardItem -> Icons.Outlined.Calculate
    is ArtCultureCardItem -> Icons.Outlined.Palette
    is VocabularyCardItem -> Icons.Outlined.MenuBook
    is PhilosophyCardItem -> Icons.Outlined.Balance
}

private fun getCardCategoryLabel(card: MindfulCard): String = when (card) {
    is QuoteCardItem -> "LEBENSWEISHEIT"
    is BrainPuzzleCardItem -> card.category.uppercase()
    is LanguageCardItem -> card.language.title.uppercase()
    is HistoryCardItem -> "GESCHICHTE"
    is MentalModelCardItem -> "DENKMODELL"
    is BreathworkCardItem -> "ATEMÜBUNG"
    is ScienceCardItem -> "WISSENSCHAFT"
    is FinanceCardItem -> "FINANZEN"
    is GeographyCardItem -> "WELTATLAS"
    is SpeedMathCardItem -> "KOPFRECHNEN"
    is ArtCultureCardItem -> "KUNST & KULTUR"
    is VocabularyCardItem -> "WORTSCHATZ"
    is PhilosophyCardItem -> "ETHIK-DILEMMA"
}

// ── Slide Contents (Fokussiert, zen, bildschirmfüllend) ─────────────────────────

@Composable
private fun QuoteSlideContent(item: QuoteCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("“", color = HikariAmber.copy(alpha = 0.5f), fontSize = 64.sp, fontWeight = FontWeight.Bold)

        Text(
            text = "„${item.quote}“",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            lineHeight = 31.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            color = HikariSurfaceHigh.copy(alpha = 0.6f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, HikariBorderStrong),
        ) {
            Text(
                text = "— ${item.author}  ·  ${item.contextEra}",
                color = HikariAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        // Reflexions-Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HikariCardBg.copy(alpha = 0.85f))
                .border(0.5.dp, HikariBorder, RoundedCornerShape(16.dp))
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Reflexionsimpuls für heute",
                        color = HikariAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.reflectionPrompt,
                    color = HikariText,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun BrainPuzzleSlideContent(item: BrainPuzzleCardItem, isDone: Boolean, onDone: () -> Unit) {
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    val answered = selectedOption != null

    Column(modifier = Modifier.fillMaxWidth()) {
        // Titel und Fähigkeit untereinander — nebeneinander quetschen sich lange Texte.
        Text(
            item.title.uppercase(),
            color = HikariAmber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        if (item.brainRegionTrained.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = Color(0xFFA7F3D0),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Trainiert: ${item.brainRegionTrained}",
                    color = Color(0xFFA7F3D0),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = item.question,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(20.dp))

        // 4 Große Touch-Optionen
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item.options.forEachIndexed { index, opt ->
                val isCorrect = index == item.correctIndex
                val isSelected = selectedOption == index
                val bgColor = when {
                    !answered -> HikariCardBg
                    isCorrect -> Color(0xFF065F46)
                    isSelected -> Color(0xFF991B1B)
                    else -> HikariCardBg.copy(alpha = 0.4f)
                }
                val borderColor = when {
                    !answered -> HikariBorderStrong
                    isCorrect -> Color(0xFF10B981)
                    isSelected -> Color(0xFFEF4444)
                    else -> HikariBorder
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .clickable(enabled = !answered) {
                            selectedOption = index
                            onDone()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = opt,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected || (answered && isCorrect)) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (answered) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCorrect) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Richtig",
                                        color = Color(0xFF34D399),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = null,
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Falsch",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (answered) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariSurfaceHigh)
                    .padding(14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = HikariAmber,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Auflösung:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.explanation, color = HikariText, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun AudiovisualEqualizer(
    isActive: Boolean,
    tint: Color = HikariAmber,
    barCount: Int = 5,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val animFractions = List(barCount) { index ->
        val duration = 300 + (index * 110) % 280
        val minH = 0.22f
        val maxH = when (index % 4) {
            0 -> 1.0f
            1 -> 0.65f
            2 -> 0.90f
            else -> 0.50f
        }
        if (isActive) {
            infiniteTransition.animateFloat(
                initialValue = minH,
                targetValue = maxH,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar_$index",
            ).value
        } else {
            remember { mutableFloatStateOf(minH) }.floatValue
        }
    }

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        animFractions.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction = frac)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(tint),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSlideContent(
    item: LanguageCardItem,
    audioPlayer: RussianAudioPlayer?,
    onRecordPractice: (String?, Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    isDone: Boolean,
    onDone: () -> Unit,
) {
    var revealed by remember(item.id) { mutableStateOf(false) }
    var selectedReply by remember(item.id) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val recorder = remember(item.id) { RussianRecorder(context) }
    val recognizer = remember(item.id) { RussianSpeechRecognizer(context) }
    val speechState by recognizer.state.collectAsState()
    val playingTrack = audioPlayer?.playing?.collectAsState()?.value

    var isRecording by remember(item.id) { mutableStateOf(false) }
    var hasRecording by remember(item.id) { mutableStateOf(false) }
    var takeStamp by remember(item.id) { mutableStateOf(0L) }
    var isComparing by remember(item.id) { mutableStateOf(false) }
    var speechScore by remember(item.id) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var speechAttempts by remember(item.id) { mutableIntStateOf(0) }
    // Zwischen "Fertig"-Tipp und Ergebnis (bis ~2,5 s): Knopf sperren, Status zeigen.
    var evaluating by remember(item.id) { mutableStateOf(false) }
    val speechHaptic = LocalHapticFeedback.current
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 0 = Aussprache & Audio, 1 = Mini-Dialog (Zero-Scroll Segment-Wechsel)
    var selectedSegment by remember(item.id) { mutableIntStateOf(0) }

    DisposableEffect(item.id) {
        onDispose {
            runCatching { recorder.stop() }
            runCatching { recognizer.destroy() }
            audioPlayer?.stop()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val act = pendingMicAction
            pendingMicAction = null
            act?.invoke()
        } else {
            pendingMicAction = null
        }
    }

    fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMicAction = action
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleRecord() {
        withMic {
            if (isRecording) {
                recorder.stop()
                isRecording = false
                hasRecording = true
                takeStamp = System.currentTimeMillis()
                isComparing = true
                // Sofort A/B-Vergleich abspielen: erst deine Stimme, danach Muttersprachler
                audioPlayer?.playFile(recorder.file) {
                    val audioRes = item.audioRes
                    if (!audioRes.isNullOrBlank()) {
                        audioPlayer.play(audioRes) {
                            isComparing = false
                        }
                    } else {
                        isComparing = false
                    }
                }
                onRecordPractice(null, false) // Eigenaufnahme vergleichen ist Üben, kein Fehlversuch
            } else {
                audioPlayer?.stop()
                recognizer.stopListening()
                isRecording = recorder.start()
            }
        }
    }

    fun toggleSpeechCheck() {
        withMic {
            if (speechState is RuListenState.Listening || speechState is RuListenState.Partial) {
                evaluating = true
                speechHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                recognizer.stopListening()
            } else {
                audioPlayer?.stop()
                speechScore = null
                if (isRecording) {
                    recorder.stop()
                    isRecording = false
                }
                recognizer.reset()
                recognizer.start()
            }
        }
    }

    LaunchedEffect(speechState) {
        if (speechState is RuListenState.Done || speechState is RuListenState.Failed) evaluating = false
        when (val s = speechState) {
            is RuListenState.Done -> {
                speechHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val sc = RuAnswerCheck.speech(item.foreignWord, s.hypotheses)
                speechScore = sc
                speechAttempts++
                if (sc.passed) {
                    onRecordPractice(item.phraseId, true)
                    onDone()
                }
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 2-Segment Control (Zero-Scroll Garantie: Aussprache vs Mini-Dialog) ──
        if (item.dialogueReplies.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val isTab0 = selectedSegment == 0
                Surface(
                    onClick = { selectedSegment = 0 },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isTab0) HikariAmber.copy(alpha = 0.22f) else Color.Transparent,
                    border = if (isTab0) BorderStroke(0.5.dp, HikariAmber.copy(alpha = 0.6f)) else null,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (isTab0) HikariAmber else HikariTextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Aussprache & Audio",
                            color = if (isTab0) HikariAmber else HikariTextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }

                val isTab1 = selectedSegment == 1
                Surface(
                    onClick = { selectedSegment = 1 },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isTab1) HikariAmber.copy(alpha = 0.22f) else Color.Transparent,
                    border = if (isTab1) BorderStroke(0.5.dp, HikariAmber.copy(alpha = 0.6f)) else null,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Forum,
                            contentDescription = null,
                            tint = if (isTab1) HikariAmber else HikariTextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Mini-Dialog",
                            color = if (isTab1) HikariAmber else HikariTextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (selectedSegment == 0 || item.dialogueReplies.isEmpty()) {
            // ── SEGMENT 0: AUSSPRACHE, AUDIO & SHADOWING ──

            // 1. Kurs-Header & Fortschritt
            if (item.dayNumber != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = HikariAmber.copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, HikariAmber.copy(alpha = 0.40f)),
                        ) {
                            Text(
                                text = item.language.shortCode,
                                color = HikariAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (item.dayNumber != null) "Lektion ${item.dayNumber} · ${item.dayTitle ?: item.language.title}" else item.language.title,
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (item.isDueReview || item.isNewPhrase) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HikariAmber.copy(alpha = 0.20f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = if (item.isNewPhrase) "NEU" else "SRS",
                                    color = HikariAmber,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = HikariAmber,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${item.currentStreak} d",
                            color = HikariAmber,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // 2. Elegante Vokabel-Karte (Kompakt für Zero-Scroll, Didaktisch sauber)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F2027), Color(0xFF1B3039), Color(0xFF244452)),
                        ),
                    )
                    .border(1.dp, Color(0xFF38EF7D).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val russian = item.language == com.hikari.app.domain.feed.LearningLanguage.RUSSIAN
                    if (russian) {
                        // Russisch: Umschrift Wort für Wort mit Bedeutung, Kyrillisch dezent.
                        com.hikari.app.ui.russian.RuInterlinear(
                            com.hikari.app.domain.russian.RussianPhonetics.fromDisplay(item.foreignWord),
                            size = 24.sp,
                            latinColor = Color.White,
                            latinWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = item.foreignWord,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    if (!russian) Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    ) {
                        Text(
                            text = "[${item.phonetic}]",
                            color = HikariAmber,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = item.nativeTranslation,
                        color = Color(0xFFA7F3D0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    if (!item.literalTranslation.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Wörtlich: „${item.literalTranslation}“",
                            color = HikariTextMuted,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }

                    // Kein Beispiel, das nur das Wort selbst wiederholt (z. B. »десять« bei Zahlen).
                    val sameAsWord = com.hikari.app.domain.russian.RuLessonBuilder.norm(item.exampleForeign) ==
                        com.hikari.app.domain.russian.RuLessonBuilder.norm(item.foreignWord)
                    if (item.exampleForeign.isNotBlank() && !sameAsWord) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.30f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                                if (russian) {
                                    com.hikari.app.ui.russian.RuInterlinear(
                                        com.hikari.app.domain.russian.RussianPhonetics.fromDisplay(item.exampleForeign),
                                        modifier = Modifier.fillMaxWidth(),
                                        size = 14.sp,
                                        latinColor = Color.White.copy(alpha = 0.92f),
                                    )
                                } else {
                                    Text(
                                        text = "„${item.exampleForeign}“",
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Text(
                                    text = item.exampleTranslation,
                                    color = HikariTextMuted,
                                    fontSize = 10.5.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // 3. Muttersprachler-Audio mit Equalizer
            if (!item.audioRes.isNullOrBlank() && audioPlayer != null) {
                val audioRes = item.audioRes
                val isPlayingNormal = playingTrack == audioRes
                val isPlayingSlow = playingTrack == "${audioRes}_slow"
                val isAnyPlaying = isPlayingNormal || isPlayingSlow

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HikariSurfaceHigh)
                        .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = {
                            if (isPlayingNormal) audioPlayer.stop() else audioPlayer.play(audioRes)
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isPlayingNormal) HikariAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isPlayingNormal) HikariAmber else Color.White.copy(alpha = 0.12f),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isPlayingNormal) Icons.Default.Pause else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = "Anhören",
                                tint = if (isPlayingNormal) HikariAmber else Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingNormal) "Spielt..." else "Muttersprachler",
                                color = if (isPlayingNormal) HikariAmber else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (isPlayingSlow) audioPlayer.stop() else audioPlayer.play(audioRes, slow = true)
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isPlayingSlow) HikariAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isPlayingSlow) HikariAmber else Color.White.copy(alpha = 0.12f),
                        ),
                        modifier = Modifier.weight(0.85f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isPlayingSlow) Icons.Default.Pause else Icons.Outlined.SlowMotionVideo,
                                contentDescription = "Langsam",
                                tint = if (isPlayingSlow) HikariAmber else Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (isPlayingSlow) "0.7× ..." else "0.7× Langsam",
                                color = if (isPlayingSlow) HikariAmber else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Audiovisual Equalizer Bars
                    AudiovisualEqualizer(
                        isActive = isAnyPlaying,
                        tint = HikariAmber,
                        barCount = 4,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }

            // 4. Aussprache & Sprach-Praxis Studio
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariCardBg)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // KI Aussprache-Prüfung Button (Primary Action)
                val isListening = speechState is RuListenState.Listening || speechState is RuListenState.Partial
                val listeningPulse by animateFloatAsState(if (isListening) 1.02f else 1f, tween(250), label = "pulse")
                Surface(
                    onClick = { if (!evaluating) toggleSpeechCheck() },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isListening) HikariAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        if (isListening) HikariAmber else Color.White.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(listeningPulse),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isListening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                            contentDescription = if (isListening) "Fertigstellen & Prüfen" else "KI-Prüfung",
                            tint = if (isListening) HikariAmber else Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                evaluating -> "Wird ausgewertet …"
                                isListening -> "Ich höre zu … (Tippen zum Fertigstellen)"
                                speechScore != null -> "Aussprache erneut prüfen"
                                else -> "Aussprache mit KI prüfen"
                            },
                            color = if (isListening) HikariAmber else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Live Partial Text
                if (speechState is RuListenState.Partial) {
                    val partial = (speechState as RuListenState.Partial).text
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "»$partial« (Tippen zum Fertigstellen)",
                        color = HikariAmber,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                }

                // Fehlerhinweis bei Spracherkennung
                (speechState as? RuListenState.Failed)?.let { failed ->
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = failed.message + if (failed.missingLanguage) {
                            " Lade in den Android-Einstellungen unter »Spracherkennung« Russisch als Offline-Sprache."
                        } else {
                            ""
                        },
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                // Score Card mit Wort-für-Wort Hervorhebung
                speechScore?.let { sc ->
                    Spacer(Modifier.height(8.dp))
                    com.hikari.app.ui.russian.RuSpeechScoreCard(sc, xpHint = "+15 XP", targetRu = com.hikari.app.domain.russian.RussianPhonetics.fromDisplay(item.foreignWord))
                }

                val clip = item.audioRes
                if (hasRecording && !isRecording && !clip.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    com.hikari.app.ui.russian.RuProsodyCard(clip, recorder.file, takeStamp)
                    Spacer(Modifier.height(8.dp))
                    com.hikari.app.ui.russian.RuPhonemeCard(clip, recorder.file, takeStamp)
                }

                Spacer(Modifier.height(8.dp))

                // Shadowing / Audio-Vergleich Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { toggleRecord() },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isRecording) Color(0xFFDC2626) else HikariSurfaceHigh,
                        border = BorderStroke(
                            0.5.dp,
                            if (isRecording) Color(0xFFEF4444) else HikariBorderStrong,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isRecording) Icons.Outlined.Stop else Icons.Outlined.GraphicEq,
                                contentDescription = "Aufnahme",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isRecording) "Stopp & Vergleichen" else "Selbst aufnehmen",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (hasRecording && !isRecording) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                audioPlayer?.stop()
                                audioPlayer?.playFile(recorder.file)
                            },
                            shape = CircleShape,
                            color = HikariSurfaceHigh,
                            border = BorderStroke(0.5.dp, HikariBorderStrong),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Outlined.Person,
                                    contentDescription = "Meine Stimme",
                                    tint = HikariText,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))
                        Surface(
                            onClick = {
                                audioPlayer?.stop()
                                isComparing = true
                                audioPlayer?.playFile(recorder.file) {
                                    val audioRes = item.audioRes
                                    if (!audioRes.isNullOrBlank()) {
                                        audioPlayer.play(audioRes) {
                                            isComparing = false
                                        }
                                    } else {
                                        isComparing = false
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = if (isComparing) HikariAmber.copy(alpha = 0.25f) else HikariSurfaceHigh,
                            border = BorderStroke(1.dp, if (isComparing) HikariAmber else HikariBorderStrong),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Outlined.CompareArrows,
                                    contentDescription = "A/B-Vergleich",
                                    tint = if (isComparing) HikariAmber else HikariText,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 5. Kurs-Shortcut
            if (item.dayNumber != null) {
                Surface(
                    onClick = {
                        onNavigate(if (item.isDueReview) "russian/review" else "russian/lesson/${item.dayNumber}")
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = HikariAmber.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = item.language.shortCode,
                                    color = HikariAmber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = if (item.isDueReview) "SRS-Wiederholung im Kurs starten" else "Zur ganzen Lektion ${item.dayNumber}",
                                color = HikariTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = HikariAmber,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        } else {
            // ── SEGMENT 1: MINI-DIALOG SZENARIO ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HikariSurfaceHigh)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.QuestionAnswer,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Szenario: ${item.dialogueScenario}",
                        color = HikariAmber,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.dialoguePrompt,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.dialogueReplies.forEachIndexed { i, reply ->
                        val isChosen = selectedReply == i
                        val isCorrect = i == item.correctReplyIndex
                        val btnBg = when {
                            selectedReply == null -> Color.White.copy(alpha = 0.06f)
                            isCorrect -> Color(0xFF065F46)
                            isChosen -> Color(0xFF991B1B)
                            else -> Color.White.copy(alpha = 0.03f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(btnBg)
                                .border(
                                    0.5.dp,
                                    if (isChosen && isCorrect) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    if (selectedReply == null) onRecordPractice(null, false)
                                    selectedReply = i
                                    if (isCorrect) onDone()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(reply, color = Color.White, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BreathworkSlideContent(item: BreathworkCardItem, isDone: Boolean, onDone: () -> Unit) {
    var active by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Bereit") }
    var secondsRemaining by remember { mutableIntStateOf(4) }

    var phaseSeconds by remember { mutableIntStateOf(item.inhaleSeconds) }
    // Beim Anhalten bleibt der Kreis dort stehen, wo die letzte Bewegung endete.
    var orbTarget by remember { mutableStateOf(1f) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // Phasen mit 0 Sekunden (z. B. kein Anhalten) fallen weg.
        val phases = listOf(
            "Einatmen" to item.inhaleSeconds, "Halten" to item.holdInSeconds,
            "Ausatmen" to item.exhaleSeconds, "Halten" to item.holdOutSeconds,
        ).filter { it.second > 0 }
        var pIdx = 0
        while (active && phases.isNotEmpty()) {
            phase = phases[pIdx].first
            phaseSeconds = phases[pIdx].second
            if (phase == "Einatmen") orbTarget = 1.35f else if (phase == "Ausatmen") orbTarget = 0.85f
            for (sec in phases[pIdx].second downTo 1) {
                secondsRemaining = sec
                delay(1000)
            }
            pIdx = (pIdx + 1) % phases.size
        }
    }

    val breathingScale by animateFloatAsState(
        targetValue = if (active) orbTarget else 1f,
        animationSpec = tween(phaseSeconds * 1000, easing = LinearEasing),
        label = "orb-scale",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ATEMÜBUNG",
            color = Color(0xFF06B6D4),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            item.scientificBenefit,
            color = HikariTextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(36.dp))

        // Interaktiver Atemkreis
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(breathingScale)
                .clip(CircleShape)
                .background(
                    if (active) Brush.radialGradient(listOf(Color(0xFF06B6D4), Color(0xFF0891B2), Color.Transparent))
                    else Brush.radialGradient(listOf(HikariSurfaceHigh, HikariCardBg)),
                )
                .border(2.dp, if (active) Color(0xFF22D3EE) else HikariBorderStrong, CircleShape)
                .clickable {
                    active = !active
                    if (active) onDone()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (active) "$secondsRemaining" else "START",
                    color = Color.White,
                    fontSize = if (active) 42.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (active) {
                    Text(
                        phase,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = {
                active = !active
                if (active) onDone()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Color(0xFF991B1B) else Color(0xFF0891B2),
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = if (active) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (active) "Pausieren" else "Sitzung starten", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistorySlideContent(item: HistoryCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = HikariAmber.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, HikariAmber.copy(alpha = 0.4f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = HikariAmber,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    item.dateLabel,
                    color = HikariAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            item.eventTitle,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 27.sp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            item.description,
            color = HikariText,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HikariCardBg)
                .border(0.5.dp, HikariAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Warum es heute zählt:", color = HikariAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(item.whyItMatters, color = Color.White.copy(alpha = 0.9f), fontSize = 13.5.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun MentalModelSlideContent(item: MentalModelCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.category.uppercase(),
            color = Color(0xFFA78BFA),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            item.modelName,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            item.explanation,
            color = HikariText,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Alltagsfalle:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                Text(item.realLifeExample, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF8B5CF6).copy(alpha = 0.18f))
                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color(0xFFDDD6FE),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Gegenstrategie:", color = Color(0xFFDDD6FE), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                Text(item.actionableDefense, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ScienceSlideContent(item: ScienceCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF6366F1).copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                item.phenomenon,
                color = Color(0xFFA5B4FC),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            item.question,
            color = HikariAmber,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 25.sp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            item.coreExplanation,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariCardBg)
                .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Faszinierendes Detail:", color = Color(0xFFA5B4FC), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(item.fascinatingDetail, color = HikariText, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun FinanceSlideContent(item: FinanceCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            item.corePrinciple,
            color = HikariText,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .padding(12.dp),
        ) {
            Column {
                Text("Praxis-Beispiel:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(item.practicalExample, color = HikariTextMuted, fontSize = 12.5.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFBBF24).copy(alpha = 0.16f))
                .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.VpnKey,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Takeaway-Regel:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(item.takeawayRule, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GeographySlideContent(item: GeographyCardItem, isDone: Boolean, onDone: () -> Unit) {
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    val answered = selectedIdx != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.question,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 25.sp,
        )

        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.forEachIndexed { i, opt ->
                val isCorrect = i == item.correctIndex
                val isChosen = selectedIdx == i
                val bg = when {
                    !answered -> HikariCardBg
                    isCorrect -> Color(0xFF065F46)
                    isChosen -> Color(0xFF991B1B)
                    else -> HikariCardBg.copy(alpha = 0.4f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.dp, if (answered && isCorrect) Color(0xFF10B981) else HikariBorderStrong, RoundedCornerShape(12.dp))
                        .clickable(enabled = !answered) {
                            selectedIdx = i
                            onDone()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(opt, color = Color.White, fontSize = 13.5.sp)
                }
            }
        }

        if (answered) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurfaceHigh)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        tint = Color(0xFFA7F3D0),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Wissenswert: ${item.interestingFact}", color = Color(0xFFA7F3D0), fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun SpeedMathCardSlideContent(item: SpeedMathCardItem, isDone: Boolean, onDone: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.trickTitle,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            item.formulaShortcut,
            color = HikariAmber,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(10.dp))

        Text(item.explanation, color = HikariText, fontSize = 13.5.sp, lineHeight = 19.sp)

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HikariCardBg)
                .border(1.dp, HikariBorderStrong, RoundedCornerShape(16.dp))
                .clickable {
                    revealed = !revealed
                    onDone()
                }
                .padding(18.dp),
        ) {
            Column {
                Text("Übungsaufgabe:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(item.practiceChallenge, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (revealed) {
                    Text("Ergebnis: ${item.challengeResult}", color = Color(0xFF34D399), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.TouchApp,
                            contentDescription = null,
                            tint = HikariTextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tippen zum Auflösen", color = HikariTextMuted, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtCultureSlideContent(item: ArtCultureCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "„${item.masterpieceTitle}“",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "${item.artist} (${item.yearAndOrigin})",
            color = Color(0xFFF472B6),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HikariCardBg)
                .border(0.5.dp, HikariBorder, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Text(item.backStory, color = HikariText, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun VocabularySlideContent(item: VocabularyCardItem, isDone: Boolean, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.word,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            item.wordType,
            color = HikariAmber,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            "Bedeutung: ${item.definition}",
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            "Herkunft: ${item.etymology}",
            color = HikariTextMuted,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .padding(14.dp),
        ) {
            Text(
                "„${item.sampleSentence}“",
                color = HikariAmber,
                fontSize = 13.5.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun PhilosophySlideContent(item: PhilosophyCardItem, isDone: Boolean, onDone: () -> Unit) {
    var votedOption by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.dilemmaTitle,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            item.scenario,
            color = HikariText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (votedOption == "A") Color(0xFF1E3A8A) else HikariCardBg)
                    .border(1.dp, if (votedOption == "A") Color(0xFF60A5FA) else HikariBorderStrong, RoundedCornerShape(12.dp))
                    .clickable {
                        votedOption = "A"
                        onDone()
                    }
                    .padding(14.dp),
            ) {
                Text("Wahl A: ${item.optionA}", color = Color.White, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (votedOption == "B") Color(0xFF581C87) else HikariCardBg)
                    .border(1.dp, if (votedOption == "B") Color(0xFFA855F7) else HikariBorderStrong, RoundedCornerShape(12.dp))
                    .clickable {
                        votedOption = "B"
                        onDone()
                    }
                    .padding(14.dp),
            ) {
                Text("Wahl B: ${item.optionB}", color = Color.White, fontSize = 13.sp)
            }
        }

        if (votedOption != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurfaceHigh)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Balance,
                        contentDescription = null,
                        tint = Color(0xFFDDD6FE),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Philosophische Einordnung: ${item.philosophicalInsight}",
                        color = Color(0xFFDDD6FE),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

// ── Finale Mindful-Abschluss-Slide ──────────────────────────────────────────

@Composable
private fun MindfulCompletionSlide(
    streak: Int,
    completedCount: Int,
    totalCount: Int,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF10B981).copy(alpha = 0.15f), Color.Transparent),
                    radius = 900f,
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.40f)),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Tagesfokus gemeistert!",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Dein Geist hat heute alle wertvollen Impulse aufgenommen. Kein sinnloses Weiterscrollen – nimm diese Gedanken mit in deinen Tag.",
                color = Color(0xFFA7F3D0),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                color = HikariCardBg,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("$streak Tage Serie", color = HikariAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text("•", color = HikariTextFaint)
                    Spacer(Modifier.width(16.dp))
                    Text("$completedCount von $totalCount geschafft", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Weiter im Feed", color = Color.White, fontSize = 12.sp)
                }

                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Feed anpassen", color = HikariText, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FeedEmptyState(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Spa,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Keine Module aktiv", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Aktiviere Zitate, Sprachen oder Rätsel in den Feed-Einstellungen.",
                color = HikariTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = HikariAmber),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Einstellungen öffnen", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Feed Settings Sheet ───────────────────────────────────────────────────────

// ── Deep Dive Slide (Horizontaler Wisch nach links) ──────────────────────────

@Composable
internal fun LanguageDeepDiveAudioPlayer(
    card: LanguageCardItem,
    audioPlayer: RussianAudioPlayer?,
) {
    val playingTrack by (audioPlayer?.playing?.collectAsState() ?: remember { mutableStateOf(null) })
    val audioRes = card.audioRes
    val isPlayingNormal = !audioRes.isNullOrBlank() && playingTrack == audioRes
    val isPlayingSlow = !audioRes.isNullOrBlank() && playingTrack == "${audioRes}_slow"
    val isAnyPlaying = isPlayingNormal || isPlayingSlow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0F2027),
                        Color(0xFF1B3039),
                        Color(0xFF244452),
                    ),
                ),
            )
            .border(1.dp, Color(0xFF38EF7D).copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Audiovisuelles Hör-Studio & Equalizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF38EF7D).copy(alpha = 0.18f),
                        border = BorderStroke(0.5.dp, Color(0xFF38EF7D).copy(alpha = 0.50f)),
                    ) {
                        Text(
                            text = card.language.shortCode,
                            color = Color(0xFFA7F3D0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AUDIOVISUELLES HÖR-STUDIO",
                        color = Color(0xFFA7F3D0),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }

                // Live Equalizer Visualizer
                AudiovisualEqualizer(
                    isActive = isAnyPlaying,
                    tint = if (isAnyPlaying) Color(0xFF38EF7D) else Color.White.copy(alpha = 0.25f),
                )
            }

            // Target Word + Phonetics + Translation
            Column {
                Text(
                    text = card.foreignWord,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = "[${card.phonetic}]",
                            color = HikariAmber,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "·",
                        color = Color.White.copy(alpha = 0.3f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = card.nativeTranslation,
                        color = Color(0xFFA7F3D0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Beispielsatz im Kontext (falls vorhanden)
            if (card.exampleForeign.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.30f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = "„${card.exampleForeign}“",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = card.exampleTranslation,
                            color = HikariTextMuted,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }

            // Audiowiedergabe Controls (1.0x & 0.7x Slow)
            if (!audioRes.isNullOrBlank() && audioPlayer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1.0x Normal Audio
                    Surface(
                        onClick = {
                            if (isPlayingNormal) audioPlayer.stop() else audioPlayer.play(audioRes)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPlayingNormal) Color(0xFF38EF7D).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.6.dp,
                            if (isPlayingNormal) Color(0xFF38EF7D) else Color.White.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isPlayingNormal) Icons.Default.Pause else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = "Muttersprachler 1.0x",
                                tint = if (isPlayingNormal) Color(0xFF38EF7D) else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingNormal) "Stopp" else "1.0× Original",
                                color = if (isPlayingNormal) Color(0xFF38EF7D) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // 0.7x Slow Audio
                    Surface(
                        onClick = {
                            if (isPlayingSlow) audioPlayer.stop() else audioPlayer.play(audioRes, slow = true)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPlayingSlow) HikariAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.6.dp,
                            if (isPlayingSlow) HikariAmber else Color.White.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isPlayingSlow) Icons.Default.Pause else Icons.Outlined.SlowMotionVideo,
                                contentDescription = "Verlangsamt 0.7x",
                                tint = if (isPlayingSlow) HikariAmber else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingSlow) "Stopp" else "0.7× Verlangsamt",
                                color = if (isPlayingSlow) HikariAmber else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepDiveSlide(
    card: MindfulCard,
    audioPlayer: RussianAudioPlayer? = null,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    bottomPadding: Dp,
) {
    val deepDive = remember(card.id) { card.resolveDeepDive() }
    val ambientColor = cardAmbientColor(card)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(ambientColor.copy(alpha = 0.15f), Color.Transparent),
                    radius = 900f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = bottomPadding),
        ) {
            // Header Bar mit Zurück-Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = HikariAmber,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Zurück zur Karte",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ambientColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, ambientColor.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = ambientColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "DEEP DIVE",
                            color = ambientColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Scrollable Deep Dive Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Audiovisuelles Hör-Studio bei Sprachkarten ganz oben
                if (card is LanguageCardItem) {
                    LanguageDeepDiveAudioPlayer(
                        card = card,
                        audioPlayer = audioPlayer,
                    )
                }

                // Titel & Subtitel
                Column {
                    Text(
                        text = deepDive.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = deepDive.subtitle,
                        color = HikariAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // 1. Kernaussage / Key Insight Glass Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HikariSurfaceHigh)
                        .border(1.dp, ambientColor.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = HikariAmber,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (deepDive.isAuthored) "DARUM GEHT ES" else "KERNERKENNTNIS",
                                color = HikariAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = ruProse(deepDive.keyInsight),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp,
                        )
                    }
                }

                // 2. Geschriebene Abschnitte: jeder mit eigener Überschrift und neuem Inhalt
                deepDive.sections.forEach { section ->
                    DeepDiveSection(section, ambientColor, audioPlayer)
                }
                val deepRoute = (card as? LanguageCardItem)?.deepRoute
                if (deepRoute != null) {
                    Surface(
                        onClick = { onNavigate(deepRoute) },
                        shape = RoundedCornerShape(14.dp),
                        color = ambientColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, ambientColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Im Russisch-Bereich weiter vertiefen",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = ambientColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // 2b. Ausführlicher Kontext & Hintergründe (alte, zusammengesetzte Fassung)
                if (deepDive.fullContext.isNotBlank()) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HikariCardBg)
                        .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            "HINTERGRUND & KONTEXT",
                            color = HikariTextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = deepDive.fullContext,
                            color = HikariText,
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }

                // 3. Praktische Anwendung im Alltag (falls vorhanden)
                deepDive.practicalApplication?.let { app ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(HikariCardBg)
                            .border(0.5.dp, HikariBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.TrackChanges,
                                    contentDescription = null,
                                    tint = Color(0xFFA7F3D0),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (deepDive.isAuthored) "PROBIER ES AUS" else "ANWENDUNG & TRANSFER",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = app,
                                color = HikariText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                        }
                    }
                }

                // 4. Herkunft & Ursprung (falls vorhanden)
                deepDive.originOrEtymology?.let { origin ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountTree,
                                contentDescription = null,
                                tint = HikariTextFaint,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "HERKUNFT / SYSTEM",
                                    color = HikariTextFaint,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = origin,
                                    color = HikariTextMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                // 5. Takeaways
                if (deepDive.relatedTakeaways.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (deepDive.isAuthored) "ZUM MITNEHMEN" else "SCHLÜSSELERKENNTNISSE",
                            color = HikariTextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        deepDive.relatedTakeaways.forEach { takeaway ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(HikariSurfaceHigh.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("•", color = ambientColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = ruProse(takeaway),
                                    color = HikariText,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Wisch-Hinweis unten
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "← Nach rechts wischen für die Hauptkarte",
                        color = HikariTextFaint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── Persönliches Wissens-Archiv („Mein Gehirn“) ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeArchiveSheet(
    savedCards: List<MindfulCard>,
    onToggleSave: (String) -> Unit,
    audioPlayer: RussianAudioPlayer? = null,
    onSharePoster: (MindfulCard) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<MindfulModuleType?>(null) }
    val playingTrack = audioPlayer?.playing?.collectAsState()?.value

    val filteredCards = remember(savedCards, searchQuery, selectedCategoryFilter) {
        savedCards.filter { card ->
            val matchesCategory = selectedCategoryFilter == null || card.type == selectedCategoryFilter
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                when (card) {
                    is QuoteCardItem -> card.quote.lowercase().contains(q) || card.author.lowercase().contains(q)
                    is BrainPuzzleCardItem -> card.question.lowercase().contains(q) || card.category.lowercase().contains(q)
                    is LanguageCardItem -> card.foreignWord.lowercase().contains(q) || card.nativeTranslation.lowercase().contains(q) || card.exampleForeign.lowercase().contains(q)
                    is HistoryCardItem -> card.eventTitle.lowercase().contains(q) || card.description.lowercase().contains(q)
                    is MentalModelCardItem -> card.modelName.lowercase().contains(q) || card.explanation.lowercase().contains(q)
                    is BreathworkCardItem -> card.title.lowercase().contains(q) || card.scientificBenefit.lowercase().contains(q)
                    is ScienceCardItem -> card.phenomenon.lowercase().contains(q) || card.question.lowercase().contains(q) || card.coreExplanation.lowercase().contains(q)
                    is FinanceCardItem -> card.title.lowercase().contains(q) || card.corePrinciple.lowercase().contains(q)
                    is GeographyCardItem -> card.question.lowercase().contains(q) || card.interestingFact.lowercase().contains(q)
                    is SpeedMathCardItem -> card.trickTitle.lowercase().contains(q) || card.explanation.lowercase().contains(q)
                    is ArtCultureCardItem -> card.masterpieceTitle.lowercase().contains(q) || card.artist.lowercase().contains(q)
                    is VocabularyCardItem -> card.word.lowercase().contains(q) || card.definition.lowercase().contains(q)
                    is PhilosophyCardItem -> card.dilemmaTitle.lowercase().contains(q) || card.philosophicalInsight.lowercase().contains(q)
                }
            }
            matchesCategory && matchesSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HikariBorderStrong) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // Header: Titel + Zähler + Fertig-Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmarks,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mein Gehirn",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = HikariAmber.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, HikariAmber.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            "${savedCards.size}",
                            color = HikariAmber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }

                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Text(
                        "Fertig",
                        color = HikariAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Cupertino Style Suchfeld
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = HikariSurfaceHigh,
                border = BorderStroke(0.5.dp, HikariBorderStrong),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Suchen",
                        tint = HikariTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 13.5.sp,
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Archiv durchsuchen (Wort, Zitat, Thema)…",
                                    color = HikariTextFaint,
                                    fontSize = 13.sp,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Löschen",
                                tint = HikariTextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Horizontale Kategorie-Filter-Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // "Alle" Pill
                val allActive = selectedCategoryFilter == null
                Surface(
                    onClick = { selectedCategoryFilter = null },
                    shape = RoundedCornerShape(12.dp),
                    color = if (allActive) HikariAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, if (allActive) HikariAmber else Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        "Alle (${savedCards.size})",
                        color = if (allActive) HikariAmber else HikariTextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = if (allActive) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                MindfulModuleType.entries.forEach { mod ->
                    val count = savedCards.count { it.type == mod }
                    if (count > 0) {
                        val active = selectedCategoryFilter == mod
                        Surface(
                            onClick = { selectedCategoryFilter = if (active) null else mod },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) mod.accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(0.5.dp, if (active) mod.accentColor else Color.White.copy(alpha = 0.12f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = mod.icon,
                                    contentDescription = null,
                                    tint = if (active) Color.White else mod.accentColor,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${mod.title.substringBefore(" ")} ($count)",
                                    color = if (active) Color.White else HikariTextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Saved Items List
            if (filteredCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmarks,
                                    contentDescription = null,
                                    tint = HikariAmber,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (savedCards.isEmpty()) "Dein Geist ist noch frei" else "Keine Treffer",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (savedCards.isEmpty())
                                "Doppeltippe auf eine beliebige Karte im Feed oder tippe auf das Lesezeichen, um wertvolle Erkenntnisse hier dauerhaft abzulegen."
                            else
                                "Für deine Suche »$searchQuery« wurden in deinen gespeicherten Karten keine Einträge gefunden.",
                            color = HikariTextMuted,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        SavedCardItemRow(
                            card = card,
                            audioPlayer = audioPlayer,
                            playingTrack = playingTrack,
                            onShare = { onSharePoster(card) },
                            onRemove = { onToggleSave(card.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedCardItemRow(
    card: MindfulCard,
    audioPlayer: RussianAudioPlayer?,
    playingTrack: String?,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    val deepDive = remember(card.id) { card.resolveDeepDive() }
    val catLabel = getCardCategoryLabel(card)
    val catIcon = when (card.type) {
        MindfulModuleType.QUOTE -> Icons.Outlined.FormatQuote
        MindfulModuleType.LANGUAGE -> Icons.Outlined.Translate
        MindfulModuleType.BRAIN_PUZZLE -> Icons.Outlined.Psychology
        MindfulModuleType.MENTAL_MODEL -> Icons.Outlined.Lightbulb
        MindfulModuleType.HISTORY -> Icons.Default.History
        MindfulModuleType.SCIENCE -> Icons.Outlined.Science
        MindfulModuleType.FINANCE -> Icons.Outlined.AccountBalance
        MindfulModuleType.PHILOSOPHY -> Icons.Outlined.AutoAwesome
        MindfulModuleType.GEOGRAPHY -> Icons.Outlined.Public
        MindfulModuleType.SPEED_MATH -> Icons.Outlined.Calculate
        MindfulModuleType.ART_CULTURE -> Icons.Outlined.Palette
        MindfulModuleType.VOCABULARY -> Icons.Outlined.MenuBook
        MindfulModuleType.BREATHWORK -> Icons.Outlined.Spa
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = HikariSurfaceHigh,
        border = BorderStroke(0.5.dp, HikariBorderStrong),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category Chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        catIcon,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        catLabel,
                        color = HikariAmber,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Share Poster button (↑)
                    Surface(
                        onClick = onShare,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Poster teilen",
                                tint = HikariAmber,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Delete / Unsave button
                    Surface(
                        onClick = onRemove,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Aus Archiv entfernen",
                                tint = HikariTextFaint,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Main Text / Insight
            Text(
                text = deepDive.subtitle,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = deepDive.keyInsight,
                color = HikariText,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Audio button if it's a language card with audio
            if (card is LanguageCardItem && !card.audioRes.isNullOrBlank() && audioPlayer != null) {
                val isPlaying = playingTrack == card.audioRes
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = {
                        if (isPlaying) audioPlayer.stop() else audioPlayer.play(card.audioRes)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPlaying) HikariAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(0.5.dp, if (isPlaying) HikariAmber else Color.White.copy(alpha = 0.12f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = if (isPlaying) HikariAmber else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "Spielt..." else "Audio anhören",
                            color = if (isPlaying) HikariAmber else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
