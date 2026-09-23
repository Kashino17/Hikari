package com.hikari.app.ui.russian

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.russian.RuExercise
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun RussianSessionScreen(
    onClose: () -> Unit,
    vm: RussianSessionViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val playing by vm.audio.playing.collectAsState()

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        val summary = ui.summary
        if (summary != null) {
            RuSummaryView(summary, onClose)
            return@Box
        }
        val step = ui.current ?: return@Box
        Column(Modifier.fillMaxSize()) {
            RuSessionTopBar(progress = ui.progress, onClose = { vm.audio.stop(); onClose() })
            key(ui.pos) {
                val env = RuStepEnv(
                    audio = vm.audio,
                    playing = playing,
                    answered = ui.feedback != null,
                    speechAvailable = vm.speechAvailable,
                    onAnswer = vm::answer,
                    onSkip = vm::skip,
                    onSpokenOk = vm::spokenCorrectly,
                    onNext = vm::next,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (step) {
                        is RuExercise.SoundTip -> SoundTipStep(step, env)
                        is RuExercise.Intro -> IntroStep(step, env)
                        is RuExercise.ListenChoose -> ListenChooseStep(step, env)
                        is RuExercise.MeaningChoose -> MeaningChooseStep(step, env)
                        is RuExercise.StressTap -> StressTapStep(step, env)
                        is RuExercise.Build -> BuildStep(step, env)
                        is RuExercise.Speak -> SpeakStep(step, env)
                        is RuExercise.Reply -> ReplyStep(step, env)
                        is RuExercise.Dialog -> DialogStep(step, env, onDone = vm::dialogDone)
                    }
                }
                // Untere Leiste: Weiter bei Lernkarten, Feedback nach Antworten.
                val fb = ui.feedback
                when {
                    step is RuExercise.SoundTip || step is RuExercise.Intro ->
                        RuPrimaryButton(
                            if (step is RuExercise.SoundTip) "Los geht's" else "Weiter",
                            modifier = Modifier.padding(16.dp),
                            onClick = vm::next,
                        )
                    fb != null -> RuFeedbackBar(fb, solutionFor(step), onNext = vm::next)
                }
            }
        }
    }
}

/** Was nach einer Antwort als Lösung gezeigt wird. */
private fun solutionFor(step: RuExercise): com.hikari.app.domain.russian.RuPhrase? = when (step) {
    is RuExercise.ListenChoose -> step.phrase
    is RuExercise.MeaningChoose -> step.phrase
    is RuExercise.StressTap -> step.phrase
    is RuExercise.Build -> step.phrase
    is RuExercise.Speak -> null
    is RuExercise.Reply -> null
    else -> null
}

@Composable
private fun RuFeedbackBar(fb: RuFeedback, solution: com.hikari.app.domain.russian.RuPhrase?, onNext: () -> Unit) {
    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 3 }) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(HikariCardBg)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (!fb.skipped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (fb.correct) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (fb.correct) RuGood else RuBad,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (fb.correct) "Richtig!" else "Noch nicht — die Lösung:",
                        color = if (fb.correct) RuGood else RuBad,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (solution != null) {
                    Spacer(Modifier.height(10.dp))
                    RuPhraseBlock(solution, cyrillicSize = 20.sp, center = false)
                }
                if (fb.requeued) {
                    Spacer(Modifier.height(4.dp))
                    Text("Kommt am Ende noch einmal.", color = HikariTextFaint, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
            }
            RuPrimaryButton("Weiter", onClick = onNext)
        }
    }
}

/** Abschluss einer Sitzung. */
@Composable
private fun RuSummaryView(s: RuSummary, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (s.empty) {
            Text("Alles erledigt", color = HikariText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.mode == RuSessionMode.REVIEW) "Gerade ist nichts zur Wiederholung fällig." else "Hier gibt es nichts zu tun.",
                color = HikariTextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            RuPrimaryButton("Zurück", onClick = onClose)
            return@Column
        }
        RuOverline(
            when (s.mode) {
                RuSessionMode.LESSON -> "Tag ${s.day} geschafft"
                RuSessionMode.REVIEW -> "Wiederholung fertig"
                RuSessionMode.DIALOG -> "Gespräch geschafft"
            },
            color = HikariAmber,
        )
        Spacer(Modifier.height(16.dp))
        Row {
            repeat(3) { i ->
                Icon(
                    if (i < s.stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (i < s.stars) HikariAmber else HikariTextFaint,
                    modifier = Modifier.size(44.dp).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when (s.stars) {
                3 -> "Отли́чно! Stark gemacht."
                2 -> "Хорошо́! Gut gemacht."
                else -> "Gut, dass du dran bleibst — Wiederholen hilft."
            },
            color = HikariText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("+${s.xp}", "XP", Modifier.weight(1f))
            SummaryTile(if (s.total == 0) "–" else "${(s.correct * 100) / s.total} %", "Treffer", Modifier.weight(1f))
            SummaryTile("${s.streak}", if (s.streak == 1) "Tag Serie" else "Tage Serie", Modifier.weight(1f), fire = true)
        }
        Spacer(Modifier.height(36.dp))
        RuPrimaryButton("Fertig", onClick = onClose)
    }
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier, fire: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fire) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = HikariAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(value, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = HikariTextMuted, fontSize = 11.5.sp)
    }
}

/** Umgebung, die jede Übung braucht. */
internal class RuStepEnv(
    val audio: RussianAudioPlayer,
    val playing: String?,
    val answered: Boolean,
    val speechAvailable: Boolean,
    val onAnswer: (Boolean) -> Unit,
    val onSkip: () -> Unit,
    val onSpokenOk: () -> Unit,
    val onNext: () -> Unit,
)

/** Scrollbarer Inhaltsbereich einer Übung. */
@Composable
internal fun RuStepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
internal fun RuStepTitle(overline: String, title: String? = null) {
    RuOverline(overline, Modifier.fillMaxWidth())
    if (title != null) {
        Spacer(Modifier.height(6.dp))
        Text(title, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
}

/** Hinweis-Karte (Notiz, wörtliche Übersetzung, Grammatik). */
@Composable
internal fun RuHintCard(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        RuOverline(title)
        Spacer(Modifier.height(6.dp))
        Text(text, color = HikariText.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Kleiner runder Mikrofon-Knopf mit Beschriftung. */
@Composable
internal fun RuMicBadge(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, if (active) HikariAmber else HikariBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(if (active) HikariAmber else Color.Transparent),
        )
        if (active) Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.RecordVoiceOver, null, tint = HikariText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Spielt beim Erscheinen einmal automatisch ab. */
@Composable
internal fun AutoPlay(env: RuStepEnv, name: String) {
    LaunchedEffect(name) { env.audio.play(name) }
}
