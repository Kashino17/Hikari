package com.hikari.app.ui.tuning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import com.hikari.app.domain.model.FilterConfig
import com.hikari.app.domain.model.TuningCatalogs
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private enum class Tab { FILTER, PROMPT, SYSTEM }

@Composable
fun TuningScreen(
    onBack: () -> Unit = {},
    vm: TuningViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()
    var tab by remember { mutableStateOf(Tab.FILTER) }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            Header(tab = tab, onTab = { tab = it }, onBack = onBack)

            if (saving) {
                LinearProgressIndicator(
                    color = HikariAmber,
                    trackColor = HikariBorder,
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            when (tab) {
                Tab.FILTER -> FilterTab(state?.filter, onUpdate = vm::updateFilter)
                Tab.PROMPT -> PromptTab(
                    promptOverride = state?.promptOverride,
                    assembledPrompt = state?.assembledPrompt ?: "",
                    onSetOverride = vm::setOverride,
                    onClearOverride = vm::clearOverride,
                )
                Tab.SYSTEM -> SystemTab(vm)
            }
        }
    }
}

@Composable
private fun Header(tab: Tab, onTab: (Tab) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = HikariText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Column {
                Text("Tuning", style = MaterialTheme.typography.titleMedium, color = HikariText)
                Text(
                    "Was die KI für dich aussortiert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HikariTextFaint,
                )
            }
        }
        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
        Row(modifier = Modifier.fillMaxWidth()) {
            Tab.values().forEach { t ->
                val active = tab == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTab(t) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            t.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (active) HikariAmber else HikariTextMuted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp,
                            ),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    if (active) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(HikariAmber),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
    }
}

// ─── FILTER TAB ──────────────────────────────────────────────────────────────

@Composable
private fun FilterTab(filter: FilterConfig?, onUpdate: ((FilterConfig) -> FilterConfig) -> Unit) {
    if (filter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Lade…", color = HikariTextFaint)
        }
        return
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Section("Themen die du magst", "Tippen, Enter zum Hinzufügen.") {
            ChipFreeInput(
                values = filter.likeTags,
                onChange = { v -> onUpdate { it.copy(likeTags = v) } },
                placeholder = "z.B. Mathematik, Geschichte…",
            )
        }
        Section("Themen die du nicht magst", "Wird hart ausgeschlossen.") {
            ChipFreeInput(
                values = filter.dislikeTags,
                onChange = { v -> onUpdate { it.copy(dislikeTags = v) } },
                placeholder = "z.B. Drama, Reaction…",
            )
        }
        Section("Stimmung", "Mehrfachauswahl.") {
            ChipMulti(
                options = TuningCatalogs.moodOptions,
                values = filter.moodTags,
                onChange = { v -> onUpdate { it.copy(moodTags = v) } },
            )
        }
        Section("Stil", "Wie tief soll's gehen.") {
            ChipMulti(
                options = TuningCatalogs.depthOptions,
                values = filter.depthTags,
                onChange = { v -> onUpdate { it.copy(depthTags = v) } },
            )
        }
        Section("Sprachen") {
            ChipMulti(
                options = TuningCatalogs.languageOptions.map { it.first },
                values = filter.languages,
                onChange = { v -> onUpdate { it.copy(languages = v) } },
                renderLabel = { code ->
                    TuningCatalogs.languageOptions.firstOrNull { it.first == code }?.second ?: code
                },
            )
        }
        Section(
            "Dauer",
            "${filter.minDurationSec / 60}–${filter.maxDurationSec / 60} Minuten",
        ) {
            LabeledSlider(
                label = "Min",
                value = filter.minDurationSec.toFloat(),
                range = 30f..1800f,
                steps = ((1800 - 30) / 30) - 1,
                valueLabel = "${filter.minDurationSec / 60}m",
                onValueChange = { v ->
                    onUpdate { it.copy(minDurationSec = v.toInt().coerceAtMost(it.maxDurationSec)) }
                },
            )
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Max",
                value = filter.maxDurationSec.toFloat(),
                range = 300f..7200f,
                steps = ((7200 - 300) / 60) - 1,
                valueLabel = "${filter.maxDurationSec / 60}m",
                onValueChange = { v ->
                    onUpdate { it.copy(maxDurationSec = v.toInt().coerceAtLeast(it.minDurationSec)) }
                },
            )
        }
        Section("Mindest-Score", "Videos unterhalb tauchen nicht im Feed auf.") {
            LabeledSlider(
                label = null,
                value = filter.scoreThreshold.toFloat(),
                range = 0f..100f,
                steps = 19,
                valueLabel = filter.scoreThreshold.toString(),
                accentLabel = true,
                onValueChange = { v -> onUpdate { it.copy(scoreThreshold = v.toInt()) } },
            )
        }
        Section("Beispiele", "Optional. Klartext, gerne mit Titeln.") {
            BasicTextField(
                value = filter.examples,
                onValueChange = { v -> onUpdate { it.copy(examples = v) } },
                textStyle = TextStyle(color = HikariText, fontSize = 12.sp, lineHeight = 18.sp),
                cursorBrush = SolidColor(HikariAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .background(HikariSurface, RoundedCornerShape(8.dp))
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (filter.examples.isEmpty()) {
                        Text(
                            "z.B. „But what is a Neural Network?\" von 3Blue1Brown — strukturiert, mathematisch, kein Hype.",
                            color = HikariTextFaint,
                            style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Section(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = HikariTextFaint,
        )
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = HikariTextFaint)
        }
        Spacer(Modifier.height(if (hint == null) 10.dp else 12.dp))
        content()
    }
    HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)
}

