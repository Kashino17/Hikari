package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.feed.BuildGame
import com.hikari.app.domain.feed.ChainMathGame
import com.hikari.app.domain.feed.ChoiceGame
import com.hikari.app.domain.feed.ChoiceStyle
import com.hikari.app.domain.feed.FeedGame
import com.hikari.app.domain.feed.LetterIntroGame
import com.hikari.app.domain.feed.MemoryGridGame
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.OddOneOutGame
import com.hikari.app.domain.feed.PairsGame
import com.hikari.app.domain.feed.RuAlphabetLogic
import com.hikari.app.domain.feed.StroopGame
import com.hikari.app.domain.russian.RuLessonBuilder
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.ui.russian.RuInterlinear
import com.hikari.app.ui.russian.RuLetterLens
import com.hikari.app.ui.russian.RussianAudioPlayer
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.util.Locale
import kotlinx.coroutines.delay

private val Wrong = Color(0xFFFB7185)
private val Tile = Color.White.copy(alpha = 0.07f)
private val TileBorder = Color.White.copy(alpha = 0.14f)

private fun gameColor(game: FeedGame): Color = when (game.module) {
    MindfulModuleType.LANGUAGE -> Color(0xFF3B82F6)
    MindfulModuleType.SPEED_MATH -> Color(0xFFF43F5E)
    else -> Color(0xFF10B981)
}

/** Meldet ein beendetes Spiel: richtig?, eigene Überschrift (sonst Standard), Zusatz-Satz. */
private typealias Finish = (ok: Boolean, headline: String?, note: String) -> Unit

/** Ein Mini-Spiel im gemeinsamen Feed-Rahmen, mit Rückmeldung und »Weiter« nach dem Ende. */
@Composable
internal fun FeedGameSlide(
    game: FeedGame,
    chrome: FeedChrome,
    isDone: Boolean,
    active: Boolean,
    audio: RussianAudioPlayer?,
    letterProgress: Map<String, Int>,
    showTranslit: Boolean,
    onFinish: (Boolean) -> GameOutcome,
    onNext: () -> Unit,
) {
    var outcome by remember(game.id) { mutableStateOf<GameOutcome?>(null) }
    var headline by remember(game.id) { mutableStateOf<String?>(null) }
    var note by remember(game.id) { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val finish: Finish = { ok, title, text ->
        if (outcome == null) {
            headline = title
            note = text
            outcome = onFinish(ok)
            haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        }
    }

    FeedSlideFrame(
        chrome = chrome,
        slideKey = game.id,
        ambientColor = gameColor(game),
        icon = game.module.icon,
        label = game.label,
        topFocus = false,
        isDone = isDone || outcome != null,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val done = outcome != null
            when (game) {
                is ChoiceGame -> ChoiceGameContent(game, active, audio, showTranslit, finish)
                is LetterIntroGame -> LetterIntroContent(game, audio, letterProgress, finish)
                is BuildGame -> BuildGameContent(game, audio, done, showTranslit, finish)
                is PairsGame -> PairsGameContent(game, audio, showTranslit, finish)
                is StroopGame -> StroopContent(game, finish)
                is MemoryGridGame -> MemoryGridContent(game, finish)
                is ChainMathGame -> ChainMathContent(game, finish)
                is OddOneOutGame -> OddOneOutContent(game, active, done, finish)
            }
            AnimatedVisibility(
                visible = done,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            ) {
                outcome?.let { OutcomeBar(it, headline, note, onNext) }
            }
        }
    }
}

