package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.hikari.app.ui.theme.HikariDanger
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

    val dateFormat = remember { SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN) }
    val todayFormatted = remember { dateFormat.format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── Header Bar ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TAGESFOKUS",
                            color = HikariAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        // Streak Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(HikariSurfaceHigh)
                                .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(10.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "🔥 $streak ${if (streak == 1) "Tag" else "Tage"}",
                                color = HikariText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = todayFormatted,
                        color = HikariTextMuted,
                        fontSize = 13.sp,
                    )
                }

                // Settings Button
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(HikariSurfaceHigh.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Feed-Einstellungen",
                        tint = HikariText,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            // ── Mindful Progress Bar ────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariCardBg)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isGoalCompleted) "Tagesziel erreicht! 🌱" else "Dein täglicher Geist-Fokus",
                        color = if (isGoalCompleted) Color(0xFF10B981) else HikariText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${cards.count { it.id in completedCardIds }} / ${cards.size}",
                        color = HikariAmber,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isGoalCompleted) Color(0xFF10B981) else HikariAmber,
                    trackColor = HikariSurfaceHigh,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Feed Cards List ─────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Bei Tagesabschluss: Feierliche Mindful Completion Card oben
                if (isGoalCompleted) {
                    item(key = "completion-banner") {
                        MindfulCompletionBanner(
                            onReset = { vm.resetDailyProgress() },
                            onOpenSettings = { showSettingsSheet = true },
                        )
                    }
                }

                items(cards, key = { it.id }) { card ->
                    val isDone = card.id in completedCardIds
                    val onDone = { vm.markCardCompleted(card.id) }
                    val onSkip = { vm.markCardCompleted(card.id) }
                    when (card) {
                        is QuoteCardItem -> QuoteCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is BrainPuzzleCardItem -> BrainPuzzleCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is LanguageCardItem -> LanguageCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is HistoryCardItem -> HistoryCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is MentalModelCardItem -> MentalModelCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is BreathworkCardItem -> BreathworkCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is ScienceCardItem -> ScienceCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is FinanceCardItem -> FinanceCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is GeographyCardItem -> GeographyCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is SpeedMathCardItem -> SpeedMathCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is ArtCultureCardItem -> ArtCultureCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is VocabularyCardItem -> VocabularyCard(card, isDone, onDone = onDone, onSkip = onSkip)
                        is PhilosophyCardItem -> PhilosophyCard(card, isDone, onDone = onDone, onSkip = onSkip)
                    }
                }
            }
        }
    }

    // ── Settings Bottom Sheet ───────────────────────────────────────────────────
    if (showSettingsSheet) {
        FeedSettingsSheet(
            enabledModules = enabledModules,
            selectedLanguage = selectedLanguage,
            moduleRanks = moduleRanks,
            onToggleModule = { module, enabled -> vm.toggleModule(module, enabled) },
            onSetModuleRank = { module, rank -> vm.setModuleRank(module, rank) },
            onSelectLanguage = { vm.setLearningLanguage(it) },
            onResetDaily = { vm.resetDailyProgress() },
            onResetLanguage = { vm.resetLanguageProgress() },
            onDismiss = { showSettingsSheet = false },
        )
    }
}

// ── Completion Banner ─────────────────────────────────────────────────────────

@Composable
private fun MindfulCompletionBanner(onReset: () -> Unit, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF064E3B), Color(0xFF022C22)),
                ),
            )
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌱", fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Du bist für heute komplett!",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Dein Gehirn hat wertvolle Impulse erhalten.",
                        color = Color(0xFFA7F3D0),
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Kein endloses Scrollen, kein billiges Dopamin. Nimm diese Weisheiten und Fähigkeiten mit in deinen Tag!",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Tag neu starten", color = Color.White, fontSize = 11.sp)
                }
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Module anpassen", color = HikariText, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Modul-Karten ─────────────────────────────────────────────────────────────

