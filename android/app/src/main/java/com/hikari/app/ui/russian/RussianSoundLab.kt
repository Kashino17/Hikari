package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.hikari.app.domain.russian.RuAnswerCheck
import com.hikari.app.domain.russian.RuLabGroup
import com.hikari.app.domain.russian.RuLabPair
import com.hikari.app.domain.russian.RuLabWord
import com.hikari.app.domain.russian.RuLessonBuilder
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.RussianSoundLabRepository
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val HEAR_ROUNDS = 8

@HiltViewModel
class RussianSoundLabViewModel @Inject constructor(
    repo: RussianSoundLabRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    val groups: List<RuLabGroup> = repo.groups
    val progress = store.state

    fun finish(group: RuLabGroup, percent: Int) {
        val today = store.today()
        val xp = 5 + percent / 10
        store.update {
            it.copy(
                labBest = it.labBest + (group.id to maxOf(it.labBest[group.id] ?: 0, percent)),
                xp = it.xp + xp,
                xpByDay = it.xpByDay + (today to it.xpOn(today) + xp),
            )
        }
    }

    override fun onCleared() {
        audio.stop()
    }
}

/** Lautlabor: Minimalpaare erst hören und unterscheiden, dann sprechen. */
@Composable
fun RussianSoundLabScreen(onClose: () -> Unit, vm: RussianSoundLabViewModel = hiltViewModel()) {
    var open by remember { mutableStateOf<RuLabGroup?>(null) }
    val group = open
    if (group != null) {
        LabSession(group, vm, onClose = { open = null })
        return
    }
    val progress by vm.progress.collectAsState()
    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(progress = 0f, onClose = onClose)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                RuStepTitle("Lautlabor", "Unterscheide ähnliche Laute — dann sprich sie")
                Spacer(Modifier.height(16.dp))
            }
            items(vm.groups, key = { it.id }) { g ->
                val best = progress.labBest[g.id]
                RuOptionCard(RuOptionState.Idle, onClick = { open = g }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                g.pairs.joinToString("  ·  ") { "${it.a.display} / ${it.b.display}" },
                                color = HikariTextMuted,
                                fontSize = 12.5.sp,
                            )
                        }
                        if (best != null) {
                            Text("$best %", color = if (best >= 80) RuGood else HikariAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private data class HearQ(val pair: RuLabPair, val playedA: Boolean) {
    val played: RuLabWord get() = if (playedA) pair.a else pair.b
}

private enum class Phase { Intro, Hear, Speak, Done }

@Composable
private fun LabSession(group: RuLabGroup, vm: RussianSoundLabViewModel, onClose: () -> Unit) {
    val rng = remember(group.id) { Random(System.nanoTime()) }
    var phase by remember(group.id) { mutableStateOf(Phase.Intro) }
    val questions = remember(group.id) {
        // Jedes Wort jedes Paars einmal, gemischt; bei wenigen Paaren auf HEAR_ROUNDS aufgefüllt.
        val base = group.pairs.flatMap { listOf(HearQ(it, true), HearQ(it, false)) }.shuffled(rng)
        (base + base.shuffled(rng)).take(minOf(HEAR_ROUNDS, base.size * 2))
    }
    var q by remember(group.id) { mutableIntStateOf(0) }
    var hearRight by remember(group.id) { mutableIntStateOf(0) }
    var speakRight by remember(group.id) { mutableIntStateOf(0) }
    val speakWords = remember(group.id) { group.pairs.flatMap { listOf(it.a to it.b, it.b to it.a) }.shuffled(rng).take(6) }
    var s by remember(group.id) { mutableIntStateOf(0) }

    val total = questions.size + speakWords.size
    val done = q + s
    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = if (phase == Phase.Intro) 0f else if (phase == Phase.Done) 1f else done.toFloat() / total,
            onClose = { vm.audio.stop(); onClose() },
        )
        when (phase) {
            Phase.Intro -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                Text(group.title, color = HikariText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(group.tip, color = HikariTextMuted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(24.dp))
                group.pairs.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PairWordCard(p.a, vm, Modifier.weight(1f))
                        PairWordCard(p.b, vm, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(24.dp))
                RuPrimaryButton("Los: Hören") { phase = Phase.Hear }
            }

            Phase.Hear -> HearPhase(
                question = questions[q],
                round = q + 1,
                rounds = questions.size,
                vm = vm,
                onAnswered = { right ->
                    if (right) hearRight++
                },
                onNext = {
                    if (q + 1 < questions.size) q++ else phase = Phase.Speak
                },
            )

            Phase.Speak -> SpeakPhase(
                group = group,
                word = speakWords[s].first,
                partner = speakWords[s].second,
                vm = vm,
                onResult = { ok -> if (ok) speakRight++ },
                onNext = {
                    if (s + 1 < speakWords.size) s++ else {
                        val percent = ((hearRight + speakRight) * 100) / total
                        vm.finish(group, percent)
                        phase = Phase.Done
                    }
                },
            )

            Phase.Done -> {
                val percent = ((hearRight + speakRight) * 100) / total
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RuOverline("Geschafft", color = HikariAmber)
                    Spacer(Modifier.height(10.dp))
                    Text("$percent %", color = HikariText, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Gehört: $hearRight/${questions.size}  ·  Gesprochen: $speakRight/${speakWords.size}",
                        color = HikariTextMuted,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(32.dp))
                    RuPrimaryButton("Nochmal") {
                        q = 0; s = 0; hearRight = 0; speakRight = 0; phase = Phase.Hear
                    }
                    Spacer(Modifier.height(10.dp))
                    RuGhostButton("Zurück", Modifier.fillMaxWidth(), onClose)
                }
            }
        }
    }
}