/** Die Rückmeldung nach jedem Spiel: was war, was es bewirkt hat, und weiter geht's. */
@Composable
private fun OutcomeBar(outcome: GameOutcome, headline: String?, note: String, onNext: () -> Unit) {
    val color = if (outcome.correct) HikariAmber else Wrong
    val body = listOf(note, outcome.message).filter { it.isNotBlank() }.joinToString(" ")
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.10f))
                .border(0.8.dp, color.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (outcome.correct) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    headline ?: if (outcome.correct) "Richtig" else "Nicht ganz",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(body, color = HikariText, fontSize = 12.5.sp, lineHeight = 18.sp)
                }
            }
            if (outcome.xp > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "+${outcome.xp} XP",
                    color = HikariAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HikariAmber.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onNext,
            shape = RoundedCornerShape(14.dp),
            color = HikariAmber,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weiter", color = Color.Black, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Bausteine ───────────────────────────────────────────────────────────────

private enum class OptionState { IDLE, CORRECT, WRONG, DIM }

@Composable
private fun OptionButton(
    text: String,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    fontSize: TextUnit = 15.sp,
    minHeight: Dp = 48.dp,
) {
    val border by animateColorAsState(
        when (state) {
            OptionState.CORRECT -> HikariAmber
            OptionState.WRONG -> Wrong
            else -> TileBorder
        },
        label = "option-border",
    )
    val fill by animateColorAsState(
        when (state) {
            OptionState.CORRECT -> HikariAmber.copy(alpha = 0.16f)
            OptionState.WRONG -> Wrong.copy(alpha = 0.14f)
            else -> Tile
        },
        label = "option-fill",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = fill,
        border = BorderStroke(if (state == OptionState.IDLE || state == OptionState.DIM) 0.6.dp else 1.2.dp, border),
        modifier = modifier.heightIn(min = minHeight),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text,
                color = if (state == OptionState.DIM) HikariTextMuted else Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = fontSize * 1.25f,
            )
            if (!sub.isNullOrBlank()) {
                Text(sub, color = HikariTextMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(0.8.dp, if (enabled) HikariAmber.copy(alpha = 0.7f) else TileBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            color = if (enabled) Color.White else HikariTextFaint,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 13.dp),
        )
    }
}

@Composable
private fun ListenButton(clip: String, audio: RussianAudioPlayer?, size: Dp = 44.dp, slow: Boolean = false) {
    val playing = audio?.playing?.collectAsState()?.value == clip
    Surface(
        onClick = { audio?.play(clip, slow) },
        shape = CircleShape,
        color = if (playing) HikariAmber.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.09f),
        border = BorderStroke(0.8.dp, if (playing) HikariAmber else TileBorder),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Anhören",
                tint = if (playing) HikariAmber else Color.White,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
private fun Prompt(text: String) {
    Text(
        text,
        color = HikariTextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
    )
}

private fun seconds(ms: Long) = String.format(Locale.GERMANY, "%.1f", ms / 1000f)

// ── Auswahl: Zahlenreihe, Buchstabe, Lesewort, Satz, Hören ──────────────────

@Composable
private fun ChoiceGameContent(game: ChoiceGame, active: Boolean, audio: RussianAudioPlayer?, showTranslit: Boolean, finish: Finish) {
    var picked by remember(game.id) { mutableStateOf<Int?>(null) }
    val answered = picked != null
    // Russisch in der Frage (nicht in den Antworten): Anhören ist schon vor der Antwort erlaubt.
    val russianQuestion = game.style == ChoiceStyle.PHRASE && game.optionSubs.isEmpty()
    val clip = game.audio?.takeIf { audio?.has(it) == true }

    LaunchedEffect(active, game.id) {
        if (active && game.style == ChoiceStyle.LISTEN && picked == null && clip != null) {
            delay(350)
            audio?.play(clip)
        }
    }

    ContextLine(game.context)
    Prompt(game.prompt)
    Spacer(Modifier.height(14.dp))

    when (game.style) {
        ChoiceStyle.LISTEN -> if (clip != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ListenButton(clip, audio, size = 72.dp)
                Surface(
                    onClick = { audio?.play(clip, slow = true) },
                    shape = RoundedCornerShape(12.dp),
                    color = Tile,
                    border = BorderStroke(0.6.dp, TileBorder),
                ) {
                    Text("langsam", color = HikariText, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        else -> game.display?.let { display ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    display,
                    color = Color.White,
                    fontSize = when (game.style) {
                        ChoiceStyle.LETTER -> 68.sp
                        ChoiceStyle.WORD -> 40.sp
                        ChoiceStyle.NUMBERS -> 24.sp
                        else -> if (display.length > 28) 21.sp else 26.sp
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = if (game.style == ChoiceStyle.LETTER) 76.sp else 34.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (russianQuestion && clip != null) {
                    Spacer(Modifier.width(12.dp))
                    ListenButton(clip, audio, size = 40.dp)
                }
            }
            // Lesehilfe, solange das Alphabet noch nicht sitzt.
            if (showTranslit && !game.displaySub.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(game.displaySub, color = HikariTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
    Spacer(Modifier.height(18.dp))

    val grid = game.options.all { it.length <= 8 }
    val onPick: (Int) -> Unit = { i ->
        if (picked == null) {
            picked = i
            // Sehen und hören zugleich prägt sich besser ein — nach der Antwort einmal vorspielen.
            if (clip != null && game.style != ChoiceStyle.LISTEN) audio?.play(clip)
            // Bei reinen Wissensfragen die Lösung nennen; bei Russisch zeigt die Auflösung darunter sie ohnehin.
            val solution = if (i != game.correctIndex && game.revealRu == null) "Richtig ist ${game.options[game.correctIndex].substringBefore("  ·  ")}." else ""
            finish(i == game.correctIndex, null, solution)
        }
    }
    fun stateOf(i: Int) = when {
        !answered -> OptionState.IDLE
        i == game.correctIndex -> OptionState.CORRECT
        i == picked -> OptionState.WRONG
        else -> OptionState.DIM
    }
    if (grid) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            game.options.indices.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        OptionButton(
                            text = game.options[i],
                            state = stateOf(i),
                            enabled = !answered,
                            onClick = { onPick(i) },
                            modifier = Modifier.weight(1f),
                            fontSize = if (game.style == ChoiceStyle.LETTER) 26.sp else 19.sp,
                            minHeight = 56.dp,
                        )
                    }
                }
            }
        }
    } else {
        Column {
            game.options.forEachIndexed { i, option ->
                // Nach der Antwort bleiben nur die richtige und die gewählte stehen — das schafft Platz für die Auflösung.
                AnimatedVisibility(
                    visible = !answered || i == game.correctIndex || i == picked,
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
                ) {
                    OptionButton(
                        text = option,
                        sub = game.optionSubs.getOrNull(i)?.takeIf { showTranslit },
                        state = stateOf(i),
                        enabled = !answered,
                        onClick = { onPick(i) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
        }
    }

    AnimatedVisibility(visible = answered, enter = fadeIn(tween(220)) + expandVertically(tween(260))) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val ru = game.revealRu
            if (ru != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (game.style == ChoiceStyle.WORD) {
                        RuLetterLens(ru)
                    } else {
                        RuInterlinear(ru, size = 19.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            game.revealDe.orEmpty(),
                            color = HikariAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (clip != null) {
                            Spacer(Modifier.width(10.dp))
                            ListenButton(clip, audio, size = 34.dp)
                        }
                    }
                }
            }
            if (game.explanation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(game.explanation, color = HikariText, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ── Neuer Buchstabe ─────────────────────────────────────────────────────────

@Composable
private fun LetterIntroContent(
    game: LetterIntroGame,
    audio: RussianAudioPlayer?,
    letterProgress: Map<String, Int>,
    finish: Finish,
) {
    val letter = game.letter
    var asking by remember(game.id) { mutableStateOf(false) }
    var picked by remember(game.id) { mutableStateOf<Int?>(null) }
    val known = RuAlphabetLogic.known(letterProgress).size

    Text(
        "Buchstabe ${game.position} von ${game.total}" + (game.group?.let { "  ·  ${it.title}" } ?: ""),
        color = HikariTextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "${letter.u} ${letter.l}",
        color = Color.White,
        fontSize = 74.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 82.sp,
    )

    AnimatedContent(
        targetState = asking,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
        label = "letter-step",
    ) { ask ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!ask) {
                Text(
                    if (letter.hasSound) "klingt wie »${letter.sound}«" else "kein eigener Laut",
                    color = HikariAmber,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (letter.hasSound) Text(letter.like, color = HikariTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(letter.tip, color = HikariText, fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    letter.words.take(3).forEach { word ->
                        val playing = audio?.playing?.collectAsState()?.value == word.clip
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (playing) HikariAmber.copy(alpha = 0.14f) else Tile)
                                .border(0.6.dp, if (playing) HikariAmber else TileBorder, RoundedCornerShape(14.dp))
                                .clickable { audio?.play(word.clip) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                buildAnnotatedString {
                                    // Der neue Buchstabe leuchtet im Wort — so sieht man ihn im Zusammenhang.
                                    word.display.forEach { c ->
                                        if (c.lowercaseChar().toString() == letter.l) {
                                            withStyle(SpanStyle(color = HikariAmber)) { append(c) }
                                        } else {
                                            append(c)
                                        }
                                    }
                                },
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(RussianPhonetics.transliterateFlat(word.ru), color = HikariTextMuted, fontSize = 11.sp, maxLines = 1)
                            Text(word.de, color = HikariText, fontSize = 11.5.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Anhören",
                                tint = if (playing) HikariAmber else HikariTextFaint,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Verstanden, frag mich ab", onClick = { asking = true })
                if (known > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Du kennst schon $known von ${game.total} Buchstaben.", color = HikariTextFaint, fontSize = 11.5.sp)
                }
            } else {
                Prompt(if (letter.hasSound) "Wie klingt dieser Buchstabe?" else "Was macht dieses Zeichen?")
                Spacer(Modifier.height(14.dp))
                val answered = picked != null
                fun stateOf(i: Int) = when {
                    !answered -> OptionState.IDLE
                    i == game.correctIndex -> OptionState.CORRECT
                    i == picked -> OptionState.WRONG
                    else -> OptionState.DIM
                }
                val onPick: (Int) -> Unit = { i ->
                    if (picked == null) {
                        picked = i
                        finish(i == game.correctIndex, null, if (i == game.correctIndex) "" else "${letter.u} ${if (letter.hasSound) "klingt wie »${letter.sound}«" else "hat keinen eigenen Laut"}.")
                    }
                }
                if (letter.hasSound) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.options.indices.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { i ->
                                    OptionButton(
                                        text = game.options[i],
                                        state = stateOf(i),
                                        enabled = !answered,
                                        onClick = { onPick(i) },
                                        modifier = Modifier.weight(1f),
                                        fontSize = 21.sp,
                                        minHeight = 56.dp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.options.forEachIndexed { i, option ->
                            OptionButton(option, stateOf(i), !answered, { onPick(i) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// ── Satzbau ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildGameContent(game: BuildGame, audio: RussianAudioPlayer?, done: Boolean, showTranslit: Boolean, finish: Finish) {
    val chosen = remember(game.id) { mutableStateListOf<Int>() }
    var checked by remember(game.id) { mutableStateOf<Boolean?>(null) }
    val clip = game.audio?.takeIf { audio?.has(it) == true }

    ContextLine(game.context)
    Prompt("Baue den Satz aus den Wörtern")
    Spacer(Modifier.height(10.dp))
    Text(game.de, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp)
    Spacer(Modifier.height(16.dp))

    val frame = when (checked) {
        true -> HikariAmber
        false -> Wrong
        null -> TileBorder
    }
    FlowRow(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(0.8.dp, frame, RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chosen.forEach { index ->
            WordTile(game.tiles[index], active = true, enabled = checked == null, translit = showTranslit) { chosen.remove(index) }
        }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        game.tiles.forEachIndexed { index, tile ->
            val used = index in chosen
            WordTile(tile, active = false, enabled = !used && checked == null, faded = used, translit = showTranslit) { chosen.add(index) }
        }
    }
    Spacer(Modifier.height(14.dp))

    if (!done) {
        PrimaryButton("Prüfen", enabled = chosen.isNotEmpty(), onClick = {
            val given = chosen.map { RuLessonBuilder.normWord(game.tiles[it]) }
            val ok = given == game.answer.map { RuLessonBuilder.normWord(it) }
            checked = ok
            if (clip != null) audio?.play(clip)
            finish(ok, null, "")
        })
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RuInterlinear(game.ru, size = 19.sp)
            if (clip != null) {
                Spacer(Modifier.height(8.dp))
                ListenButton(clip, audio, size = 34.dp)
            }
        }
    }
}

@Composable
private fun WordTile(
    text: String,
    active: Boolean,
    enabled: Boolean,
    faded: Boolean = false,
    translit: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        color = if (active) Color(0xFF3B82F6).copy(alpha = 0.20f) else Tile,
        border = BorderStroke(0.6.dp, if (active) Color(0xFF3B82F6).copy(alpha = 0.6f) else TileBorder),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text,
                color = if (faded) Color.Transparent else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (translit) {
                Text(
                    RussianPhonetics.transliterateFlat(RussianPhonetics.fromDisplay(text)),
                    color = if (faded) Color.Transparent else HikariTextMuted,
                    fontSize = 10.5.sp,
                )
            }
        }
    }
}

/** Kleine Zeile über einer Russisch-Übung: woher der Satz stammt, auf welchem Niveau geübt wird. */
@Composable
private fun ContextLine(text: String?) {
    if (text.isNullOrBlank()) return
    Text(text, color = HikariTextFaint, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
}

// ── Paare ───────────────────────────────────────────────────────────────────

@Composable
private fun PairsGameContent(game: PairsGame, audio: RussianAudioPlayer?, showTranslit: Boolean, finish: Finish) {
    val matched = remember(game.id) { mutableStateListOf<Int>() }
    var left by remember(game.id) { mutableStateOf<Int?>(null) }
    var missLeft by remember(game.id) { mutableStateOf<Int?>(null) }
    var missRight by remember(game.id) { mutableStateOf<Int?>(null) }
    var mistakes by remember(game.id) { mutableIntStateOf(0) }

    LaunchedEffect(missLeft, missRight) {
        if (missLeft != null) {
            delay(450)
            missLeft = null
            missRight = null
        }
    }

    Prompt(game.prompt)
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            game.pairs.forEachIndexed { i, pair ->
                val state = when {
                    i in matched -> OptionState.CORRECT
                    i == missLeft -> OptionState.WRONG
                    else -> OptionState.IDLE
                }
                Box {
                    OptionButton(
                        text = pair.left,
                        sub = pair.leftSub?.takeIf { showTranslit },
                        state = state,
                        enabled = i !in matched,
                        onClick = {
                            left = i
                            pair.audio?.takeIf { audio?.has(it) == true }?.let { audio?.play(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = if (game.letters) 24.sp else 15.sp,
                        minHeight = 58.dp,
                    )
                    if (left == i && i !in matched) {
                        Box(Modifier.matchParentSize().border(1.4.dp, Color.White, RoundedCornerShape(14.dp)))
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            game.rightOrder.forEach { i ->
                val state = when {
                    i in matched -> OptionState.CORRECT
                    i == missRight -> OptionState.WRONG
                    else -> OptionState.IDLE
                }
                OptionButton(
                    text = game.pairs[i].right,
                    state = state,
                    enabled = i !in matched && left != null,
                    onClick = {
                        val l = left ?: return@OptionButton
                        if (l == i) {
                            matched.add(i)
                            left = null
                            if (matched.size == game.pairs.size) {
                                finish(
                                    mistakes == 0,
                                    if (mistakes == 0) "Fehlerfrei" else "Alle Paare gefunden",
                                    if (mistakes == 0) "" else if (mistakes == 1) "Ein Fehlgriff war dabei." else "$mistakes Fehlgriffe waren dabei.",
                                )
                            }
                        } else {
                            mistakes += 1
                            missLeft = l
                            missRight = i
                            left = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = if (game.letters) 19.sp else 14.sp,
                    minHeight = 58.dp,
                )
            }
        }
    }
    if (matched.isEmpty() && left == null) {
        Spacer(Modifier.height(10.dp))
        Text("Erst links tippen, dann rechts das Gegenstück.", color = HikariTextFaint, fontSize = 11.5.sp)
    }
}

// ── Farb-Wort-Test ──────────────────────────────────────────────────────────

private val STROOP_COLORS = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFFACC15))

@Composable
private fun StroopContent(game: StroopGame, finish: Finish) {
    // -1 = Erklärung, 0..n-1 = Runde, n = fertig
    var round by remember(game.id) { mutableIntStateOf(-1) }
    var right by remember(game.id) { mutableIntStateOf(0) }
    var startedAt by remember(game.id) { mutableLongStateOf(0L) }
    val total = game.rounds.size

    if (round < 0) {
        Text("Welche Farbe hat die Schrift?", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tippe die Farbe, in der das Wort geschrieben ist. Was das Wort sagt, zählt nicht. $total Runden, so schnell du kannst.",
            color = HikariText,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text("Blau", color = STROOP_COLORS[0], fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("Hier wäre »Rot« richtig.", color = HikariTextMuted, fontSize = 12.5.sp)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Los geht's", onClick = {
            startedAt = System.currentTimeMillis()
            round = 0
        })
        return
    }

    // Nach der letzten Runde spricht die Rückmeldung darunter für sich.
    if (round >= total) return

    val shown = game.rounds[round]
    Text(
        "Runde ${round + 1} von $total",
        color = HikariTextMuted,
        fontSize = 12.5.sp,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        StroopGame.COLOR_NAMES[shown.word],
        color = STROOP_COLORS[shown.ink],
        fontSize = 52.sp,
        fontWeight = FontWeight.Black,
    )
    Spacer(Modifier.height(22.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StroopGame.COLOR_NAMES.indices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    OptionButton(
                        text = StroopGame.COLOR_NAMES[i],
                        state = OptionState.IDLE,
                        enabled = true,
                        onClick = {
                            if (i == shown.ink) right += 1
                            if (round + 1 >= total) {
                                val ms = System.currentTimeMillis() - startedAt
                                round = total
                                finish(
                                    right >= total - 1,
                                    "$right von $total richtig",
                                    "In ${seconds(ms)} Sekunden. Dein Kopf will das Wort lesen, du musst ihn bremsen. Genau dieses Bremsen wird hier geübt.",
                                )
                            } else {
                                round += 1
                            }
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        minHeight = 56.dp,
                    )
                }
            }
        }
    }
}

// ── Mustergedächtnis ────────────────────────────────────────────────────────

private enum class MemoryPhase { READY, SHOWING, INPUT, DONE }

@Composable
private fun MemoryGridContent(game: MemoryGridGame, finish: Finish) {
    var phase by remember(game.id) { mutableStateOf(MemoryPhase.READY) }
    val tapped = remember(game.id) { mutableStateListOf<Int>() }
    var wrongCell by remember(game.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(phase) {
        if (phase == MemoryPhase.SHOWING) {
            delay(900L + 260L * game.cells.size)
            phase = MemoryPhase.INPUT
        }
    }

    Text(
        when (phase) {
            MemoryPhase.READY -> "Merk dir die ${game.cells.size} leuchtenden Felder"
            MemoryPhase.SHOWING -> "Einprägen …"
            MemoryPhase.INPUT -> "Tippe die Felder an (${tapped.size} von ${game.cells.size})"
            MemoryPhase.DONE -> if (wrongCell == null) "Alle ${game.cells.size} gemerkt" else "So sah das Muster aus"
        },
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(game.size) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(game.size) { c ->
                    val cell = r * game.size + c
                    val lit = when (phase) {
                        MemoryPhase.SHOWING -> cell in game.cells
                        MemoryPhase.INPUT -> cell in tapped
                        MemoryPhase.DONE -> cell in game.cells
                        MemoryPhase.READY -> false
                    }
                    val color by animateColorAsState(
                        when {
                            cell == wrongCell -> Wrong.copy(alpha = 0.75f)
                            lit && phase == MemoryPhase.DONE && cell !in tapped -> HikariAmber.copy(alpha = 0.35f)
                            lit -> HikariAmber
                            else -> Tile
                        },
                        animationSpec = tween(180),
                        label = "memory-cell",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .border(0.6.dp, TileBorder, RoundedCornerShape(12.dp))
                            .clickable(enabled = phase == MemoryPhase.INPUT && cell !in tapped) {
                                if (cell in game.cells) {
                                    tapped.add(cell)
                                    if (tapped.size == game.cells.size) {
                                        phase = MemoryPhase.DONE
                                        finish(true, "Muster gemerkt", "")
                                    }
                                } else {
                                    wrongCell = cell
                                    phase = MemoryPhase.DONE
                                    finish(false, "Daneben", "${tapped.size} von ${game.cells.size} Feldern hattest du.")
                                }
                            },
                    )
                }
            }
        }
    }
    if (phase == MemoryPhase.READY) {
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Muster zeigen", onClick = { phase = MemoryPhase.SHOWING })
    }
}

// ── Kettenrechnen ───────────────────────────────────────────────────────────

@Composable
private fun ChainMathContent(game: ChainMathGame, finish: Finish) {
    // -2 = Erklärung, -1 = Startzahl, 0..n-1 = Schritt, n = Frage
    var step by remember(game.id) { mutableIntStateOf(-2) }
    var picked by remember(game.id) { mutableStateOf<Int?>(null) }
    val total = game.steps.size

    LaunchedEffect(step) {
        if (step in -1 until total) {
            delay(if (step == -1) 1500 else 1900)
            step += 1
        }
    }

    if (step == -2) {
        Text("Rechne im Kopf mit", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Du siehst eine Startzahl und danach $total Rechenschritte, einen nach dem anderen. Jeder verschwindet wieder. Am Ende zählt nur dein Ergebnis.",
            color = HikariText,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Start", onClick = { step = -1 })
        return
    }

    if (step < total) {
        Text(if (step == -1) "Startzahl" else "Schritt ${step + 1} von $total", color = HikariTextMuted, fontSize = 12.5.sp)
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
            label = "chain-step",
        ) { s ->
            Text(
                if (s == -1) game.start.toString() else game.steps.getOrElse(s) { "" },
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    val answered = picked != null
    Prompt("Was kommt heraus?")
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        game.options.indices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    OptionButton(
                        text = game.options[i].toString(),
                        state = when {
                            !answered -> OptionState.IDLE
                            i == game.correctIndex -> OptionState.CORRECT
                            i == picked -> OptionState.WRONG
                            else -> OptionState.DIM
                        },
                        enabled = !answered,
                        onClick = {
                            picked = i
                            val path = buildString {
                                append(game.start)
                                game.steps.forEachIndexed { n, s -> append("  $s = ${game.trail[n]}") }
                            }
                            finish(i == game.correctIndex, null, "Der Weg: $path.")
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = 22.sp,
                        minHeight = 58.dp,
                    )
                }
            }
        }
    }
}

// ── Adlerauge ───────────────────────────────────────────────────────────────

@Composable
private fun OddOneOutContent(game: OddOneOutGame, active: Boolean, done: Boolean, finish: Finish) {
    var startedAt by remember(game.id) { mutableLongStateOf(0L) }
    var misses by remember(game.id) { mutableIntStateOf(0) }
    var missCell by remember(game.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(active) {
        if (active && startedAt == 0L) startedAt = System.currentTimeMillis()
    }
    LaunchedEffect(missCell) {
        if (missCell != null) {
            delay(350)
            missCell = null
        }
    }

    Prompt("Ein Zeichen ist anders. Finde es.")
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(game.rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(game.columns) { c ->
                    val cell = r * game.columns + c
                    val isOdd = cell == game.oddIndex
                    val bg by animateColorAsState(
                        when {
                            done && isOdd -> HikariAmber.copy(alpha = 0.30f)
                            cell == missCell -> Wrong.copy(alpha = 0.35f)
                            else -> Color.Transparent
                        },
                        label = "odd-cell",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(bg)
                            .clickable(enabled = !done) {
                                if (isOdd) {
                                    val ms = System.currentTimeMillis() - (startedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
                                    finish(
                                        misses == 0,
                                        "Gefunden in ${seconds(ms)} Sekunden",
                                        (if (misses > 0) "Mit $misses Fehlgriff${if (misses == 1) "" else "en"}. " else "") + game.note,
                                    )
                                } else {
                                    misses += 1
                                    missCell = cell
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isOdd) game.odd else game.common,
                            color = if (done && isOdd) HikariAmber else Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
