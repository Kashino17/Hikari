package com.hikari.app.ui.russian

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.russian.*

@Composable internal fun JourneyFreeSetup(vm: RussianConversationViewModel, p: RuChatProgress, onResume: () -> Unit) {
    val s = vm.settings
    var options by rememberSaveable { mutableStateOf(false) }
    JourneyLabel("DEINE WÖRTER. DEIN GESPRÄCH.")
    JourneyTitle("Was bewegt\ndich heute?")
    JourneyBody("Anja knüpft an das an, was du schon gelernt hast. Neue Wörter schauen wir uns vor deiner Antwort gemeinsam an.", muted = true)
    JourneyArtwork("hello", Modifier.fillMaxWidth().height(155.dp))
    if (vm.session?.finished == false) JourneyCard(color = JourneySage) {
        JourneyBody("Euer letztes Gespräch wartet noch auf dich.")
        JourneyButton("Gespräch fortsetzen", onClick = onResume)
    }
    ConversationChoices("Wo möchtest du Anja treffen?", RuChatCurriculum.scenarios.take(4).map { it.id to it.title }, s.scenario) { vm.configure(s.copy(scenario = it)) }
    OutlinedTextField(s.interest, { vm.configure(s.copy(interest = it.take(300))) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        label = { Text("Dein Thema, wenn du magst") }, placeholder = { Text("Meine Katze, eine Reise, mein Lieblingsfilm …") })
    JourneyButton("Ein neues Gespräch beginnen", Icons.Rounded.ChatBubbleOutline, enabled = !vm.busy && RuJourney.freeReady(p.foundation)) { vm.start() }
    if (vm.available == false) JourneyCard(color = JourneyPeach) {
        JourneyBody("Anja ist gerade nicht verbunden.")
        JourneySmall("Für das freie Gespräch muss dein KI-Server erreichbar sein. Deine Mini-Gespräche und dein Wortschatz funktionieren weiterhin.")
        TextButton(onClick = vm::refreshStatus) { Text("Verbindung erneut prüfen") }
    }
    TextButton(onClick = { options = !options }) { Text(if (options) "Weniger anzeigen" else "Gespräch nach meinem Geschmack") }
    if (options) {
        ConversationChoices("Weitere Orte", RuChatCurriculum.scenarios.map { it.id to it.title }, s.scenario) { vm.configure(s.copy(scenario = it)) }
        ConversationChoices("Was möchtest du üben?", listOf("everyday" to "Alltag", "stories" to "Geschichten", "media" to "Film & Musik"), s.goal) { vm.configure(s.copy(goal = it)) }
        ConversationChoices("Deine Stimmung", listOf("calm" to "Entspannt", "playful" to "Verspielt", "curious" to "Neugierig"), s.mood) { vm.configure(s.copy(mood = it)) }
        ConversationChoices("Zeit für …", listOf("6" to "6 Antworten", "12" to "12 Antworten", "24" to "24 Antworten"), s.turns.toString()) { vm.configure(s.copy(turns = it.toInt())) }
    }
    JourneySmall("Du kannst eigene Antworten geben oder etwas erfinden. Du entscheidest, wann das Mikrofon startet und welcher erkannte Text gesendet wird.")
}

@Composable internal fun JourneyFreeChat(
    vm: RussianConversationViewModel, p: RuChatProgress, listening: Boolean, partial: String,
    onListen: () -> Unit, onStop: () -> Unit, onPlay: (String) -> Unit, onSend: (String) -> Unit,
    onFinish: () -> Unit, onHome: () -> Unit, onNew: () -> Unit,
) {
    val s = vm.session ?: return
    val turn = s.turns.lastOrNull()
    var prepared by rememberSaveable(turn?.id) { mutableStateOf(false) }
    var typing by rememberSaveable(turn?.id) { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var ideas by rememberSaveable { mutableStateOf(false) }
    var correction by rememberSaveable(turn?.id) { mutableStateOf(false) }
    if (s.finished) {
        JourneyLabel("DEIN GESPRÄCHSMOMENT")
        JourneyTitle("Du hast etwas\nzu sagen.")
        JourneyArtwork("heart", Modifier.fillMaxWidth().height(150.dp))
        JourneyBody("${s.answers.size} eigene Antworten. ${s.answers.count { it.spoken }} davon gesprochen. ${s.answers.count { !it.helped }} ohne aufgedeckte Hilfe.")
        JourneySmall("Dein Fortschritt wächst über mehrere Tage. Eine einzelne gute Runde ist ein Anfang, noch kein Beleg für sicheres Beherrschen.")
        ConversationChoices("Wie fühlst du dich jetzt?", listOf("1" to "Unsicher", "2" to "Okay", "3" to "Mutig"), s.confidenceAfter?.toString().orEmpty()) { vm.confidence(it.toInt()) }
        s.turns.map { it.reply.chunk }.distinctBy { it.ru }.takeLast(3).forEach { phrase ->
            JourneyCard {
                JourneyBody(phrase.ru); JourneySmall(phrase.de)
                TextButton(onClick = { vm.savePhrase(phrase) }, enabled = p.cards.none { it.phrase.ru == phrase.ru }) {
                    Text(if (p.cards.any { it.phrase.ru == phrase.ru }) "Für später gemerkt" else "In meinen Wortschatz")
                }
            }
        }
        JourneyButton("Zurück zu meiner Reise", onClick = onHome)
        TextButton(onClick = onNew) { Text("Ein anderes Gespräch") }
        return
    }
    JourneyLabel("${RuChatCurriculum.scenario(s.settings.scenario).title} · ${s.answers.size} EIGENE ANTWORTEN")
    if (turn == null) {
        JourneyTitle("Anja ist gleich da.", small = true)
        JourneyArtwork("hello", Modifier.fillMaxWidth().height(170.dp))
        JourneyBody("Du startest mit einer kurzen Frage. Erst anschauen, dann antworten.", muted = true)
        if (!vm.busy) JourneyButton("Noch einmal verbinden") { onSend("start") }
    } else {
        val r = turn.reply
        if (turn.answer.isNotBlank()) JourneyCard(color = JourneySage) {
            JourneyLabel("DU")
            JourneyBody(turn.answer)
            r.feedback?.let { f ->
                JourneySmall(f.praise)
                if (f.focus.isNotBlank()) {
                    JourneyBody(f.focus)
                    JourneySmall(f.repairCue)
                    if (!vm.repair) TextButton(onClick = vm::retryAnswer, enabled = !vm.busy) { Text("Selbst verbessern") }
                    TextButton(onClick = { correction = !correction; vm.assistance() }) { Text("Ein Beispiel hilft mir") }
                    if (correction) { JourneyBody(f.correctedRu); JourneySmall(f.explanationDe); JourneyAudio { onPlay(f.correctedRu) } }
                }
            }
        }
        JourneyPartnerBubble(r.partnerRu, r.partnerDe) { onPlay(r.partnerRu) }
        if (!prepared && !vm.busy && !vm.repair) {
            JourneyCard {
                JourneyLabel("ERST IN RUHE ANSCHAUEN")
                JourneyBody(r.cue)
                r.words.forEach { word ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(word.ru, fontSize = 21.sp, color = JourneyInk, fontWeight = FontWeight.Medium)
                            JourneySmall(word.de)
                        }
                        IconButton(onClick = { onPlay(word.ru) }) { Icon(Icons.Rounded.VolumeUp, "${word.de} anhören", tint = JourneyGreen) }
                    }
                }
                JourneySmall("Diese Wörter können dir helfen. Die Antwort darf ganz deine eigene sein.")
                JourneyButton("Bereit für meine Antwort") { vm.learnTurnWords(); prepared = true }
            }
        } else if (!vm.busy) {
            if (vm.repair) {
                JourneyBody("Dein neuer Versuch — ändere nur die eine Stelle.")
                TextButton(onClick = vm::cancelRepair) { Text("Lieber weiterreden") }
            }
            if (s.hint > 0) JourneyCard(color = JourneySage) {
                JourneyLabel("EIN BISSCHEN RÜCKENWIND")
                JourneyBody(r.cue)
                if (s.hint >= 2) r.words.forEach { JourneyBody("${it.ru} — ${it.de}") }
                if (s.hint >= 3) { JourneyBody(r.starter); TextButton(onClick = { vm.draft(r.starter); vm.assistance() }) { Text("Diesen Anfang benutzen") } }
                if (s.hint >= 4) r.examples.forEach { example ->
                    JourneyBody(example.ru); JourneySmall(example.de)
                    Row {
                        TextButton(onClick = { onPlay(example.ru) }) { Text("Anhören") }
                        TextButton(onClick = { vm.draft(example.ru); vm.assistance() }) { Text("Damit anfangen") }
                    }
                }
            }
            if (listening) JourneyCard(color = JourneySage) { JourneyBody("Ich höre zu …"); if (partial.isNotBlank()) JourneySmall(partial) }
            if (typing || s.draft.isNotBlank()) OutlinedTextField(s.draft, { vm.draft(it) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), minLines = 2, maxLines = 5,
                label = { Text(if (s.draftSpoken) "So wurde es verstanden" else "Deine russische Antwort") })
            JourneyButton(if (listening) "Aufnahme beenden" else if (s.draft.isBlank()) "Ich antworte" else "Neu aufnehmen", if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                onClick = if (listening) onStop else onListen)
            if (s.draft.isNotBlank()) JourneyButton("Meine Antwort senden", Icons.Rounded.Send, enabled = !listening) { onSend(if (vm.repair) "repair" else "reply") }
            if (!typing && s.draft.isBlank()) TextButton(onClick = { typing = true }) { Text("Heute lieber tippen") }
            TextButton(onClick = vm::hint, enabled = s.hint < 4) { Icon(Icons.Rounded.Lightbulb, null); Spacer(Modifier.width(8.dp)); Text(if (s.hint == 0) "Ein kleiner Tipp" else "Mehr Starthilfe") }
            JourneySmall("KI-Rückmeldung zu deinem bestätigten Text. Kein Aussprachetest. Andere Formulierungen sind willkommen.")
        }
        if (!vm.busy) {
            TextButton(onClick = { vm.savePhrase(r.chunk) }, enabled = p.cards.none { it.phrase.ru == r.chunk.ru }) { Text(if (p.cards.any { it.phrase.ru == r.chunk.ru }) "Ausdruck gemerkt" else "Diesen Ausdruck für später merken") }
            if (s.answers.size >= s.settings.turns) JourneyCard(color = JourneyPeach) { JourneyBody("Dein Gesprächsziel für heute ist erreicht. Ein guter Moment für eine Pause."); JourneyButton("Mein Rückblick", onClick = onFinish) }
            if (s.activeSeconds >= s.settings.blockMinutes * 60) JourneySmall("Dein geplanter Übungsblock ist vorbei. Du kannst jederzeit Pause machen; dein Gespräch bleibt gespeichert.")
            TextButton(onClick = { ideas = !ideas }) { Text("Das Gespräch gestalten") }
            if (ideas) listOf("simplify" to "Einfacher fragen", "deeper" to "Tiefer nachfragen", "twist" to "Eine kleine Überraschung", "roles" to "Jetzt frage ich", "retell" to "Aus dem Kopf erzählen", "listen" to "Eine Szene verstehen").forEach { (action, label) ->
                OutlinedButton(onClick = { onSend(action); ideas = false }, enabled = !listening && s.draft.isBlank(), modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        if (s.turns.size > 1) {
            TextButton(onClick = { history = !history }) { Text(if (history) "Verlauf schließen" else "Unser bisheriges Gespräch") }
            if (history) s.turns.dropLast(1).forEach { old -> JourneyCard { if (old.answer.isNotBlank()) JourneySmall("Du: ${old.answer}"); JourneyBody(old.reply.partnerRu); JourneySmall(old.reply.partnerDe) } }
        }
    }
    if (vm.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); JourneySmall("Anja denkt über ihre Antwort nach …") }
    TextButton(onClick = onFinish) { Text("Für heute beenden") }
}

@Composable internal fun JourneySettings(vm: RussianConversationViewModel) {
    JourneyTitle("So, wie es\nfür dich passt.")
    ConversationChoices("Sprechtempo", listOf("0.7" to "Langsam", "0.85" to "Ruhig", "1.0" to "Normal"), vm.settings.speed.toString()) { vm.configure(vm.settings.copy(speed = it.toFloat())) }
    JourneyCard {
        JourneyBody("Du bestimmst, wann du hörst.")
        JourneySmall("Wörter und Fragen werden erst vorgelesen, wenn du auf Anhören tippst. Beim Verlassen der Seite wird der Ton gestoppt.")
    }
    JourneyCard {
        JourneyBody("Sprechen oder tippen — beides geht.")
        JourneySmall("Dein Android-Sprachdienst kann Audio online verarbeiten. Hikari schickt nur den von dir bestätigten Text an die Gesprächs-KI. Die ersten Mini-Gespräche benötigen keine KI-Verbindung.")
    }
}
