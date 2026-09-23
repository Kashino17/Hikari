package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hikari.app.domain.russian.RuAnswerCheck
import com.hikari.app.domain.russian.RuExercise
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.delay

// ── Aussprache-Tipp ──────────────────────────────────────────────────────────

@Composable
internal fun SoundTipStep(step: RuExercise.SoundTip, env: RuStepEnv) {
    RuStepColumn {
        RuStepTitle("Tag ${step.day.day} · ${step.day.title}", step.day.goal)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HikariCardBg)
                .border(1.dp, HikariBorder, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            RuOverline("Aussprache", color = HikariAmber)
            Spacer(Modifier.height(6.dp))
            Text(step.day.sound.title, color = HikariText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(step.day.sound.text, color = HikariText.copy(alpha = 0.88f), fontSize = 14.5.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(14.dp))
            step.examples.forEach { ex ->
                RuListenRow(ex, env)
                Spacer(Modifier.height(8.dp))
            }
        }
        step.day.grammar?.let {
            Spacer(Modifier.height(14.dp))
            RuHintCard("Satzbau", it)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Zeile mit Satz + Abspielknopf (Tipp auf die Zeile spielt ab). */
@Composable
internal fun RuListenRow(p: RuPhrase, env: RuStepEnv, showGerman: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariSurfaceHigh.copy(alpha = 0.5f))
            .clickable { env.audio.play(p.audio) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.display, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(translitAnnotated(p.ru), color = HikariTextMuted, fontSize = 13.5.sp)
            if (showGerman) Text(p.de, color = HikariTextFaint, fontSize = 12.5.sp)
        }
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = "Anhören",
            tint = if (env.playing == p.audio) HikariAmber else HikariTextMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Neue Karte ───────────────────────────────────────────────────────────────

@Composable
internal fun IntroStep(step: RuExercise.Intro, env: RuStepEnv) {
    val p = step.phrase
    AutoPlay(env, p.audio)
    var lens by remember { mutableStateOf(false) }
    RuStepColumn {
        RuStepTitle("Neu")
        Spacer(Modifier.height(8.dp))
        RuPhraseBlock(p, cyrillicSize = if (p.plain.length > 22) 26.sp else 32.sp)
        Spacer(Modifier.height(22.dp))
        RuPlayRow(
            playingNow = env.playing == p.audio,
            onPlay = { env.audio.play(p.audio) },
            onSlow = { env.audio.play(p.audio, slow = true) },
        )
        Spacer(Modifier.height(20.dp))
        p.lit?.let {
            RuHintCard("Wörtlich", it)
            Spacer(Modifier.height(10.dp))
        }
        p.note?.let {
            RuHintCard("Tipp", it)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            if (lens) "Buchstaben ausblenden" else "Buchstaben zeigen",
            color = HikariTextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { lens = !lens }.padding(8.dp),
        )
        if (lens) {
            Spacer(Modifier.height(6.dp))
            RuLetterLens(p.ru)
        }
        Spacer(Modifier.height(14.dp))
        RuRecordCompare(p, env)
    }
}

/**
 * Nachsprechen ohne Bewertung: eigene Stimme aufnehmen und direkt mit dem
 * Original vergleichen (Shadowing) — funktioniert auf jedem Gerät, offline.
 */
@Composable
internal fun RuRecordCompare(p: RuPhrase, env: RuStepEnv) {
    val context = LocalContext.current
    val recorder = remember { RussianRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var hasTake by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            env.audio.stop()
            recording = recorder.start()
        }
    }
    fun toggle() {
        if (recording) {
            recorder.stop()
            recording = false
            hasTake = true
            // Direkt vergleichen: erst du, dann das Original.
            env.audio.playFile(recorder.file) { env.audio.play(p.audio) }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            env.audio.stop()
            recording = recorder.start()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        RuMicBadge(if (recording) "Stopp & vergleichen" else "Nachsprechen & vergleichen", recording) { toggle() }
        if (hasTake && !recording) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).border(1.dp, HikariBorderStrong, CircleShape)
                    .clickable { env.audio.playFile(recorder.file) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Person, contentDescription = "Meine Aufnahme", tint = HikariText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Auswahl-Übungen ──────────────────────────────────────────────────────────

@Composable
internal fun ListenChooseStep(step: RuExercise.ListenChoose, env: RuStepEnv) {
    AutoPlay(env, step.phrase.audio)
    var picked by remember { mutableStateOf<String?>(null) }
    RuStepColumn {
        RuStepTitle("Hören", "Was bedeutet das?")
        RuPlayRow(
            playingNow = env.playing == step.phrase.audio,
            onPlay = { env.audio.play(step.phrase.audio) },
            onSlow = { env.audio.play(step.phrase.audio, slow = true) },
            size = 76.dp,
        )
        Spacer(Modifier.height(28.dp))
        step.options.forEach { o ->
            RuOptionCard(
                state = optionState(picked, o.id, step.phrase.id),
                onClick = {
                    if (picked == null) {
                        picked = o.id
                        env.onAnswer(o.id == step.phrase.id)
                    }
                },
            ) { Text(o.de, color = HikariText, fontSize = 16.sp) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun MeaningChooseStep(step: RuExercise.MeaningChoose, env: RuStepEnv) {
    var picked by remember { mutableStateOf<String?>(null) }
    RuStepColumn {
        RuStepTitle("Wie sagt man …")
        Text(step.phrase.de, color = HikariText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        step.options.forEach { o ->
            RuOptionCard(
                state = optionState(picked, o.id, step.phrase.id),
                onClick = {
                    if (picked == null) {
                        picked = o.id
                        env.onAnswer(o.id == step.phrase.id)
                        env.audio.play(step.phrase.audio)
                    }
                },
            ) {
                Column {
                    Text(o.display, color = HikariText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(translitAnnotated(o.ru), color = HikariTextMuted, fontSize = 13.5.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun optionState(picked: String?, id: String, correctId: String): RuOptionState = when {
    picked == null -> RuOptionState.Idle
    id == correctId -> RuOptionState.Correct
    id == picked -> RuOptionState.Wrong
    else -> RuOptionState.Dimmed
}

@Composable
internal fun ReplyStep(step: RuExercise.Reply, env: RuStepEnv) {
    AutoPlay(env, step.question.audio)
    var picked by remember { mutableStateOf<String?>(null) }
    var showDe by remember { mutableStateOf(false) }
    RuStepColumn {
        RuStepTitle("Antworte", "Was passt als Antwort?")
        RuPhraseBlock(step.question, cyrillicSize = 26.sp, showGerman = showDe || picked != null)
        Spacer(Modifier.height(14.dp))
        RuPlayRow(
            playingNow = env.playing == step.question.audio,
            onPlay = { env.audio.play(step.question.audio) },
            onSlow = { env.audio.play(step.question.audio, slow = true) },
            size = 56.dp,
        )
        if (!showDe && picked == null) {
            Text(
                "Übersetzung zeigen",
                color = HikariTextFaint,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { showDe = true }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        step.options.forEach { o ->
            val ok = o.id in step.correctIds
            val state = when {
                picked == null -> RuOptionState.Idle
                ok -> RuOptionState.Correct
                o.id == picked -> RuOptionState.Wrong
                else -> RuOptionState.Dimmed
            }
            RuOptionCard(
                state = state,
                onClick = {
                    if (picked == null) {
                        picked = o.id
                        env.onAnswer(ok)
                        env.audio.play(o.audio)
                    } else {
                        env.audio.play(o.audio)
                    }
                },
            ) {
                Column {
                    Text(o.display, color = HikariText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(translitAnnotated(o.ru), color = HikariTextMuted, fontSize = 13.5.sp)
                    if (picked != null) Text(o.de, color = HikariTextFaint, fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Betonung ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StressTapStep(step: RuExercise.StressTap, env: RuStepEnv) {
    AutoPlay(env, step.phrase.audio)
    var picked by remember { mutableIntStateOf(-1) }
    RuStepColumn {
        RuStepTitle("Betonung", "Welche Silbe ist betont?")
        Text(step.phrase.de, color = HikariTextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (RuLessonWordCount(step.phrase) > 1) {
            Spacer(Modifier.height(4.dp))
            Text(RussianPhonetics.plain(step.phrase.ru), color = HikariTextFaint, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        RuPlayRow(
            playingNow = env.playing == step.phrase.audio,
            onPlay = { env.audio.play(step.phrase.audio) },
            onSlow = { env.audio.play(step.phrase.audio, slow = true) },
        )
        Spacer(Modifier.height(30.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            step.syllables.forEachIndexed { i, syl ->
                val state = when {
                    picked < 0 -> RuOptionState.Idle
                    i == step.stressedIndex -> RuOptionState.Correct
                    i == picked -> RuOptionState.Wrong
                    else -> RuOptionState.Dimmed
                }
                val border = when (state) {
                    RuOptionState.Correct -> RuGood
                    RuOptionState.Wrong -> RuBad
                    else -> HikariBorderStrong
                }
                Box(
                    Modifier
                        .heightIn(min = 64.dp)
                        .widthIn(min = 64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (state == RuOptionState.Correct) RuGood.copy(alpha = 0.12f) else HikariCardBg)
                        .border(1.5.dp, border, RoundedCornerShape(14.dp))
                        .clickable {
                            if (picked < 0) {
                                picked = i
                                env.onAnswer(i == step.stressedIndex)
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(syl, color = HikariText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Hör auf die Silbe, die länger und lauter klingt.",
            color = HikariTextFaint,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun RuLessonWordCount(p: RuPhrase) = p.wordCount

// ── Satz bauen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuildStep(step: RuExercise.Build, env: RuStepEnv) {
    // Indizes der gewählten Kacheln (Wörter können doppelt vorkommen).
    val chosen = remember { mutableStateListOf<Int>() }
    var result by remember { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RuStepTitle("Satz bauen", "Übersetze ins Russische")
            Text(step.phrase.de, color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            // Antwortzeile
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        when (result) {
                            true -> RuGood
                            false -> RuBad
                            null -> HikariBorderStrong
                        },
                        RoundedCornerShape(14.dp),
                    )
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chosen.forEach { idx ->
                    RuTile(step.tiles[idx], selected = true) {
                        if (result == null) chosen.remove(idx)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                step.tiles.forEachIndexed { idx, word ->
                    val used = idx in chosen
                    RuTile(word, selected = false, ghost = used) {
                        if (result == null && !used) {
                            chosen.add(idx)
                            env.audio.stop()
                        }
                    }
                }
            }
        }
        if (result == null) {
            RuPrimaryButton(
                "Prüfen",
                modifier = Modifier.padding(16.dp),
                enabled = chosen.isNotEmpty(),
            ) {
                val ok = RuAnswerCheck.buildCorrect(step, chosen.map { step.tiles[it] })
                result = ok
                env.onAnswer(ok)
                env.audio.play(step.phrase.audio)
            }
        }
    }
}

@Composable
private fun RuTile(word: String, selected: Boolean, ghost: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (ghost) Color.Transparent else if (selected) HikariSurfaceHigh else HikariCardBg)
            .border(1.dp, if (ghost) HikariBorder else HikariBorderStrong, RoundedCornerShape(12.dp))
            .clickable(enabled = !ghost, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val alpha = if (ghost) 0f else 1f
        Text(word, color = HikariText.copy(alpha = alpha), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            translitAnnotated(toPlus(word)),
            color = HikariTextMuted.copy(alpha = alpha * HikariTextMuted.alpha),
            fontSize = 12.sp,
        )
    }
}

/** Akut-Schreibweise (Anzeige) zurück in "+"-Notation für die Umschrift. */
private fun toPlus(display: String): String {
    val sb = StringBuilder()
    for (c in display) {
        if (c == '́' && sb.isNotEmpty()) {
            val v = sb.last()
            sb.setLength(sb.length - 1)
            sb.append('+').append(v)
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}

// ── Sprechen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeakStep(step: RuExercise.Speak, env: RuStepEnv) {
    val p = step.phrase
    val context = LocalContext.current
    val recognizer = remember { RussianSpeechRecognizer(context) }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }
    val state by recognizer.state.collectAsState()
    var attempts by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var selfCheck by remember { mutableStateOf(!env.speechAvailable) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            env.audio.stop()
            recognizer.start()
        } else {
            selfCheck = true
        }
    }
    LaunchedEffect(state) {
        val s = state
        if (s is RuListenState.Done) {
            val sc = RuAnswerCheck.speech(p.plain, s.hypotheses)
            score = sc
            attempts++
            if (sc.passed && !env.answered) {
                env.onSpokenOk()
                env.onAnswer(true)
            }
        }
        if (s is RuListenState.Failed) selfCheck = true
    }
    fun listen() {
        score = null
        recognizer.reset()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            env.audio.stop()
            recognizer.start()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RuStepTitle("Sprechen", "Sag es laut")
            RuPhraseBlock(p, cyrillicSize = 28.sp)
            Spacer(Modifier.height(18.dp))
            RuPlayRow(
                playingNow = env.playing == p.audio,
                onPlay = { env.audio.play(p.audio) },
                onSlow = { env.audio.play(p.audio, slow = true) },
                size = 56.dp,
            )
            Spacer(Modifier.height(30.dp))
            if (!selfCheck) {
                val listening = state is RuListenState.Listening || state is RuListenState.Partial
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(if (listening) HikariAmber else Color.White)
                        .clickable(enabled = !env.answered) { if (listening) recognizer.stopListening() else listen() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (listening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                        contentDescription = "Sprechen",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when (val s = state) {
                        is RuListenState.Listening -> "Ich höre zu …"
                        is RuListenState.Partial -> s.text
                        else -> if (score == null) "Tippen und sprechen" else ""
                    },
                    color = HikariTextMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                score?.let { sc ->
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        sc.words.forEach { (w, ok) ->
                            Text(w, color = if (ok) RuGood else RuBad, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            sc.passed -> "Verstanden!"
                            sc.heard.isBlank() -> "Ich habe nichts verstanden — noch mal, etwas lauter."
                            else -> "Gehört: »${sc.heard}«"
                        },
                        color = if (sc.passed) RuGood else HikariTextMuted,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val failed = state as? RuListenState.Failed
                if (failed != null) {
                    RuHintCard(
                        "Spracherkennung",
                        failed.message + if (failed.missingLanguage) {
                            " Du kannst in den Android-Einstellungen unter »Spracherkennung« Russisch als Offline-Sprache laden."
                        } else {
                            ""
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    "Nimm dich auf und vergleiche selbst mit dem Original.",
                    color = HikariTextMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                RuRecordCompare(p, env)
            }
        }
        if (!env.answered) {
            Column(Modifier.padding(16.dp)) {
                if (selfCheck) {
                    RuPrimaryButton("Klingt wie das Original") {
                        env.onAnswer(true)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!selfCheck && attempts >= 2) {
                        RuGhostButton("Selbst vergleichen", Modifier.weight(1f)) { selfCheck = true }
                    }
                    RuGhostButton("Überspringen", Modifier.weight(1f)) { env.onSkip() }
                }
            }
        }
    }
}

// ── Rollenspiel ──────────────────────────────────────────────────────────────

@Composable
internal fun DialogStep(step: RuExercise.Dialog, env: RuStepEnv, onDone: (Int, Int) -> Unit) {
    val lines = step.lines
    var started by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(0) }
    var firstTryRight by remember { mutableIntStateOf(0) }
    val wrongPicks = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val userLines = lines.count { it.first.isUser }

    // Partnerzeilen laufen automatisch; bei deinen Zeilen wartet das Gespräch.
    // Solange ein Satz klingt (busy), startet nichts Neues — sonst schnitte
    // der nächste Partnersatz deinen gerade gesprochenen ab.
    LaunchedEffect(started, shown, busy) {
        if (!started || busy || shown >= lines.size) return@LaunchedEffect
        val (line, phrase) = lines[shown]
        if (!line.isUser) {
            delay(350)
            busy = true
            env.audio.play(phrase.audio) {
                busy = false
                shown++
            }
        }
    }
    LaunchedEffect(shown) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            RuOverline("Rollenspiel · ${step.day.dialog.title}")
            Spacer(Modifier.height(6.dp))
            Text(step.day.dialog.scene, color = HikariTextMuted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            lines.take(shown).forEach { (line, phrase) ->
                RuBubble(phrase, mine = line.isUser, partner = step.day.dialog.partner, enabled = !busy, env = env)
                Spacer(Modifier.height(10.dp))
            }
        }
        val current = lines.getOrNull(shown)
        when {
            !started -> Column(Modifier.padding(16.dp)) {
                Text(
                    "${step.day.dialog.partner} spricht, du antwortest. Wähl deine Antwort — danach hörst du sie. Sprich sie am besten laut mit.",
                    color = HikariTextMuted,
                    fontSize = 13.5.sp,
                )
                Spacer(Modifier.height(12.dp))
                RuPrimaryButton("Gespräch starten") { started = true }
            }
            current == null -> Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RuGhostButton("Ganz anhören", Modifier.weight(1f)) { playAll(env, lines.map { it.second.audio }) }
                    RuPrimaryButton("Weiter", Modifier.weight(1f)) { onDone(firstTryRight, userLines) }
                }
            }
            current.first.isUser && !busy -> {
                val (line, phrase) = current
                val options = remember(shown) {
                    val others = lines.filter { it.first.isUser && it.second.plain != phrase.plain }
                        .map { it.second }.distinctBy { it.plain }.shuffled().take(2)
                    (others + phrase).shuffled()
                }
                Column(Modifier.background(HikariCardBg).padding(16.dp)) {
                    RuOverline("Du willst sagen")
                    Spacer(Modifier.height(4.dp))
                    Text(phrase.de, color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    options.forEach { o ->
                        val wrong = o.id + o.plain in wrongPicks
                        RuOptionCard(
                            state = if (wrong) RuOptionState.Wrong else RuOptionState.Idle,
                            onClick = {
                                if (busy || wrong) return@RuOptionCard
                                if (o.plain == phrase.plain) {
                                    if (wrongPicks.isEmpty()) firstTryRight++
                                    wrongPicks.clear()
                                    busy = true
                                    shown++
                                    env.audio.play(phrase.audio) { busy = false }
                                } else {
                                    wrongPicks += o.id + o.plain
                                }
                            },
                        ) {
                            Column {
                                Text(o.display, color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(translitAnnotated(o.ru), color = HikariTextMuted, fontSize = 12.5.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (line.gloss != null) Text(line.gloss, color = HikariTextFaint, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun playAll(env: RuStepEnv, names: List<String>) {
    if (names.isEmpty()) return
    env.audio.play(names.first()) { playAll(env, names.drop(1)) }
}

/** Sprechblase im Rollenspiel — Tipp spielt erneut ab. */
@Composable
private fun RuBubble(p: RuPhrase, mine: Boolean, partner: String, enabled: Boolean, env: RuStepEnv) {
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 16.dp,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) HikariSurfaceHigh else HikariCardBg)
                .border(1.dp, if (env.playing == p.audio) HikariAmber.copy(alpha = 0.6f) else HikariBorder, shape)
                .clickable(enabled = enabled) { env.audio.play(p.audio) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(if (mine) "Du" else partner, color = HikariTextFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(p.display, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(translitAnnotated(p.ru), color = HikariTextMuted, fontSize = 13.sp)
            Text(p.de, color = HikariTextFaint, fontSize = 12.5.sp)
            p.note?.let { Text(it, color = HikariAmber.copy(alpha = 0.8f), fontSize = 11.5.sp) }
        }
    }
}
