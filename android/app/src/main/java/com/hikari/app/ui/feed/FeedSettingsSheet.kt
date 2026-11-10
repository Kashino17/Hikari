package com.hikari.app.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.feed.FeedComposer
import com.hikari.app.domain.feed.LearningLanguage
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.ModuleRank
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay

/** Reihenfolge der Kacheln: erst das, was man aktiv tut, dann Wissen, zuletzt die Pause. */
private val TILE_ORDER = listOf(
    MindfulModuleType.LANGUAGE, MindfulModuleType.BRAIN_PUZZLE, MindfulModuleType.SPEED_MATH, MindfulModuleType.QUOTE,
    MindfulModuleType.HISTORY, MindfulModuleType.SCIENCE, MindfulModuleType.GEOGRAPHY, MindfulModuleType.MENTAL_MODEL,
    MindfulModuleType.VOCABULARY, MindfulModuleType.PHILOSOPHY, MindfulModuleType.ART_CULTURE, MindfulModuleType.FINANCE,
    MindfulModuleType.BREATHWORK,
)

private fun shortName(module: MindfulModuleType): String = when (module) {
    MindfulModuleType.QUOTE -> "Zitate"
    MindfulModuleType.BRAIN_PUZZLE -> "Gehirnjogging"
    MindfulModuleType.LANGUAGE -> "Sprache"
    MindfulModuleType.HISTORY -> "Geschichte"
    MindfulModuleType.MENTAL_MODEL -> "Denkmodelle"
    MindfulModuleType.BREATHWORK -> "Atmen"
    MindfulModuleType.SCIENCE -> "Wissenschaft"
    MindfulModuleType.FINANCE -> "Finanzen"
    MindfulModuleType.GEOGRAPHY -> "Geografie"
    MindfulModuleType.SPEED_MATH -> "Kopfrechnen"
    MindfulModuleType.ART_CULTURE -> "Kunst"
    MindfulModuleType.VOCABULARY -> "Wortschatz"
    MindfulModuleType.PHILOSOPHY -> "Philosophie"
}

private val LEVEL_NAMES = listOf("Aus", "Ab und zu", "Öfter", "Fokus")

/** Ein fertiges Profil: Stufe 0 (aus) bis 3 (Fokus) je Bereich; nicht genannte Bereiche sind aus. */
private data class Preset(val name: String, val levels: Map<MindfulModuleType, Int>) {
    fun level(module: MindfulModuleType) = levels[module] ?: 0
}

private val PRESETS = listOf(
    Preset(
        "Ausgewogen",
        MindfulModuleType.entries.associateWith { 1 } +
            mapOf(MindfulModuleType.LANGUAGE to 3, MindfulModuleType.BRAIN_PUZZLE to 2, MindfulModuleType.QUOTE to 2),
    ),
    Preset(
        "Sprachfokus",
        mapOf(
            MindfulModuleType.LANGUAGE to 3, MindfulModuleType.BRAIN_PUZZLE to 1, MindfulModuleType.SPEED_MATH to 1,
            MindfulModuleType.QUOTE to 1, MindfulModuleType.VOCABULARY to 1, MindfulModuleType.HISTORY to 1,
        ),
    ),
    Preset(
        "Gehirnjogging",
        mapOf(
            MindfulModuleType.BRAIN_PUZZLE to 3, MindfulModuleType.SPEED_MATH to 3, MindfulModuleType.LANGUAGE to 2,
            MindfulModuleType.MENTAL_MODEL to 1, MindfulModuleType.PHILOSOPHY to 1,
        ),
    ),
    Preset(
        "Wissen",
        mapOf(
            MindfulModuleType.HISTORY to 2, MindfulModuleType.SCIENCE to 2, MindfulModuleType.GEOGRAPHY to 2,
            MindfulModuleType.MENTAL_MODEL to 2, MindfulModuleType.VOCABULARY to 2, MindfulModuleType.PHILOSOPHY to 2,
            MindfulModuleType.ART_CULTURE to 2, MindfulModuleType.FINANCE to 2, MindfulModuleType.QUOTE to 1,
            MindfulModuleType.LANGUAGE to 2,
        ),
    ),
)