@Composable
private fun PairWordCard(w: RuLabWord, vm: RussianSoundLabViewModel, modifier: Modifier = Modifier) {
    val playing by vm.audio.playing.collectAsState()
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .clickable { vm.audio.play(w.clip) }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            w.display,
            color = if (playing == w.clip) HikariAmber else HikariText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(w.de, color = HikariTextMuted, fontSize = 12.5.sp)
    }
}

@Composable
private fun HearPhase(
    question: HearQ,
    round: Int,
    rounds: Int,
    vm: RussianSoundLabViewModel,
    onAnswered: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    var picked by remember(round) { mutableStateOf<Boolean?>(null) } // true = A gewählt
    val playing by vm.audio.playing.collectAsState()
    LaunchedEffect(round) { vm.audio.play(question.played.clip) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        RuOverline("Hören  $round / $rounds")
        Spacer(Modifier.height(18.dp))
        RuPlayRow(
            playingNow = playing == question.played.clip,
            onPlay = { vm.audio.play(question.played.clip) },
            onSlow = { vm.audio.play(question.played.clip, slow = true) },
            size = 84.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text("Welches Wort hast du gehört?", color = HikariTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        listOf(true to question.pair.a, false to question.pair.b).forEach { (isA, word) ->
            val state = when {
                picked == null -> RuOptionState.Idle
                isA == question.playedA -> RuOptionState.Correct
                isA == picked -> RuOptionState.Wrong
                else -> RuOptionState.Dimmed
            }
            RuOptionCard(state, onClick = {
                if (picked == null) {
                    picked = isA
                    onAnswered(isA == question.playedA)
                }
            }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(word.display, color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(12.dp))
                    Text(word.de, color = HikariTextMuted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (picked != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (picked == question.playedA) "Richtig." else "Gespielt wurde »${question.played.display}« — ${question.played.de}.",
                color = if (picked == question.playedA) RuGood else RuBad,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            RuPrimaryButton("Weiter", onClick = onNext)
        }
    }
}

@Composable
private fun SpeakPhase(
    group: RuLabGroup,
    word: RuLabWord,
    partner: RuLabWord,
    vm: RussianSoundLabViewModel,
    onResult: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val recognizer = remember { RussianSpeechRecognizer(context) }
    val recorder = remember { RussianRecorder(context) }
    DisposableEffect(Unit) { onDispose { recognizer.destroy(); recorder.stop() } }
    val state by recognizer.state.collectAsState()
    val playing by vm.audio.playing.collectAsState()
    var verdict by remember(word.ru) { mutableStateOf<String?>(null) }
    var passed by remember(word.ru) { mutableStateOf(false) }
    var evaluating by remember(word.ru) { mutableStateOf(false) }
    var recording by remember(word.ru) { mutableStateOf(false) }
    var countedOnce by remember(word.ru) { mutableStateOf(false) }
    var takeStamp by remember(word.ru) { mutableStateOf(0L) }
    // Spracherkennung kann Betonungspaare nicht unterscheiden → hier Aufnahme & Vergleich.
    val useRecorder = group.isStress

    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val act = pending
        pending = null
        if (granted) act?.invoke()
    }
    fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pending = action
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state) {
        val st = state
        if (st is RuListenState.Done || st is RuListenState.Failed) evaluating = false
        if (st is RuListenState.Done) {
            val target = RuLessonBuilder.normWord(word.plain)
            val other = RuLessonBuilder.normWord(partner.plain)
            val heardAll = st.hypotheses.flatMap { RuLessonBuilder.norm(it).split(' ') }.filter { it.isNotBlank() }
            val hitTarget = heardAll.any { RuAnswerCheck.similarity(it, target) >= 0.99 }
            val hitPartner = heardAll.any { it == other }
            when {
                hitTarget -> {
                    verdict = "Genau — das war »${word.display}«."
                    passed = true
                    if (!countedOnce) { countedOnce = true; onResult(true) }
                }
                hitPartner -> verdict = "Das klang wie »${partner.display}«. ${group.tip}"
                heardAll.isEmpty() -> verdict = "Nichts verstanden — nochmal, etwas lauter."
                else -> verdict = "Gehört: »${heardAll.first()}«. Hör das Original nochmal und versuche es erneut."
            }
        }
        if (st is RuListenState.Failed) verdict = st.message
    }

    val listening = state is RuListenState.Listening || state is RuListenState.Partial
    val active = listening || recording
    val pulse by animateFloatAsState(if (active) 1.12f else 1f, tween(250), label = "lab-mic")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        RuOverline("Sprechen")
        Spacer(Modifier.height(16.dp))
        Text(word.display, color = HikariText, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
        Text(word.de, color = HikariTextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        RuPlayRow(
            playingNow = playing == word.clip,
            onPlay = { vm.audio.play(word.clip) },
            onSlow = { vm.audio.play(word.clip, slow = true) },
            size = 64.dp,
        )
        Spacer(Modifier.height(26.dp))
        Box(
            Modifier
                .size(86.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(if (active) HikariAmber else Color.White)
                .clickable(enabled = !evaluating) {
                    withMic {
                        vm.audio.stop()
                        if (useRecorder) {
                            if (recording) {
                                recorder.stop()
                                recording = false
                                takeStamp = System.currentTimeMillis()
                                if (!countedOnce) { countedOnce = true; onResult(true) }
                                passed = true
                                verdict = "Erst du, dann das Original — hörst du den Unterschied?"
                                vm.audio.playFile(recorder.file) { vm.audio.play(word.clip) }
                            } else {
                                recording = recorder.start()
                            }
                        } else if (listening) {
                            evaluating = true
                            recognizer.stopListening()
                        } else {
                            verdict = null
                            recognizer.reset()
                            recognizer.start()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                contentDescription = "Sprechen",
                tint = Color.Black,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                evaluating -> "Wird ausgewertet …"
                listening -> "Ich höre zu … Tippe nochmal, wenn du fertig bist"
                recording -> "Aufnahme läuft … Tippe nochmal zum Vergleichen"
                verdict != null -> verdict!!
                useRecorder -> "Tippen, Wort sprechen, nochmal tippen"
                else -> "Tippen und das Wort sagen"
            },
            color = when {
                passed -> RuGood
                active -> HikariAmber
                verdict != null -> HikariText
                else -> HikariTextMuted
            },
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        if (useRecorder && takeStamp > 0L && !recording) {
            Spacer(Modifier.height(16.dp))
            RuProsodyCard(word.clip, recorder.file, takeStamp)
            Spacer(Modifier.height(8.dp))
            RuPhonemeCard(word.clip, recorder.file, takeStamp)
        }
        Spacer(Modifier.height(24.dp))
        RuPrimaryButton(if (passed) "Weiter" else "Überspringen") {
            vm.audio.stop()
            recognizer.reset()
            onNext()
        }
        Spacer(Modifier.height(20.dp))
    }
}
