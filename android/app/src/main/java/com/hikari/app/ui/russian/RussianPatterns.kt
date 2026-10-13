package com.hikari.app.ui.russian

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.russian.RuDeepdiveLogic
import com.hikari.app.domain.russian.RuGender
import com.hikari.app.domain.russian.RuMySentence
import com.hikari.app.domain.russian.RuPattern
import com.hikari.app.domain.russian.RuPatternLogic
import com.hikari.app.domain.russian.RuSceneLine
import com.hikari.app.domain.russian.RuSessionResult
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.apply
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RussianPatternsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    val patterns: List<RuPattern> = repo.patterns
    val pattern: RuPattern? = savedState.get<String>("id")?.let { id -> patterns.firstOrNull { it.id == id } }
    val progress = store.state
    val gender: RuGender get() = store.state.value.settings.gender

    init {
        viewModelScope.launch { audio.warmUp() }
    }

    fun save(s: RuMySentence) = store.update { it.copy(mySentences = it.mySentences.filterNot { x -> x.ru == s.ru } + s) }

    fun remove(ru: String) = store.update { it.copy(mySentences = it.mySentences.filterNot { x -> x.ru == ru }) }

    fun complete(id: String) = store.update {
        it.apply(RuSessionResult(xp = if (id in it.patternsDone) 5 else 25, graded = emptyMap()), store.today())
            .copy(patternsDone = it.patternsDone + id)
    }

    override fun onCleared() {
        audio.stop()
    }
}

