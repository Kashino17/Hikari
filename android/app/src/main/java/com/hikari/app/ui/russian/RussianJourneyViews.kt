package com.hikari.app.ui.russian

import com.hikari.app.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.russian.*

@Composable internal fun JourneyScaffold(
    title: String, subtitle: String, onBack: () -> Unit, onMore: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    contentKey: Any? = title,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalConfiguration.current.let { it.screenWidthDp < 360 || it.fontScale > 1.15f }
    Column(Modifier.fillMaxSize().background(HikariBg).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Zurück", tint = HikariText) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(title, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = HikariTextMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreHoriz, "Weitere Möglichkeiten", tint = HikariText) }
        }
        key(contentKey) {
            Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp), content = content)
        }
        if (footer != null) Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().background(HikariBg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, content = footer)
    }
}

@Composable internal fun JourneyHome(
    progress: RuChatProgress, today: Long, onStart: (String) -> Unit, onResume: () -> Unit,
    onReview: () -> Unit, onPlan: () -> Unit, onFree: () -> Unit,
) {
    val f = progress.foundation
    val resumable = f.run?.phase in listOf("learn", "guided", "recall")
    val next = if (resumable) RuJourney.lesson(f.run!!.lessonId) else RuJourney.next(f)
    val allDone = f.completed.size == RuJourney.lessons.size
    JourneyLabel(if (f.completed.isEmpty()) "DEIN ERSTER RUSSISCH-MOMENT" else "SCHÖN, DASS DU WIEDER DA BIST")
    JourneyTitle(if (f.completed.isEmpty()) "Dein erstes Gespräch" else "Deine Gesprächsreise")
    JourneyBody(if (f.completed.isEmpty()) "Du musst noch nichts können. Wir lernen die Wörter zuerst — und benutzen sie dann zusammen." else "Deine Wörter reisen mit. Heute kommt ein kleiner neuer Gesprächsmoment dazu.", muted = true)
    JourneyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(HikariSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LocalCafe, null, Modifier.size(20.dp), tint = HikariAmber)
            }
            Column(Modifier.weight(1f)) {
                Text(if (allDone && !resumable) "Bereit für deine Geschichten" else next.title, color = HikariText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(if (allDone && !resumable) "Mit deinen Wörtern frei sprechen" else "${next.words.size} Wörter · ein kleines Gespräch", color = HikariTextMuted, fontSize = 13.sp)
            }
        }
        JourneyButton(if (resumable) "Hier weitermachen" else if (allDone) "Mein eigenes Gespräch" else if (f.completed.isEmpty()) "Meine ersten 4 Wörter" else "Nächsten Moment entdecken") {
            if (resumable) onResume() else if (allDone) onFree() else onStart(next.id)
        }
        Text("Du startest den Ton selbst. Alles wird auf Deutsch erklärt.", color = HikariTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
    val due = RuChatLearning.due(progress, today).size
    if (due > 0) Surface(color = HikariSurfaceHigh.copy(alpha = .6f), shape = RoundedCornerShape(18.dp), onClick = onReview) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Replay, null, tint = HikariAmber); Spacer(Modifier.width(12.dp))
            Text("$due Wörter warten auf ein Wiedersehen", fontSize = 14.sp, color = HikariText, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = HikariAmber)
        }
    }
    JourneyLabel("DEINE KLEINE REISE")
    Column {
        RuJourney.lessons.forEachIndexed { index, lesson ->
            val done = lesson.id in f.completed
            val available = RuJourney.available(f, lesson.id)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = available) { onStart(lesson.id) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(if (done) HikariAmber else if (available) HikariSurfaceHigh else HikariBorderStrong, CircleShape), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Rounded.Check, "Abgeschlossen", tint = Color.Black, modifier = Modifier.size(19.dp))
                    else if (!available) Icon(Icons.Rounded.Lock, "Später", tint = HikariTextMuted, modifier = Modifier.size(16.dp))
                    else Text("${index + 1}", color = HikariAmber, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(lesson.title, color = if (available) HikariText else HikariTextMuted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(if (done) "Noch einmal erleben" else if (available) "Bereit, wenn du es bist" else "Nach dem vorherigen Gespräch", color = HikariTextMuted, fontSize = 12.sp)
                }
                if (available) Icon(Icons.Rounded.ChevronRight, null, tint = HikariTextMuted)
            }
        }
    }
    if (RuJourney.freeReady(f)) JourneyCard(color = HikariSurfaceHigh) {
        JourneyLabel("DEINE WÖRTER. DEINE GESCHICHTEN.")
        JourneyTitle("Worüber reden wir?", small = true)
        JourneyBody("Mit Anja frei sprechen. Neue Formulierungen schaust du dir vorher in Ruhe an.")
        JourneyButton("Mein eigenes Gespräch", Icons.Rounded.ChatBubbleOutline, onClick = onFree)
    } else JourneySmall("Freie Gespräche kommen nach deinen ersten drei Mini-Gesprächen. Bis dahin üben wir alles gemeinsam.")
    HorizontalDivider(color = HikariBorderStrong)
    JourneyLink("Mein Wortschatz · ${RuJourney.known(f).size} kennengelernt", Icons.Rounded.AutoStories, onReview)
    JourneyLink("Mein Lernplan", Icons.Rounded.CalendarToday, onPlan)
}

