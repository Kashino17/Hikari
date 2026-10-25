package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hikari.app.domain.russian.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RussianConversationScreen(onClose: () -> Unit, vm: RussianConversationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val voice = remember { RussianConversationVoice(context) }
    val mic = remember { RussianSpeechRecognizer(context) }
    val speech by mic.state.collectAsState()
    val progress by vm.progress.collectAsState()
    val run = progress.foundation.run
    var more by remember { mutableStateOf(false) }
    var micError by remember { mutableStateOf<String?>(null) }
    var recordingTarget by remember { mutableStateOf("") }
    var reviewHeard by rememberSaveable { mutableStateOf("") }
    var reviewSpoken by remember { mutableStateOf(false) }
    var typing by rememberSaveable(run?.id, run?.phase, run?.index) { mutableStateOf(false) }
    val listening = speech is RuListenState.Listening || speech is RuListenState.Partial
    fun stopMedia() { recordingTarget = ""; voice.stop(); mic.destroy(); mic.reset() }
    fun go(page: String) { stopMedia(); micError = null; vm.navigate(page) }
    fun back() { stopMedia(); if (vm.page == "home") onClose() else go("home") }
    fun play(text: String) { stopMedia(); vm.touch(); voice.speak(text, vm.settings.speed) }
    fun beginListening() {
        stopMedia(); micError = null; recordingTarget = vm.page
        vm.touch(); mic.start()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) beginListening() else micError = "Mikrofon nicht freigegeben. Du kannst die Antwort auch tippen."
    }
    fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginListening()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    BackHandler { back() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { stopMedia(); vm.flushTime() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); voice.close(); mic.destroy(); vm.flushTime() }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(1000); vm.tick() }
        }
    }
    LaunchedEffect(speech) {
        when (val result = speech) {
            is RuListenState.Done -> {
                val heard = result.hypotheses.firstOrNull().orEmpty().trim()
                if (recordingTarget == vm.page) {
                    if (heard.isBlank()) micError = "Nichts sicher erkannt. Versuch es noch einmal oder tippe."
                    else when (vm.page) {
                        "lesson" -> vm.foundationDraft(heard, spoken = true)
                        "chat" -> vm.draft(heard, spoken = true)
                        "review" -> { reviewHeard = heard; reviewSpoken = true }
                    }
                }
                recordingTarget = ""; mic.reset()
            }
            is RuListenState.Failed -> if (recordingTarget == vm.page) { micError = result.message; recordingTarget = "" }
            else -> Unit
        }
    }
    JourneyTheme {
        val lesson = run?.let { RuJourney.lesson(it.lessonId) }
        JourneyScaffold(
            title = when (vm.page) { "lesson" -> lesson?.title ?: "Dein Einstieg"; "review" -> "Dein Wortschatz"; "plan" -> "Dein Lernplan"; "evidence" -> "Warum wir so üben"; "chat" -> "Mit Anja sprechen"; else -> "Gesprächsreise" },
            subtitle = when (vm.page) { "lesson" -> lesson?.place.orEmpty(); "chat" -> "Dein Tempo. Deine Geschichten."; else -> "Russisch · Schritt für Schritt" },
            onBack = ::back, onMore = { more = true },
            contentKey = "${vm.page}:${run?.id}:${run?.phase}:${run?.index}:${vm.session?.turns?.lastOrNull()?.id}",
            footer = if (vm.page == "lesson" && run != null) ({
                micError?.let { JourneySmall(it) }
                voice.error?.let { JourneySmall(it) }
                JourneyLessonFooter(run, progress.foundation, listening,
                    onNext = { stopMedia(); vm.nextFoundation() }, onHome = { go("home") },
                    onNextLesson = { stopMedia(); vm.beginLesson(RuJourney.next(progress.foundation).id) },
                    onListen = ::listen, onStop = { mic.stopListening() }, onCheck = { stopMedia(); vm.checkFoundation() })
            }) else null,
        ) {
            when (vm.page) {
                "lesson" -> if (run != null) when (run.phase) {
                    "learn" -> JourneyWordView(run, ::play)
                    "complete" -> JourneyCelebration(run, ::play)
                    else -> JourneyPracticeView(run, listening, (speech as? RuListenState.Partial)?.text.orEmpty(), typing,
                        onText = { vm.foundationDraft(it) }, onPlay = ::play, onHelp = vm::foundationHelp, onType = { typing = true; vm.touch() })
                }
                "free" -> JourneyFreeSetup(vm, progress, onResume = { go("chat") })
                "chat" -> JourneyFreeChat(vm, progress, listening, (speech as? RuListenState.Partial)?.text.orEmpty(),
                    onListen = ::listen, onStop = { mic.stopListening() }, onPlay = ::play,
                    onSend = { action -> stopMedia(); vm.send(action) }, onFinish = { stopMedia(); vm.finish() },
                    onHome = { go("home") }, onNew = { stopMedia(); vm.setup(); go("free") })
                "review" -> ConversationReview(vm, progress, reviewHeard, reviewSpoken,
                    onText = { reviewHeard = it; reviewSpoken = false }, onListen = ::listen,
                    onStop = { mic.stopListening() }, listening = listening, onPlay = ::play,
                    onClear = { reviewHeard = ""; reviewSpoken = false; stopMedia() })
                "plan" -> ConversationPlan(vm, progress)
                "evidence" -> ConversationEvidence()
                "settings" -> JourneySettings(vm)
                else -> JourneyHome(progress, vm.today(), onStart = { stopMedia(); vm.beginLesson(it) }, onResume = { stopMedia(); vm.resumeLesson() },
                    onReview = { go("review") }, onPlan = { go("plan") }, onFree = { go("free") })
            }
            vm.error?.let { JourneyCard(color = JourneyPeach) { JourneyBody(it) } }
            if (vm.page != "lesson") {
                micError?.let { JourneyCard(color = JourneyPeach) { JourneyBody(it) } }
                voice.error?.let { JourneySmall(it) }
            }
            if (voice.speaking) TextButton(onClick = voice::stop) { Text("Ton stoppen") }
            Spacer(Modifier.height(8.dp))
        }
        if (more) ModalBottomSheet(onDismissRequest = { more = false }, containerColor = JourneyPaper) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                JourneyTitle("Dein Platz zum Lernen", small = true)
                listOf(Triple("home", "Meine Reise", Icons.Rounded.Home), Triple("review", "Mein Wortschatz", Icons.Rounded.AutoStories),
                    Triple("plan", "Mein Lernplan", Icons.Rounded.CalendarToday), Triple("settings", "Sprechtempo & Hilfe", Icons.Rounded.Tune),
                    Triple("evidence", "Was die Forschung sagt", Icons.Rounded.Science)).forEach { (page, label, icon) ->
                    JourneyLink(label, icon) { more = false; go(page) }
                }
            }
        }
    }
}