/** Übersicht der Grammatik-Muster, nach Woche gruppiert. */
@Composable
fun RussianPatternListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    vm: RussianPatternsViewModel = hiltViewModel(),
) {
    val progress by vm.progress.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(HikariBg), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
                }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Satzbau", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Muster, mit denen du eigene Sätze baust", color = HikariTextMuted, fontSize = 12.5.sp)
                }
            }
        }
        item {
            Text(
                "Jedes Muster beginnt mit einer Szene voller Gefühl. Danach siehst du die Regel, baust eigene Sätze und sprichst frei. " +
                    "${progress.patternsDone.size} von ${vm.patterns.size} geschafft.",
                color = HikariTextMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        val byWeek = vm.patterns.groupBy { it.week }.toSortedMap()
        byWeek.forEach { (week, list) ->
            item(key = "w$week") { RuOverline("Woche $week", Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp)) }
            items(list, key = { it.id }) { p ->
                val done = p.id in progress.patternsDone
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HikariCardBg)
                        .border(1.dp, if (done) HikariAmber.copy(alpha = 0.5f) else HikariBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpen(p.id) }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title, color = HikariText, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (done) Icon(Icons.Default.Check, "Geschafft", tint = HikariAmber, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(mk(p.formula), color = HikariAmber, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.emotion.isNotBlank()) Tag(p.emotion)
                        Text(p.scene.title, color = HikariTextFaint, fontSize = 12.5.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
        }
    }
}

private val STEPS = listOf("Szene", "Muster", "Bauen", "Sprechen")

/** Eine Muster-Lektion in vier Schritten: Szene hören, Muster verstehen, eigene Sätze bauen, frei sprechen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RussianPatternScreen(onBack: () -> Unit, vm: RussianPatternsViewModel = hiltViewModel()) {
    val p = vm.pattern
    val playing by vm.audio.playing.collectAsState()
    val progress by vm.progress.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val gender = vm.gender
    if (p == null) {
        Box(Modifier.fillMaxSize().background(HikariBg), contentAlignment = Alignment.Center) {
            Text("Muster nicht gefunden.", color = HikariTextMuted)
        }
        return
    }
    val choices = remember(p.id) { mutableStateListOf<Int>().also { l -> repeat(p.slots.size) { l.add(0) } } }
    var note by remember(p.id) { mutableStateOf("") }
    val built = RuPatternLogic.build(p, choices, gender)
    val saved = progress.mySentences.any { it.ru == built.ru }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Column(Modifier.padding(start = 4.dp).weight(1f)) {
                Text(p.title, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STEPS.forEachIndexed { i, name ->
                Column(Modifier.weight(1f).clickable { step = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) HikariAmber else HikariBorder),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = if (i == step) HikariText else HikariTextFaint, fontSize = 11.5.sp)
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            when (step) {
                0 -> SceneStep(p, vm, gender, playing)
                1 -> PatternStep(p, vm, playing)
                2 -> BuildStep(
                    p = p, vm = vm, playing = playing, choices = choices, gender = gender, built = built,
                    note = note, onNote = { note = it }, saved = saved,
                    onSave = { vm.save(built.copy(note = note.trim())) }, onUnsave = { vm.remove(built.ru) },
                )
                else -> FreeStep(p, vm, playing, built)
            }
        }

        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            val last = step == STEPS.lastIndex
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariAmber)
                    .clickable {
                        vm.audio.stop()
                        if (last) {
                            vm.complete(p.id)
                            onBack()
                        } else {
                            step++
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (last) "Fertig" else "Weiter", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SceneStep(p: RuPattern, vm: RussianPatternsViewModel, gender: RuGender, playing: String?) {
    val lines = p.scene.lines
    fun playFrom(i: Int) {
        if (i >= lines.size) return
        val clip = RuPatternLogic.lineClip(lines[i], gender)
        if (!vm.audio.has(clip)) playFrom(i + 1) else vm.audio.play(clip) { playFrom(i + 1) }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (p.emotion.isNotBlank()) Tag(p.emotion)
        Spacer(Modifier.height(8.dp))
        Text(p.scene.title, color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(p.scene.setting, color = HikariTextMuted, fontSize = 14.sp, fontStyle = FontStyle.Italic, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        DeepChip("Ganze Szene anhören", false) { vm.audio.stop(); playFrom(0) }
        Spacer(Modifier.height(14.dp))
        lines.forEach { l -> SceneBubble(l, gender, vm.audio, playing); Spacer(Modifier.height(10.dp)) }
        Text(
            "Tipp: Sprich die Zeilen mit dem beschriebenen Gefühl mit — erst dadurch bleiben sie hängen.",
            color = HikariTextFaint,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun SceneBubble(l: RuSceneLine, gender: RuGender, audio: RussianAudioPlayer, playing: String?) {
    val (ru, de) = RuPatternLogic.line(l, gender)
    val clip = RuPatternLogic.lineClip(l, gender)
    val user = l.isUser
    var speak by remember(ru) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (user) HikariAmber.copy(alpha = 0.13f) else HikariCardBg)
                .border(1.dp, if (playing == clip) HikariAmber else HikariBorder, RoundedCornerShape(16.dp))
                .clickable { if (audio.has(clip)) audio.play(clip) }
                .padding(14.dp),
        ) {
            Text(if (user) "Du" else "Anja", color = if (user) HikariAmber else HikariTextFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(mk(ru), color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(translitAnnotated(ru), color = HikariTextMuted, fontSize = 12.5.sp)
            Text(de, color = HikariTextFaint, fontSize = 13.sp)
            if (l.how.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Gefühl: ${l.how}", color = HikariAmber.copy(alpha = 0.9f), fontSize = 12.5.sp, fontStyle = FontStyle.Italic)
            }
            if (user) {
                Spacer(Modifier.height(8.dp))
                DeepChip(if (speak) "Prüfung zu" else "Nachsprechen", speak) { speak = !speak }
                if (speak) {
                    Spacer(Modifier.height(8.dp))
                    PhraseSpeakCheck(RuDeepdiveLogic.asPhrase(ru, de))
                }
            }
        }
    }
}

@Composable
private fun PatternStep(p: RuPattern, vm: RussianPatternsViewModel, playing: String?) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Das Muster", color = HikariTextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HikariAmber.copy(alpha = 0.14f))
                .border(1.dp, HikariAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(18.dp),
        ) {
            Text(mk(p.formula), color = HikariAmber, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(p.hook, color = HikariText, fontSize = 15.sp, lineHeight = 22.sp)
        Section("So klingt es", "Hör zu, sprich nach — dann sitzt das Muster.")
        p.core.forEach { c ->
            Box(Modifier.padding(vertical = 5.dp)) {
                PhraseCard(RuDeepdiveLogic.asPhrase(c.ru, c.de), vm.audio, playing, explain = c.why.takeIf { it.isNotBlank() }?.let { "Warum so: ${mk(it)}" })
            }
        }
        Section("Die Regel dahinter", "Damit baust du Sätze, die du nie gehört hast.")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HikariCardBg)
                .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Text(mk(p.explain), color = HikariText, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildStep(
    p: RuPattern,
    vm: RussianPatternsViewModel,
    playing: String?,
    choices: MutableList<Int>,
    gender: RuGender,
    built: RuMySentence,
    note: String,
    onNote: (String) -> Unit,
    saved: Boolean,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Bau deinen Satz", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Tausche die Bausteine — jede Kombination ist ein richtiger russischer Satz.", color = HikariTextMuted, fontSize = 13.5.sp)
        p.slots.forEachIndexed { si, slot ->
            Spacer(Modifier.height(16.dp))
            Text(slot.label, color = HikariTextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slot.options.forEachIndexed { oi, o ->
                    val ru = if (gender == RuGender.FEMALE && o.ruF != null) o.ruF else o.ru
                    DeepChip(mk(ru), choices[si] == oi) { choices[si] = oi }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PhraseCard(RuDeepdiveLogic.asPhrase(built.ru, built.de), vm.audio, playing, highlight = true)
        Spacer(Modifier.height(12.dp))
        Text("Wem würdest du das sagen? (optional)", color = HikariTextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariCardBg)
                .border(1.dp, HikariBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            if (note.isEmpty()) Text("z. B. meiner Mama, meiner Freundin …", color = HikariTextFaint, fontSize = 14.sp)
            BasicTextField(
                value = note,
                onValueChange = onNote,
                singleLine = true,
                textStyle = TextStyle(color = HikariText, fontSize = 14.sp),
                cursorBrush = SolidColor(HikariAmber),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        DeepChip(if (saved) "Gespeichert ✓ — tippen zum Entfernen" else "In »Meine Sätze« speichern", saved) {
            if (saved) onUnsave() else onSave()
        }
    }
}

@Composable
private fun FreeStep(p: RuPattern, vm: RussianPatternsViewModel, playing: String?, built: RuMySentence) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Jetzt du", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(p.free.prompt, color = HikariText, fontSize = 16.sp, lineHeight = 23.sp)
        Section("Zum Orientieren", "Ein Beispiel — sag es mit deinen eigenen Worten und deinem Gefühl.")
        PhraseCard(RuDeepdiveLogic.asPhrase(p.free.model.ru, p.free.model.de), vm.audio, playing, highlight = true)
        Section("Dein gebauter Satz", "Sprich ihn laut, mit dem Gefühl aus der Szene.")
        PhraseCard(RuDeepdiveLogic.asPhrase(built.ru, built.de), vm.audio, playing)
        Spacer(Modifier.height(14.dp))
        Text(
            "Wenn du „Fertig“ tippst, zählt das Muster als geschafft. Du kannst es jederzeit wiederholen.",
            color = HikariTextFaint,
            fontSize = 12.5.sp,
        )
    }
}
