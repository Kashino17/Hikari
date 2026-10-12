package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.hikari.app.domain.russian.RuLessonBuilder
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RuSessionResult
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.apply
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random

private const val ROUNDS = 6

/** Eine Frage der Partnerin und alles, was darauf als Antwort gelten darf. */
data class RuTalkRound(
    val question: RuPhrase,
    /** Passende, inhaltliche Antworten (für Hilfe und Feedback). */
    val replies: List<RuPhrase>,
    /** Zusätzlich akzeptierte Allzweck-Reaktionen (Да, Конечно …). */
    val universal: List<RuPhrase>,
) {
    val accepted: List<RuPhrase> get() = replies + universal
}

@HiltViewModel
class RussianTalkViewModel @Inject constructor(
    repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    val rounds: List<RuTalkRound> = run {
        val index = repo.index
        val progress = store.state.value
        val settings = progress.settings
        val unlocked = progress.unlockedDay(index.days.size)
        val universal = RuLessonBuilder.UNIVERSAL_REACTIONS.mapNotNull { index.phrase(it, settings) }
        index.days.filter { it.day <= unlocked }
            .flatMap { d -> d.items.filter { it.replies.isNotEmpty() } }
            .shuffled(Random(System.nanoTime()))
            .mapNotNull { item ->
                val q = index.phrase(item.id, settings) ?: return@mapNotNull null
                val replies = item.replies.mapNotNull { index.phrase(it, settings) }
                if (replies.isEmpty()) null else RuTalkRound(q, replies, universal)
            }
            .take(ROUNDS)
    }

    fun credit(round: RuTalkRound, matched: RuPhrase) {
        val today = store.today()
        store.update {
            it.apply(
                RuSessionResult(xp = 10, graded = mapOf(round.question.id to true, matched.id to true), spokenOk = 1),
                today,
            )
        }
    }

    override fun onCleared() {
        audio.stop()
    }
}

/** Sprech-Gespräch: Partnerin fragt, du antwortest frei — ohne Auswahl. */
@Composable
fun RussianTalkScreen(onClose: () -> Unit, vm: RussianTalkViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) started = true
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        RuSessionTopBar(
            progress = if (!started || vm.rounds.isEmpty()) 0f else (if (finished) 1f else round.toFloat() / vm.rounds.size),
            onClose = { vm.audio.stop(); onClose() },
        )
        when {
            vm.rounds.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Noch zu wenig Stoff", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Schließ zuerst ein paar Tage ab, dann kann ich dir Fragen stellen.",
                    color = HikariTextMuted, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                RuPrimaryButton("Zurück", onClick = onClose)
            }

            !started -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sprich frei", color = HikariText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Du hörst eine Frage und antwortest laut auf Russisch — ohne Auswahl, ohne Lesen. " +
                        "Mehrere Antworten sind richtig. Das Mikro startet von selbst.",
                    color = HikariTextMuted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                RuPrimaryButton("Start") {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        started = true
                    } else {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }

            finished -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RuOverline("Gespräch beendet", color = HikariAmber)
                Spacer(Modifier.height(10.dp))
                Text("$correct / ${vm.rounds.size}", color = HikariText, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Antworten passten", color = HikariTextMuted, fontSize = 15.sp)
                Spacer(Modifier.height(32.dp))
                RuPrimaryButton("Nochmal") { round = 0; correct = 0; finished = false }
                Spacer(Modifier.height(10.dp))
                RuGhostButton("Fertig", Modifier.fillMaxWidth(), onClose)
            }

            else -> TalkRound(
                key = round,
                data = vm.rounds[round],
                vm = vm,
                onFinish = { ok ->
                    if (ok) correct++
                    if (round + 1 < vm.rounds.size) round++ else finished = true
                },
            )
        }
    }
}