@Composable internal fun JourneyPhase(run: RuFoundationRun) {
    val active = when (run.phase) { "learn" -> 0; "guided" -> 1; else -> 2 }
    val compact = LocalConfiguration.current.let { it.screenWidthDp < 360 || it.fontScale > 1.15f }
    val labels = listOf("Kennenlernen", "Gemeinsam", "Du selbst")
    if (compact) JourneySmall("${active + 1} von 3 · ${labels[active]}")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(if (index <= active) HikariAmber else HikariBorderStrong))
                if (!compact) Text(label, color = if (index == active) HikariText else HikariTextMuted, fontSize = 11.sp,
                    fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable internal fun JourneyWordView(run: RuFoundationRun, onPlay: (String) -> Unit) {
    val lesson = RuJourney.lesson(run.lessonId)
    val word = lesson.words[run.index]
    val compact = LocalConfiguration.current.let { it.screenWidthDp < 360 || it.fontScale > 1.15f }
    JourneyPhase(run)
    JourneyLabel("WORT ${run.index + 1} VON ${lesson.words.size}")
    if (!compact) JourneyTitle(if (run.index == 0) "Lernen wir uns\nkennen." else "Noch ein kleines\nStück Russisch.", small = true)
    JourneyCard {
        JourneyArtwork(word.art, Modifier.fillMaxWidth().height(if (compact) 64.dp else 88.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word.display, color = HikariText, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
            Text(word.de, color = HikariTextMuted, fontSize = 20.sp, lineHeight = 26.sp)
            Surface(color = HikariBg, shape = RoundedCornerShape(50)) {
                Text("${word.reading} · Lesehilfe", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = HikariAmber, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            JourneyAudio("So klingt das Wort") { onPlay(word.plain) }
        }
    }
    JourneyBody(word.note)
    JourneySmall("Erst anschauen und anhören, wenn du möchtest. Antworten kommt danach. Die Lesehilfe ist eine Annäherung an den Klang.")
}

@Composable internal fun JourneyPartnerBubble(ru: String, de: String, onPlay: () -> Unit) {
    val compact = LocalConfiguration.current.let { it.screenWidthDp < 360 || it.fontScale > 1.15f }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(HikariSurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(22.dp), tint = HikariAmber)
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).background(HikariCardBg).padding(if (compact) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                JourneyLabel("ANJA")
                IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.VolumeUp, "Anja anhören", tint = HikariAmber) }
            } else JourneyLabel("ANJA")
            Text(ru, color = HikariText, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
            JourneyBody(de, muted = true)
            if (!compact) JourneyAudio(onClick = onPlay)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun JourneyPracticeView(
    run: RuFoundationRun, listening: Boolean, partial: String, typing: Boolean,
    onText: (String) -> Unit, onPlay: (String) -> Unit, onHelp: () -> Unit, onType: () -> Unit,
) {
    val lesson = RuJourney.lesson(run.lessonId)
    val line = RuJourney.line(run)
    val guided = run.phase == "guided"
    val compact = LocalConfiguration.current.let { it.screenWidthDp < 360 || it.fontScale > 1.15f }
    val answerPosition = remember { BringIntoViewRequester() }
    LaunchedEffect(run.draft.isNotBlank(), typing, run.result) {
        if (run.draft.isNotBlank() || typing) { withFrameNanos { }; answerPosition.bringIntoView() }
    }
    JourneyPhase(run)
    JourneyLabel("${if (guided) "ZUSAMMEN" else "DEIN VERSUCH"} · ${run.index + 1} VON ${lesson.lines.size}")
    if (!compact) JourneyBody(line.situation, muted = true)
    JourneyPartnerBubble(line.ru, line.de) { onPlay(line.ru) }
    if (run.result == "correct") {
        JourneyCard(modifier = Modifier.bringIntoViewRequester(answerPosition), color = HikariSurfaceHigh) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = HikariAmber)
                Text(line.success, color = HikariAmber, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            JourneyBody("Deine Antwort: ${run.draft}")
            JourneySmall(if (guided || run.helped) "Mit Unterstützung geübt. Genau dafür ist sie da." else "Aus dem Kopf — diesen Versuch merken wir uns für deinen Fortschritt.")
        }
    } else {
        JourneyBody(line.instruction)
        if (listening) JourneyCard(color = HikariSurfaceHigh) {
            Text("Ich höre zu …", color = HikariAmber, fontSize = 19.sp)
            if (partial.isNotBlank()) JourneyBody(partial) else JourneySmall("Ein Wort reicht. Du kannst die Aufnahme jederzeit beenden.")
        }
        if (typing || run.draft.isNotBlank()) {
            OutlinedTextField(run.draft, onText, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(answerPosition), minLines = 1, maxLines = 3,
                shape = RoundedCornerShape(18.dp), label = { Text(if (run.spoken) "So wurde es verstanden" else "Deine russische Antwort") })
            JourneySmall("Prüfe den Text vor dem Weitergehen. Hier wird der Inhalt erkannt, nicht dein Akzent benotet.")
        }
        if (run.result == "retry") JourneyCard(color = HikariSurfaceHigh.copy(alpha = .65f)) {
            JourneyBody("Das passt noch nicht ganz zur Aufgabe.")
            JourneySmall("Vielleicht hat die Spracherkennung etwas anderes verstanden. Du kannst den Text ändern, noch einmal sprechen oder die Starthilfe öffnen.")
        }
        if (guided || run.helped) JourneyCard(color = HikariSurfaceHigh) {
            if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (guided) "DEINE ANTWORT" else "DEINE STARTHILFE", modifier = Modifier.weight(1f),
                    color = HikariTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onPlay(line.answers.first()) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.VolumeUp, "Antwort anhören", tint = HikariAmber)
                }
            } else JourneyLabel(if (guided) "DEINE ANTWORT" else "DEINE STARTHILFE")
            Text(line.answers.first(), color = HikariText, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            JourneyBody(line.answerDe, muted = true)
            if (!compact) JourneyAudio("Antwort anhören") { onPlay(line.answers.first()) }
            if (run.helped || typing) TextButton(onClick = { onHelp(); onText(line.answers.first()) }) { Text("Als Starthilfe einsetzen") }
        } else TextButton(onClick = onHelp) {
            Icon(Icons.Rounded.Lightbulb, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Ein kleiner Tipp")
        }
        if (!typing && run.draft.isBlank()) TextButton(onClick = onType) {
            Icon(Icons.Rounded.Keyboard, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Heute lieber tippen")
        }
    }
}

@Composable internal fun JourneyCelebration(run: RuFoundationRun, onPlay: (String) -> Unit) {
    val lesson = RuJourney.lesson(run.lessonId)
    JourneyLabel("EIN KLEINER MOMENT. EIN ECHTER ANFANG.")
    JourneyTitle(if (lesson.id == "hello") "Dein erstes\nGespräch." else "Das hast du\nselbst gesagt.")
    JourneyArtwork("cafe", Modifier.fillMaxWidth().height(72.dp))
    JourneyBody(when (lesson.id) {
        "hello" -> "Du hast Anja begrüßt, einen Tee angenommen und Danke gesagt. Mit vier Wörtern."
        "choice" -> "Du hast abgelehnt, selbst gewählt und bestellt. Deine Worte machen einen Unterschied."
        "likes" -> "Du kannst eine Vorliebe ausdrücken. Aus einzelnen Wörtern werden deine Sätze."
        else -> "Du hast von dir erzählt und zurückgefragt. So entsteht ein Gespräch."
    })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(HikariSurfaceHigh).padding(18.dp)) {
            Text("${lesson.words.size}", color = HikariAmber, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            JourneySmall("Wörter kennengelernt")
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(HikariSurfaceHigh.copy(alpha = .65f)).padding(18.dp)) {
            Text("${run.recalled}", color = HikariAmber, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            JourneySmall("Antworten ohne Hilfe")
        }
    }
    JourneyCard {
        JourneyLabel("DAS NIMMST DU MIT")
        lesson.words.forEach { word ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(word.plain) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(word.display, color = HikariText, fontSize = 19.sp)
                    JourneySmall(word.de)
                }
                Icon(Icons.Rounded.VolumeUp, "${word.de} anhören", tint = HikariAmber)
            }
        }
        JourneySmall("Automatisch in deinem Wortschatz. Morgen begegnen dir die Wörter wieder — dann zeigt sich, was geblieben ist.")
    }
}