/**
 * Einstellungen des Feeds auf einer Seite: oben die Mischung einer Runde als Leiste, darunter
 * Schnellwahl-Profile und je Bereich eine Kachel. Antippen stellt eine Stufe höher, nach »Fokus«
 * kommt wieder »Aus«; lange drücken schaltet direkt aus oder an.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FeedSettingsSheet(
    enabledModules: Set<MindfulModuleType>,
    selectedLanguage: LearningLanguage,
    moduleRanks: Map<MindfulModuleType, ModuleRank>,
    streak: Int,
    completedCount: Int,
    totalCount: Int,
    feedSecondsToday: Int = 0,
    onToggleModule: (MindfulModuleType, Boolean) -> Unit,
    onSetModuleRank: (MindfulModuleType, ModuleRank) -> Unit,
    onSelectLanguage: (LearningLanguage) -> Unit,
    onResetDaily: () -> Unit,
    onResetLanguage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    fun levelOf(module: MindfulModuleType): Int =
        if (module !in enabledModules) 0 else (moduleRanks[module] ?: ModuleRank.RANK_1).rankNumber

    fun setLevel(module: MindfulModuleType, level: Int) {
        if (level == 0) {
            onToggleModule(module, false)
        } else {
            onSetModuleRank(module, ModuleRank.fromNumber(level))
            if (module !in enabledModules) onToggleModule(module, true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HikariBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.22f), width = 38.dp, height = 4.dp)
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp)
                .navigationBarsPadding(),
        ) {
            // ── Kopf: Titel und die drei Zahlen des Tages ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dein Feed", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatPill {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = HikariAmber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("$streak", color = HikariAmber, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                StatPill {
                    Text(
                        "$completedCount / $totalCount",
                        color = if (completedCount >= totalCount) HikariAmber else Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                StatPill { Text("${(feedSecondsToday + 30) / 60} Min.", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(16.dp))

            // ── Mischung einer Runde ──
            val weights = TILE_ORDER.filter { it in enabledModules }
                .associateWith { FeedComposer.weight(moduleRanks[it] ?: ModuleRank.RANK_1) }
            val total = weights.values.sum()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("So ist eine Runde gemischt", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (total == 0) "nichts an" else "$total Karten", color = HikariTextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    weights.forEach { (module, w) ->
                        val share by animateFloatAsState(w.toFloat(), tween(260), label = "share")
                        Box(Modifier.weight(share.coerceAtLeast(0.01f)).fillMaxHeight().background(module.accentColor))
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Die drei größten Anteile beim Namen, der Rest als eine Zahl — mehr liest hier niemand.
                val top = weights.entries.sortedByDescending { it.value }.take(3)
                val rest = total - top.sumOf { it.value }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    top.forEach { (module, w) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(module.accentColor))
                            Spacer(Modifier.width(5.dp))
                            Text("${shortName(module)} ${Math.round(w * 100f / total)} %", color = HikariText, fontSize = 11.5.sp)
                        }
                    }
                    if (rest > 0) Text("übrige ${Math.round(rest * 100f / total)} %", color = HikariTextMuted, fontSize = 11.5.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Schnellwahl ──
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { preset ->
                    val active = MindfulModuleType.entries.all { levelOf(it) == preset.level(it) }
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            MindfulModuleType.entries.forEach { setLevel(it, preset.level(it)) }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (active) HikariAmber else Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(0.6.dp, if (active) HikariAmber else Color.White.copy(alpha = 0.14f)),
                    ) {
                        Text(
                            preset.name,
                            color = if (active) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Antippen: mehr davon. Lange drücken: aus oder an.", color = HikariTextFaint, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))

            // ── Kacheln ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TILE_ORDER.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { module ->
                            ModuleTile(
                                module = module,
                                level = levelOf(module),
                                onCycle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    setLevel(module, (levelOf(module) + 1) % 4)
                                },
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    setLevel(module, if (levelOf(module) == 0) 1 else 0)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ── Sprache ──
            if (MindfulModuleType.LANGUAGE in enabledModules) {
                Spacer(Modifier.height(18.dp))
                Text("Sprache", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LearningLanguage.entries.forEach { language ->
                        val active = language == selectedLanguage
                        val accent = MindfulModuleType.LANGUAGE.accentColor
                        Surface(
                            onClick = { onSelectLanguage(language) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(0.6.dp, if (active) accent else Color.White.copy(alpha = 0.12f)),
                        ) {
                            Text(
                                language.title,
                                color = if (active) Color.White else HikariTextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Russisch ist an deinen Kurs angebunden: Übungen nach Lernstand, Kyrillisch, Statistik. Die anderen Sprachen haben nur einzelne Vokabelkarten.",
                    color = HikariTextFaint,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfirmButton("Heute zurücksetzen", onResetDaily, Modifier.weight(1f))
                ConfirmButton("Alphabet neu lernen", onResetLanguage, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(content: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Kachel eines Bereichs: Symbol in der Bereichsfarbe, Name, Stufe als Wort und als drei Balken. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleTile(
    module: MindfulModuleType,
    level: Int,
    onCycle: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = level > 0
    val accent = module.accentColor
    val fill by animateColorAsState(if (on) accent.copy(alpha = 0.07f + 0.045f * level) else Color.White.copy(alpha = 0.03f), label = "tile-fill")
    val edge by animateColorAsState(if (on) accent.copy(alpha = 0.25f + 0.2f * level) else Color.White.copy(alpha = 0.08f), label = "tile-edge")
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(0.8.dp, edge, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onCycle, onLongClick = onToggle)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (on) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(module.icon, contentDescription = null, tint = if (on) accent else HikariTextFaint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).alpha(if (on) 1f else 0.55f)) {
            Text(shortName(module), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(LEVEL_NAMES[level], color = if (on) accent else HikariTextMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
        // Drei Balken, von unten gefüllt — die Stufe auf einen Blick.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..3) {
                Box(
                    Modifier
                        .size(width = 5.dp, height = (8 + 5 * i).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= level) accent else Color.White.copy(alpha = 0.10f)),
                )
            }
        }
    }
}

/** Zweimal tippen statt Rückfrage-Dialog: Der erste Tipp fragt nach, der zweite führt aus. */
@Composable
private fun ConfirmButton(label: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(armed, done) {
        if (armed || done) {
            delay(2600)
            armed = false
            done = false
        }
    }
    Surface(
        onClick = {
            if (armed) {
                onConfirm()
                armed = false
                done = true
            } else {
                armed = true
            }
        },
        shape = RoundedCornerShape(12.dp),
        color = if (armed) Color(0xFFFB7185).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.6.dp, if (armed) Color(0xFFFB7185) else Color.White.copy(alpha = 0.10f)),
        modifier = modifier,
    ) {
        Text(
            when {
                done -> "Erledigt"
                armed -> "Sicher? Nochmal tippen"
                else -> label
            },
            color = if (armed) Color.White else HikariTextMuted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 11.dp).fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