@Composable
private fun TalkRound(key: Int, data: RuTalkRound, vm: RussianTalkViewModel, onFinish: (Boolean) -> Unit) {
    val context = LocalContext.current
    val recognizer = remember(key) { RussianSpeechRecognizer(context) }
    DisposableEffect(key) { onDispose { recognizer.destroy() } }
    val state by recognizer.state.collectAsState()
    val playing by vm.audio.playing.collectAsState()
    var score by remember(key) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var matched by remember(key) { mutableStateOf<RuPhrase?>(null) }
    var showHelp by remember(key) { mutableStateOf(false) }
    var evaluating by remember(key) { mutableStateOf(false) }
    var attempts by remember(key) { mutableIntStateOf(0) }
    var failure by remember(key) { mutableStateOf<String?>(null) }

    fun listen() {
        score = null
        matched = null
        failure = null
        recognizer.reset()
        recognizer.start()
    }

    // Frage automatisch stellen, danach hört die App von selbst zu.
    LaunchedEffect(key) {
        vm.audio.play(data.question.audio) { listen() }
    }

    LaunchedEffect(state) {
        val st = state
        if (st is RuListenState.Done || st is RuListenState.Failed) evaluating = false
        if (st is RuListenState.Failed) failure = st.message
        if (st is RuListenState.Done) {
            attempts++
            val scored = data.accepted.map { it to RuAnswerCheck.speech(it.plain, st.hypotheses) }
            val pass = scored.filter { it.second.passed }.maxByOrNull { it.second.score }
            if (pass != null) {
                matched = pass.first
                score = pass.second
                vm.credit(data, pass.first)
            } else {
                // Beste Annäherung zeigen, damit der Laut-Coach Hinweise geben kann.
                score = scored.maxByOrNull { it.second.score }?.second
            }
        }
    }

    val listening = state is RuListenState.Listening || state is RuListenState.Partial
    val pulse by animateFloatAsState(if (listening) 1.12f else 1f, tween(250), label = "talk-mic")
    val sc = score
    val m = matched

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        RuStepTitle("Anja fragt", "Antworte auf Russisch")
        RuPlayRow(
            playingNow = playing == data.question.audio,
            onPlay = { vm.audio.play(data.question.audio) { if (m == null) listen() } },
            onSlow = { vm.audio.play(data.question.audio, slow = true) { if (m == null) listen() } },
            size = 64.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(data.question.de, color = HikariTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Box(
            Modifier
                .size(86.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(if (listening) HikariAmber else Color.White)
                .clickable(enabled = !evaluating && m == null) {
                    if (listening) {
                        evaluating = true
                        recognizer.stopListening()
                    } else {
                        vm.audio.stop()
                        listen()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                contentDescription = "Antworten",
                tint = Color.Black,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                m != null -> "Passt — das war eine gute Antwort."
                evaluating -> "Wird ausgewertet …"
                failure != null -> failure!!
                state is RuListenState.Partial -> "»${(state as RuListenState.Partial).text}«"
                listening -> "Ich höre zu … sprich jetzt"
                playing == data.question.audio -> "Hör zu …"
                sc != null -> "Das hat noch nicht gepasst — nochmal versuchen oder Hilfe ansehen."
                else -> "Tippe aufs Mikro und antworte"
            },
            color = if (m != null) RuGood else if (listening) HikariAmber else HikariTextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        if (m != null) {
            Spacer(Modifier.height(14.dp))
            Text(m.display, color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(m.de, color = HikariTextMuted, fontSize = 14.sp)
        } else if (sc != null && attempts > 0) {
            Spacer(Modifier.height(14.dp))
            RuSpeechScoreCard(sc, large = true)
        }

        if (m == null && (showHelp || attempts >= 2)) {
            Spacer(Modifier.height(16.dp))
            RuOverline("So könntest du antworten")
            Spacer(Modifier.height(8.dp))
            data.replies.take(3).forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable { vm.audio.play(r.audio) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.display, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(r.de, color = HikariTextMuted, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (m != null) {
            RuPrimaryButton("Weiter") { vm.audio.stop(); onFinish(true) }
        } else {
            if (!showHelp && attempts < 2) {
                RuGhostButton("Hilfe: mögliche Antworten", Modifier.fillMaxWidth()) {
                    vm.audio.stop()
                    showHelp = true
                }
                Spacer(Modifier.height(8.dp))
            }
            RuGhostButton("Überspringen", Modifier.fillMaxWidth()) { vm.audio.stop(); onFinish(false) }
        }
        Spacer(Modifier.height(20.dp))
    }
}
