package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
 * Nachsprechen & Aussprachekontrolle: eigene Stimme aufnehmen, direkt mit dem
 * Original vergleichen (Shadowing) und optional per KI-Spracherkennung prüfen.
 */
@Composable
internal fun RuRecordCompare(p: RuPhrase, env: RuStepEnv) {
    val context = LocalContext.current
    val recorder = remember { RussianRecorder(context) }
    val recognizer = remember { RussianSpeechRecognizer(context) }
    var recording by remember { mutableStateOf(false) }
    var hasTake by remember { mutableStateOf(false) }
    var isComparing by remember { mutableStateOf(false) }
    val speechState by recognizer.state.collectAsState()
    var miniScore by remember { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            recognizer.destroy()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val act = pendingAction
            pendingAction = null
            act?.invoke()
        } else {
            pendingAction = null
        }
    }

    fun withMic(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleRecord() {
        withMic {
            if (recording) {
                recorder.stop()
                recording = false
                hasTake = true
                isComparing = true
                // Direkt vergleichen: erst du, dann das Original.
                env.audio.playFile(recorder.file) {
                    env.audio.play(p.audio) {
                        isComparing = false
                    }
                }
            } else {
                env.audio.stop()
                recognizer.stopListening()
                recording = recorder.start()
            }
        }
    }

    fun toggleListenCheck() {
        withMic {
            if (speechState is RuListenState.Listening || speechState is RuListenState.Partial) {
                recognizer.stopListening()
            } else {
                env.audio.stop()
                if (recording) {
                    recorder.stop()
                    recording = false
                }
                recognizer.reset()
                recognizer.start()
            }
        }
    }

    LaunchedEffect(speechState) {
        when (val s = speechState) {
            is RuListenState.Done -> {
                miniScore = RuAnswerCheck.speech(p.plain, s.hypotheses)
            }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            RuMicBadge(
                if (recording) "Stopp & vergleichen" else "Nachsprechen & vergleichen",
                active = recording,
            ) { toggleRecord() }

            val isListening = speechState is RuListenState.Listening || speechState is RuListenState.Partial
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isListening) HikariAmber.copy(alpha = 0.2f) else HikariCardBg)
                    .border(1.dp, if (isListening) HikariAmber else HikariBorder, RoundedCornerShape(22.dp))
                    .clickable { toggleListenCheck() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isListening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                        contentDescription = "KI-Prüfung",
                        tint = if (isListening) HikariAmber else HikariText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isListening) "Höre zu …" else "KI-Check",
                        color = if (isListening) HikariAmber else HikariText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (hasTake && !recording) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.dp, HikariBorderStrong, CircleShape)
                        .clickable {
                            env.audio.stop()
                            env.audio.playFile(recorder.file)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = "Meine Aufnahme", tint = HikariText, modifier = Modifier.size(18.dp))
                }

                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isComparing) HikariAmber.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (isComparing) HikariAmber else HikariBorderStrong, CircleShape)
                        .clickable {
                            env.audio.stop()
                            isComparing = true
                            env.audio.playFile(recorder.file) {
                                env.audio.play(p.audio) {
                                    isComparing = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Replay, contentDescription = "A/B-Vergleich", tint = if (isComparing) HikariAmber else HikariText, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Mini AI Score Anzeige falls in Intro geprüft wurde
        miniScore?.let { sc ->
            Spacer(Modifier.height(8.dp))
            val pct = (sc.score * 100).toInt()
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sc.passed) RuGood.copy(alpha = 0.12f) else HikariAmber.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (sc.passed) Icons.Outlined.CheckCircle else Icons.Outlined.Replay,
                    null,
                    tint = if (sc.passed) RuGood else HikariAmber,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (sc.passed) "Sehr gut! $pct %" else "Fast: $pct % (Nochmal versuchen)",
                    color = if (sc.passed) RuGood else HikariAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
    val recognizerState by recognizer.state.collectAsState()

    val recorder = remember { RussianRecorder(context) }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var hasRecordedVoice by remember { mutableStateOf(false) }
    var isComparingAudio by remember { mutableStateOf(false) }

    var attempts by remember { mutableIntStateOf(0) }
    var currentScore by remember { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var bestScore by remember { mutableDoubleStateOf(0.0) }
    var hasPassed by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val act = pendingAudioAction
            pendingAudioAction = null
            act?.invoke()
        } else {
            pendingAudioAction = null
        }
    }

    fun withMicPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAudioAction = action
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun startListening() {
        withMicPermission {
            env.audio.stop()
            if (isRecordingVoice) {
                recorder.stop()
                isRecordingVoice = false
            }
            recognizer.reset()
            recognizer.start()
        }
    }

    fun toggleVoiceRecording() {
        withMicPermission {
            if (isRecordingVoice) {
                recorder.stop()
                isRecordingVoice = false
                hasRecordedVoice = true
                isComparingAudio = true
                // Direkt vergleichen: erst deine Aufnahme, dann Muttersprachler
                env.audio.playFile(recorder.file) {
                    env.audio.play(p.audio) {
                        isComparingAudio = false
                    }
                }
            } else {
                env.audio.stop()
                recognizer.stopListening()
                isRecordingVoice = recorder.start()
            }
        }
    }

    LaunchedEffect(recognizerState) {
        when (val s = recognizerState) {
            is RuListenState.Done -> {
                val sc = RuAnswerCheck.speech(p.plain, s.hypotheses)
                currentScore = sc
                attempts++
                if (sc.score > bestScore) {
                    bestScore = sc.score
                }
                if (sc.passed) {
                    hasPassed = true
                }
            }
            else -> Unit
        }
    }

    fun advance(correct: Boolean) {
        if (advanced) return
        advanced = true
        recognizer.destroy()
        recorder.stop()
        if (correct) {
            env.onSpokenOk()
            env.onAnswer(true)
        } else {
            env.onSkip()
        }
        env.onNext()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RuStepTitle("Sprechen", "Sag es laut auf Russisch")
            RuPhraseBlock(p, cyrillicSize = 28.sp)
            Spacer(Modifier.height(16.dp))

            // Original Audio Player
            RuPlayRow(
                playingNow = env.playing == p.audio,
                onPlay = { env.audio.play(p.audio) },
                onSlow = { env.audio.play(p.audio, slow = true) },
                size = 56.dp,
            )

            Spacer(Modifier.height(26.dp))

            // ── 1. KI Aussprache-Prüfung ──────────────────────────────
            val listening = recognizerState is RuListenState.Listening || recognizerState is RuListenState.Partial
            val listeningPulse by animateFloatAsState(if (listening) 1.12f else 1f, tween(250), label = "mic-pulse")

            Box(
                Modifier
                    .size(86.dp)
                    .scale(listeningPulse)
                    .clip(CircleShape)
                    .background(if (listening) HikariAmber else Color.White)
                    .border(2.dp, if (listening) HikariAmber.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                    .clickable { if (listening) recognizer.stopListening() else startListening() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (listening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                    contentDescription = if (listening) "Stoppen" else "Sprechen",
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                when (val s = recognizerState) {
                    is RuListenState.Listening -> "Ich höre zu … Sprich jetzt"
                    is RuListenState.Partial -> "»${s.text}«"
                    else -> if (currentScore == null) "Tippen & auf Russisch sprechen" else "Tippen für weiteren Versuch"
                },
                color = if (listening) HikariAmber else HikariTextMuted,
                fontSize = 14.sp,
                fontWeight = if (listening) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )

            // Auswertung der Spracherkennung
            currentScore?.let { sc ->
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HikariCardBg)
                        .border(
                            1.dp,
                            if (sc.passed) RuGood.copy(alpha = 0.4f) else HikariAmber.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val scorePct = (sc.score * 100).toInt()
                    val badgeColor = if (sc.passed) RuGood else if (scorePct >= 50) HikariAmber else RuBad
                    val badgeText = when {
                        scorePct >= 95 -> "Ausgezeichnet! 100 %"
                        sc.passed -> "Sehr gut! $scorePct %"
                        scorePct > 0 -> "Fast da: $scorePct %"
                        else -> "Noch nicht verstanden"
                    }

                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (sc.passed) Icons.Outlined.CheckCircle else Icons.Outlined.Replay,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(badgeText, color = badgeColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sc.words.forEach { (word, ok) ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (ok) RuGood.copy(alpha = 0.12f) else RuBad.copy(alpha = 0.12f))
                                    .border(1.dp, if (ok) RuGood.copy(alpha = 0.4f) else RuBad.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    word,
                                    color = if (ok) RuGood else RuBad,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        if (sc.heard.isNotBlank()) "Gehört: »${sc.heard}«" else "Nichts gehört — sprich etwas lauter und näher ans Mikrofon.",
                        color = HikariTextMuted,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Versuch $attempts · Bester Score: ${(bestScore * 100).toInt()} %",
                        color = HikariTextFaint,
                        fontSize = 11.5.sp,
                    )
                }
            }

            // Fehlerhinweis bei Spracherkennung
            val failed = recognizerState as? RuListenState.Failed
            if (failed != null) {
                Spacer(Modifier.height(12.dp))
                RuHintCard(
                    "Hinweis zur Spracherkennung",
                    failed.message + if (failed.missingLanguage) {
                        " Du kannst in den Android-Einstellungen unter »Sprache & Eingabe ➔ Spracherkennung« das russische Offline-Sprachpaket herunterladen."
                    } else {
                        ""
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 2. Nachsprechen & Audio-Vergleich (Gleichzeitig da!) ──────
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HikariSurfaceHigh.copy(alpha = 0.5f))
                    .border(1.dp, HikariBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RuOverline("Eigenaufnahme & Audio-Vergleich")
                    if (hasRecordedVoice) {
                        Text("Aufnahme bereit", color = RuGood, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RuMicBadge(
                        if (isRecordingVoice) "Stopp & Vergleichen" else "Stimme aufnehmen",
                        active = isRecordingVoice,
                        onClick = { toggleVoiceRecording() },
                    )

                    if (hasRecordedVoice && !isRecordingVoice) {
                        Spacer(Modifier.width(10.dp))

                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(HikariCardBg)
                                .border(1.dp, HikariBorderStrong, CircleShape)
                                .clickable {
                                    env.audio.stop()
                                    env.audio.playFile(recorder.file)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = "Meine Aufnahme anhören",
                                tint = HikariText,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isComparingAudio) HikariAmber.copy(alpha = 0.2f) else HikariCardBg)
                                .border(1.dp, if (isComparingAudio) HikariAmber else HikariBorderStrong, CircleShape)
                                .clickable {
                                    env.audio.stop()
                                    isComparingAudio = true
                                    env.audio.playFile(recorder.file) {
                                        env.audio.play(p.audio) {
                                            isComparingAudio = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Replay,
                                contentDescription = "Direkter Vergleich (Ich ➔ Original)",
                                tint = if (isComparingAudio) HikariAmber else HikariText,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    if (hasRecordedVoice)
                        "Höre deine Aufnahme (👤) oder starte den direkten A/B-Vergleich (🔁)."
                    else
                        "Nimm deine Stimme auf und vergleiche Melodie & Betonung direkt mit dem Original.",
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
            }
        }

        // ── Untere Steuerleiste (User behält volle Kontrolle, beliebig viele Versuche) ──
        Column(Modifier.padding(16.dp)) {
            if (hasPassed) {
                RuPrimaryButton("Weiter ✓") {
                    advance(correct = true)
                }
                Spacer(Modifier.height(8.dp))
                RuGhostButton("Nochmal sprechen 🔄", Modifier.fillMaxWidth()) {
                    startListening()
                }
            } else if (attempts > 0 || hasRecordedVoice) {
                RuPrimaryButton("Klingt gut & weiter") {
                    advance(correct = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RuGhostButton("Nochmal sprechen 🔄", Modifier.weight(1f)) {
                        startListening()
                    }
                    RuGhostButton("Überspringen", Modifier.weight(1f)) {
                        advance(correct = false)
                    }
                }
            } else {
                RuGhostButton("Überspringen", Modifier.fillMaxWidth()) {
                    advance(correct = false)
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
