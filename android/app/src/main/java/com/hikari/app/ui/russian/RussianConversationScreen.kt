package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.hikari.app.domain.russian.*
import com.hikari.app.ui.theme.*
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun RussianConversationScreen(onClose: () -> Unit, vm: RussianConversationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val voice = remember { RussianConversationVoice(context) }
    val mic = remember { RussianSpeechRecognizer(context) }
    val speech by mic.state.collectAsState()
    val progress by vm.progress.collectAsState()
    var tab by rememberSaveable { mutableStateOf("chat") }
    var micError by remember { mutableStateOf<String?>(null) }
    var reviewHeard by rememberSaveable { mutableStateOf("") }
    var reviewSpoken by remember { mutableStateOf(false) }
    var recordingBase by remember { mutableStateOf("") }
    var recordingBaseSpoken by remember { mutableStateOf(true) }
    val session = vm.session
    val listening = speech is RuListenState.Listening || speech is RuListenState.Partial

    fun beginListening() {
        voice.stop(); micError = null
        recordingBase = if (tab == "chat") vm.session?.draft.orEmpty() else ""
        recordingBaseSpoken = recordingBase.isBlank() || vm.session?.draftSpoken == true
        vm.touch(); mic.reset(); mic.start()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) beginListening() else micError = "Mikrofon nicht freigegeben. Du kannst die Antwort auch tippen."
    }
    fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginListening()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun stopMedia() { voice.stop(); mic.destroy(); mic.reset() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE) { foreground = false; stopMedia(); vm.flushTime() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); voice.close(); mic.destroy(); vm.flushTime() }
    }
    LaunchedEffect(lifecycle, tab) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(1000); if (tab == "chat") vm.tick() }
        }
    }
    LaunchedEffect(speech) {
        when (val result = speech) {
            is RuListenState.Done -> {
                val heard = result.hypotheses.firstOrNull().orEmpty().trim()
                if (heard.isBlank()) micError = "Nichts sicher erkannt. Versuch es noch einmal oder tippe deine Antwort."
                else if (tab == "review") { reviewHeard = heard; reviewSpoken = true }
                else vm.draft(listOf(recordingBase, heard).filter { it.isNotBlank() }.joinToString(" "), spoken = recordingBaseSpoken)
                mic.reset()
            }
            is RuListenState.Failed -> micError = result.message
            else -> Unit
        }
    }
    val latest = session?.turns?.lastOrNull()
    LaunchedEffect(latest?.id, voice.ready, tab, foreground) {
        if (foreground && tab == "chat" && session?.finished == false && session.settings.autoPlay && voice.ready && latest != null && !listening) {
            voice.speak(latest.reply.partnerRu, session.settings.speed)
        }
    }
    Column(Modifier.fillMaxSize().background(HikariBg).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { stopMedia(); onClose() }) { Text("‹ Zurück", color = HikariTextMuted) }
            Text("Gesprächsreise", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { stopMedia(); vm.finish() }, enabled = session != null && !session.finished) { Text("Fertig", color = HikariAmber) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("chat" to "Gespräch", "review" to "Merken", "plan" to "Mein Plan", "evidence" to "Warum so?").forEach { (id, title) ->
                FilterChip(selected = tab == id, onClick = { stopMedia(); tab = id }, label = { Text(title) })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (tab) {
                "plan" -> ConversationPlan(vm, progress)
                "evidence" -> ConversationEvidence()
                "review" -> ConversationReview(vm, progress, reviewHeard, reviewSpoken,
                    onText = { reviewHeard = it; reviewSpoken = false }, onListen = { listen() },
                    onStop = { mic.stopListening() }, listening = listening,
                    onPlay = { voice.speak(it, vm.settings.speed) },
                    onClear = { reviewHeard = ""; reviewSpoken = false; stopMedia() })
                else -> when {
                    session == null -> ConversationSetup(vm, progress)
                    session.finished -> ConversationSummary(vm, session, progress, onReview = { tab = "review" })
                    else -> {
                        val scene = RuChatCurriculum.scenario(session.settings.scenario)
                        ConversationPanel {
                            Text("${scene.emoji}  ${scene.title}", color = HikariAmber, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            ChatBody(scene.scene)
                            ChatSmall("${RuChatCurriculum.stages[session.stage]} · ${session.answers.size}/${session.settings.turns} Antworten")
                            LinearProgressIndicator(progress = { (session.answers.size.toFloat() / session.settings.turns).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth(), color = HikariAmber)
                            ChatSmall(latest?.reply?.mission ?: scene.goal)
                        }
                        if (session.turns.isEmpty() && !vm.busy) {
                            ChatBody("Dein Gespräch ist vorbereitet. Ein Wort reicht am Anfang.")
                            RuPrimaryButton("Gespräch starten") { vm.send("start") }
                        }
                        session.turns.forEachIndexed { index, turn ->
                            val current = index == session.turns.lastIndex
                            if (turn.answer.isNotBlank()) ConversationPanel(learner = true) {
                                ChatSmall(if (turn.spoken) "DU · gesprochen${if (turn.helped) " · mit Hilfe" else ""}" else "DU · getippt")
                                Text(turn.answer, color = HikariText, fontSize = 19.sp)
                            }
                            ConversationPartner(turn, session.settings, current, voice.ready, vm,
                                onRetry = { stopMedia(); vm.retryAnswer() },
                                onPlay = { text, speed -> mic.destroy(); mic.reset(); voice.speak(text, speed); vm.touch() })
                        }
                        if (vm.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = HikariAmber)
                            ChatSmall("Anja hört auf den Inhalt und denkt über ihre Antwort nach …")
                        }
                        if (latest != null && !vm.busy) {
                            val reply = latest.reply
                            if (session.hint > 0) ConversationPanel {
                                Text("Deine Hilfe · ${session.hint}/4", color = HikariAmber, fontWeight = FontWeight.SemiBold)
                                ChatBody(reply.cue)
                                if (session.hint >= 2) reply.words.forEach { ChatBody("${it.ru} — ${it.de}") }
                                if (session.hint >= 3) {
                                    ChatBody(reply.starter)
                                    TextButton(onClick = { vm.draft(reply.starter); vm.assistance() }) { Text("Satzanfang übernehmen") }
                                }
                                if (session.hint >= 4) reply.examples.forEach { example ->
                                    ChatBody(example.ru); ChatSmall(example.de)
                                    Row {
                                        TextButton(onClick = { stopMedia(); voice.speak(example.ru, session.settings.speed) }) { Text("Anhören") }
                                        TextButton(onClick = { vm.draft(example.ru); vm.assistance() }) { Text("Als Starthilfe") }
                                    }
                                }
                            }
                            if (vm.repair) {
                                ChatBody("Noch einmal in deinen Worten — verbessere deine letzte Antwort.")
                                TextButton(onClick = vm::cancelRepair) { Text("Lieber weiterreden") }
                            }
                            OutlinedTextField(value = session.draft, onValueChange = { vm.draft(it) },
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                                label = { Text(if (session.draftSpoken) "So habe ich dich verstanden — stimmt das?" else "Deine Antwort auf Russisch") },
                                placeholder = { Text("Sprechen oder tippen. Ein Wort ist auch okay.") })
                            if (speech is RuListenState.Partial) ChatBody((speech as RuListenState.Partial).text)
                            RuPrimaryButton(if (listening) "Aufnahme beenden" else if (session.draft.isBlank()) "🎙  Ich antworte" else "🎙  Weiter diktieren") {
                                if (listening) mic.stopListening() else listen()
                            }
                            if (listening) TextButton(onClick = { mic.destroy(); mic.reset() }) { Text("Aufnahme verwerfen") }
                            if (session.draft.isNotBlank()) RuPrimaryButton("Antwort prüfen & weiter", enabled = !listening) { stopMedia(); vm.send() }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = vm::hint, enabled = session.hint < 4) { Text(if (session.hint == 0) "Ich brauche einen Tipp" else "Mehr Hilfe", color = HikariAmber) }
                                TextButton(onClick = { stopMedia(); vm.send("simplify") }, enabled = session.draft.isBlank() && !listening) { Text("Einfacher fragen") }
                            }
                            if (session.draft.isBlank() && !listening) ConversationTools(vm, onStop = { stopMedia() })
                            if (session.answers.size >= session.settings.turns) ConversationPanel {
                                ChatBody("Dein Gesprächsziel für heute ist erreicht. Du kannst noch weiterreden oder bewusst Pause machen.")
                                RuPrimaryButton("Mein Rückblick") { stopMedia(); vm.finish() }
                            }
                            if (session.activeSeconds >= session.settings.blockMinutes * 60) {
                                ChatSmall("Dein ${session.settings.blockMinutes}-Minuten-Block ist erreicht. Lust auf eine Pause? Dein Gespräch bleibt gespeichert.")
                            }
                        }
                    }
                }
            }
            vm.error?.let { ChatBody(it) }
            micError?.let { ChatBody(it) }
            voice.error?.let { ChatSmall(it) }
            if (voice.speaking) TextButton(onClick = voice::stop) { Text("Ton stoppen") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun ConversationSetup(vm: RussianConversationViewModel, p: RuChatProgress) {
    val s = vm.settings
    var options by rememberSaveable { mutableStateOf(false) }
    var confidence by rememberSaveable { mutableIntStateOf(2) }
    Text("Ein echtes Gespräch.\nIn deinem Tempo.", fontSize = 28.sp, lineHeight = 34.sp, color = HikariText, fontWeight = FontWeight.SemiBold)
    ChatBody("Du startest mit Wörtern, die du schon kannst. Anja hört zu, fragt nach und hilft erst, wenn du sie darum bittest.")
    ChatSmall("Deine Übungsstufe: ${RuChatCurriculum.stages[p.stage]}. Sie wächst mit selbstständigen Antworten und Erinnern an späteren Tagen — kein Sprachzertifikat.")
    ConversationChoices("Heute treffen wir uns …", RuChatCurriculum.scenarios.map { it.id to "${it.emoji} ${it.title}" }, s.scenario) { vm.configure(s.copy(scenario = it)) }
    OutlinedTextField(value = s.interest, onValueChange = { vm.configure(s.copy(interest = it.take(300))) },
        modifier = Modifier.fillMaxWidth(), label = { Text("Was interessiert dich? (freiwillig)") }, placeholder = { Text("Zum Beispiel mein Lieblingsfilm, Reisen, meine Katze …") })
    if (vm.available == false) ConversationPanel {
        ChatBody("Für freie Gespräche braucht Hikari den lokalen KI-Server in LM Studio. Wiederholen und Planen funktionieren auch ohne ihn.")
        TextButton(onClick = vm::refreshStatus) { Text("Verbindung prüfen") }
    }
    RuPrimaryButton("Mit Anja sprechen", enabled = !vm.busy) { vm.start(confidence) }
    ChatSmall("Du entscheidest, wann das Mikrofon startet. Prüfe den erkannten Text vor dem Senden. Dein Android-Sprachdienst kann Audio online verarbeiten; Hikari sendet nur den bestätigten Text an dein Backend und dessen konfigurierten KI-Server.")
    TextButton(onClick = { options = !options }) { Text(if (options) "Weniger Einstellungen" else "Gespräch anpassen") }
    if (options) {
        ConversationChoices("Was möchtest du üben?", listOf("everyday" to "Alltag", "stories" to "Geschichten", "media" to "Film & Musik"), s.goal) { vm.configure(s.copy(goal = it)) }
        ConversationChoices("Welche Stimmung passt heute?", listOf("calm" to "Entspannt", "playful" to "Verspielt", "curious" to "Neugierig"), s.mood) { vm.configure(s.copy(mood = it)) }
        ConversationChoices("Antworten pro Gespräch", listOf("6" to "6 · kurz", "12" to "12 · vertieft", "24" to "24 · ausführlich"), s.turns.toString()) { vm.configure(s.copy(turns = it.toInt())) }
        ConversationChoices("Wie sicher fühlst du dich gerade?", listOf("1" to "Unsicher", "2" to "Okay", "3" to "Mutig"), confidence.toString()) { confidence = it.toInt() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { ChatBody("Zuerst hören, Text auf Wunsch"); Switch(s.audioFirst, { vm.configure(s.copy(audioFirst = it)) }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { ChatBody("Anja automatisch vorlesen"); Switch(s.autoPlay, { vm.configure(s.copy(autoPlay = it)) }) }
        ConversationChoices("Sprechtempo", listOf("0.7" to "Langsam", "0.85" to "Ruhig", "1.0" to "Normal"), s.speed.toString()) { vm.configure(s.copy(speed = it.toFloat())) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ConversationPartner(turn: RuChatTurn, settings: RuChatSettings, current: Boolean, voiceReady: Boolean,
    vm: RussianConversationViewModel, onRetry: () -> Unit, onPlay: (String, Float) -> Unit) {
    var reveal by rememberSaveable(turn.id) { mutableStateOf(!settings.audioFirst) }
    var translate by rememberSaveable(turn.id) { mutableStateOf(false) }
    var feedbackOpen by rememberSaveable(turn.id) { mutableStateOf(false) }
    var correction by rememberSaveable(turn.id) { mutableStateOf(false) }
    var saved by rememberSaveable(turn.id) { mutableStateOf(false) }
    val question = remember { BringIntoViewRequester() }
    LaunchedEffect(turn.id, current) {
        if (current) { withFrameNanos { }; question.bringIntoView() }
    }
    val r = turn.reply
    ConversationPanel(modifier = Modifier.bringIntoViewRequester(question)) {
        ChatSmall("ANJA · Gesprächspartnerin")
        if (reveal || !voiceReady) Text(r.partnerRu, fontSize = 21.sp, lineHeight = 29.sp, color = HikariText)
        else ChatBody("Erst hören. Was möchte Anja wissen?")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { onPlay(r.partnerRu, settings.speed) }) { Text("▶ Anhören") }
            TextButton(onClick = { onPlay(r.partnerRu, 0.65f) }) { Text("Langsamer") }
            TextButton(onClick = { reveal = !reveal; if (current) vm.assistance() }) { Text(if (reveal) "Text verbergen" else "Text zeigen") }
            TextButton(onClick = { translate = !translate; if (current) vm.assistance() }) { Text("Deutsch") }
        }
        if (translate) ChatBody(r.partnerDe)
        if (current && r.feedback != null) {
            val f = r.feedback
            Text(when (f.meaning) { "clear" -> "Deine Aussage ist angekommen"; "partial" -> "Ein Teil ist angekommen"; else -> "Lass uns die Aussage klären" }, color = if (f.meaning == "clear") RuGood else HikariAmber)
            ChatSmall(f.praise)
            if (f.focus.isNotBlank()) {
                TextButton(onClick = { feedbackOpen = !feedbackOpen }) { Text("Ein kleiner Lernimpuls") }
                if (feedbackOpen) {
                    ChatBody(f.focus); ChatSmall(f.repairCue)
                    Row {
                        TextButton(onClick = onRetry, enabled = !vm.busy) { Text("Selbst verbessern") }
                        TextButton(onClick = { correction = !correction }) { Text("Beispiel zeigen") }
                    }
                    if (correction) {
                        ChatBody(f.correctedRu); ChatSmall(f.explanationDe)
                        TextButton(onClick = { onPlay(f.correctedRu, 0.75f) }) { Text("Verbesserung anhören") }
                    }
                }
            }
            ChatSmall("KI-Rückmeldung zu Inhalt und Wortwahl, kein Ausspracheurteil. Bei Erkennungsfehlern Text korrigieren und erneut versuchen.")
        }
        TextButton(onClick = { vm.savePhrase(r.chunk); saved = true }, enabled = !saved) { Text(if (saved) "Für später gemerkt ✓" else "Nützlichen Ausdruck merken") }
        if (saved) { ChatBody(r.chunk.ru); ChatSmall(r.chunk.de) }
    }
}

@Composable private fun ConversationTools(vm: RussianConversationViewModel, onStop: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger Gesprächsideen" else "Das Gespräch gestalten") }
    if (expanded) {
        val controls = listOf("deeper" to "Tiefer nachfragen", "twist" to "Eine kleine Überraschung", "roles" to "Jetzt frage ich", "retell" to "Aus dem Kopf nacherzählen", "listen" to "Eine Szene verstehen")
        controls.forEach { (action, label) -> OutlinedButton(onClick = { onStop(); vm.send(action) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy) { Text(label) } }
        ChatSmall("Auch tiefere Fragen bleiben auf deiner aktuellen Übungsstufe. Du darfst immer etwas erfinden oder das Thema wechseln.")
    }
}

@Composable private fun ConversationSummary(vm: RussianConversationViewModel, s: RuChatSession, p: RuChatProgress, onReview: () -> Unit) {
    val answers = s.answers
    Text("Du hast etwas zu sagen.", color = HikariText, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
    ChatBody("${answers.size} eigene Antworten · ${answers.count { it.spoken }} gesprochen · ${answers.count { !it.helped }} ohne aufgedeckte Hilfe")
    ChatSmall("Etwa ${s.activeSeconds / 60} aktive Minuten. Hilfen sind erlaubt; sie zählen nur anders für deine nächste Übungsstufe.")
    ConversationChoices("Wie fühlst du dich jetzt?", listOf("1" to "Unsicher", "2" to "Okay", "3" to "Mutig"), s.confidenceAfter?.toString() ?: "") { vm.confidence(it.toInt()) }
    s.confidenceAfter?.let { ChatSmall("Vorher: ${s.confidenceBefore}/3 · Jetzt: $it/3. Gefühl und Sprachleistung sind verschiedene Dinge.") }
    Text("Das nimmst du mit", color = HikariAmber, fontWeight = FontWeight.SemiBold)
    s.turns.map { it.reply.chunk }.distinctBy { it.ru }.takeLast(3).forEach { phrase ->
        ConversationPanel {
            ChatBody(phrase.ru); ChatSmall(phrase.de)
            TextButton(onClick = { vm.savePhrase(phrase) }, enabled = p.cards.none { it.phrase.ru == phrase.ru }) { Text(if (p.cards.any { it.phrase.ru == phrase.ru }) "Gemerkt ✓" else "Morgen wiederholen") }
        }
    }
    ChatBody("Deine Übungsstufe: ${RuChatCurriculum.stages[p.stage]}")
    ChatSmall("Weiter geht es nach stabilen, selbstständigen gesprochenen Antworten über mindestens drei Tage und zwei Situationen sowie späteren Erinnerungsproben. Diese vorsichtige App-Regel ist kein wissenschaftlich normierter Sprachtest.")
    RuPrimaryButton("Fällige Ausdrücke wiederholen", onClick = onReview)
    OutlinedButton(onClick = { vm.start(s.confidenceAfter ?: 2) }, modifier = Modifier.fillMaxWidth()) { Text("Gleiche Szene noch einmal") }
    TextButton(onClick = vm::setup) { Text("Eine andere Situation wählen") }
}

@Composable private fun ConversationReview(vm: RussianConversationViewModel, p: RuChatProgress, heard: String, spoken: Boolean,
    onText: (String) -> Unit, onListen: () -> Unit, onStop: () -> Unit, listening: Boolean, onPlay: (String) -> Unit, onClear: () -> Unit) {
    val due = RuChatLearning.due(p, vm.today())
    val card = due.firstOrNull()
    var revealed by remember(card?.phrase?.ru) { mutableStateOf(false) }
    var checked by remember(card?.phrase?.ru) { mutableStateOf<Boolean?>(null) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    Text("Was bleibt im Kopf?", color = HikariText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
    ChatBody("${due.size} fällig · ${p.cards.size} persönliche Ausdrücke")
    if (card == null) {
        ChatBody(if (p.cards.isEmpty()) "Merke dir im Gespräch einen nützlichen Ausdruck. Hier rufst du ihn später aktiv ab." else "Für heute ist alles wiederholt. Neue Ausdrücke kommen frühestens morgen wieder.")
    } else {
        ConversationPanel {
            ChatSmall("Wie sagst du das auf Russisch?")
            Text(card.phrase.de, color = HikariText, fontSize = 23.sp)
            if (revealed || checked != null) {
                ChatBody(card.phrase.ru)
                TextButton(onClick = { onPlay(card.phrase.ru) }) { Text("Ausdruck anhören") }
            }
            OutlinedTextField(heard, onText, modifier = Modifier.fillMaxWidth(), label = { Text("Erinnern, sprechen, dann prüfen") })
            RuPrimaryButton(if (listening) "Aufnahme beenden" else "🎙  Aus dem Kopf sagen", onClick = if (listening) onStop else onListen)
            if (checked == null) {
                TextButton(onClick = { revealed = true }) { Text("Ich brauche die Lösung") }
                RuPrimaryButton("Erinnerung prüfen", enabled = heard.isNotBlank() && !listening) {
                    checked = RuAnswerCheck.speech(card.phrase.ru, listOf(heard)).passed && !revealed && !archiveOpen
                }
            } else {
                ChatBody(if (checked == true) "Aus dem Kopf erinnert!" else "Noch nicht sicher. Vergleiche den Ausdruck und probiere ihn morgen wieder.")
                ChatSmall("Dies prüft den gemerkten Ausdruck anhand des Textes. Andere Formulierungen können ebenfalls richtig sein. Für den Aufstieg zählt hier nur selbstständiges Sprechen; getippte Antworten bleiben Übung. Es ist kein Aussprachetest.")
                RuPrimaryButton("Weiter") {
                    vm.gradeReview(card, checked == true && spoken)
                    onClear()
                }
            }
        }
    }
    if (p.cards.isNotEmpty()) {
        TextButton(onClick = { archiveOpen = !archiveOpen; if (card != null) revealed = true }) { Text(if (archiveOpen) "Sprachschatz verbergen" else "Sprachschatz nachschlagen (zeigt Lösungen)") }
        if (archiveOpen) p.cards.takeLast(10).reversed().forEach { entry ->
            ChatSmall("${entry.phrase.ru} · ${entry.phrase.de} · wieder am ${LocalDate.ofEpochDay(entry.due)}")
        }
    }
}

@Composable private fun ConversationPlan(vm: RussianConversationViewModel, p: RuChatProgress) {
    val s = vm.settings
    val today = LocalDate.now()
    val target = runCatching { LocalDate.parse(s.targetDate) }.getOrNull()
    val plan = target?.let { RuStudyPlanner.calculate(today, it, s.daysPerWeek, s.minutesPerDay, s.blockMinutes) }
    val uri = LocalUriHandler.current
    Text("Dein Wunschdatum.\nEin ehrlicher Plan.", color = HikariText, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)
    ChatBody("Wann möchtest du möglichst sicher sprechen? Ein Datum hilft beim Planen. Niemand kann seriös garantieren, wann du fließend sprichst oder jeden Film verstehst.")
    ConversationChoices("Mein Horizont", listOf("3" to "3 Monate", "6" to "6 Monate", "12" to "1 Jahr"), "") { vm.configure(s.copy(targetDate = today.plusMonths(it.toLong()).toString())) }
    OutlinedTextField(s.targetDate, { vm.configure(s.copy(targetDate = it.take(10))) }, modifier = Modifier.fillMaxWidth(), label = { Text("Wunschdatum · JJJJ-MM-TT") }, isError = s.targetDate.isNotEmpty() && plan == null)
    ConversationChoices("Lerntage pro Woche", listOf("3" to "3", "5" to "5", "6" to "6", "7" to "7"), s.daysPerWeek.toString()) { vm.configure(s.copy(daysPerWeek = it.toInt())) }
    ChatBody("Dein verfügbares Zeitbudget: ${s.minutesPerDay} Minuten pro Lerntag")
    Slider(value = s.minutesPerDay.toFloat(), onValueChange = { vm.configure(s.copy(minutesPerDay = (it.toInt() / 5 * 5).coerceAtLeast(5))) }, valueRange = 5f..600f)
    ConversationChoices("Ein Übungsblock dauert …", listOf("5" to "5 Min", "10" to "10 Min", "20" to "20 Min", "30" to "30 Min"), s.blockMinutes.toString()) { vm.configure(s.copy(blockMinutes = it.toInt())) }
    plan?.let {
        ConversationPanel {
            Text("Dein machbarer Kalender", color = HikariAmber, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ChatBody("${it.studyDays} geplante Lerntage · ${String.format(Locale.GERMAN, "%.1f", it.availableHours)} Stunden Zeitbudget bis zum Wunschdatum")
            ChatBody("${it.blocksPerDay} Blöcke pro Lerntag, letzter Block gegebenenfalls kürzer. Verteile sie nach Möglichkeit über den Tag.")
            ChatSmall("Die Blocklänge wählst du selbst. 10 oder 20 Minuten sind keine wissenschaftlich festgelegten Optimalwerte. Pausen sind nicht im Lernzeitbudget enthalten.")
        }
        ConversationPanel {
            Text("Was ein Stundenrichtwert aussagt", color = HikariAmber, fontWeight = FontWeight.SemiBold)
            ChatBody("Der archivierte FSI-Richtwert nennt für Russisch etwa 1.100 Unterrichtsstunden. Auf deinen Kalender verteilt wären das rechnerisch ${String.format(Locale.GERMAN, "%.1f", it.referenceHoursPerStudyDay)} Unterrichtsstunden pro Lerntag.")
            ChatSmall("Historischer Ausbildungsrichtwert für englischsprachige US-Diplomaten bis ILR S3/R3. Keine kontrollierte Studie, keine persönliche Empfehlung, keine Umrechnung in App-Stunden oder CEFR und keine Garantie für Film- oder Musikverständnis. Zusätzliche Eigenarbeit ist hier nicht beziffert.")
            if (it.exceedsBudget) ChatBody("Dieser reine Vergleich übersteigt dein Zeitbudget. Das bedeutet nicht, dass dein persönliches Ziel exakt 1.100 Stunden braucht. Wähle zunächst ein kleineres Gesprächsziel und überprüfe deinen Fortschritt regelmäßig.")
            if (it.referenceHoursPerStudyDay > 24) ChatBody("Die Vergleichsrechnung passt nicht einmal in einen Kalendertag. Das Wunschdatum ist für dieses Referenzpensum rechnerisch unmöglich.")
            TextButton(onClick = { uri.openUri(RuStudyPlanner.SOURCE) }) { Text("FSI-Quelle & Geltungsbereich") }
        }
    }
    ConversationPanel {
        ChatBody("Keine erfundene Gehirn-Grenze")
        ChatSmall("Studien stützen verteiltes Üben und späteres Abrufen. Sie liefern aber keine allgemeine Formel für deine optimalen Stunden pro Tag. Zehn Stunden sind nicht automatisch zehnmal so wirksam wie eine Stunde. Beende oder erleichtere einen Block, wenn du müde bist, und prüfe am nächsten Tag, was hängen geblieben ist.")
    }
    val seconds = p.activeSecondsByDay[vm.today()] ?: 0L
    ChatBody("Heute in Gesprächen aktiv: ${seconds / 60} Min · eigenes Budget: ${s.minutesPerDay} Min")
    LinearProgressIndicator(progress = { (seconds / (s.minutesPerDay * 60f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = HikariAmber)
    ChatSmall("Gemessene Gesprächszeit, ohne KI-Warten und längere Inaktivität. Keine Gleichsetzung mit FSI-Unterrichtsstunden.")
    val days = (6L downTo 0L).map { vm.today() - it }
    ChatSmall("Letzte 7 Tage: " + days.joinToString(" · ") { "${(p.activeSecondsByDay[it] ?: 0) / 60}m" })
    val recent = p.observations.takeLast(20)
    ChatBody("${recent.count { it.clear && it.independent && it.spoken }}/${recent.size} letzte Antworten selbstständig gesprochen und inhaltlich verständlich")
    ChatSmall("${p.cards.count { it.delayedCorrect > 0 }} Ausdrücke an späteren Tagen erinnert. Diese Beobachtungen steuern deine Übungsstufe; sie prognostizieren keinen Prüfungserfolg.")
}

@Composable private fun ConversationEvidence() {
    val uri = LocalUriHandler.current
    Text("Weniger raten. Mehr erinnern.", color = HikariText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    ChatBody("Die Lernprinzipien sind erforscht. Genau diese App, ihre KI und ihre Stufenschwellen sind noch nicht durch eine Wirksamkeitsstudie validiert.")
    val evidence = listOf(
        Triple("Selbst abrufen & später wiederholen", "Antworten bleiben zunächst verborgen. Gemerkte Ausdrücke kommen an späteren Tagen wieder. Der konkrete Wiederholungsplan ist eine App-Entscheidung, kein erwiesenes Optimum.", "https://doi.org/10.1111/lang.12479"),
        Triple("Eine hilfreiche Korrektur, dann selbst versuchen", "Rückmeldung kann Sprachlernen verbessern. Deshalb gibt es einen gezielten Lernimpuls und einen eigenen Verbesserungsversuch.", "https://doi.org/10.1017/S0272263109990520"),
        Triple("Gleiche Szene, neuer Versuch", "Wiederholte Sprechaufgaben können Ausdruck und Genauigkeit stärken. Der Transfer auf völlig neue Gespräche muss zusätzlich geübt werden.", "https://doi.org/10.1016/j.system.2025.103868"),
        Triple("Hören mit dosierter Textstütze", "Text kann Hörverstehen und Wortlernen unterstützen. Hier ist er auf Wunsch verfügbar. Ein KI-Dialog ersetzt keine authentischen Filmszenen oder menschlichen Stimmen.", "https://doi.org/10.1016/j.system.2013.07.013"),
        Triple("Als Anfänger hören und mitlesen", "Auch eine Studie mit Russischlernenden spricht für Textstützen beim Hörverstehen. Deshalb siehst du anfangs Anjas Frage; mögliche Antworten bleiben verborgen. Nur-Hören kannst du später selbst einschalten.", "https://www.lltjournal.org/item/10125-44203/"),
        Triple("Freude, Interesse & wenig Druck", "Lernfreude hängt positiv mit Lernleistung zusammen. Das beweist nicht, dass Emotion allein am schnellsten zur Sprachbeherrschung führt.", "https://doi.org/10.14746/ssllt.2022.12.2.3"),
        Triple("KI als Übungspartner", "Aktuelle Forschung zeigt Potenzial, aber auch uneinheitliche Studien und fehlerhafte Rückmeldungen. Beurteile Aussagen kritisch und übe auch mit Menschen.", "https://doi.org/10.1007/s10791-026-10015-1"),
        Triple("Spracherkennung ist kein Ausspracheprüfer", "Ein korrekt erkanntes Wort belegt keine perfekte Aussprache. Dieser Modus bewertet den bestätigten Text; für Laute gibt es Hikari-Lautlabor.", "https://doi.org/10.1017/S0958344023000113"),
        Triple("Verteilen statt nur anhäufen", "Eine Metaanalyse von 2025 unterstützt verteilte Übung. Daraus folgt keine universelle Tagesobergrenze und keine Formel für eine persönliche Fließend-Frist.", "https://doi.org/10.3390/bs15060771"),
    )
    evidence.forEach { (title, explanation, url) -> ConversationPanel {
        Text(title, color = HikariAmber, fontWeight = FontWeight.SemiBold)
        ChatBody(explanation)
        TextButton(onClick = { uri.openUri(url) }) { Text("Studie öffnen") }
    } }
    ChatSmall("Recherche: 26.10.2026. Überwiegend Zweitsprachenforschung, oft Englisch; Übertragung auf Russisch und diesen Modus ist begründet, aber nicht direkt bewiesen. Keine Funktion garantiert vollständiges Verstehen aller Filme oder Lieder.")
}

@Composable private fun ConversationChoices(title: String, values: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    ChatSmall(title)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (id, label) -> FilterChip(selected == id, onClick = { onSelect(id) }, label = { Text(label) }, modifier = Modifier.weight(1f)) }
        } }
    }
}
@Composable private fun ConversationPanel(learner: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(if (learner) HikariSurfaceHigh else HikariCardBg, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
@Composable private fun ChatBody(text: String) { Text(text, color = HikariText, fontSize = 15.sp, lineHeight = 22.sp) }
@Composable private fun ChatSmall(text: String) { Text(text, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 18.sp) }