@Composable internal fun JourneyLessonFooter(
    run: RuFoundationRun, foundation: RuFoundationProgress, listening: Boolean,
    onNext: () -> Unit, onHome: () -> Unit, onNextLesson: () -> Unit,
    onListen: () -> Unit, onStop: () -> Unit, onCheck: () -> Unit,
) {
    val lesson = RuJourney.lesson(run.lessonId)
    when (run.phase) {
        "learn" -> JourneyButton(if (run.index + 1 == lesson.words.size) "Jetzt gemeinsam benutzen" else "Nächstes Wort", onClick = onNext)
        "complete" -> {
            val allDone = foundation.completed.size == RuJourney.lessons.size
            JourneyButton(if (allDone) "Zurück zu meiner Reise" else "Nächsten Moment entdecken", onClick = if (allDone) onHome else onNextLesson)
            TextButton(onClick = onHome) { Text("Für heute ein guter Anfang") }
        }
        else -> when {
            run.result == "correct" -> JourneyButton(if (run.index + 1 == lesson.lines.size && run.phase == "guided") "Jetzt versuche ich es selbst" else "Weiter im Gespräch", onClick = onNext)
            listening -> JourneyButton("Aufnahme beenden", Icons.Rounded.Stop, onClick = onStop)
            run.draft.isNotBlank() -> {
                JourneyButton("Antwort prüfen", Icons.Rounded.Check, onClick = onCheck)
                TextButton(onClick = onListen) { Text("Noch einmal aufnehmen") }
            }
            else -> JourneyButton(if (run.phase == "guided") "Ich spreche es nach" else "Ich antworte", Icons.Rounded.Mic, onClick = onListen)
        }
    }
}
