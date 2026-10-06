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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
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
) {
    val cards by vm.mindfulCards.collectAsState()
    val completedCardIds by vm.completedCards.collectAsState()
    val progressFraction by vm.progressFraction.collectAsState()
    val isGoalCompleted by vm.isGoalCompleted.collectAsState()
    val enabledModules by vm.enabledModules.collectAsState()
    val selectedLanguage by vm.selectedLanguage.collectAsState()
    val moduleRanks by vm.moduleRanks.collectAsState()
    val streak = remember(completedCardIds) { vm.getStreak() }

    var showSettingsSheet by remember { mutableStateOf(false) }

    // Gesamte Pager-Seiten: Kartenanzahl + 1 finale Mindful-Abschlusskarte
    val totalPages = if (cards.isEmpty()) 0 else cards.size + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    var settledPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage = it }
    }

    LaunchedEffect(resetTick) {
        if (resetTick > 0 && cards.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg),
    ) {
        if (cards.isEmpty()) {
            FeedEmptyState(onOpenSettings = { showSettingsSheet = true })
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
                key = { page ->
                    if (page < cards.size) cards[page].id else "zen_completion_slide"
                },
            ) { page ->
                if (page < cards.size) {
                    val card = cards[page]
                    val isDone = card.id in completedCardIds
                    FeedCardSlide(
                        card = card,
                        pageIndex = page,
                        totalCount = cards.size,
                        streak = streak,
                        isDone = isDone,
                        audioPlayer = vm.audio,
                        onRecordPractice = { phraseId, correct -> vm.recordRussianPractice(phraseId, correct) },
                        onNavigate = onNavigate,
                        onDone = { vm.markCardCompleted(card.id) },
                        onOpenSettings = { showSettingsSheet = true },
                        showSwipeHint = page == 0,
                    )
                } else {
                    MindfulCompletionSlide(
                        streak = streak,
                        completedCount = cards.count { it.id in completedCardIds },
                        totalCount = cards.size,
                        onReset = { vm.resetDailyProgress() },
                        onOpenSettings = { showSettingsSheet = true },
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        FeedSettingsSheet(
            enabledModules = enabledModules,
            selectedLanguage = selectedLanguage,
            moduleRanks = moduleRanks,
            streak = streak,
            completedCount = cards.count { it.id in completedCardIds },
            totalCount = cards.size,
            onToggleModule = { module, enabled -> vm.toggleModule(module, enabled) },
            onSetModuleRank = { module, rank -> vm.setModuleRank(module, rank) },
            onSelectLanguage = { vm.setLearningLanguage(it) },
            onResetDaily = { vm.resetDailyProgress() },
            onResetLanguage = { vm.resetLanguageProgress() },
            onDismiss = { showSettingsSheet = false },
        )
    }
}

// ── Einzelne Slide (Full-Screen Focus) ──────────────────────────────────────────

@Composable
private fun FeedCardSlide(
    card: MindfulCard,
    pageIndex: Int,
    totalCount: Int,
    streak: Int,
    isDone: Boolean,
    audioPlayer: RussianAudioPlayer? = null,
    onRecordPractice: (String?, Boolean) -> Unit = { _, _ -> },
    onNavigate: (String) -> Unit = {},
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    showSwipeHint: Boolean,
) {
    val ambientColor = when (card) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                .padding(top = 8.dp, bottom = 84.dp), // Aussparung für Statusbar & Bottom-Nav-Bar
        ) {
            // ── Top Story Progress Bar (Ganz oben, dezent & präzise) ───────────────
            val progress by animateFloatAsState(
                targetValue = (pageIndex + 1).toFloat() / totalCount.toFloat(),
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
            )

            Spacer(Modifier.height(12.dp))

            // ── Header Bar: HIKARI Brand + Kategorie + Streak + Settings ────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Linke Seite: Brand "HIKARI" in Gold + Kategorie-Pill + Zähler
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "HIKARI",
                        color = HikariAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                    )

                    Spacer(Modifier.width(8.dp))

                    // Minimalist Frosted Category Chip
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(getCardEmoji(card), fontSize = 12.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = getCardCategoryLabel(card),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (card.rank == ModuleRank.RANK_3) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(HikariAmber.copy(alpha = 0.20f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "TOP-FOKUS",
                                        color = HikariAmber,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "${pageIndex + 1}/$totalCount",
                        color = HikariTextFaint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Rechte Seite: Streak Badge + Edler Frosted Settings Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("🔥", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$streak",
                                color = HikariAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Premium Frosted Glass Settings Button
                    Surface(
                        onClick = onOpenSettings,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.20f)),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Feed-Einstellungen",
                                tint = HikariText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Center Content Box (Fokus auf genau 1 Thema) ─────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
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
        }

        // ── Floating TikTok/Reels Style Action Sidebar (Rechte Seite) ─────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "Erledigt" / "Gelernt" Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDone) Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                        else Brush.linearGradient(listOf(HikariSurfaceHigh, HikariCardBg)),
                    )
                    .border(
                        1.dp,
                        if (isDone) Color(0xFF34D399) else HikariBorderStrong,
                        CircleShape,
                    )
                    .clickable { onDone() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = if (isDone) "Erledigt" else "Als gelernt markieren",
                    tint = if (isDone) Color.White else HikariText,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = if (isDone) "Gelernt ✓" else "Lernen",
                color = if (isDone) Color(0xFF34D399) else HikariTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
                    .padding(bottom = 88.dp)
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

private fun getCardEmoji(card: MindfulCard): String = when (card) {
    is QuoteCardItem -> "📜"
    is BrainPuzzleCardItem -> "🧩"
    is LanguageCardItem -> "🗣️"
    is HistoryCardItem -> "🏛️"
    is MentalModelCardItem -> "💡"
    is BreathworkCardItem -> "🫁"
    is ScienceCardItem -> "🔬"
    is FinanceCardItem -> "💰"
    is GeographyCardItem -> "🌍"
    is SpeedMathCardItem -> "🔢"
    is ArtCultureCardItem -> "🎨"
    is VocabularyCardItem -> "📚"
    is PhilosophyCardItem -> "⚖️"
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
                    Text("💡", fontSize = 16.sp)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.title.uppercase(),
                color = HikariAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Surface(
                color = HikariSurfaceHigh,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "🧠 ${item.brainRegionTrained}",
                    color = Color(0xFFA7F3D0),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                            Text(
                                text = if (isCorrect) "✓ Richtig" else if (isSelected) "✕ Falsch" else "",
                                color = if (isCorrect) Color(0xFF34D399) else Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
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
                    Text("💡 Auflösung:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(item.explanation, color = HikariText, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
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
    var isComparing by remember(item.id) { mutableStateOf(false) }
    var speechScore by remember(item.id) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var speechAttempts by remember(item.id) { mutableIntStateOf(0) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                onRecordPractice(item.phraseId, false)
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
                recognizer.stopListening()
            } else {
                audioPlayer?.stop()
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
        when (val s = speechState) {
            is RuListenState.Done -> {
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Kurs-Header & Fortschritts-Status ──────────────────────────
        if (item.dayNumber != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tag ${item.dayNumber} · ${item.dayTitle ?: "Russisch Kurs"}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.isDueReview) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HikariAmber.copy(alpha = 0.20f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "SRS FÄLLIG",
                                color = HikariAmber,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    text = "🔥 ${item.currentStreak} d · ⚡ ${item.totalXp} XP",
                    color = HikariAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ── Große Vokabel-Karte (Klar & Edel) ───────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F2027), Color(0xFF1B3039), Color(0xFF244452)),
                    ),
                )
                .border(1.dp, Color(0xFF38EF7D).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .clickable {
                    revealed = !revealed
                    onDone()
                }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.foreignWord,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = item.phonetic,
                    color = HikariAmber,
                    fontSize = 14.5.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Spacer(Modifier.height(12.dp))

                if (revealed) {
                    Text(
                        text = item.nativeTranslation,
                        color = Color(0xFFA7F3D0),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (!item.literalTranslation.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Wörtlich: „${item.literalTranslation}“",
                            color = HikariTextMuted,
                            fontSize = 11.5.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    if (!item.note.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.note,
                            color = HikariAmber.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (item.exampleForeign.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "„${item.exampleForeign}“\n(${item.exampleTranslation})",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                        )
                    }
                } else {
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "👉 Tippen zum Aufdecken",
                            color = HikariTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        // ── Muttersprachler-Audio (Offline-Originalstimme) ───────────────
        if (!item.audioRes.isNullOrBlank() && audioPlayer != null) {
            val audioRes = item.audioRes
            val isPlayingNormal = playingTrack == audioRes
            val isPlayingSlow = playingTrack == "${audioRes}_slow"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariSurfaceHigh)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(14.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Button 1: Normal Audio
                Surface(
                    onClick = {
                        if (isPlayingNormal) audioPlayer.stop() else audioPlayer.play(audioRes)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPlayingNormal) HikariAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(
                        0.5.dp,
                        if (isPlayingNormal) HikariAmber else Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isPlayingNormal) Icons.Default.Pause else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = "Anhören",
                            tint = if (isPlayingNormal) HikariAmber else Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPlayingNormal) "Spielt..." else "Muttersprachler",
                            color = if (isPlayingNormal) HikariAmber else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Button 2: Slow Audio
                Surface(
                    onClick = {
                        if (isPlayingSlow) audioPlayer.stop() else audioPlayer.play(audioRes, slow = true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPlayingSlow) HikariAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(
                        0.5.dp,
                        if (isPlayingSlow) HikariAmber else Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.weight(0.9f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🐢", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPlayingSlow) "Spielt langsam..." else "Langsam",
                            color = if (isPlayingSlow) HikariAmber else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ── Interaktive Aussprache & Sprach-Praxis ───────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HikariCardBg)
                .border(0.5.dp, HikariBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Aussprache & Sprachkontrolle",
                    color = HikariAmber,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                if (speechAttempts > 0) {
                    Text(
                        text = "$speechAttempts Versuche",
                        color = HikariTextMuted,
                        fontSize = 10.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 1. Aufnahme & Nachsprechen Studio (A/B)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Record / Stop Button
                Surface(
                    onClick = { toggleRecord() },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRecording) Color(0xFFDC2626) else HikariSurfaceHigh,
                    border = BorderStroke(
                        1.dp,
                        if (isRecording) Color(0xFFEF4444) else HikariBorderStrong,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            contentDescription = "Aufnahme",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isRecording) "Stopp & Vergleichen" else "🎙️ Nachsprechen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // If recording exists: Play own take & A/B Replay
                if (hasRecording && !isRecording) {
                    Surface(
                        onClick = {
                            audioPlayer?.stop()
                            audioPlayer?.playFile(recorder.file)
                        },
                        shape = CircleShape,
                        color = HikariSurfaceHigh,
                        border = BorderStroke(0.5.dp, HikariBorderStrong),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = "Meine Stimme",
                                tint = HikariText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

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
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Outlined.Replay,
                                contentDescription = "A/B-Vergleich",
                                tint = if (isComparing) HikariAmber else HikariText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 2. KI Aussprache-Prüfung (Spracherkennung & Bewertung)
            val isListening = speechState is RuListenState.Listening || speechState is RuListenState.Partial
            Surface(
                onClick = { toggleSpeechCheck() },
                shape = RoundedCornerShape(10.dp),
                color = if (isListening) HikariAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(
                    1.dp,
                    if (isListening) HikariAmber else Color.White.copy(alpha = 0.15f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isListening) Icons.Outlined.GraphicEq else Icons.Outlined.CheckCircle,
                        contentDescription = "KI-Prüfung",
                        tint = if (isListening) HikariAmber else Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isListening -> "Ich höre zu … Sprich jetzt!"
                            speechScore != null -> "🔄 Aussprache erneut prüfen"
                            else -> "🎯 KI-Ausspracheprüfung starten"
                        },
                        color = if (isListening) HikariAmber else Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Live Partial Heard Text
            if (speechState is RuListenState.Partial) {
                val partial = (speechState as RuListenState.Partial).text
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "»$partial«",
                    color = HikariAmber,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                )
            }

            // Failure message if recognition had an issue
            if (speechState is RuListenState.Failed) {
                val failed = speechState as RuListenState.Failed
                Spacer(Modifier.height(6.dp))
                Text(
                    text = failed.message,
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // Detailed Score Card after KI Evaluation
            speechScore?.let { sc ->
                val scorePct = (sc.score * 100).toInt()
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sc.passed) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFF78350F).copy(alpha = 0.5f),
                        )
                        .border(
                            1.dp,
                            if (sc.passed) Color(0xFF10B981) else Color(0xFFF59E0B),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (sc.passed) "🎉 $scorePct % · Exzellent! (+15 XP)" else "⚡ $scorePct % · Fast geschafft!",
                            color = if (sc.passed) Color(0xFF6EE7B7) else HikariAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Word-by-word visual highlight chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        sc.words.forEach { (word, ok) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (ok) Color(0xFF065F46) else Color(0xFF991B1B).copy(alpha = 0.8f))
                                    .border(0.5.dp, if (ok) Color(0xFF34D399) else Color(0xFFF87171), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = word,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    if (sc.heard.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Erkannt: »${sc.heard}«",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }

        // ── Mini-Dialog Szenario (Optionale Vertiefung) ──────────────────
        if (item.dialogueReplies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HikariSurfaceHigh)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "Szenario: ${item.dialogueScenario}",
                    color = HikariAmber,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.dialoguePrompt,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    selectedReply = i
                                    if (isCorrect) onDone()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(reply, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ── Kurs-Shortcut (Direkter Sprung in die volle Lektion) ─────────
        if (item.dayNumber != null) {
            Surface(
                onClick = {
                    onNavigate(if (item.isDueReview) "russian/review" else "russian/lesson/${item.dayNumber}")
                },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🇷🇺", fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (item.isDueReview) "SRS-Wiederholung im Kurs starten" else "Volle Lektion (Tag ${item.dayNumber}) öffnen",
                            color = HikariText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text("→", color = HikariAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val phases = listOf("Einatmen", "Halten", "Ausatmen", "Halten")
        var pIdx = 0
        while (active) {
            phase = phases[pIdx]
            for (sec in 4 downTo 1) {
                secondsRemaining = sec
                delay(1000)
            }
            pIdx = (pIdx + 1) % phases.size
        }
    }

    val breathingScale by animateFloatAsState(
        targetValue = when {
            !active -> 1f
            phase == "Einatmen" -> 1.35f
            phase == "Ausatmen" -> 0.85f
            else -> 1.15f
        },
        animationSpec = tween(4000, easing = LinearEasing),
        label = "orb-scale",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "1-MINUTEN-ATEMÜBUNG",
            color = Color(0xFF06B6D4),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Box Breathing (4-4-4-4)",
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
            Text(
                "📅 ${item.dateLabel}",
                color = HikariAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
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
                Text("💡 Warum es heute zählt:", color = HikariAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text("⚠️ Alltagsfalle:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                Text("🛡️ Gegenstrategie:", color = Color(0xFFDDD6FE), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                Text("✨ Faszinierendes Detail:", color = Color(0xFFA5B4FC), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                Text("🔑 Takeaway-Regel:", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                Text("🌍 Wissenswert: ${item.interestingFact}", color = Color(0xFFA7F3D0), fontSize = 12.5.sp)
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
                    Text("👉 Tippen zum Auflösen", color = HikariTextMuted, fontSize = 11.5.sp)
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
                Text(
                    "⚖️ Philosophische Einordnung: ${item.philosophicalInsight}",
                    color = Color(0xFFDDD6FE),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
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
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
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
            .padding(top = 8.dp, bottom = 84.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🌱", fontSize = 64.sp)

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
                    Text("🔥 $streak Tage Serie", color = HikariAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text("•", color = HikariTextFaint)
                    Spacer(Modifier.width(16.dp))
                    Text("$completedCount / $totalCount gelernt", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tag neu starten", color = Color.White, fontSize = 12.sp)
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
            Text("🌱", fontSize = 48.sp)
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

private enum class SettingsTab(val title: String, val icon: ImageVector) {
    TOPICS("Themen & Fokus", Icons.Outlined.Tune),
    LANGUAGES("Sprachen", Icons.Default.Translate),
    SYSTEM("Verwaltung", Icons.Default.Settings),
}

private enum class TopicFilter(val label: String) {
    ALL("Alle"),
    ACTIVE("Aktiv"),
    FOCUS("Top-Fokus"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedSettingsSheet(
    enabledModules: Set<MindfulModuleType>,
    selectedLanguage: LearningLanguage,
    moduleRanks: Map<MindfulModuleType, ModuleRank>,
    streak: Int,
    completedCount: Int,
    totalCount: Int,
    onToggleModule: (MindfulModuleType, Boolean) -> Unit,
    onSetModuleRank: (MindfulModuleType, ModuleRank) -> Unit,
    onSelectLanguage: (LearningLanguage) -> Unit,
    onResetDaily: () -> Unit,
    onResetLanguage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.TOPICS) }
    var topicFilter by remember { mutableStateOf(TopicFilter.ALL) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HikariBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.22f),
                width = 38.dp,
                height = 4.dp,
            )
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // ── Header Bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Feed anpassen",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Personalisiere Wissensbereiche & Prioritäten",
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                    )
                }

                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // ── Segmented Tab Switcher (iOS / Linear Style) ──────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isTabSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isTabSelected) HikariCardBg else Color.Transparent)
                            .then(
                                if (isTabSelected) {
                                    Modifier.border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                                } else Modifier,
                            )
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isTabSelected) HikariAmber else HikariTextMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                color = if (isTabSelected) Color.White else HikariTextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isTabSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Toast feedback
            AnimatedVisibility(visible = toastMessage != null) {
                toastMessage?.let { msg ->
                    Surface(
                        color = HikariAmber.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, HikariAmber.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✓", color = HikariAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(msg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── Tab Content ──────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    SettingsTab.TOPICS -> TopicsTabContent(
                        enabledModules = enabledModules,
                        moduleRanks = moduleRanks,
                        topicFilter = topicFilter,
                        onSelectFilter = { topicFilter = it },
                        onToggleModule = onToggleModule,
                        onSetModuleRank = onSetModuleRank,
                    )
                    SettingsTab.LANGUAGES -> LanguagesTabContent(
                        selectedLanguage = selectedLanguage,
                        onSelectLanguage = onSelectLanguage,
                        onResetLanguage = {
                            onResetLanguage()
                            toastMessage = "Sprach-Fortschritt wurde zurückgesetzt"
                        },
                    )
                    SettingsTab.SYSTEM -> SystemTabContent(
                        streak = streak,
                        completedCount = completedCount,
                        totalCount = totalCount,
                        onResetDaily = {
                            onResetDaily()
                            toastMessage = "Tagesfortschritt zurückgesetzt"
                        },
                        onResetLanguage = {
                            onResetLanguage()
                            toastMessage = "Sprach-Fortschritt zurückgesetzt"
                        },
                    )
                }
            }
        }
    }
}

// ── Tab 1: Themen & Fokus ────────────────────────────────────────────────────

@Composable
private fun TopicsTabContent(
    enabledModules: Set<MindfulModuleType>,
    moduleRanks: Map<MindfulModuleType, ModuleRank>,
    topicFilter: TopicFilter,
    onSelectFilter: (TopicFilter) -> Unit,
    onToggleModule: (MindfulModuleType, Boolean) -> Unit,
    onSetModuleRank: (MindfulModuleType, ModuleRank) -> Unit,
) {
    val activeCount = enabledModules.size
    val focusCount = MindfulModuleType.entries.count {
        it in enabledModules && (moduleRanks[it] ?: (if (it == MindfulModuleType.LANGUAGE) ModuleRank.RANK_3 else ModuleRank.RANK_1)) == ModuleRank.RANK_3
    }

    val filteredModules = remember(enabledModules, moduleRanks, topicFilter) {
        when (topicFilter) {
            TopicFilter.ALL -> MindfulModuleType.entries
            TopicFilter.ACTIVE -> MindfulModuleType.entries.filter { it in enabledModules }
            TopicFilter.FOCUS -> MindfulModuleType.entries.filter {
                it in enabledModules && (moduleRanks[it] ?: (if (it == MindfulModuleType.LANGUAGE) ModuleRank.RANK_3 else ModuleRank.RANK_1)) == ModuleRank.RANK_3
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Minimalist Info & Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicFilter.entries.forEach { f ->
                val count = when (f) {
                    TopicFilter.ALL -> MindfulModuleType.entries.size
                    TopicFilter.ACTIVE -> activeCount
                    TopicFilter.FOCUS -> focusCount
                }
                val isSelected = f == topicFilter
                Surface(
                    onClick = { onSelectFilter(f) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) HikariAmber.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(
                        0.5.dp,
                        if (isSelected) HikariAmber.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = f.label,
                            color = if (isSelected) HikariAmber else HikariTextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "$count",
                            color = if (isSelected) HikariAmber.copy(alpha = 0.8f) else HikariTextFaint,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (filteredModules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (topicFilter == TopicFilter.FOCUS) "Noch kein Thema im Top-Fokus" else "Keine aktiven Themen",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Wähle bei einem Thema „Stufe 3 (Top-Fokus)“, um es zu priorisieren.",
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
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
                items(filteredModules, key = { it.name }) { module ->
                    val isChecked = module in enabledModules
                    val currentRank = moduleRanks[module] ?: if (module == MindfulModuleType.LANGUAGE) ModuleRank.RANK_3 else ModuleRank.RANK_1
                    val isCappedModule = module == MindfulModuleType.QUOTE || module == MindfulModuleType.BRAIN_PUZZLE
                    val isTopFocus = isChecked && currentRank == ModuleRank.RANK_3

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isTopFocus) HikariSurfaceHigh.copy(alpha = 0.7f) else HikariSurface)
                            .border(
                                width = if (isTopFocus) 1.dp else 0.5.dp,
                                color = if (isTopFocus) HikariAmber.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTopFocus) HikariAmber.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isTopFocus) HikariAmber.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                    ),
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(module.iconEmoji, fontSize = 18.sp)
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = module.title,
                                            color = if (isChecked) Color.White else HikariTextMuted,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (isTopFocus) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(HikariAmber.copy(alpha = 0.20f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    text = "TOP-FOKUS",
                                                    color = HikariAmber,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.6.sp,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = if (isChecked) module.subtitle else "Deaktiviert",
                                        color = HikariTextMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }

                            Switch(
                                checked = isChecked,
                                onCheckedChange = { onToggleModule(module, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = HikariAmber,
                                    uncheckedThumbColor = HikariTextFaint,
                                    uncheckedTrackColor = HikariSurfaceHigh,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                                modifier = Modifier.scale(0.85f),
                            )
                        }

                        if (isChecked) {
                            Spacer(Modifier.height(10.dp))

                            // ── Sleek 3-Stufiger Segmented Slider ──────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF141416))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(9.dp))
                                    .padding(2.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                val rankOptions = listOf(
                                    Triple(ModuleRank.RANK_1, "Stufe 1", "Standard · 1x"),
                                    Triple(ModuleRank.RANK_2, "Stufe 2", if (isCappedModule) "2x täglich" else "3–4x täglich"),
                                    Triple(
                                        ModuleRank.RANK_3,
                                        "Stufe 3",
                                        if (isCappedModule) "2x & Fokus" else if (module == MindfulModuleType.LANGUAGE) "Top-Fokus (alle 2–3)" else "6–8x & Fokus",
                                    ),
                                )

                                rankOptions.forEach { (rank, title, sub) ->
                                    val isSelected = currentRank == rank
                                    val bg = when {
                                        isSelected && rank == ModuleRank.RANK_3 -> HikariAmber
                                        isSelected -> HikariSurfaceHigh
                                        else -> Color.Transparent
                                    }
                                    val titleColor = when {
                                        isSelected && rank == ModuleRank.RANK_3 -> Color.Black
                                        isSelected -> Color.White
                                        else -> HikariTextMuted
                                    }
                                    val subColor = when {
                                        isSelected && rank == ModuleRank.RANK_3 -> Color.Black.copy(alpha = 0.75f)
                                        isSelected -> Color.White.copy(alpha = 0.65f)
                                        else -> HikariTextFaint
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(bg)
                                            .then(
                                                if (isSelected && rank != ModuleRank.RANK_3) {
                                                    Modifier.border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(7.dp))
                                                } else Modifier,
                                            )
                                            .clickable { onSetModuleRank(module, rank) }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = title,
                                                color = titleColor,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            )
                                            Text(
                                                text = sub,
                                                color = subColor,
                                                fontSize = 9.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tab 2: Sprachen ──────────────────────────────────────────────────────────

@Composable
private fun LanguagesTabContent(
    selectedLanguage: LearningLanguage,
    onSelectLanguage: (LearningLanguage) -> Unit,
    onResetLanguage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                text = "Wähle die Sprache, deren Vokabeln, Grammatik und Lerneinheiten in deinen täglichen Feed einfließen.",
                color = HikariTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        items(LearningLanguage.entries) { lang ->
            val isSelected = lang == selectedLanguage
            val subtitle = when (lang) {
                LearningLanguage.RUSSIAN -> "A1–B2 · Kyrillisch, Vokabeln & Aussprache"
                LearningLanguage.SPANISH -> "A1–C1 · Konversation, Redewendungen & Grammatik"
                LearningLanguage.ENGLISH -> "Business Englisch · Verhandlung, Pitch & Karriere"
                LearningLanguage.JAPANESE -> "Romaji & Kanji · N5–N3 Grammatik & Phrasen"
                LearningLanguage.FRENCH -> "A1–B2 · Alltagskonversation & Wortschatz"
                LearningLanguage.ITALIAN -> "A1–B2 · Sprachgefühl & Satzstrukturen"
                LearningLanguage.GERMAN_ADVANCED -> "Gehobenes Deutsch · Rhetorik, Fremdwörter & Eloquenz"
            }

            Surface(
                onClick = { onSelectLanguage(lang) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) HikariAmber.copy(alpha = 0.08f) else HikariSurface,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 0.5.dp,
                    color = if (isSelected) HikariAmber else Color.White.copy(alpha = 0.08f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.06f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(lang.flagEmoji, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = lang.title,
                                color = if (isSelected) HikariAmber else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = subtitle,
                                color = HikariTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(HikariAmber),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = HikariSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vokabel-Fortschritt zurücksetzen", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text("Startet die Vokabelabfolge wieder ab Tag 1", color = HikariTextMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        onClick = onResetLanguage,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    ) {
                        Text(
                            text = "Reset",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Tab 3: Verwaltung & Philosophie ──────────────────────────────────────────

@Composable
private fun SystemTabContent(
    streak: Int,
    completedCount: Int,
    totalCount: Int,
    onResetDaily: () -> Unit,
    onResetLanguage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Mindful Philosophie
        item {
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Text("🌱", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Mindful Philosophie", color = Color(0xFFA7F3D0), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Hikari schützt deinen Geist vor Reizüberflutung. Nach Abschluss deiner Tageseinheiten stoppt der Feed bewusst, damit dein Gehirn das Gelernte festigen kann, anstatt in endlosem Scrollen zu versinken.",
                            color = Color(0xFFD1FAE5),
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp,
                        )
                    }
                }
            }
        }

        // Statistik-Kachel
        item {
            Surface(
                color = HikariSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Serie", color = HikariTextMuted, fontSize = 10.5.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("🔥 $streak Tage", color = HikariAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.10f)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Heute", color = HikariTextMuted, fontSize = 10.5.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("$completedCount / $totalCount", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.10f)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Status", color = HikariTextMuted, fontSize = 10.5.sp)
                        Spacer(Modifier.height(2.dp))
                        val isFinished = completedCount >= totalCount && totalCount > 0
                        Text(
                            if (isFinished) "Gemeistert ✓" else "Aktiv",
                            color = if (isFinished) Color(0xFF10B981) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Aktionen
        item {
            Text(
                "AKTIONEN",
                color = HikariAmber,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )

            Surface(
                color = HikariSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tages-Fortschritt zurücksetzen", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text("Erledigte Karten von heute zurücksetzen", color = HikariTextMuted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            onClick = onResetDaily,
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sprach-Lernstand zurücksetzen", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text("Vokabeln der gewählten Sprache ab Tag 1 starten", color = HikariTextMuted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            onClick = onResetLanguage,
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