@Composable
private fun ModuleCardHeader(
    emoji: String,
    title: String,
    badgeText: String? = null,
    rank: ModuleRank = ModuleRank.RANK_3,
    isDone: Boolean,
    onDone: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = HikariText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badgeText != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HikariSurfaceHigh)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(badgeText, color = HikariAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            if (rank == ModuleRank.RANK_1) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                        .border(0.5.dp, Color(0xFF10B981).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("⭐ R1 · TOP", color = Color(0xFF10B981), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            } else if (rank == ModuleRank.RANK_2) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.16f))
                        .border(0.5.dp, Color(0xFF3B82F6).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("🔷 R2", color = Color(0xFF60A5FA), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isDone && onSkip != null) {
                Text(
                    text = "Überspringen ⏩",
                    color = HikariTextMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSkip() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(2.dp))
            }

            IconButton(
                onClick = onDone,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = "Erledigt",
                    tint = if (isDone) Color(0xFF10B981) else HikariTextFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun QuoteCard(item: QuoteCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("📜", "LEBENSWEISHEIT", "Stoiker", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "„${item.quote}“",
                color = HikariText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "— ${item.author} (${item.contextEra})",
                color = HikariAmber,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariSurfaceHigh.copy(alpha = 0.6f))
                    .padding(10.dp),
            ) {
                Text(
                    text = "Reflexion: ${item.reflectionPrompt}",
                    color = HikariTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun BrainPuzzleCard(item: BrainPuzzleCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    val answered = selectedOption != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🧩", item.title.uppercase(), item.category, rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.question, color = HikariText, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.options.forEachIndexed { index, opt ->
                    val isCorrect = index == item.correctIndex
                    val isSelected = selectedOption == index
                    val bgColor = when {
                        !answered -> HikariSurfaceHigh
                        isCorrect -> Color(0xFF065F46)
                        isSelected -> Color(0xFF991B1B)
                        else -> HikariSurfaceHigh.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .clickable(enabled = !answered) {
                                selectedOption = index
                                onDone()
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = opt,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected || (answered && isCorrect)) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (answered) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HikariSurfaceHigh)
                        .padding(10.dp),
                ) {
                    Column {
                        Text(item.explanation, color = HikariText, fontSize = 11.5.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "🧠 Trainiert: ${item.brainRegionTrained}",
                            color = HikariAmber,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(item: LanguageCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    var revealed by remember { mutableStateOf(false) }
    var selectedReply by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🗣️", "SPRACHEN LERNEN", item.language.title, rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(10.dp))

            // Flashcard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43))),
                    )
                    .clickable { revealed = !revealed; onDone() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.foreignWord,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.phonetic,
                        color = HikariAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (revealed) {
                        Text(
                            text = item.nativeTranslation,
                            color = Color(0xFFA7F3D0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "„${item.exampleForeign}“\n(${item.exampleTranslation})",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = "Tippen zum Aufdecken & Sprechen",
                            color = HikariTextFaint,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mini Dialogue
            Text("Mini-Dialog: ${item.dialogueScenario}", color = HikariAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.dialoguePrompt, color = HikariText, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.dialogueReplies.forEachIndexed { i, reply ->
                    val isChosen = selectedReply == i
                    val isCorrect = i == item.correctReplyIndex
                    val btnBg = when {
                        selectedReply == null -> HikariSurfaceHigh
                        isCorrect -> Color(0xFF065F46)
                        isChosen -> Color(0xFF991B1B)
                        else -> HikariSurfaceHigh.copy(alpha = 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(btnBg)
                            .clickable {
                                selectedReply = i
                                onDone()
                            }
                            .padding(8.dp),
                    ) {
                        Text(reply, color = Color.White, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🏛️", "HEUTE IN DER GESCHICHTE", item.dateLabel, rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.eventTitle, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.description, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HikariSurfaceHigh)
                    .padding(8.dp),
            ) {
                Text("Bedeutung heute: ${item.whyItMatters}", color = HikariAmber, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun MentalModelCard(item: MentalModelCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("💡", "KRITISCHES DENKEN", item.category, rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.modelName, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.explanation, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("Alltagsfalle: ${item.realLifeExample}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.5.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                    .border(0.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text("Gegenstrategie: ${item.actionableDefense}", color = Color(0xFFDDD6FE), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BreathworkCard(item: BreathworkCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ModuleCardHeader("🫁", "1-MINUTEN-ATEMÜBUNG", "Box Breathing", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(10.dp))
            Text(item.scientificBenefit, color = HikariTextMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))

            // Breathing Circle Animation
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Brush.radialGradient(listOf(Color(0xFF06B6D4), Color(0xFF0891B2), Color.Transparent))
                        else Brush.radialGradient(listOf(HikariSurfaceHigh, HikariCardBg)),
                    )
                    .clickable {
                        active = !active
                        if (active) onDone()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (active) "$secondsRemaining" else "Start",
                        color = Color.White,
                        fontSize = if (active) 22.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (active) {
                        Text(phase, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScienceCard(item: ScienceCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🔬", "WISSENSCHAFTS-HAPPEN", item.phenomenon, rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.question, color = HikariAmber, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.coreExplanation, color = HikariText, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HikariSurfaceHigh)
                    .padding(8.dp),
            ) {
                Text("Erstaunlich: ${item.fascinatingDetail}", color = HikariTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FinanceCard(item: FinanceCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("💰", "FINANZIELLE BILDUNG", "Life Skill", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.title, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.corePrinciple, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("Beispiel: ${item.practicalExample}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFBBF24).copy(alpha = 0.15f))
                    .border(0.5.dp, Color(0xFFFBBF24).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text("Regel: ${item.takeawayRule}", color = HikariAmber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GeographyCard(item: GeographyCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    val answered = selectedIdx != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🌍", "WELTATLAS-QUIZ", "Geografie", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.question, color = HikariText, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                item.options.forEachIndexed { i, opt ->
                    val isCorrect = i == item.correctIndex
                    val isChosen = selectedIdx == i
                    val bg = when {
                        !answered -> HikariSurfaceHigh
                        isCorrect -> Color(0xFF065F46)
                        isChosen -> Color(0xFF991B1B)
                        else -> HikariSurfaceHigh.copy(alpha = 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable(enabled = !answered) {
                                selectedIdx = i
                                onDone()
                            }
                            .padding(8.dp),
                    ) {
                        Text(opt, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            if (answered) {
                Spacer(Modifier.height(8.dp))
                Text(item.interestingFact, color = Color(0xFFA7F3D0), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SpeedMathCard(item: SpeedMathCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    var revealed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🔢", "KOPFRECHNEN-TRICK", item.trickTitle, rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.formulaShortcut, color = HikariAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.explanation, color = HikariTextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HikariSurfaceHigh)
                    .clickable { revealed = !revealed; onDone() }
                    .padding(10.dp),
            ) {
                Column {
                    Text("Aufgabe: ${item.practiceChallenge}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (revealed) {
                        Spacer(Modifier.height(4.dp))
                        Text("Lösung: ${item.challengeResult}", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Tippen zum Auflösen", color = HikariTextFaint, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtCultureCard(item: ArtCultureCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("🎨", "KUNST & KULTUR", "Meisterwerk", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text("„${item.masterpieceTitle}“", color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${item.artist} (${item.yearAndOrigin})", color = HikariAmber, fontSize = 11.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(item.backStory, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun VocabularyCard(item: VocabularyCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("📚", "WORTSCHATZ-MEISTER", "Wort des Tages", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.word, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(item.wordType, color = HikariTextFaint, fontSize = 10.5.sp)
            Spacer(Modifier.height(6.dp))
            Text("Bedeutung: ${item.definition}", color = HikariText, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text("Herkunft: ${item.etymology}", color = HikariTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("Beispiel: „${item.sampleSentence}“", color = HikariAmber, fontSize = 11.sp, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun PhilosophyCard(item: PhilosophyCardItem, isDone: Boolean, onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    var votedOption by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(
                0.5.dp,
                if (isDone) Color(0xFF10B981).copy(alpha = 0.5f) else HikariBorderStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            ModuleCardHeader("⚖️", "GEDANKENEXPERIMENT", "Ethik", rank = item.rank, isDone = isDone, onDone = onDone, onSkip = onSkip)
            Spacer(Modifier.height(8.dp))
            Text(item.dilemmaTitle, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(item.scenario, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))

            // Voting Options
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (votedOption == "A") Color(0xFF1E3A8A) else HikariSurfaceHigh)
                        .clickable { votedOption = "A"; onDone() }
                        .padding(9.dp),
                ) {
                    Text(item.optionA, color = Color.White, fontSize = 11.5.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (votedOption == "B") Color(0xFF581C87) else HikariSurfaceHigh)
                        .clickable { votedOption = "B"; onDone() }
                        .padding(9.dp),
                ) {
                    Text(item.optionB, color = Color.White, fontSize = 11.5.sp)
                }
            }

            if (votedOption != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HikariSurfaceHigh)
                        .padding(8.dp),
                ) {
                    Text(
                        "Philosophischer Einblick: ${item.philosophicalInsight}",
                        color = HikariAmber,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

// ── Feed Settings Sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedSettingsSheet(
    enabledModules: Set<MindfulModuleType>,
    selectedLanguage: LearningLanguage,
    moduleRanks: Map<MindfulModuleType, ModuleRank>,
    onToggleModule: (MindfulModuleType, Boolean) -> Unit,
    onSetModuleRank: (MindfulModuleType, ModuleRank) -> Unit,
    onSelectLanguage: (LearningLanguage) -> Unit,
    onResetDaily: () -> Unit,
    onResetLanguage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HikariCardBg,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Feed-Einstellungen & Ranking",
                    color = HikariText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Fertig",
                    color = HikariAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismiss() },
                )
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Section: Zielsprache
                item {
                    Text(
                        "ZIELSPRACHE FÜR LERN-EINHEITEN",
                        color = HikariAmber,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LearningLanguage.entries.forEach { lang ->
                            val isSelected = lang == selectedLanguage
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) HikariAmber else HikariSurfaceHigh)
                                    .clickable { onSelectLanguage(lang) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "${lang.flagEmoji} ${lang.title}",
                                    color = if (isSelected) Color.Black else HikariText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Section: Modul-Toggles & Ranking
                item {
                    Text(
                        "MODULE, RANKING & FREQUENZ",
                        color = HikariAmber,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                items(MindfulModuleType.entries) { module ->
                    val isChecked = module in enabledModules
                    val currentRank = moduleRanks[module] ?: ModuleRank.RANK_3
                    val isCappedModule = module == MindfulModuleType.QUOTE || module == MindfulModuleType.BRAIN_PUZZLE

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HikariSurfaceHigh.copy(alpha = 0.6f))
                            .border(
                                width = if (isChecked && currentRank == ModuleRank.RANK_1) 1.dp else 0.5.dp,
                                color = if (isChecked && currentRank == ModuleRank.RANK_1) HikariAmber.copy(alpha = 0.5f) else HikariBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(module.iconEmoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(module.title, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(module.subtitle, color = HikariTextFaint, fontSize = 10.sp)
                                }
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { onToggleModule(module, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = HikariAmber,
                                    uncheckedThumbColor = HikariTextFaint,
                                    uncheckedTrackColor = HikariSurface,
                                ),
                            )
                        }

                        if (isChecked) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ModuleRank.entries.forEach { rank ->
                                    val isSelected = currentRank == rank
                                    val rankLabel = when (rank) {
                                        ModuleRank.RANK_1 -> if (isCappedModule) "⭐ R1 (2x)" else "⭐ R1 (3x)"
                                        ModuleRank.RANK_2 -> "🔷 R2 (2x)"
                                        ModuleRank.RANK_3 -> "R3 (1x)"
                                    }
                                    val bg = when {
                                        isSelected && rank == ModuleRank.RANK_1 -> HikariAmber
                                        isSelected && rank == ModuleRank.RANK_2 -> Color(0xFF3B82F6)
                                        isSelected -> HikariSurfaceHigh
                                        else -> HikariSurface.copy(alpha = 0.5f)
                                    }
                                    val textColor = when {
                                        isSelected && rank == ModuleRank.RANK_1 -> Color.Black
                                        isSelected -> Color.White
                                        else -> HikariTextMuted
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .clickable { onSetModuleRank(module, rank) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = rankLabel,
                                            color = textColor,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Aktionen & Reset
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "FORTSCHRITT & GRENZEN",
                        color = HikariAmber,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onResetDaily() },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Tages-Karten zurücksetzen", fontSize = 11.sp, color = HikariText)
                        }

                        Button(
                            onClick = { onResetLanguage() },
                            colors = ButtonDefaults.buttonColors(containerColor = HikariSurfaceHigh),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Sprachen-Reset", fontSize = 11.sp, color = HikariText)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