@Composable
private fun LabeledSlider(
    label: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    accentLabel: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(
                label,
                color = HikariTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.size(width = 40.dp, height = 24.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = HikariAmber,
                activeTrackColor = HikariAmber,
                inactiveTrackColor = HikariBorder,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            valueLabel,
            color = if (accentLabel) HikariAmber else HikariTextMuted,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.size(width = 44.dp, height = 18.dp),
        )
    }
}

// ─── PROMPT TAB ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PromptTab(
    promptOverride: String?,
    assembledPrompt: String,
    onSetOverride: (String) -> Unit,
    onClearOverride: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(promptOverride, assembledPrompt) {
        mutableStateOf(promptOverride ?: assembledPrompt)
    }
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (promptOverride != null) "Manueller Override aktiv"
                else "Live aus dem Filter generiert",
                color = if (promptOverride != null) HikariAmber else HikariTextFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (!editing) {
                if (promptOverride != null) {
                    PromptAction("Auto wiederherstellen") { onClearOverride() }
                    Spacer(Modifier.size(12.dp))
                }
                PromptAction("Kopieren") {
                    clipboard.setText(AnnotatedString(promptOverride ?: assembledPrompt))
                }
                Spacer(Modifier.size(12.dp))
                PromptAction("Bearbeiten") {
                    draft = promptOverride ?: assembledPrompt
                    editing = true
                }
            } else {
                PromptAction("Verwerfen") { editing = false }
                Spacer(Modifier.size(12.dp))
                PromptAction("Speichern", accent = true) {
                    if (draft != assembledPrompt) onSetOverride(draft)
                    else onClearOverride()
                    editing = false
                }
            }
        }
        HorizontalDivider(color = HikariBorder, thickness = 0.5.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(
                        color = HikariText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                    ),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp)
                        .background(HikariSurface, RoundedCornerShape(8.dp))
                        .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                )
            } else {
                Text(
                    promptOverride ?: assembledPrompt,
                    color = HikariText.copy(alpha = 0.85f),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PromptAction(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (accent) HikariAmber else HikariTextMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        )
    }
}

// ─── SYSTEM TAB ──────────────────────────────────────────────────────────────

@Composable
private fun SystemTab(vm: TuningViewModel) {
    val backendUrl by vm.backendUrl.collectAsState()
    val dailyBudget by vm.dailyBudget.collectAsState()
    val sb by vm.sbBehaviors.collectAsState()

    var urlDraft by remember(backendUrl) { mutableStateOf(backendUrl) }
    var budgetDraft by remember(dailyBudget) { mutableIntStateOf(dailyBudget) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Section("Backend", "Server-URL.") {
            BasicTextField(
                value = urlDraft,
                onValueChange = {
                    urlDraft = it
                    vm.setBackendUrl(it)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = HikariText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(HikariAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HikariSurface, RoundedCornerShape(6.dp))
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Section("Tagesbudget", "Bis zu $budgetDraft Videos pro Tag werden gescort.") {
            LabeledSlider(
                label = null,
                value = budgetDraft.toFloat(),
                range = 5f..50f,
                steps = 44,
                valueLabel = budgetDraft.toString(),
                accentLabel = true,
                onValueChange = {
                    budgetDraft = it.toInt()
                    vm.setDailyBudget(budgetDraft)
                },
            )
        }

        Section("SponsorBlock", "A = Auto · M = Manuell · I = Ignorieren") {
            SegmentCategories.all.forEach { cat ->
                val current = sb[cat.apiKey] ?: cat.defaultBehavior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat.label, color = HikariText, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            cat.description,
                            color = HikariTextFaint,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    BehaviorPicker(current = current, onPick = { vm.setSbBehavior(cat.apiKey, it) })
                }
            }
        }
        Section("Manga") {
            val mangaStatus by vm.mangaSyncStatus.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(6.dp))
                    .clickable { vm.triggerMangaSync() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("Manga sync now", color = HikariText.copy(alpha = 0.9f), fontSize = 13.sp)
            }
            mangaStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BehaviorPicker(current: SegmentBehavior, onPick: (SegmentBehavior) -> Unit) {
    Row(
        modifier = Modifier
            .background(HikariSurface, RoundedCornerShape(6.dp))
            .border(0.5.dp, HikariBorder, RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        listOf(
            SegmentBehavior.SKIP_AUTO to "A",
            SegmentBehavior.SKIP_MANUAL to "M",
            SegmentBehavior.IGNORE to "I",
        ).forEach { (b, letter) ->
            val active = current == b
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 28.dp)
                    .background(if (active) HikariAmber else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { onPick(b) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    color = if (active) Color.Black else HikariTextMuted,
                    style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}
